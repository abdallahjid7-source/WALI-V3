package com.example.wali.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.wali.model.Player
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Représentation visuelle artisanale des pions du Wali Songhaï :
 * - BÂTONNETS : bâtonnet sculpté adapté au thème actif
 * - CAILLOUX : galet minéral adapté au thème actif
 */
@Composable
fun PieceView(
    player: Player,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isCapturable: Boolean = false
) {
    val waliColors = LocalWaliColors.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "pieceScale"
    )

    val pieceName = if (player == Player.STICKS) "Bâtonnet" else "Caillou"

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = pieceName },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.5.dp)
        ) {
            val canvasWidth = size.width * scale
            val canvasHeight = size.height * scale
            val center = Offset(size.width / 2f, size.height / 2f)

            if (player == Player.STICKS) {
                // --- BÂTONNET EN BOIS ---
                // Forme allongée arrondie avec anneau doré traditionnel
                val stickWidth = canvasWidth * 0.38f
                val stickHeight = canvasHeight * 0.82f
                val stickTopLeft = Offset(center.x - stickWidth / 2f, center.y - stickHeight / 2f)

                // Ombre portée du bâtonnet
                drawRoundRect(
                    color = waliColors.stoneShadow,
                    topLeft = stickTopLeft + Offset(3f, 4f),
                    size = Size(stickWidth, stickHeight),
                    cornerRadius = CornerRadius(stickWidth / 2f, stickWidth / 2f)
                )

                // Corps en bois dégradé
                val woodGradient = Brush.linearGradient(
                    colors = listOf(
                        waliColors.stickGradientTop,
                        waliColors.stickGradientMiddle,
                        waliColors.stickGradientDark
                    ),
                    start = Offset(stickTopLeft.x, stickTopLeft.y),
                    end = Offset(stickTopLeft.x + stickWidth, stickTopLeft.y + stickHeight)
                )

                drawRoundRect(
                    brush = woodGradient,
                    topLeft = stickTopLeft,
                    size = Size(stickWidth, stickHeight),
                    cornerRadius = CornerRadius(stickWidth / 2f, stickWidth / 2f)
                )

                // Anneau décoratif doré au milieu (style artisanal Songhaï)
                val ringHeight = stickHeight * 0.16f
                val ringTopLeft = Offset(stickTopLeft.x, center.y - ringHeight / 2f)
                drawRoundRect(
                    color = waliColors.stickRing,
                    topLeft = ringTopLeft,
                    size = Size(stickWidth, ringHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Ligne de lumière le long du bâtonnet
                drawLine(
                    color = waliColors.stickHighlight,
                    start = Offset(stickTopLeft.x + stickWidth * 0.3f, stickTopLeft.y + 6f),
                    end = Offset(stickTopLeft.x + stickWidth * 0.3f, stickTopLeft.y + stickHeight - 6f),
                    strokeWidth = 2f
                )

                // Bordure de contour nette
                drawRoundRect(
                    color = if (isSelected) waliColors.accentGold else waliColors.boardBorder.copy(alpha = 0.6f),
                    topLeft = stickTopLeft,
                    size = Size(stickWidth, stickHeight),
                    cornerRadius = CornerRadius(stickWidth / 2f, stickWidth / 2f),
                    style = Stroke(width = if (isSelected) 3f else 1.5f)
                )
            } else {
                // --- CAILLOU / GALET DE PIERRE ---
                // Galet ovale avec reflet doux minéral
                val stoneRadiusX = (canvasWidth * 0.40f)
                val stoneRadiusY = (canvasHeight * 0.36f)

                // Ombre portée
                drawOval(
                    color = waliColors.stoneShadow,
                    topLeft = Offset(center.x - stoneRadiusX + 3f, center.y - stoneRadiusY + 4f),
                    size = Size(stoneRadiusX * 2f, stoneRadiusY * 2f)
                )

                // Corps du galet minéral
                val stoneGradient = Brush.radialGradient(
                    colors = listOf(
                        waliColors.stoneGradientLight,
                        waliColors.stoneGradientMid,
                        waliColors.stoneGradientDark
                    ),
                    center = Offset(center.x - stoneRadiusX * 0.25f, center.y - stoneRadiusY * 0.3f),
                    radius = stoneRadiusX * 1.8f
                )

                drawOval(
                    brush = stoneGradient,
                    topLeft = Offset(center.x - stoneRadiusX, center.y - stoneRadiusY),
                    size = Size(stoneRadiusX * 2f, stoneRadiusY * 2f)
                )

                // Éclat de lumière supérieur du galet poli
                drawOval(
                    color = Color(0x44FFFFFF),
                    topLeft = Offset(center.x - stoneRadiusX * 0.65f, center.y - stoneRadiusY * 0.75f),
                    size = Size(stoneRadiusX * 0.9f, stoneRadiusY * 0.5f)
                )

                // Bordure de contour
                drawOval(
                    color = if (isSelected) Color(0xFFFFD54F) else Color(0x881E1C19),
                    topLeft = Offset(center.x - stoneRadiusX, center.y - stoneRadiusY),
                    size = Size(stoneRadiusX * 2f, stoneRadiusY * 2f),
                    style = Stroke(width = if (isSelected) 3f else 1.5f)
                )
            }
        }
    }
}
