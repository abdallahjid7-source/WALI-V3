package com.example.wali.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WaliErrorRed
import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.ui.theme.LocalWaliColors

@Composable
fun ScoreHeader(
    gameState: GameState,
    feedbackMessage: String?,
    gameMode: GameMode = GameMode.TWO_PLAYERS,
    humanPlayer: Player = Player.STICKS,
    isAiThinking: Boolean = false,
    isTimerEnabled: Boolean = true,
    timerRemainingSeconds: Int = 30,
    timerDurationSeconds: Int = 30,
    isTimerPaused: Boolean = false,
    localPlayerRole: Player? = null,
    gameDurationSeconds: Long = 0L,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val waliColors = LocalWaliColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isCompact) 4.dp else 16.dp, vertical = if (isCompact) 4.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- BADGE DE PHASE ET MODE & DURÉE DE JEU ---
        val phaseLabel = when (gameState.phase) {
            Phase.PLACEMENT -> if (isCompact) "Phase 1 : Pose" else "Phase 1 — Pose des pions"
            Phase.MOVEMENT -> if (isCompact) "Phase 2 : Jeu" else "Phase 2 — Déplacement"
            Phase.GAME_OVER -> "Partie terminée"
        }

        val modeLabel = when (gameMode) {
            GameMode.VS_COMPUTER -> if (isCompact) " • IA" else " • vs Machine"
            GameMode.ONLINE -> if (isCompact) " • Ligne" else " • En Ligne"
            else -> if (isCompact) " • 2J" else " • 2 Joueurs"
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(waliColors.surfaceAlt.copy(alpha = 0.5f))
                    .border(1.dp, waliColors.accentGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = if (isCompact) 10.dp else 14.dp, vertical = if (isCompact) 2.dp else 4.dp)
            ) {
                Text(
                    text = phaseLabel + modeLabel,
                    style = if (isCompact) MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = waliColors.textPrimary
                    ) else MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = waliColors.textPrimary
                    ),
                    modifier = Modifier.testTag("phase_label")
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(waliColors.surfaceAlt.copy(alpha = 0.5f))
                    .border(1.dp, waliColors.boardBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = if (isCompact) 2.dp else 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Durée de la partie",
                        tint = waliColors.accentGold,
                        modifier = Modifier.size(if (isCompact) 12.dp else 14.dp)
                    )
                    Text(
                        text = com.example.wali.storage.formatTimerDigital(gameDurationSeconds),
                        style = (if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium).copy(
                            fontWeight = FontWeight.Bold,
                            color = waliColors.textPrimary
                        ),
                        modifier = Modifier.testTag("game_duration_indicator")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 10.dp))

        // --- CARTES DE SCORE DES JOUEURS ---
        val isSticksActive = gameState.currentPlayer == Player.STICKS && gameState.phase != Phase.GAME_OVER
        val isStonesActive = gameState.currentPlayer == Player.STONES && gameState.phase != Phase.GAME_OVER

        val isAiCurrentlyPlaying = gameMode == GameMode.VS_COMPUTER &&
                gameState.currentPlayer != humanPlayer &&
                gameState.phase != Phase.GAME_OVER

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Carte BÂTONNETS
            val sticksSubtitle = when (gameMode) {
                GameMode.VS_COMPUTER -> if (humanPlayer == Player.STICKS) " (Vous)" else " (Machine)"
                GameMode.ONLINE -> if (localPlayerRole == Player.STICKS) " (Vous)" else " (Adversaire)"
                else -> ""
            }

            val isSticksTimerActive = if (gameMode == GameMode.ONLINE) {
                isSticksActive && isTimerEnabled
            } else {
                isSticksActive && isTimerEnabled && !isAiCurrentlyPlaying
            }

            PlayerScoreCard(
                player = Player.STICKS,
                title = "BÂTONNETS$sticksSubtitle",
                isActive = isSticksActive,
                remaining = gameState.sticksRemaining,
                piecesOnBoard = gameState.piecesOnBoard(Player.STICKS),
                pendingCount = gameState.sticksPendingPlacement,
                phase = gameState.phase,
                isTimerActive = isSticksTimerActive,
                timerRemainingSeconds = timerRemainingSeconds,
                timerDurationSeconds = timerDurationSeconds,
                isTimerPaused = isTimerPaused,
                isCompact = isCompact,
                modifier = Modifier.weight(1f)
            )

            // Carte CAILLOUX
            val stonesSubtitle = when (gameMode) {
                GameMode.VS_COMPUTER -> if (humanPlayer == Player.STONES) " (Vous)" else " (Machine)"
                GameMode.ONLINE -> if (localPlayerRole == Player.STONES) " (Vous)" else " (Adversaire)"
                else -> ""
            }

            val isStonesTimerActive = if (gameMode == GameMode.ONLINE) {
                isStonesActive && isTimerEnabled
            } else {
                isStonesActive && isTimerEnabled && !isAiCurrentlyPlaying
            }

            PlayerScoreCard(
                player = Player.STONES,
                title = "CAILLOUX$stonesSubtitle",
                isActive = isStonesActive,
                remaining = gameState.stonesRemaining,
                piecesOnBoard = gameState.piecesOnBoard(Player.STONES),
                pendingCount = gameState.stonesPendingPlacement,
                phase = gameState.phase,
                isTimerActive = isStonesTimerActive,
                timerRemainingSeconds = timerRemainingSeconds,
                timerDurationSeconds = timerDurationSeconds,
                isTimerPaused = isTimerPaused,
                isCompact = isCompact,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 10.dp))

        // --- BANNIÈRE D'ÉTAT DYNAMIQUE ---
        val isCaptureActive = WaliEngine.canCapture(gameState)
        val isPlacementRequired = WaliEngine.isPlacementRequired(gameState)
        val activePlayerName = if (gameState.currentPlayer == Player.STICKS) "BÂTONNETS" else "CAILLOUX"

        val turnMessage = if (gameState.phase != Phase.GAME_OVER) {
            if (isAiThinking || isAiCurrentlyPlaying) {
                "L'ordinateur joue..."
            } else if (gameMode == GameMode.ONLINE) {
                if (isCaptureActive) {
                    if (gameState.currentPlayer == localPlayerRole) "★ Ligne formée ! Choisissez 1 pion adverse à capturer"
                    else "L'adversaire effectue une capture..."
                } else if (isPlacementRequired) {
                    if (gameState.currentPlayer == localPlayerRole) "★ Pose obligatoire : Placez votre pion en attente"
                    else "L'adversaire pose son pion en attente..."
                } else if (gameState.currentPlayer == localPlayerRole) {
                    "C'est à votre tour de jouer !"
                } else {
                    "Tour de l'adversaire distant…"
                }
            } else if (isCaptureActive) {
                "★ Ligne formée ! Choisissez 1 pion adverse à capturer"
            } else if (isPlacementRequired) {
                "★ Pose obligatoire : Placez votre pion en attente"
            } else {
                "Tour des $activePlayerName"
            }
        } else {
            "Partie achevée"
        }

        val bannerColor = when {
            isAiThinking -> waliColors.accentGold
            isCaptureActive -> waliColors.captureAccent
            isPlacementRequired -> waliColors.accentGold
            gameState.currentPlayer == Player.STICKS -> waliColors.accentEarth
            else -> waliColors.boardBorder
        }

        val showTimerInBanner = if (gameMode == GameMode.ONLINE) {
            isTimerEnabled && gameState.phase != Phase.GAME_OVER
        } else {
            isTimerEnabled && !isAiCurrentlyPlaying && gameState.phase != Phase.GAME_OVER
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bannerColor)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isAiThinking) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("ai_thinking_indicator"),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Text(
                    text = turnMessage,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.testTag("turn_indicator")
                )

                if (showTimerInBanner) {
                    Spacer(modifier = Modifier.width(10.dp))
                    TurnTimerIndicator(
                        remainingSeconds = timerRemainingSeconds,
                        totalSeconds = timerDurationSeconds,
                        isPaused = isTimerPaused
                    )
                }
            }
        }

        // --- MESSAGE DE FEEDBACK (ERREUR OU AVERTISSEMENT) ---
        AnimatedVisibility(
            visible = feedbackMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            feedbackMessage?.let { msg ->
                val isError = msg.contains("interdit", ignoreCase = true) ||
                        msg.contains("occupée", ignoreCase = true) ||
                        msg.contains("aucun", ignoreCase = true)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("feedback_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) Color(0xFFFFECEB) else waliColors.surfaceAlt
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isError) WaliErrorRed.copy(alpha = 0.4f) else waliColors.accentGold.copy(alpha = 0.4f)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isError) WaliErrorRed else waliColors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isError) WaliErrorRed else waliColors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerScoreCard(
    player: Player,
    title: String,
    isActive: Boolean,
    remaining: Int,
    piecesOnBoard: Int,
    pendingCount: Int = 0,
    phase: Phase,
    isTimerActive: Boolean = false,
    timerRemainingSeconds: Int = 30,
    timerDurationSeconds: Int = 30,
    isTimerPaused: Boolean = false,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val waliColors = LocalWaliColors.current
    val borderColor = if (isActive) waliColors.accentGold else waliColors.accentEarth.copy(alpha = 0.2f)
    val backgroundColor = if (isActive) waliColors.surface else waliColors.surface.copy(alpha = 0.6f)

    Card(
        modifier = modifier
            .testTag("score_card_${player.name.lowercase()}")
            .border(
                width = if (isActive) 2.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 6.dp else 10.dp, vertical = if (isCompact) 5.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Miniature de pion
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 14.dp else 18.dp)
                        .clip(CircleShape)
                        .background(if (player == Player.STICKS) waliColors.stickGradientMiddle else waliColors.stoneGradientMid)
                )
                Spacer(modifier = Modifier.width(if (isCompact) 4.dp else 6.dp))
                Text(
                    text = title,
                    style = (if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge).copy(
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = waliColors.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(if (isCompact) 2.dp else 4.dp))

            // Décompte de pions selon la phase
            val countText = when (phase) {
                Phase.PLACEMENT -> "À poser : $remaining"
                Phase.MOVEMENT, Phase.GAME_OVER -> {
                    if (pendingCount > 0) "Pions : $piecesOnBoard (+$pendingCount)"
                    else "Pions : $piecesOnBoard"
                }
            }

            Text(
                text = countText,
                style = (if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium).copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) waliColors.textPrimary else waliColors.textSecondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isTimerActive) {
                Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 6.dp))
                TurnTimerIndicator(
                    remainingSeconds = timerRemainingSeconds,
                    totalSeconds = timerDurationSeconds,
                    isPaused = isTimerPaused
                )
            }
        }
    }
}

/**
 * Pastille visuelle dynamique indiquant le temps restant pour le coup en cours.
 * Adopte une couleur rouge d'alerte lors des 5 dernières secondes.
 */
@Composable
fun TurnTimerIndicator(
    remainingSeconds: Int,
    totalSeconds: Int,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val isUrgent = remainingSeconds <= 5
    val progress = if (totalSeconds > 0) (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f

    val badgeColor = if (isUrgent) Color(0xFFD32F2F) else Color.White
    val containerColor = if (isUrgent) Color(0xFFFFEBEE) else Color.Black.copy(alpha = 0.25f)
    val progressTrackColor = if (isUrgent) Color(0xFFFFCDD2) else Color.White.copy(alpha = 0.25f)
    val progressColor = if (isUrgent) Color(0xFFD32F2F) else Color(0xFFFFD54F)

    Row(
        modifier = modifier
            .testTag("turn_timer_display")
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(15.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(15.dp),
                color = progressColor,
                trackColor = progressTrackColor,
                strokeWidth = 2.dp
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isPaused) "${remainingSeconds}s ⏸" else "${remainingSeconds}s",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                letterSpacing = 0.5.sp
            )
        )
    }
}
