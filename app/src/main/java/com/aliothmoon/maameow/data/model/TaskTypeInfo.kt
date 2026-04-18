package com.aliothmoon.maameow.data.model

import com.aliothmoon.maameow.data.preferences.AppSettingsManager

enum class TaskTypeInfo(
    private val defaultNameZh: String,
    private val defaultNameEn: String,
    val defaultConfig: () -> TaskParamProvider
) {
    WAKE_UP("开始唤醒", "Login", { WakeUpConfig() }),
    RECRUITING("自动公招", "Auto Recruit", { RecruitConfig() }),
    BASE("基建换班", "Base", { InfrastConfig() }),
    COMBAT("理智作战", "Combat", { FightConfig() }),
    MALL("信用收支", "Credit Store", { MallConfig() }),
    MISSION("领取奖励", "Collect Rewards", { AwardConfig() }),
    AUTO_ROGUELIKE("自动肉鸽", "Auto I.S.", { RoguelikeConfig() }),
    RECLAMATION("生息演算", "Reclamation Algorithm", { ReclamationConfig() }),
    EPIC7_START_GAME("启动游戏", "Start Game", { StartGame() });

    fun defaultName(language: AppSettingsManager.AppLanguage): String {
        return when (language) {
            AppSettingsManager.AppLanguage.EN -> defaultNameEn
            else -> defaultNameZh
        }
    }

    companion object {
        val normalTaskTypes: List<TaskTypeInfo>
            get() = entries.filter { it != EPIC7_START_GAME }
    }
}
