package com.koushik.retrocam.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.core.content.ContextCompat
import com.koushik.retrocam.data.model.CameraPreset
import com.koushik.retrocam.gl.LUTManager
import com.koushik.retrocam.gl.RetroFilterPipeline
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Live viewfinder. Draws the camera frame through the retro shader pipeline.
 *
 * The frame is rotated upright using the rotation CameraX reports for the Preview and drawn
 * letterboxed (never stretched), so it lines up exactly with what StillImageProcessor saves.
 */
class CameraEffectSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val TAG = "CameraEffectSurface"

    private val lock = Any()
    private var oesTextureId = 0

    @Volatile private var surfaceTexture: SurfaceTexture? = null
    @Volatile private var surface: Surface? = null

    private var pipeline: RetroFilterPipeline? = null
    private var lutManager: LUTManager? = null

    private val transformMatrix = FloatArray(16)
    private val orientMatrix = FloatArray(16)
    private val displayMatrix = FloatArray(16)

    @Volatile var currentPreset: CameraPreset = CameraPreset(id = "cpm35", name = "CPM35")
    @Volatile var currentZoom: Float = 1.0f
    @Volatile var isMirror: Boolean = false
    @Volatile var doubleExpTextureId: Int? = null
    @Volatile var doubleExpIntensity: Float = 0.5f

    /** Clockwise degrees the camera buffer must be rotated to be upright (from CameraX). */
    @Volatile var previewRotation: Int = 0
        private set
    @Volatile private var bufferWidth = 0
    @Volatile private var bufferHeight = 0

    /** Called (on the main thread) when the GL context was lost and recreated; camera must rebind. */
    @Volatile var onGlContextRecreated: (() -> Unit)? = null

    @Volatile private var surfaceWidth = 1080
    @Volatile private var surfaceHeight = 1920
    private val startTime = System.currentTimeMillis()

    private var pendingSurfaceRequest: SurfaceRequest? = null

    init {
        setEGLContextClientVersion(3)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** Where the (undistorted) camera frame is drawn inside this view, in view pixels. */
    fun frameRect(): FrameRect {
        val aspect = PreviewGeometry.uprightAspect(bufferWidth, bufferHeight, previewRotation)
        return PreviewGeometry.fit(surfaceWidth, surfaceHeight, aspect)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated GLES 3.0")
        var recreated = false
        synchronized(lock) {
            recreated = surfaceTexture != null
            // Anything from a previous GL context is already gone; just drop our references.
            surface?.release()
            surfaceTexture?.release()

            oesTextureId = createOesTexture()
            val st = SurfaceTexture(oesTextureId)
            st.setOnFrameAvailableListener(this)
            surfaceTexture = st
            surface = Surface(st)

            pipeline = RetroFilterPipeline()
            lutManager = LUTManager()

            pendingSurfaceRequest?.let { request ->
                pendingSurfaceRequest = null
                provideSurfaceToRequest(request)
            }
        }
        if (recreated) {
            // CameraX is still streaming into the old, released Surface. Ask for a fresh one.
            post { onGlContextRecreated?.invoke() }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        val pl = pipeline ?: return
        val lm = lutManager ?: return

        try {
            st.updateTexImage()
            st.getTransformMatrix(transformMatrix)
        } catch (e: Exception) {
            Log.w(TAG, "updateTexImage failed: ${e.message}")
            return
        }

        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val rect = frameRect()
        buildDisplayMatrix(previewRotation, isMirror)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        pl.render(
            mainTextureId = oesTextureId,
            isOesTexture = true,
            lutManager = lm,
            preset = currentPreset,
            width = rect.width,
            height = rect.height,
            timeSeconds = elapsed,
            zoom = currentZoom,
            mirror = false, // mirroring is done in display space inside displayMatrix
            texMatrix = displayMatrix,
            doubleExpTextureId = doubleExpTextureId,
            doubleExpIntensity = doubleExpIntensity,
            viewportX = rect.left,
            // GL's origin is bottom-left; the frame is centred so top == bottom margin.
            viewportY = surfaceHeight - rect.top - rect.height
        )
    }

    /**
     * displayMatrix = SurfaceTexture matrix * T, where T maps an upright on-screen coordinate to
     * the unrotated buffer coordinate: mirror (selfie) first, then rotate counter-clockwise by the
     * clockwise rotation CameraX says the buffer needs.
     */
    private fun buildDisplayMatrix(rotationDegrees: Int, mirror: Boolean) {
        Matrix.setIdentityM(orientMatrix, 0)
        Matrix.translateM(orientMatrix, 0, 0.5f, 0.5f, 0f)
        Matrix.rotateM(orientMatrix, 0, rotationDegrees.toFloat(), 0f, 0f, 1f)
        if (mirror) Matrix.scaleM(orientMatrix, 0, -1f, 1f, 1f)
        Matrix.translateM(orientMatrix, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(displayMatrix, 0, transformMatrix, 0, orientMatrix, 0)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        requestRender()
    }

    fun getSurfaceProvider(): Preview.SurfaceProvider {
        return Preview.SurfaceProvider { request ->
            synchronized(lock) {
                if (surface != null && surfaceTexture != null) {
                    provideSurfaceToRequest(request)
                } else {
                    pendingSurfaceRequest?.willNotProvideSurface()
                    pendingSurfaceRequest = request
                }
            }
        }
    }

    private fun provideSurfaceToRequest(request: SurfaceRequest) {
        val s = surface ?: return
        val st = surfaceTexture ?: return
        val mainExecutor = ContextCompat.getMainExecutor(context)

        bufferWidth = request.resolution.width
        bufferHeight = request.resolution.height
        st.setDefaultBufferSize(bufferWidth, bufferHeight)

        request.setTransformationInfoListener(mainExecutor) { info ->
            Log.i(TAG, "Preview rotation=${info.rotationDegrees} buffer=${bufferWidth}x$bufferHeight")
            previewRotation = info.rotationDegrees
            requestRender()
        }
        request.provideSurface(s, mainExecutor) { result ->
            Log.d(TAG, "Surface result: ${result.resultCode}")
        }
    }

    private fun createOesTexture(): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    fun onDestroy() {
        onGlContextRecreated = null
        synchronized(lock) {
            pendingSurfaceRequest?.willNotProvideSurface()
            pendingSurfaceRequest = null
            surface?.release()
            surfaceTexture?.release()
            surface = null
            surfaceTexture = null
        }
        queueEvent {
            pipeline?.release()
            lutManager?.release()
            if (oesTextureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            }
        }
    }
}
