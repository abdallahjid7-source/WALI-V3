package com.example.wali.online

import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.storage.InMemoryWaliGameStorage
import com.example.wali.ui.WaliViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

/**
 * Faux dépôt en mémoire pour tester rigoureusement la logique multijoueur en ligne
 * sans dépendance externe à Cloud Firestore.
 */
class FakeWaliOnlineRepository(
    override val currentUserId: String = "user_player_1"
) : WaliOnlineRepository {

    val games = mutableMapOf<String, MutableStateFlow<OnlineGameSession?>>()

    override suspend fun ensureAuthenticated(): String = currentUserId

    override suspend fun createGame(
        code: String,
        isPublic: Boolean,
        creatorSide: Player,
        initialGameState: GameState
    ): Result<OnlineGameSession> {
        val now = System.currentTimeMillis()
        val session = OnlineGameSession(
            code = code,
            isPublic = isPublic,
            status = OnlineGameStatus.WAITING,
            creatorUid = currentUserId,
            creatorPlayer = creatorSide,
            opponentUid = null,
            opponentPlayer = null,
            players = mapOf(currentUserId to creatorSide.name),
            gameState = initialGameState,
            lastMoveAt = now,
            createdAt = now
        )
        games[code] = MutableStateFlow(session)
        return Result.success(session)
    }

    override suspend fun joinGame(code: String): Result<OnlineGameSession> {
        val flow = games[code] ?: return Result.failure(IllegalArgumentException("Partie introuvable"))
        val current = flow.value ?: return Result.failure(IllegalStateException("Partie nulle"))
        val opponentPlayer = current.creatorPlayer.opponent
        val now = System.currentTimeMillis()
        val updated = current.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = currentUserId,
            opponentPlayer = opponentPlayer,
            players = current.players + (currentUserId to opponentPlayer.name),
            lastMoveAt = now
        )
        flow.value = updated
        return Result.success(updated)
    }

    override fun observeGame(code: String): Flow<OnlineGameSession?> {
        return games[code]?.asStateFlow() ?: flowOf(null)
    }

    override fun observePublicWaitingGames(): Flow<List<PublicGameItem>> {
        return flowOf(
            games.values.mapNotNull { it.value }
                .filter { it.status == OnlineGameStatus.WAITING && it.isPublic }
                .map { PublicGameItem(it.code, it.creatorPlayer, it.createdAt) }
        )
    }

    override suspend fun updateGameState(code: String, newGameState: GameState): Result<Unit> {
        val flow = games[code] ?: return Result.failure(IllegalArgumentException("Partie introuvable"))
        val current = flow.value ?: return Result.failure(IllegalStateException("Partie nulle"))
        val newStatus = if (newGameState.winner != null) OnlineGameStatus.FINISHED else current.status
        val updated = current.copy(
            gameState = newGameState,
            status = newStatus,
            winner = newGameState.winner,
            lastMoveAt = System.currentTimeMillis()
        )
        flow.value = updated
        return Result.success(Unit)
    }

    override suspend fun declareAbandonment(code: String, winnerPlayer: Player): Result<Unit> {
        val flow = games[code] ?: return Result.failure(IllegalArgumentException("Partie introuvable"))
        val current = flow.value ?: return Result.failure(IllegalStateException("Partie nulle"))
        val updated = current.copy(
            status = OnlineGameStatus.ABANDONED,
            winner = winnerPlayer,
            abandonedBy = currentUserId,
            lastMoveAt = System.currentTimeMillis()
        )
        flow.value = updated
        return Result.success(Unit)
    }

    override suspend fun checkExistingOngoingGame(): OnlineGameSession? {
        return games.values.mapNotNull { it.value }
            .firstOrNull { it.status == OnlineGameStatus.ONGOING && it.isParticipant(currentUserId) }
    }

    override suspend fun leaveGame(code: String): Result<Unit> {
        games.remove(code)
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WaliOnlineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeWaliOnlineRepository
    private lateinit var viewModel: WaliViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeWaliOnlineRepository("local_user_42")
        viewModel = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            onlineRepository = fakeRepo
        )
        viewModel.stopTurnTimer()
    }

    @After
    fun tearDown() {
        viewModel.stopTurnTimer()
        viewModel.stopOnlineInactivityMonitoring()
        Dispatchers.resetMain()
    }

    private fun runOnlineTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            viewModel.stopTurnTimer()
            viewModel.stopOnlineInactivityMonitoring()
            viewModel.cancelOnlineWaiting()
            testScheduler.runCurrent()
        }
    }

    @Test
    fun codeGenerator_generatesValidSixCharUniqueCode() {
        val code1 = WaliOnlineCodeGenerator.generateCode()
        val code2 = WaliOnlineCodeGenerator.generateCode()

        assertEquals(6, code1.length)
        assertEquals(6, code2.length)
        assertTrue(WaliOnlineCodeGenerator.isValidCode(code1))
        assertTrue(WaliOnlineCodeGenerator.isValidCode(code2))

        // Ne contient aucun caractère ambigu (0, 1, O, I)
        assertFalse(code1.contains('0'))
        assertFalse(code1.contains('1'))
        assertFalse(code1.contains('O'))
        assertFalse(code1.contains('I'))

        assertEquals("ABC123", WaliOnlineCodeGenerator.normalizeCode(" abc123 "))
    }

    @Test
    fun onlineCreation_initializesWaitingSession() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(GameMode.ONLINE, state.gameMode)
        assertEquals(Player.STICKS, state.localPlayerRole)
        assertNotNull(state.waitingForOpponentCode)
        assertNotNull(state.onlineGameSession)
        assertEquals(OnlineGameStatus.WAITING, state.onlineGameSession?.status)
    }

    @Test
    fun onlineOpponentTurn_blocksLocalClicks() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STONES)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!

        // Simule l'arrivée d'un adversaire (qui joue STICKS)
        val flow = fakeRepo.games[code]!!
        val ongoing = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STICKS
        )
        flow.value = ongoing
        testScheduler.runCurrent()

        // Le joueur local a choisi CAILLOUX (STONES). Au tour 1, c'est aux BÂTONNETS (l'adversaire) de jouer.
        // Les clics locaux doivent donc être ignorés.
        val clickPos = Position(0, 0)
        viewModel.onCellClicked(clickPos)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.gameState.board[clickPos])
        assertEquals("Tour de l'adversaire distant…", state.feedbackMessage)
    }

    @Test
    fun onlineLocalTurn_appliesMoveAndSyncs() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!

        // Simule l'arrivée d'un adversaire
        val flow = fakeRepo.games[code]!!
        val ongoing = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES
        )
        flow.value = ongoing
        testScheduler.runCurrent()

        // C'est au tour des BÂTONNETS (notre joueur local)
        val target = Position(0, 0)
        viewModel.onCellClicked(target)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(Player.STICKS, state.gameState.board[target])
        assertEquals(Player.STONES, state.gameState.currentPlayer)

        // Vérifie la synchronisation dans le faux repository
        val syncedState = fakeRepo.games[code]!!.value!!.gameState
        assertEquals(Player.STICKS, syncedState.board[target])
    }

    @Test
    fun onlineAbandonment_declaresForfeitAndAwardsVictory() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            lastMoveAt = System.currentTimeMillis() - 130_000L // 130 secondes d'inactivité
        )
        testScheduler.runCurrent()

        // Déclare l'abandon
        viewModel.declareOpponentAbandoned()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(Phase.GAME_OVER, state.gameState.phase)
        assertEquals(Player.STICKS, state.gameState.winner)
        assertTrue(state.feedbackMessage?.contains("abandon") == true)
        assertFalse(state.canDeclareAbandonment)
        assertFalse(state.isOpponentDisconnected)
    }

    @Test
    fun onlineInactivity_shortDelayShowsDiscreteIndicator() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val t0 = 1_000_000L

        // Adversaire a rejoint et c'est au tour de l'adversaire (CAILLOUX)
        val ongoing = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            gameState = WaliEngine.initializeGame().copy(currentPlayer = Player.STONES),
            lastMoveAt = t0
        )
        flow.value = ongoing
        testScheduler.runCurrent()

        // Après 25 secondes d'inactivité pendant le tour adverse (> 20s et < 120s)
        viewModel.checkOnlineInactivity(t0 + 25_000L)

        val state = viewModel.uiState.value
        assertTrue(state.isOpponentDisconnected)
        assertFalse(state.canDeclareAbandonment)
        // La partie n'est pas interrompue ni terminée
        assertEquals(Phase.PLACEMENT, state.gameState.phase)
    }

    @Test
    fun onlineInactivity_opponentReconnectsClearsIndicator() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val t0 = 1_000_000L

        // Adversaire (CAILLOUX) inactif
        val ongoing = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            gameState = WaliEngine.initializeGame().copy(currentPlayer = Player.STONES),
            lastMoveAt = t0
        )
        flow.value = ongoing
        testScheduler.runCurrent()

        viewModel.checkOnlineInactivity(t0 + 25_000L)
        assertTrue(viewModel.uiState.value.isOpponentDisconnected)

        // L'adversaire se reconnecte et joue son coup
        val updatedGame = ongoing.gameState.copy(currentPlayer = Player.STICKS)
        flow.value = ongoing.copy(
            gameState = updatedGame,
            lastMoveAt = t0 + 30_000L
        )
        testScheduler.runCurrent()

        // L'indicateur disparaît automatiquement dès la réception de la mise à jour Firestore
        val state = viewModel.uiState.value
        assertFalse(state.isOpponentDisconnected)
        assertFalse(state.canDeclareAbandonment)
    }

    @Test
    fun onlineInactivity_prolongedDelayOpponentTurnShowsAbandonButton() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val t0 = 1_000_000L

        // Tour de l'adversaire (CAILLOUX)
        val ongoing = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            gameState = WaliEngine.initializeGame().copy(currentPlayer = Player.STONES),
            lastMoveAt = t0
        )
        flow.value = ongoing
        testScheduler.runCurrent()

        // Avant 2 minutes (ex: 119s), le bouton ne doit PAS apparaître
        viewModel.checkOnlineInactivity(t0 + 119_000L)
        assertFalse(viewModel.uiState.value.canDeclareAbandonment)
        assertTrue(viewModel.uiState.value.isOpponentDisconnected)

        // À 2 minutes révolues (120s), le bouton d'abandon apparaît
        viewModel.checkOnlineInactivity(t0 + 120_000L)
        assertTrue(viewModel.uiState.value.canDeclareAbandonment)
        assertTrue(viewModel.uiState.value.isOpponentDisconnected)
    }

    @Test
    fun onlineInactivity_prolongedDelayLocalTurnDoesNotShowAbandonButton() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val t0 = 1_000_000L

        // C'est au tour du joueur LOCAL (BÂTONNETS)
        val ongoing = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            gameState = WaliEngine.initializeGame().copy(currentPlayer = Player.STICKS),
            lastMoveAt = t0
        )
        flow.value = ongoing
        testScheduler.runCurrent()

        // Même après 3 minutes d'inactivité pendant son PROPRE tour,
        // le bouton d'abandon ne doit JAMAIS apparaître pour le joueur local
        viewModel.checkOnlineInactivity(t0 + 180_000L)
        assertFalse(viewModel.uiState.value.canDeclareAbandonment)
        assertFalse(viewModel.uiState.value.isOpponentDisconnected)
    }

    @Test
    fun onlineResume_onForegroundProposesOngoingGame() = runOnlineTest {
        // Crée une partie ONGOING dans le faux dépôt
        val code = "TEST99"
        val now = System.currentTimeMillis()
        val ongoingSession = OnlineGameSession(
            code = code,
            isPublic = false,
            status = OnlineGameStatus.ONGOING,
            creatorUid = "local_user_42",
            creatorPlayer = Player.STICKS,
            opponentUid = "opponent_55",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_55" to "STONES"),
            gameState = WaliEngine.initializeGame(),
            lastMoveAt = now,
            createdAt = now
        )
        fakeRepo.games[code] = MutableStateFlow(ongoingSession)

        // L'app était au départ en mode local (TWO_PLAYERS)
        assertEquals(GameMode.TWO_PLAYERS, viewModel.uiState.value.gameMode)

        // L'app revient au premier plan
        viewModel.onAppForegrounded()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.showResumeOnlineDialog)
        assertEquals(code, state.pendingResumeOnlineGame?.code)

        // Reprise acceptée
        viewModel.resumePendingOnlineGame()
        testScheduler.runCurrent()

        val resumedState = viewModel.uiState.value
        assertFalse(resumedState.showResumeOnlineDialog)
        assertEquals(GameMode.ONLINE, resumedState.gameMode)
        assertEquals(code, resumedState.onlineGameSession?.code)
    }

    @Test
    fun onlineWaiting_cancelResetsToLocal() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = false, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.waitingForOpponentCode != null)

        viewModel.cancelOnlineWaiting()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(GameMode.TWO_PLAYERS, state.gameMode)
        assertNull(state.waitingForOpponentCode)
        assertNull(state.onlineGameSession)
    }

    @Test
    fun onlineGame_localPlayerForfeit_awardsWinToOpponent() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Opponent rejoint la partie
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "remote_uid",
            opponentPlayer = Player.STONES,
            lastMoveAt = System.currentTimeMillis()
        )
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.canDeclareForfeit)

        // Déclare forfait
        viewModel.declareForfeit()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(Phase.GAME_OVER, state.gameState.phase)
        assertEquals(Player.STONES, state.gameState.winner)
        assertEquals("Abandon", state.gameOverReason)
        assertEquals(OnlineGameStatus.ABANDONED, flow.value?.status)
        assertEquals(Player.STONES, flow.value?.winner)
    }
}
