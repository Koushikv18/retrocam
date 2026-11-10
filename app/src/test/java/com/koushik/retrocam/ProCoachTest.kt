package com.koushik.retrocam

import com.koushik.retrocam.util.ProCoach
import com.koushik.retrocam.util.SceneStats
import org.junit.Assert.*
import org.junit.Test

class ProCoachTest {
    private val ok = SceneStats(luma = 120f, blownPct = 0f, crushedPct = 0f, sharpness = 20f, warmth = 0f)

    @Test
    fun rollNormalizesToNearestRightAngle() {
        assertEquals(2f, ProCoach.normalizedRoll(92f), 0.001f)
        assertEquals(-3f, ProCoach.normalizedRoll(-3f), 0.001f)
        assertEquals(-44f, ProCoach.normalizedRoll(46f), 0.001f)
    }

    @Test
    fun lensStopsFollowCameraRange() {
        assertEquals(listOf(0.6f, 1f, 2f, 3f, 5f, 10f), ProCoach.lensStops(0.6f, 10f))
        assertEquals(listOf(1f, 2f, 3f, 5f), ProCoach.lensStops(1f, 5f))
        assertEquals(listOf(1f), ProCoach.lensStops(1f, 1f))
    }

    @Test
    fun tipsArePrioritised() {
        assertEquals("level", ProCoach.advise(null, 10f, 1f, 1f, null, true, "cpm35", false, false)?.id)
        assertEquals("blown", ProCoach.advise(ok.copy(blownPct = 10f), 0f, 1f, 1f, null, true, "cpm35", false, false)?.id)
        assertEquals("lowlight", ProCoach.advise(ok.copy(luma = 30f), 0f, 1f, 1f, null, true, "cpm35", false, false)?.id)
        assertNull(ProCoach.advise(null, 0f, 1f, 1f, null, true, "cpm35", false, false))
        assertEquals("grid", ProCoach.advise(null, 0f, 1f, 1f, null, false, "cpm35", false, false)?.id)
    }

    @Test
    fun suggestsALookOnlyWhenItIsNotAlreadySelected() {
        val warm = ok.copy(luma = 170f, warmth = 12f)
        assertEquals("gold200", ProCoach.advise(warm, 0f, 1f, 1f, null, true, "cpm35", false, false)?.suggestPresetId)
        assertNotEquals("gold200", ProCoach.advise(warm, 0f, 1f, 1f, null, true, "gold200", false, false)?.suggestPresetId)
    }
}
