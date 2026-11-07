package com.koushik.retrocam.ui.camera.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.koushik.retrocam.data.model.CameraMode
import com.koushik.retrocam.ui.theme.*

@Composable
fun ShutterButton(
    cameraMode: CameraMode,
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "shutterScale"
    )

    val innerColor by animateColorAsState(
        targetValue = when {
            isRecording -> RetroRed
            cameraMode == CameraMode.VIDEO -> RetroRed
            cameraMode == CameraMode.DOUBLE_EXPOSURE -> RetroCyan
            else -> RetroAmber
        },
        label = "innerColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(84.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(RetroDarkSurface)
            .border(4.dp, RetroWhite.copy(alpha = 0.85f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        if (isRecording) {
            // Square stop button during recording
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(RetroRed)
            )
        } else {
            // Circular mechanical shutter button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(innerColor)
                    .border(2.dp, RetroBlack.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

