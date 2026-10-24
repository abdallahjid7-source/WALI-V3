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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
    isSpectator: Boolean = false,
    gameDurationSeconds: Long = 0L,
    salonMyWins: Int = 0,
    salonOpponentWins: Int = 0,
    salonSticksPlayerWins: Int = 0,
    salonStonesPlayerWins: Int = 0,
    salonGamesPlayed: Int = 1,
    canProposeDraw: Boolean = false,
    isWaitingForDrawResponse: Boolean = false,
    onProposeDraw: (() -> Unit)? = null,
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
            GameMode.ONLINE -> when {
                isSpectator -> if (isCompact) " • Spectateur" else " • Mode Spectateur"
                isCompact -> " • Ligne"
                else -> " • En Ligne"
            }
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

        if (gameMode == GameMode.ONLINE) {
            Spacer(modifier = Modifier.height(if (isCompact) 3.dp else 5.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(waliColors.surfaceAlt.copy(alpha = 0.55f))
                    .border(1.dp, waliColors.accentGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .testTag("online_salon_score_header")
            ) {
                Text(
                    text = if (isSpectator) {
                        "Score du salon : Bâtons $salonSticksPlayerWins – $salonStonesPlayerWins Cailloux • Parties : $salonGamesPlayed"
                    } else {
                        "Score du salon : $salonMyWins – $salonOpponentWins • Parties : $salonGamesPlayed"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = waliColors.accentGold
                    )
                )
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
                GameMode.ONLINE -> when {
                    isSpectator -> ""
                    localPlayerRole == Player.STICKS -> " (Vous)"
                    else -> " (Adversaire)"
                }
                else -> ""
            }

            val isSticksTimerActive = if (gameMode == GameMode.ONLINE) {
                isSticksActive && isTimerEnabled && !isSpectator
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
                GameMode.ONLINE -> when {
                    isSpectator -> ""
                    localPlayerRole == Player.STONES -> " (Vous)"
                    else -> " (Adversaire)"
                }
                else -> ""
            }

            val isStonesTimerActive = if (gameMode == GameMode.ONLINE) {
                isStonesActive && isTimerEnabled && !isSpectator
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
                if (isSpectator) {
                    if (isCaptureActive) {
                        "★ Les $activePlayerName effectuent une capture…"
                    } else if (isPlacementRequired) {
                        "★ Les $activePlayerName posent leur pion en attente…"
                    } else {
                        "Observation en direct • Tour des $activePlayerName"
                    }
                } else if (isCaptureActive) {
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

        if (canProposeDraw && onProposeDraw != null) {
            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 6.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onProposeDraw,
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = if (isCompact) 2.dp else 4.dp
                ),
                modifier = Modifier
                    .height(if (isCompact) 32.dp else 36.dp)
                    .testTag("btn_propose_draw")
            ) {
                Text(
                    text = "Proposer match nul",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = waliColors.accentGold
                    )
                )
            }
        } else if (isWaitingForDrawResponse) {
            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 6.dp))
            Text(
                text = "Proposition de match nul en attente…",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = waliColors.accentGold
                ),
                modifier = Modifier.testTag("waiting_for_draw_indicator")
            )
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

/**
 * Panneau dédié à un joueur en mode local 2 joueurs (face-à-face).
 * - Pour [Player.STICKS] (en haut de l'écran), [isRotated180] = true pivote l'ensemble du panneau
 *   (titre, compteurs, indicateur de tour, boutons d'action dédiés et boîtes de confirmation/réponse)
 *   à 180° via [Modifier.graphicsLayer] afin d'être lisible et cliquable par le joueur assis en face.
 * - Pour [Player.STONES] (en bas de l'écran), [isRotated180] = false conserve l'orientation normale.
 */
@Composable
fun FaceToFacePlayerPanel(
    player: Player,
    gameState: GameState,
    isRotated180: Boolean,
    isTimerEnabled: Boolean,
    timerRemainingSeconds: Int,
    timerDurationSeconds: Int,
    isTimerPaused: Boolean,
    canForfeit: Boolean,
    canProposeDraw: Boolean,
    isWaitingForDrawResponse: Boolean,
    isDrawOfferedToThisPlayer: Boolean,
    isConfirmingForfeitForThisPlayer: Boolean,
    onRequestForfeit: () -> Unit,
    onConfirmForfeit: () -> Unit,
    onDismissForfeit: () -> Unit,
    onProposeDraw: () -> Unit,
    onAcceptDraw: () -> Unit,
    onRefuseDraw: () -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val waliColors = LocalWaliColors.current
    val tagSuffix = player.name.lowercase()
    val isActive = gameState.currentPlayer == player && gameState.phase != Phase.GAME_OVER
    val campTitle = if (player == Player.STICKS) "BÂTONNETS" else "CAILLOUX"
    val campDisplayName = if (player == Player.STICKS) "Bâtonnets" else "Cailloux"
    val opponentDisplayName = if (player == Player.STICKS) "Cailloux" else "Bâtonnets"

    val remaining = if (player == Player.STICKS) gameState.sticksRemaining else gameState.stonesRemaining
    val piecesOnBoard = gameState.piecesOnBoard(player)
    val pendingCount = if (player == Player.STICKS) gameState.sticksPendingPlacement else gameState.stonesPendingPlacement
    val capturedCount = gameState.capturesMadeBy(player)

    val borderColor = if (isActive) waliColors.accentGold else waliColors.accentEarth.copy(alpha = 0.25f)
    val backgroundColor = if (isActive) waliColors.surface else waliColors.surface.copy(alpha = 0.68f)

    val isCaptureActive = isActive && WaliEngine.canCapture(gameState)
    val isPlacementRequired = isActive && WaliEngine.isPlacementRequired(gameState)

    val turnBadgeText = when {
        gameState.phase == Phase.GAME_OVER -> "Partie terminée"
        isCaptureActive -> "★ Capturez 1 pion !"
        isPlacementRequired -> "★ Pose obligatoire !"
        isActive -> "À votre tour"
        else -> "En attente"
    }

    val turnBadgeColor = when {
        isCaptureActive -> waliColors.captureAccent
        isPlacementRequired -> waliColors.accentGold
        isActive && player == Player.STICKS -> waliColors.accentEarth
        isActive -> waliColors.boardBorder
        else -> waliColors.surfaceAlt
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(rotationZ = if (isRotated180) 180f else 0f)
            .testTag("player_panel_$tagSuffix")
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
                .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = if (isCompact) 4.dp else 6.dp)
                .testTag("score_card_$tagSuffix"),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 3.dp else 5.dp)
        ) {
            // Ligne 1 : Identité du camp + Indicateur de tour + Minuteur du joueur actif
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 14.dp else 16.dp)
                            .clip(CircleShape)
                            .background(if (player == Player.STICKS) waliColors.stickGradientMiddle else waliColors.stoneGradientMid)
                    )
                    Text(
                        text = campTitle,
                        style = (if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall).copy(
                            fontWeight = FontWeight.Bold,
                            color = waliColors.textPrimary
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(turnBadgeColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .testTag("turn_badge_$tagSuffix")
                    ) {
                        Text(
                            text = turnBadgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) Color.White else waliColors.textSecondary
                            )
                        )
                    }

                    if (isActive && isTimerEnabled) {
                        TurnTimerIndicator(
                            remainingSeconds = timerRemainingSeconds,
                            totalSeconds = timerDurationSeconds,
                            isPaused = isTimerPaused
                        )
                    }
                }
            }

            // Ligne 2 : Réserve / Pions sur le plateau + Compteur de pions adverses capturés
            val reserveText = when (gameState.phase) {
                Phase.PLACEMENT -> "À poser : $remaining"
                Phase.MOVEMENT, Phase.GAME_OVER -> {
                    if (pendingCount > 0) "Pions : $piecesOnBoard (+$pendingCount)"
                    else "Pions : $piecesOnBoard"
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = reserveText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) waliColors.textPrimary else waliColors.textSecondary
                    ),
                    modifier = Modifier.testTag("reserve_count_$tagSuffix")
                )

                Text(
                    text = "Capturés : $capturedCount",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = waliColors.accentGold
                    ),
                    modifier = Modifier.testTag("captured_count_$tagSuffix")
                )
            }

            // Ligne 3 : Boîte de confirmation d'abandon, boîte de réponse au match nul, ou boutons d'action dédiés
            if (isConfirmingForfeitForThisPlayer) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forfeit_confirm_$tagSuffix"),
                    colors = CardDefaults.cardColors(containerColor = waliColors.surfaceAlt),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$campDisplayName, voulez-vous vraiment abandonner ?",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = waliColors.textPrimary
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("forfeit_confirm_text_$tagSuffix")
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismissForfeit,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_cancel_forfeit_$tagSuffix")
                            ) {
                                Text(
                                    text = "Annuler",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = waliColors.textSecondary
                                    )
                                )
                            }
                            Button(
                                onClick = onConfirmForfeit,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_confirm_forfeit_$tagSuffix")
                            ) {
                                Text(
                                    text = "Abandonner",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            } else if (isDrawOfferedToThisPlayer) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("draw_offer_box_$tagSuffix"),
                    colors = CardDefaults.cardColors(containerColor = waliColors.surfaceAlt),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(waliColors.accentGold)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$opponentDisplayName propose un match nul. Accepter ou Refuser ?",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = waliColors.textPrimary
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("draw_offer_text_$tagSuffix")
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onRefuseDraw,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_refuse_draw_$tagSuffix")
                            ) {
                                Text(
                                    text = "Refuser",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = waliColors.textPrimary
                                    )
                                )
                            }
                            Button(
                                onClick = onAcceptDraw,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = waliColors.accentEarth,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_accept_draw_$tagSuffix")
                            ) {
                                Text(
                                    text = "Accepter",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            } else if (gameState.phase != Phase.GAME_OVER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onRequestForfeit,
                        enabled = canForfeit,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .height(if (isCompact) 30.dp else 32.dp)
                            .testTag("btn_forfeit_$tagSuffix")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Abandonner ($campDisplayName)",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Abandonner",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }

                    if (isWaitingForDrawResponse) {
                        Text(
                            text = "Proposition de nul en attente…",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = waliColors.accentGold
                            ),
                            modifier = Modifier.testTag("waiting_for_draw_$tagSuffix")
                        )
                    } else if (gameState.phase == Phase.MOVEMENT) {
                        OutlinedButton(
                            onClick = onProposeDraw,
                            enabled = canProposeDraw,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(if (isCompact) 30.dp else 32.dp)
                                .testTag("btn_propose_draw_$tagSuffix")
                        ) {
                            Text(
                                text = "Proposer match nul",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (canProposeDraw) waliColors.accentGold else waliColors.textSecondary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bandeau central compact (non pivoté) affiché à côté du plateau en mode 2 joueurs local :
 * indique la phase, la durée de la partie et les éventuels messages d'information/erreur.
 */
@Composable
fun FaceToFaceCenterStatusStrip(
    gameState: GameState,
    gameDurationSeconds: Long,
    feedbackMessage: String?,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val waliColors = LocalWaliColors.current
    val phaseLabel = when (gameState.phase) {
        Phase.PLACEMENT -> if (isCompact) "Phase 1 : Pose" else "Phase 1 — Pose des pions"
        Phase.MOVEMENT -> if (isCompact) "Phase 2 : Jeu" else "Phase 2 — Déplacement"
        Phase.GAME_OVER -> "Partie terminée"
    }
    val modeLabel = if (isCompact) " • 2J" else " • 2 Joueurs"
    val activePlayerName = if (gameState.currentPlayer == Player.STICKS) "BÂTONNETS" else "CAILLOUX"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(waliColors.surfaceAlt.copy(alpha = 0.5f))
                    .border(1.dp, waliColors.accentGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = phaseLabel + modeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = waliColors.textPrimary
                    ),
                    modifier = Modifier.testTag("phase_label")
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(waliColors.surfaceAlt.copy(alpha = 0.5f))
                    .border(1.dp, waliColors.boardBorder.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Durée de la partie",
                        tint = waliColors.accentGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = com.example.wali.storage.formatTimerDigital(gameDurationSeconds),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = waliColors.textPrimary
                        ),
                        modifier = Modifier.testTag("game_duration_indicator")
                    )
                }
            }

            Text(
                text = if (gameState.phase != Phase.GAME_OVER) "Tour : $activePlayerName" else "Partie achevée",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = waliColors.accentGold
                ),
                modifier = Modifier.testTag("turn_indicator")
            )
        }

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
                        .padding(top = 3.dp)
                        .testTag("feedback_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) Color(0xFFFFECEB) else waliColors.surfaceAlt
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(
                            if (isError) WaliErrorRed.copy(alpha = 0.4f) else waliColors.accentGold.copy(alpha = 0.4f)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Warning else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isError) WaliErrorRed else waliColors.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall.copy(
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

