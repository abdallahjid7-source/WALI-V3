package com.example.wali.ui

import com.example.wali.ai.AiDifficulty
import com.example.wali.chat.WaliChatUiState
import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Phase
import com.example.wali.model.PieceMove
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.storage.GameResultRecord
import com.example.wali.storage.ScoreboardStats
import com.example.wali.ui.theme.WaliThemePalette

/**
 * État complet de l'interface utilisateur pour le jeu Wali Songhaï.
 *
 * @property gameState État pur du jeu issu de WaliEngine.
 * @property gameMode Mode de jeu (Deux Joueurs ou Contre la Machine).
 * @property humanPlayer Camp choisi par le joueur humain en mode Machine (par défaut STICKS).
 * @property aiDifficulty Niveau de difficulté de l'ordinateur.
 * @property isAiThinking Indique si l'ordinateur est en train de calculer son coup.
 * @property selectedPosition Case du pion actuellement sélectionné par le joueur en phase de déplacement.
 * @property highlightedMoves Ensemble des cases vides directement accessibles pour le pion sélectionné.
 * @property capturablePositions Ensemble des pions adverses capturables lorsqu'une capture est requise.
 * @property feedbackMessage Message d'avertissement ou d'information (ex: coup interdit, formation de ligne).
 * @property showResetConfirmation Indique si la boîte de dialogue de confirmation de nouvelle partie est visible.
 * @property showModeSelectionDialog Indique si la boîte de dialogue de choix de mode/camp/difficulté est visible.
 * @property sfxVolume Volume des effets sonores (0f à 1f).
 * @property musicVolume Volume de la musique d'ambiance (0f à 1f).
 * @property isMuted Mode silencieux général.
 * @property themePalette Thème visuel actif parmi les palettes officielles.
 */
data class WaliUiState(
    val gameState: GameState = WaliEngine.initializeGame(),
    val gameMode: GameMode = GameMode.TWO_PLAYERS,
    val humanPlayer: Player = Player.STICKS,
    val aiDifficulty: AiDifficulty = AiDifficulty.EASY,
    val isAiThinking: Boolean = false,
    val selectedPosition: Position? = null,
    val highlightedMoves: Set<Position> = emptySet(),
    val capturablePositions: Set<Position> = emptySet(),
    val lastMove: PieceMove? = null,
    val feedbackMessage: String? = null,
    val showPauseMenu: Boolean = false,
    val showResetConfirmation: Boolean = false,
    val showModeSelectionDialog: Boolean = false,
    val showResumeDialog: Boolean = false,
    val sfxVolume: Float = 0.8f,
    val musicVolume: Float = 0.5f,
    val isMuted: Boolean = false,
    val themePalette: WaliThemePalette = WaliThemePalette.SAHEL_JOUR,
    val isTimerEnabled: Boolean = true,
    val timerDurationSeconds: Int = 30,
    val timerRemainingSeconds: Int = 30,
    val isMandatoryAlignmentEnabled: Boolean = false,
    val isDefeatAtTwoPiecesEnabled: Boolean = false,
    val isAppInBackground: Boolean = false,
    val isSecondaryScreenOpen: Boolean = false,
    val isAdShowing: Boolean = false,
    val onlineGameSession: com.example.wali.online.OnlineGameSession? = null,
    val localPlayerRole: Player? = null,
    val localPlayerUid: String? = null,
    val waitingForOpponentCode: String? = null,
    val publicWaitingGames: List<com.example.wali.online.PublicGameItem> = emptyList(),
    val isOnlineLoading: Boolean = false,
    val onlineErrorMessage: String? = null,
    val isOpponentDisconnected: Boolean = false,
    val canDeclareAbandonment: Boolean = false,
    val showResumeOnlineDialog: Boolean = false,
    val pendingResumeOnlineGame: com.example.wali.online.OnlineGameSession? = null,
    val gameDurationSeconds: Long = 0L,
    val isGameResultRecorded: Boolean = false,
    val scoreboardStats: ScoreboardStats = ScoreboardStats(),
    val recentGames: List<GameResultRecord> = emptyList(),
    val showScoreboardDialog: Boolean = false,
    val showForfeitConfirmation: Boolean = false,
    val gameOverReason: String? = null,
    val chatState: WaliChatUiState = WaliChatUiState()
) {
    /**
     * Vrai si la partie en cours se déroule en ligne via Firestore.
     */
    val isOnlineGame: Boolean
        get() = gameMode == GameMode.ONLINE

    /**
     * Vrai si une partie est active et que le joueur peut déclarer forfait.
     */
    val canDeclareForfeit: Boolean
        get() = gameState.phase != Phase.GAME_OVER &&
                gameState.winner == null &&
                (gameMode != GameMode.ONLINE || onlineGameSession?.status == com.example.wali.online.OnlineGameStatus.ONGOING)

    /**
     * Vrai si c'est le tour de l'adversaire distant en mode en ligne.
     */
    val isOpponentTurnOnline: Boolean
        get() = isOnlineGame &&
                onlineGameSession?.status == com.example.wali.online.OnlineGameStatus.ONGOING &&
                gameState.currentPlayer != localPlayerRole &&
                gameState.winner == null

    /**
     * Vrai si le minuteur doit être suspendu (dialogues ouverts, arrière-plan, écran secondaire, etc.).
     */
    val isTimerPaused: Boolean
        get() = isAppInBackground ||
                isSecondaryScreenOpen ||
                isAdShowing ||
                showPauseMenu ||
                showResetConfirmation ||
                showModeSelectionDialog ||
                showResumeDialog ||
                showResumeOnlineDialog ||
                showForfeitConfirmation ||
                isAiThinking ||
                isAiTurn ||
                (isOnlineGame && onlineGameSession?.status != com.example.wali.online.OnlineGameStatus.ONGOING) ||
                gameState.phase == Phase.GAME_OVER

    /**
     * Vrai si le tour actuel appartient à l'ordinateur en mode VS_COMPUTER.
     */
    val isAiTurn: Boolean
        get() = gameMode == GameMode.VS_COMPUTER &&
                gameState.currentPlayer != humanPlayer &&
                gameState.winner == null

    /**
     * Vrai si le joueur courant a au moins un pion en attente de pose suite à un blocage.
     */
    val hasPendingPlacement: Boolean
        get() = gameState.hasPendingPlacements(gameState.currentPlayer)

    /**
     * Vrai si le joueur courant doit obligatoirement poser son pion en attente ce tour-ci.
     */
    val isPendingPlacementRequired: Boolean
        get() = WaliEngine.isPlacementRequired(gameState)
}
