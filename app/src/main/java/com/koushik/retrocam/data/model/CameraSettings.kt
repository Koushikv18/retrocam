package com.koushik.retrocam.data.model

enum class CameraMode(val label: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    DOUBLE_EXPOSURE("DOUBLE EXP"),
    BURST("BURST")
}

enum class SaveFormat(val displayName: String, val extension: String) {
    JPEG_HIGH("JPEG High (95%)", "jpg"),
    JPEG_MAX("JPEG Max (100%)", "jpg"),
    PNG("PNG Lossless", "png")
}

enum class DateStampStyle(val displayName: String, val hexColor: String?) {
    PRESET("Preset Default", null),
    AMBER("Quartz Amber", "#FF9A2E"),
    GREEN("80s VCR Green", "#26DE81"),
    RED("Digital Red", "#FF4757"),
    WHITE("Film White", "#F1F2F6")
}

data class CameraSettings(
    val selectedPresetId: String = "cpm35",
    val dateStampEnabled: Boolean = true,
    val dateStampStyle: DateStampStyle = DateStampStyle.PRESET,
    val gridEnabled: Boolean = false,
    val levelEnabled: Boolean = false,
    val timerSeconds: Int = 0, // 0, 3, 5, 10
    val mirrorFrontCamera: Boolean = true,
    val shutterSound: Boolean = true,
    val hapticFeedback: Boolean = true,
    val saveFormat: SaveFormat = SaveFormat.JPEG_HIGH,
    val frameEnabled: Boolean = true,
    val proCoach: Boolean = true,
    val doubleExposureIntensity: Float = 0.5f,
    val filmRollCounter: Int = 1
)
