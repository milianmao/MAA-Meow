package com.aliothmoon.maameow.presentation.viewmodel

import com.aliothmoon.maameow.data.model.AwardConfig
import com.aliothmoon.maameow.data.model.StartGame
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class Epic7StartPlanTest {

    @Test
    fun buildEpic7Params_keepsEnabledCustomParamsOnly() {
        val chain = listOf(
            TaskChainNode(name = "start-1", enabled = true, config = StartGame()),
            TaskChainNode(name = "award", enabled = true, config = AwardConfig()),
            TaskChainNode(name = "start-2", enabled = false, config = StartGame()),
        )

        val params = buildEpic7Params(chain)

        assertEquals(1, params.size)
        assertEquals(MaaTaskType.CUSTOM, params.first().type)

        val payload = Json.parseToJsonElement(params.first().params).jsonObject
        val taskNames = payload["task_names"]?.jsonArray

        assertEquals(
            listOf(StartGame.TASK_NAME),
            taskNames?.map { it.jsonPrimitive.content }
        )
    }
}
