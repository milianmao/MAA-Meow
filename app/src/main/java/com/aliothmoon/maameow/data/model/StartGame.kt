package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

@Serializable
class StartGame : TaskParamProvider {
    override fun toTaskParams(): MaaTaskParams {
        val paramsJson = buildJsonObject {
            putJsonArray("task_names") {
                add(JsonPrimitive(TASK_NAME))
            }
        }
        return MaaTaskParams(MaaTaskType.CUSTOM, paramsJson.toString())
    }

    companion object {
        const val TASK_NAME = "EPIC7@StartGame"
    }
}