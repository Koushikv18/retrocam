package com.koushik.retrocam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.koushik.retrocam.data.model.CameraPreset
import com.koushik.retrocam.gl.LUTManager
import com.koushik.retrocam.gl.RetroVideoEffect
import com.koushik.retrocam.gl.RetroVideoProcessor
import com.koushik.retrocam.util.MediaStoreHelper
import com.koushik.retrocam.util.SceneStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val TAG = "CameraManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Receives smoothed live scene statistics on the main thread (null if analysis could not be bound). */
    var onSceneStats: ((SceneStats) -> Unit)? = null
    var analysisActive: Boolean = false
        private set

    private val lutManager = LUTManager()
    val videoProcessor = RetroVideoProcessor(lutManager)
    private val videoEffect = RetroVideoEffect(videoProcessor)

    var isFrontCamera: Boolean = false
        private set

    var flashMode: Int = ImageCapture.FLASH_MODE_OFF
        private set

    var isTorchOn: Boolean = false
        private set

    /** Human-readable reason if the camera could not be started (shown on screen). */
    var lastError: String? = null
        private set

    suspend fun initCamera(
        surfaceProvider: Preview.SurfaceProvider
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            val provider = suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            cameraProvider = provider
            val ok = bindCameraUseCases(surfaceProvider)
            if (ok) lastError = null
            ok
        } catch (e: Exception) {
            Log.e(TAG, "initCamera failed", e)
            lastError = "Camera init failed: ${e.message}"
            false
        }
    }

    /** Re-issue the preview request (e.g. after the GL context was recreated). */
    fun rebind(surfaceProvider: Preview.SurfaceProvider): Boolean = bindCameraUseCases(surfaceProvider)

    fun switchCamera(surfaceProvider: Preview.SurfaceProvider) {
        isFrontCamera = !isFrontCamera
        bindCameraUseCases(surfaceProvider)
    }

    fun setFlash(mode: Int) {
        flashMode = mode
        imageCapture?.flashMode = mode
    }

    fun toggleTorch(): Boolean {
        isTorchOn = !isTorchOn
        camera?.cameraControl?.enableTorch(isTorchOn)
        return isTorchOn
    }

    /** Min/max zoom of the active camera (S25 Ultra reports about 0.6x to 10x+). */
    fun zoomRange(): Pair<Float, Float>? {
        val zs = camera?.cameraInfo?.zoomState?.value ?: return null
        return Pair(zs.minZoomRatio, zs.maxZoomRatio)
    }

    /** Exposure compensation as (minIndex, maxIndex, evPerStep), or null when unsupported. */
    fun exposureRange(): Triple<Int, Int, Float>? {
        val es = camera?.cameraInfo?.exposureState ?: return null
        if (!es.isExposureCompensationSupported) return null
        val r = es.exposureCompensationRange
        if (r.upper <= r.lower) return null
        return Triple(r.lower, r.upper, es.exposureCompensationStep.toFloat())
    }

    fun setZoom(zoomRatio: Float) {
        val range = zoomRange() ?: Pair(1.0f, 5.0f)
        camera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(range.first, range.second))
    }

    /** [x],[y] are 0..1 in sensor orientation (see PreviewGeometry.displayToSensor). */
    fun tapToFocus(x: Float, y: Float) {
        val cam = camera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun setExposureIndex(index: Int) {
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    private fun bindCameraUseCases(surfaceProvider: Preview.SurfaceProvider): Boolean {
        val provider = cameraProvider ?: return false
        provider.unbindAll()

        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
        preview.setSurfaceProvider(surfaceProvider)
        this.preview = preview

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setFlashMode(flashMode)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor, SceneAnalyzer { stats ->
            mainExecutor.execute { onSceneStats?.invoke(stats) }
        })

        // Try the richest combination first, then degrade gracefully on devices that cannot do it.
        // Each plan: (video effect, live analysis, video recording, photo capture)
        val plans = listOf(
            BindPlan(effect = true, analysis = true, video = true, image = true),
            BindPlan(effect = true, analysis = false, video = true, image = true),
            BindPlan(effect = false, analysis = true, video = true, image = true),
            BindPlan(effect = false, analysis = false, video = true, image = true),
            BindPlan(effect = false, analysis = false, video = false, image = true),   // photo only
            BindPlan(effect = false, analysis = false, video = false, image = false)   // preview only
        )
        var lastMessage: String? = null
        for (plan in plans) {
            try {
                val group = UseCaseGroup.Builder().addUseCase(preview)
                if (plan.image) group.addUseCase(imageCapture!!)
                if (plan.video) group.addUseCase(videoCapture!!)
                if (plan.analysis) group.addUseCase(analysis)
                if (plan.effect) group.addEffect(videoEffect)
                camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, group.build())
                analysisActive = plan.analysis
                if (!plan.video) videoCapture = null
                if (!plan.image) imageCapture = null
                Log.i(TAG, "Bound camera: $plan")
                return true
            } catch (e: Exception) {
                lastMessage = e.message
                Log.w(TAG, "Bind attempt failed ($plan): ${e.message}")
                provider.unbindAll()
            }
        }
        lastError = "Could not start the camera: ${lastMessage ?: "unknown error"}"
        Log.e(TAG, lastError ?: "bind failed")
        return false
    }

    private data class BindPlan(val effect: Boolean, val analysis: Boolean, val video: Boolean, val image: Boolean)

    suspend fun capturePhoto(): Pair<Bitmap, Int> = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resumeWithException(IllegalStateException("ImageCapture is not initialized"))
            return@suspendCancellableCoroutine
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        // BitmapFactory ignores EXIF, so the JPEG's own orientation tag is the
                        // ground truth for how the pixels must be turned upright.
                        val rotation = jpegRotationDegrees(bytes, image.imageInfo.rotationDegrees)
                        Log.i(TAG, "Capture ${image.width}x${image.height} exif/info rotation=$rotation")
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        image.close()

                        if (bitmap != null) {
                            cont.resume(Pair(bitmap, rotation))
                        } else {
                            cont.resumeWithException(RuntimeException("Failed to decode captured image"))
                        }
                    } catch (e: Exception) {
                        image.close()
                        cont.resumeWithException(e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    cont.resumeWithException(exception)
                }
            }
        )
    }

    private fun jpegRotationDegrees(jpeg: ByteArray, fallback: Int): Int {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(jpeg))
            if (exif.getAttribute(ExifInterface.TAG_ORIENTATION) != null) exif.rotationDegrees else fallback
        } catch (e: Exception) {
            fallback
        }
    }

    fun startRecording(
        presetName: String,
        onEvent: (VideoRecordEvent) -> Unit
    ) {
        val vc = videoCapture ?: return
        val tempFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
        val fileOutputOptions = FileOutputOptions.Builder(tempFile).build()

        activeRecording = vc.output
            .prepareRecording(context, fileOutputOptions)
            .apply {
                try {
                    withAudioEnabled()
                } catch (e: SecurityException) {
                    Log.w(TAG, "Audio permission not granted for video: ${e.message}")
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (!event.hasError()) {
                        // Move recorded video to MediaStore
                        cameraExecutor.execute {
                            try {
                                val (values, _) = kotlinx.coroutines.runBlocking {
                                    MediaStoreHelper.createVideoContentValues(presetName)
                                }
                                val uri = context.contentResolver.insert(
                                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    values
                                )
                                if (uri != null) {
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        tempFile.inputStream().use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                    tempFile.delete()
                                    Log.i(TAG, "Saved video to MediaStore: $uri")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error saving video to MediaStore: ${e.message}")
                            }
                        }
                    }
                }
                onEvent(event)
            }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun setPreset(preset: CameraPreset) {
        videoProcessor.currentPreset = preset
    }

    fun onDestroy() {
        cameraExecutor.shutdown()
        analysisExecutor.shutdown()
        cameraProvider?.unbindAll()
        videoProcessor.release()
        lutManager.release()
    }
}

