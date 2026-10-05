package com.adshield.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.adshield.domain.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = ShieldGreen,
    onPrimary = ShieldGreenDark,
    secondary = ShieldBlue,
    error = ShieldRed,
    background = ShieldBackground,
    onBackground = ShieldText,
    surface = ShieldSurface,
    onSurface = ShieldText,
    surfaceVariant = ShieldSurfaceHigh,
    onSurfaceVariant = ShieldTextMuted,
    outline = ShieldOutline
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00795A),
    onPrimary = Color.White,
    secondary = Color(0xFF1F5FBF),
    error = Color(0xFFC62838),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE6EBF2),
    onSurfaceVariant = Color(0xFF4B5768),
    outline = Color(0xFFB8C2D1)
)

@Composable
fun AdShieldTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Iconos de la barra de estado legibles sobre el fondo del tema elegido.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AdShieldTypography,
        content = content
    )
}
