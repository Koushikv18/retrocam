package com.koushik.retrocam.ui.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koushik.retrocam.ui.theme.*
import com.koushik.retrocam.util.CoachTip
import com.koushik.retrocam.util.ProCoach
import com.koushik.retrocam.util.TipLevel
import kotlin.math.abs

private fun stopLabel(s: Float): String =
    if (abs(s - s.toInt()) < 0.01f) "${s.toInt()}" else "$s"

private fun zoomLabel(z: Float): String {
    val tenths = Math.round(z * 10f) / 10f
    return if (abs(tenths - tenths.toInt()) < 0.01f) "${tenths.toInt()}x" else "${tenths}x"
}

/** Row of lens shortcuts (0.6x / 1x / 2x / 3x / 5x / 10x) limited to what the camera supports. */
@Composable
fun LensSelector(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    caption: String?,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val stops = remember(minZoom, maxZoom) { ProCoach.lensStops(minZoom, maxZoom) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (caption != null) {
            Text(
                text = caption,
                color = RetroGray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = zoomLabel(zoom),
                color = RetroAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(44.dp)
            )
            stops.forEach { stop ->
                val selected = abs(stop - zoom) < 0.08f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (selected) RetroAmber else Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, if (selected) RetroAmber else RetroDarkGray, CircleShape)
                        .clickable { onSelect(stop) }
                ) {
                    Text(
                        text = stopLabel(stop),
                        color = if (selected) RetroBlack else RetroWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/** Small vertical exposure-compensation control for the right edge of the viewfinder. */
@Composable
fun EvControl(
    evText: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("+", color = RetroWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onUp).padding(horizontal = 10.dp, vertical = 4.dp))
        Text(evText, color = RetroAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable(onClick = onReset).padding(vertical = 2.dp))
        Text("\u2212", color = RetroWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onDown).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Floating pro tip. Tap the text to dismiss; "TRY" applies a suggested look. */
@Composable
fun CoachBanner(
    tip: CoachTip,
    onTry: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (tip.level) {
        TipLevel.WARN -> Color(0xFFFF7043)
        TipLevel.SUGGEST -> RetroAmber
        TipLevel.INFO -> RetroWhite
    }
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("PRO", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(
            text = tip.text,
            color = RetroWhite,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f, fill = false).clickable(onClick = onDismiss)
        )
        if (tip.suggestPresetId != null) {
            Text(
                text = "TRY",
                color = RetroBlack,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(RetroAmber)
                    .clickable { onTry(tip.suggestPresetId) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}
