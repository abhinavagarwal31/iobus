package com.iobus.client.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * IOBus HUD dark theme — wraps Material3 with our custom palette.
 */

private val HudDarkScheme = darkColorScheme(
    primary = HudCyan,
    onPrimary = HudBlack,
    primaryContainer = HudCyanDim,
    onPrimaryContainer = HudTextPrimary,

    secondary = HudAmber,
    onSecondary = HudBlack,
    secondaryContainer = HudAmberDim,
    onSecondaryContainer = HudTextPrimary,

    tertiary = HudGreen,
    onTertiary = HudBlack,

    error = HudRed,
    onError = HudBlack,
    errorContainer = HudRedDim,
    onErrorContainer = HudTextPrimary,

    background = HudBlack,
    onBackground = HudTextPrimary,

    surface = HudSurface,
    onSurface = HudTextPrimary,
    surfaceVariant = HudSurfaceElevated,
    onSurfaceVariant = HudTextSecondary,

    outline = HudSurfaceBorder,
    outlineVariant = HudKeyBorder,
)

@Composable
fun IOBusTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = HudBlack.toArgb()
            window.navigationBarColor = HudBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = HudDarkScheme,
        typography = HudTypography,
        shapes = HudShapes,
        content = content,
    )
}
