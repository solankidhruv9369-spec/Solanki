package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = SlateBackground,
    primaryContainer = BrightIndigo,
    onPrimaryContainer = TextPrimaryDark,
    secondary = BrightIndigoLight,
    onSecondary = TextPrimaryDark,
    background = SlateBackground,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    error = ColorError
)

private val LightColorScheme = lightColorScheme(
    primary = CoolTeal,
    onPrimary = SoftLightSurface,
    primaryContainer = CoolIndigo,
    onPrimaryContainer = SoftLightSurface,
    secondary = CoolIndigo,
    onSecondary = SoftLightSurface,
    background = SoftLightBackground,
    onBackground = TextPrimaryLight,
    surface = SoftLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftLightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    error = ColorError
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val isLight = !darkTheme
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
