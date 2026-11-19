package com.koushik.retrocam.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.opengl.GLES30
import androidx.exifinterface.media.ExifInterface
import com.koushik.retrocam.data.model.CameraPreset
import com.koushik.retrocam.data.model.CameraSettings
import com.koushik.retrocam.util.DateStampRenderer
import com.koushik.retrocam.util.FrameCompositor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class StillImageProcessor {

    suspend fun processStillImage(
        inputBitmap: Bitmap,
        rotationDegrees: Int,
        preset: CameraPreset,
        settings: CameraSettings,
        lutManager: LUTManager,
        doubleExpBitmap: Bitmap? = null,
        mirror: Boolean = false
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Correct rotation and crop to the preset aspect ratio (portrait for upright photos,
        //    landscape for landscape sources such as imported images)
        val oriented = orient(inputBitmap, rotationDegrees)
        val portrait = preset.captureAspect
        val targetAspect = if (oriented.width > oriented.height && portrait < 1f) 1f / portrait else portrait
        val cropped = cropToAspectRatio(oriented, targetAspect)

        // Limit size to max 4096 for GPU texture to prevent GL out-of-spec or memory crashes
        val maxDim = max(cropped.width, cropped.height)
        val scaled = if (maxDim > 4096) {
            val scale = 4096f / maxDim
            Bitmap.createScaledBitmap(cropped, (cropped.width * scale).roundToInt(), (cropped.height * scale).roundToInt(), true)
        } else {
            cropped
        }

        val width = scaled.width
        val height = scaled.height

        // 2. Offscreen EGL rendering with GLES 3.0 with guaranteed resource cleanup and OOM shield
        var eglCore: EglCore? = null
        var eglSurface: android.opengl.EGLSurface? = null
        var pipeline: RetroFilterPipeline? = null
        var mainTexId = 0
        var doubleExpTexId: Int? = null

        val processedBitmap = try {
            val core = EglCore()
            eglCore = core
            val surface = core.createOffscreenSurface(width, height)
            eglSurface = surface
            core.makeCurrent(surface)

            val pipe = RetroFilterPipeline()
            pipeline = pipe

            mainTexId = uploadBitmapToTexture(scaled)
            doubleExpTexId = doubleExpBitmap?.let {
                val scaledExp = Bitmap.createScaledBitmap(cropToAspectRatio(it, targetAspect), width, height, true)
                uploadBitmapToTexture(scaledExp)
            }

            // Generate a unique per-shot seed so every photograph has an organic, authentic light leak and grain signature
            val randomSeed = (System.currentTimeMillis() % 1000000).toFloat() / 1000f

            pipe.render(
                mainTextureId = mainTexId,
                isOesTexture = false,
                lutManager = lutManager,
                preset = preset,
                width = width,
                height = height,
                timeSeconds = randomSeed,
                zoom = 1.0f,
                mirror = mirror,
                texMatrix = null,
                doubleExpTextureId = doubleExpTexId,
                doubleExpIntensity = settings.doubleExposureIntensity
            )

            val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuffer)

            val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            pixelBuffer.position(0)
            outputBitmap.copyPixelsFromBuffer(pixelBuffer)

            val flipMatrix = Matrix().apply { postScale(1.0f, -1.0f) }
            val finalFlipped = Bitmap.createBitmap(outputBitmap, 0, 0, width, height, flipMatrix, true)
            if (finalFlipped != outputBitmap) {
                outputBitmap.recycle()
            }
            finalFlipped
        } catch (t: Throwable) {
            android.util.Log.e("StillImageProcessor", "GL offscreen processing failed, falling back safely to source bitmap", t)
            scaled
        } finally {
            try {
                val texList = mutableListOf<Int>()
                if (mainTexId != 0) texList.add(mainTexId)
                doubleExpTexId?.let { if (it != 0) texList.add(it) }
                if (texList.isNotEmpty()) {
                    GLES30.glDeleteTextures(texList.size, texList.toIntArray(), 0)
                }
            } catch (e: Exception) { }
            try { pipeline?.release() } catch (e: Exception) { }
            try { eglSurface?.let { eglCore?.releaseSurface(it) } } catch (e: Exception) { }
            try { eglCore?.release() } catch (e: Exception) { }
        }

        // Ensure bitmap is mutable before drawing date stamp on Canvas
        val mutableGlBitmap = if (processedBitmap.isMutable) {
            processedBitmap
        } else {
            try {
                processedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            } catch (oom: OutOfMemoryError) {
                processedBitmap
            }
        }

        // 3. Draw date stamp if enabled
        if (settings.dateStampEnabled && preset.dateStamp.enabled && mutableGlBitmap.isMutable) {
            try {
                val canvas = Canvas(mutableGlBitmap)
                DateStampRenderer.drawDateStamp(
                    canvas = canvas,
                    width = width,
                    height = height,
                    config = preset.dateStamp,
                    overrideColor = settings.dateStampStyle.hexColor
                )
            } catch (e: Exception) {
                android.util.Log.w("StillImageProcessor", "Date stamp rendering skipped: ${e.message}")
            }
        }

        // 4. Apply frames and borders if enabled
        val framedBitmap = if (settings.frameEnabled && preset.frame != "none") {
            try {
                FrameCompositor.applyFrame(mutableGlBitmap, preset.frame)
            } catch (e: Exception) {
                mutableGlBitmap
            }
        } else {
            mutableGlBitmap
        }

        framedBitmap
    }

    /** Rotates a decoded camera bitmap so it is upright. */
    fun orient(src: Bitmap, rotationDegrees: Int): Bitmap =
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } else src

    private fun cropToAspectRatio(src: Bitmap, targetRatio: Float): Bitmap {
        val srcRatio = src.width.toFloat() / src.height.toFloat()
        return if (srcRatio > targetRatio) {
            // Source is wider: crop sides
            val targetWidth = (src.height * targetRatio).roundToInt().coerceAtMost(src.width)
            val xOffset = (src.width - targetWidth) / 2
            Bitmap.createBitmap(src, xOffset, 0, targetWidth, src.height)
        } else if (srcRatio < targetRatio) {
            // Source is taller: crop top/bottom
            val targetHeight = (src.width / targetRatio).roundToInt().coerceAtMost(src.height)
            val yOffset = (src.height - targetHeight) / 2
            Bitmap.createBitmap(src, 0, yOffset, src.width, targetHeight)
        } else {
            src
        }
    }

    private fun uploadBitmapToTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        val texId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        return texId
    }
}
