package com.koushik.retrocam.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.koushik.retrocam.data.model.CameraPreset
import com.koushik.retrocam.gl.LUTManager
import com.koushik.retrocam.gl.RetroFilterPipeline
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

/**
 * Live camera preview rendered through the retro filter shader.
 *
 * The view is sized to the aspect ratio of the saved photo. The camera frame is
 * centre-cropped ("cover") into it, so what you see is exactly what gets captured.
 */
class CameraEffectSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val TAG = "CameraEffectSurface"

    private var oesTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    private var pipeline: RetroFilterPipeline? = null
    private var lutManager: LUTManager? = null

    private val transformMatrix = FloatArray(16)
    private val lock = Any()
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile var currentPreset: CameraPreset = CameraPreset(id = "cpm35", name = "CPM35")
    @Volatile var currentZoom: Float = 1.0f
    @Volatile var isMirror: Boolean = false
    @Volatile var doubleExpTextureId: Int? = null
    @Volatile var doubleExpIntensity: Float = 0.5f

    /** Number of frames drawn so far (0 means the camera never delivered anything). */
    @Volatile var framesRendered: Long = 0L
        private set

    /** Visible fraction of the camera frame (display space); used to map taps to focus points. */
    @Volatile var cropX: Float = 1f
        private set
    @Volatile var cropY: Float = 1f
        private set

    /** Called on the main thread if the GL context had to be rebuilt; the camera must be rebound. */
    @Volatile var onGlRecreated: (() -> Unit)? = null

    @Volatile private var bufferWidth = 0
    @Volatile private var bufferHeight = 0
    private var surfaceWidth = 1080
    private var surfaceHeight = 1440
    private val startTime = System.currentTimeMillis()

    private var pendingSurfaceRequest: SurfaceRequest? = null

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated GLES 3.0")
        val recreated: Boolean
        synchronized(lock) {
            recreated = surfaceTexture != null
            if (recreated) {
                // Old GL objects died with the previous context.
                try { surface?.release() } catch (e: Exception) { }
                try { surfaceTexture?.release() } catch (e: Exception) { }
            }
            oesTextureId = createOesTexture()
            val st = SurfaceTexture(oesTextureId)
            st.setOnFrameAvailableListener(this)
            surfaceTexture = st
            surface = Surface(st)

            pipeline = RetroFilterPipeline()
            lutManager = LUTManager()

            pendingSurfaceRequest?.let { request ->
                provideSurfaceToRequest(request)
                pendingSurfaceRequest = null
            }
        }
        if (recreated) {
            Log.w(TAG, "GL context was recreated, asking owner to rebind the camera")
            mainHandler.post { onGlRecreated?.invoke() }
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

        // Work out how much of the camera frame fits (centre "cover" crop) in display space.
        val rotated = abs(transformMatrix[0]) < abs(transformMatrix[4])
        val bw = bufferWidth
        val bh = bufferHeight
        val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight.coerceAtLeast(1)
        val contentAspect = if (bw > 0 && bh > 0) {
            if (rotated) bh.toFloat() / bw else bw.toFloat() / bh
        } else surfaceAspect
        val cx = if (contentAspect > surfaceAspect) surfaceAspect / contentAspect else 1f
        val cy = if (contentAspect < surfaceAspect) contentAspect / surfaceAspect else 1f
        cropX = cx
        cropY = cy

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f

        pl.render(
            mainTextureId = oesTextureId,
            isOesTexture = true,
            lutManager = lm,
            preset = currentPreset,
            width = surfaceWidth,
            height = surfaceHeight,
            timeSeconds = elapsed,
            zoom = currentZoom,
            mirror = isMirror,
            texMatrix = transformMatrix,
            cropX = cx,
            cropY = cy,
            doubleExpTextureId = doubleExpTextureId,
            doubleExpIntensity = doubleExpIntensity
        )
        framesRendered++
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
        bufferWidth = request.resolution.width
        bufferHeight = request.resolution.height
        st.setDefaultBufferSize(bufferWidth, bufferHeight)
        Log.i(TAG, "Providing preview surface ${bufferWidth}x${bufferHeight}")
        request.provideSurface(s, callbackExecutor) { result ->
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
        queueEvent {
            pipeline?.release()
            lutManager?.release()
            if (oesTextureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            }
            surface?.release()
            surfaceTexture?.release()
        }
        callbackExecutor.shutdown()
    }
}
