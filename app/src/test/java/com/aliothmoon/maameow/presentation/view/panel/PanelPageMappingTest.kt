package com.aliothmoon.maameow.presentation.view.panel

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelPageMappingTest {

    @Test
    fun pageToTabMapping_matchesExpectedOrder() {
        val expectedOrder = listOf(
            PanelTab.TASKS,
            PanelTab.EPIC7,
            PanelTab.AUTO_BATTLE,
            PanelTab.TOOLS,
            PanelTab.LOG,
        )

        expectedOrder.forEachIndexed { index, tab ->
            assertEquals(tab, panelTabForPage(index))
        }
    }

    @Test
    fun tabToPageMapping_matchesExpectedOrder() {
        val expectedOrder = listOf(
            PanelTab.TASKS,
            PanelTab.EPIC7,
            PanelTab.AUTO_BATTLE,
            PanelTab.TOOLS,
            PanelTab.LOG,
        )

        expectedOrder.forEachIndexed { index, tab ->
            assertEquals(index, pageForPanelTab(tab))
        }
    }
}
