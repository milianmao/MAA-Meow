package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.alibaba.fastjson2.JSON
import com.aliothmoon.maameow.MaaCoreCallback
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.data.model.LogLevel

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import com.aliothmoon.maameow.maa.MaaInstanceOptions.ANDROID
import com.aliothmoon.maameow.maa.MaaInstanceOptions.TOUCH_MODE
import com.aliothmoon.maameow.maa.callback.MaaCallbackDispatcher
import com.aliothmoon.maameow.maa.callback.MaaExecutionStateHolder
import com.aliothmoon.maameow.maa.callback.SubTaskHandler
import com.aliothmoon.maameow.maa.callback.TaskChainStatusTracker
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.manager.RemoteServiceManager.useRemoteService
import com.aliothmoon.maameow.utils.Misc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * MaaCompositionService 负责协调 MaaCore 实例的启动/停止、显示层与任务链的组合逻辑。
 *
 * 主要职责：
 * - 加载并校验运行所需资源
 * - 创建并配置 MaaCore 实例
 * - 启动虚拟显示并与 MaaCore 建立连接
 * - 将任务追加到 MaaCore 并启动执行
 *
 * 注：仅添加注释，不修改业务逻辑。
 * @author YML
 */
class MaaCompositionService(
    private val context: Context,
    private val resourceLoader: MaaResourceLoader,
    private val appSettings: AppSettingsManager,
    private val unifiedStateDispatcher: UnifiedStateDispatcher,
    private val sessionLogger: MaaSessionLogger,
    private val activityManager: ActivityManager,
    private val appWatchdog: AppWatchdog,
    private val taskChainState: TaskChainState,
    private val subTaskHandler: SubTaskHandler,
    private val taskChainStatusTracker: TaskChainStatusTracker,
    private val notificationCenter: MaaNotificationCenter,
) : MaaExecutionStateHolder {

    private val _state = MutableStateFlow(MaaExecutionState.IDLE)
    val state: StateFlow<MaaExecutionState> = _state.asStateFlow()

    private val defaultResolution = DefaultDisplayConfig.Resolution(
        DefaultDisplayConfig.WIDTH, DefaultDisplayConfig.HEIGHT, DefaultDisplayConfig.DPI
    )
    private val _displayResolution = MutableStateFlow(defaultResolution)
    val displayResolution: StateFlow<DefaultDisplayConfig.Resolution> =
        _displayResolution.asStateFlow()

    override fun reportRunState(state: MaaExecutionState) {
        // STOPPING 期间，回调不主动设 IDLE — 由 finishStop() 统一处理
        if (_state.value == MaaExecutionState.STOPPING && state == MaaExecutionState.IDLE) {
            return
        }
        setRunState(state)
    }

    /**
     * 根据 MaaExecutionState 更新内部状态并在需要时启动或停止 TaskExecutionService
     * @author YML
     */
    private fun setRunState(state: MaaExecutionState) {
        _state.value = state
        when (state) {
            MaaExecutionState.STARTING ->
                TaskExecutionService.start(context)

            MaaExecutionState.IDLE, MaaExecutionState.ERROR ->
                TaskExecutionService.stop(context)

            MaaExecutionState.STOPPING, MaaExecutionState.RUNNING -> {}
        }
    }

    private val callbackDispatcher: MaaCallbackDispatcher by inject(MaaCallbackDispatcher::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectDeferred = AtomicReference<CompletableDeferred<Boolean>?>()

    /**
     * 启动流程的结果类型集合，表示各种可能的启动状态或错误原因
     * @author YML
     */
    sealed class StartResult {
        data class Success(val version: String) : StartResult()

        /** 资源加载失败（网络/IO/解压） */
        data class ResourceError(
            val exception: Throwable? = null
        ) : StartResult()

        /** MaaCore 实例初始化失败（创建实例、设置选项） */
        data class InitializationError(
            val phase: InitPhase,
        ) : StartResult() {
            enum class InitPhase {
                CREATE_INSTANCE,
                SET_TOUCH_MODE,
            }
        }

        /** 显示/连接层失败（虚拟屏幕、连接） */
        data class ConnectionError(
            val phase: ConnectPhase,
        ) : StartResult() {
            enum class ConnectPhase {
                DISPLAY_MODE,
                VIRTUAL_DISPLAY,
                MAA_CONNECT,
            }
        }

        /** MaaCore 运行时启动失败 */
        data object StartError : StartResult()

        /** 前台模式下检测到竖屏（高 > 宽），需要横屏才能运行 */
        data object PortraitOrientationError : StartResult()
    }

    /**
     * 停止流程的结果类型
     * @author YML
     */
    sealed class StopResult {
        /** 停止成功 */
        data object Success : StopResult()

        /** 停止失败 */
        data object Failed : StopResult()
    }


    init {
        scope.launch {
            unifiedStateDispatcher.serviceDiedEvent.collect {
                appWatchdog.stopWatching()
                setRunState(MaaExecutionState.ERROR)
                sessionLogger.completeSessionAndWait(
                    "SERVICE_DIED",
                    "MAA服务异常终止",
                    LogLevel.ERROR
                )
                notificationCenter.notifyServiceDied()
            }
        }

        scope.launch {
            appWatchdog.appDiedEvent.collect { packageName ->
                Timber.w("App watchdog detected app died: %s", packageName)
                sessionLogger.appendAndWait(
                    "游戏进程未启动或被异常关闭($packageName)",
                    LogLevel.WARNING
                )
            }
        }
    }

    /**
     * 统一处理来自 MaaCore 的回调：优先处理 async connect 的回调，否则转发给回调分发器
     * @param msg 回调消息码
     * @param json 回调携带的 JSON 字符串（可为 null）
     * @author YML
     */
    fun handleCallback(msg: Int, json: String?) {
        if (onAsyncConnectCallback(msg, json)) return
        callbackDispatcher.dispatch(msg, json)
    }

    val callback = object : MaaCoreCallback.Stub() {
        override fun onCallback(msg: Int, json: String?) = handleCallback(msg, json)
    }

    /**
     * 异步连接回调处理：用于 AsyncConnect 的结果解析与 CompletableDeferred 完成
     * @return 如果该回调为 async connect 的回调则返回 true（已处理），否则返回 false
     * @author YML
     */
    private fun onAsyncConnectCallback(msg: Int, json: String?): Boolean {
        if (msg != AsstMsg.AsyncCallInfo.value) return false
        val deferred = connectDeferred.get() ?: return true
        val obj = JSON.parseObject(json)
        val details = obj.getJSONObject("details")
        if (details != null) {
            val ret = details.getBooleanValue("ret", false)
            deferred.complete(ret)
        }
        return true
    }

    /**
     * 启动执行任务的入口方法，会调用 executeStart 完成完整的启动流程
     * @param tasks 要执行的任务列表
     * @param clientType 客户端类型标识，默认从 taskChainState 获取
     * @param onSessionStarted 可选的会话开始回调
     * @return 启动结果封装在 StartResult 中
     * @author YML
     */
    suspend fun start(
        tasks: List<MaaTaskParams>,
        clientType: String = taskChainState.getClientType(),
        onSessionStarted: (suspend () -> Unit)? = null
    ): StartResult = executeStart(
        tasks = tasks,
        clientType = clientType,
        startMessage = "开始执行任务，共 ${tasks.size} 项",
        successMessage = "任务开始运行",
        onSessionStarted = onSessionStarted,
    )

    suspend fun startCopilot(
        tasks: List<MaaTaskParams>,
        clientType: String = taskChainState.getClientType()
    ): StartResult = executeStart(
        tasks = tasks,
        clientType = clientType,
        startMessage = "开始执行自动战斗",
        successMessage = "自动战斗开始运行",
    )

    /**
     * 启动自动战斗（简化的 start 入口）
     * @param tasks 要执行的任务列表
     * @param clientType 客户端类型标识
     * @return 启动结果
     * @author YML
     */

    /**
     * 启动失败时的统一处理：设置运行状态、记录日志并结束会话
     * @author YML
     */
    private suspend fun failStart(
        message: String, sessionStatus: String, result: StartResult
    ): StartResult {
        setRunState(MaaExecutionState.ERROR)
        sessionLogger.appendAndWait(message, LogLevel.ERROR)
        sessionLogger.endSessionAndWait(sessionStatus)
        return result
    }

    /**
     * 检查启动前置条件：资源加载、前台模式方向校验等
     * @return 若有错误则返回对应的 StartResult，否则返回 null
     * @author YML
     */
    private suspend fun checkPreconditions(mode: RunMode): StartResult? {
        activityManager.runIfDirty { resourceLoader.load() }
        val loaded = resourceLoader.ensureLoaded()
        if (loaded.isFailure) {
            return failStart(
                "资源加载失败", "RESOURCE_ERROR",
                StartResult.ResourceError(loaded.exceptionOrNull())
            )
        }
        if (mode == RunMode.FOREGROUND) {
            val (width, height) = Misc.getScreenSize(context)
            if (height > width) {
                return failStart(
                    "当前为竖屏，无法在前台模式运行", "PORTRAIT",
                    StartResult.PortraitOrientationError
                )
            }
        }
        return null
    }

    /**
     * 确保 MaaCore 实例已创建并设置必要选项（如触控模式）
     * @return 若初始化失败返回对应 StartResult，否则返回 null
     * @author YML
     */
    private suspend fun ensureMaaInstance(maa: MaaCoreService): StartResult? {
        if (maa.hasInstance()) return null
        if (!maa.CreateInstance(callback)) {
            return failStart(
                "创建 MaaCore 实例失败", "CREATE_INSTANCE_ERROR",
                StartResult.InitializationError(StartResult.InitializationError.InitPhase.CREATE_INSTANCE)
            )
        }
        if (!maa.SetInstanceOption(TOUCH_MODE, ANDROID)) {
            return failStart(
                "设置触控模式失败", "SET_TOUCH_MODE_ERROR",
                StartResult.InitializationError(StartResult.InitializationError.InitPhase.SET_TOUCH_MODE)
            )
        }
        return null
    }

    /**
     * 发起异步连接并等待结果（带超时）
     * @param config 连接配置 JSON 字符串
     * @return 若连接失败或超时则返回对应的 StartResult，否则返回 null
     * @author YML
     */
    private suspend fun asyncConnect(maa: MaaCoreService, config: String): StartResult? {
        val deferred = CompletableDeferred<Boolean>()
        connectDeferred.set(deferred)
        maa.AsyncConnect("", "Android", config, false)
        val ret = withTimeoutOrNull(2000) { deferred.await() }
        connectDeferred.set(null)
        if (ret != true) {
            return failStart(
                "启动 MaaCore 超时或失败", "MAA_CONNECT_ERROR",
                StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.MAA_CONNECT)
            )
        }
        return null
    }

    /**
     * 设置显示并建立与 MaaCore 的连接（包含前台/后台分支逻辑）
     * @author YML
     */
    private suspend fun setupDisplayAndConnect(
        service: RemoteService, maa: MaaCoreService, mode: RunMode, clientType: String
    ): StartResult? {
        if (!service.setVirtualDisplayMode(mode.displayMode))
            return failStart(
                "设置显示模式失败", "DISPLAY_MODE_ERROR",
                StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE)
            )
        val displayId = service.startVirtualDisplay()
        if (displayId == -1)
            return failStart(
                "启动虚拟显示失败", "VIRTUAL_DISPLAY_ERROR",
                StartResult.ConnectionError(StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY)
            )
        val config = when (mode) {
            RunMode.FOREGROUND -> {
                val (w, h) = Misc.getScreenSize(context)
                buildConnectConfig(w, h, displayId)
            }

            RunMode.BACKGROUND -> {
                val r = resolveAndSetResolution(service, clientType)
                // 打印displayId
                Timber.i("Virtual display id: %d", displayId)
                buildConnectConfig(r.width, r.height, displayId)
            }
        }
        return asyncConnect(maa, config)
    }

    /**
     * 将任务追加到 MaaCore 并启动执行；会记录任务 id 并跟踪状态
     * @param maa MaaCore 服务实例
     * @param tasks 待追加的任务列表
     * @param successMessage 启动成功时记录的信息
     * @param mode 运行模式（前台/后台）
     * @author YML
     */
    private suspend fun appendTasksAndStart(
        maa: MaaCoreService,
        tasks: List<MaaTaskParams>,
        successMessage: String,
        mode: RunMode,
    ): StartResult {
        taskChainStatusTracker.clear()
        tasks.forEach { t ->
            sessionLogger.appendToFileOnly("[TaskParams] ${t.type.value}: ${t.params}")
            val taskId = maa.AppendTask(t.type.value, t.params)
            if (taskId > 0) {
                taskChainStatusTracker.register(taskId, t.type.value)
            }
        }
        if (!maa.Start()) {
            return failStart("MaaCore 启动失败", "START_ERROR", StartResult.StartError)
        }
        setRunState(MaaExecutionState.RUNNING)
        if (mode == RunMode.BACKGROUND) {
            appWatchdog.startWatching()
        }
        sessionLogger.appendAndWait(successMessage, LogLevel.SUCCESS)
        return StartResult.Success(maa.GetVersion())
    }

    /**
     * 执行启动主流程：资源检查、创建实例、显示设置、���加任务并启动
     * @param tasks 要执行的任务列表
     * @param clientType 客户端类型标识
     * @param startMessage 会话开始时的日志信息
     * @param successMessage 启动成功后的日志信息
     * @author YML
     */
    private suspend fun executeStart(
        tasks: List<MaaTaskParams>,
        clientType: String,
        startMessage: String,
        successMessage: String,
        onSessionStarted: (suspend () -> Unit)? = null,
    ): StartResult {
        // 将状态置为 STARTING，外部（如 TaskExecutionService）会响应并启动前台通知
        setRunState(MaaExecutionState.STARTING)

        // 开始一个新的会话（记录任务链的入参类型），用于会话日志管理
        sessionLogger.startSession(tasks.map { it.type.value })

        // 重置子任务处理器的会话相关状态，保证新会话干净开始
        subTaskHandler.resetSessionState()

        // 如果调用方传入了会话开始的自定义回调，异步调用它（可选）
        onSessionStarted?.invoke()

        // 记录会话开始的日志信息（阻塞直到写入完成）
        sessionLogger.appendAndWait(startMessage, LogLevel.INFO)

        val mode = appSettings.runMode.value
        return withContext(Dispatchers.IO) {
            // 检查启动前置条件（资源是否加载、前台模式方向校验等）
            checkPreconditions(mode)?.let { return@withContext it }

            // 使用 RemoteService 提供的连接上下文执行 MaaCore 相关操作
            useRemoteService { service ->
                val maa = service.maaCoreService

                // 确保 MaaCore 实例已创建并设置必要选项（例如触控模式）
                ensureMaaInstance(maa)?.let { return@useRemoteService it }

                // 根据运行模式设置显示并与 MaaCore 建立连接（包含虚拟显示、AsyncConnect 等）
                setupDisplayAndConnect(
                    service,
                    maa,
                    mode,
                    clientType
                )?.let { return@useRemoteService it }

                // 将任务追加到 MaaCore 并启动执行；启动成功后会切换到 RUNNING
                val result = appendTasksAndStart(maa, tasks, successMessage, mode)
                if (result is StartResult.Success) {
                    // 记录最后一次使用的客户端类型，便于下次默认选择
                    taskChainState.saveLastUsedClientType(clientType)
                }
                result
            }
        }
    }

    private fun resolveAndSetResolution(
        service: RemoteService,
        clientType: String
    ): DefaultDisplayConfig.Resolution {
        val preference = appSettings.backgroundResolution.value
        val r = DefaultDisplayConfig.resolveResolution(clientType, preference)
        service.setVirtualDisplayResolution(r.width, r.height, r.dpi)
        Timber.i(
            "Virtual display resolution: %dx%d@%d for client=%s, pref=%s",
            r.width,
            r.height,
            r.dpi,
            clientType,
            preference
        )
        _displayResolution.value = r
        return r
    }

    private fun buildConnectConfig(width: Int, height: Int, displayId: Int): String {
        return buildJsonObject {
            put("screen_resolution", buildJsonObject {
                put("width", width)
                put("height", height)
            })
            put("display_id", displayId)
            if (displayId != 0) {
                put("force_stop", true)
            }
        }.toString()
    }

    /**
     * 构造连接给 MaaCore 的配置 JSON（包含分辨率与 displayId）
     * @author YML
     */

    suspend fun stop(): StopResult {
        setRunState(MaaExecutionState.STOPPING)
        sessionLogger.appendAndWait("正在停止任务...", LogLevel.INFO)

        return withContext(Dispatchers.IO) {
            useRemoteService { service ->
                val maa = service.maaCoreService
                if (!maa.Running()) {
                    return@useRemoteService finishStop(StopResult.Success)
                }

                if (!maa.Stop()) {
                    return@useRemoteService finishStop(StopResult.Failed)
                }

                // 轮询等待 Core 真正停止，60 秒超时
                var elapsed = 0
                while (maa.Running() && elapsed < 60_000) {
                    delay(100)
                    elapsed += 100
                }

                if (maa.Running()) {
                    finishStop(StopResult.Failed)
                } else {
                    finishStop(StopResult.Success)
                }
            }
        }
    }

    private fun finishStop(result: StopResult): StopResult {
        appWatchdog.stopWatching()
        setRunState(MaaExecutionState.IDLE)
        val status = if (result is StopResult.Success) "STOPPED" else "STOP_FAILED"
        sessionLogger.append(
            "任务停止，状态: $status",
            if (result is StopResult.Success) LogLevel.INFO else LogLevel.ERROR
        )
        sessionLogger.endSession(status)
        return result
    }

    /**
     * 停止虚拟显示并重置相关状态
     * @author YML
     */
    suspend fun stopVirtualDisplay() {
        appWatchdog.stopWatching()
        _displayResolution.value = defaultResolution
        useRemoteService { it.stopVirtualDisplay() }
    }
}
