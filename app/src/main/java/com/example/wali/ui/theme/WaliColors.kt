package com.example.wali.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Jeu de couleurs complet spécifique à l'univers visuel du Wali Songhaï.
 * Adapte l'ensemble des éléments graphiques : fond, cartes, plateau, quadrillage, pions, accents.
 */
@Immutable
data class WaliColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentGold: Color,
    val accentEarth: Color,
    val boardBorder: Color,
    val gridLine: Color,
    val cellBackground: Color,
    val cellBackgroundAlt: Color,
    val highlightMove: Color,
    val captureAccent: Color,
    // Pions Bâtonnets
    val stickGradientTop: Color,
    val stickGradientMiddle: Color,
    val stickGradientDark: Color,
    val stickRing: Color,
    val stickHighlight: Color,
    // Pions Cailloux
    val stoneGradientLight: Color,
    val stoneGradientMid: Color,
    val stoneGradientDark: Color,
    val stoneShadow: Color
)

// THÈME 1 — "Sahel Jour"
// Sable #D9B27C, terre cuite #9B6A3C, brun foncé #4A2C18, or #C69A45, gris pierre #77736A
val SahelJourColors = WaliColors(
    background = Color(0xFFFBF8F1),
    surface = Color(0xFFF4E9D8),
    surfaceAlt = Color(0xFFEADCC6),
    textPrimary = Color(0xFF4A2C18),
    textSecondary = Color(0xFF9B6A3C),
    accentGold = Color(0xFFC69A45),
    accentEarth = Color(0xFF9B6A3C),
    boardBorder = Color(0xFF5C3B20),
    gridLine = Color(0xFF8C5F35),
    cellBackground = Color(0xFFECD8BD),
    cellBackgroundAlt = Color(0xFFE3CBAE),
    highlightMove = Color(0xFF3D7E50),
    captureAccent = Color(0xFFD05315),
    stickGradientTop = Color(0xFFC69A45),
    stickGradientMiddle = Color(0xFF9B6A3C),
    stickGradientDark = Color(0xFF3B1E0D),
    stickRing = Color(0xFFF1D479),
    stickHighlight = Color(0x66FFF5D6),
    stoneGradientLight = Color(0xFF9E9A90),
    stoneGradientMid = Color(0xFF6B675E),
    stoneGradientDark = Color(0xFF23221F),
    stoneShadow = Color(0x66181614)
)

// THÈME 2 — "Nuit du Désert"
// Fond bleu nuit profond #1B2333, sable assombri #6E5B3E, or lunaire discret #B8975A, argile sombre #3A2A20, accents gris-bleu #4E5B6E
val DesertNightColors = WaliColors(
    background = Color(0xFF1B2333),
    surface = Color(0xFF232E42),
    surfaceAlt = Color(0xFF2D3B54),
    textPrimary = Color(0xFFE2E8F0),
    textSecondary = Color(0xFFB8975A),
    accentGold = Color(0xFFB8975A),
    accentEarth = Color(0xFF6E5B3E),
    boardBorder = Color(0xFF3A2A20),
    gridLine = Color(0xFF4E5B6E),
    cellBackground = Color(0xFF2A374F),
    cellBackgroundAlt = Color(0xFF243045),
    highlightMove = Color(0xFF2E8B57),
    captureAccent = Color(0xFFE06C43),
    stickGradientTop = Color(0xFFD4B372),
    stickGradientMiddle = Color(0xFF8A6C44),
    stickGradientDark = Color(0xFF3A2A20),
    stickRing = Color(0xFFFFE082),
    stickHighlight = Color(0x88FFF8E1),
    stoneGradientLight = Color(0xFF8B9CB5),
    stoneGradientMid = Color(0xFF4E5B6E),
    stoneGradientDark = Color(0xFF151C28),
    stoneShadow = Color(0x880D111A)
)

// THÈME 3 — "Moderne Clair"
// Fond blanc cassé #F5F1EA, bois clair #C9A876, accent terracotta doux #D08B5B, gris ardoise #5C6570, touches turquoise discret #6FA8A0
val ModernLightColors = WaliColors(
    background = Color(0xFFF5F1EA),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFECE7DE),
    textPrimary = Color(0xFF2D3748),
    textSecondary = Color(0xFF5C6570),
    accentGold = Color(0xFFC9A876),
    accentEarth = Color(0xFFD08B5B),
    boardBorder = Color(0xFF5C6570),
    gridLine = Color(0xFFBAC2CB),
    cellBackground = Color(0xFFFBF9F5),
    cellBackgroundAlt = Color(0xFFF2EEE7),
    highlightMove = Color(0xFF48A388),
    captureAccent = Color(0xFFD08B5B),
    stickGradientTop = Color(0xFFE0C599),
    stickGradientMiddle = Color(0xFFC9A876),
    stickGradientDark = Color(0xFF7A5C33),
    stickRing = Color(0xFF6FA8A0),
    stickHighlight = Color(0x77FFFFFF),
    stoneGradientLight = Color(0xFF8C95A0),
    stoneGradientMid = Color(0xFF5C6570),
    stoneGradientDark = Color(0xFF2C323B),
    stoneShadow = Color(0x442C323B)
)

val LocalWaliColors = staticCompositionLocalOf { SahelJourColors }

fun getColorsForPalette(palette: WaliThemePalette): WaliColors {
    return when (palette) {
        WaliThemePalette.SAHEL_JOUR -> SahelJourColors
        WaliThemePalette.DESERT_NIGHT -> DesertNightColors
        WaliThemePalette.MODERN_LIGHT -> ModernLightColors
    }
}
