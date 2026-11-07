package com.koushik.retrocam.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class LUTManager {
    companion object {
        const val LUT_SIZE = 33
    }

    private val lutTextureCache = mutableMapOf<String, Int>()

    fun getOrLoadLut(lutId: String): Int {
        lutTextureCache[lutId]?.let { return it }

        val textureId = generate3DLut(lutId)
        lutTextureCache[lutId] = textureId
        return textureId
    }

    fun bindLut(lutId: String, textureUnit: Int) {
        val texId = getOrLoadLut(lutId)
        GLES30.glActiveTexture(textureUnit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
    }

    private fun generate3DLut(lutId: String): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val texId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        val totalPixels = LUT_SIZE * LUT_SIZE * LUT_SIZE
        val buffer = ByteBuffer.allocateDirect(totalPixels * 4).order(ByteOrder.nativeOrder())

        val step = 1.0f / (LUT_SIZE - 1).toFloat()

        for (bIdx in 0 until LUT_SIZE) {
            val b = bIdx * step
            for (gIdx in 0 until LUT_SIZE) {
                val g = gIdx * step
                for (rIdx in 0 until LUT_SIZE) {
                    val r = rIdx * step

                    val (tr, tg, tb) = applyFilmTransform(lutId, r, g, b)

                    buffer.put((clamp01(tr) * 255.0f).toInt().toByte())
                    buffer.put((clamp01(tg) * 255.0f).toInt().toByte())
                    buffer.put((clamp01(tb) * 255.0f).toInt().toByte())
                    buffer.put(255.toByte())
                }
            }
        }
        buffer.position(0)

        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGBA8,
            LUT_SIZE,
            LUT_SIZE,
            LUT_SIZE,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            buffer
        )

        return texId
    }

    private fun applyFilmTransform(lutId: String, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b

        return when (lutId) {
            "cpm35" -> {
                // Warm, lifted shadows with subtle cyan shadow tint and warm highlights
                val cr = sCurve(r, 1.15f) * 1.05f + 0.02f
                val cg = sCurve(g, 1.10f) * 1.02f
                val cb = sCurve(b, 1.05f) * 0.92f - 0.01f
                Triple(cr, cg, cb)
            }
            "classic" -> {
                // 90s compact film camera: slightly compressed dynamic range, gentle warmth
                val cr = (r - 0.5f) * 0.95f + 0.52f
                val cg = (g - 0.5f) * 0.92f + 0.50f
                val cb = (b - 0.5f) * 0.88f + 0.47f
                Triple(cr, cg, cb)
            }
            "hr1984" -> {
                // High contrast color negative: punchy reds, cool cyan shadows
                val cr = sCurve(r, 1.30f) * 1.10f
                val cg = sCurve(g, 1.25f) * 1.05f
                val cb = sCurve(b, 1.20f) * 1.15f
                Triple(cr, cg, cb)
            }
            "instant", "pola600" -> {
                // Instant film: milked blacks (lifted 0.08), soft highlight rolloff, warm cast
                val cr = (r * 0.88f + 0.08f) * 1.05f
                val cg = (g * 0.86f + 0.07f) * 1.02f
                val cb = (b * 0.82f + 0.06f) * 0.95f
                Triple(cr, cg, cb)
            }
            "fuji_ccd" -> {
                // Super CCD emerald green tint, crisp clarity
                val cr = sCurve(r, 1.12f) * 0.98f
                val cg = sCurve(g, 1.15f) * 1.08f
                val cb = sCurve(b, 1.12f) * 1.04f
                Triple(cr, cg, cb)
            }
            "gold" -> {
                // Kodak Gold 200: intense golden amber glow in highlights, warm skin tones
                val cr = sCurve(r, 1.18f) * 1.12f + 0.02f
                val cg = sCurve(g, 1.10f) * 1.04f
                val cb = sCurve(b, 1.05f) * 0.82f
                Triple(cr, cg, cb)
            }
            "disposable" -> {
                // Flash disposable: harsh contrast, crushed shadows, saturated skin
                val cr = sCurve(r, 1.35f) * 1.15f
                val cg = sCurve(g, 1.30f) * 1.05f
                val cb = sCurve(b, 1.25f) * 0.90f
                Triple(cr, cg, cb)
            }
            "vhs" -> {
                // VHS tape: slightly desaturated red, boosted cyan, magnetic blue bias
                val cr = sCurve(r, 1.10f) * 0.95f
                val cg = sCurve(g, 1.12f) * 1.02f
                val cb = sCurve(b, 1.15f) * 1.18f + 0.03f
                Triple(cr, cg, cb)
            }
            "noir" -> {
                // High contrast street black and white
                val bw = sCurve(lum, 1.45f)
                Triple(bw, bw, bw)
            }
            "tri_x" -> {
                // Kodak Tri-X 400: iconic rich midtone street black & white with smooth rolloff
                val bw = sCurve(lum, 1.32f) * 0.98f + 0.02f
                Triple(bw, bw, bw)
            }
            "portra" -> {
                // Portra 400: gentle contrast, creamy highlights, flattering skin tones
                val cr = sCurve(r, 1.06f) * 1.04f
                val cg = sCurve(g, 1.04f) * 1.00f
                val cb = sCurve(b, 1.02f) * 0.96f
                Triple(cr, cg, cb)
            }
            "cinestill" -> {
                // Cine 800T: tungsten teal-and-orange grade with intense red halation potential
                val cr = sCurve(r, 1.25f) * 1.18f
                val cg = sCurve(g, 1.15f) * 1.02f
                val cb = sCurve(b, 1.18f) * 1.20f
                Triple(cr, cg, cb)
            }
            "aerochrome" -> {
                // False color infrared: foliage (green) shifts to pink and crimson
                val ir = g * 1.5f + r * 0.3f
                val cr = ir * 1.1f
                val cg = r * 0.4f + g * 0.2f
                val cb = b * 0.8f
                Triple(cr, cg, cb)
            }
            "ektachrome" -> {
                // Saturated positive slide film: deep blues, vibrant punch
                val cr = sCurve(r, 1.35f) * 1.10f
                val cg = sCurve(g, 1.30f) * 1.05f
                val cb = sCurve(b, 1.40f) * 1.25f
                Triple(cr, cg, cb)
            }
            "y2k", "cyber_shot" -> {
                // Digicam 2000s: digital sharp look, cool tones, slightly blown highlights
                val cr = sCurve(r, 1.20f) * 0.96f
                val cg = sCurve(g, 1.20f) * 1.02f
                val cb = sCurve(b, 1.25f) * 1.15f
                Triple(cr, cg, cb)
            }
            "expired" -> {
                // Expired film: magenta shift, green-tinted shadows, faded blacks
                val cr = (r * 0.85f + 0.12f) * 1.15f
                val cg = (g * 0.80f + 0.08f) * 0.90f
                val cb = (b * 0.85f + 0.10f) * 1.10f
                Triple(cr, cg, cb)
            }
            else -> {
                // Identity
                Triple(r, g, b)
            }
        }
    }

    private fun sCurve(x: Float, contrast: Float): Float {
        val clamped = clamp01(x)
        return if (clamped <= 0.5f) {
            0.5f * (2.0f * clamped).pow(contrast)
        } else {
            1.0f - 0.5f * (2.0f * (1.0f - clamped)).pow(contrast)
        }
    }

    private fun clamp01(v: Float) = v.coerceIn(0.0f, 1.0f)

    fun release() {
        for (texId in lutTextureCache.values) {
            val tex = intArrayOf(texId)
            GLES30.glDeleteTextures(1, tex, 0)
        }
        lutTextureCache.clear()
    }
}

