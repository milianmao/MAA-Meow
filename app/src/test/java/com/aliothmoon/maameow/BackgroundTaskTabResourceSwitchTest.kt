package com.aliothmoon.maameow

import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.presentation.viewmodel.resolveResourceClientTypeForTab
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundTaskTabResourceSwitchTest {

    @Test
    fun epic7Tab_usesEpic7ResourceClientType() {
        val clientType = resolveResourceClientTypeForTab(
            targetTab = PanelTab.EPIC7,
            defaultClientType = "Official"
        )

        assertEquals("epic7", clientType)
    }

    @Test
    fun leavingEpic7Tab_restoresDefaultClientType() {
        val clientType = resolveResourceClientTypeForTab(
            targetTab = PanelTab.TASKS,
            defaultClientType = "Official"
        )

        assertEquals("Official", clientType)
    }
}
