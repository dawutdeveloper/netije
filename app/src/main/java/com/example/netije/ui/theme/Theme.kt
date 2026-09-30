package com.example.netije.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightNetijeScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryBlue,
    primaryContainer = PrimaryBlueContainer,
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
    surfaceVariant = NeutralBgLight,
    onSurfaceVariant = NeutralTextSecondary,
    surfaceTint = Color.Transparent,
    outline = NeutralBorder,
    outlineVariant = NeutralBorder,
    error = NegativeRed,
    onError = Color.White,
    errorContainer = NegativeRedLight.copy(alpha = 0.18f),
    onErrorContainer = NegativeRed
)

private val DarkNetijeScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = NeutralTextPrimaryDark,
    primaryContainer = PrimaryBlue,
    onPrimaryContainer = OnPrimaryBlue,
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
    darkMode: com.example.netije.domain.model.DarkModeConfig = com.example.netije.domain.model.DarkModeConfig.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (darkMode) {
        com.example.netije.domain.model.DarkModeConfig.ON -> true
        com.example.netije.domain.model.DarkModeConfig.OFF -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkNetijeScheme else LightNetijeScheme
    MaterialTheme(
        colorScheme = colors,
        typography = NetijeTypography,
        content = content
    )
}
