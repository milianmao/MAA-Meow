package com.aliothmoon.maameow.presentation.view.panel

import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.StartGame
import com.aliothmoon.maameow.data.model.TaskChainNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TabNodeFilterTest {

    @Test
    fun epic7Tab_returnsOnlyStartGameNode() {
        val nodes = listOf(
            TaskChainNode(name = "start", config = StartGame()),
            TaskChainNode(name = "award", config = AwardConfig()),
        )

        val filtered = filterNodesForTab(PanelTab.EPIC7, nodes)

        assertEquals(1, filtered.size)
        assertEquals(StartGame::class, filtered.first().config::class)
    }

    @Test
    fun tasksTab_excludesStartGameNodeAndKeepsAwardNode() {
        val nodes = listOf(
            TaskChainNode(name = "start", config = StartGame()),
            TaskChainNode(name = "award", config = AwardConfig()),
        )

        val filtered = filterNodesForTab(PanelTab.TASKS, nodes)

        assertEquals(1, filtered.size)
        assertFalse(filtered.any { it.config is StartGame })
        assertEquals(AwardConfig::class, filtered.first().config::class)
    }
}
