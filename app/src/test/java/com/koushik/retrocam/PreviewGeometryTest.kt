package com.koushik.retrocam

import com.koushik.retrocam.camera.FrameRect
import com.koushik.retrocam.camera.PreviewGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewGeometryTest {

    @Test
    fun landscapeBufferWithQuarterTurnIsPortrait() {
        assertEquals(0.75f, PreviewGeometry.uprightAspect(1440, 1080, 90), 0.001f)
        assertEquals(0.75f, PreviewGeometry.uprightAspect(1440, 1080, 270), 0.001f)
        assertEquals(1.3333f, PreviewGeometry.uprightAspect(1440, 1080, 0), 0.001f)
        assertEquals(1.3333f, PreviewGeometry.uprightAspect(1440, 1080, 180), 0.001f)
    }

    @Test
    fun fitIsLetterboxedNotStretched() {
        val r = PreviewGeometry.fit(1080, 2400, 0.75f)
        assertEquals(1080, r.width)
        assertEquals(1440, r.height)
        assertEquals(0, r.left)
        assertEquals(480, r.top)
        // Aspect of the drawn frame equals the camera aspect (no distortion).
        assertEquals(0.75f, r.width.toFloat() / r.height, 0.001f)
    }

    @Test
    fun overlayWindowIsExactlyTheSavedCrop() {
        val frame = PreviewGeometry.fit(1080, 2400, 0.75f)
        // 3:2 look: full width, 720 tall, centred -> same numbers the overlay draws.
        val w = PreviewGeometry.captureWindow(frame, 1.5f)
        assertEquals(FrameRect(0, 840, 1080, 720), w)
        // 1:1 look
        assertEquals(FrameRect(0, 660, 1080, 1080), PreviewGeometry.captureWindow(frame, 1f))
        // 3:4 look = whole frame
        assertEquals(frame, PreviewGeometry.captureWindow(frame, 0.75f))
    }

    @Test
    fun touchOutsideFrameIsIgnored() {
        val frame = PreviewGeometry.fit(1080, 2400, 0.75f)
        assertNull(PreviewGeometry.touchToFrame(540f, 100f, frame))
        val hit = PreviewGeometry.touchToFrame(540f, 1200f, frame)
        assertNotNull(hit)
        assertEquals(0.5f, hit!!.first, 0.001f)
        assertEquals(0.5f, hit.second, 0.001f)
    }

    @Test
    fun displayToSensorHandlesRotation() {
        // Right-middle of an upright frame is the TOP-middle of a buffer that needs 90deg CW.
        val a = PreviewGeometry.displayToSensor(1f, 0.5f, 90, false)
        assertEquals(0.5f, a.first, 0.001f)
        assertEquals(0f, a.second, 0.001f)
        // No rotation: identity.
        val b = PreviewGeometry.displayToSensor(0.25f, 0.75f, 0, false)
        assertEquals(0.25f, b.first, 0.001f)
        assertEquals(0.75f, b.second, 0.001f)
        // 180: point-reflected.
        val c = PreviewGeometry.displayToSensor(0.25f, 0.75f, 180, false)
        assertEquals(0.75f, c.first, 0.001f)
        assertEquals(0.25f, c.second, 0.001f)
        // Mirrored preview flips X before anything else.
        val d = PreviewGeometry.displayToSensor(0.25f, 0.75f, 0, true)
        assertEquals(0.75f, d.first, 0.001f)
        assertEquals(0.75f, d.second, 0.001f)
    }
}
