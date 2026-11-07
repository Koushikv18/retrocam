package com.koushik.retrocam.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.koushik.retrocam.data.model.CameraPreset
import java.util.concurrent.Executor

class RetroVideoProcessor(
    private val lutManager: LUTManager
) : SurfaceProcessor {

    private val TAG = "RetroVideoProcessor"

    private val handlerThread = HandlerThread("RetroVideoGL").apply { start() }
    val handler = Handler(handlerThread.looper)
    val executor = Executor { command -> handler.post(command) }

    private var eglCore: EglCore? = null
    private var dummySurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var outputEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var pipeline: RetroFilterPipeline? = null

    private var inputTextureId = 0
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private var currentOutput: SurfaceOutput? = null
    private var outputSize = Size(1920, 1080)

    private val texMatrix = FloatArray(16)
    private val finalMatrix = FloatArray(16)

    @Volatile
    var currentPreset: CameraPreset = CameraPreset(id = "cpm35", name = "CPM35")

    init {
        handler.post {
            initGl()
        }
    }

    private fun initGl() {
        try {
            val core = EglCore()
            eglCore = core
            dummySurface = core.createOffscreenSurface(1, 1)
            core.makeCurrent(dummySurface)
            pipeline = RetroFilterPipeline()

            val textures = IntArray(1)
            GLES30.glGenTextures(1, textures, 0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            inputTextureId = textures[0]
            Log.d(TAG, "GL initialized for video recording with OES texture: $inputTextureId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init GL for video processor", e)
        }
    }

    override fun onInputSurface(request: SurfaceRequest) {
        handler.post {
            try {
                val core = eglCore ?: return@post
                core.makeCurrent(dummySurface)

                inputSurfaceTexture?.release()
                inputSurface?.release()

                val st = SurfaceTexture(inputTextureId).apply {
                    setDefaultBufferSize(request.resolution.width, request.resolution.height)
                    setOnFrameAvailableListener({
                        onFrameAvailable()
                    }, handler)
                }
                inputSurfaceTexture = st
                val surface = Surface(st)
                inputSurface = surface

                request.provideSurface(surface, executor) { result ->
                    Log.d(TAG, "Input surface closed with result: ${result.resultCode}")
                    inputSurfaceTexture?.release()
                    inputSurface?.release()
                    inputSurfaceTexture = null
                    inputSurface = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onInputSurface", e)
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        handler.post {
            try {
                currentOutput = surfaceOutput
                outputSize = surfaceOutput.size

                val core = eglCore ?: return@post
                if (outputEglSurface != EGL14.EGL_NO_SURFACE) {
                    core.releaseSurface(outputEglSurface)
                    outputEglSurface = EGL14.EGL_NO_SURFACE
                }

                val surface = surfaceOutput.getSurface(executor) {
                    handler.post {
                        core.releaseSurface(outputEglSurface)
                        outputEglSurface = EGL14.EGL_NO_SURFACE
                        currentOutput = null
                    }
                }
                outputEglSurface = core.createWindowSurface(surface)
                Log.d(TAG, "Video output surface configured: size=${outputSize.width}x${outputSize.height}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in onOutputSurface", e)
            }
        }
    }

    private fun onFrameAvailable() {
        val st = inputSurfaceTexture ?: return
        val out = currentOutput ?: return
        val core = eglCore ?: return
        if (outputEglSurface == EGL14.EGL_NO_SURFACE) return

        try {
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
            out.updateTransformMatrix(finalMatrix, texMatrix)

            core.makeCurrent(outputEglSurface)
            GLES30.glViewport(0, 0, outputSize.width, outputSize.height)

            val preset = currentPreset

            pipeline?.render(
                mainTextureId = inputTextureId,
                isOesTexture = true,
                lutManager = lutManager,
                preset = preset,
                width = outputSize.width,
                height = outputSize.height,
                timeSeconds = (System.currentTimeMillis() % 1000000L) / 1000.0f,
                zoom = 1.0f,
                mirror = false,
                texMatrix = finalMatrix
            )

            core.setPresentationTime(outputEglSurface, st.timestamp)
            core.swapBuffers(outputEglSurface)
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering video frame: ${e.message}")
        }
    }

    fun release() {
        handler.post {
            pipeline?.release()
            eglCore?.let { core ->
                if (outputEglSurface != EGL14.EGL_NO_SURFACE) core.releaseSurface(outputEglSurface)
                if (dummySurface != EGL14.EGL_NO_SURFACE) core.releaseSurface(dummySurface)
                core.release()
            }
            handlerThread.quitSafely()
        }
    }
}

class RetroVideoEffect(
    val processor: RetroVideoProcessor
) : CameraEffect(
    VIDEO_CAPTURE,
    processor.executor,
    processor,
    Consumer { error ->
        Log.e("RetroVideoEffect", "CameraEffect error: ", error)
    }
)
