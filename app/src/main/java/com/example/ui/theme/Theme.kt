package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.runtime.CompositionLocalProvider
import com.example.wali.ui.theme.LocalWaliColors
import com.example.wali.ui.theme.WaliColors
import com.example.wali.ui.theme.WaliThemePalette
import com.example.wali.ui.theme.getColorsForPalette

private val DarkColorScheme = darkColorScheme(
    primary = WaliGoldLight,
    onPrimary = WaliEarthDark,
    primaryContainer = WaliEarth,
    onPrimaryContainer = WaliSandLight,
    secondary = WaliSand,
    onSecondary = WaliEarthDark,
    background = Color(0xFF1D1712),
    surface = Color(0xFF261F18),
    onBackground = WaliSandLight,
    onSurface = WaliSandLight,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = WaliEarthDark,
    onPrimary = WaliSandLight,
    primaryContainer = WaliSand,
    onPrimaryContainer = WaliEarthDark,
    secondary = WaliGold,
    onSecondary = WaliEarthDark,
    background = WaliSandBackground,
    surface = WaliSandLight,
    onBackground = WaliEarthDark,
    onSurface = WaliEarthDark,
    error = WaliErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    palette: WaliThemePalette = WaliThemePalette.SAHEL_JOUR,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Désactivé par défaut pour préserver l'identité visuelle Tombouctou
    content: @Composable () -> Unit
) {
    val waliColors = getColorsForPalette(palette)

    val colorScheme = when (palette) {
        WaliThemePalette.DESERT_NIGHT -> darkColorScheme(
            primary = waliColors.accentGold,
            onPrimary = waliColors.background,
            primaryContainer = waliColors.surfaceAlt,
            onPrimaryContainer = waliColors.textPrimary,
            secondary = waliColors.accentGold,
            onSecondary = waliColors.background,
            background = waliColors.background,
            surface = waliColors.surface,
            onBackground = waliColors.textPrimary,
            onSurface = waliColors.textPrimary,
            error = WaliErrorRed,
            onError = Color.White
        )
        WaliThemePalette.MODERN_LIGHT -> lightColorScheme(
            primary = waliColors.accentEarth,
            onPrimary = Color.White,
            primaryContainer = waliColors.surfaceAlt,
            onPrimaryContainer = waliColors.textPrimary,
            secondary = waliColors.accentGold,
            onSecondary = Color.White,
            background = waliColors.background,
            surface = waliColors.surface,
            onBackground = waliColors.textPrimary,
            onSurface = waliColors.textPrimary,
            error = WaliErrorRed,
            onError = Color.White
        )
        WaliThemePalette.SAHEL_JOUR -> LightColorScheme
    }

    CompositionLocalProvider(LocalWaliColors provides waliColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
