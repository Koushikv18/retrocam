package com.koushik.retrocam.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
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

    @Volatile
    var currentPreset: CameraPreset = CameraPreset(id = "cpm35", name = "CPM35")
    @Volatile
    var currentZoom: Float = 1.0f
    @Volatile
    var isMirror: Boolean = false
    @Volatile
    var doubleExpTextureId: Int? = null
    @Volatile
    var doubleExpIntensity: Float = 0.5f

    private var surfaceWidth = 1080
    private var surfaceHeight = 1920
    private val startTime = System.currentTimeMillis()

    private var pendingSurfaceRequest: SurfaceRequest? = null

    init {
        setEGLContextClientVersion(3)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated GLES 3.0")
        oesTextureId = createOesTexture()
        val st = SurfaceTexture(oesTextureId)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st
        surface = Surface(st)

        pipeline = RetroFilterPipeline()
        lutManager = LUTManager()

        // Provide surface to pending CameraX request if any
        pendingSurfaceRequest?.let { request ->
            provideSurfaceToRequest(request)
            pendingSurfaceRequest = null
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
            return
        }

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
            doubleExpTextureId = doubleExpTextureId,
            doubleExpIntensity = doubleExpIntensity
        )
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        requestRender()
    }

    fun getSurfaceProvider(): Preview.SurfaceProvider {
        return Preview.SurfaceProvider { request ->
            if (surface != null && surfaceTexture != null) {
                provideSurfaceToRequest(request)
            } else {
                pendingSurfaceRequest = request
            }
        }
    }

    private fun provideSurfaceToRequest(request: SurfaceRequest) {
        val s = surface ?: return
        val st = surfaceTexture ?: return
        st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
        request.provideSurface(s, Executors.newSingleThreadExecutor()) { result ->
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
    }
}

