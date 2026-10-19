package com.example.wali.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import com.example.wali.storage.ScoreboardStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wali.ai.AiDifficulty
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Player
import com.example.wali.ui.theme.LocalWaliColors

/**
 * Dialogue de célébration de victoire avec affichage clair de la raison
 * (adversaire éliminé ou adversaire bloqué).
 */
@Composable
fun VictoryDialog(
    gameState: GameState,
    gameMode: GameMode = GameMode.TWO_PLAYERS,
    humanPlayer: Player = Player.STICKS,
    localPlayerRole: Player? = null,
    gameDurationSeconds: Long = 0L,
    scoreboardStats: ScoreboardStats? = null,
    gameOverReason: String? = null,
    salonMyWins: Int = 0,
    salonOpponentWins: Int = 0,
    salonGamesPlayed: Int = 1,
    isWaitingForRematch: Boolean = false,
    isRematchRequestedByOpponent: Boolean = false,
    rematchTimeoutRemainingSeconds: Int? = null,
    rematchStatusMessage: String? = null,
    onRequestRematch: (() -> Unit)? = null,
    onCancelRematch: (() -> Unit)? = null,
    onLeaveOnlineSalon: (() -> Unit)? = null,
    onOpenScoreboard: (() -> Unit)? = null,
    onNewGameClicked: () -> Unit
) {
    val colors = LocalWaliColors.current
    val winner = gameState.winner ?: return
    val winnerLabel = if (winner == Player.STICKS) "BÂTONNETS" else "CAILLOUX"
    val loser = winner.opponent

    val loserPieces = gameState.piecesOnBoard(loser)
    val isAbandon = gameOverReason == "Abandon" ||
            gameOverReason == "Abandon de l'adversaire" ||
            gameState.moveHistory.lastOrNull()?.contains("abandon", ignoreCase = true) == true ||
            gameState.moveHistory.lastOrNull()?.contains("forfait", ignoreCase = true) == true
    val isReducedToTwo = gameOverReason == "Réduit à deux pions" ||
            (gameState.ruleOptions.defeatAtTwoPieces && !gameState.hasPendingPlacements(loser) && loserPieces == 2) ||
            gameState.moveHistory.lastOrNull()?.contains("réduit à deux pions", ignoreCase = true) == true

    val reason = when {
        isAbandon -> "Abandon"
        loserPieces == 0 -> "Adversaire éliminé (tous les pions ont été capturés)"
        isReducedToTwo -> "Réduit à deux pions"
        else -> "Adversaire bloqué (aucun déplacement orthogonal possible)"
    }

    val subtitle = when (gameMode) {
        GameMode.VS_COMPUTER -> if (winner == humanPlayer) "Félicitations, vous avez battu la Machine !" else "L'ordinateur remporte la partie."
        GameMode.ONLINE -> {
            val myRole = localPlayerRole ?: humanPlayer
            if (winner == myRole) "Félicitations, vous remportez la partie en ligne !" else "Votre adversaire remporte la partie en ligne."
        }
        else -> reason
    }

    // Animations Compose pour le trophée : surgissement dynamique avec rebond et halo doré pulsant
    val trophyScale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        trophyScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 650,
                easing = FastOutSlowInEasing
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "trophyGlow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(12.dp)
                .testTag("victory_dialog")
                .border(2.5.dp, colors.accentGold, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Trophée avec animation de surgissement et aura dorée pulsante
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            scaleX = trophyScale.value
                            scaleY = trophyScale.value
                        }
                        .clip(CircleShape)
                        .background(colors.accentGold.copy(alpha = pulseGlow))
                        .border(2.dp, colors.accentGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🏆", fontSize = 32.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Victoire des $winnerLabel !",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )

                if (gameMode == GameMode.VS_COMPUTER || isAbandon || (gameMode == GameMode.ONLINE && isReducedToTwo)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAbandon) "Raison : Abandon" else reason,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textSecondary.copy(alpha = 0.8f)
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                // Score cumulé du salon en mode EN LIGNE (indexé par uid)
                if (gameMode == GameMode.ONLINE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accentGold.copy(alpha = 0.14f))
                            .border(1.dp, colors.accentGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("online_salon_score_victory"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Score du salon : $salonMyWins – $salonOpponentWins",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.accentGold
                            )
                        )
                        Text(
                            text = "Parties jouées dans ce salon : $salonGamesPlayed",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.textSecondary
                            )
                        )
                    }
                }

                // Durée de la partie et score persistant local
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceAlt.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⏱ Durée : ${com.example.wali.storage.formatDuration(gameDurationSeconds)}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.accentGold
                        )
                    )
                    if (scoreboardStats != null && (scoreboardStats.totalWins > 0 || scoreboardStats.totalLosses > 0)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium.copy(color = colors.textSecondary)
                        )
                        Text(
                            text = "${scoreboardStats.totalWins}V - ${scoreboardStats.totalLosses}D",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (gameMode == GameMode.ONLINE) {
                    if (!rematchStatusMessage.isNullOrBlank()) {
                        Text(
                            text = rematchStatusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = colors.accentEarth,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .testTag("rematch_status_message")
                        )
                    } else if (isRematchRequestedByOpponent && !isWaitingForRematch) {
                        Text(
                            text = "Votre adversaire propose une revanche !",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = colors.accentGold,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .testTag("opponent_rematch_offer")
                        )
                    }

                    if (isWaitingForRematch) {
                        val waitText = if (rematchTimeoutRemainingSeconds != null) {
                            "En attente de l'adversaire… (${rematchTimeoutRemainingSeconds}s)"
                        } else {
                            "En attente de l'adversaire…"
                        }
                        Text(
                            text = waitText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.accentGold,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .testTag("waiting_for_rematch_indicator")
                        )

                        OutlinedButton(
                            onClick = { onCancelRematch?.invoke() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_cancel_rematch"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Annuler la demande",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentEarth
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { (onLeaveOnlineSalon ?: onNewGameClicked).invoke() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_leave_online_salon"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Quitter le salon",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            )
                        }
                    } else {
                        Button(
                            onClick = { (onRequestRematch ?: onNewGameClicked).invoke() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_online_rematch"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentEarth,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Revanche",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { (onLeaveOnlineSalon ?: onNewGameClicked).invoke() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_leave_online_salon"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Quitter le salon",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onNewGameClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_victory_new_game"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentEarth,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nouvelle partie",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (onOpenScoreboard != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenScoreboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_victory_scoreboard"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = null,
                            tint = colors.accentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tableau des scores",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialogue de confirmation avant réinitialisation d'une partie en cours.
 */
@Composable
fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onChangeMode: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaliColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reset_confirm_dialog"),
        shape = RoundedCornerShape(16.dp),
        containerColor = colors.surface,
        title = {
            Text(
                text = "Recommencer la partie ?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
        },
        text = {
            Text(
                text = "Voulez-vous relancer la partie ou changer de mode de jeu ?",
                style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onChangeMode,
                    modifier = Modifier.testTag("btn_change_mode")
                ) {
                    Text("Changer de mode", color = colors.textPrimary)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentEarth,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("btn_confirm_reset")
                ) {
                    Text("Recommencer")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_reset")
            ) {
                Text("Annuler", color = colors.textPrimary)
            }
        }
    )
}

/**
 * Dialogue de confirmation obligatoire avant déclaration de forfait.
 */
@Composable
fun ForfeitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaliColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("forfeit_confirm_dialog"),
        shape = RoundedCornerShape(16.dp),
        containerColor = colors.surface,
        icon = {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Déclarer forfait",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
        },
        text = {
            Text(
                text = "Abandonner la partie ? Votre adversaire sera déclaré vainqueur.",
                style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_forfeit_button")
            ) {
                Text("Abandonner")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_forfeit_button")
            ) {
                Text(
                    text = "Annuler",
                    color = colors.textSecondary
                )
            }
        }
    )
}

/**
 * Menu de pause (overlay) permettant de :
 * 1. Reprendre la partie (Resume)
 * 2. Recommencer la partie en cours (Restart)
 * 3. Revenir au menu principal (Main Menu / Sélection de mode)
 * 4. Déclarer forfait
 */
@Composable
fun PauseMenuOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit,
    onScoreboard: (() -> Unit)? = null,
    onForfeit: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = LocalWaliColors.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(12.dp)
                .testTag("pause_menu_overlay")
                .border(2.dp, colors.accentGold, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // En-tête avec titre épuré
                Text(
                    text = "PARTIE EN PAUSE",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 1.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Que souhaitez-vous faire ?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = colors.textSecondary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bouton 1 : Reprendre la partie
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentEarth,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_pause_resume")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reprendre",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bouton 2 : Recommencer la partie actuelle
                OutlinedButton(
                    onClick = onRestart,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_pause_restart")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recommencer la partie",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bouton 3 : Menu principal
                OutlinedButton(
                    onClick = onMainMenu,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_pause_main_menu")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Menu principal",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    )
                }

                if (onScoreboard != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onScoreboard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_pause_scoreboard")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = null,
                            tint = colors.accentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tableau des scores",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        )
                    }
                }

                if (onForfeit != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onForfeit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_pause_forfeit")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Déclarer forfait (vous perdez)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialogue de sélection du mode de jeu (Humain vs Humain ou Humain vs Machine).
 * Permet de choisir le camp (BÂTONNETS ou CAILLOUX) et la difficulté (Facile, Moyen, Difficile).
 */
@Composable
fun ModeSelectionDialog(
    currentMode: GameMode,
    currentHumanPlayer: Player,
    currentDifficulty: AiDifficulty,
    onStartGame: (GameMode, Player, AiDifficulty) -> Unit,
    onDismiss: () -> Unit,
    onOpenRules: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenOnline: (() -> Unit)? = null,
    onOpenScoreboard: (() -> Unit)? = null
) {
    val colors = LocalWaliColors.current
    var selectedMode by remember { mutableStateOf(if (currentMode == GameMode.ONLINE) GameMode.TWO_PLAYERS else currentMode) }
    var selectedSide by remember { mutableStateOf(currentHumanPlayer) }
    var selectedDifficulty by remember { mutableStateOf(currentDifficulty) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(12.dp)
                .testTag("mode_selection_dialog")
                .border(2.dp, colors.accentGold, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Contre qui voulez-vous jouer ?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sélecteur de mode (2 Joueurs vs Machine)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableChip(
                        title = "2 Joueurs",
                        subtitle = "Sur le même écran",
                        isSelected = selectedMode == GameMode.TWO_PLAYERS,
                        onClick = { selectedMode = GameMode.TWO_PLAYERS },
                        modifier = Modifier.weight(1f).testTag("mode_chip_two_players")
                    )
                    SelectableChip(
                        title = "Machine",
                        subtitle = "Contre l'IA",
                        isSelected = selectedMode == GameMode.VS_COMPUTER,
                        onClick = { selectedMode = GameMode.VS_COMPUTER },
                        modifier = Modifier.weight(1f).testTag("mode_chip_vs_computer")
                    )
                }

                if (onOpenOnline != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onDismiss()
                                onOpenOnline()
                            }
                            .border(1.5.dp, colors.accentGold, RoundedCornerShape(12.dp))
                            .testTag("btn_mode_online"),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.accentGold.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = null,
                                    tint = colors.accentGold,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Jouer en Ligne",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accentGold
                                        )
                                    )
                                    Text(
                                        text = "2 joueurs à distance via Internet",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = colors.textSecondary
                                        )
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = colors.accentGold
                            )
                        }
                    }
                }

                // Options spécifiques à la Machine
                if (selectedMode == GameMode.VS_COMPUTER) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Choisissez votre camp :",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        ),
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectableChip(
                            title = "BÂTONNETS",
                            subtitle = "Commence en premier",
                            isSelected = selectedSide == Player.STICKS,
                            onClick = { selectedSide = Player.STICKS },
                            modifier = Modifier.weight(1f).testTag("side_chip_sticks")
                        )
                        SelectableChip(
                            title = "CAILLOUX",
                            subtitle = "L'ordinateur commence",
                            isSelected = selectedSide == Player.STONES,
                            onClick = { selectedSide = Player.STONES },
                            modifier = Modifier.weight(1f).testTag("side_chip_stones")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Niveau de difficulté :",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        ),
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SelectableChip(
                            title = "Facile",
                            subtitle = "Coups simples",
                            isSelected = selectedDifficulty == AiDifficulty.EASY,
                            onClick = { selectedDifficulty = AiDifficulty.EASY },
                            modifier = Modifier.weight(1f).testTag("diff_chip_easy")
                        )
                        SelectableChip(
                            title = "Moyen",
                            subtitle = "Tactique",
                            isSelected = selectedDifficulty == AiDifficulty.MEDIUM,
                            onClick = { selectedDifficulty = AiDifficulty.MEDIUM },
                            modifier = Modifier.weight(1f).testTag("diff_chip_medium")
                        )
                        SelectableChip(
                            title = "Difficile",
                            subtitle = "Minimax",
                            isSelected = selectedDifficulty == AiDifficulty.HARD,
                            onClick = { selectedDifficulty = AiDifficulty.HARD },
                            modifier = Modifier.weight(1f).testTag("diff_chip_hard")
                        )
                    }
                }

                if (onOpenRules != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onOpenRules,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_mode_dialog_rules")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = null,
                            tint = colors.accentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Comment jouer au Wali ? (Règles)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                if (onOpenSettings != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_mode_dialog_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = colors.accentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Réglages (Sons, Musique, Thème)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                if (onOpenScoreboard != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onOpenScoreboard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_mode_dialog_scoreboard")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = null,
                            tint = colors.accentGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tableau des scores & Statistiques",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancel_mode_selection")
                    ) {
                        Text("Annuler", color = colors.textPrimary)
                    }

                    Button(
                        onClick = {
                            onStartGame(selectedMode, selectedSide, selectedDifficulty)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentEarth,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_confirm_start_game")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Jouer",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val borderColor = if (isSelected) colors.accentGold else colors.surfaceAlt
    val backgroundColor = if (isSelected) colors.surfaceAlt else colors.surface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = colors.textPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    color = colors.textSecondary
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
