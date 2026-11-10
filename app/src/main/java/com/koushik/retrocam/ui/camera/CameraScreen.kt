package com.koushik.retrocam.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.koushik.retrocam.camera.CameraEffectSurface
import com.koushik.retrocam.camera.CameraManager
import com.koushik.retrocam.camera.PreviewGeometry
import com.koushik.retrocam.data.model.*
import com.koushik.retrocam.gl.LUTManager
import com.koushik.retrocam.gl.StillImageProcessor
import com.koushik.retrocam.ui.camera.components.*
import com.koushik.retrocam.ui.theme.*
import com.koushik.retrocam.util.CoachTip
import com.koushik.retrocam.util.DeviceProfile
import com.koushik.retrocam.util.HapticsHelper
import com.koushik.retrocam.util.ProCoach
import com.koushik.retrocam.util.SceneStats
import com.koushik.retrocam.util.MediaStoreHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    presets: List<CameraPreset>,
    settings: CameraSettings,
    onUpdateSettings: (CameraSettings) -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEdit: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val haptics = remember { HapticsHelper(context) }
    val stillProcessor = remember { StillImageProcessor() }
    val lutManager = remember { LUTManager() }

    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var effectSurface by remember { mutableStateOf<CameraEffectSurface?>(null) }

    var selectedPreset by remember {
        mutableStateOf(presets.find { it.id == settings.selectedPresetId } ?: presets.first())
    }

    var cameraMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isShooting by remember { mutableStateOf(false) }
    var showFlashOverlay by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableStateOf<Int?>(null) }
    var latestPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Double exposure state
    var doubleExpBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Pro mode state (lens range, exposure compensation, live scene analysis, coach tips)
    var zoomRange by remember { mutableStateOf(Pair(1.0f, 5.0f)) }
    var evRange by remember { mutableStateOf<Triple<Int, Int, Float>?>(null) }
    var evIndex by remember { mutableIntStateOf(0) }
    var sceneStats by remember { mutableStateOf<SceneStats?>(null) }
    var cameraEpoch by remember { mutableIntStateOf(0) }
    var dismissedTipId by remember { mutableStateOf<String?>(null) }
    var shownTip by remember { mutableStateOf<CoachTip?>(null) }

    // Release the camera, its GL surface and the video processor when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.onDestroy()
            effectSurface?.onDestroy()
        }
    }

    // Accelerometer for spirit level
    var deviceRoll by remember { mutableFloatStateOf(0f) }
    var devicePitch by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    // Calculate roll angle in degrees
                    deviceRoll = Math.toDegrees(kotlin.math.atan2(-ax.toDouble(), ay.toDouble())).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Read the real zoom/exposure range from the camera once it is bound, and listen for scene stats
    LaunchedEffect(cameraManager, cameraEpoch) {
        val cm = cameraManager ?: return@LaunchedEffect
        cm.onSceneStats = { stats -> sceneStats = stats }
        for (attempt in 0 until 25) {
            val zr = cm.zoomRange()
            if (zr != null) {
                zoomRange = zr
                evRange = cm.exposureRange()
                return@LaunchedEffect
            }
            delay(300)
        }
    }

    // Pro Coach: pick the most useful tip, debounced so it never flickers
    val rawTip: CoachTip? = if (settings.proCoach && !isShooting) {
        ProCoach.advise(
            stats = sceneStats,
            rollDeg = deviceRoll,
            zoom = zoomRatio,
            minZoom = zoomRange.first,
            maxOpticalZoom = DeviceProfile.maxOpticalZoom,
            gridOn = settings.gridEnabled,
            currentPresetId = selectedPreset.id,
            isFront = cameraManager?.isFrontCamera == true,
            isRecording = isRecording
        )
    } else null
    LaunchedEffect(rawTip?.id) {
        delay(if (rawTip == null) 400L else 700L)
        shownTip = rawTip
        if (rawTip?.id != dismissedTipId) dismissedTipId = null
    }
    val displayTip: CoachTip? =
        if (rawTip != null && rawTip.id == shownTip?.id) rawTip else shownTip

    // Load initial gallery thumbnail
    LaunchedEffect(Unit) {
        val media = MediaStoreHelper.queryGalleryMedia(context)
        latestPhotoUri = media.firstOrNull()?.uri
    }

    // Photo picker launcher for edit mode
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onNavigateToEdit(it) }
    }

    // Shutter capture trigger
    fun triggerShutter() {
        if (isShooting) return

        if (cameraMode == CameraMode.VIDEO) {
            val cm = cameraManager ?: return
            if (!isRecording) {
                isRecording = true
                haptics.vibrateShutter(settings.hapticFeedback)
                cm.startRecording(selectedPreset.name) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        isRecording = false
                    }
                }
            } else {
                cm.stopRecording()
                isRecording = false
                haptics.vibrateShutter(settings.hapticFeedback)
            }
            return
        }

        if (isRecording) return

        // Timer handling
        if (settings.timerSeconds > 0 && countdownSeconds == null) {
            coroutineScope.launch {
                for (i in settings.timerSeconds downTo 1) {
                    countdownSeconds = i
                    haptics.vibrateDialTick(settings.hapticFeedback)
                    delay(1000)
                }
                countdownSeconds = null
                executePhotoCapture(
                    cameraManager = cameraManager,
                    stillProcessor = stillProcessor,
                    lutManager = lutManager,
                    preset = selectedPreset,
                    settings = settings,
                    haptics = haptics,
                    context = context,
                    doubleExpBitmap = doubleExpBitmap,
                    onStart = {
                        isShooting = true
                        showFlashOverlay = true
                    },
                    onDoubleExpCaptured = { bmp ->
                        doubleExpBitmap = bmp
                        haptics.vibrateDoubleExposure(settings.hapticFeedback)
                        isShooting = false
                        showFlashOverlay = false
                    },
                    onComplete = { uri ->
                        isShooting = false
                        showFlashOverlay = false
                        doubleExpBitmap = null
                        latestPhotoUri = uri
                    }
                )
            }
            return
        }

        // Direct capture
        coroutineScope.launch {
            executePhotoCapture(
                cameraManager = cameraManager,
                stillProcessor = stillProcessor,
                lutManager = lutManager,
                preset = selectedPreset,
                settings = settings,
                haptics = haptics,
                context = context,
                doubleExpBitmap = doubleExpBitmap,
                onStart = {
                    isShooting = true
                    showFlashOverlay = true
                },
                onDoubleExpCaptured = { bmp ->
                    doubleExpBitmap = bmp
                    haptics.vibrateDoubleExposure(settings.hapticFeedback)
                    isShooting = false
                    showFlashOverlay = false
                },
                onComplete = { uri ->
                    isShooting = false
                    showFlashOverlay = false
                    doubleExpBitmap = null
                    latestPhotoUri = uri
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroBlack)
    ) {
        // 1. OpenGL Camera Preview Surface with Gestures
        AndroidView(
            factory = { ctx ->
                CameraEffectSurface(ctx).apply {
                    currentPreset = selectedPreset
                    currentZoom = 1.0f
                    isMirror = settings.mirrorFrontCamera && (cameraManager?.isFrontCamera == true)
                    effectSurface = this

                    val cm = CameraManager(ctx, lifecycleOwner)
                    cameraManager = cm
                    onGlContextRecreated = { cm.rebind(getSurfaceProvider()) }
                    coroutineScope.launch {
                        val started = cm.initCamera(getSurfaceProvider())
                        if (!started) {
                            Toast.makeText(ctx, "Camera could not start. Close other camera apps and reopen RetroCam.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            update = { surface ->
                surface.currentPreset = selectedPreset
                cameraManager?.setPreset(selectedPreset)
                surface.currentZoom = 1.0f
                surface.isMirror = settings.mirrorFrontCamera && (cameraManager?.isFrontCamera == true)
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        zoomRatio = (zoomRatio * zoom).coerceIn(zoomRange.first, zoomRange.second)
                        cameraManager?.setZoom(zoomRatio)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val surf = effectSurface ?: return@detectTapGestures
                        // Only taps on the actual camera frame count, mapped through the same
                        // rotation/mirroring the preview uses.
                        val hit = PreviewGeometry.touchToFrame(offset.x, offset.y, surf.frameRect())
                            ?: return@detectTapGestures
                        focusPoint = offset
                        val (sx, sy) = PreviewGeometry.displayToSensor(
                            hit.first, hit.second, surf.previewRotation, surf.isMirror
                        )
                        cameraManager?.tapToFocus(sx, sy)
                        coroutineScope.launch {
                            delay(1800)
                            focusPoint = null
                        }
                    }
                }
        )

        // 2. Viewfinder Overlay (Aspect ratio mask, live frame, grid, spirit level, countdown)
        ViewfinderOverlay(
            preset = selectedPreset,
            gridEnabled = settings.gridEnabled,
            levelEnabled = settings.levelEnabled,
            devicePitch = devicePitch,
            deviceRoll = deviceRoll,
            countdownSeconds = countdownSeconds,
            doubleExposureCount = if (doubleExpBitmap != null) 1 else 0,
            focusPoint = focusPoint,
            frameEnabled = settings.frameEnabled
        )

        // 3. Shutter Flash Overlay
        if (showFlashOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
            LaunchedEffect(Unit) {
                delay(65)
                showFlashOverlay = false
            }
        }

        // 3b. Pro Coach banner + exposure control
        AnimatedVisibility(
            visible = displayTip != null && displayTip.id != dismissedTipId,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 60.dp)
        ) {
            val tip = displayTip
            if (tip != null) {
                CoachBanner(
                    tip = tip,
                    onTry = { id ->
                        presets.find { it.id == id }?.let { p ->
                            selectedPreset = p
                            effectSurface?.currentPreset = p
                            cameraManager?.setPreset(p)
                            haptics.vibrateDialTick(settings.hapticFeedback)
                            onUpdateSettings(settings.copy(selectedPresetId = p.id))
                        }
                        dismissedTipId = tip.id
                    },
                    onDismiss = { dismissedTipId = tip.id }
                )
            }
        }

        val ev = evRange
        if (ev != null) {
            EvControl(
                evText = "EV " + "%+.1f".format(evIndex * ev.third),
                onUp = {
                    evIndex = (evIndex + 1).coerceAtMost(ev.second)
                    cameraManager?.setExposureIndex(evIndex)
                },
                onDown = {
                    evIndex = (evIndex - 1).coerceAtLeast(ev.first)
                    cameraManager?.setExposureIndex(evIndex)
                },
                onReset = {
                    evIndex = 0
                    cameraManager?.setExposureIndex(0)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            )
        }

        // 4. Top Quick Settings Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Mode
            IconButton(
                onClick = {
                    val nextFlash = when (cameraManager?.flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    cameraManager?.setFlash(nextFlash)
                    haptics.vibrateDialTick(settings.hapticFeedback)
                }
            ) {
                Icon(
                    imageVector = when (cameraManager?.flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = if (cameraManager?.flashMode != ImageCapture.FLASH_MODE_OFF) RetroAmber else RetroWhite
                )
            }

            // Timer
            IconButton(
                onClick = {
                    val nextTimer = when (settings.timerSeconds) {
                        0 -> 3
                        3 -> 5
                        5 -> 10
                        else -> 0
                    }
                    onUpdateSettings(settings.copy(timerSeconds = nextTimer))
                    haptics.vibrateDialTick(settings.hapticFeedback)
                }
            ) {
                Text(
                    text = if (settings.timerSeconds > 0) "${settings.timerSeconds}S" else "OFF",
                    color = if (settings.timerSeconds > 0) RetroAmber else RetroWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Frame / Border Toggle
            IconButton(
                onClick = {
                    onUpdateSettings(settings.copy(frameEnabled = !settings.frameEnabled))
                    haptics.vibrateDialTick(settings.hapticFeedback)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.CropPortrait,
                    contentDescription = "Frames",
                    tint = if (settings.frameEnabled) RetroAmber else RetroGray
                )
            }

            // Grid & Level
            IconButton(
                onClick = {
                    val newGrid = !settings.gridEnabled
                    onUpdateSettings(settings.copy(gridEnabled = newGrid, levelEnabled = newGrid))
                    haptics.vibrateDialTick(settings.hapticFeedback)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "Grid",
                    tint = if (settings.gridEnabled) RetroAmber else RetroWhite
                )
            }

            // Pro Coach toggle
            IconButton(
                onClick = {
                    onUpdateSettings(settings.copy(proCoach = !settings.proCoach))
                    haptics.vibrateDialTick(settings.hapticFeedback)
                }
            ) {
                Text(
                    text = "PRO",
                    color = if (settings.proCoach) RetroAmber else RetroGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Settings Screen
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = RetroWhite
                )
            }
        }

        // 5. Bottom Controls Section
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lens selector (0.6x / 1x / 2x / 3x / 5x ... limited to the camera's real zoom range)
            LensSelector(
                zoom = zoomRatio,
                minZoom = zoomRange.first,
                maxZoom = zoomRange.second,
                caption = DeviceProfile.caption,
                onSelect = { z ->
                    zoomRatio = z
                    cameraManager?.setZoom(z)
                    haptics.vibrateDialTick(settings.hapticFeedback)
                },
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Camera Mode Selector (PHOTO, VIDEO, DOUBLE EXP, BURST)
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                CameraMode.values().forEach { mode ->
                    val isSelected = cameraMode == mode
                    Text(
                        text = mode.label,
                        color = if (isSelected) RetroAmber else RetroGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable {
                                cameraMode = mode
                                haptics.vibrateDialTick(settings.hapticFeedback)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            // Horizontal Look Carousel (22 Retro Cameras)
            LookCarousel(
                presets = presets,
                selectedPreset = selectedPreset,
                onSelectPreset = { preset ->
                    selectedPreset = preset
                    effectSurface?.currentPreset = preset
                    cameraManager?.setPreset(preset)
                    haptics.vibrateDialTick(settings.hapticFeedback)
                    onUpdateSettings(settings.copy(selectedPresetId = preset.id))
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Main Controls Row: Gallery Thumbnail | Shutter Button | Camera Flip / Import
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail / Import Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RetroDarkSurface)
                        .border(1.5.dp, RetroDarkGray, RoundedCornerShape(12.dp))
                        .clickable(onClick = onNavigateToGallery)
                ) {
                    if (latestPhotoUri != null) {
                        AsyncImage(
                            model = latestPhotoUri,
                            contentDescription = "Gallery",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = RetroWhite.copy(alpha = 0.7f)
                        )
                    }
                }

                // Shutter Button
                ShutterButton(
                    cameraMode = cameraMode,
                    isRecording = isRecording,
                    onClick = { triggerShutter() }
                )

                // Flip Camera Button
                var flipAngle by remember { mutableFloatStateOf(0f) }
                val animatedAngle by animateFloatAsState(targetValue = flipAngle, label = "flipAnim")

                IconButton(
                    onClick = {
                        flipAngle += 180f
                        haptics.vibrateDialTick(settings.hapticFeedback)
                        val surf = effectSurface ?: return@IconButton
                        cameraManager?.switchCamera(surf.getSurfaceProvider())
                        zoomRatio = 1.0f
                        evIndex = 0
                        sceneStats = null
                        zoomRange = Pair(1.0f, 5.0f)
                        cameraEpoch++
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(RetroDarkSurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = RetroWhite,
                        modifier = Modifier.rotate(animatedAngle)
                    )
                }
            }
        }
    }
}

private suspend fun executePhotoCapture(
    cameraManager: CameraManager?,
    stillProcessor: StillImageProcessor,
    lutManager: LUTManager,
    preset: CameraPreset,
    settings: CameraSettings,
    haptics: HapticsHelper,
    context: Context,
    doubleExpBitmap: Bitmap?,
    onStart: () -> Unit,
    onDoubleExpCaptured: (Bitmap) -> Unit,
    onComplete: (Uri?) -> Unit
) {
    val cm = cameraManager ?: return
    onStart()
    haptics.vibrateShutter(settings.hapticFeedback)

    try {
        val (rawBitmap, rotation) = cm.capturePhoto()

        if (doubleExpBitmap == null && preset.id == "half_frame") {
            // First exposure of double exposure / diptych
            onDoubleExpCaptured(rawBitmap)
            return
        }

        // Process through full-resolution GLES 3.0 filter pipeline
        val processed = stillProcessor.processStillImage(
            inputBitmap = rawBitmap,
            rotationDegrees = rotation,
            preset = preset,
            settings = settings,
            lutManager = lutManager,
            doubleExpBitmap = doubleExpBitmap,
            mirrorHorizontally = settings.mirrorFrontCamera && cm.isFrontCamera
        )

        // Save high-resolution still to MediaStore Pictures/RetroCam
        val savedUri = MediaStoreHelper.saveImage(
            context = context,
            bitmap = processed,
            format = settings.saveFormat,
            presetName = preset.name
        )

        onComplete(savedUri)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        onComplete(null)
    }
}

