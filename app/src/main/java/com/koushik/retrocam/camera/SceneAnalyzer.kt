package com.koushik.retrocam.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.koushik.retrocam.util.SceneStats

/**
 * Cheap live scene analysis on the YUV preview frames (about 4 times per second).
 * Feeds the Pro Coach with brightness, clipping, detail and colour-cast numbers.
 */
class SceneAnalyzer(private val onStats: (SceneStats) -> Unit) : ImageAnalysis.Analyzer {

    private var lastRun = 0L
    private var smoothed: SceneStats? = null

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastRun < 250L) return
            lastRun = now
            if (image.planes.isEmpty()) return

            val yPlane = image.planes[0]
            val yBuf = yPlane.buffer
            val w = image.width
            val h = image.height
            val rowStride = yPlane.rowStride
            val pixStride = yPlane.pixelStride
            val limit = yBuf.limit()

            var count = 0
            var sum = 0L
            var blown = 0
            var crushed = 0
            var lapSum = 0L

            var y = 1
            while (y < h - 1) {
                var x = 1
                while (x < w - 1) {
                    val idx = y * rowStride + x * pixStride
                    if (idx + rowStride < limit && idx - rowStride >= 0) {
                        val c = yBuf.get(idx).toInt() and 0xFF
                        val l = yBuf.get(idx - pixStride).toInt() and 0xFF
                        val r = yBuf.get(idx + pixStride).toInt() and 0xFF
                        val u = yBuf.get(idx - rowStride).toInt() and 0xFF
                        val d = yBuf.get(idx + rowStride).toInt() and 0xFF
                        sum += c
                        if (c >= 250) blown++
                        if (c <= 8) crushed++
                        val lap = 4 * c - l - r - u - d
                        lapSum += if (lap < 0) -lap else lap
                        count++
                    }
                    x += 4
                }
                y += 4
            }
            if (count == 0) return

            var warmth = 0f
            if (image.planes.size >= 3) {
                val uPlane = image.planes[1]
                val vPlane = image.planes[2]
                val uBuf = uPlane.buffer
                val vBuf = vPlane.buffer
                var uSum = 0L
                var vSum = 0L
                var n = 0
                var cy = 0
                while (cy < h / 2) {
                    var cx = 0
                    while (cx < w / 2) {
                        val ui = cy * uPlane.rowStride + cx * uPlane.pixelStride
                        val vi = cy * vPlane.rowStride + cx * vPlane.pixelStride
                        if (ui < uBuf.limit() && vi < vBuf.limit()) {
                            uSum += uBuf.get(ui).toInt() and 0xFF
                            vSum += vBuf.get(vi).toInt() and 0xFF
                            n++
                        }
                        cx += 8
                    }
                    cy += 8
                }
                if (n > 0) warmth = (vSum - uSum).toFloat() / n
            }

            val raw = SceneStats(
                luma = sum.toFloat() / count,
                blownPct = 100f * blown / count,
                crushedPct = 100f * crushed / count,
                sharpness = lapSum.toFloat() / count,
                warmth = warmth
            )
            val prev = smoothed
            val a = 0.35f
            val out = if (prev == null) raw else SceneStats(
                luma = prev.luma + a * (raw.luma - prev.luma),
                blownPct = prev.blownPct + a * (raw.blownPct - prev.blownPct),
                crushedPct = prev.crushedPct + a * (raw.crushedPct - prev.crushedPct),
                sharpness = prev.sharpness + a * (raw.sharpness - prev.sharpness),
                warmth = prev.warmth + a * (raw.warmth - prev.warmth)
            )
            smoothed = out
            onStats(out)
        } catch (e: Exception) {
            // Never let analysis crash the camera.
        } finally {
            image.close()
        }
    }
}
