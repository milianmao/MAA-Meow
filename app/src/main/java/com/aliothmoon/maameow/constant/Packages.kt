package com.aliothmoon.maameow.constant

object Packages {
    private val packageName = mapOf(
        "Official" to "com.hypergryph.arknights",
        "Bilibili" to "com.hypergryph.arknights.bilibili",
        "YoStarEN" to "com.YoStarEN.Arknights",
        "YoStarJP" to "com.YoStarJP.Arknights",
        "YoStarKR" to "com.YoStarKR.Arknights",
        "txwy" to "tw.txwy.and.arknights",
        "epic7" to "com.stove.epic7.google"
    )

    operator fun get(type: String): String? = packageName[type]
}