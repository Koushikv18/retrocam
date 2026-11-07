package com.koushik.retrocam.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koushik.retrocam.data.model.CameraSettings
import com.koushik.retrocam.data.model.SaveFormat
import com.koushik.retrocam.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: CameraSettings,
    onUpdateSettings: (CameraSettings) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SETTINGS",
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = RetroBlack)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Image Quality & Format Section
                SectionHeader("IMAGE & EXPORT FORMAT")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveFormat.values().forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = format.displayName,
                                color = if (settings.saveFormat == format) RetroAmber else RetroWhite,
                                fontSize = 14.sp
                            )
                            RadioButton(
                                selected = settings.saveFormat == format,
                                onClick = { onUpdateSettings(settings.copy(saveFormat = format)) },
                                colors = RadioButtonDefaults.colors(selectedColor = RetroAmber)
                            )
                        }
                    }
                }

                HorizontalDivider(color = RetroDarkGray)

                // Camera Preferences
                SectionHeader("PREFERENCES")

                SettingsSwitchRow(
                    title = "Mirror Front Camera",
                    subtitle = "Saves front-facing selfie photos as mirrored",
                    checked = settings.mirrorFrontCamera,
                    onCheckedChange = { onUpdateSettings(settings.copy(mirrorFrontCamera = it)) }
                )

                SettingsSwitchRow(
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on shutter press and look selection",
                    checked = settings.hapticFeedback,
                    onCheckedChange = { onUpdateSettings(settings.copy(hapticFeedback = it)) }
                )

                SettingsSwitchRow(
                    title = "Date Stamp",
                    subtitle = "Stamp vintage timestamp onto captured photos",
                    checked = settings.dateStampEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(dateStampEnabled = it)) }
                )

                SettingsSwitchRow(
                    title = "Frames & Borders",
                    subtitle = "Apply Polaroid, film strip, and rounded borders",
                    checked = settings.frameEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(frameEnabled = it)) }
                )

                HorizontalDivider(color = RetroDarkGray)

                // About section
                SectionHeader("ABOUT RETROCAM")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "RetroCam v1.0.0",
                        color = RetroWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Native Android + CameraX + OpenGL ES 3.0\nHigh-precision 3D LUTs and procedural film simulation.",
                        color = RetroGray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = RetroAmber,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = RetroWhite, fontSize = 14.sp)
            Text(text = subtitle, color = RetroGray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RetroBlack,
                checkedTrackColor = RetroAmber
            )
        )
    }
}

