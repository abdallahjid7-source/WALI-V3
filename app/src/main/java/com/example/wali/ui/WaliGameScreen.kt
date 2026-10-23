package com.example.wali.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wali.ai.AiDifficulty
import com.example.wali.ads.WaliAdManager
import com.example.wali.model.GameMode
import com.example.wali.model.Phase
import com.example.wali.online.OnlineGameStatus
import com.example.wali.ui.components.DrawProposalDialog
import com.example.wali.ui.components.ForfeitConfirmDialog
import com.example.wali.ui.components.ModeSelectionDialog
import com.example.wali.ui.components.PauseMenuOverlay
import com.example.wali.ui.components.ResetConfirmDialog
import com.example.wali.ui.components.ResumeGameDialog
import com.example.wali.ui.components.ResumeOnlineGameDialog
import com.example.wali.ui.components.ScoreHeader
import com.example.wali.ui.components.VictoryCelebrationOverlay
import com.example.wali.ui.components.VictoryDialog
import com.example.wali.ui.components.WaliBoardView
import com.example.wali.ui.chat.WaliChatButton
import com.example.wali.ui.chat.WaliChatPanel
import com.example.wali.ui.chat.WaliReportDialog
import com.example.wali.ui.theme.LocalWaliColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Écran principal de jeu pour le Wali Songhaï de Tombouctou.
 * Supporte le mode local 2 joueurs, le mode contre la Machine et le mode multijoueur En Ligne (Firebase).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaliGameScreen(
    viewModel: WaliViewModel = viewModel(
        factory = WaliViewModel.provideFactory(LocalContext.current)
    ),
    onOpenRules: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenOnline: () -> Unit = {},
    onOpenScoreboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAdActive by WaliAdManager.isAdActive.collectAsState()
    val context = LocalContext.current
    val colors = LocalWaliColors.current

    // Cycle de vie : démarre la musique à l'arrivée/reprise, la met en pause lors de la mise en arrière-plan
    // et gère la suspension du minuteur
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onAppForegrounded()
                    viewModel.startMusic()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onAppBackgrounded()
                    viewModel.pauseMusic()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.runtime.LaunchedEffect(isAdActive) {
        viewModel.setAdShowing(isAdActive)
    }

    // Gestion naturelle du bouton retour Android pour fermer les dialogues ouverts
    BackHandler(
        enabled = !isAdActive && (uiState.showPauseMenu || uiState.showModeSelectionDialog || uiState.showResetConfirmation || uiState.showResumeDialog || uiState.showResumeOnlineDialog || uiState.showForfeitConfirmation)
    ) {
        if (uiState.showForfeitConfirmation) {
            viewModel.dismissForfeitConfirmation()
        } else if (uiState.showPauseMenu) {
            viewModel.resumeGame()
        } else if (uiState.showModeSelectionDialog) {
            viewModel.dismissModeSelectionDialog()
        } else if (uiState.showResetConfirmation) {
            viewModel.dismissNewGameDialog()
        } else if (uiState.showResumeDialog) {
            viewModel.declineResumeAndStartNewGame()
        } else if (uiState.showResumeOnlineDialog) {
            viewModel.dismissResumeOnlineDialog()
        }
    }

    // Bouton retour en mode spectateur : quitte immédiatement sans dialogue d'abandon
    BackHandler(
        enabled = !isAdActive && uiState.gameMode == GameMode.ONLINE && uiState.isSpectator &&
            !uiState.showPauseMenu && !uiState.showModeSelectionDialog
    ) {
        viewModel.leaveOnlineGame()
    }

    val subtitle = when (uiState.gameMode) {
        GameMode.TWO_PLAYERS -> "Tombouctou • 2 Joueurs Local"
        GameMode.VS_COMPUTER -> {
            val diffLabel = when (uiState.aiDifficulty) {
                AiDifficulty.EASY -> "Facile"
                AiDifficulty.MEDIUM -> "Moyen"
                AiDifficulty.HARD -> "Difficile"
            }
            "Tombouctou • vs IA ($diffLabel)"
        }
        GameMode.ONLINE -> {
            val code = uiState.onlineGameSession?.code ?: uiState.waitingForOpponentCode ?: "..."
            val specCode = uiState.spectatorCode
            if (uiState.isSpectator) {
                "Spectateur • Code: ${specCode.ifBlank { code }}"
            } else if (specCode.isNotBlank()) {
                "En Ligne • Code: $code • Spect: $specCode"
            } else {
                "En Ligne • Code: $code"
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            val configuration = LocalConfiguration.current
            val isCompactWidth = configuration.screenWidthDp < 400
            val iconSize = if (isCompactWidth) 38.dp else 48.dp

            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (configuration.screenWidthDp < 360) "Wali" else "Wali Songhaï",
                            style = (if (isCompactWidth) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                letterSpacing = 0.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.textSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = if (isCompactWidth) 10.sp else 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (uiState.gameMode == GameMode.ONLINE) {
                        WaliChatButton(
                            unreadCount = uiState.chatState.unreadCount,
                            isOpen = uiState.chatState.isOpen,
                            onClick = {
                                if (uiState.chatState.isOpen) {
                                    viewModel.closeChat()
                                } else {
                                    viewModel.openChat()
                                }
                            },
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.openPauseMenu() },
                        modifier = Modifier
                            .testTag("btn_pause_game")
                            .size(iconSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Mettre en pause",
                            tint = colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = onOpenScoreboard,
                        modifier = Modifier
                            .testTag("btn_scoreboard")
                            .size(iconSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = "Tableau des scores",
                            tint = colors.accentGold
                        )
                    }

                    IconButton(
                        onClick = onOpenRules,
                        modifier = Modifier
                            .testTag("btn_rules")
                            .size(iconSize)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Règles du jeu",
                            tint = colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .testTag("btn_settings")
                            .size(iconSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Réglages",
                            tint = colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.openModeSelection() },
                        modifier = Modifier
                            .testTag("btn_mode_selection")
                            .size(iconSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Changer de mode",
                            tint = colors.textPrimary
                        )
                    }

                    if (uiState.canDeclareForfeit) {
                        IconButton(
                            onClick = { viewModel.requestForfeit() },
                            modifier = Modifier
                                .testTag("forfeit_button")
                                .size(iconSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Déclarer forfait (vous perdez)",
                                tint = colors.textPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (uiState.gameMode == GameMode.ONLINE) {
                                viewModel.leaveOnlineGame()
                            } else {
                                viewModel.onNewGameRequested()
                            }
                        },
                        modifier = Modifier
                            .testTag("btn_reset_game")
                            .size(iconSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (uiState.gameMode == GameMode.ONLINE) "Quitter la partie en ligne" else "Recommencer la partie",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val totalWidth = maxWidth
            val totalHeight = maxHeight
            val isLandscape = totalWidth > totalHeight

            if (isLandscape) {
                // --- DISPOSITION PAYSAGE (DOUBLE PANNEAU ADAPTATIF) ---
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Panneau latéral gauche : Informations, statuts des joueurs et messages
                    val sidePaneWidth = (totalWidth * 0.38f).coerceIn(240.dp, 380.dp)
                    Column(
                        modifier = Modifier
                            .width(sidePaneWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Bandeau de déconnexion / abandon en ligne
                        if (uiState.gameMode == GameMode.ONLINE && uiState.onlineGameSession?.status == OnlineGameStatus.ONGOING) {
                            if (uiState.canDeclareAbandonment) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Adversaire inactif (> 2 min).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = { viewModel.declareOpponentAbandoned() },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.testTag("online_abandon_button")
                                        ) {
                                            Text("Adversaire inactif — réclamer la victoire")
                                        }
                                    }
                                }
                            } else if (uiState.isOpponentDisconnected) {
                                Text(
                                    text = "Connexion instable / Adversaire déconnecté…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .testTag("online_disconnect_indicator")
                                )
                            }
                        }

                        if (uiState.gameMode == GameMode.ONLINE && (uiState.spectatorCode.isNotBlank() || uiState.isSpectator)) {
                            OnlineInGameSpectatorBar(
                                spectatorCode = uiState.spectatorCode,
                                isSpectator = uiState.isSpectator,
                                onLeaveSpectator = { viewModel.leaveOnlineGame() }
                            )
                        }

                        // ScoreHeader compact
                        ScoreHeader(
                            gameState = uiState.gameState,
                            feedbackMessage = uiState.feedbackMessage,
                            gameMode = uiState.gameMode,
                            humanPlayer = uiState.humanPlayer,
                            localPlayerRole = uiState.localPlayerRole,
                            isSpectator = uiState.isSpectator,
                            isAiThinking = uiState.isAiThinking,
                            isTimerEnabled = uiState.isTimerEnabled,
                            timerRemainingSeconds = uiState.timerRemainingSeconds,
                            timerDurationSeconds = uiState.timerDurationSeconds,
                            isTimerPaused = uiState.isTimerPaused,
                            gameDurationSeconds = uiState.gameDurationSeconds,
                            salonMyWins = uiState.salonMyWins,
                            salonOpponentWins = uiState.salonOpponentWins,
                            salonSticksPlayerWins = uiState.salonSticksPlayerWins,
                            salonStonesPlayerWins = uiState.salonStonesPlayerWins,
                            salonGamesPlayed = uiState.salonGamesPlayed,
                            canProposeDraw = uiState.canProposeDraw,
                            isWaitingForDrawResponse = uiState.isWaitingForDrawResponse,
                            onProposeDraw = { viewModel.proposeDraw() },
                            isCompact = true
                        )

                        if (totalHeight >= 420.dp) {
                            Spacer(modifier = Modifier.height(6.dp))
                            // Pied de page informatif discret
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceAlt.copy(alpha = 0.7f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Posez vos pions sans aligner 3 pièces, puis déplacez-les orthogonalement.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = colors.textSecondary,
                                        fontSize = 10.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Panneau droit : Plateau de jeu occupant toute la hauteur disponible sans scroll
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WaliBoardView(
                            board = uiState.gameState.board,
                            selectedPosition = uiState.selectedPosition,
                            highlightedMoves = uiState.highlightedMoves,
                            capturablePositions = uiState.capturablePositions,
                            onCellClicked = { pos -> viewModel.onCellClicked(pos) },
                            lastMove = uiState.lastMove,
                            showCellNumbers = uiState.isCellNumbersEnabled,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // --- DISPOSITION PORTRAIT (ADAPTATIVE PHONES, PLIANTS & TABLETTES) ---
                val hasSufficientHeight = totalHeight >= 550.dp
                if (hasSufficientHeight) {
                    // Disposition non-déroulante : le plateau s'adapte précisément à la hauteur restante
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Bandeau de déconnexion / abandon en ligne
                            if (uiState.gameMode == GameMode.ONLINE && uiState.onlineGameSession?.status == OnlineGameStatus.ONGOING) {
                                if (uiState.canDeclareAbandonment) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Adversaire inactif (> 2 min).",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                onClick = { viewModel.declareOpponentAbandoned() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.testTag("online_abandon_button")
                                            ) {
                                                Text("Adversaire inactif — réclamer la victoire")
                                            }
                                        }
                                    }
                                } else if (uiState.isOpponentDisconnected) {
                                    Text(
                                        text = "Connexion instable / Adversaire déconnecté…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .padding(vertical = 2.dp)
                                            .testTag("online_disconnect_indicator")
                                    )
                                }
                            }

                            if (uiState.gameMode == GameMode.ONLINE && (uiState.spectatorCode.isNotBlank() || uiState.isSpectator)) {
                                OnlineInGameSpectatorBar(
                                    spectatorCode = uiState.spectatorCode,
                                    isSpectator = uiState.isSpectator,
                                    onLeaveSpectator = { viewModel.leaveOnlineGame() }
                                )
                            }

                            // En-tête des scores, phase et statut de réflexion IA
                            ScoreHeader(
                                gameState = uiState.gameState,
                                feedbackMessage = uiState.feedbackMessage,
                                gameMode = uiState.gameMode,
                                humanPlayer = uiState.humanPlayer,
                                localPlayerRole = uiState.localPlayerRole,
                                isSpectator = uiState.isSpectator,
                                isAiThinking = uiState.isAiThinking,
                                isTimerEnabled = uiState.isTimerEnabled,
                                timerRemainingSeconds = uiState.timerRemainingSeconds,
                                timerDurationSeconds = uiState.timerDurationSeconds,
                                isTimerPaused = uiState.isTimerPaused,
                                gameDurationSeconds = uiState.gameDurationSeconds,
                                salonMyWins = uiState.salonMyWins,
                                salonOpponentWins = uiState.salonOpponentWins,
                                salonSticksPlayerWins = uiState.salonSticksPlayerWins,
                                salonStonesPlayerWins = uiState.salonStonesPlayerWins,
                                salonGamesPlayed = uiState.salonGamesPlayed,
                                canProposeDraw = uiState.canProposeDraw,
                                isWaitingForDrawResponse = uiState.isWaitingForDrawResponse,
                                onProposeDraw = { viewModel.proposeDraw() },
                                isCompact = totalHeight < 680.dp
                            )
                        }

                        // Plateau interactif 5x6 occupant la hauteur restante sans scrolling
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            WaliBoardView(
                                board = uiState.gameState.board,
                                selectedPosition = uiState.selectedPosition,
                                highlightedMoves = uiState.highlightedMoves,
                                capturablePositions = uiState.capturablePositions,
                                onCellClicked = { pos -> viewModel.onCellClicked(pos) },
                                lastMove = uiState.lastMove,
                                showCellNumbers = uiState.isCellNumbersEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Pied de page informatif discret (affiché si hauteur suffisante)
                        if (totalHeight >= 640.dp) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 620.dp)
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceAlt.copy(alpha = 0.7f))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Posez vos pions sans aligner 3 pièces, puis déplacez-les orthogonalement.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                } else {
                    // Écrans très courts ou mode multi-fenêtres : déroulement sécurisé avec composants compacts
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (uiState.gameMode == GameMode.ONLINE && uiState.onlineGameSession?.status == OnlineGameStatus.ONGOING) {
                                if (uiState.canDeclareAbandonment) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Adversaire inactif (> 2 min).",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                onClick = { viewModel.declareOpponentAbandoned() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.testTag("online_abandon_button")
                                            ) {
                                                Text("Adversaire inactif — réclamer la victoire")
                                            }
                                        }
                                    }
                                } else if (uiState.isOpponentDisconnected) {
                                    Text(
                                        text = "Connexion instable / Adversaire déconnecté…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .padding(vertical = 2.dp)
                                            .testTag("online_disconnect_indicator")
                                    )
                                }
                            }

                            if (uiState.gameMode == GameMode.ONLINE && (uiState.spectatorCode.isNotBlank() || uiState.isSpectator)) {
                                OnlineInGameSpectatorBar(
                                    spectatorCode = uiState.spectatorCode,
                                    isSpectator = uiState.isSpectator,
                                    onLeaveSpectator = { viewModel.leaveOnlineGame() }
                                )
                            }

                            ScoreHeader(
                                gameState = uiState.gameState,
                                feedbackMessage = uiState.feedbackMessage,
                                gameMode = uiState.gameMode,
                                humanPlayer = uiState.humanPlayer,
                                localPlayerRole = uiState.localPlayerRole,
                                isSpectator = uiState.isSpectator,
                                isAiThinking = uiState.isAiThinking,
                                isTimerEnabled = uiState.isTimerEnabled,
                                timerRemainingSeconds = uiState.timerRemainingSeconds,
                                timerDurationSeconds = uiState.timerDurationSeconds,
                                isTimerPaused = uiState.isTimerPaused,
                                gameDurationSeconds = uiState.gameDurationSeconds,
                                salonMyWins = uiState.salonMyWins,
                                salonOpponentWins = uiState.salonOpponentWins,
                                salonSticksPlayerWins = uiState.salonSticksPlayerWins,
                                salonStonesPlayerWins = uiState.salonStonesPlayerWins,
                                salonGamesPlayed = uiState.salonGamesPlayed,
                                canProposeDraw = uiState.canProposeDraw,
                                isWaitingForDrawResponse = uiState.isWaitingForDrawResponse,
                                onProposeDraw = { viewModel.proposeDraw() },
                                isCompact = true
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            WaliBoardView(
                                board = uiState.gameState.board,
                                selectedPosition = uiState.selectedPosition,
                                highlightedMoves = uiState.highlightedMoves,
                                capturablePositions = uiState.capturablePositions,
                                onCellClicked = { pos -> viewModel.onCellClicked(pos) },
                                lastMove = uiState.lastMove,
                                showCellNumbers = uiState.isCellNumbersEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            // Panneau repliable de chat en mode en ligne (ne bloque pas le jeu)
            if (uiState.gameMode == GameMode.ONLINE && uiState.chatState.isOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 2.dp),
                    contentAlignment = if (isLandscape) Alignment.BottomEnd else Alignment.BottomCenter
                ) {
                    WaliChatPanel(
                        chatState = uiState.chatState,
                        localPlayerRole = uiState.localPlayerRole,
                        localPlayerUid = uiState.localPlayerUid,
                        isLandscape = isLandscape,
                        onClose = { viewModel.closeChat() },
                        onSendMessage = { text -> viewModel.sendChatMessage(text) },
                        onSendQuickMessage = { quickText -> viewModel.sendQuickMessage(quickText) },
                        onInputChanged = { text -> viewModel.onChatInputChanged(text) },
                        onBlockOpponent = { viewModel.blockOpponentInChat() },
                        onOpenReport = { viewModel.openReportDialog() },
                        onDismissWarning = { viewModel.dismissChatWarningBanner() }
                    )
                }
            }
        }

        // --- DIALOGUES & ANIMATION DE VICTOIRE / MATCH NUL ---
        if (!isAdActive && uiState.gameState.phase == Phase.GAME_OVER && (uiState.gameState.winner != null || uiState.isDrawGameOver)) {
            if (uiState.gameState.winner != null) {
                // Animation spectaculaire de confettis et feux de joie en arrière-plan plein écran
                VictoryCelebrationOverlay()
            }

            VictoryDialog(
                gameState = uiState.gameState,
                gameMode = uiState.gameMode,
                humanPlayer = uiState.humanPlayer,
                localPlayerRole = uiState.localPlayerRole,
                isSpectator = uiState.isSpectator,
                gameDurationSeconds = uiState.gameDurationSeconds,
                scoreboardStats = uiState.scoreboardStats,
                gameOverReason = uiState.gameOverReason,
                isDoubleWin = uiState.isDoubleWin,
                salonMyWins = uiState.salonMyWins,
                salonOpponentWins = uiState.salonOpponentWins,
                salonSticksPlayerWins = uiState.salonSticksPlayerWins,
                salonStonesPlayerWins = uiState.salonStonesPlayerWins,
                salonGamesPlayed = uiState.salonGamesPlayed,
                isWaitingForRematch = uiState.isWaitingForRematch,
                isRematchRequestedByOpponent = uiState.isRematchRequestedByOpponent,
                rematchTimeoutRemainingSeconds = uiState.rematchTimeoutRemainingSeconds,
                rematchStatusMessage = uiState.rematchStatusMessage,
                onRequestRematch = { viewModel.requestOnlineRematch() },
                onCancelRematch = { viewModel.cancelOnlineRematchRequest() },
                onLeaveOnlineSalon = { viewModel.leaveOnlineSalonAfterGame() },
                onOpenScoreboard = onOpenScoreboard,
                onNewGameClicked = {
                    val activity = context as? Activity
                    if (activity != null && uiState.gameMode != GameMode.ONLINE && !uiState.chatState.isOpen) {
                        WaliAdManager.showInterstitialIfEligible(activity) {
                            viewModel.onGameOverContinue()
                        }
                    } else {
                        viewModel.onGameOverContinue()
                    }
                }
            )
        }

        if (!isAdActive && uiState.isDrawOfferedToMe) {
            DrawProposalDialog(
                onAccept = { viewModel.acceptDraw() },
                onRefuse = { viewModel.refuseDraw() }
            )
        }

        if (!isAdActive && uiState.showForfeitConfirmation) {
            ForfeitConfirmDialog(
                onConfirm = { viewModel.confirmForfeit() },
                onDismiss = { viewModel.dismissForfeitConfirmation() }
            )
        }

        if (!isAdActive && uiState.showPauseMenu) {
            PauseMenuOverlay(
                onResume = { viewModel.resumeGame() },
                onRestart = { viewModel.restartCurrentGame() },
                onMainMenu = { viewModel.returnToMainMenu() },
                onScoreboard = onOpenScoreboard,
                onForfeit = if (uiState.canDeclareForfeit) { { viewModel.requestForfeit() } } else null,
                onDismiss = { viewModel.resumeGame() }
            )
        }

        if (!isAdActive && uiState.showResetConfirmation) {
            ResetConfirmDialog(
                onConfirm = { viewModel.confirmNewGame() },
                onChangeMode = {
                    viewModel.dismissNewGameDialog()
                    viewModel.openModeSelection()
                },
                onDismiss = { viewModel.dismissNewGameDialog() }
            )
        }

        if (!isAdActive && uiState.showModeSelectionDialog) {
            ModeSelectionDialog(
                currentMode = uiState.gameMode,
                currentHumanPlayer = uiState.humanPlayer,
                currentDifficulty = uiState.aiDifficulty,
                onStartGame = { mode, side, diff ->
                    viewModel.startNewGame(mode, side, diff)
                },
                onOpenOnline = {
                    viewModel.dismissModeSelectionDialog()
                    onOpenOnline()
                },
                onOpenScoreboard = onOpenScoreboard,
                onDismiss = { viewModel.dismissModeSelectionDialog() },
                onOpenRules = onOpenRules,
                onOpenSettings = onOpenSettings
            )
        }

        if (!isAdActive && uiState.showResumeDialog) {
            ResumeGameDialog(
                onResume = { viewModel.resumeSavedGame() },
                onDeclineNewGame = { viewModel.declineResumeAndStartNewGame() }
            )
        }

        if (!isAdActive && uiState.showResumeOnlineDialog && uiState.pendingResumeOnlineGame != null) {
            ResumeOnlineGameDialog(
                session = uiState.pendingResumeOnlineGame!!,
                onResume = { viewModel.resumePendingOnlineGame() },
                onDismiss = { viewModel.dismissResumeOnlineDialog() }
            )
        }

        // Boîte de dialogue de signalement de l'adversaire
        if (uiState.chatState.showReportDialog) {
            WaliReportDialog(
                onDismiss = { viewModel.closeReportDialog() },
                onSubmitReport = { reason -> viewModel.submitOpponentReport(reason) }
            )
        }

        // Avis de confirmation de signalement
        if (uiState.chatState.reportNotice != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.dismissReportNotice() },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.dismissReportNotice() }) {
                        Text("D'accord", color = colors.accentGold)
                    }
                },
                title = { Text("Signalement envoyé", color = colors.textPrimary) },
                text = { Text(uiState.chatState.reportNotice ?: "", color = colors.textSecondary) },
                containerColor = colors.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/**
 * Bandeau compact affiché en cours de partie en ligne :
 * - Permet au créateur et à l'adversaire de voir, copier et partager le code spectateur à tout moment.
 * - Permet aux spectateurs de voir le code du salon observé et de quitter la partie d'un clic sans confirmation.
 */
@Composable
private fun OnlineInGameSpectatorBar(
    spectatorCode: String,
    isSpectator: Boolean,
    onLeaveSpectator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaliColors.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceAlt.copy(alpha = 0.85f))
            .border(1.dp, colors.accentGold.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("ingame_spectator_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = colors.accentGold,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (spectatorCode.isNotBlank()) {
                    if (isSpectator) "Mode Spectateur • $spectatorCode" else "Code Spectateur : $spectatorCode"
                } else {
                    "Mode Spectateur"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ingame_spectator_code_text")
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (spectatorCode.isNotBlank()) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Code Spectateur Wali Songhaï", spectatorCode)
                        clipboard?.setPrimaryClip(clip)
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("copy_ingame_spectator_code_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copier le code spectateur",
                        tint = colors.accentGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (copied) "Copié" else "Copier",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.accentGold,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    )
                }

                TextButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Observe notre partie de Wali Songhaï en direct avec le code spectateur : $spectatorCode"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Partager le code spectateur"))
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("share_ingame_spectator_code_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Partager le code spectateur",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (isSpectator) {
                OutlinedButton(
                    onClick = onLeaveSpectator,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("spectator_leave_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Quitter",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

