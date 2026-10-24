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
    val isCellNumbersEnabled: Boolean = true,
    val isAppInBackground: Boolean = false,
    val isSecondaryScreenOpen: Boolean = false,
    val isAdShowing: Boolean = false,
    val onlineGameSession: com.example.wali.online.OnlineGameSession? = null,
    val onlineRole: com.example.wali.online.OnlineRole? = null,
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
    val pendingForfeitPlayer: Player? = null,
    val gameOverReason: String? = null,
    val rematchTimeoutRemainingSeconds: Int? = null,
    val rematchStatusMessage: String? = null,
    val chatState: WaliChatUiState = WaliChatUiState(),
    val localDrawOfferedBy: Player? = null,
    val localDrawOfferTurnCount: Int = -1,
    val localLastDrawProposer: Player? = null
) {
    /**
     * Vrai si la partie en cours se déroule en ligne via Firestore.
     */
    val isOnlineGame: Boolean
        get() = gameMode == GameMode.ONLINE

    /**
     * Vrai si l'utilisateur courant observe la partie en ligne en tant que spectateur.
     * Source unique de vérité basée sur [onlineRole] (ou dérivée de la session si non initialisée).
     */
    val isSpectator: Boolean
        get() {
            if (!isOnlineGame) return false
            if (onlineRole != null) return onlineRole == com.example.wali.online.OnlineRole.SPECTATOR
            val session = onlineGameSession ?: return false
            val uid = localPlayerUid ?: return false
            return !session.isParticipant(uid)
        }

    /**
     * Code spectateur du salon en ligne courant (7 caractères commençant par 'S').
     */
    val onlineSpectatorCode: String?
        get() = onlineGameSession?.spectatorCode

    val spectatorCode: String
        get() = onlineGameSession?.spectatorCode.orEmpty()

    /**
     * Vrai si le joueur local a demandé une revanche dans le salon actuel.
     * Toujours faux pour un spectateur.
     */
    val isRematchRequestedByMe: Boolean
        get() {
            if (isSpectator) return false
            val session = onlineGameSession ?: return false
            val uid = localPlayerUid ?: return false
            return session.rematchRequestedBy.contains(uid)
        }

    /**
     * Vrai si l'adversaire a demandé une revanche dans le salon actuel.
     * Toujours faux pour un spectateur.
     */
    val isRematchRequestedByOpponent: Boolean
        get() {
            if (isSpectator) return false
            val session = onlineGameSession ?: return false
            val myUid = localPlayerUid ?: return false
            val oppUid = session.getOpponentUid(myUid) ?: return false
            return session.rematchRequestedBy.contains(oppUid)
        }

    /**
     * Vrai si seul le joueur local a demandé la revanche et attend la réponse de l'adversaire.
     */
    val isWaitingForRematch: Boolean
        get() = isOnlineGame && !isSpectator && isRematchRequestedByMe && !isRematchRequestedByOpponent

    /**
     * Nombre de victoires du joueur local dans le salon courant (indexé par uid).
     */
    val salonMyWins: Int
        get() {
            val session = onlineGameSession ?: return 0
            val myUid = localPlayerUid ?: session.creatorUid
            return session.getWinsForUid(myUid)
        }

    /**
     * Nombre de victoires de l'adversaire dans le salon courant (indexé par uid).
     */
    val salonOpponentWins: Int
        get() {
            val session = onlineGameSession ?: return 0
            val myUid = localPlayerUid ?: session.creatorUid
            val oppUid = session.getOpponentUid(myUid)
            return session.getWinsForUid(oppUid)
        }

    /**
     * Nombre de victoires dans le salon courant du joueur qui occupe actuellement le camp Bâtons (STICKS).
     * Utilisé notamment par les spectateurs pour afficher le score par camp.
     */
    val salonSticksPlayerWins: Int
        get() {
            val session = onlineGameSession ?: return 0
            val sticksUid = session.resolveParticipantUidForRole(Player.STICKS)
                ?: if (session.creatorPlayer == Player.STICKS) session.creatorUid else session.opponentUid
            return session.getWinsForUid(sticksUid)
        }

    /**
     * Nombre de victoires dans le salon courant du joueur qui occupe actuellement le camp Cailloux (STONES).
     * Utilisé notamment par les spectateurs pour afficher le score par camp.
     */
    val salonStonesPlayerWins: Int
        get() {
            val session = onlineGameSession ?: return 0
            val stonesUid = session.resolveParticipantUidForRole(Player.STONES)
                ?: if (session.creatorPlayer == Player.STONES) session.creatorUid else session.opponentUid
            return session.getWinsForUid(stonesUid)
        }

    /**
     * Nombre de parties jouées dans le salon courant.
     */
    val salonGamesPlayed: Int
        get() = onlineGameSession?.gamesPlayed ?: 1

    /**
     * Vrai si une proposition de match nul est actuellement en attente de réponse.
     */
    val isDrawOfferPending: Boolean
        get() = when (gameMode) {
            GameMode.TWO_PLAYERS -> localDrawOfferedBy != null
            GameMode.ONLINE -> onlineGameSession?.drawOfferedBy != null &&
                    onlineGameSession.status == com.example.wali.online.OnlineGameStatus.ONGOING
            GameMode.VS_COMPUTER -> false
        }

    /**
     * Vrai si une proposition de match nul est adressée au joueur local (ou à l'adversaire sur le même écran en mode 2 joueurs).
     */
    val isDrawOfferedToMe: Boolean
        get() = when (gameMode) {
            GameMode.TWO_PLAYERS -> localDrawOfferedBy != null &&
                    gameState.phase == Phase.MOVEMENT &&
                    gameState.winner == null
            GameMode.ONLINE -> !isSpectator &&
                    onlineGameSession?.status == com.example.wali.online.OnlineGameStatus.ONGOING &&
                    onlineGameSession.drawOfferedBy != null &&
                    !localPlayerUid.isNullOrBlank() &&
                    onlineGameSession.drawOfferedBy != localPlayerUid
            GameMode.VS_COMPUTER -> false
        }

    /**
     * Vrai si le joueur local en ligne a proposé un match nul et attend la réponse de son adversaire.
     */
    val isWaitingForDrawResponse: Boolean
        get() = gameMode == GameMode.ONLINE &&
                !isSpectator &&
                onlineGameSession?.status == com.example.wali.online.OnlineGameStatus.ONGOING &&
                onlineGameSession.drawOfferedBy != null &&
                !localPlayerUid.isNullOrBlank() &&
                onlineGameSession.drawOfferedBy == localPlayerUid

    /**
     * Indique si une proposition de match nul est adressée spécifiquement à [player] (notamment en mode TWO_PLAYERS).
     */
    fun isDrawOfferedToPlayer(player: Player): Boolean {
        if (isSpectator) return false
        if (gameState.phase != Phase.MOVEMENT || gameState.winner != null) return false
        return when (gameMode) {
            GameMode.TWO_PLAYERS -> localDrawOfferedBy != null && localDrawOfferedBy != player
            GameMode.ONLINE -> isDrawOfferedToMe && (localPlayerRole ?: humanPlayer) == player
            GameMode.VS_COMPUTER -> false
        }
    }

    /**
     * Indique si [player] a proposé un match nul et attend la réponse de son adversaire.
     */
    fun isPlayerWaitingForDrawResponse(player: Player): Boolean {
        if (isSpectator) return false
        if (gameState.phase != Phase.MOVEMENT || gameState.winner != null) return false
        return when (gameMode) {
            GameMode.TWO_PLAYERS -> localDrawOfferedBy == player
            GameMode.ONLINE -> isWaitingForDrawResponse && (localPlayerRole ?: humanPlayer) == player
            GameMode.VS_COMPUTER -> false
        }
    }

    /**
     * Vrai si le joueur [player] peut proposer un match nul maintenant :
     * - Uniquement en Phase.MOVEMENT (jamais en Phase.PLACEMENT)
     * - Jamais en mode VS_COMPUTER ni en mode spectateur
     * - C'est bien le tour de [player]
     * - Aucune capture ni pose obligatoire en attente
     * - Aucune proposition déjà en attente
     * - Au moins un coup a été joué par [player] depuis sa dernière proposition refusée
     */
    fun canPlayerProposeDraw(player: Player): Boolean {
        if (isSpectator) return false
        if (gameMode == GameMode.VS_COMPUTER) return false
        if (gameState.phase != Phase.MOVEMENT || gameState.winner != null) return false
        if (gameState.pendingCaptures > 0) return false
        if (gameState.hasPendingPlacements(player)) return false
        if (isDrawOfferPending) return false
        if (gameState.currentPlayer != player) return false

        val currentTurnCount = gameState.moveHistory.size
        return when (gameMode) {
            GameMode.TWO_PLAYERS -> {
                val inCooldown = localDrawOfferTurnCount >= 0 &&
                        (localLastDrawProposer == null || localLastDrawProposer == player) &&
                        currentTurnCount <= localDrawOfferTurnCount
                !inCooldown
            }
            GameMode.ONLINE -> {
                val session = onlineGameSession ?: return false
                if (session.status != com.example.wali.online.OnlineGameStatus.ONGOING) return false
                val myRole = localPlayerRole ?: session.getPlayerRole(localPlayerUid.orEmpty())
                if (myRole == null || player != myRole) return false
                val inCooldown = session.drawOfferTurnCount >= 0 && currentTurnCount <= session.drawOfferTurnCount
                !inCooldown
            }
            GameMode.VS_COMPUTER -> false
        }
    }

    /**
     * Vrai si le joueur dont c'est le tour peut proposer un match nul maintenant :
     * - Uniquement en Phase.MOVEMENT (jamais en Phase.PLACEMENT)
     * - Jamais en mode VS_COMPUTER ni en mode spectateur
     * - Aucune proposition déjà en attente
     * - C'est bien le tour du joueur local (en ligne)
     * - Au moins un tour complet s'est écoulé depuis la dernière proposition refusée
     */
    val canProposeDraw: Boolean
        get() = canPlayerProposeDraw(gameState.currentPlayer)

    /**
     * Vrai si la partie est terminée sur un match nul accepté par les deux joueurs.
     */
    val isDrawGameOver: Boolean
        get() = gameState.phase == Phase.GAME_OVER && gameState.winner == null

    /**
     * Vrai si la victoire actuelle vaut 2 points (le perdant n'a capturé aucun pion adverse).
     */
    val isDoubleWin: Boolean
        get() = gameState.isDoubleWin(gameState.winner)

    /**
     * Vrai si une partie est active et que le joueur peut déclarer forfait.
     * Un spectateur ne peut jamais déclarer forfait.
     */
    val canDeclareForfeit: Boolean
        get() = !isSpectator &&
                gameState.phase != Phase.GAME_OVER &&
                gameState.winner == null &&
                (gameMode != GameMode.ONLINE || onlineGameSession?.status == com.example.wali.online.OnlineGameStatus.ONGOING)

    /**
     * Vrai si c'est le tour de l'adversaire distant en mode en ligne (ou si l'utilisateur est spectateur).
     */
    val isOpponentTurnOnline: Boolean
        get() = isOnlineGame &&
                onlineGameSession?.status == com.example.wali.online.OnlineGameStatus.ONGOING &&
                (isSpectator || (localPlayerRole != null && gameState.currentPlayer != localPlayerRole)) &&
                gameState.winner == null

    /**
     * Vrai si le minuteur doit être suspendu (dialogues ouverts, arrière-plan, écran secondaire, spectateur, etc.).
     */
    val isTimerPaused: Boolean
        get() = isSpectator ||
                isDrawOfferPending ||
                isAppInBackground ||
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
