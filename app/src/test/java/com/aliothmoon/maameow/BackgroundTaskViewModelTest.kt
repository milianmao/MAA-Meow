package com.aliothmoon.maameow

import android.content.Context
import com.aliothmoon.maameow.data.model.LogItem
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundTaskViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onTabChange_toEpic7_loadsEpic7Resources() = runTest {
        val viewModel = createViewModel(defaultClientType = "Official")

        viewModel.onTabChange(PanelTab.EPIC7)
        advanceUntilIdle()

        coVerify(exactly = 1) { resourceLoader.load("epic7") }
    }

    @Test
    fun onTabChange_fromEpic7ToTasks_restoresDefaultResources() = runTest {
        val viewModel = createViewModel(defaultClientType = "Official")

        viewModel.onTabChange(PanelTab.EPIC7)
        viewModel.onTabChange(PanelTab.TASKS)
        advanceUntilIdle()

        coVerify(exactly = 1) { resourceLoader.load("Official") }
    }

    private val resourceLoader = mockk<MaaResourceLoader> {
        coEvery { load(any()) } returns Result.success(Unit)
    }

    private fun createViewModel(defaultClientType: String): BackgroundTaskViewModel {
        val chainState = mockk<TaskChainState> {
            every { getClientType() } returns defaultClientType
            every { getClientTypeOrNull() } returns defaultClientType
            every { chain } returns MutableStateFlow(emptyList())
            every { activeProfileId } returns MutableStateFlow("")
            every { profiles } returns MutableStateFlow(emptyList())
            every { isLoaded } returns MutableStateFlow(true)
        }
        val compositionService = mockk<MaaCompositionService> {
            every { state } returns MutableStateFlow(MaaExecutionState.IDLE)
        }
        val sessionLogger = mockk<MaaSessionLogger> {
            every { logs } returns MutableStateFlow<List<LogItem>>(emptyList())
            every { clearRuntimeLogs() } returns Unit
        }
        val appSettingsManager = mockk<AppSettingsManager> {
            every { showTouchPreview } returns MutableStateFlow(false)
            every { closeAppOnTaskEnd } returns MutableStateFlow(false)
            every { muteOnGameLaunch } returns MutableStateFlow(false)
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
