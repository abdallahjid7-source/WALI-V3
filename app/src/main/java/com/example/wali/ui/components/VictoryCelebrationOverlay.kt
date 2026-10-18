package com.example.wali.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import kotlin.math.sin
import kotlin.random.Random

/**
 * Type de particule de confettis pour la célébration de victoire.
 */
private enum class ParticleShape {
    RECTANGLE,
    CIRCLE,
    STAR,
    SAHEL_STICK // Bâtonnet stylisé aux couleurs du Wali
}

/**
 * Données d'une particule individuelle dans la pluie de confettis.
 */
private data class VictoryParticle(
    val initialX: Float,        // Position X proportionnelle (0f..1f)
    val startDelayMs: Int,      // Délai avant départ
    val speedY: Float,          // Vitesse de chute
    val speedXDrift: Float,     // Oscillation horizontale
    val rotationSpeed: Float,   // Vitesse de rotation sur elle-même
    val size: Float,            // Taille en dp/px
    val color: Color,
    val shape: ParticleShape
)

/**
 * Animation Compose spectaculaire et fluide de feux de joie et confettis
 * déclenchée lors de la victoire d'un joueur.
 * Couleurs adaptées au thème sahélien : Or brillant, Terre de Sienne, Ambre, Émeraude et Crème.
 */
@Composable
fun VictoryCelebrationOverlay(
    modifier: Modifier = Modifier,
    particleCount: Int = 65
) {
    // Palette festive vibrante aux tons dorés et chaleureux
    val festiveColors = remember {
        listOf(
            Color(0xFFFFD700), // Or brillant
            Color(0xFFFFA000), // Ambre chaud
            Color(0xFFE65100), // Terre de sienne flamboyante
            Color(0xFFFF6F00), // Éclat safran
            Color(0xFF43A047), // Vert émeraude victoire
            Color(0xFF00ACC1), // Turquoise oasis
            Color(0xFFFFF8E1), // Perle ivoire éclatante
            Color(0xFFFFB300)  // Or solaire
        )
    }

    // Génération stable et déterministe d'un ensemble de particules
    val particles = remember {
        val random = Random(42)
        List(particleCount) {
            VictoryParticle(
                initialX = random.nextFloat(),
                startDelayMs = random.nextInt(0, 800),
                speedY = 0.55f + random.nextFloat() * 0.70f,
                speedXDrift = (random.nextFloat() - 0.5f) * 0.40f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 720f,
                size = 12f + random.nextFloat() * 16f,
                color = festiveColors[random.nextInt(festiveColors.size)],
                shape = ParticleShape.values()[random.nextInt(ParticleShape.values().size)]
            )
        }
    }

    // Progression principale de l'animation de chute
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3800, easing = LinearEasing)
        )
    }

    // Mouvement continu d'ondulation du vent et scintillement
    val infiniteTransition = rememberInfiniteTransition(label = "victoryInfinite")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val shimmerScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerScale"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("victory_celebration_overlay")
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val progress = animProgress.value

        particles.forEachIndexed { index, p ->
            // Calcul du temps écoulé normalisé pour cette particule
            val particleProgress = (progress * 3800f - p.startDelayMs).coerceAtLeast(0f) / 3000f

            if (particleProgress in 0f..1.15f) {
                val effectiveProgress = particleProgress.coerceIn(0f, 1f)

                // Position Y : chute du haut vers le bas avec gravité accélérée
                val currentY = effectiveProgress * (canvasHeight + 120f) - 60f

                // Position X : dérive naturelle avec sinusoïde douce (effet de feuille portée par le vent)
                val drift = sin((wavePhase + index.toFloat()).toDouble()).toFloat() * 40f + p.speedXDrift * effectiveProgress * canvasWidth
                val currentX = (p.initialX * canvasWidth + drift).coerceIn(0f, canvasWidth)

                // Rotation et inclinaison
                val currentRotation = (effectiveProgress * p.rotationSpeed) % 360f

                // Effet de fondu en fin de course
                val alpha = if (effectiveProgress > 0.80f) {
                    ((1f - effectiveProgress) / 0.20f).coerceIn(0f, 1f)
                } else {
                    1f
                }

                val particleCenter = Offset(currentX, currentY)
                rotate(degrees = currentRotation, pivot = particleCenter) {
                    drawParticleShape(
                        shape = p.shape,
                        center = particleCenter,
                        size = p.size * shimmerScale,
                        color = p.color.copy(alpha = alpha)
                    )
                }
            }
        }
    }
}

/**
 * Dessine la forme vectorielle de chaque confettis selon sa géométrie.
 */
private fun DrawScope.drawParticleShape(
    shape: ParticleShape,
    center: Offset,
    size: Float,
    color: Color
) {
    when (shape) {
        ParticleShape.RECTANGLE -> {
            drawRect(
                color = color,
                topLeft = Offset(center.x - size / 2, center.y - size / 4),
                size = Size(size, size / 2)
            )
        }
        ParticleShape.CIRCLE -> {
            drawCircle(
                color = color,
                radius = size / 2.5f,
                center = center
            )
        }
        ParticleShape.SAHEL_STICK -> {
            // Bâtonnet stylisé évocateur du jeu de Wali
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - size / 6, center.y - size),
                size = Size(size / 3, size * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size / 6, size / 6)
            )
        }
        ParticleShape.STAR -> {
            val starPath = Path().apply {
                val halfSize = size / 1.8f
                moveTo(center.x, center.y - halfSize)
                lineTo(center.x + halfSize * 0.35f, center.y - halfSize * 0.35f)
                lineTo(center.x + halfSize, center.y)
                lineTo(center.x + halfSize * 0.35f, center.y + halfSize * 0.35f)
                lineTo(center.x, center.y + halfSize)
                lineTo(center.x - halfSize * 0.35f, center.y + halfSize * 0.35f)
                lineTo(center.x - halfSize, center.y)
                lineTo(center.x - halfSize * 0.35f, center.y - halfSize * 0.35f)
                close()
            }
            drawPath(path = starPath, color = color, style = Fill)
        }
    }
}
