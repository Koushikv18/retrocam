package com.koushik.retrocam.util

import kotlin.math.abs

/** Aggregated live-preview statistics (all values already smoothed). */
data class SceneStats(
    val luma: Float,        // 0..255 average brightness
    val blownPct: Float,    // % of pixels nearly white
    val crushedPct: Float,  // % of pixels nearly black
    val sharpness: Float,   // mean absolute Laplacian, higher = more detail
    val warmth: Float       // V - U chroma; positive = warm light
)

enum class TipLevel { WARN, INFO, SUGGEST }

data class CoachTip(
    val id: String,
    val text: String,
    val level: TipLevel,
    val suggestPresetId: String? = null
)

/** Rule-based "shoot like a pro" assistant. Pure Kotlin so it can be unit tested. */
object ProCoach {

    /** Roll relative to the nearest 90 degrees, so landscape grips are judged too. */
    fun normalizedRoll(rollDeg: Float): Float {
        var r = rollDeg % 90f
        if (r > 45f) r -= 90f
        if (r < -45f) r += 90f
        return r
    }

    /** Lens shortcuts that fall inside the camera's zoom range. */
    fun lensStops(minZoom: Float, maxZoom: Float): List<Float> {
        val stops = listOf(0.6f, 1f, 2f, 3f, 5f, 10f).filter { it >= minZoom - 0.01f && it <= maxZoom + 0.01f }
        return if (stops.isEmpty()) listOf(1f) else stops
    }

    fun advise(
        stats: SceneStats?,
        rollDeg: Float,
        zoom: Float,
        minZoom: Float,
        maxOpticalZoom: Float?,
        gridOn: Boolean,
        currentPresetId: String,
        isFront: Boolean,
        isRecording: Boolean
    ): CoachTip? {
        val tilt = abs(normalizedRoll(rollDeg))
        if (tilt in 2.5f..25f) {
            return CoachTip("level", "Level your phone (tilted ${tilt.toInt()}\u00B0). Straight horizons look pro.", TipLevel.WARN)
        }
        if (stats != null) {
            if (stats.blownPct > 6f) {
                return CoachTip("blown", "Highlights are blowing out. Tap the bright area, then lower exposure (EV \u2212).", TipLevel.WARN)
            }
            if (stats.luma < 45f && !isFront) {
                val text = if (zoom >= 2f) "Low light at ${fmtZoom(zoom)}. Go back to 1x for cleaner shots, brace your arms."
                else "Low light. Brace your arms, tap your subject to brighten, and hold still."
                return CoachTip("lowlight", text, TipLevel.WARN)
            }
            if (stats.crushedPct > 35f && stats.luma < 90f) {
                return CoachTip("shadows", "Shadows are crushing. Raise exposure (EV +) or turn your subject toward the light.", TipLevel.INFO)
            }
            if (stats.sharpness < 2f && stats.luma >= 50f) {
                return CoachTip("soft", "Looks soft or flat. Tap to focus on your subject and hold steady.", TipLevel.INFO)
            }
        }
        if (isRecording) return null

        if (stats != null) {
            val suggestion: Triple<String, String, String>? = when {
                stats.luma < 60f -> Triple("cinestill800", "Night scene. Cinestill 800 is made for glowing lights.", "night")
                stats.luma >= 150f && stats.warmth > 6f -> Triple("gold200", "Warm daylight. Gold 200 loves this light.", "warm")
                stats.luma >= 150f -> Triple("fuji_ccd", "Bright, crisp light. Try Fuji CCD.", "crisp")
                zoom in 2.5f..6f -> Triple("portra400", "Portrait range. Portra 400 flatters skin tones.", "portrait")
                else -> null
            }
            if (suggestion != null && suggestion.first != currentPresetId) {
                return CoachTip("look_" + suggestion.third, suggestion.second, TipLevel.SUGGEST, suggestion.first)
            }
        }

        if (minZoom < 0.95f && zoom <= minZoom + 0.05f) {
            return CoachTip("ultrawide", "Ultra-wide: great for architecture and landscapes. Keep subjects away from the edges.", TipLevel.INFO)
        }
        if (zoom in 2.9f..3.2f) {
            return CoachTip("tele3", "3x is flattering for portraits. Step back and fill the frame.", TipLevel.INFO)
        }
        if (zoom in 4.8f..5.3f) {
            return CoachTip("tele5", "5x: brace your elbows. This is where long-lens shots look sharpest.", TipLevel.INFO)
        }
        if (maxOpticalZoom != null && zoom > maxOpticalZoom + 0.3f) {
            return CoachTip("digital", "Past ${fmtZoom(maxOpticalZoom)} is digital zoom. Move closer for more detail.", TipLevel.INFO)
        }
        if (maxOpticalZoom != null && zoom in 1.9f..2.1f) {
            return CoachTip("crop2", "2x is a sensor crop and stays crisp on high-megapixel phones.", TipLevel.INFO)
        }
        if (isFront) {
            return CoachTip("selfie", "Hold the phone slightly above eye level and face the light.", TipLevel.INFO)
        }
        if (!gridOn) {
            return CoachTip("grid", "Turn on the grid and place your subject on a thirds line.", TipLevel.INFO)
        }
        return null
    }

    private fun fmtZoom(z: Float): String =
        if (abs(z - z.toInt()) < 0.05f) "${z.toInt()}x" else "${(z * 10).toInt() / 10f}x"
}
