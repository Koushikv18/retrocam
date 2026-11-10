package com.koushik.retrocam.camera

import kotlin.math.roundToInt

/** A rectangle in view pixels (origin top-left). */
data class FrameRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Single source of truth for "what you see is what you capture".
 *
 * The camera buffer is delivered in sensor orientation. CameraX tells us how many degrees
 * (clockwise) it must be rotated to be upright. The still-photo path rotates the bitmap by the
 * same amount and then centre-crops to the preset aspect ratio, so the preview must:
 *   1. rotate the buffer the same way,
 *   2. show it undistorted (letterboxed "fit", never stretched),
 *   3. let the overlay window be a centred crop of exactly that frame.
 */
object PreviewGeometry {

    /** Width / height of the camera frame after it has been rotated upright. */
    fun uprightAspect(bufferWidth: Int, bufferHeight: Int, rotationDegrees: Int): Float {
        val w = bufferWidth.coerceAtLeast(1).toFloat()
        val h = bufferHeight.coerceAtLeast(1).toFloat()
        val r = ((rotationDegrees % 360) + 360) % 360
        return if (r == 90 || r == 270) h / w else w / h
    }

    /** Largest rect with [frameAspect] that fits inside the view, centred (no stretching). */
    fun fit(viewWidth: Int, viewHeight: Int, frameAspect: Float): FrameRect {
        val vw = viewWidth.coerceAtLeast(1)
        val vh = viewHeight.coerceAtLeast(1)
        val viewAspect = vw.toFloat() / vh.toFloat()
        val w: Int
        val h: Int
        if (frameAspect > viewAspect) {
            w = vw
            h = (vw / frameAspect).roundToInt().coerceIn(1, vh)
        } else {
            h = vh
            w = (vh * frameAspect).roundToInt().coerceIn(1, vw)
        }
        return FrameRect((vw - w) / 2, (vh - h) / 2, w, h)
    }

    /**
     * The part of [frame] that ends up in the saved photo for a preset aspect ratio.
     * Mirrors StillImageProcessor.cropToAspectRatio (centre crop).
     */
    fun captureWindow(frame: FrameRect, targetRatio: Float): FrameRect {
        val frameRatio = frame.width.toFloat() / frame.height.toFloat()
        return if (frameRatio > targetRatio) {
            val w = (frame.height * targetRatio).roundToInt().coerceAtMost(frame.width)
            FrameRect(frame.left + (frame.width - w) / 2, frame.top, w, frame.height)
        } else {
            val h = (frame.width / targetRatio).roundToInt().coerceAtMost(frame.height)
            FrameRect(frame.left, frame.top + (frame.height - h) / 2, frame.width, h)
        }
    }

    /** Touch position as 0..1 inside the frame (origin top-left), or null if outside it. */
    fun touchToFrame(x: Float, y: Float, rect: FrameRect): Pair<Float, Float>? {
        val u = (x - rect.left) / rect.width
        val v = (y - rect.top) / rect.height
        return if (u in 0f..1f && v in 0f..1f) Pair(u, v) else null
    }

    /**
     * Converts a point on the upright (displayed) frame, 0..1 with origin top-left, into the
     * sensor-oriented 0..1 coordinates that CameraX metering points expect.
     * [mirrored] = the on-screen preview is horizontally mirrored (front-camera selfie view).
     */
    fun displayToSensor(u: Float, v: Float, rotationDegrees: Int, mirrored: Boolean): Pair<Float, Float> {
        // Work in a centred, y-up frame.
        var x = (if (mirrored) 1f - u else u) - 0.5f
        var y = (1f - v) - 0.5f
        // The buffer must be rotated clockwise by R to be upright, so go back counter-clockwise.
        when (((rotationDegrees % 360) + 360) % 360) {
            90 -> { val nx = -y; val ny = x; x = nx; y = ny }
            180 -> { x = -x; y = -y }
            270 -> { val nx = y; val ny = -x; x = nx; y = ny }
        }
        return Pair((x + 0.5f).coerceIn(0f, 1f), (1f - (y + 0.5f)).coerceIn(0f, 1f))
    }
}
