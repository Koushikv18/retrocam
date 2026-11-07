package com.koushik.retrocam.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = RetroAmber,
    secondary = RetroAmberGlow,
    tertiary = RetroCyan,
    background = RetroBlack,
    surface = RetroDarkSurface,
    onPrimary = RetroBlack,
    onSecondary = RetroBlack,
    onTertiary = RetroBlack,
    onBackground = RetroWhite,
    onSurface = RetroWhite,
    surfaceVariant = RetroSurfaceVariant,
    onSurfaceVariant = RetroGray
)

@Composable
fun RetroCamTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = RetroBlack.toArgb()
            window.navigationBarColor = RetroBlack.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

