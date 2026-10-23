package com.example.wali.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wali.ai.AiDifficulty
import com.example.wali.ai.WaliAi
import com.example.wali.audio.WaliAudio
import com.example.wali.audio.WaliAudioManager
import com.example.wali.chat.ChatMessageType
import com.example.wali.chat.FirestoreWaliChatRepository
import com.example.wali.chat.WaliChatMessage
import com.example.wali.chat.WaliChatRateLimiter
import com.example.wali.chat.WaliChatRepository
import com.example.wali.chat.WaliChatUiState
import com.example.wali.chat.WaliProfanityFilter
import com.example.wali.chat.WaliReport
import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Phase
import com.example.wali.model.PieceMove
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.model.WaliRuleOptions
import com.example.wali.online.FirestoreWaliOnlineRepository
import com.example.wali.online.OnlineGameSession
import com.example.wali.online.OnlineGameStatus
import com.example.wali.online.WaliOnlineCodeGenerator
import com.example.wali.online.WaliOnlineRepository
import com.example.wali.storage.DataStoreWaliGameStorage
import com.example.wali.storage.DataStoreWaliPreferencesRepository
import com.example.wali.storage.DataStoreWaliScoreboardRepository
import com.example.wali.storage.GameOutcome
import com.example.wali.storage.GameResultRecord
import com.example.wali.storage.SavedGame
import com.example.wali.storage.ScoreboardStats
import com.example.wali.storage.WaliGameStorage
import com.example.wali.storage.WaliPreferencesRepository
import com.example.wali.storage.WaliScoreboardRepository
import com.example.wali.ui.theme.WaliThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel orchestrant les interactions utilisateur et l'état du jeu Wali Songhaï.
 *
 * Utilise EXCLUSIVEMENT [WaliEngine] pour l'évaluation et l'application des règles.
 * Ne duplique aucune règle du jeu.
 *
 * Supporte le mode 2 Joueurs Local, le mode Humain vs Machine (3 niveaux) et le mode En Ligne (Firebase Firestore).
 * Gère la persistance hors-ligne automatique via [WaliGameStorage] et [WaliPreferencesRepository].
 * Pilote le son et la musique via [WaliAudio].
 */
class WaliViewModel(
    private val storage: WaliGameStorage? = null,
    private val preferencesRepository: WaliPreferencesRepository? = null,
    private val scoreboardRepository: WaliScoreboardRepository? = null,
    private val audio: WaliAudio? = null,
    private val onlineRepository: WaliOnlineRepository? = null,
    private val chatRepository: WaliChatRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaliUiState())
    val uiState: StateFlow<WaliUiState> = _uiState.asStateFlow()

    private var aiTurnJob: Job? = null
    private var timerJob: Job? = null
    private var durationJob: Job? = null
    private var onlineSessionJob: Job? = null
    private var onlineInactivityJob: Job? = null
    private var chatMessagesJob: Job? = null
    private var rematchTimeoutJob: Job? = null
    private var preRematchMessageIds: Set<String> = emptySet()
    private val chatRateLimiter = WaliChatRateLimiter()

    /**
     * Délai d'attente maximal en secondes pour l'acceptation d'une revanche en ligne (60s par défaut).
     */
    var rematchTimeoutSeconds: Int = 60

    /**
     * Délai visuel en millisecondes pour rendre le coup de l'IA naturel et lisible.
     */
    var aiDelayMs: Long = 650L

    init {
        // Chargement et observation des préférences utilisateur (volumes, mute, thème, minuteur)
        preferencesRepository?.let { prefsRepo ->
            viewModelScope.launch {
                prefsRepo.preferencesFlow.collect { userPrefs ->
                    val wasTimerEnabled = _uiState.value.isTimerEnabled
                    _uiState.update { current ->
                        current.copy(
                            sfxVolume = userPrefs.sfxVolume,
                            musicVolume = userPrefs.musicVolume,
                            isMuted = userPrefs.isMuted,
                            themePalette = userPrefs.themePalette,
                            isTimerEnabled = userPrefs.isTimerEnabled,
                            timerDurationSeconds = userPrefs.timerDurationSeconds,
                            timerRemainingSeconds = if (!wasTimerEnabled || current.timerRemainingSeconds > userPrefs.timerDurationSeconds) {
                                userPrefs.timerDurationSeconds
                            } else current.timerRemainingSeconds,
                            isMandatoryAlignmentEnabled = userPrefs.isMandatoryAlignmentEnabled,
                            isDefeatAtTwoPiecesEnabled = userPrefs.isDefeatAtTwoPiecesEnabled,
                            isCellNumbersEnabled = userPrefs.isCellNumbersEnabled
                        )
                    }
                    audio?.setSfxVolume(userPrefs.sfxVolume)
                    audio?.setMusicVolume(userPrefs.musicVolume)
                    audio?.setMuted(userPrefs.isMuted)

                    if (userPrefs.isTimerEnabled && !wasTimerEnabled) {
                        onTurnTransition()
                    } else if (!userPrefs.isTimerEnabled) {
                        stopTurnTimer()
                    }
                }
            }
        }

        if (storage != null) {
            viewModelScope.launch {
                checkSavedGameAtStartup()
            }
        } else {
            onTurnTransition()
        }

        // Observation des parties multijoueurs publiques et détection de reconnexion en ligne
        onlineRepository?.let { repo ->
            viewModelScope.launch {
                repo.observePublicWaitingGames().collect { list ->
                    _uiState.update { it.copy(publicWaitingGames = list) }
                }
            }
            viewModelScope.launch {
                val pending = repo.checkExistingOngoingGame()
                if (pending != null && pending.status == OnlineGameStatus.ONGOING) {
                    _uiState.update {
                        it.copy(
                            pendingResumeOnlineGame = pending,
                            showResumeOnlineDialog = true
                        )
                    }
                }
            }
        }

        // Observation du tableau des scores persisté via DataStore
        scoreboardRepository?.let { scoreRepo ->
            viewModelScope.launch {
                scoreRepo.statsFlow.collect { stats ->
                    _uiState.update { it.copy(scoreboardStats = stats) }
                }
            }
            viewModelScope.launch {
                scoreRepo.recentGamesFlow.collect { recent ->
                    _uiState.update { it.copy(recentGames = recent) }
                }
            }
        }

        // Démarrage du chronomètre de suivi de durée de jeu
        startDurationTracking()
    }

    /**
     * Démarre la musique d'ambiance si elle n'est pas déjà active.
     */
    fun startMusic() {
        audio?.resumeMusic()
    }

    /**
     * Met en pause la musique d'ambiance.
     */
    fun pauseMusic() {
        audio?.pauseMusic()
    }

    /**
     * Met à jour le volume des effets sonores (SFX).
     */
    fun onSfxVolumeChanged(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { it.copy(sfxVolume = clamped) }
        audio?.setSfxVolume(clamped)
        viewModelScope.launch {
            preferencesRepository?.updateSfxVolume(clamped)
        }
    }

    /**
     * Met à jour le volume de la musique d'ambiance.
     */
    fun onMusicVolumeChanged(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { it.copy(musicVolume = clamped) }
        audio?.setMusicVolume(clamped)
        viewModelScope.launch {
            preferencesRepository?.updateMusicVolume(clamped)
        }
    }

    /**
     * Active ou désactive le mode muet général.
     */
    fun onMutedChanged(muted: Boolean) {
        _uiState.update { it.copy(isMuted = muted) }
        audio?.setMuted(muted)
        viewModelScope.launch {
            preferencesRepository?.updateMuted(muted)
        }
    }

    /**
     * Change le thème visuel actif et le persiste.
     */
    fun onThemeSelected(palette: WaliThemePalette) {
        audio?.playClick()
        _uiState.update { it.copy(themePalette = palette) }
        viewModelScope.launch {
            preferencesRepository?.updateThemePalette(palette)
        }
    }

    /**
     * Active ou désactive le minuteur par coup.
     */
    fun onTimerEnabledChanged(enabled: Boolean) {
        _uiState.update { it.copy(isTimerEnabled = enabled) }
        viewModelScope.launch {
            preferencesRepository?.updateTimerEnabled(enabled)
        }
        if (enabled) {
            onTurnTransition()
        } else {
            stopTurnTimer()
        }
    }

    /**
     * Modifie la durée du minuteur par coup (ex: 15s, 30s, 60s, 90s).
     */
    fun onTimerDurationChanged(durationSeconds: Int) {
        _uiState.update {
            it.copy(
                timerDurationSeconds = durationSeconds,
                timerRemainingSeconds = durationSeconds
            )
        }
        viewModelScope.launch {
            preferencesRepository?.updateTimerDuration(durationSeconds)
        }
    }

    /**
     * Active ou désactive la variante « Alignement et capture obligatoires » pour les prochaines parties.
     */
    fun onMandatoryAlignmentChanged(enabled: Boolean) {
        audio?.playClick()
        _uiState.update { it.copy(isMandatoryAlignmentEnabled = enabled) }
        viewModelScope.launch {
            preferencesRepository?.updateMandatoryAlignment(enabled)
        }
    }

    /**
     * Active ou désactive la variante « Défaite à deux pions » pour les prochaines parties.
     */
    fun onDefeatAtTwoPiecesChanged(enabled: Boolean) {
        audio?.playClick()
        _uiState.update { it.copy(isDefeatAtTwoPiecesEnabled = enabled) }
        viewModelScope.launch {
            preferencesRepository?.updateDefeatAtTwoPieces(enabled)
        }
    }

    /**
     * Active ou désactive l'affichage des numéros de cases (1 à 30) sur le plateau.
     */
    fun onCellNumbersEnabledChanged(enabled: Boolean) {
        audio?.playClick()
        _uiState.update { it.copy(isCellNumbersEnabled = enabled) }
        viewModelScope.launch {
            preferencesRepository?.updateCellNumbersEnabled(enabled)
        }
    }

    private fun currentRuleOptions(): WaliRuleOptions {
        val s = _uiState.value
        return WaliRuleOptions(
            mandatoryAlignment = s.isMandatoryAlignmentEnabled,
            defeatAtTwoPieces = s.isDefeatAtTwoPiecesEnabled
        )
    }

    /**
     * Signale que l'application passe en arrière-plan.
     */
    fun onAppBackgrounded() {
        _uiState.update { it.copy(isAppInBackground = true) }
        stopOnlineInactivityMonitoring()
    }

    /**
     * Signale que l'application revient au premier plan.
     */
    fun onAppForegrounded() {
        _uiState.update { it.copy(isAppInBackground = false) }
        val current = _uiState.value
        if (current.gameMode == GameMode.ONLINE && current.onlineGameSession?.status == OnlineGameStatus.ONGOING) {
            checkOnlineInactivity()
            startOnlineInactivityMonitoring()
        } else if (current.gameMode != GameMode.ONLINE && !current.showResumeOnlineDialog) {
            checkForOngoingOnlineGame()
        }
    }

    /**
     * Signale l'ouverture ou fermeture d'un écran secondaire (Règles, Réglages).
     */
    fun setSecondaryScreenOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSecondaryScreenOpen = isOpen) }
    }

    /**
     * Signale l'affichage ou la fermeture d'une publicité plein écran.
     */
    fun setAdShowing(showing: Boolean) {
        _uiState.update { it.copy(isAdShowing = showing) }
    }

    /**
     * Joue le son de clic de bouton UI.
     */
    fun playButtonClick() {
        audio?.playClick()
    }

    /**
     * Vérifie au démarrage si une partie non terminée existe dans le stockage hors-ligne.
     */
    private suspend fun checkSavedGameAtStartup() {
        val storage = storage ?: return
        runCatching {
            if (storage.hasActiveSavedGame()) {
                _uiState.update { it.copy(showResumeDialog = true) }
            } else {
                _uiState.update { it.copy(showModeSelectionDialog = true) }
            }
        }.onFailure { e ->
            Log.w("WaliViewModel", "checkSavedGameAtStartup safely ignored: ${e.message}")
            _uiState.update { it.copy(showModeSelectionDialog = true) }
        }
    }

    /**
     * Reprend la partie sauvegardée en cours.
     */
    fun resumeSavedGame() {
        audio?.playClick()
        val storage = storage ?: return
        viewModelScope.launch {
            runCatching {
                val saved = storage.loadSavedGame()
                if (saved != null && saved.gameState.winner == null && saved.gameState.phase != Phase.GAME_OVER) {
                    _uiState.update { current ->
                        current.copy(
                            gameState = saved.gameState,
                            gameMode = saved.gameMode,
                            humanPlayer = saved.humanPlayer,
                            aiDifficulty = saved.aiDifficulty,
                            showResumeDialog = false,
                            showModeSelectionDialog = false,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = if (WaliEngine.canCapture(saved.gameState)) {
                                val opp = saved.gameState.currentPlayer.opponent
                                saved.gameState.board.filter { it.value == opp }.keys
                            } else {
                                emptySet()
                            },
                            feedbackMessage = "Partie reprise avec succès.",
                            gameDurationSeconds = saved.elapsedDurationSeconds,
                            isGameResultRecorded = false
                        )
                    }
                    startDurationTracking()
                    onTurnTransition()
                } else {
                    _uiState.update { it.copy(showResumeDialog = false, showModeSelectionDialog = true) }
                }
            }.onFailure { e ->
                Log.w("WaliViewModel", "resumeSavedGame safely caught error: ${e.message}")
                _uiState.update { it.copy(showResumeDialog = false, showModeSelectionDialog = true) }
            }
        }
    }

    /**
     * Refuse la reprise d'une partie : efface la sauvegarde et propose le choix de mode.
     */
    fun declineResumeAndStartNewGame() {
        audio?.playClick()
        viewModelScope.launch {
            runCatching {
                storage?.clearSavedGame()
            }.onFailure { e ->
                Log.w("WaliViewModel", "clearSavedGame safely ignored: ${e.message}")
            }
            _uiState.update {
                it.copy(
                    showResumeDialog = false,
                    showModeSelectionDialog = true
                )
            }
        }
    }

    /**
     * Sauvegarde automatique et silencieuse de l'état de la partie en cours.
     * Efface la sauvegarde si la partie est terminée.
     */
    private fun autoSaveGame(state: WaliUiState) {
        // Le mode multijoueur en ligne n'utilise pas la sauvegarde locale DataStore
        if (state.gameMode == GameMode.ONLINE) {
            return
        }
        val storage = storage ?: return
        viewModelScope.launch {
            runCatching {
                if (state.gameState.winner != null || state.gameState.phase == Phase.GAME_OVER) {
                    storage.clearSavedGame()
                } else {
                    storage.saveGame(
                        SavedGame(
                            gameState = state.gameState,
                            gameMode = state.gameMode,
                            humanPlayer = state.humanPlayer,
                            aiDifficulty = state.aiDifficulty,
                            elapsedDurationSeconds = state.gameDurationSeconds
                        )
                    )
                }
            }.onFailure { e ->
                Log.w("WaliViewModel", "autoSaveGame safely ignored exception: ${e.message}")
            }
        }
    }

    /**
     * Traite le clic sur une case [position] de la grille selon la phase active.
     * Ignore les clics si l'IA est en train de réfléchir ou si c'est le tour de l'ordinateur.
     */
    fun onCellClicked(position: Position) {
        val state = _uiState.value

        // Si la partie est terminée, si une proposition de match nul est en attente, ou si c'est le tour de l'ordinateur, les clics sont bloqués
        if (state.gameState.phase == Phase.GAME_OVER || state.isDrawOfferPending || state.isAiTurn || state.isAiThinking) {
            return
        }

        // En mode en ligne, bloquer si l'utilisateur est spectateur, si la session n'est pas active ou si ce n'est pas son tour
        if (state.gameMode == GameMode.ONLINE) {
            if (state.isSpectator) {
                _uiState.update { it.copy(feedbackMessage = "Mode spectateur : observation uniquement.") }
                return
            }
            if (state.onlineGameSession?.status != OnlineGameStatus.ONGOING) {
                return
            }
            if (state.localPlayerRole == null || state.gameState.currentPlayer != state.localPlayerRole) {
                _uiState.update { it.copy(feedbackMessage = "Tour de l'adversaire distant…") }
                return
            }
        }

        // Si une capture est requise, le clic doit cibler un pion adverse capturable
        if (WaliEngine.canCapture(state.gameState)) {
            onCaptureTargetSelected(position)
            return
        }

        when (state.gameState.phase) {
            Phase.PLACEMENT -> handlePlacement(position)
            Phase.MOVEMENT -> handleMovement(position)
            Phase.GAME_OVER -> Unit
        }
    }

    /**
     * Gère la pose d'un pion en Phase 1 par le joueur humain.
     */
    private fun handlePlacement(position: Position) {
        val currentGameState = _uiState.value.gameState

        // Case déjà occupée
        if (currentGameState.board[position] != null) {
            _uiState.update { it.copy(feedbackMessage = "Cette case est déjà occupée.") }
            return
        }

        // Vérification de la règle essentielle de pose : pas de ligne de 3
        val validPlacements = WaliEngine.getValidPlacements(currentGameState)
        if (position !in validPlacements) {
            _uiState.update {
                it.copy(feedbackMessage = "Coup interdit — cela formerait une ligne de trois.")
            }
            return
        }

        // Application de la pose via le moteur
        val player = currentGameState.currentPlayer
        val nextGameState = WaliEngine.placePiece(currentGameState, position)
        val isNowMovement = nextGameState.phase == Phase.MOVEMENT

        audio?.playPlace(player)

        _uiState.update {
            it.copy(
                gameState = nextGameState,
                selectedPosition = null,
                highlightedMoves = emptySet(),
                capturablePositions = emptySet(),
                feedbackMessage = if (isNowMovement) "Pose terminée (24 pions posés). Début des déplacements !" else null
            )
        }

        syncOnlineMoveIfNeeded(nextGameState)
        autoSaveGame(_uiState.value)
        onTurnTransition()
    }

    /**
     * Gère le déplacement d'un pion en Phase 2 par le joueur humain.
     */
    private fun handleMovement(position: Position) {
        val state = _uiState.value
        val currentGameState = state.gameState
        val selected = state.selectedPosition

        // CORRECTION 3 : Si une pose de pion en attente est requise ce tour-ci
        if (WaliEngine.isPlacementRequired(currentGameState)) {
            val validPlacements = WaliEngine.getValidPlacements(currentGameState)
            if (position in validPlacements) {
                val player = currentGameState.currentPlayer
                val nextGameState = WaliEngine.placePiece(currentGameState, position)
                audio?.playPlace(player)

                if (nextGameState.winner != null) {
                    checkPlayEndGameAudio(nextGameState)
                }

                _uiState.update {
                    it.copy(
                        gameState = nextGameState,
                        selectedPosition = null,
                        highlightedMoves = emptySet(),
                        capturablePositions = emptySet(),
                        feedbackMessage = "Pion en attente posé avec succès."
                    )
                }

                syncOnlineMoveIfNeeded(nextGameState)
                autoSaveGame(_uiState.value)
                onTurnTransition()
            } else {
                _uiState.update {
                    it.copy(feedbackMessage = "Vous devez placer votre pion restant sur une case libre autorisée.")
                }
            }
            return
        }

        if (selected == null) {
            // Aucun pion sélectionné pour l'instant
            val pieceOnCell = currentGameState.board[position]
            when (pieceOnCell) {
                currentGameState.currentPlayer -> {
                    // Sélection du pion du joueur actif
                    val validMoves = WaliEngine.getValidMoves(currentGameState, position).toSet()
                    if (validMoves.isEmpty()) {
                        val hasObligatory = currentGameState.ruleOptions.mandatoryAlignment &&
                            WaliEngine.getObligatoryMoves(currentGameState).isNotEmpty()
                        _uiState.update {
                            it.copy(
                                selectedPosition = null,
                                highlightedMoves = emptySet(),
                                feedbackMessage = if (hasObligatory) {
                                    "Alignement obligatoire : vous devez jouer un coup qui forme une ligne de trois."
                                } else {
                                    "Ce pion n'a aucune case adjacente libre."
                                }
                            )
                        }
                    } else {
                        audio?.playClick()
                        _uiState.update {
                            it.copy(
                                selectedPosition = position,
                                highlightedMoves = validMoves,
                                feedbackMessage = null
                            )
                        }
                    }
                }
                currentGameState.currentPlayer.opponent -> {
                    _uiState.update {
                        val playerLabel = if (currentGameState.currentPlayer == Player.STICKS) "BÂTONNETS" else "CAILLOUX"
                        it.copy(feedbackMessage = "C'est au tour des $playerLabel.")
                    }
                }
                else -> {
                    // Clic sur case vide sans pion sélectionné
                    _uiState.update {
                        it.copy(
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            feedbackMessage = null
                        )
                    }
                }
            }
        } else {
            // Un pion est déjà sélectionné
            if (position == selected) {
                // Re-clic sur le même pion -> désélection
                _uiState.update {
                    it.copy(selectedPosition = null, highlightedMoves = emptySet(), feedbackMessage = null)
                }
            } else if (position in state.highlightedMoves) {
                // Déplacement valide vers la case cible
                val pieceMove = PieceMove(from = selected, to = position, player = currentGameState.currentPlayer)
                val nextGameState = WaliEngine.movePiece(currentGameState, selected, position)
                audio?.playMove()

                if (WaliEngine.canCapture(nextGameState)) {
                    val opponent = nextGameState.currentPlayer.opponent
                    val capturable = nextGameState.board.filter { it.value == opponent }.keys.toSet()
                    val message = "Ligne de trois formée ! Choisissez un pion adverse à capturer."

                    _uiState.update {
                        it.copy(
                            gameState = nextGameState,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = capturable,
                            lastMove = pieceMove,
                            feedbackMessage = message
                        )
                    }
                    autoSaveGame(_uiState.value)
                } else {
                    if (nextGameState.winner != null) {
                        checkPlayEndGameAudio(nextGameState)
                        checkRecordGameOverResult(nextGameState)
                    }
                    _uiState.update {
                        it.copy(
                            gameState = nextGameState,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = emptySet(),
                            lastMove = pieceMove,
                            feedbackMessage = null
                        )
                    }
                    syncOnlineMoveIfNeeded(nextGameState)
                    autoSaveGame(_uiState.value)
                    onTurnTransition()
                }
            } else if (currentGameState.board[position] == currentGameState.currentPlayer) {
                // Clic sur un autre pion de sa propre couleur -> changer de sélection
                val newValidMoves = WaliEngine.getValidMoves(currentGameState, position).toSet()
                if (newValidMoves.isEmpty()) {
                    val hasObligatory = currentGameState.ruleOptions.mandatoryAlignment &&
                        WaliEngine.getObligatoryMoves(currentGameState).isNotEmpty()
                    _uiState.update {
                        it.copy(
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            feedbackMessage = if (hasObligatory) {
                                "Alignement obligatoire : vous devez jouer un coup qui forme une ligne de trois."
                            } else {
                                "Ce pion n'a aucune case libre."
                            }
                        )
                    }
                } else {
                    audio?.playClick()
                    _uiState.update {
                        it.copy(
                            selectedPosition = position,
                            highlightedMoves = newValidMoves,
                            feedbackMessage = null
                        )
                    }
                }
            } else {
                // Clic ailleurs -> annulation de la sélection
                _uiState.update {
                    it.copy(selectedPosition = null, highlightedMoves = emptySet(), feedbackMessage = null)
                }
            }
        }
    }

    /**
     * Joue le son de fin de partie (victoire ou défaite) en fonction du joueur humain.
     */
    private fun checkPlayEndGameAudio(state: GameState) {
        val winner = state.winner ?: return
        val human = _uiState.value.humanPlayer
        val mode = _uiState.value.gameMode
        val localRole = _uiState.value.localPlayerRole

        if (mode == GameMode.VS_COMPUTER) {
            if (winner == human) {
                audio?.playVictory()
            } else {
                audio?.playDefeat()
            }
        } else if (mode == GameMode.ONLINE) {
            if (_uiState.value.isSpectator) {
                audio?.playVictory()
            } else if (winner == localRole) {
                audio?.playVictory()
            } else {
                audio?.playDefeat()
            }
        } else {
            audio?.playVictory()
        }
    }

    /**
     * Enregistre le résultat de la partie et sa durée dans le tableau des scores persisté via DataStore.
     * Garantit qu'une même partie terminée n'est comptabilisée qu'une seule fois.
     * Ne comptabilise jamais une partie observée en tant que spectateur dans le bilan personnel.
     */
    fun checkRecordGameOverResult(state: GameState, customReason: String? = null) {
        val current = _uiState.value
        if (current.isSpectator) return
        if (current.isGameResultRecorded) return
        if (state.phase != Phase.GAME_OVER && state.winner == null) return

        _uiState.update { it.copy(isGameResultRecorded = true) }

        val mode = current.gameMode
        val human = current.humanPlayer
        val localRole = current.localPlayerRole ?: human
        val durationSeconds = maxOf(1L, current.gameDurationSeconds)

        val winner = state.winner
        val record = if (winner == null) {
            val drawReason = customReason ?: "Match nul — Les deux joueurs ont accepté."
            GameResultRecord(
                outcome = GameOutcome.DRAW,
                gameMode = mode,
                aiDifficulty = if (mode == GameMode.VS_COMPUTER) current.aiDifficulty else null,
                durationSeconds = durationSeconds,
                winner = localRole,
                reason = drawReason,
                isDoubleWin = false
            )
        } else {
            val outcome = when (mode) {
                GameMode.VS_COMPUTER -> if (winner == human) GameOutcome.WIN else GameOutcome.LOSS
                GameMode.ONLINE -> if (winner == localRole) GameOutcome.WIN else GameOutcome.LOSS
                GameMode.TWO_PLAYERS -> GameOutcome.COMPLETED
            }

            val loser = winner.opponent
            val loserPieces = state.piecesOnBoard(loser)
            val defaultReason = when {
                loserPieces == 0 -> "Adversaire éliminé"
                state.ruleOptions.defeatAtTwoPieces && !state.hasPendingPlacements(loser) && loserPieces == 2 -> "Réduit à deux pions"
                else -> "Adversaire bloqué"
            }
            val reason = customReason ?: defaultReason
            val isDouble = state.isDoubleWin(winner)

            GameResultRecord(
                outcome = outcome,
                gameMode = mode,
                aiDifficulty = if (mode == GameMode.VS_COMPUTER) current.aiDifficulty else null,
                durationSeconds = durationSeconds,
                winner = winner,
                reason = reason,
                isDoubleWin = isDouble
            )
        }

        viewModelScope.launch {
            runCatching {
                scoreboardRepository?.recordGameResult(record)
            }.onFailure { e ->
                Log.w("WaliViewModel", "checkRecordGameOverResult failed: ${e.message}")
            }
        }
    }

    /**
     * Chronomètre mesurant la durée active de la partie (suspendu en pause, arrêt sur game over).
     */
    private fun startDurationTracking() {
        durationJob?.cancel()
        if (_uiState.value.isTimerPaused || _uiState.value.gameState.phase == Phase.GAME_OVER) {
            return
        }
        durationJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val current = _uiState.value
                if (current.isTimerPaused || current.gameState.phase == Phase.GAME_OVER) {
                    break
                }
                _uiState.update { it.copy(gameDurationSeconds = it.gameDurationSeconds + 1L) }
            }
        }
    }

    /**
     * Ouvre la vue du tableau des scores.
     */
    fun openScoreboard() {
        audio?.playClick()
        _uiState.update { it.copy(showScoreboardDialog = true) }
    }

    /**
     * Ferme la vue du tableau des scores.
     */
    fun dismissScoreboard() {
        audio?.playClick()
        _uiState.update { it.copy(showScoreboardDialog = false) }
    }

    /**
     * Réinitialise l'ensemble des scores et durées persistés dans le DataStore.
     */
    fun resetScoreboard() {
        audio?.playClick()
        viewModelScope.launch {
            runCatching {
                scoreboardRepository?.resetScoreboard()
            }.onFailure { e ->
                Log.w("WaliViewModel", "resetScoreboard error: ${e.message}")
            }
        }
    }

    /**
     * Traite la sélection d'une cible de capture par le joueur humain.
     */
    fun onCaptureTargetSelected(targetPosition: Position) {
        val state = _uiState.value
        if (state.isSpectator) return
        val currentGameState = state.gameState

        if (!WaliEngine.canCapture(currentGameState)) {
            return
        }

        if (targetPosition !in state.capturablePositions) {
            _uiState.update {
                it.copy(feedbackMessage = "Sélectionnez l'un des pions adverses en surbrillance pour le capturer.")
            }
            return
        }

        val nextGameState = WaliEngine.capturePiece(currentGameState, targetPosition)
        audio?.playCapture()

        if (nextGameState.winner != null) {
            checkPlayEndGameAudio(nextGameState)
            checkRecordGameOverResult(nextGameState)
        }
        val winner = nextGameState.winner
        val isTwoPiecesDefeat = winner != null &&
            nextGameState.ruleOptions.defeatAtTwoPieces &&
            !nextGameState.hasPendingPlacements(winner.opponent) &&
            nextGameState.piecesOnBoard(winner.opponent) == 2
        _uiState.update {
            it.copy(
                gameState = nextGameState,
                gameOverReason = if (isTwoPiecesDefeat) "Réduit à deux pions" else it.gameOverReason,
                capturablePositions = emptySet(),
                selectedPosition = null,
                highlightedMoves = emptySet(),
                feedbackMessage = null
            )
        }
        syncOnlineMoveIfNeeded(nextGameState)
        autoSaveGame(_uiState.value)
        onTurnTransition()
    }

    // =========================================================================
    // INTÉGRATION DE L'INTELLIGENCE ARTIFICIELLE (IA)
    // =========================================================================

    /**
     * Vérifie si c'est le tour de l'IA et déclenche son coup dans une coroutine si nécessaire.
     */
    fun checkTriggerAiIfNeeded() {
        val state = _uiState.value
        if (!state.isAiTurn || state.isAiThinking || state.gameState.winner != null) {
            return
        }

        aiTurnJob?.cancel()
        aiTurnJob = viewModelScope.launch {
            playAiTurn()
        }
    }

    /**
     * Exécute le tour de l'IA avec un délai visuel naturel et calcul hors du thread UI.
     */
    private suspend fun playAiTurn() {
        try {
            _uiState.update { it.copy(isAiThinking = true) }

            // Court délai visuel pour la lisibilité
            if (aiDelayMs > 0) {
                delay(aiDelayMs)
            }

            val currentState = _uiState.value.gameState
            val difficulty = _uiState.value.aiDifficulty

            if (currentState.phase == Phase.GAME_OVER || currentState.winner != null) {
                _uiState.update { it.copy(isAiThinking = false) }
                return
            }

            // Calcul du coup hors thread principal (Dispatchers.Default)
            val aiMove = withContext(Dispatchers.Default) {
                WaliAi.chooseMove(currentState, difficulty)
            }

            if (aiMove == null) {
                _uiState.update { it.copy(isAiThinking = false) }
                return
            }

            when (currentState.phase) {
                Phase.PLACEMENT -> {
                    val placementPos = aiMove.placement ?: return
                    val nextGameState = WaliEngine.placePiece(currentState, placementPos)
                    val isNowMovement = nextGameState.phase == Phase.MOVEMENT

                    audio?.playPlace(currentState.currentPlayer)

                    _uiState.update {
                        it.copy(
                            gameState = nextGameState,
                            isAiThinking = false,
                            feedbackMessage = if (isNowMovement) "Pose terminée (24 pions posés). Début des déplacements !" else null
                        )
                    }

                    autoSaveGame(_uiState.value)
                    onTurnTransition()
                }
                Phase.MOVEMENT -> {
                    if (aiMove.placement != null) {
                        val pos = aiMove.placement
                        val player = currentState.currentPlayer
                        val nextGameState = WaliEngine.placePiece(currentState, pos)
                        audio?.playPlace(player)

                        if (nextGameState.winner != null) {
                            checkPlayEndGameAudio(nextGameState)
                            checkRecordGameOverResult(nextGameState)
                        }

                        _uiState.update {
                            it.copy(
                                gameState = nextGameState,
                                isAiThinking = false,
                                selectedPosition = null,
                                highlightedMoves = emptySet(),
                                capturablePositions = emptySet(),
                                feedbackMessage = null
                            )
                        }

                        autoSaveGame(_uiState.value)
                        onTurnTransition()
                        return
                    }

                    val from = aiMove.moveFrom ?: return
                    val to = aiMove.moveTo ?: return
                    val pieceMove = PieceMove(from = from, to = to, player = currentState.currentPlayer)
                    var nextGameState = WaliEngine.movePiece(currentState, from, to)
                    audio?.playMove()

                    _uiState.update {
                        it.copy(
                            gameState = nextGameState,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = emptySet(),
                            lastMove = pieceMove
                        )
                    }

                    // Résolution de l'unique capture éventuelle de l'IA (une seule capture autorisée par coup)
                    if (WaliEngine.canCapture(nextGameState)) {
                        delay(320) // Laisser l'animation fluide de transition du déplacement se terminer
                        val opp = nextGameState.currentPlayer.opponent
                        val capturable = nextGameState.board.filter { it.value == opp }.keys

                        val target = withContext(Dispatchers.Default) {
                            WaliAi.chooseCaptureTarget(nextGameState, capturable, difficulty)
                        }

                        if (target != null) {
                            nextGameState = WaliEngine.capturePiece(nextGameState, target)
                            audio?.playCapture()
                        }
                    }

                    if (nextGameState.winner != null) {
                        checkPlayEndGameAudio(nextGameState)
                        checkRecordGameOverResult(nextGameState)
                    }
                    val aiWinner = nextGameState.winner
                    val isAiTwoPiecesDefeat = aiWinner != null &&
                        nextGameState.ruleOptions.defeatAtTwoPieces &&
                        !nextGameState.hasPendingPlacements(aiWinner.opponent) &&
                        nextGameState.piecesOnBoard(aiWinner.opponent) == 2

                    _uiState.update {
                        it.copy(
                            gameState = nextGameState,
                            gameOverReason = if (isAiTwoPiecesDefeat) "Réduit à deux pions" else it.gameOverReason,
                            isAiThinking = false,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = emptySet(),
                            lastMove = pieceMove
                        )
                    }

                    autoSaveGame(_uiState.value)
                    onTurnTransition()
                }
                Phase.GAME_OVER -> {
                    _uiState.update { it.copy(isAiThinking = false) }
                }
            }
        } catch (e: Exception) {
            Log.e("WaliViewModel", "Error in playAiTurn safely caught: ${e.message}", e)
            _uiState.update { it.copy(isAiThinking = false) }
        }
    }

    // =========================================================================
    // CONFIGURATION DE NOUVELLE PARTIE & SÉLECTION DE MODE
    // =========================================================================

    /**
     * Ouvre la boîte de dialogue de sélection de mode (Humain vs Humain / Humain vs Machine).
     */
    fun openModeSelection() {
        audio?.playClick()
        _uiState.update { it.copy(showModeSelectionDialog = true) }
    }

    /**
     * Ferme la boîte de dialogue de sélection de mode.
     */
    fun dismissModeSelectionDialog() {
        audio?.playClick()
        _uiState.update { it.copy(showModeSelectionDialog = false) }
    }

    /**
     * Démarre une nouvelle partie configurée.
     * Si l'humain a choisi les CAILLOUX face à la machine, l'IA (BÂTONNETS) commence automatiquement.
     */
    fun startNewGame(
        mode: GameMode,
        humanPlayer: Player = Player.STICKS,
        difficulty: AiDifficulty = AiDifficulty.EASY
    ) {
        audio?.playClick()
        aiTurnJob?.cancel()
        if (mode != GameMode.ONLINE) {
            stopOnlineInactivityMonitoring()
            onlineSessionJob?.cancel()
            onlineSessionJob = null
        }

        viewModelScope.launch {
            runCatching {
                storage?.clearSavedGame()
            }.onFailure { e ->
                Log.w("WaliViewModel", "clearSavedGame safely ignored exception: ${e.message}")
            }
        }

        _uiState.value = _uiState.value.copy(
            gameState = WaliEngine.initializeGame(ruleOptions = currentRuleOptions()),
            gameMode = mode,
            humanPlayer = humanPlayer,
            aiDifficulty = difficulty,
            isAiThinking = false,
            selectedPosition = null,
            highlightedMoves = emptySet(),
            capturablePositions = emptySet(),
            lastMove = null,
            feedbackMessage = null,
            showPauseMenu = false,
            showResetConfirmation = false,
            showModeSelectionDialog = false,
            showForfeitConfirmation = false,
            gameOverReason = null,
            onlineGameSession = if (mode == GameMode.ONLINE) _uiState.value.onlineGameSession else null,
            onlineRole = if (mode == GameMode.ONLINE) _uiState.value.onlineRole else null,
            waitingForOpponentCode = if (mode == GameMode.ONLINE) _uiState.value.waitingForOpponentCode else null,
            localPlayerRole = if (mode == GameMode.ONLINE) _uiState.value.localPlayerRole else null,
            canDeclareAbandonment = false,
            isOpponentDisconnected = false,
            gameDurationSeconds = 0L,
            isGameResultRecorded = false,
            localDrawOfferedBy = null,
            localDrawOfferTurnCount = -1,
            localLastDrawProposer = null
        )

        startDurationTracking()

        // Transition de tour : IA si elle commence, ou minuteur pour le joueur humain
        onTurnTransition()
    }

    /**
     * Demande l'ouverture de la boîte de dialogue de confirmation de nouvelle partie.
     */
    fun onNewGameRequested() {
        audio?.playClick()
        _uiState.update { it.copy(showResetConfirmation = true) }
    }

    /**
     * Confirme et réinitialise la partie selon les réglages actuels de mode.
     */
    fun confirmNewGame() {
        audio?.playClick()
        val currentMode = _uiState.value.gameMode
        val currentHuman = _uiState.value.humanPlayer
        val currentDiff = _uiState.value.aiDifficulty

        startNewGame(
            mode = currentMode,
            humanPlayer = currentHuman,
            difficulty = currentDiff
        )
    }

    /**
     * Clôture le dialogue de fin de partie et ouvre le dialogue de sélection de mode
     * pour lancer une nouvelle partie.
     */
    fun onGameOverContinue() {
        audio?.playClick()
        aiTurnJob?.cancel()

        viewModelScope.launch {
            runCatching {
                storage?.clearSavedGame()
            }.onFailure { e ->
                Log.w("WaliViewModel", "clearSavedGame safely ignored exception: ${e.message}")
            }
        }

        _uiState.update {
            it.copy(
                gameState = WaliEngine.initializeGame(ruleOptions = currentRuleOptions()),
                selectedPosition = null,
                highlightedMoves = emptySet(),
                capturablePositions = emptySet(),
                lastMove = null,
                feedbackMessage = null,
                showPauseMenu = false,
                showResetConfirmation = false,
                showForfeitConfirmation = false,
                gameOverReason = null,
                localDrawOfferedBy = null,
                localDrawOfferTurnCount = -1,
                localLastDrawProposer = null,
                showModeSelectionDialog = true
            )
        }
    }

    /**
     * Demande l'ouverture de la boîte de dialogue de confirmation pour déclarer forfait.
     */
    fun requestForfeit() {
        audio?.playClick()
        if (_uiState.value.canDeclareForfeit) {
            _uiState.update { it.copy(showForfeitConfirmation = true, showPauseMenu = false) }
        }
    }

    /**
     * Ferme la boîte de dialogue de confirmation d'abandon.
     */
    fun dismissForfeitConfirmation() {
        _uiState.update { it.copy(showForfeitConfirmation = false) }
    }

    /**
     * Valide l'abandon après confirmation par l'utilisateur.
     */
    fun confirmForfeit() {
        dismissForfeitConfirmation()
        declareForfeit()
    }

    /**
     * Déclare forfait pour la partie en cours.
     * Le joueur qui abandonne perd immédiatement et son adversaire est déclaré vainqueur.
     */
    fun declareForfeit() {
        audio?.playClick()
        val current = _uiState.value
        if (!current.canDeclareForfeit) return

        stopTurnTimer()
        aiTurnJob?.cancel()

        // Effacer la sauvegarde locale
        viewModelScope.launch {
            runCatching {
                storage?.clearSavedGame()
            }.onFailure { e ->
                Log.w("WaliViewModel", "clearSavedGame safely ignored: ${e.message}")
            }
        }

        when (current.gameMode) {
            GameMode.ONLINE -> {
                val session = current.onlineGameSession
                val myRole = current.localPlayerRole ?: current.humanPlayer
                val winner = myRole.opponent
                val gameOverState = current.gameState.copy(
                    winner = winner,
                    phase = Phase.GAME_OVER,
                    sticksRemaining = current.gameState.remainingPiecesOf(Player.STICKS),
                    stonesRemaining = current.gameState.remainingPiecesOf(Player.STONES),
                    moveHistory = current.gameState.moveHistory + "Abandon de $myRole. Victoire de $winner !"
                )
                checkPlayEndGameAudio(gameOverState)
                checkRecordGameOverResult(gameOverState, "Abandon")

                _uiState.update {
                    it.copy(
                        gameState = gameOverState,
                        gameOverReason = "Abandon",
                        showForfeitConfirmation = false,
                        showPauseMenu = false,
                        localDrawOfferedBy = null,
                        feedbackMessage = "Vous avez déclaré forfait. Victoire de l'adversaire."
                    )
                }

                if (session != null) {
                    viewModelScope.launch {
                        runCatching {
                            onlineRepository?.declareAbandonment(session.code, winner)
                        }.onFailure { e ->
                            Log.w("WaliViewModel", "declareForfeit Firestore warning: ${e.message}")
                        }
                    }
                }
            }
            GameMode.VS_COMPUTER -> {
                val winner = current.humanPlayer.opponent
                val gameOverState = current.gameState.copy(
                    winner = winner,
                    phase = Phase.GAME_OVER,
                    sticksRemaining = current.gameState.remainingPiecesOf(Player.STICKS),
                    stonesRemaining = current.gameState.remainingPiecesOf(Player.STONES),
                    moveHistory = current.gameState.moveHistory + "Partie abandonnée. Victoire de $winner !"
                )
                checkPlayEndGameAudio(gameOverState)
                checkRecordGameOverResult(gameOverState, "Abandon")

                _uiState.update {
                    it.copy(
                        gameState = gameOverState,
                        gameOverReason = "Abandon",
                        showForfeitConfirmation = false,
                        showPauseMenu = false,
                        localDrawOfferedBy = null,
                        feedbackMessage = "Vous avez déclaré forfait."
                    )
                }
            }
            GameMode.TWO_PLAYERS -> {
                val forfeiter = current.gameState.currentPlayer
                val winner = forfeiter.opponent
                val gameOverState = current.gameState.copy(
                    winner = winner,
                    phase = Phase.GAME_OVER,
                    sticksRemaining = current.gameState.remainingPiecesOf(Player.STICKS),
                    stonesRemaining = current.gameState.remainingPiecesOf(Player.STONES),
                    moveHistory = current.gameState.moveHistory + "Abandon de $forfeiter. Victoire de $winner !"
                )
                checkPlayEndGameAudio(gameOverState)
                checkRecordGameOverResult(gameOverState, "Abandon")

                _uiState.update {
                    it.copy(
                        gameState = gameOverState,
                        gameOverReason = "Abandon",
                        showForfeitConfirmation = false,
                        showPauseMenu = false,
                        localDrawOfferedBy = null,
                        feedbackMessage = "Forfait déclaré par $forfeiter."
                    )
                }
            }
        }
    }

    // =========================================================================
    // PROPOSITION ET RÉPONSE DE MATCH NUL (PARTIE G1)
    // =========================================================================

    /**
     * Propose un match nul pendant le tour du joueur courant en phase de déplacement.
     * - Mode TWO_PLAYERS : ouvre immédiatement la boîte de dialogue bloquante pour l'adversaire.
     * - Mode ONLINE : enregistre la proposition dans Firestore et publie le message système dans le chat.
     * - Mode VS_COMPUTER : indisponible (l'IA ne peut pas accepter de match nul).
     */
    fun proposeDraw() {
        val current = _uiState.value
        if (!current.canProposeDraw) return
        audio?.playClick()

        when (current.gameMode) {
            GameMode.VS_COMPUTER -> return
            GameMode.TWO_PLAYERS -> {
                val proposer = current.gameState.currentPlayer
                val turnCount = current.gameState.moveHistory.size
                _uiState.update {
                    it.copy(
                        localDrawOfferedBy = proposer,
                        localDrawOfferTurnCount = turnCount,
                        localLastDrawProposer = proposer,
                        selectedPosition = null,
                        highlightedMoves = emptySet(),
                        feedbackMessage = "Votre adversaire propose un match nul. Accepter ou Refuser ?"
                    )
                }
            }
            GameMode.ONLINE -> {
                val session = current.onlineGameSession ?: return
                val repo = onlineRepository ?: return
                val myUid = current.localPlayerUid ?: repo.currentUserId
                val myRole = current.localPlayerRole ?: session.getPlayerRole(myUid) ?: return
                val campName = if (myRole == Player.STICKS) "Bâtonnets" else "Cailloux"
                val sysText = "$campName propose un match nul"

                viewModelScope.launch {
                    val result = repo.proposeDraw(session.code, myUid)
                    result.onSuccess {
                        runCatching {
                            chatRepository?.postSystemMessage(session.code, sysText)
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(feedbackMessage = err.message ?: "Impossible de proposer un match nul.")
                        }
                    }
                }
            }
        }
    }

    /**
     * Répond à une proposition de match nul en attente ([accept] = true pour accepter, false pour refuser).
     */
    fun respondToDraw(accept: Boolean) {
        val current = _uiState.value
        if (current.isSpectator) return
        audio?.playClick()

        when (current.gameMode) {
            GameMode.VS_COMPUTER -> return
            GameMode.TWO_PLAYERS -> {
                val offeredBy = current.localDrawOfferedBy ?: return
                if (accept) {
                    stopTurnTimer()
                    aiTurnJob?.cancel()
                    viewModelScope.launch {
                        runCatching { storage?.clearSavedGame() }
                    }
                    val drawReason = "Match nul — Les deux joueurs ont accepté."
                    val endedState = current.gameState.copy(
                        phase = Phase.GAME_OVER,
                        winner = null,
                        pendingCaptures = 0,
                        moveHistory = current.gameState.moveHistory + drawReason
                    )
                    checkRecordGameOverResult(endedState, drawReason)
                    _uiState.update {
                        it.copy(
                            gameState = endedState,
                            localDrawOfferedBy = null,
                            gameOverReason = drawReason,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = emptySet(),
                            feedbackMessage = drawReason
                        )
                    }
                } else {
                    val refuser = offeredBy.opponent
                    val baseState = current.gameState.copy(pendingCaptures = 0)
                    val nextState = if (baseState.currentPlayer != refuser) {
                        WaliEngine.switchPlayer(baseState)
                    } else {
                        baseState
                    }
                    _uiState.update {
                        it.copy(
                            gameState = nextState,
                            localDrawOfferedBy = null,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = emptySet(),
                            feedbackMessage = "Match nul refusé — la partie continue."
                        )
                    }
                    autoSaveGame(_uiState.value)
                    onTurnTransition()
                }
            }
            GameMode.ONLINE -> {
                val session = current.onlineGameSession ?: return
                val repo = onlineRepository ?: return
                val myUid = current.localPlayerUid ?: repo.currentUserId
                if (session.drawOfferedBy == null || session.drawOfferedBy == myUid) return
                val myRole = current.localPlayerRole ?: session.getPlayerRole(myUid)
                val campName = if (myRole == Player.STICKS) "Bâtonnets" else "Cailloux"
                val sysText = if (accept) {
                    "Match nul accepté par les deux joueurs"
                } else {
                    "$campName a refusé le match nul — la partie continue"
                }

                viewModelScope.launch {
                    val result = repo.respondToDraw(session.code, accept, myUid)
                    result.onSuccess {
                        runCatching {
                            chatRepository?.postSystemMessage(session.code, sysText)
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(feedbackMessage = err.message ?: "Impossible de répondre au match nul.")
                        }
                    }
                }
            }
        }
    }

    /**
     * Accepte la proposition de match nul en attente.
     */
    fun acceptDraw() = respondToDraw(true)

    /**
     * Refuse la proposition de match nul en attente.
     */
    fun refuseDraw() = respondToDraw(false)

    /**
     * Ouvre le menu de pause (overlay).
     */
    fun openPauseMenu() {
        audio?.playClick()
        _uiState.update { it.copy(showPauseMenu = true) }
    }

    /**
     * Reprend la partie en cours en fermant le menu de pause.
     */
    fun resumeGame() {
        audio?.playClick()
        _uiState.update { it.copy(showPauseMenu = false) }
    }

    /**
     * Recommence la partie courante depuis le menu de pause.
     */
    fun restartCurrentGame() {
        audio?.playClick()
        _uiState.update { it.copy(showPauseMenu = false) }
        val currentMode = _uiState.value.gameMode
        val currentHuman = _uiState.value.humanPlayer
        val currentDiff = _uiState.value.aiDifficulty

        startNewGame(
            mode = currentMode,
            humanPlayer = currentHuman,
            difficulty = currentDiff
        )
    }

    /**
     * Quitte vers le menu principal (sélection de mode) depuis le menu de pause.
     */
    fun returnToMainMenu() {
        audio?.playClick()
        if (_uiState.value.isOnlineGame) {
            leaveOnlineGame()
        }
        _uiState.update {
            it.copy(
                showPauseMenu = false,
                showModeSelectionDialog = true
            )
        }
    }

    /**
     * Annule la boîte de dialogue de nouvelle partie.
     */
    fun dismissNewGameDialog() {
        audio?.playClick()
        _uiState.update { it.copy(showResetConfirmation = false) }
    }

    /**
     * Efface le message de feedback courant.
     */
    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    // =========================================================================
    // MINUTEUR PAR TOUR & GESTION DU DÉPASSEMENT DE TEMPS
    // =========================================================================

    /**
     * Démarre le compte à rebours pour le joueur humain dont c'est le tour.
     */
    fun startTurnTimer() {
        timerJob?.cancel()
        val state = _uiState.value
        if (state.isSpectator || !state.isTimerEnabled || state.gameState.phase == Phase.GAME_OVER || state.isAiTurn) {
            return
        }

        _uiState.update { it.copy(timerRemainingSeconds = it.timerDurationSeconds) }

        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val beforeTick = _uiState.value
                if (beforeTick.isTimerPaused) {
                    break
                }
                tickTimer()
                val current = _uiState.value
                if (!current.isTimerEnabled || current.isTimerPaused || current.gameState.phase == Phase.GAME_OVER || current.isAiTurn || current.timerRemainingSeconds <= 0) {
                    break
                }
            }
        }
    }

    /**
     * Arrête le minuteur actif sans réinitialiser l'état.
     */
    fun stopTurnTimer() {
        timerJob?.cancel()
        timerJob = null
        durationJob?.cancel()
        durationJob = null
        rematchTimeoutJob?.cancel()
        rematchTimeoutJob = null
    }

    /**
     * Décrémente le minuteur d'une seconde si celui-ci n'est pas suspendu (pause).
     * Si le temps tombe à zéro, déclenche un coup légal aléatoire automatique.
     */
    fun tickTimer() {
        val current = _uiState.value
        if (current.isSpectator || !current.isTimerEnabled || current.gameState.phase == Phase.GAME_OVER || current.isAiTurn) {
            return
        }
        if (current.isTimerPaused) {
            return
        }

        if (current.isOnlineGame) {
            checkOnlineInactivity()

            if (current.isOpponentTurnOnline) {
                val nextSec = current.timerRemainingSeconds - 1
                _uiState.update { it.copy(timerRemainingSeconds = nextSec.coerceAtLeast(0)) }
                return
            }
        }

        val nextSec = current.timerRemainingSeconds - 1
        if (nextSec <= 0) {
            _uiState.update { it.copy(timerRemainingSeconds = 0) }
            handleTimerExpired()
        } else {
            _uiState.update { it.copy(timerRemainingSeconds = nextSec) }
        }
    }

    /**
     * Orchestre la transition de tour entre joueurs (IA ou joueur humain suivant).
     */
    fun onTurnTransition() {
        val state = _uiState.value
        if (state.isSpectator || state.gameState.winner != null || state.gameState.phase == Phase.GAME_OVER) {
            stopTurnTimer()
            return
        }
        if (state.isAiTurn) {
            stopTurnTimer()
            checkTriggerAiIfNeeded()
        } else {
            startTurnTimer()
        }
    }

    /**
     * Exécute un coup légal aléatoire lorsque le temps du joueur humain est écoulé.
     * Ne s'exécute jamais pour un spectateur.
     */
    fun handleTimerExpired() {
        val state = _uiState.value
        val currentGameState = state.gameState

        if (state.isSpectator || currentGameState.phase == Phase.GAME_OVER || state.isAiTurn || !state.isTimerEnabled) {
            return
        }

        // Cas 1 : Capture en attente (le joueur a formé un alignement de 3 mais n'a pas sélectionné de pion adverse)
        if (WaliEngine.canCapture(currentGameState)) {
            val opponent = currentGameState.currentPlayer.opponent
            val capturable = currentGameState.board.filter { it.value == opponent }.keys.toList()
            if (capturable.isNotEmpty()) {
                val randomTarget = capturable.random()
                val nextGameState = WaliEngine.capturePiece(currentGameState, randomTarget)
                audio?.playCapture()

                if (nextGameState.winner != null) {
                    checkPlayEndGameAudio(nextGameState)
                }

                _uiState.update {
                    it.copy(
                        gameState = nextGameState,
                        capturablePositions = emptySet(),
                        selectedPosition = null,
                        highlightedMoves = emptySet(),
                        feedbackMessage = "Temps écoulé — un coup a été joué automatiquement."
                    )
                }

                syncOnlineMoveIfNeeded(nextGameState)
                autoSaveGame(_uiState.value)
                onTurnTransition()
            }
            return
        }

        // Cas 2 : Phase 1 (pose des pions)
        if (currentGameState.phase == Phase.PLACEMENT) {
            val validPlacements = WaliEngine.getValidPlacements(currentGameState)
            if (validPlacements.isNotEmpty()) {
                val randomPlacement = validPlacements.random()
                val player = currentGameState.currentPlayer
                val nextGameState = WaliEngine.placePiece(currentGameState, randomPlacement)
                val isNowMovement = nextGameState.phase == Phase.MOVEMENT

                audio?.playPlace(player)

                _uiState.update {
                    it.copy(
                        gameState = nextGameState,
                        selectedPosition = null,
                        highlightedMoves = emptySet(),
                        capturablePositions = emptySet(),
                        feedbackMessage = if (isNowMovement) {
                            "Temps écoulé — pion posé. Début des déplacements !"
                        } else {
                            "Temps écoulé — un coup a été joué automatiquement."
                        }
                    )
                }

                syncOnlineMoveIfNeeded(nextGameState)
                autoSaveGame(_uiState.value)
                onTurnTransition()
            }
            return
        }

        // Cas 3 : Phase 2 (déplacement)
        if (currentGameState.phase == Phase.MOVEMENT) {
            // Cas 3a : Pose obligatoire d'un pion en attente
            if (WaliEngine.isPlacementRequired(currentGameState)) {
                val validPlacements = WaliEngine.getValidPlacements(currentGameState)
                if (validPlacements.isNotEmpty()) {
                    val randomPlacement = validPlacements.random()
                    val player = currentGameState.currentPlayer
                    val nextGameState = WaliEngine.placePiece(currentGameState, randomPlacement)
                    audio?.playPlace(player)

                    if (nextGameState.winner != null) {
                        checkPlayEndGameAudio(nextGameState)
                    }

                    _uiState.update {
                        it.copy(
                            gameState = nextGameState,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = emptySet(),
                            feedbackMessage = "Temps écoulé — un coup a été joué automatiquement."
                        )
                    }

                    syncOnlineMoveIfNeeded(nextGameState)
                    autoSaveGame(_uiState.value)
                    onTurnTransition()
                }
                return
            }

            // Cas 3b : Déplacement orthogonal classique
            val allValidMoves = mutableListOf<Pair<Position, Position>>()
            currentGameState.board.forEach { (pos, owner) ->
                if (owner == currentGameState.currentPlayer) {
                    val destinations = WaliEngine.getValidMoves(currentGameState, pos)
                    destinations.forEach { dest ->
                        allValidMoves.add(pos to dest)
                    }
                }
            }

            if (allValidMoves.isEmpty()) {
                // Aucun coup légal disponible -> défaite par blocage
                val winner = currentGameState.currentPlayer.opponent
                val gameOverState = currentGameState.copy(
                    winner = winner,
                    phase = Phase.GAME_OVER,
                    moveHistory = currentGameState.moveHistory + "${currentGameState.currentPlayer} n'a aucun coup légal. Victoire de $winner !"
                )
                checkPlayEndGameAudio(gameOverState)
                _uiState.update {
                    it.copy(
                        gameState = gameOverState,
                        selectedPosition = null,
                        highlightedMoves = emptySet(),
                        capturablePositions = emptySet(),
                        feedbackMessage = "Temps écoulé — aucun coup légal disponible."
                    )
                }
                syncOnlineMoveIfNeeded(gameOverState)
                autoSaveGame(_uiState.value)
                stopTurnTimer()
                return
            }

            val (randomFrom, randomTo) = allValidMoves.random()
            val timeoutMove = PieceMove(from = randomFrom, to = randomTo, player = currentGameState.currentPlayer)
            var nextGameState = WaliEngine.movePiece(currentGameState, randomFrom, randomTo)
            audio?.playMove()

            // Si le coup forme une ligne de 3, capture immédiate d'un pion adverse au hasard pour achever le tour
            if (WaliEngine.canCapture(nextGameState)) {
                val opp = nextGameState.currentPlayer.opponent
                val capturable = nextGameState.board.filter { it.value == opp }.keys.toList()
                if (capturable.isNotEmpty()) {
                    val randomCaptureTarget = capturable.random()
                    nextGameState = WaliEngine.capturePiece(nextGameState, randomCaptureTarget)
                    audio?.playCapture()
                }
            }

            if (nextGameState.winner != null) {
                checkPlayEndGameAudio(nextGameState)
            }

            _uiState.update {
                it.copy(
                    gameState = nextGameState,
                    selectedPosition = null,
                    highlightedMoves = emptySet(),
                    capturablePositions = emptySet(),
                    lastMove = timeoutMove,
                    feedbackMessage = "Temps écoulé — un coup a été joué automatiquement."
                )
            }

            syncOnlineMoveIfNeeded(nextGameState)
            autoSaveGame(_uiState.value)
            onTurnTransition()
        }
    }

    // =========================================================================
    // MULTIJOUEUR EN LIGNE (FIREBASE FIRESTORE)
    // =========================================================================

    /**
     * Crée une nouvelle partie en ligne (publique ou privée) avec le rôle choisi.
     */
    fun startCreateOnlineGame(isPublic: Boolean, chosenSide: Player) {
        audio?.playClick()
        val repo = onlineRepository ?: run {
            _uiState.update { it.copy(onlineErrorMessage = "Service en ligne indisponible.") }
            return
        }
        _uiState.update { it.copy(isOnlineLoading = true, onlineErrorMessage = null) }

        viewModelScope.launch {
            try {
                val newCode = WaliOnlineCodeGenerator.generateCode()
                val prefs = preferencesRepository?.getPreferences()
                val creatorRuleOptions = WaliRuleOptions(
                    mandatoryAlignment = prefs?.isMandatoryAlignmentEnabled ?: _uiState.value.isMandatoryAlignmentEnabled,
                    defeatAtTwoPieces = prefs?.isDefeatAtTwoPiecesEnabled ?: _uiState.value.isDefeatAtTwoPiecesEnabled
                )
                val initialGameState = WaliEngine.initializeGame(ruleOptions = creatorRuleOptions)
                val result = repo.createGame(
                    code = newCode,
                    isPublic = isPublic,
                    creatorSide = chosenSide,
                    initialGameState = initialGameState
                )
                val session = result.getOrThrow()
                val uid = repo.currentUserId
                _uiState.update {
                    it.copy(
                        isOnlineLoading = false,
                        gameMode = GameMode.ONLINE,
                        onlineRole = com.example.wali.online.OnlineRole.CREATOR,
                        localPlayerUid = uid,
                        localPlayerRole = chosenSide,
                        waitingForOpponentCode = session.code,
                        onlineGameSession = session,
                        gameState = initialGameState,
                        feedbackMessage = "Partie créée ! En attente d'un adversaire…"
                    )
                }
                observeOnlineSession(session.code)
            } catch (e: Exception) {
                Log.w("WaliViewModel", "startCreateOnlineGame warning: ${e.message}")
                _uiState.update {
                    it.copy(
                        isOnlineLoading = false,
                        onlineErrorMessage = e.message ?: "Échec de création de la partie"
                    )
                }
            }
        }
    }

    /**
     * Rejoint une partie en ligne existante via son code joueur de 6 caractères.
     */
    fun startJoinOnlineGame(code: String) {
        audio?.playClick()
        val repo = onlineRepository ?: run {
            _uiState.update { it.copy(onlineErrorMessage = "Service en ligne indisponible.") }
            return
        }
        val cleanCode = code.trim().uppercase()
        if (WaliOnlineCodeGenerator.isValidSpectatorCode(cleanCode)) {
            _uiState.update {
                it.copy(onlineErrorMessage = "Ce code est un code spectateur. Utilisez l'onglet Spectateurs.")
            }
            return
        }
        if (cleanCode.length != 6) {
            _uiState.update { it.copy(onlineErrorMessage = "Le code doit comporter exactement 6 caractères.") }
            return
        }
        _uiState.update { it.copy(isOnlineLoading = true, onlineErrorMessage = null) }

        viewModelScope.launch {
            try {
                val result = repo.joinGame(cleanCode)
                val session = result.getOrThrow()
                val uid = repo.currentUserId
                val myRole = if (session.creatorUid == uid) {
                    session.creatorPlayer
                } else {
                    session.opponentPlayer ?: session.creatorPlayer.opponent
                }
                val resolvedOnlineRole = if (session.creatorUid == uid) {
                    com.example.wali.online.OnlineRole.CREATOR
                } else {
                    com.example.wali.online.OnlineRole.OPPONENT
                }

                _uiState.update {
                    it.copy(
                        isOnlineLoading = false,
                        gameMode = GameMode.ONLINE,
                        onlineRole = resolvedOnlineRole,
                        localPlayerUid = uid,
                        localPlayerRole = myRole,
                        onlineGameSession = session,
                        gameState = session.gameState,
                        waitingForOpponentCode = null,
                        feedbackMessage = "Partie rejointe avec succès ! Bonne chance !"
                    )
                }
                observeOnlineSession(session.code)
                onTurnTransition()
            } catch (e: Exception) {
                Log.w("WaliViewModel", "startJoinOnlineGame warning: ${e.message}")
                _uiState.update {
                    it.copy(
                        isOnlineLoading = false,
                        onlineErrorMessage = e.message ?: "Impossible de rejoindre la partie"
                    )
                }
            }
        }
    }

    /**
     * Rejoint un salon en ligne en tant que spectateur via son code spectateur de 7 caractères ('S' + 6 caractères).
     * N'écrit jamais dans le document du salon et positionne strictement `onlineRole = OnlineRole.SPECTATOR`
     * et `localPlayerRole = null`.
     */
    fun startSpectateOnlineGame(spectatorCode: String) {
        audio?.playClick()
        val repo = onlineRepository ?: run {
            _uiState.update { it.copy(onlineErrorMessage = "Service en ligne indisponible.") }
            return
        }
        val cleanCode = WaliOnlineCodeGenerator.normalizeCode(spectatorCode)
        if (WaliOnlineCodeGenerator.isValidCode(cleanCode)) {
            _uiState.update {
                it.copy(onlineErrorMessage = "Ce code est un code joueur. Utilisez l'onglet Rejoindre.")
            }
            return
        }
        if (!WaliOnlineCodeGenerator.isValidSpectatorCode(cleanCode)) {
            _uiState.update {
                it.copy(onlineErrorMessage = "Le code spectateur doit comporter 7 caractères et commencer par S.")
            }
            return
        }
        _uiState.update { it.copy(isOnlineLoading = true, onlineErrorMessage = null) }

        viewModelScope.launch {
            try {
                val result = repo.joinAsSpectator(cleanCode)
                val session = result.getOrThrow()
                val uid = repo.currentUserId

                stopTurnTimer()
                stopOnlineInactivityMonitoring()

                _uiState.update {
                    it.copy(
                        isOnlineLoading = false,
                        gameMode = GameMode.ONLINE,
                        onlineRole = com.example.wali.online.OnlineRole.SPECTATOR,
                        localPlayerUid = uid,
                        localPlayerRole = null,
                        onlineGameSession = session,
                        gameState = session.gameState,
                        waitingForOpponentCode = null,
                        selectedPosition = null,
                        highlightedMoves = emptySet(),
                        capturablePositions = emptySet(),
                        canDeclareAbandonment = false,
                        isOpponentDisconnected = false,
                        feedbackMessage = if (session.status == OnlineGameStatus.WAITING) {
                            "Mode spectateur — En attente du second joueur…"
                        } else {
                            "Mode spectateur — Observation en direct."
                        }
                    )
                }
                observeOnlineSession(session.code)
            } catch (e: Exception) {
                Log.w("WaliViewModel", "startSpectateOnlineGame warning: ${e.message}")
                _uiState.update {
                    it.copy(
                        isOnlineLoading = false,
                        onlineErrorMessage = e.message ?: "Impossible de rejoindre ce salon en spectateur"
                    )
                }
            }
        }
    }

    /**
     * Annule la salle d'attente d'une partie créée et repasse en mode local.
     */
    fun cancelOnlineWaiting() {
        audio?.playClick()
        val isSpectator = _uiState.value.isSpectator
        val code = _uiState.value.waitingForOpponentCode ?: _uiState.value.onlineGameSession?.code
        onlineSessionJob?.cancel()
        onlineSessionJob = null
        chatMessagesJob?.cancel()
        chatMessagesJob = null
        rematchTimeoutJob?.cancel()
        rematchTimeoutJob = null
        preRematchMessageIds = emptySet()
        chatRateLimiter.reset()

        if (code != null && !isSpectator) {
            viewModelScope.launch {
                runCatching { onlineRepository?.leaveGame(code) }
                runCatching { chatRepository?.clearMessages(code) }
            }
        }

        _uiState.update {
            it.copy(
                waitingForOpponentCode = null,
                onlineGameSession = null,
                onlineRole = null,
                gameMode = GameMode.TWO_PLAYERS,
                localPlayerRole = null,
                feedbackMessage = null,
                chatState = WaliChatUiState()
            )
        }
    }

    /**
     * Observe en temps réel les mises à jour Firestore de la session de jeu [code].
     */
    private fun observeOnlineSession(code: String) {
        onlineSessionJob?.cancel()
        onlineSessionJob = viewModelScope.launch {
            onlineRepository?.observeGame(code)?.collect { session ->
                if (session == null) {
                    val prevSession = _uiState.value.onlineGameSession
                    val wasEndOfGame = prevSession != null &&
                            (prevSession.status == OnlineGameStatus.FINISHED ||
                                    prevSession.status == OnlineGameStatus.ABANDONED ||
                                    _uiState.value.gameState.phase == Phase.GAME_OVER)
                    if (wasEndOfGame) {
                        handleOpponentLeftDuringRematch()
                    } else {
                        handleOpponentLeftSystemMessage()
                    }
                    return@collect
                }
                handleOnlineSessionUpdate(session)
            }
        }
        startChatObservation(code)
    }

    /**
     * Observe en temps réel les messages de chat pour la partie [code].
     */
    private fun startChatObservation(code: String) {
        chatMessagesJob?.cancel()
        val repo = chatRepository ?: return
        chatMessagesJob = viewModelScope.launch {
            repo.observeMessages(code).collect { rawMessages ->
                val isBlocked = _uiState.value.chatState.isOpponentBlocked
                val myRole = _uiState.value.localPlayerRole
                val myUid = _uiState.value.localPlayerUid ?: onlineRepository?.currentUserId ?: ""
                val currentSessionMessages = rawMessages.filter { it.id !in preRematchMessageIds }
                val filteredMessages = if (isBlocked) {
                    currentSessionMessages.filter {
                        (myUid.isNotBlank() && it.senderUid == myUid) ||
                                (myRole != null && it.senderRole == myRole) ||
                                it.type == ChatMessageType.SYSTEM
                    }
                } else {
                    currentSessionMessages
                }

                val wasOpen = _uiState.value.chatState.isOpen
                val oldMessageIds = _uiState.value.chatState.messages.map { it.id }.toSet()
                val newIncomingFromOther = currentSessionMessages.any { msg ->
                    val isMine = (myUid.isNotBlank() && msg.senderUid == myUid) ||
                            (myRole != null && msg.senderRole == myRole)
                    msg.id.isNotBlank() && msg.id !in oldMessageIds && !isMine && msg.type != ChatMessageType.SYSTEM
                }

                if (newIncomingFromOther) {
                    audio?.playChatMessage()
                }

                val unread = if (wasOpen) 0 else {
                    _uiState.value.chatState.unreadCount + (if (newIncomingFromOther) 1 else 0)
                }

                _uiState.update { current ->
                    current.copy(
                        chatState = current.chatState.copy(
                            messages = filteredMessages,
                            unreadCount = unread,
                            isConnected = true
                        )
                    )
                }
            }
        }
    }

    private fun handleOpponentLeftSystemMessage() {
        _uiState.update { current ->
            val sysMessage = WaliChatMessage(
                id = "sys_left_${System.currentTimeMillis()}",
                senderRole = null,
                senderUid = "system",
                text = "L'adversaire a quitté la partie.",
                type = ChatMessageType.SYSTEM
            )
            current.copy(
                chatState = current.chatState.copy(
                    messages = current.chatState.messages + sysMessage
                )
            )
        }
    }

    // =========================================================================
    // GESTION DU CHAT MULTIJOUEUR EN LIGNE
    // =========================================================================

    /**
     * Ouvre le panneau de chat et réinitialise le compteur de messages non lus.
     */
    fun openChat() {
        _uiState.update { current ->
            current.copy(
                chatState = current.chatState.copy(
                    isOpen = true,
                    unreadCount = 0
                )
            )
        }
    }

    /**
     * Replie le panneau de chat sans interrompre la partie.
     */
    fun closeChat() {
        _uiState.update { current ->
            current.copy(
                chatState = current.chatState.copy(isOpen = false)
            )
        }
    }

    /**
     * Met à jour le texte en cours de saisie dans le chat (max 200 caractères).
     */
    fun onChatInputChanged(text: String) {
        if (text.length <= WaliChatMessage.MAX_TEXT_LENGTH) {
            _uiState.update { current ->
                current.copy(
                    chatState = current.chatState.copy(
                        inputText = text,
                        rateLimitNotice = null
                    )
                )
            }
        }
    }

    /**
     * Masque la bannière d'avertissement initiale de modération.
     */
    fun dismissChatWarningBanner() {
        _uiState.update { current ->
            current.copy(
                chatState = current.chatState.copy(showWarningBanner = false)
            )
        }
    }

    /**
     * Envoie un message texte libre avec vérification du limiteur de débit et filtrage de modération.
     * Autorisé pour les joueurs actifs ET pour les spectateurs (`senderRole == null`, `isSpectator == true`).
     */
    fun sendChatMessage(text: String = _uiState.value.chatState.inputText) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.length > WaliChatMessage.MAX_TEXT_LENGTH) return
        if (_uiState.value.chatState.isOpponentBlocked) return

        val state = _uiState.value
        val code = state.onlineGameSession?.code ?: return
        val isSpectator = state.isSpectator
        val role = if (isSpectator) null else (state.localPlayerRole ?: return)
        val uid = state.localPlayerUid ?: onlineRepository?.currentUserId ?: ""

        when (val rateCheck = chatRateLimiter.canSend()) {
            is WaliChatRateLimiter.Result.TooFast -> {
                _uiState.update { current ->
                    current.copy(
                        chatState = current.chatState.copy(
                            rateLimitNotice = "Veuillez patienter ${rateCheck.waitSecondsRemaining}s avant d'envoyer un message."
                        )
                    )
                }
                return
            }
            is WaliChatRateLimiter.Result.QuotaExceeded -> {
                _uiState.update { current ->
                    current.copy(
                        chatState = current.chatState.copy(
                            rateLimitNotice = "Limite atteinte : maximum 10 messages par minute (${rateCheck.waitSecondsRemaining}s d'attente)."
                        )
                    )
                }
                return
            }
            is WaliChatRateLimiter.Result.Allowed -> {
                // Autorisé
            }
        }

        val cleanText = WaliProfanityFilter.filterOrMask(trimmed)
        chatRateLimiter.recordSent()

        _uiState.update { current ->
            current.copy(
                chatState = current.chatState.copy(
                    inputText = "",
                    rateLimitNotice = null
                )
            )
        }

        viewModelScope.launch {
            val message = WaliChatMessage(
                senderRole = role,
                senderUid = uid,
                isSpectator = isSpectator,
                text = cleanText,
                timestamp = System.currentTimeMillis(),
                type = ChatMessageType.FREE_TEXT
            )
            chatRepository?.sendMessage(code, message)
        }
    }

    /**
     * Envoie un message prédéfini rapide en un tap.
     * Autorisé pour les joueurs actifs ET pour les spectateurs.
     */
    fun sendQuickMessage(quickText: String) {
        if (_uiState.value.chatState.isOpponentBlocked) return
        val state = _uiState.value
        val code = state.onlineGameSession?.code ?: return
        val isSpectator = state.isSpectator
        val role = if (isSpectator) null else (state.localPlayerRole ?: return)
        val uid = state.localPlayerUid ?: onlineRepository?.currentUserId ?: ""

        when (val rateCheck = chatRateLimiter.canSend()) {
            is WaliChatRateLimiter.Result.TooFast -> {
                _uiState.update { current ->
                    current.copy(
                        chatState = current.chatState.copy(
                            rateLimitNotice = "Veuillez patienter ${rateCheck.waitSecondsRemaining}s."
                        )
                    )
                }
                return
            }
            is WaliChatRateLimiter.Result.QuotaExceeded -> {
                _uiState.update { current ->
                    current.copy(
                        chatState = current.chatState.copy(
                            rateLimitNotice = "Trop de messages envoyés (${rateCheck.waitSecondsRemaining}s d'attente)."
                        )
                    )
                }
                return
            }
            is WaliChatRateLimiter.Result.Allowed -> {
                // Autorisé
            }
        }

        chatRateLimiter.recordSent()
        viewModelScope.launch {
            val message = WaliChatMessage(
                senderRole = role,
                senderUid = uid,
                isSpectator = isSpectator,
                text = quickText,
                timestamp = System.currentTimeMillis(),
                type = ChatMessageType.QUICK_MESSAGE
            )
            chatRepository?.sendMessage(code, message)
        }
    }

    /**
     * Masque localement les messages des autres participants pour toute la durée de la partie.
     */
    fun blockOpponentInChat() {
        _uiState.update { current ->
            val myRole = current.localPlayerRole
            val myUid = current.localPlayerUid ?: onlineRepository?.currentUserId ?: ""
            val filtered = current.chatState.messages.filter {
                (myUid.isNotBlank() && it.senderUid == myUid) ||
                        (myRole != null && it.senderRole == myRole) ||
                        it.type == ChatMessageType.SYSTEM
            }
            current.copy(
                chatState = current.chatState.copy(
                    isOpponentBlocked = true,
                    messages = filtered,
                    rateLimitNotice = "Adversaire masqué pour cette partie."
                )
            )
        }
    }

    /**
     * Ouvre la boîte de dialogue de signalement.
     */
    fun openReportDialog() {
        _uiState.update { current ->
            current.copy(
                chatState = current.chatState.copy(showReportDialog = true)
            )
        }
    }

    /**
     * Ferme la boîte de dialogue de signalement.
     */
    fun closeReportDialog() {
        _uiState.update { current ->
            current.copy(
                chatState = current.chatState.copy(showReportDialog = false)
            )
        }
    }

    /**
     * Envoie un signalement modéré dans Firestore.
     */
    fun submitOpponentReport(reason: String) {
        val session = _uiState.value.onlineGameSession ?: return
        val myRole = _uiState.value.localPlayerRole ?: return
        val myUid = onlineRepository?.currentUserId ?: ""
        val opponentRole = myRole.opponent
        val opponentUid = if (myRole == session.creatorPlayer) session.opponentUid ?: "" else session.creatorUid

        val recentMessages = _uiState.value.chatState.messages
            .takeLast(10)
            .map { "${it.senderRole?.name ?: "SYSTEM"}: ${it.text}" }

        val report = WaliReport(
            gameCode = session.code,
            reportedUid = opponentUid,
            reportedRole = opponentRole.name,
            reporterUid = myUid,
            reporterRole = myRole.name,
            reason = reason,
            recentMessages = recentMessages,
            timestamp = System.currentTimeMillis()
        )

        viewModelScope.launch {
            chatRepository?.submitReport(report)
            _uiState.update { current ->
                current.copy(
                    chatState = current.chatState.copy(
                        showReportDialog = false,
                        reportNotice = "Signalement envoyé. Merci de contribuer au respect de la communauté."
                    )
                )
            }
        }
    }

    /**
     * Masque l'avis de confirmation de signalement.
     */
    fun dismissReportNotice() {
        _uiState.update { current ->
            current.copy(
                chatState = current.chatState.copy(reportNotice = null)
            )
        }
    }

    /**
     * Traite les changements d'état reçus depuis Firestore.
     */
    private fun handleOnlineSessionUpdate(session: OnlineGameSession) {
        val current = _uiState.value
        val prevSession = current.onlineGameSession
        val prevGameState = current.gameState
        val currentUid = current.localPlayerUid ?: onlineRepository?.currentUserId

        val isSpectator = current.onlineRole == com.example.wali.online.OnlineRole.SPECTATOR ||
                (!currentUid.isNullOrBlank() && !session.isParticipant(currentUid) && current.localPlayerRole == null)

        val resolvedOnlineRole: com.example.wali.online.OnlineRole = when {
            isSpectator -> com.example.wali.online.OnlineRole.SPECTATOR
            session.creatorUid == currentUid -> com.example.wali.online.OnlineRole.CREATOR
            session.opponentUid == currentUid -> com.example.wali.online.OnlineRole.OPPONENT
            else -> current.onlineRole ?: com.example.wali.online.OnlineRole.CREATOR
        }

        // Piège 2 corrigé : aucun repli sur session.creatorPlayer pour un spectateur.
        // Un spectateur garde strictement localPlayerRole = null.
        val myRole: Player? = when {
            isSpectator -> null
            session.creatorUid == currentUid -> session.creatorPlayer
            session.opponentUid == currentUid -> session.opponentPlayer ?: session.creatorPlayer.opponent
            else -> current.localPlayerRole ?: session.creatorPlayer
        }

        val prevStatus = prevSession?.status
        val prevGamesPlayed = prevSession?.gamesPlayed ?: 1

        when (session.status) {
            OnlineGameStatus.WAITING -> {
                stopOnlineInactivityMonitoring()
                stopRematchTimeoutCountdown()
                _uiState.update {
                    it.copy(
                        onlineGameSession = session,
                        onlineRole = resolvedOnlineRole,
                        waitingForOpponentCode = if (isSpectator) null else session.code,
                        localPlayerRole = myRole
                    )
                }
            }
            OnlineGameStatus.ONGOING -> {
                stopRematchTimeoutCountdown()
                val wasWaiting = prevSession?.status == OnlineGameStatus.WAITING || current.waitingForOpponentCode != null
                val isRematchReset = (prevStatus == OnlineGameStatus.FINISHED ||
                        prevStatus == OnlineGameStatus.ABANDONED ||
                        prevGameState.phase == Phase.GAME_OVER) &&
                        (session.gamesPlayed > prevGamesPlayed || session.gameState.phase != Phase.GAME_OVER)

                if (isRematchReset) {
                    // Revanche acceptée par les deux joueurs : réinitialisation complète dans le même salon
                    audio?.playClick()
                    durationJob?.cancel()
                    preRematchMessageIds = current.chatState.messages.map { it.id }.toSet()

                    val separatorMsg = WaliChatMessage(
                        id = "sys_rematch_${session.gamesPlayed}",
                        senderRole = null,
                        senderUid = "system",
                        text = "--- Nouvelle partie ---",
                        timestamp = System.currentTimeMillis(),
                        type = ChatMessageType.SYSTEM
                    )

                    _uiState.update { state ->
                        state.copy(
                            onlineGameSession = session,
                            onlineRole = resolvedOnlineRole,
                            gameState = session.gameState,
                            waitingForOpponentCode = null,
                            gameMode = GameMode.ONLINE,
                            localPlayerRole = myRole,
                            selectedPosition = null,
                            highlightedMoves = emptySet(),
                            capturablePositions = emptySet(),
                            lastMove = null,
                            feedbackMessage = when {
                                isSpectator -> "Nouvelle manche lancée dans ce salon !"
                                session.gameState.currentPlayer == myRole -> {
                                    val label = if (myRole == Player.STICKS) "Bâtonnets" else "Cailloux"
                                    "Revanche lancée ! C'est votre tour ($label)."
                                }
                                else -> {
                                    val oppLabel = if (session.gameState.currentPlayer == Player.STICKS) "Bâtonnets" else "Cailloux"
                                    "Revanche lancée ! Tour de l'adversaire ($oppLabel)."
                                }
                            },
                            canDeclareAbandonment = false,
                            isOpponentDisconnected = false,
                            showForfeitConfirmation = false,
                            gameOverReason = null,
                            localDrawOfferedBy = null,
                            localDrawOfferTurnCount = -1,
                            localLastDrawProposer = null,
                            gameDurationSeconds = 0L,
                            isGameResultRecorded = false,
                            rematchTimeoutRemainingSeconds = null,
                            rematchStatusMessage = null,
                            chatState = state.chatState.copy(
                                messages = listOf(separatorMsg),
                                unreadCount = 0
                            )
                        )
                    }

                    if (!isSpectator) {
                        startDurationTracking()
                        startOnlineInactivityMonitoring()
                        onTurnTransition()
                    }

                    if (!isSpectator && currentUid != null && currentUid == session.creatorUid) {
                        viewModelScope.launch {
                            runCatching {
                                chatRepository?.postSystemMessage(session.code, "--- Nouvelle partie ---")
                            }
                        }
                    }
                    return
                }

                val remoteMoved = prevGameState != session.gameState
                val drawJustOffered = session.drawOfferedBy != null && prevSession?.drawOfferedBy == null
                val drawJustRefused = session.drawOfferedBy == null && prevSession?.drawOfferedBy != null
                var onlineMove: PieceMove? = null

                if (wasWaiting) {
                    audio?.playClick()
                } else if (remoteMoved && !drawJustRefused) {
                    val oldPieces = prevGameState.piecesOnBoard(Player.STICKS) + prevGameState.piecesOnBoard(Player.STONES)
                    val newPieces = session.gameState.piecesOnBoard(Player.STICKS) + session.gameState.piecesOnBoard(Player.STONES)
                    val oldPlaced = (12 - prevGameState.sticksRemaining) + (12 - prevGameState.stonesRemaining)
                    val newPlaced = (12 - session.gameState.sticksRemaining) + (12 - session.gameState.stonesRemaining)

                    if (newPlaced > oldPlaced) {
                        audio?.playPlace(session.gameState.currentPlayer.opponent)
                    } else if (newPieces < oldPieces) {
                        audio?.playCapture()
                    } else {
                        audio?.playMove()
                        val fromPos = prevGameState.board.entries.firstOrNull { (pos, p) -> p != null && session.gameState.board[pos] == null }?.key
                        val toPos = session.gameState.board.entries.firstOrNull { (pos, p) -> p != null && prevGameState.board[pos] == null }?.key
                        if (fromPos != null && toPos != null && prevGameState.board[fromPos] == session.gameState.board[toPos]) {
                            onlineMove = PieceMove(from = fromPos, to = toPos, player = session.gameState.board[toPos]!!)
                        }
                    }
                }

                if (session.gameState.winner != null && prevGameState.winner == null) {
                    checkPlayEndGameAudio(session.gameState)
                    checkRecordGameOverResult(session.gameState)
                }

                _uiState.update {
                    it.copy(
                        onlineGameSession = session,
                        onlineRole = resolvedOnlineRole,
                        gameState = session.gameState,
                        waitingForOpponentCode = null,
                        gameMode = GameMode.ONLINE,
                        localPlayerRole = myRole,
                        selectedPosition = null,
                        highlightedMoves = emptySet(),
                        lastMove = onlineMove ?: it.lastMove,
                        capturablePositions = if (!isSpectator && myRole != null && WaliEngine.canCapture(session.gameState) && session.gameState.currentPlayer == myRole) {
                            val opp = session.gameState.currentPlayer.opponent
                            session.gameState.board.filter { entry -> entry.value == opp }.keys
                        } else emptySet(),
                        feedbackMessage = when {
                            isSpectator && wasWaiting -> "Les deux joueurs sont connectés ! La partie commence."
                            wasWaiting -> "Adversaire connecté ! La partie commence."
                            drawJustOffered && isSpectator -> "Match nul proposé — en attente de réponse…"
                            drawJustOffered && session.drawOfferedBy == currentUid -> "Proposition de match nul envoyée à l'adversaire…"
                            drawJustOffered -> "Votre adversaire propose un match nul. Accepter ou Refuser ?"
                            drawJustRefused -> "Match nul refusé — la partie continue."
                            else -> null
                        },
                        canDeclareAbandonment = false,
                        isOpponentDisconnected = false
                    )
                }

                if (!isSpectator) {
                    startOnlineInactivityMonitoring()

                    if (wasWaiting || remoteMoved || drawJustRefused) {
                        onTurnTransition()
                    }
                }
            }
            OnlineGameStatus.ABANDONED -> {
                stopOnlineInactivityMonitoring()

                if (session.abandonedBy?.startsWith("LEFT:") == true && session.abandonedBy != "LEFT:$currentUid") {
                    handleOpponentLeftDuringRematch()
                    return
                }

                val winner = session.winner ?: myRole
                val isLocalWinner = (myRole != null && winner == myRole)
                val reasonText = when {
                    isSpectator -> "Abandon"
                    isLocalWinner -> "Abandon de l'adversaire"
                    else -> "Abandon"
                }
                val gameOverState = session.gameState.copy(
                    winner = winner,
                    phase = Phase.GAME_OVER,
                    sticksRemaining = session.gameState.remainingPiecesOf(Player.STICKS),
                    stonesRemaining = session.gameState.remainingPiecesOf(Player.STONES),
                    moveHistory = if (prevGameState.phase == Phase.GAME_OVER) {
                        prevGameState.moveHistory
                    } else {
                        session.gameState.moveHistory + when {
                            isSpectator -> "Partie terminée par abandon."
                            isLocalWinner -> "Victoire par abandon de l'adversaire !"
                            else -> "Partie terminée par forfait."
                        }
                    }
                )
                if (prevGameState.winner == null && prevGameState.phase != Phase.GAME_OVER) {
                    checkPlayEndGameAudio(gameOverState)
                    checkRecordGameOverResult(gameOverState, reasonText)
                }
                stopTurnTimer()
                if (!isSpectator) {
                    viewModelScope.launch {
                        runCatching { storage?.clearSavedGame() }
                    }
                }
                _uiState.update {
                    it.copy(
                        onlineGameSession = session,
                        onlineRole = resolvedOnlineRole,
                        gameState = gameOverState,
                        gameOverReason = reasonText,
                        feedbackMessage = when {
                            isSpectator -> "La partie s'est terminée par abandon."
                            isLocalWinner -> "Partie remportée par abandon de l'adversaire !"
                            else -> "Vous avez déclaré forfait."
                        },
                        canDeclareAbandonment = false,
                        showForfeitConfirmation = false,
                        isOpponentDisconnected = false
                    )
                }
                if (!isSpectator) {
                    updateRematchCountdownState(session, currentUid)
                }
            }
            OnlineGameStatus.FINISHED -> {
                stopOnlineInactivityMonitoring()

                if (session.abandonedBy?.startsWith("LEFT:") == true && session.abandonedBy != "LEFT:$currentUid") {
                    handleOpponentLeftDuringRematch()
                    return
                }

                val isDrawEnd = session.winner == null && session.gameState.winner == null
                val finalGameState = if (session.gameState.phase == Phase.GAME_OVER) {
                    session.gameState
                } else {
                    session.gameState.copy(
                        phase = Phase.GAME_OVER,
                        winner = session.winner
                    )
                }

                if (finalGameState.winner != null && prevGameState.winner == null) {
                    checkPlayEndGameAudio(finalGameState)
                    checkRecordGameOverResult(finalGameState)
                } else if (isDrawEnd && prevGameState.phase != Phase.GAME_OVER) {
                    checkRecordGameOverResult(finalGameState, "Match nul — Les deux joueurs ont accepté.")
                }
                stopTurnTimer()
                _uiState.update {
                    it.copy(
                        onlineGameSession = session,
                        onlineRole = resolvedOnlineRole,
                        gameState = finalGameState,
                        gameOverReason = if (isDrawEnd) "Match nul — Les deux joueurs ont accepté." else it.gameOverReason,
                        feedbackMessage = if (isDrawEnd) "Match nul — Les deux joueurs ont accepté." else it.feedbackMessage,
                        canDeclareAbandonment = false,
                        isOpponentDisconnected = false
                    )
                }
                if (!isSpectator) {
                    updateRematchCountdownState(session, currentUid)
                }
            }
        }
    }

    private fun updateRematchCountdownState(session: OnlineGameSession, currentUid: String?) {
        if (_uiState.value.isSpectator) return
        val myUid = currentUid ?: return
        val oppUid = session.getOpponentUid(myUid)
        val onlyMeWaiting = session.rematchRequestedBy.contains(myUid) &&
                (oppUid == null || !session.rematchRequestedBy.contains(oppUid))
        if (onlyMeWaiting) {
            startRematchTimeoutCountdown(session.code)
        } else {
            stopRematchTimeoutCountdown()
        }
    }

    private fun startRematchTimeoutCountdown(code: String) {
        if (rematchTimeoutJob?.isActive == true) return
        val totalSeconds = rematchTimeoutSeconds.coerceAtLeast(1)
        _uiState.update {
            it.copy(
                rematchTimeoutRemainingSeconds = totalSeconds,
                rematchStatusMessage = null
            )
        }
        rematchTimeoutJob = viewModelScope.launch {
            for (remaining in (totalSeconds - 1) downTo 1) {
                delay(1_000L)
                if (!_uiState.value.isWaitingForRematch) {
                    _uiState.update { it.copy(rematchTimeoutRemainingSeconds = null) }
                    return@launch
                }
                _uiState.update { it.copy(rematchTimeoutRemainingSeconds = remaining) }
            }
            delay(1_000L)
            if (_uiState.value.isWaitingForRematch) {
                runCatching { onlineRepository?.cancelRematchRequest(code) }
                _uiState.update {
                    it.copy(
                        rematchTimeoutRemainingSeconds = null,
                        rematchStatusMessage = "Délai d'attente dépassé (${totalSeconds} s). Demande de revanche annulée.",
                        feedbackMessage = "Délai d'attente dépassé pour la revanche."
                    )
                }
            }
        }
    }

    private fun stopRematchTimeoutCountdown() {
        rematchTimeoutJob?.cancel()
        rematchTimeoutJob = null
        if (_uiState.value.rematchTimeoutRemainingSeconds != null) {
            _uiState.update { it.copy(rematchTimeoutRemainingSeconds = null) }
        }
    }

    private fun handleOpponentLeftDuringRematch() {
        val wasSpectator = _uiState.value.isSpectator
        stopOnlineInactivityMonitoring()
        stopRematchTimeoutCountdown()
        onlineSessionJob?.cancel()
        onlineSessionJob = null
        chatMessagesJob?.cancel()
        chatMessagesJob = null
        val msg = if (wasSpectator) "Un joueur a quitté le salon" else "L'adversaire a quitté le salon"
        _uiState.update {
            it.copy(
                onlineGameSession = null,
                onlineRole = null,
                waitingForOpponentCode = null,
                gameMode = GameMode.TWO_PLAYERS,
                localPlayerRole = null,
                canDeclareAbandonment = false,
                isOpponentDisconnected = false,
                rematchTimeoutRemainingSeconds = null,
                rematchStatusMessage = msg,
                onlineErrorMessage = msg,
                showModeSelectionDialog = true,
                feedbackMessage = msg
            )
        }
    }

    /**
     * Demande ou accepte une revanche dans le même salon en ligne après la fin d'une partie.
     * Un spectateur ne peut pas demander ni accepter de revanche.
     */
    fun requestOnlineRematch() {
        audio?.playClick()
        if (_uiState.value.isSpectator) return
        val session = _uiState.value.onlineGameSession ?: return
        val repo = onlineRepository ?: return
        val myUid = _uiState.value.localPlayerUid ?: repo.currentUserId

        if (session.abandonedBy?.startsWith("LEFT:") == true && session.abandonedBy != "LEFT:$myUid") {
            handleOpponentLeftDuringRematch()
            return
        }

        _uiState.update {
            it.copy(
                rematchStatusMessage = null,
                onlineErrorMessage = null
            )
        }

        viewModelScope.launch {
            val result = repo.requestRematch(session.code)
            result.onFailure { error ->
                val msg = error.message ?: "Impossible de demander la revanche."
                if (msg.contains("quitté le salon", ignoreCase = true)) {
                    handleOpponentLeftDuringRematch()
                } else {
                    _uiState.update {
                        it.copy(
                            rematchStatusMessage = msg,
                            feedbackMessage = msg
                        )
                    }
                }
            }
        }
    }

    /**
     * Annule la demande de revanche en cours du joueur local.
     */
    fun cancelOnlineRematchRequest() {
        audio?.playClick()
        if (_uiState.value.isSpectator) return
        stopRematchTimeoutCountdown()
        val session = _uiState.value.onlineGameSession ?: return
        val repo = onlineRepository ?: return

        _uiState.update {
            it.copy(
                rematchTimeoutRemainingSeconds = null,
                rematchStatusMessage = "Demande de revanche annulée."
            )
        }

        viewModelScope.launch {
            runCatching { repo.cancelRematchRequest(session.code) }
        }
    }

    /**
     * Quitte le salon depuis l'écran de fin de partie en ligne et ramène au menu principal.
     * Pour un spectateur, n'écrit jamais dans le document du salon.
     */
    fun leaveOnlineSalonAfterGame() {
        audio?.playClick()
        val isSpectator = _uiState.value.isSpectator
        val session = _uiState.value.onlineGameSession
        val code = session?.code ?: _uiState.value.waitingForOpponentCode
        stopOnlineInactivityMonitoring()
        stopRematchTimeoutCountdown()
        onlineSessionJob?.cancel()
        onlineSessionJob = null
        chatMessagesJob?.cancel()
        chatMessagesJob = null
        preRematchMessageIds = emptySet()

        if (code != null && !isSpectator) {
            viewModelScope.launch {
                runCatching { onlineRepository?.leaveGame(code) }
            }
        }

        _uiState.update {
            it.copy(
                onlineGameSession = null,
                onlineRole = null,
                waitingForOpponentCode = null,
                gameMode = GameMode.TWO_PLAYERS,
                localPlayerRole = null,
                canDeclareAbandonment = false,
                isOpponentDisconnected = false,
                rematchTimeoutRemainingSeconds = null,
                rematchStatusMessage = null,
                showModeSelectionDialog = true,
                feedbackMessage = "Vous avez quitté le salon."
            )
        }
    }

    /**
     * Envoie l'état du jeu mis à jour à Firestore si la partie est en ligne.
     * Ne s'exécute jamais pour un spectateur.
     */
    private fun syncOnlineMoveIfNeeded(newGameState: GameState) {
        if (_uiState.value.gameMode != GameMode.ONLINE || _uiState.value.isSpectator) return
        val session = _uiState.value.onlineGameSession ?: return
        viewModelScope.launch {
            try {
                onlineRepository?.updateGameState(session.code, newGameState)
            } catch (e: Exception) {
                Log.w("WaliViewModel", "syncOnlineMove warning: ${e.message}")
                _uiState.update { it.copy(feedbackMessage = "Erreur de synchronisation réseau : ${e.message}") }
            }
        }
    }

    /**
     * Déclare la victoire du joueur local suite à l'inactivité prolongée (abandon) de l'adversaire.
     * Ne s'exécute jamais pour un spectateur.
     */
    fun declareOpponentAbandoned() {
        audio?.playClick()
        if (_uiState.value.isSpectator) return
        val session = _uiState.value.onlineGameSession ?: return
        val myRole = _uiState.value.localPlayerRole ?: return
        viewModelScope.launch {
            try {
                onlineRepository?.declareAbandonment(session.code, myRole)
            } catch (e: Exception) {
                Log.w("WaliViewModel", "declareAbandonment warning: ${e.message}")
            }
        }
    }

    /**
     * Reprend une partie en ligne en cours détectée au démarrage.
     */
    fun resumePendingOnlineGame() {
        audio?.playClick()
        val session = _uiState.value.pendingResumeOnlineGame ?: return
        val currentUid = _uiState.value.localPlayerUid ?: onlineRepository?.currentUserId
        val myRole = if (session.creatorUid == currentUid) {
            session.creatorPlayer
        } else {
            session.opponentPlayer ?: session.creatorPlayer.opponent
        }
        val resolvedOnlineRole = if (session.creatorUid == currentUid) {
            com.example.wali.online.OnlineRole.CREATOR
        } else {
            com.example.wali.online.OnlineRole.OPPONENT
        }
        _uiState.update {
            it.copy(
                showResumeOnlineDialog = false,
                pendingResumeOnlineGame = null,
                gameMode = GameMode.ONLINE,
                onlineRole = resolvedOnlineRole,
                onlineGameSession = session,
                gameState = session.gameState,
                localPlayerUid = currentUid,
                localPlayerRole = myRole,
                feedbackMessage = "Partie en ligne reprise !"
            )
        }
        observeOnlineSession(session.code)
        onTurnTransition()
    }

    /**
     * Ferme la boîte de dialogue de reprise de partie en ligne sans la rejoindre.
     */
    fun dismissResumeOnlineDialog() {
        audio?.playClick()
        _uiState.update {
            it.copy(
                showResumeOnlineDialog = false,
                pendingResumeOnlineGame = null
            )
        }
    }

    /**
     * Efface le message d'erreur du mode en ligne.
     */
    fun dismissOnlineError() {
        _uiState.update { it.copy(onlineErrorMessage = null) }
    }

    /**
     * Quitte la partie en ligne actuelle et la déclare abandonnée si elle était en cours (pour un joueur).
     * Pour un spectateur, quitte proprement sans écrire dans Firestore.
     */
    fun leaveOnlineGame() {
        audio?.playClick()
        val isSpectator = _uiState.value.isSpectator
        val session = _uiState.value.onlineGameSession
        val myRole = _uiState.value.localPlayerRole
        if (!isSpectator && session != null && session.status == OnlineGameStatus.ONGOING && myRole != null) {
            viewModelScope.launch {
                runCatching {
                    onlineRepository?.declareAbandonment(session.code, myRole.opponent)
                }
            }
        } else if (!isSpectator && session != null && (session.status == OnlineGameStatus.FINISHED || session.status == OnlineGameStatus.ABANDONED)) {
            viewModelScope.launch {
                runCatching {
                    onlineRepository?.leaveGame(session.code)
                }
            }
        }
        stopOnlineInactivityMonitoring()
        stopRematchTimeoutCountdown()
        onlineSessionJob?.cancel()
        onlineSessionJob = null
        chatMessagesJob?.cancel()
        chatMessagesJob = null
        preRematchMessageIds = emptySet()
        _uiState.update {
            it.copy(
                onlineGameSession = null,
                onlineRole = null,
                waitingForOpponentCode = null,
                gameMode = GameMode.TWO_PLAYERS,
                localPlayerRole = null,
                canDeclareAbandonment = false,
                isOpponentDisconnected = false,
                rematchTimeoutRemainingSeconds = null,
                rematchStatusMessage = null,
                feedbackMessage = null
            )
        }
    }

    /**
     * Démarre la surveillance périodique de l'activité du joueur adverse en mode en ligne.
     * Inactif pour un spectateur.
     */
    fun startOnlineInactivityMonitoring() {
        onlineInactivityJob?.cancel()
        if (_uiState.value.isSpectator) return
        onlineInactivityJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                checkOnlineInactivity()
            }
        }
    }

    /**
     * Arrête la surveillance périodique de l'activité du joueur adverse.
     */
    fun stopOnlineInactivityMonitoring() {
        onlineInactivityJob?.cancel()
        onlineInactivityJob = null
    }

    /**
     * Vérifie l'inactivité de l'adversaire distant à partir de lastMoveAt.
     * - Après un court délai (20s) d'inactivité pendant son tour : affiche l'indicateur discret "Connexion instable / Adversaire déconnecté…".
     * - Après 2 minutes (120s) d'inactivité pendant son tour : propose le bouton "Déclarer l'adversaire abandonnataire".
     * - Un spectateur ne peut jamais déclarer d'abandon (Piège 3).
     */
    fun checkOnlineInactivity(currentTimeMs: Long = System.currentTimeMillis()) {
        val current = _uiState.value
        if (current.gameMode != GameMode.ONLINE) return
        if (current.isSpectator) {
            if (current.isOpponentDisconnected || current.canDeclareAbandonment) {
                _uiState.update {
                    it.copy(
                        isOpponentDisconnected = false,
                        canDeclareAbandonment = false
                    )
                }
            }
            return
        }
        val session = current.onlineGameSession ?: return

        if (session.status != OnlineGameStatus.ONGOING || current.gameState.phase == Phase.GAME_OVER) {
            if (current.isOpponentDisconnected || current.canDeclareAbandonment) {
                _uiState.update {
                    it.copy(
                        isOpponentDisconnected = false,
                        canDeclareAbandonment = false
                    )
                }
            }
            return
        }

        val lastMoveAt = session.lastMoveAt
        if (lastMoveAt <= 0L) return

        val elapsedMs = currentTimeMs - lastMoveAt
        val isOpponentTurn = current.isOpponentTurnOnline

        // Indicateur discret de connexion instable après 20s d'inactivité pendant le tour de l'adversaire
        val isDisconnected = elapsedMs >= 20_000L && isOpponentTurn
        // Bouton d'abandon après 2 minutes (120s) strictes d'inactivité pendant le tour de l'adversaire
        val canAbandon = elapsedMs >= 120_000L && isOpponentTurn

        if (isDisconnected != current.isOpponentDisconnected || canAbandon != current.canDeclareAbandonment) {
            _uiState.update {
                it.copy(
                    isOpponentDisconnected = isDisconnected,
                    canDeclareAbandonment = canAbandon
                )
            }
        }
    }

    /**
     * Vérifie auprès du serveur Firestore si une partie multijoueur en cours (ONGOING) existe
     * pour le joueur courant et propose une boîte de dialogue pour la reprendre.
     */
    fun checkForOngoingOnlineGame() {
        val repo = onlineRepository ?: return
        viewModelScope.launch {
            try {
                val pending = repo.checkExistingOngoingGame()
                if (pending != null && pending.status == OnlineGameStatus.ONGOING) {
                    _uiState.update {
                        it.copy(
                            pendingResumeOnlineGame = pending,
                            showResumeOnlineDialog = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("WaliViewModel", "checkForOngoingOnlineGame warning: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTurnTimer()
        durationJob?.cancel()
        stopOnlineInactivityMonitoring()
        stopRematchTimeoutCountdown()
        aiTurnJob?.cancel()
        onlineSessionJob?.cancel()
        chatMessagesJob?.cancel()
        audio?.release()
    }

    companion object {
        /**
         * Fournit une factory pour instancier le ViewModel avec le stockage, les préférences, le tableau des scores, l'audio hors-ligne et les repositories Firestore en ligne.
         */
        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val storage = DataStoreWaliGameStorage(appContext)
                val prefsRepo = DataStoreWaliPreferencesRepository(appContext)
                val scoreboardRepo = DataStoreWaliScoreboardRepository(appContext)
                val audioManager = WaliAudioManager(appContext)
                val onlineRepo = FirestoreWaliOnlineRepository(
                    firestore = com.example.wali.online.WaliFirebaseProvider.getFirestore(appContext)
                )
                val chatRepo = FirestoreWaliChatRepository(
                    firestore = com.example.wali.online.WaliFirebaseProvider.getFirestore(appContext)
                )
                return WaliViewModel(
                    storage = storage,
                    preferencesRepository = prefsRepo,
                    scoreboardRepository = scoreboardRepo,
                    audio = audioManager,
                    onlineRepository = onlineRepo,
                    chatRepository = chatRepo
                ) as T
            }
        }
    }
}
