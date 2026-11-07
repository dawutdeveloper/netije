package com.gazna.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gazna.presentation.util.findActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightNetijeScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F0F0),
    onPrimaryContainer = NeutralTextPrimary,
    secondary = NeutralTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = NeutralBorder,
    onSecondaryContainer = NeutralTextPrimary,
    tertiary = PositiveGreen,
    onTertiary = Color.White,
    tertiaryContainer = PositiveGreenLight.copy(alpha = 0.18f),
    onTertiaryContainer = PositiveGreen,
    background = NeutralBgLight,
    onBackground = NeutralTextPrimary,
    surface = NeutralSurface,
    onSurface = NeutralTextPrimary,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = NeutralTextSecondary,
    surfaceDim = Color(0xFFE0E0E0),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    inverseSurface = Color(0xFF202020),
    inverseOnSurface = Color.White,
    inversePrimary = Color.White,
    surfaceTint = Color.Transparent,
    outline = NeutralBorder,
    outlineVariant = NeutralBorder,
    error = NegativeRed,
    onError = Color.White,
    errorContainer = NegativeRedLight.copy(alpha = 0.18f),
    onErrorContainer = NegativeRed
)

private val DarkNetijeScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF242424),
    onPrimaryContainer = Color.White,
    secondary = NeutralTextSecondaryDark,
    onSecondary = NeutralBgDark,
    secondaryContainer = NeutralBorderDark,
    onSecondaryContainer = NeutralTextPrimaryDark,
    tertiary = PositiveGreenLight,
    onTertiary = NeutralBgDark,
    tertiaryContainer = PositiveGreen,
    onTertiaryContainer = Color.White,
    background = NeutralBgDark,
    onBackground = NeutralTextPrimaryDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralTextPrimaryDark,
    surfaceVariant = NeutralSurfaceDark,
    onSurfaceVariant = NeutralTextSecondaryDark,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF303030),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF101010),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF202020),
    surfaceContainerHighest = Color(0xFF282828),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = Color.Black,
    surfaceTint = Color.Transparent,
    outline = NeutralBorderDark,
    outlineVariant = NeutralBorderDark,
    error = NegativeRedLight,
    onError = NeutralBgDark,
    errorContainer = NegativeRed,
    onErrorContainer = Color.White
)

@Composable
fun NetijeTheme(
    darkMode: com.gazna.domain.model.DarkModeConfig = com.gazna.domain.model.DarkModeConfig.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (darkMode) {
        com.gazna.domain.model.DarkModeConfig.ON -> true
        com.gazna.domain.model.DarkModeConfig.OFF -> false
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    val colors = if (dark) DarkNetijeScheme else LightNetijeScheme
    MaterialTheme(
        colorScheme = colors,
        typography = NetijeTypography,
        content = content
    )
}
