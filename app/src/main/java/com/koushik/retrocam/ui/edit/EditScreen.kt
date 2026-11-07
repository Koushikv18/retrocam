package com.koushik.retrocam.ui.edit

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.koushik.retrocam.data.model.CameraPreset
import com.koushik.retrocam.data.model.CameraSettings
import com.koushik.retrocam.gl.LUTManager
import com.koushik.retrocam.gl.StillImageProcessor
import com.koushik.retrocam.ui.camera.components.LookCarousel
import com.koushik.retrocam.ui.theme.*
import com.koushik.retrocam.util.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    imageUri: Uri,
    presets: List<CameraPreset>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val stillProcessor = remember { StillImageProcessor() }
    val lutManager = remember { LUTManager() }

    var selectedPreset by remember { mutableStateOf(presets.first()) }
    var intensity by remember { mutableFloatStateOf(1.0f) }
    var warmth by remember { mutableFloatStateOf(0.0f) }
    var grain by remember { mutableFloatStateOf(selectedPreset.grain) }
    var fade by remember { mutableFloatStateOf(selectedPreset.fade) }
    var dateStampEnabled by remember { mutableStateOf(selectedPreset.dateStamp.enabled) }
    var frameEnabled by remember { mutableStateOf(selectedPreset.frame != "none") }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Load original bitmap
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, imageUri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                }
                originalBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Update preview when parameters change
    LaunchedEffect(originalBitmap, selectedPreset, intensity, warmth, grain, fade, dateStampEnabled, frameEnabled) {
        val orig = originalBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val adjustedPreset = selectedPreset.copy(
                temperature = selectedPreset.temperature + warmth,
                grain = grain,
                fade = fade,
                contrast = (selectedPreset.contrast - 1.0f) * intensity + 1.0f,
                saturation = (selectedPreset.saturation - 1.0f) * intensity + 1.0f,
                toneAmt = selectedPreset.toneAmt * intensity
            )
            val settings = CameraSettings(
                dateStampEnabled = dateStampEnabled,
                frameEnabled = frameEnabled
            )

            // Downscaled preview for fast responsiveness
            val previewScale = 1080f / maxOf(orig.width, orig.height)
            val scaledBmp = if (previewScale < 1.0f) {
                Bitmap.createScaledBitmap(orig, (orig.width * previewScale).toInt(), (orig.height * previewScale).toInt(), true)
            } else orig

            val result = stillProcessor.processStillImage(
                inputBitmap = scaledBmp,
                rotationDegrees = 0,
                preset = adjustedPreset,
                settings = settings,
                lutManager = lutManager
            )
            previewBitmap = result
        }
    }

    fun exportPhoto() {
        val orig = originalBitmap ?: return
        isProcessing = true
        coroutineScope.launch {
            try {
                val adjustedPreset = selectedPreset.copy(
                    temperature = selectedPreset.temperature + warmth,
                    grain = grain,
                    fade = fade,
                    contrast = (selectedPreset.contrast - 1.0f) * intensity + 1.0f,
                    saturation = (selectedPreset.saturation - 1.0f) * intensity + 1.0f,
                    toneAmt = selectedPreset.toneAmt * intensity
                )
                val settings = CameraSettings(
                    dateStampEnabled = dateStampEnabled,
                    frameEnabled = frameEnabled
                )

                // Process full-resolution image
                val fullResult = stillProcessor.processStillImage(
                    inputBitmap = orig,
                    rotationDegrees = 0,
                    preset = adjustedPreset,
                    settings = settings,
                    lutManager = lutManager
                )

                val uri = MediaStoreHelper.saveImage(
                    context = context,
                    bitmap = fullResult,
                    presetName = "${selectedPreset.name}_Edit"
                )

                if (uri != null) {
                    Toast.makeText(context, "Saved to Pictures/RetroCam", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                } else {
                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "EDIT PHOTO",
                        color = RetroWhite,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RetroWhite)
                    }
                },
                actions = {
                    TextButton(onClick = { exportPhoto() }, enabled = !isProcessing) {
                        Text(
                            text = if (isProcessing) "SAVING..." else "EXPORT",
                            color = RetroAmber,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = RetroBlack)
            )

            // Preview Area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                if (previewBitmap != null) {
                    AsyncImage(
                        model = previewBitmap,
                        contentDescription = "Edited Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = RetroAmber)
                }
            }

            // Controls & Adjustments
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetroDarkSurface)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Look Carousel
                LookCarousel(
                    presets = presets,
                    selectedPreset = selectedPreset,
                    onSelectPreset = {
                        selectedPreset = it
                        grain = it.grain
                        fade = it.fade
                        dateStampEnabled = it.dateStamp.enabled
                        frameEnabled = it.frame != "none"
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Slider: Intensity
                Text(
                    text = "LOOK INTENSITY: ${(intensity * 100).toInt()}%",
                    color = RetroGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = RetroAmber, activeTrackColor = RetroAmber)
                )

                // Slider: Warmth
                Text(
                    text = "WARMTH: ${if (warmth > 0) "+" else ""}${(warmth * 100).toInt()}",
                    color = RetroGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = warmth,
                    onValueChange = { warmth = it },
                    valueRange = -0.3f..0.3f,
                    colors = SliderDefaults.colors(thumbColor = RetroAmber, activeTrackColor = RetroAmber)
                )

                // Slider: Grain
                Text(
                    text = "FILM GRAIN: ${(grain * 100).toInt()}%",
                    color = RetroGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = grain,
                    onValueChange = { grain = it },
                    valueRange = 0.0f..0.35f,
                    colors = SliderDefaults.colors(thumbColor = RetroAmber, activeTrackColor = RetroAmber)
                )

                // Switches: Date Stamp & Frame
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = dateStampEnabled,
                            onCheckedChange = { dateStampEnabled = it },
                            colors = CheckboxDefaults.colors(checkedColor = RetroAmber)
                        )
                        Text("DATE STAMP", color = RetroWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = frameEnabled,
                            onCheckedChange = { frameEnabled = it },
                            colors = CheckboxDefaults.colors(checkedColor = RetroAmber)
                        )
                        Text("FRAME", color = RetroWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        if (isProcessing) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(RetroBlack.copy(alpha = 0.65f))
            ) {
                CircularProgressIndicator(color = RetroAmber)
            }
        }
    }
}

