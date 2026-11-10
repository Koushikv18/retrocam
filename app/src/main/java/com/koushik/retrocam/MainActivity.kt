package com.koushik.retrocam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.koushik.retrocam.data.model.CameraSettings
import com.koushik.retrocam.ui.camera.CameraScreen
import com.koushik.retrocam.ui.edit.EditScreen
import com.koushik.retrocam.ui.gallery.GalleryScreen
import com.koushik.retrocam.ui.settings.SettingsScreen
import com.koushik.retrocam.ui.theme.RetroAmber
import com.koushik.retrocam.ui.theme.RetroBlack
import com.koushik.retrocam.ui.theme.RetroCamTheme
import com.koushik.retrocam.ui.theme.RetroGray
import com.koushik.retrocam.ui.theme.RetroWhite
import kotlinx.coroutines.launch

sealed class Screen {
    data object Camera : Screen()
    data object Gallery : Screen()
    data object Settings : Screen()
    data class Edit(val imageUri: Uri) : Screen()
}

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)
    private var shouldShowRationale by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        shouldShowRationale = !hasCameraPermission && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        val app = application as RetroCamApp
        val presetRepo = app.presetRepository
        val settingsRepo = app.settingsRepository

        setContent {
            RetroCamTheme {
                val coroutineScope = rememberCoroutineScope()
                val settings by settingsRepo.settingsFlow.collectAsStateWithLifecycle(
                    initialValue = CameraSettings()
                )
                val presets = remember { presetRepo.getAllPresets() }

                var currentScreen by remember { mutableStateOf<Screen>(Screen.Camera) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RetroBlack
                ) {
                    if (!hasCameraPermission) {
                        PermissionRequiredScreen(
                            shouldOpenSettings = shouldShowRationale,
                            onRequestPermission = { checkAndRequestPermissions() }
                        )
                    } else {
                        when (val screen = currentScreen) {
                            is Screen.Camera -> {
                                CameraScreen(
                                    presets = presets,
                                    settings = settings,
                                    onUpdateSettings = { newSettings ->
                                        coroutineScope.launch {
                                            settingsRepo.updatePresetId(newSettings.selectedPresetId)
                                            settingsRepo.toggleDateStamp(newSettings.dateStampEnabled)
                                            settingsRepo.toggleGrid(newSettings.gridEnabled)
                                            settingsRepo.toggleLevel(newSettings.levelEnabled)
                                            settingsRepo.setTimerSeconds(newSettings.timerSeconds)
                                            settingsRepo.toggleMirrorFront(newSettings.mirrorFrontCamera)
                                            settingsRepo.toggleShutterSound(newSettings.shutterSound)
                                            settingsRepo.toggleHaptics(newSettings.hapticFeedback)
                                            settingsRepo.setSaveFormat(newSettings.saveFormat)
                                            settingsRepo.toggleFrame(newSettings.frameEnabled)
                                            settingsRepo.toggleProCoach(newSettings.proCoach)
                                        }
                                    },
                                    onNavigateToGallery = { currentScreen = Screen.Gallery },
                                    onNavigateToSettings = { currentScreen = Screen.Settings },
                                    onNavigateToEdit = { uri -> currentScreen = Screen.Edit(uri) }
                                )
                            }
                            is Screen.Gallery -> {
                                GalleryScreen(
                                    onNavigateBack = { currentScreen = Screen.Camera },
                                    onNavigateToEdit = { uri -> currentScreen = Screen.Edit(uri) }
                                )
                            }
                            is Screen.Settings -> {
                                SettingsScreen(
                                    settings = settings,
                                    onUpdateSettings = { newSettings ->
                                        coroutineScope.launch {
                                            settingsRepo.updatePresetId(newSettings.selectedPresetId)
                                            settingsRepo.toggleDateStamp(newSettings.dateStampEnabled)
                                            settingsRepo.toggleGrid(newSettings.gridEnabled)
                                            settingsRepo.toggleLevel(newSettings.levelEnabled)
                                            settingsRepo.setTimerSeconds(newSettings.timerSeconds)
                                            settingsRepo.toggleMirrorFront(newSettings.mirrorFrontCamera)
                                            settingsRepo.toggleShutterSound(newSettings.shutterSound)
                                            settingsRepo.toggleHaptics(newSettings.hapticFeedback)
                                            settingsRepo.setSaveFormat(newSettings.saveFormat)
                                            settingsRepo.toggleFrame(newSettings.frameEnabled)
                                            settingsRepo.toggleProCoach(newSettings.proCoach)
                                        }
                                    },
                                    onNavigateBack = { currentScreen = Screen.Camera }
                                )
                            }
                            is Screen.Edit -> {
                                EditScreen(
                                    imageUri = screen.imageUri,
                                    presets = presets,
                                    onNavigateBack = { currentScreen = Screen.Camera }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted permissions from system Settings while we were paused.
        hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        val hasMediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasCameraPermission || !hasAudioPermission || !hasMediaPermission) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    shouldOpenSettings: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(RetroBlack)
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "RETROCAM",
                color = RetroAmber,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera and microphone permissions are required to shoot retro photos and videos with live film filters.",
                color = RetroGray,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (shouldOpenSettings) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } else {
                        onRequestPermission()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RetroAmber)
            ) {
                Text(
                    text = if (shouldOpenSettings) "OPEN SETTINGS" else "GRANT PERMISSION",
                    color = RetroBlack,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

