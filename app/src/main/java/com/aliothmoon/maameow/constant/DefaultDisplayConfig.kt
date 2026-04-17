package com.aliothmoon.maameow.constant

/**
 * 默认允许的一些宽高配置
 */
object DefaultDisplayConfig {
    const val VD_NAME = "MAA_VD"
    const val DISPLAY_NONE = -1

    // 720p (默认，适用于大多数客户端)
    const val WIDTH = 1280
    const val HEIGHT = 720
    const val DPI = 160

    const val ASPECT_RATIO_WIDTH = 16
    const val ASPECT_RATIO_HEIGHT = 9

    /** 16:9 宽高比 */
    val ASPECT_RATIO: Float get() = WIDTH.toFloat() / HEIGHT

    const val FRAME_INTERVAL_MS = 16L

    data class Resolution(val width: Int, val height: Int, val dpi: Int)

    val RES_720P = Resolution(1280, 720, 160)
    val RES_1080P = Resolution(1920, 1080, 240)

    /** 用户可选的后台虚拟屏分辨率偏好 */
    enum class ResolutionPreference { P720, P1080 }

    /**
     * 根据用户偏好 + clientType 解析最终分辨率。
     * YoStarEN 静默强制 1080p；其他客户端使用用户偏好。
     */
    fun resolveResolution(
        clientType: String,
        preference: ResolutionPreference
    ): Resolution {
        if (clientType == "YoStarEN") return RES_1080P
        return when (preference) {
            ResolutionPreference.P720 -> RES_720P
            ResolutionPreference.P1080 -> RES_1080P
        }
    }
}
