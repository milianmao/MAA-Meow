package com.aliothmoon.maameow.presentation.view.panel

import com.aliothmoon.maameow.data.model.TaskTypeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TaskGalleryFilterTest {

    @Test
    fun epic7Tab_returnsOnlyEpic7StartGame() {
        val available = availableTaskTypesForTab(PanelTab.EPIC7)

        assertEquals(listOf(TaskTypeInfo.EPIC7_START_GAME), available)
    }

    @Test
    fun tasksTab_excludesEpic7StartGame() {
        val available = availableTaskTypesForTab(PanelTab.TASKS)

        assertFalse(available.contains(TaskTypeInfo.EPIC7_START_GAME))
    }
}
