package com.aliothmoon.maameow

import android.content.Context
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.StartGame
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaResourceLoader
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.PrepareTaskStartUseCase
import com.aliothmoon.maameow.overlay.screensaver.HardwareScreenOffManager
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundTaskViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val resourceLoader = mockk<MaaResourceLoader> {
        coEvery { load(any()) } returns Result.success(Unit)
    }

    @Test
    fun onTabChange_toEpic7_loadsEpic7Resources() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(defaultClientType = "Official")

        viewModel.onTabChange(PanelTab.EPIC7)
        advanceUntilIdle()

        coVerify(exactly = 1) { resourceLoader.load("epic7") }
    }

    @Test
    fun onTabChange_fromEpic7ToTasks_restoresDefaultResources() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(defaultClientType = "Official")

        viewModel.onTabChange(PanelTab.EPIC7)
        viewModel.onTabChange(PanelTab.TASKS)
        advanceUntilIdle()

        coVerify(exactly = 1) { resourceLoader.load("Official") }
    }

    @Test
    fun onStartTasks_inEpic7_usesEnabledStartGameClientType() = runTest(mainDispatcherRule.dispatcher) {
        val compositionService = mockk<MaaCompositionService> {
            every { state } returns MutableStateFlow(MaaExecutionState.IDLE)
            coEvery {
                start(
                    tasks = any(),
                    clientType = any(),
                )
            } returns MaaCompositionService.StartResult.Success("1.0")
        }
        val chainState = createEpic7ChainState(
            startGame = StartGame(clientType = "com.stove.epic7.google"),
        )
        val viewModel = createViewModel(
            defaultClientType = "Official",
            chainState = chainState,
            compositionService = compositionService,
        )

        viewModel.onTabChange(PanelTab.EPIC7)
        viewModel.onStartTasks()
        advanceUntilIdle()

        coVerify {
            compositionService.start(
                tasks = any(),
                clientType = "com.stove.epic7.google",
            )
        }
        verify { chainState.grantGameBatteryExemption("com.stove.epic7.google") }
    }

    @Test
    fun onStartTasks_inEpic7_fallsBackToDefaultStartGameClient() = runTest(mainDispatcherRule.dispatcher) {
        val compositionService = mockk<MaaCompositionService> {
            every { state } returns MutableStateFlow(MaaExecutionState.IDLE)
            coEvery {
                start(
                    tasks = any(),
                    clientType = any(),
                )
            } returns MaaCompositionService.StartResult.Success("1.0")
        }
        val chainState = createEpic7ChainState(startGame = StartGame())
        val viewModel = createViewModel(
            defaultClientType = "Official",
            chainState = chainState,
            compositionService = compositionService,
        )

        viewModel.onTabChange(PanelTab.EPIC7)
        viewModel.onStartTasks()
        advanceUntilIdle()

        coVerify {
            compositionService.start(
                tasks = any(),
                clientType = StartGame.DEFAULT_CLIENT_TYPE,
            )
        }
    }

    private fun createEpic7ChainState(startGame: StartGame): TaskChainState = mockk {
        every { getClientType() } returns "Official"
        every { getClientTypeOrNull() } returns "Official"
        every {
            chain
        } returns MutableStateFlow(
            listOf(
                TaskChainNode(
                    id = "epic7-start",
                    name = "Start Game",
                    enabled = true,
                    config = startGame,
                ),
            ),
        )
        every { activeProfileId } returns MutableStateFlow("")
        every { profiles } returns MutableStateFlow(emptyList())
        every { isLoaded } returns MutableStateFlow(true)
        every { grantGameBatteryExemption(any()) } returns Unit
    }

    private fun createViewModel(
        defaultClientType: String,
        chainState: TaskChainState = mockk {
            every { getClientType() } returns defaultClientType
            every { getClientTypeOrNull() } returns defaultClientType
            every { chain } returns MutableStateFlow(emptyList())
            every { activeProfileId } returns MutableStateFlow("")
            every { profiles } returns MutableStateFlow(emptyList())
            every { isLoaded } returns MutableStateFlow(true)
            every { grantGameBatteryExemption(any()) } returns Unit
        },
        compositionService: MaaCompositionService = mockk {
            every { state } returns MutableStateFlow(MaaExecutionState.IDLE)
        },
    ): BackgroundTaskViewModel {
        val sessionLogger = mockk<MaaSessionLogger> {
            every { logs } returns MutableStateFlow<List<LogItem>>(emptyList())
            every { clearRuntimeLogs() } returns Unit
        }
        val appSettingsManager = mockk<AppSettingsManager> {
            every { showTouchPreview } returns MutableStateFlow(false)
            every { closeAppOnTaskEnd } returns MutableStateFlow(false)
            every { muteOnGameLaunch } returns MutableStateFlow(true)
        }
        val scheduleRepository = mockk<ScheduleStrategyRepository>(relaxed = true)
        val triggerLogger = mockk<ScheduleTriggerLogger>(relaxed = true)
        val application = mockk<Context>(relaxed = true)
        val prepareTaskStart = mockk<PrepareTaskStartUseCase>(relaxed = true)
        val hardwareScreenOffManager = mockk<HardwareScreenOffManager>(relaxed = true)

        return BackgroundTaskViewModel(
            chainState = chainState,
            prepareTaskStart = prepareTaskStart,
            compositionService = compositionService,
            resourceLoader = resourceLoader,
            sessionLogger = sessionLogger,
            appSettingsManager = appSettingsManager,
            hardwareScreenOffManager = hardwareScreenOffManager,
            scheduleRepository = scheduleRepository,
            triggerLogger = triggerLogger,
            application = application,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}