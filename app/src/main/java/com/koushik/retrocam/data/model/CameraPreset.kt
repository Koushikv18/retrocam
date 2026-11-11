package com.koushik.retrocam.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DateStampConfig(
    val enabled: Boolean = true,
    val color: String = "#FF9A2E",
    val font: String = "quartz",
    val format: String = "'YY  M  D",
    val position: String = "bottom_right"
)

@Serializable
data class CameraPreset(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val aspectRatio: String = "3:2", // "3:2", "4:3", "1:1", "16:9", "3:4"
    val frame: String = "none",       // "none", "polaroid", "film_strip", "rounded_black", "rounded_white", "slide_mount"
    val lut: String = "neutral",
    val exposure: Float = 0.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val temperature: Float = 0.0f,
    val tint: Float = 0.0f,
    val fade: Float = 0.0f,
    val grain: Float = 0.05f,
    val vignette: Float = 0.25f,
    val halation: Float = 0.20f,
    val aberration: Float = 0.001f,
    val bloom: Float = 0.15f,
    val leak: Float = 0.0f,
    val scanlines: Float = 0.0f,
    val dust: Float = 0.0f,
    val tone: List<Float> = listOf(1.0f, 1.0f, 1.0f),
    val toneAmt: Float = 0.0f,
    val dateStamp: DateStampConfig = DateStampConfig()
) {
    val aspectWidth: Int
        get() = when (aspectRatio) {
            "1:1" -> 1
            "4:3" -> 4
            "3:4" -> 3
            "16:9" -> 16
            else -> 3 // "3:2"
        }

    val aspectHeight: Int
        get() = when (aspectRatio) {
            "1:1" -> 1
            "4:3" -> 3
            "3:4" -> 4
            "16:9" -> 9
            else -> 2 // "3:2"
        }

    val aspectRatioFloat: Float
        get() = aspectWidth.toFloat() / aspectHeight.toFloat()

    /** Aspect (width / height) of a photo taken with the phone held upright; always <= 1. */
    val captureAspect: Float
        get() = if (aspectRatioFloat > 1f) 1f / aspectRatioFloat else aspectRatioFloat
}

