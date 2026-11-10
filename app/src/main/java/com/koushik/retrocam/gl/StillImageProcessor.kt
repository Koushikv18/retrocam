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
        mirrorHorizontally: Boolean = false
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Correct rotation (and selfie mirroring, so the photo matches the mirrored preview)
        val oriented = if (rotationDegrees != 0 || mirrorHorizontally) {
            val matrix = Matrix().apply {
                if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
                if (mirrorHorizontally) postScale(-1f, 1f)
            }
            Bitmap.createBitmap(inputBitmap, 0, 0, inputBitmap.width, inputBitmap.height, matrix, true)
        } else {
            inputBitmap
        }

        // Crop to preset aspect ratio
        val cropped = cropToAspectRatio(oriented, preset.aspectRatioFloat)

        // Limit size to max 4096 for GPU texture
        val maxDim = max(cropped.width, cropped.height)
        val scaled = if (maxDim > 4096) {
            val scale = 4096f / maxDim
            Bitmap.createScaledBitmap(cropped, (cropped.width * scale).roundToInt(), (cropped.height * scale).roundToInt(), true)
        } else {
            cropped
        }

        val width = scaled.width
        val height = scaled.height

        // 2. Offscreen EGL rendering with GLES 3.0
        val eglCore = EglCore()
        val eglSurface = eglCore.createOffscreenSurface(width, height)
        eglCore.makeCurrent(eglSurface)

        val pipeline = RetroFilterPipeline()

        // Upload main texture
        val mainTexId = uploadBitmapToTexture(scaled)

        // Upload double exposure texture if present
        val doubleExpTexId = doubleExpBitmap?.let {
            val scaledExp = Bitmap.createScaledBitmap(it, width, height, true)
            uploadBitmapToTexture(scaledExp)
        }

        // Render through GL pipeline
        pipeline.render(
            mainTextureId = mainTexId,
            isOesTexture = false,
            lutManager = lutManager,
            preset = preset,
            width = width,
            height = height,
            timeSeconds = (System.currentTimeMillis() % 100000).toFloat() / 1000f,
            zoom = 1.0f,
            mirror = false,
            texMatrix = null,
            doubleExpTextureId = doubleExpTexId,
            doubleExpIntensity = settings.doubleExposureIntensity
        )

        // Read pixels back
        val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixelBuffer)

        // Clean up GL resources
        val texArray = if (doubleExpTexId != null) intArrayOf(mainTexId, doubleExpTexId) else intArrayOf(mainTexId)
        GLES30.glDeleteTextures(texArray.size, texArray, 0)
        pipeline.release()
        eglCore.releaseSurface(eglSurface)
        eglCore.release()

        // Invert Y axis from OpenGL coordinates
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        pixelBuffer.position(0)
        outputBitmap.copyPixelsFromBuffer(pixelBuffer)

        val flipMatrix = Matrix().apply { postScale(1.0f, -1.0f) }
        val correctedGlBitmap = Bitmap.createBitmap(outputBitmap, 0, 0, width, height, flipMatrix, true)

        // 3. Draw date stamp if enabled
        if (settings.dateStampEnabled && preset.dateStamp.enabled) {
            val canvas = Canvas(correctedGlBitmap)
            DateStampRenderer.drawDateStamp(canvas, width, height, preset.dateStamp)
        }

        // 4. Apply frames and borders if enabled
        val framedBitmap = if (settings.frameEnabled && preset.frame != "none") {
            FrameCompositor.applyFrame(correctedGlBitmap, preset.frame)
        } else {
            correctedGlBitmap
        }

        framedBitmap
    }

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

