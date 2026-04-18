package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StartGameConfigTest {

    @Test
    fun toTaskParams_returnsCustomWithExpectedTaskNameOnly() {
        val taskParams = StartGame().toTaskParams()

        assertEquals(MaaTaskType.CUSTOM, taskParams.type)

        val payload = Json.parseToJsonElement(taskParams.params).jsonObject
        val taskNames = payload["task_names"]?.jsonArray

        assertEquals(1, payload.size)
        assertEquals(1, taskNames?.size)
        assertEquals(
            listOf(StartGame.TASK_NAME),
            taskNames?.map { it.jsonPrimitive.content }
        )
        assertFalse(payload.containsKey("client_type"))
        assertFalse(payload.containsKey("start_game_enabled"))
        assertFalse(payload.containsKey("account_name"))
    }
}
