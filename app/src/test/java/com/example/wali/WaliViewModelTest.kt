package com.example.wali

import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.storage.InMemoryWaliGameStorage
import com.example.wali.storage.SavedGame
import com.example.wali.ui.WaliViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WaliViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: WaliViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = WaliViewModel()
        viewModel.stopTurnTimer()
    }

    @After
    fun tearDown() {
        viewModel.stopTurnTimer()
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isPlacementPhase_withSticksStarting() {
        val state = viewModel.uiState.value
        assertEquals(Phase.PLACEMENT, state.gameState.phase)
        assertEquals(Player.STICKS, state.gameState.currentPlayer)
        assertEquals(12, state.gameState.sticksRemaining)
        assertEquals(12, state.gameState.stonesRemaining)
        assertNull(state.selectedPosition)
        assertTrue(state.highlightedMoves.isEmpty())
        assertTrue(state.capturablePositions.isEmpty())
        assertNull(state.feedbackMessage)
        assertFalse(state.showResetConfirmation)
    }

    @Test
    fun placement_validPlacement_updatesBoardAndSwitchesPlayer() {
        val target = Position(0, 0)
        viewModel.onCellClicked(target)

        val state = viewModel.uiState.value
        assertEquals(Player.STICKS, state.gameState.board[target])
        assertEquals(Player.STONES, state.gameState.currentPlayer)
        assertEquals(11, state.gameState.sticksRemaining)
        assertNull(state.feedbackMessage)
    }

    @Test
    fun placement_occupiedCell_showsFeedback() {
        val target = Position(0, 0)
        viewModel.onCellClicked(target) // STICKS at (0,0)
        viewModel.onCellClicked(target) // STONES clicks (0,0)

        val state = viewModel.uiState.value
        assertNotNull(state.feedbackMessage)
        assertTrue(state.feedbackMessage!!.contains("occupée"))
    }

    @Test
    fun placement_lineOfThree_isForbidden() {
        viewModel.onCellClicked(Position(0, 0)) // STICKS (0,0)
        viewModel.onCellClicked(Position(1, 0)) // STONES (1,0)
        viewModel.onCellClicked(Position(0, 1)) // STICKS (0,1)
        viewModel.onCellClicked(Position(1, 1)) // STONES (1,1)

        // STICKS tries (0,2) -> forms 3-in-a-row
        viewModel.onCellClicked(Position(0, 2))

        val state = viewModel.uiState.value
        assertNull(state.gameState.board[Position(0, 2)]) // Not placed
        assertNotNull(state.feedbackMessage)
        assertTrue(state.feedbackMessage!!.contains("interdit"))
        assertEquals(Player.STICKS, state.gameState.currentPlayer)
    }

    @Test
    fun resetDialog_openConfirmDismiss() {
        viewModel.onNewGameRequested()
        assertTrue(viewModel.uiState.value.showResetConfirmation)

        viewModel.dismissNewGameDialog()
        assertFalse(viewModel.uiState.value.showResetConfirmation)

        viewModel.onCellClicked(Position(0, 0))
        viewModel.onNewGameRequested()
        viewModel.confirmNewGame()

        val resetState = viewModel.uiState.value
        assertFalse(resetState.showResetConfirmation)
        assertEquals(12, resetState.gameState.sticksRemaining)
        // Vérifie qu'aucun pion n'est posé sur le plateau
        assertTrue(resetState.gameState.board.values.all { it == null })
    }

    @Test
    fun storage_autoSavesAfterPlacement() = runTest {
        val storage = InMemoryWaliGameStorage()
        val vm = WaliViewModel(storage = storage)
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        assertFalse(storage.hasActiveSavedGame())

        // Pose d'un pion par BÂTONNETS en (0,0)
        vm.onCellClicked(Position(0, 0))
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        assertTrue(storage.hasActiveSavedGame())
        val saved = storage.loadSavedGame()
        assertNotNull(saved)
        assertEquals(Player.STICKS, saved!!.gameState.board[Position(0, 0)])
        assertEquals(Player.STONES, saved.gameState.currentPlayer)
        assertEquals(11, saved.gameState.sticksRemaining)
        vm.stopTurnTimer()
    }

    @Test
    fun storage_startupWithActiveSavedGame_showsResumeDialog() = runTest {
        val storage = InMemoryWaliGameStorage()
        val inProgress = WaliEngine.placePiece(WaliEngine.initializeGame(), Position(2, 2))
        storage.saveGame(SavedGame(inProgress, GameMode.TWO_PLAYERS))

        val vm = WaliViewModel(storage = storage)
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        assertTrue(vm.uiState.value.showResumeDialog)
        assertFalse(vm.uiState.value.showModeSelectionDialog)
        vm.stopTurnTimer()
    }

    @Test
    fun storage_resumeLoadsSavedGameProperly() = runTest {
        val storage = InMemoryWaliGameStorage()
        val inProgress = WaliEngine.placePiece(WaliEngine.initializeGame(), Position(3, 3))
        storage.saveGame(SavedGame(inProgress, GameMode.TWO_PLAYERS))

        val vm = WaliViewModel(storage = storage)
        vm.stopTurnTimer()
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.showResumeDialog)

        vm.resumeSavedGame()
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        assertFalse(vm.uiState.value.showResumeDialog)
        assertEquals(Player.STICKS, vm.uiState.value.gameState.board[Position(3, 3)])
        assertEquals(Player.STONES, vm.uiState.value.gameState.currentPlayer)
        vm.stopTurnTimer()
    }

    @Test
    fun storage_declineResume_clearsStorageAndOpensModeSelection() = runTest {
        val storage = InMemoryWaliGameStorage()
        val inProgress = WaliEngine.placePiece(WaliEngine.initializeGame(), Position(1, 1))
        storage.saveGame(SavedGame(inProgress, GameMode.TWO_PLAYERS))

        val vm = WaliViewModel(storage = storage)
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        vm.declineResumeAndStartNewGame()
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        assertFalse(vm.uiState.value.showResumeDialog)
        assertTrue(vm.uiState.value.showModeSelectionDialog)
        assertFalse(storage.hasActiveSavedGame())
        assertNull(storage.loadSavedGame())
        vm.stopTurnTimer()
    }

    @Test
    fun storage_newGameConfirmation_clearsStorage() = runTest {
        val storage = InMemoryWaliGameStorage()
        val vm = WaliViewModel(storage = storage)
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        vm.onCellClicked(Position(0, 0))
        vm.stopTurnTimer()
        testScheduler.runCurrent()
        assertTrue(storage.hasActiveSavedGame())

        vm.onNewGameRequested()
        vm.confirmNewGame()
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        assertFalse(storage.hasActiveSavedGame())
        assertNull(storage.loadSavedGame())
        vm.stopTurnTimer()
    }

    @Test
    fun gameOver_continueResetsBoardAndOpensModeSelection() = runTest {
        val storage = InMemoryWaliGameStorage()
        val vm = WaliViewModel(storage = storage)
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        vm.onCellClicked(Position(0, 0))
        vm.stopTurnTimer()
        testScheduler.runCurrent()
        assertTrue(storage.hasActiveSavedGame())

        vm.onGameOverContinue()
        vm.stopTurnTimer()
        testScheduler.runCurrent()

        assertFalse(storage.hasActiveSavedGame())
        assertTrue(vm.uiState.value.showModeSelectionDialog)
        assertEquals(0, vm.uiState.value.gameState.board.values.count { it != null })
        assertEquals(Phase.PLACEMENT, vm.uiState.value.gameState.phase)
        vm.stopTurnTimer()
    }

    @Test
    fun timer_initialState_isConfiguredAndCanTick() {
        val state = viewModel.uiState.value
        assertTrue(state.isTimerEnabled)
        assertEquals(30, state.timerDurationSeconds)
        assertEquals(30, state.timerRemainingSeconds)
        assertFalse(state.isTimerPaused)

        viewModel.tickTimer()
        assertEquals(29, viewModel.uiState.value.timerRemainingSeconds)
    }

    @Test
    fun timer_isPaused_whenAppBackgroundedOrDialogsOpen() {
        viewModel.onAppBackgrounded()
        assertTrue(viewModel.uiState.value.isTimerPaused)

        viewModel.tickTimer()
        assertEquals(30, viewModel.uiState.value.timerRemainingSeconds)

        viewModel.onAppForegrounded()
        assertFalse(viewModel.uiState.value.isTimerPaused)

        viewModel.tickTimer()
        assertEquals(29, viewModel.uiState.value.timerRemainingSeconds)
    }

    @Test
    fun timer_expired_triggersRandomLegalMoveInPlacement() {
        assertEquals(0, viewModel.uiState.value.gameState.board.values.count { it != null })
        assertEquals(Player.STICKS, viewModel.uiState.value.gameState.currentPlayer)

        viewModel.handleTimerExpired()

        // Un pion a été posé automatiquement
        assertEquals(1, viewModel.uiState.value.gameState.board.values.count { it != null })
        assertEquals(Player.STONES, viewModel.uiState.value.gameState.currentPlayer)
        assertTrue(viewModel.uiState.value.feedbackMessage?.contains("Temps écoulé") == true)
    }

    @Test
    fun timer_disabled_doesNotTickOrTriggerAutomaticMoves() {
        viewModel.onTimerEnabledChanged(false)
        assertFalse(viewModel.uiState.value.isTimerEnabled)

        viewModel.tickTimer()
        assertEquals(30, viewModel.uiState.value.timerRemainingSeconds)

        viewModel.handleTimerExpired()
        assertEquals(0, viewModel.uiState.value.gameState.board.values.count { it != null })
    }

    @Test
    fun pauseMenu_opensResumesRestartsAndReturnsToMainMenu() {
        assertFalse(viewModel.uiState.value.showPauseMenu)
        assertFalse(viewModel.uiState.value.isTimerPaused)

        // 1. Ouvrir le menu de pause
        viewModel.openPauseMenu()
        assertTrue(viewModel.uiState.value.showPauseMenu)
        assertTrue(viewModel.uiState.value.isTimerPaused)

        // Le minuteur ne doit pas décrémenter pendant la pause
        viewModel.tickTimer()
        assertEquals(30, viewModel.uiState.value.timerRemainingSeconds)

        // 2. Reprendre (Resume)
        viewModel.resumeGame()
        assertFalse(viewModel.uiState.value.showPauseMenu)
        assertFalse(viewModel.uiState.value.isTimerPaused)

        // 3. Recommencer la partie actuelle (Restart)
        viewModel.openPauseMenu()
        viewModel.restartCurrentGame()
        assertFalse(viewModel.uiState.value.showPauseMenu)
        assertEquals(Phase.PLACEMENT, viewModel.uiState.value.gameState.phase)
        assertEquals(12, viewModel.uiState.value.gameState.sticksRemaining)
        assertEquals(12, viewModel.uiState.value.gameState.stonesRemaining)

        // 4. Revenir au menu principal (Return to Main Menu)
        viewModel.openPauseMenu()
        viewModel.returnToMainMenu()
        assertFalse(viewModel.uiState.value.showPauseMenu)
        assertTrue(viewModel.uiState.value.showModeSelectionDialog)
    }

    @Test
    fun forfeit_confirmationFlow_cancelsWithoutEndingGame() {
        assertTrue(viewModel.uiState.value.canDeclareForfeit)
        assertFalse(viewModel.uiState.value.showForfeitConfirmation)

        // 1. Demande d'abandon
        viewModel.requestForfeit()
        assertTrue(viewModel.uiState.value.showForfeitConfirmation)
        assertTrue(viewModel.uiState.value.isTimerPaused)
        // La partie n'est pas encore terminée
        assertEquals(Phase.PLACEMENT, viewModel.uiState.value.gameState.phase)
        assertNull(viewModel.uiState.value.gameState.winner)

        // 2. Annulation
        viewModel.dismissForfeitConfirmation()
        assertFalse(viewModel.uiState.value.showForfeitConfirmation)
        assertEquals(Phase.PLACEMENT, viewModel.uiState.value.gameState.phase)
        assertNull(viewModel.uiState.value.gameState.winner)
    }

    @Test
    fun forfeit_twoPlayersLocal_awardsWinToOpponent() {
        // En début de partie, c'est le tour de STICKS
        assertEquals(Player.STICKS, viewModel.uiState.value.gameState.currentPlayer)

        viewModel.requestForfeit()
        viewModel.confirmForfeit()

        val state = viewModel.uiState.value
        assertFalse(state.showForfeitConfirmation)
        assertEquals(Phase.GAME_OVER, state.gameState.phase)
        assertEquals(Player.STONES, state.gameState.winner)
        assertEquals("Abandon", state.gameOverReason)
        assertFalse(state.canDeclareForfeit)
    }

    @Test
    fun forfeit_vsComputer_awardsWinToAi() {
        viewModel.startNewGame(GameMode.VS_COMPUTER, Player.STICKS, com.example.wali.ai.AiDifficulty.EASY)
        viewModel.stopTurnTimer()

        assertTrue(viewModel.uiState.value.canDeclareForfeit)

        viewModel.declareForfeit()

        val state = viewModel.uiState.value
        assertEquals(Phase.GAME_OVER, state.gameState.phase)
        assertEquals(Player.STONES, state.gameState.winner)
        assertEquals("Abandon", state.gameOverReason)
        assertFalse(state.canDeclareForfeit)
    }

    @Test
    fun onlineGame_creatorVariantsApplied_joinerIgnoresLocalPreferences() = runTest {
        val sharedRepo = com.example.wali.online.FakeWaliOnlineRepository("creator_uid")
        val creatorPrefs = com.example.wali.storage.InMemoryWaliPreferencesRepository(
            com.example.wali.storage.WaliUserPreferences(
                isMandatoryAlignmentEnabled = true,
                isDefeatAtTwoPiecesEnabled = false
            )
        )
        val creatorVm = WaliViewModel(
            preferencesRepository = creatorPrefs,
            onlineRepository = sharedRepo
        )
        creatorVm.stopTurnTimer()
        testScheduler.runCurrent()

        // Le créateur crée un salon : ses variantes (mandatoryAlignment=true, defeatAtTwoPieces=false) sont inscrites
        creatorVm.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        creatorVm.stopTurnTimer()
        testScheduler.runCurrent()

        val code = creatorVm.uiState.value.waitingForOpponentCode!!
        assertTrue(creatorVm.uiState.value.gameState.ruleOptions.mandatoryAlignment)
        assertFalse(creatorVm.uiState.value.gameState.ruleOptions.defeatAtTwoPieces)

        // Le second joueur a des préférences locales opposées (mandatoryAlignment=false, defeatAtTwoPieces=true)
        val joinerPrefs = com.example.wali.storage.InMemoryWaliPreferencesRepository(
            com.example.wali.storage.WaliUserPreferences(
                isMandatoryAlignmentEnabled = false,
                isDefeatAtTwoPiecesEnabled = true
            )
        )
        val joinerVm = WaliViewModel(
            preferencesRepository = joinerPrefs,
            onlineRepository = sharedRepo
        )
        joinerVm.stopTurnTimer()
        testScheduler.runCurrent()

        // Le second joueur rejoint le salon et DOIT appliquer les variantes du créateur
        joinerVm.startJoinOnlineGame(code)
        joinerVm.stopTurnTimer()
        testScheduler.runCurrent()

        assertTrue(joinerVm.uiState.value.gameState.ruleOptions.mandatoryAlignment)
        assertFalse(joinerVm.uiState.value.gameState.ruleOptions.defeatAtTwoPieces)

        creatorVm.stopTurnTimer()
        creatorVm.stopOnlineInactivityMonitoring()
        joinerVm.stopTurnTimer()
        joinerVm.stopOnlineInactivityMonitoring()
    }
}
