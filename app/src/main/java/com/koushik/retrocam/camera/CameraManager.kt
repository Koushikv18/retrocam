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
import androidx.lifecycle.LifecycleOwner
import com.koushik.retrocam.data.model.CameraPreset
import com.koushik.retrocam.gl.LUTManager
import com.koushik.retrocam.gl.RetroVideoEffect
import com.koushik.retrocam.gl.RetroVideoProcessor
import com.koushik.retrocam.util.MediaStoreHelper
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
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val lutManager = LUTManager()
    val videoProcessor = RetroVideoProcessor(lutManager)
    private val videoEffect = RetroVideoEffect(videoProcessor)

    var isFrontCamera: Boolean = false
        private set

    var flashMode: Int = ImageCapture.FLASH_MODE_OFF
        private set

    var isTorchOn: Boolean = false
        private set

    suspend fun initCamera(
        surfaceProvider: Preview.SurfaceProvider
    ): Boolean = withContext(Dispatchers.Main) {
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
        bindCameraUseCases(surfaceProvider)
    }

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

    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(1.0f, 5.0f))
    }

    fun tapToFocus(x: Float, y: Float, viewWidth: Float, viewHeight: Float) {
        val cam = camera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight)
        val point = factory.createPoint(x, y)
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

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setFlashMode(flashMode)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture!!)
                .addUseCase(videoCapture!!)
                .addEffect(videoEffect)
                .build()

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                useCaseGroup
            )
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Binding with video effect failed, falling back to direct use cases: ${e.message}")
            try {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
                return true
            } catch (e2: Exception) {
                Log.e(TAG, "Use case binding failed", e2)
                return false
            }
        }
    }

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
                        val rotation = image.imageInfo.rotationDegrees
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
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
        cameraProvider?.unbindAll()
        videoProcessor.release()
        lutManager.release()
    }
}

