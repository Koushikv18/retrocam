package com.koushik.retrocam.ui.camera.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koushik.retrocam.data.model.CameraPreset
import com.koushik.retrocam.ui.theme.*

@Composable
fun ViewfinderOverlay(
    preset: CameraPreset,
    gridEnabled: Boolean,
    levelEnabled: Boolean,
    devicePitch: Float,
    deviceRoll: Float,
    countdownSeconds: Int?,
    doubleExposureCount: Int,
    focusPoint: Offset?,
    frameEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1. Aspect Ratio Mask and Frames
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // Calculate active viewport based on preset aspect ratio
            val targetRatio = preset.captureAspect
            val viewRatio = canvasW / canvasH

            val (cropW, cropH) = if (viewRatio > targetRatio) {
                Pair(canvasH * targetRatio, canvasH)
            } else {
                Pair(canvasW, canvasW / targetRatio)
            }

            val left = (canvasW - cropW) / 2f
            val top = (canvasH - cropH) / 2f
            val right = left + cropW
            val bottom = top + cropH

            // Letterbox masks
            val maskColor = Color(0xCC050505)
            if (top > 0) {
                drawRect(maskColor, Offset(0f, 0f), Size(canvasW, top))
                drawRect(maskColor, Offset(0f, bottom), Size(canvasW, canvasH - bottom))
            }
            if (left > 0) {
                drawRect(maskColor, Offset(0f, 0f), Size(left, canvasH))
                drawRect(maskColor, Offset(right, 0f), Size(canvasW - right, canvasH))
            }

            // Live Polaroid frame preview
            if (frameEnabled && preset.frame == "polaroid") {
                val side = cropW * 0.055f
                val chin = cropW * 0.22f
                val frameColor = Color(0xF0F5F2EB)

                // Top, left, right border
                drawRect(frameColor, Offset(left, top), Size(cropW, side))
                drawRect(frameColor, Offset(left, top), Size(side, cropH))
                drawRect(frameColor, Offset(right - side, top), Size(side, cropH))
                // Bottom chin
                drawRect(frameColor, Offset(left, bottom - chin), Size(cropW, chin))
            }

            // Live Film Strip sprockets preview
            if (frameEnabled && preset.frame == "film_strip") {
                val border = cropH * 0.08f
                val stripColor = Color(0xF00E0E0E)
                drawRect(stripColor, Offset(left, top), Size(cropW, border))
                drawRect(stripColor, Offset(left, bottom - border), Size(cropW, border))
            }

            // 2. Rule-of-Thirds Grid
            if (gridEnabled) {
                val gridStroke = 1.dp.toPx()
                val gridColor = Color(0x55FFFFFF)

                val x1 = left + cropW / 3f
                val x2 = left + 2f * cropW / 3f
                val y1 = top + cropH / 3f
                val y2 = top + 2f * cropH / 3f

                drawLine(gridColor, Offset(x1, top), Offset(x1, bottom), gridStroke)
                drawLine(gridColor, Offset(x2, top), Offset(x2, bottom), gridStroke)
                drawLine(gridColor, Offset(left, y1), Offset(right, y1), gridStroke)
                drawLine(gridColor, Offset(left, y2), Offset(right, y2), gridStroke)
            }

            // 3. Spirit Level (Horizon indicator)
            if (levelEnabled) {
                val centerX = canvasW / 2f
                val centerY = canvasH / 2f
                val isLevel = kotlin.math.abs(deviceRoll) < 1.0f
                val levelColor = if (isLevel) RetroGreen else RetroAmber.copy(alpha = 0.8f)

                val lineLen = 80.dp.toPx()
                val angleRad = Math.toRadians(deviceRoll.toDouble())
                val dx = (kotlin.math.cos(angleRad) * lineLen / 2).toFloat()
                val dy = (kotlin.math.sin(angleRad) * lineLen / 2).toFloat()

                drawLine(
                    levelColor,
                    Offset(centerX - dx, centerY - dy),
                    Offset(centerX + dx, centerY + dy),
                    strokeWidth = 2.dp.toPx()
                )
                // Center reticle
                drawCircle(levelColor, radius = 3.dp.toPx(), center = Offset(centerX, centerY))
            }
        }

        // 4. Tap-To-Focus Square Indicator
        focusPoint?.let { pt ->
            Box(
                modifier = Modifier
                    .offset { IntOffset((pt.x - 30.dp.toPx()).toInt(), (pt.y - 30.dp.toPx()).toInt()) }
                    .size(60.dp)
                    .border(1.5.dp, RetroAmber, RoundedCornerShape(4.dp))
            )
        }

        // 5. Large Countdown Timer (3, 2, 1)
        if (countdownSeconds != null && countdownSeconds > 0) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1.2f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                label = "countdownPulse"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "$countdownSeconds",
                    color = RetroAmberGlow,
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.scale(scale)
                )
            }
        }

        // 6. Double Exposure Banner
        if (doubleExposureCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(RetroCyan.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "EXPOSURE 1/2 CAPTURED • SHOOT SECOND",
                    color = RetroBlack,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

