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
    val presetSpectatorCodes = ArrayDeque<String>()

    private fun generateUniqueSpectatorCode(playerCode: String): String {
        for (attempt in 1..10) {
            val candidate = if (presetSpectatorCodes.isNotEmpty()) {
                presetSpectatorCodes.removeFirst()
            } else {
                WaliOnlineCodeGenerator.generateSpectatorCode()
            }
            val alreadyExists = games.values.any { it.value?.spectatorCode.equals(candidate, ignoreCase = true) }
            if (!alreadyExists && !candidate.equals(playerCode, ignoreCase = true)) {
                return candidate
            }
        }
        return WaliOnlineCodeGenerator.generateSpectatorCode()
    }

    override suspend fun ensureAuthenticated(): String = currentUserId

    override suspend fun createGame(
        code: String,
        isPublic: Boolean,
        creatorSide: Player,
        initialGameState: GameState
    ): Result<OnlineGameSession> {
        val now = System.currentTimeMillis()
        val spectatorCode = generateUniqueSpectatorCode(code)
        val session = OnlineGameSession(
            code = code,
            spectatorCode = spectatorCode,
            isPublic = isPublic,
            status = OnlineGameStatus.WAITING,
            creatorUid = currentUserId,
            creatorPlayer = creatorSide,
            opponentUid = null,
            opponentPlayer = null,
            players = mapOf(currentUserId to creatorSide.name),
            gameState = initialGameState,
            lastMoveAt = now,
            createdAt = now,
            rematchRequestedBy = emptyList(),
            gamesPlayed = 1,
            statsByUid = mapOf(currentUserId to 0),
            lastScoredGameIndex = 0
        )
        games[code] = MutableStateFlow(session)
        return Result.success(session)
    }

    override suspend fun joinGame(code: String): Result<OnlineGameSession> {
        val normalized = WaliOnlineCodeGenerator.normalizeCode(code)
        if (WaliOnlineCodeGenerator.isValidSpectatorCode(normalized) ||
            games.values.any { it.value?.spectatorCode.equals(normalized, ignoreCase = true) }
        ) {
            return Result.failure(
                IllegalArgumentException("Ce code est un code spectateur. Utilisez l'onglet Spectateurs.")
            )
        }
        val flow = games[normalized] ?: return Result.failure(IllegalArgumentException("Partie introuvable"))
        val current = flow.value ?: return Result.failure(IllegalStateException("Partie nulle"))
        val opponentPlayer = current.creatorPlayer.opponent
        val now = System.currentTimeMillis()
        val updatedStats = current.statsByUid.toMutableMap().apply {
            putIfAbsent(current.creatorUid, 0)
            putIfAbsent(currentUserId, 0)
        }
        val updated = current.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = currentUserId,
            opponentPlayer = opponentPlayer,
            players = current.players + (currentUserId to opponentPlayer.name),
            statsByUid = updatedStats,
            lastMoveAt = now
        )
        flow.value = updated
        return Result.success(updated)
    }

    override suspend fun joinAsSpectator(spectatorCode: String): Result<OnlineGameSession> {
        val normalized = WaliOnlineCodeGenerator.normalizeCode(spectatorCode)
        if (WaliOnlineCodeGenerator.isValidCode(normalized) || games.containsKey(normalized)) {
            return Result.failure(
                IllegalArgumentException("Ce code est un code joueur. Utilisez l'onglet Rejoindre.")
            )
        }
        if (!WaliOnlineCodeGenerator.isValidSpectatorCode(normalized)) {
            return Result.failure(
                IllegalArgumentException("Format de code spectateur invalide (7 caractères commençant par S).")
            )
        }
        val session = findGameBySpectatorCode(normalized)
            ?: return Result.failure(IllegalArgumentException("Aucun salon trouvé pour ce code spectateur."))
        return Result.success(session)
    }

    override suspend fun findGameBySpectatorCode(spectatorCode: String): OnlineGameSession? {
        val normalized = WaliOnlineCodeGenerator.normalizeCode(spectatorCode)
        val candidates = games.values
            .mapNotNull { it.value }
            .filter { it.spectatorCode.equals(normalized, ignoreCase = true) }
        return selectDeterministicSpectatorSession(candidates)
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

    /**
     * Calcule de façon idempotente la mise à jour de statsByUid et lastScoredGameIndex.
     * Invariant :
     * - lastScoredGameIndex == gamesPlayed  ->  la manche en cours a déjà été comptabilisée
     * - lastScoredGameIndex <  gamesPlayed  ->  la manche en cours n'a pas encore été comptabilisée
     */
    private fun pointsForWin(session: OnlineGameSession, winnerRole: Player): Int {
        return if (session.gameState.capturesMadeBy(winnerRole.opponent) == 0) 2 else 1
    }

    private fun computeScoredSalonState(
        session: OnlineGameSession,
        winnerRole: Player?
    ): Pair<Map<String, Int>, Int> {
        val creatorUid = session.creatorUid.takeIf { it.isNotBlank() }
        val opponentUid = session.opponentUid?.takeIf { it.isNotBlank() }

        // Garde salon incomplet (Point 4)
        if (creatorUid == null || opponentUid == null) {
            val safeStats = if (creatorUid != null) {
                session.statsByUid.filterKeys { it == creatorUid }
            } else {
                emptyMap()
            }
            return safeStats to session.lastScoredGameIndex
        }

        val allowedParticipants = setOf(creatorUid, opponentUid)
        val sanitizedStats = session.statsByUid
            .filterKeys { it in allowedParticipants }
            .toMutableMap()
            .apply {
                putIfAbsent(creatorUid, 0)
                putIfAbsent(opponentUid, 0)
            }

        if (session.lastScoredGameIndex >= session.gamesPlayed) {
            return sanitizedStats to session.lastScoredGameIndex
        }

        if (winnerRole != null) {
            val winnerUid = session.resolveParticipantUidForRole(winnerRole)
            if (winnerUid != null && winnerUid in allowedParticipants) {
                sanitizedStats[winnerUid] = (sanitizedStats[winnerUid] ?: 0) + pointsForWin(session, winnerRole)
            }
        }

        return sanitizedStats to session.gamesPlayed
    }

    override suspend fun updateGameState(code: String, newGameState: GameState): Result<Unit> {
        val flow = games[code] ?: return Result.failure(IllegalArgumentException("Partie introuvable"))
        val current = flow.value ?: return Result.failure(IllegalStateException("Partie nulle"))
        val isGameEnding = newGameState.winner != null || newGameState.phase == Phase.GAME_OVER

        if (!isGameEnding) {
            flow.value = current.copy(
                gameState = newGameState,
                lastMoveAt = System.currentTimeMillis()
            )
            return Result.success(Unit)
        }

        // Règle de course (Point 2) : le premier écrit gagne. Si status != ONGOING, on ne réécrit rien.
        if (current.status != OnlineGameStatus.ONGOING) {
            return Result.success(Unit)
        }

        val sessionWithFinalBoard = current.copy(gameState = newGameState)
        val (updatedStats, newLastScoredIndex) = computeScoredSalonState(sessionWithFinalBoard, newGameState.winner)
        val updated = current.copy(
            gameState = newGameState,
            status = OnlineGameStatus.FINISHED,
            winner = newGameState.winner,
            drawOfferedBy = null,
            statsByUid = updatedStats,
            lastScoredGameIndex = newLastScoredIndex,
            lastMoveAt = System.currentTimeMillis()
        )
        flow.value = updated
        return Result.success(Unit)
    }

    override suspend fun declareAbandonment(code: String, winnerPlayer: Player): Result<Unit> {
        return declareAbandonmentAsUser(code, winnerPlayer, currentUserId)
    }

    fun declareAbandonmentAsUser(code: String, winnerPlayer: Player, callerUid: String): Result<Unit> {
        val flow = games[code] ?: return Result.failure(IllegalArgumentException("Partie introuvable"))
        val current = flow.value ?: return Result.failure(IllegalStateException("Partie nulle"))

        // Règle de course (Point 2) : le premier écrit gagne. Si status != ONGOING, on abandonne sans réécrire ni lever d'erreur.
        if (current.status != OnlineGameStatus.ONGOING) {
            return Result.success(Unit)
        }

        // Vérification 1 : abandonedBy est l'UID du joueur perdant (winnerPlayer.opponent), ou null si non résoluble (jamais callerUid)
        val abandonedByUid: String? = current.resolveParticipantUidForRole(winnerPlayer.opponent)
        val (updatedStats, newLastScoredIndex) = computeScoredSalonState(current, winnerPlayer)

        val updated = current.copy(
            status = OnlineGameStatus.ABANDONED,
            winner = winnerPlayer,
            abandonedBy = abandonedByUid,
            drawOfferedBy = null,
            statsByUid = updatedStats,
            lastScoredGameIndex = newLastScoredIndex,
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
        return leaveGameAsUser(code, currentUserId)
    }

    fun leaveGameAsUser(code: String, callerUid: String): Result<Unit> {
        val flow = games[code]
        val current = flow?.value ?: return Result.success(Unit)
        if (!current.isParticipant(callerUid)) {
            return Result.success(Unit)
        }
        if (current.status == OnlineGameStatus.FINISHED || current.status == OnlineGameStatus.ABANDONED) {
            flow.value = current.copy(
                abandonedBy = "LEFT:$callerUid",
                rematchRequestedBy = emptyList(),
                lastMoveAt = System.currentTimeMillis()
            )
        } else {
            games.remove(code)
        }
        return Result.success(Unit)
    }

    override suspend fun requestRematch(code: String): Result<Unit> {
        return requestRematchAsUser(code, currentUserId)
    }

    fun requestRematchAsUser(code: String, requesterUid: String): Result<Unit> {
        val flow = games[code] ?: return Result.failure(IllegalStateException("L'adversaire a quitté le salon"))
        val current = flow.value ?: return Result.failure(IllegalStateException("L'adversaire a quitté le salon"))
        val creatorUid = current.creatorUid
        val opponentUid = current.opponentUid
        if (opponentUid.isNullOrBlank() || current.abandonedBy?.startsWith("LEFT:") == true) {
            return Result.failure(IllegalStateException("L'adversaire a quitté le salon"))
        }
        if (requesterUid != creatorUid && requesterUid != opponentUid) {
            return Result.failure(IllegalStateException("Vous ne participez pas à ce salon."))
        }
        if (current.status == OnlineGameStatus.ONGOING) {
            return Result.success(Unit)
        }

        val updatedRequests = (current.rematchRequestedBy + requesterUid).distinct()
        val bothAccepted = updatedRequests.contains(creatorUid) && updatedRequests.contains(opponentUid)
        val now = System.currentTimeMillis()

        if (!bothAccepted) {
            flow.value = current.copy(
                rematchRequestedBy = updatedRequests,
                lastMoveAt = now
            )
        } else {
            val storedWinnerRole: Player? = current.winner ?: current.gameState.winner

            // Filet de sécurité de compatibilité ascendante uniquement (no-op si déjà comptabilisé à la fin de manche)
            val (updatedStats, scoredIndex) = computeScoredSalonState(current, storedWinnerRole)

            val winnerUid: String = (storedWinnerRole?.let { current.resolveParticipantUidForRole(it) })
                ?: current.abandonedBy?.takeIf { !it.startsWith("LEFT:") }?.let { abUid ->
                    if (abUid == creatorUid) opponentUid else creatorUid
                }
                ?: creatorUid

            // Le vainqueur de la partie précédente reçoit STICKS, le perdant reçoit STONES
            val newCreatorPlayer = if (winnerUid == creatorUid) Player.STICKS else Player.STONES
            val newOpponentPlayer = newCreatorPlayer.opponent
            val newPlayersMap = mapOf(
                creatorUid to newCreatorPlayer.name,
                opponentUid to newOpponentPlayer.name
            )

            val preservedRuleOptions = current.gameState.ruleOptions
            val newGameState = WaliEngine.initializeGame(ruleOptions = preservedRuleOptions)

            flow.value = current.copy(
                status = OnlineGameStatus.ONGOING,
                creatorPlayer = newCreatorPlayer,
                opponentPlayer = newOpponentPlayer,
                players = newPlayersMap,
                gameState = newGameState,
                winner = null,
                abandonedBy = null,
                drawOfferedBy = null,
                drawOfferTurnCount = -1,
                rematchRequestedBy = emptyList(),
                gamesPlayed = current.gamesPlayed + 1,
                statsByUid = updatedStats,
                lastScoredGameIndex = scoredIndex,
                lastMoveAt = now
            )
        }
        return Result.success(Unit)
    }

    override suspend fun cancelRematchRequest(code: String): Result<Unit> {
        val flow = games[code] ?: return Result.success(Unit)
        val current = flow.value ?: return Result.success(Unit)
        if (current.rematchRequestedBy.contains(currentUserId)) {
            flow.value = current.copy(
                rematchRequestedBy = current.rematchRequestedBy - currentUserId,
                lastMoveAt = System.currentTimeMillis()
            )
        }
        return Result.success(Unit)
    }

    override suspend fun proposeDraw(code: String, uid: String): Result<Unit> {
        val flow = games[code] ?: return Result.failure(IllegalArgumentException("Partie introuvable"))
        val current = flow.value ?: return Result.failure(IllegalStateException("Partie nulle"))

        if (current.status != OnlineGameStatus.ONGOING) {
            return Result.failure(IllegalStateException("La partie n'est pas en cours"))
        }
        if (current.gameState.phase != Phase.MOVEMENT) {
            return Result.failure(IllegalStateException("Le match nul ne peut être proposé qu'en phase de déplacement"))
        }
        if (current.gameState.pendingCaptures > 0) {
            return Result.failure(IllegalStateException("Impossible de proposer un match nul lorsqu'une capture est en attente"))
        }
        if (current.drawOfferedBy != null) {
            return Result.failure(IllegalStateException("Une proposition de match nul est déjà en attente"))
        }

        val proposerRole = current.getPlayerRole(uid)
            ?: return Result.failure(IllegalStateException("Vous ne participez pas à cette partie"))
        if (current.gameState.currentPlayer != proposerRole) {
            return Result.failure(IllegalStateException("Vous ne pouvez proposer un match nul que pendant votre tour"))
        }

        val currentTurnCount = current.gameState.moveHistory.size
        if (current.drawOfferTurnCount >= 0 && currentTurnCount <= current.drawOfferTurnCount) {
            return Result.failure(IllegalStateException("Vous devez attendre au moins un tour complet avant de reproposer un match nul"))
        }

        flow.value = current.copy(
            drawOfferedBy = uid,
            drawOfferTurnCount = currentTurnCount,
            lastMoveAt = System.currentTimeMillis()
        )
        return Result.success(Unit)
    }

    override suspend fun respondToDraw(code: String, accept: Boolean, uid: String): Result<Unit> {
        val flow = games[code] ?: return Result.failure(IllegalArgumentException("Partie introuvable"))
        val current = flow.value ?: return Result.failure(IllegalStateException("Partie nulle"))

        if (current.status != OnlineGameStatus.ONGOING) {
            return Result.failure(IllegalStateException("La partie n'est pas en cours"))
        }
        val offeredBy = current.drawOfferedBy
            ?: return Result.failure(IllegalStateException("Aucune proposition de match nul en attente"))
        if (offeredBy == uid) {
            return Result.failure(IllegalStateException("Vous ne pouvez pas répondre à votre propre proposition"))
        }
        if (current.getPlayerRole(uid) == null) {
            return Result.failure(IllegalStateException("Vous ne participez pas à cette partie"))
        }

        val now = System.currentTimeMillis()
        if (accept) {
            val drawGameState = current.gameState.copy(
                phase = Phase.GAME_OVER,
                winner = null,
                pendingCaptures = 0,
                moveHistory = current.gameState.moveHistory + "Match nul — Les deux joueurs ont accepté."
            )
            val (updatedStats, newLastScoredIndex) = computeScoredSalonState(current, null)
            flow.value = current.copy(
                gameState = drawGameState,
                status = OnlineGameStatus.FINISHED,
                winner = null,
                drawOfferedBy = null,
                statsByUid = updatedStats,
                lastScoredGameIndex = newLastScoredIndex,
                lastMoveAt = now
            )
        } else {
            flow.value = current.copy(
                drawOfferedBy = null,
                lastMoveAt = now
            )
        }
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

    @Test
    fun rematch_singlePlayerRequest_addsUidAndWaitsWithoutResetting() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Fin de la 1re partie : victoire de STICKS (local_user_42)
        val finishedState = WaliEngine.initializeGame().copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.FINISHED,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = finishedState,
            winner = Player.STICKS
        )
        testScheduler.runCurrent()

        // Le joueur local demande une revanche
        viewModel.requestOnlineRematch()
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(listOf("local_user_42"), session.rematchRequestedBy)
        assertEquals(OnlineGameStatus.FINISHED, session.status)
        assertEquals(1, session.gamesPlayed)
        assertTrue(viewModel.uiState.value.isWaitingForRematch)
        assertEquals(Phase.GAME_OVER, viewModel.uiState.value.gameState.phase)
    }

    @Test
    fun rematch_cancelRequest_emptiesRematchRequestedBy() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        val finishedState = WaliEngine.initializeGame().copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.FINISHED,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = finishedState,
            winner = Player.STICKS
        )
        testScheduler.runCurrent()

        viewModel.requestOnlineRematch()
        testScheduler.runCurrent()
        assertEquals(listOf("local_user_42"), flow.value!!.rematchRequestedBy)

        // Annulation de la demande de revanche
        viewModel.cancelOnlineRematchRequest()
        testScheduler.runCurrent()

        assertTrue(flow.value!!.rematchRequestedBy.isEmpty())
        assertFalse(viewModel.uiState.value.isWaitingForRematch)
        assertNull(viewModel.uiState.value.rematchTimeoutRemainingSeconds)
    }

    @Test
    fun rematch_secondPlayerAccepts_resetsToOngoingIncrementsGamesPlayedStatsAndPreservesRuleOptions() = runOnlineTest {
        // Active les deux variantes pour le créateur du salon
        viewModel.onMandatoryAlignmentChanged(true)
        viewModel.onDefeatAtTwoPiecesChanged(true)
        testScheduler.runCurrent()

        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Fin de la partie 1 : victoire de l'adversaire (opponent_99 qui jouait STONES)
        val finishedState = flow.value!!.gameState.copy(
            phase = Phase.GAME_OVER,
            winner = Player.STONES,
            sticksRemaining = 0,
            stonesRemaining = 0,
            capturedBySticks = 12,
            capturedByStones = 12
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.FINISHED,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = finishedState,
            winner = Player.STONES
        )
        testScheduler.runCurrent()

        // Joueur 1 demande la revanche
        viewModel.requestOnlineRematch()
        testScheduler.runCurrent()
        assertEquals(listOf("local_user_42"), flow.value!!.rematchRequestedBy)

        // Joueur 2 (opponent_99) accepte la revanche
        fakeRepo.requestRematchAsUser(code, "opponent_99")
        testScheduler.runCurrent()

        val resetSession = flow.value!!
        assertEquals(OnlineGameStatus.ONGOING, resetSession.status)
        assertEquals(2, resetSession.gamesPlayed)
        assertTrue(resetSession.rematchRequestedBy.isEmpty())
        assertNull(resetSession.winner)
        assertNull(resetSession.abandonedBy)

        // statsByUid[winnerUid] incrémenté de 1, le perdant reste à 0
        assertEquals(0, resetSession.statsByUid["local_user_42"])
        assertEquals(1, resetSession.statsByUid["opponent_99"])

        // WaliRuleOptions d'origine du salon conservées intactes
        assertTrue(resetSession.gameState.ruleOptions.mandatoryAlignment)
        assertTrue(resetSession.gameState.ruleOptions.defeatAtTwoPieces)
        assertEquals(Phase.PLACEMENT, resetSession.gameState.phase)
        assertEquals(12, resetSession.gameState.sticksRemaining)
        assertEquals(12, resetSession.gameState.stonesRemaining)

        // Vérifie l'état UI côté ViewModel
        val uiState = viewModel.uiState.value
        assertEquals(Phase.PLACEMENT, uiState.gameState.phase)
        assertEquals(2, uiState.salonGamesPlayed)
        assertEquals(0, uiState.salonMyWins)
        assertEquals(1, uiState.salonOpponentWins)
    }

    @Test
    fun rematch_assignsSticksToPreviousWinner_regardlessOfCreatorOrPreviousSide() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Cas A : L'adversaire (opponent_99, qui jouait STONES) gagne la partie 1 -> il doit recevoir STICKS à la partie 2
        val game1End = flow.value!!.gameState.copy(phase = Phase.GAME_OVER, winner = Player.STONES)
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.FINISHED,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = game1End,
            winner = Player.STONES
        )
        testScheduler.runCurrent()

        fakeRepo.requestRematchAsUser(code, "local_user_42")
        fakeRepo.requestRematchAsUser(code, "opponent_99")
        testScheduler.runCurrent()

        val sessionGame2 = flow.value!!
        assertEquals(Player.STONES, sessionGame2.creatorPlayer)
        assertEquals(Player.STICKS, sessionGame2.opponentPlayer)
        assertEquals(Player.STONES, sessionGame2.getPlayerRole("local_user_42"))
        assertEquals(Player.STICKS, sessionGame2.getPlayerRole("opponent_99"))
        assertEquals(Player.STICKS, viewModel.uiState.value.gameState.currentPlayer)
        assertEquals(Player.STONES, viewModel.uiState.value.localPlayerRole)

        // Cas B : Le créateur (local_user_42, qui jouait STONES en partie 2) gagne la partie 2 -> il doit recevoir STICKS à la partie 3
        val game2End = sessionGame2.gameState.copy(phase = Phase.GAME_OVER, winner = Player.STONES)
        flow.value = sessionGame2.copy(
            status = OnlineGameStatus.FINISHED,
            gameState = game2End,
            winner = Player.STONES
        )
        testScheduler.runCurrent()

        fakeRepo.requestRematchAsUser(code, "opponent_99")
        fakeRepo.requestRematchAsUser(code, "local_user_42")
        testScheduler.runCurrent()

        val sessionGame3 = flow.value!!
        assertEquals(3, sessionGame3.gamesPlayed)
        assertEquals(Player.STICKS, sessionGame3.creatorPlayer)
        assertEquals(Player.STONES, sessionGame3.opponentPlayer)
        assertEquals(Player.STICKS, sessionGame3.getPlayerRole("local_user_42"))
        assertEquals(Player.STONES, sessionGame3.getPlayerRole("opponent_99"))
        assertEquals(2, sessionGame3.statsByUid["local_user_42"])
        assertEquals(2, sessionGame3.statsByUid["opponent_99"])
    }

    @Test
    fun rematch_opponentLeavesDuringWait_returnsToMainMenuCleanlyWithoutBlocking() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        val finishedState = WaliEngine.initializeGame().copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.FINISHED,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = finishedState,
            winner = Player.STICKS
        )
        testScheduler.runCurrent()

        // Le joueur local demande la revanche et attend
        viewModel.requestOnlineRematch()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isWaitingForRematch)

        // L'adversaire quitte le salon
        flow.value = flow.value!!.copy(
            abandonedBy = "LEFT:opponent_99",
            rematchRequestedBy = emptyList()
        )
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isWaitingForRematch)
        assertNull(state.onlineGameSession)
        assertTrue(state.showModeSelectionDialog)
        assertEquals("L'adversaire a quitté le salon", state.feedbackMessage)
    }

    @Test
    fun rematch_timeoutAfterConfiguredDelay_cancelsPendingRequestCleanly() = runOnlineTest {
        viewModel.rematchTimeoutSeconds = 3
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        val finishedState = WaliEngine.initializeGame().copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.FINISHED,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = finishedState,
            winner = Player.STICKS
        )
        testScheduler.runCurrent()

        viewModel.requestOnlineRematch()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isWaitingForRematch)

        // Avance le temps au-delà du délai d'attente de revanche
        testScheduler.advanceTimeBy(3_500L)
        testScheduler.runCurrent()

        assertTrue(flow.value!!.rematchRequestedBy.isEmpty())
        assertFalse(viewModel.uiState.value.isWaitingForRematch)
        assertNull(viewModel.uiState.value.rematchTimeoutRemainingSeconds)
    }

    // =========================================================================
    // PARTIE D — AUDIT ET CORRECTION DU COMPTAGE DES VICTOIRES DU SALON
    // =========================================================================

    @Test
    fun salonStats_normalGameEndWithoutRematch_incrementsWinnerOnceImmediately() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0)
        )
        testScheduler.runCurrent()

        // La partie se termine normalement par la victoire de STICKS (local_user_42), sans aucune revanche demandée
        val winningState = flow.value!!.gameState.copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        fakeRepo.updateGameState(code, winningState)
        testScheduler.runCurrent()

        val finishedSession = flow.value!!
        assertEquals(OnlineGameStatus.FINISHED, finishedSession.status)
        assertEquals(1, finishedSession.gamesPlayed)
        assertEquals(1, finishedSession.lastScoredGameIndex)
        assertEquals(2, finishedSession.statsByUid["local_user_42"])
        assertEquals(0, finishedSession.statsByUid["opponent_99"])
    }

    @Test
    fun salonStats_abandonment_awardsWinToNonAbandoningPlayerImmediatelyAndSetsAbandonedByToLoser() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0)
        )
        testScheduler.runCurrent()

        // Cas d'inactivité prolongée : le joueur local (local_user_42, STICKS) déclare l'adversaire (opponent_99, STONES) abandonnataire
        viewModel.declareOpponentAbandoned()
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, session.status)
        assertEquals(Player.STICKS, session.winner)
        // Point 1 : abandonedBy doit être l'UID de celui qui a abandonné (opponent_99), et non du vainqueur (local_user_42)
        assertEquals("opponent_99", session.abandonedBy)
        assertEquals(2, session.statsByUid["local_user_42"])
        assertEquals(0, session.statsByUid["opponent_99"])
        assertEquals(1, session.lastScoredGameIndex)
    }

    @Test
    fun salonStats_threeConsecutiveGamesWithSideSwaps_tracksWinsByUidCorrectly() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0)
        )
        testScheduler.runCurrent()

        // Manche 1 : local_user_42 (STICKS) vs opponent_99 (STONES) -> opponent_99 gagne (avec STONES, 0 capture adverse -> +2)
        fakeRepo.updateGameState(code, flow.value!!.gameState.copy(phase = Phase.GAME_OVER, winner = Player.STONES))
        testScheduler.runCurrent()
        assertEquals(0, flow.value!!.statsByUid["local_user_42"])
        assertEquals(2, flow.value!!.statsByUid["opponent_99"])
        assertEquals(1, flow.value!!.lastScoredGameIndex)

        // Revanche pour Manche 2 -> opponent_99 reçoit STICKS, local_user_42 reçoit STONES (pas de double comptage)
        fakeRepo.requestRematchAsUser(code, "local_user_42")
        fakeRepo.requestRematchAsUser(code, "opponent_99")
        testScheduler.runCurrent()
        assertEquals(2, flow.value!!.gamesPlayed)
        assertEquals(1, flow.value!!.lastScoredGameIndex)
        assertEquals(Player.STONES, flow.value!!.creatorPlayer)
        assertEquals(Player.STICKS, flow.value!!.opponentPlayer)
        assertEquals(0, flow.value!!.statsByUid["local_user_42"])
        assertEquals(2, flow.value!!.statsByUid["opponent_99"])

        // Manche 2 : local_user_42 (STONES) gagne contre opponent_99 (STICKS) (0 capture adverse -> +2)
        fakeRepo.updateGameState(code, flow.value!!.gameState.copy(phase = Phase.GAME_OVER, winner = Player.STONES))
        testScheduler.runCurrent()
        assertEquals(2, flow.value!!.statsByUid["local_user_42"])
        assertEquals(2, flow.value!!.statsByUid["opponent_99"])
        assertEquals(2, flow.value!!.lastScoredGameIndex)

        // Revanche pour Manche 3 -> local_user_42 reçoit STICKS, opponent_99 reçoit STONES
        fakeRepo.requestRematchAsUser(code, "opponent_99")
        fakeRepo.requestRematchAsUser(code, "local_user_42")
        testScheduler.runCurrent()
        assertEquals(3, flow.value!!.gamesPlayed)
        assertEquals(2, flow.value!!.lastScoredGameIndex)
        assertEquals(Player.STICKS, flow.value!!.creatorPlayer)
        assertEquals(Player.STONES, flow.value!!.opponentPlayer)

        // Manche 3 : opponent_99 (STONES) gagne par forfait de local_user_42 (STICKS) (0 capture -> +2)
        fakeRepo.declareAbandonmentAsUser(code, winnerPlayer = Player.STONES, callerUid = "local_user_42")
        testScheduler.runCurrent()

        val finalSession = flow.value!!
        assertEquals(3, finalSession.gamesPlayed)
        assertEquals(3, finalSession.lastScoredGameIndex)
        assertEquals(2, finalSession.statsByUid["local_user_42"])
        assertEquals(4, finalSession.statsByUid["opponent_99"])
        assertEquals(2, viewModel.uiState.value.salonMyWins)
        assertEquals(4, viewModel.uiState.value.salonOpponentWins)
    }

    @Test
    fun salonStats_duplicateOrConcurrentEndGameCalls_isIdempotentNoDoubleCount() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0)
        )
        testScheduler.runCurrent()

        val winState = flow.value!!.gameState.copy(phase = Phase.GAME_OVER, winner = Player.STICKS)
        // Appel 1
        fakeRepo.updateGameState(code, winState)
        // Appel 2 (retry / doublon)
        fakeRepo.updateGameState(code, winState)
        // Appel 3 (abandon concurrent tardif)
        fakeRepo.declareAbandonment(code, Player.STICKS)
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(2, session.statsByUid["local_user_42"])
        assertEquals(0, session.statsByUid["opponent_99"])
        assertEquals(1, session.lastScoredGameIndex)
    }

    @Test
    fun salonStats_nonParticipantUid_neverAppearsInStatsByUidAndIncompleteSalonIsSafe() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Point 4 : Salon incomplet (opponentUid == null) -> aucun crash, pas d'incrément ni d'avancée du marqueur
        flow.value = flow.value!!.copy(status = OnlineGameStatus.ONGOING, opponentUid = null)
        fakeRepo.declareAbandonmentAsUser(code, Player.STICKS, "intruder_uid")
        assertEquals(0, flow.value!!.lastScoredGameIndex)
        assertFalse(flow.value!!.statsByUid.containsKey("intruder_uid"))

        // Salon complet avec un uid tiers injecté dans statsByUid : le filtre l'élimine et refuse sa demande de revanche
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0, "spectator_or_intruder" to 5),
            lastScoredGameIndex = 0
        )
        fakeRepo.updateGameState(code, flow.value!!.gameState.copy(phase = Phase.GAME_OVER, winner = Player.STICKS))
        testScheduler.runCurrent()

        val scored = flow.value!!
        assertEquals(setOf("local_user_42", "opponent_99"), scored.statsByUid.keys)
        assertFalse(scored.statsByUid.containsKey("spectator_or_intruder"))
        assertTrue(fakeRepo.requestRematchAsUser(code, "spectator_or_intruder").isFailure)
    }

    @Test
    fun salonStats_forfeitAndInactivityTimeoutRace_firstWriteWinsAndScoresOnce() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0)
        )
        testScheduler.runCurrent()

        // 1er appel : local_user_42 (STICKS) déclare forfait -> vainqueur = STONES (opponent_99), abandonedBy = local_user_42
        val firstResult = fakeRepo.declareAbandonmentAsUser(code, winnerPlayer = Player.STONES, callerUid = "local_user_42")
        // 2e appel concurrent : local_user_42 ou opponent_99 déclare un abandon opposé (vainqueur = STICKS)
        val secondResult = fakeRepo.declareAbandonmentAsUser(code, winnerPlayer = Player.STICKS, callerUid = "local_user_42")
        testScheduler.runCurrent()

        assertTrue(firstResult.isSuccess)
        assertTrue(secondResult.isSuccess) // Pas d'erreur remontée à l'utilisateur

        val session = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, session.status)
        assertEquals(Player.STONES, session.winner)
        assertEquals("local_user_42", session.abandonedBy)
        assertEquals(0, session.statsByUid["local_user_42"])
        assertEquals(2, session.statsByUid["opponent_99"])
        assertEquals(1, session.lastScoredGameIndex)
    }

    @Test
    fun victoryDialog_scoreIsUpToDateImmediatelyAtGameEnd() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0)
        )
        testScheduler.runCurrent()

        // Fin de la manche 1 (victoire du joueur local STICKS), SANS aucune revanche demandée
        val winningState = flow.value!!.gameState.copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        fakeRepo.updateGameState(code, winningState)
        testScheduler.runCurrent()

        val uiState = viewModel.uiState.value
        assertEquals(Phase.GAME_OVER, uiState.gameState.phase)
        assertEquals(1, uiState.salonGamesPlayed)
        // Le score du salon dans l'UI affiche immédiatement 2 - 0 (0 capture du perdant) dès la fin de la manche 1
        assertEquals(2, uiState.salonMyWins)
        assertEquals(0, uiState.salonOpponentWins)
    }

    // =========================================================================
    // VÉRIFICATION 1 & PARTIE F — MODE SPECTATEUR (DEUX CODES PAR SALON)
    // =========================================================================

    @Test
    fun verification1_declareAbandonmentWhenLoserRoleUnresolvable_setsAbandonedByToNullNotCallerUid() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Salon ONGOING dont opponentUid est absent : resolveParticipantUidForRole(Player.STONES) renvoie null
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = null,
            opponentPlayer = null
        )

        fakeRepo.declareAbandonmentAsUser(code, winnerPlayer = Player.STICKS, callerUid = "local_user_42")
        val updated = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, updated.status)
        assertEquals(Player.STICKS, updated.winner)
        // Vérification 1 : abandonedBy reste honnêtement null au lieu de recevoir l'UID du déclarant (vainqueur)
        assertNull(updated.abandonedBy)
    }

    @Test
    fun spectatorCode_createdWithDistinctSevenCharFormatAndRegeneratedOnCollision() = runOnlineTest {
        // Prépare une collision volontaire sur le code spectateur "S234567"
        fakeRepo.presetSpectatorCodes.addLast("S234567")
        fakeRepo.presetSpectatorCodes.addLast("S234567") // collision avec le 1er salon (même s'il est FINISHED)
        fakeRepo.presetSpectatorCodes.addLast("S89ABCD") // code de repli unique

        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val firstPlayerCode = viewModel.uiState.value.waitingForOpponentCode!!
        val firstSpectatorCode = viewModel.uiState.value.spectatorCode
        assertEquals("S234567", firstSpectatorCode)
        assertTrue(WaliOnlineCodeGenerator.isValidSpectatorCode(firstSpectatorCode))
        assertFalse(WaliOnlineCodeGenerator.isValidCode(firstSpectatorCode))
        assertFalse(firstPlayerCode.equals(firstSpectatorCode, ignoreCase = true))

        // Même si le 1er salon est passé à FINISHED, son spectatorCode ne doit pas être réattribué
        fakeRepo.games[firstPlayerCode]!!.value = fakeRepo.games[firstPlayerCode]!!.value!!.copy(
            status = OnlineGameStatus.FINISHED
        )

        // Création d'un 2e salon : le 1er tirage "S234567" entre en collision -> régénération automatique vers "S89ABCD"
        val secondCreate = fakeRepo.createGame(
            code = "ABCDEF",
            isPublic = true,
            creatorSide = Player.STONES,
            initialGameState = WaliEngine.initializeGame()
        )
        assertTrue(secondCreate.isSuccess)
        assertEquals("S89ABCD", secondCreate.getOrNull()?.spectatorCode)
    }

    @Test
    fun spectatorSelection_whenCollisionExistsInDatabase_prioritizesOngoingThenMostRecentCreatedAt() {
        val baseState = WaliEngine.initializeGame()
        val finishedRecent = OnlineGameSession(
            code = "FIN999",
            spectatorCode = "SCOLLID",
            isPublic = true,
            status = OnlineGameStatus.FINISHED,
            creatorUid = "u1",
            creatorPlayer = Player.STICKS,
            gameState = baseState,
            lastMoveAt = 5_000L,
            createdAt = 5_000L
        )
        val ongoingOlder = OnlineGameSession(
            code = "ONG111",
            spectatorCode = "SCOLLID",
            isPublic = true,
            status = OnlineGameStatus.ONGOING,
            creatorUid = "u2",
            creatorPlayer = Player.STICKS,
            gameState = baseState,
            lastMoveAt = 2_000L,
            createdAt = 2_000L
        )
        val ongoingNewer = OnlineGameSession(
            code = "ONG222",
            spectatorCode = "SCOLLID",
            isPublic = true,
            status = OnlineGameStatus.ONGOING,
            creatorUid = "u3",
            creatorPlayer = Player.STICKS,
            gameState = baseState,
            lastMoveAt = 4_000L,
            createdAt = 4_000L
        )

        // 1. Entre un salon FINISHED plus récent et un salon ONGOING plus ancien, ONGOING est prioritaire
        val chosen1 = selectDeterministicSpectatorSession(
            listOf(finishedRecent, ongoingOlder)
        )
        assertEquals("ONG111", chosen1?.code)

        // 2. Entre deux salons ONGOING, le plus récent est sélectionné
        val chosen2 = selectDeterministicSpectatorSession(
            listOf(finishedRecent, ongoingOlder, ongoingNewer)
        )
        assertEquals("ONG222", chosen2?.code)
    }

    @Test
    fun crossCodeRejection_spectatorCodeInJoinTabAndPlayerCodeInSpectatorTabReturnExplicitErrors() = runOnlineTest {
        // Crée un salon avec un code joueur et un code spectateur
        val created = fakeRepo.createGame(
            code = "PLAY23",
            isPublic = true,
            creatorSide = Player.STICKS,
            initialGameState = WaliEngine.initializeGame()
        ).getOrThrow()
        val playerCode = created.code
        val spectatorCode = created.spectatorCode!!

        // 1. Saisir un code spectateur dans "Rejoindre une partie" -> refusé avec message explicite
        viewModel.startJoinOnlineGame(spectatorCode)
        testScheduler.runCurrent()
        assertEquals(
            "Ce code est un code spectateur. Utilisez l'onglet Spectateurs.",
            viewModel.uiState.value.onlineErrorMessage
        )

        // 2. Saisir un code joueur dans "Spectateurs" -> refusé avec message explicite
        viewModel.dismissOnlineError()
        viewModel.startSpectateOnlineGame(playerCode)
        testScheduler.runCurrent()
        assertEquals(
            "Ce code est un code joueur. Utilisez l'onglet Rejoindre.",
            viewModel.uiState.value.onlineErrorMessage
        )
    }

    @Test
    fun spectatorJoin_onWaitingAndOngoingSalon_remainsStrictlyReadOnlyAndReceivesRealtimeMoves() = runOnlineTest {
        // Un créateur ("creator_1") crée un salon en attente
        val creatorRepo = FakeWaliOnlineRepository("creator_1")
        val created = creatorRepo.createGame(
            code = "ROOM42",
            isPublic = true,
            creatorSide = Player.STICKS,
            initialGameState = WaliEngine.initializeGame()
        ).getOrThrow()

        // Partage la même table de salons avec le repository du spectateur ("spectator_77")
        val spectatorRepo = FakeWaliOnlineRepository("spectator_77").also {
            it.games.putAll(creatorRepo.games)
        }
        val spectatorStorage = InMemoryWaliGameStorage()
        val spectatorVm = WaliViewModel(
            storage = spectatorStorage,
            onlineRepository = spectatorRepo
        )
        spectatorVm.stopTurnTimer()

        // Le spectateur rejoint pendant que le salon est encore WAITING
        spectatorVm.startSpectateOnlineGame(created.spectatorCode!!)
        testScheduler.runCurrent()

        val stateWhileWaiting = spectatorVm.uiState.value
        assertEquals(GameMode.ONLINE, stateWhileWaiting.gameMode)
        assertEquals(OnlineRole.SPECTATOR, stateWhileWaiting.onlineRole)
        assertTrue(stateWhileWaiting.isSpectator)
        assertNull(stateWhileWaiting.localPlayerRole)

        // Le salon est resté WAITING, opponentUid est toujours null, statsByUid ne contient pas le spectateur
        val sessionAfterSpectate = creatorRepo.games["ROOM42"]!!.value!!
        assertEquals(OnlineGameStatus.WAITING, sessionAfterSpectate.status)
        assertNull(sessionAfterSpectate.opponentUid)
        assertFalse(sessionAfterSpectate.players.containsKey("spectator_77"))
        assertFalse(sessionAfterSpectate.statsByUid.containsKey("spectator_77"))

        // L'adversaire réel ("opponent_2") rejoint la partie -> passage en ONGOING
        val ongoingSession = sessionAfterSpectate.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_2",
            opponentPlayer = Player.STONES,
            players = mapOf("creator_1" to "STICKS", "opponent_2" to "STONES"),
            statsByUid = mapOf("creator_1" to 0, "opponent_2" to 0)
        )
        creatorRepo.games["ROOM42"]!!.value = ongoingSession
        testScheduler.runCurrent()

        assertEquals(OnlineGameStatus.ONGOING, spectatorVm.uiState.value.onlineGameSession?.status)
        assertNull(spectatorVm.uiState.value.localPlayerRole)
        assertFalse(spectatorVm.uiState.value.canDeclareForfeit)
        assertFalse(spectatorVm.uiState.value.canDeclareAbandonment)

        // Le spectateur clique sur le plateau -> aucun pion posé, aucune écriture réseau
        val clickPos = Position(0, 0)
        spectatorVm.onCellClicked(clickPos)
        testScheduler.runCurrent()
        assertNull(spectatorVm.uiState.value.gameState.board[clickPos])
        assertNull(creatorRepo.games["ROOM42"]!!.value!!.gameState.board[clickPos])

        // Le joueur STICKS pose un pion en (2, 2) -> le spectateur le voit en temps réel
        val movePos = Position(2, 2)
        val afterMoveState = WaliEngine.placePiece(ongoingSession.gameState, movePos)
        creatorRepo.updateGameState("ROOM42", afterMoveState)
        testScheduler.runCurrent()

        assertEquals(Player.STICKS, spectatorVm.uiState.value.gameState.board[movePos])
        assertEquals(Player.STONES, spectatorVm.uiState.value.gameState.currentPlayer)

        spectatorVm.stopTurnTimer()
        spectatorVm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun spectatorGuards_gameEndRematchInactivityAndLeave_neverPolluteScoreboardOrSalonDocument() = runOnlineTest {
        val creatorRepo = FakeWaliOnlineRepository("creator_1")
        val created = creatorRepo.createGame(
            code = "GUARD9",
            isPublic = true,
            creatorSide = Player.STICKS,
            initialGameState = WaliEngine.initializeGame()
        ).getOrThrow()

        val t0 = 1_000_000L
        creatorRepo.games["GUARD9"]!!.value = created.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_2",
            opponentPlayer = Player.STONES,
            players = mapOf("creator_1" to "STICKS", "opponent_2" to "STONES"),
            statsByUid = mapOf("creator_1" to 0, "opponent_2" to 0),
            lastMoveAt = t0
        )

        val spectatorRepo = FakeWaliOnlineRepository("spectator_88").also {
            it.games.putAll(creatorRepo.games)
        }
        val spectatorScoreboard = com.example.wali.storage.InMemoryWaliScoreboardRepository()
        val spectatorVm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            scoreboardRepository = spectatorScoreboard,
            onlineRepository = spectatorRepo
        )
        spectatorVm.stopTurnTimer()

        spectatorVm.startSpectateOnlineGame(created.spectatorCode!!)
        testScheduler.runCurrent()

        // 1. Inactivité prolongée (> 3 min) : le spectateur ne voit ni bouton d'abandon ni forfait et ne peut pas déclarer d'abandon
        spectatorVm.checkOnlineInactivity(t0 + 200_000L)
        assertFalse(spectatorVm.uiState.value.canDeclareAbandonment)
        assertFalse(spectatorVm.uiState.value.isOpponentDisconnected)
        spectatorVm.declareOpponentAbandoned()
        spectatorVm.declareForfeit()
        testScheduler.runCurrent()
        assertEquals(OnlineGameStatus.ONGOING, creatorRepo.games["GUARD9"]!!.value!!.status)

        // 2. La partie se termine par la victoire de STICKS ("creator_1")
        val finishedGameState = creatorRepo.games["GUARD9"]!!.value!!.gameState.copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        creatorRepo.updateGameState("GUARD9", finishedGameState)
        testScheduler.runCurrent()

        // Le spectateur voit la fin de partie et les scores du salon par camp, mais son ScoreboardRepository personnel reste à 0
        val specStateAtEnd = spectatorVm.uiState.value
        assertEquals(Phase.GAME_OVER, specStateAtEnd.gameState.phase)
        assertEquals(2, specStateAtEnd.salonSticksPlayerWins)
        assertEquals(0, specStateAtEnd.salonStonesPlayerWins)
        assertEquals(0, specStateAtEnd.scoreboardStats.totalGamesPlayed)
        assertTrue(specStateAtEnd.recentGames.isEmpty())

        // 3. Le spectateur tente de demander/annuler une revanche -> ignoré, rematchRequestedBy reste vide
        spectatorVm.requestOnlineRematch()
        testScheduler.runCurrent()
        assertTrue(creatorRepo.games["GUARD9"]!!.value!!.rematchRequestedBy.isEmpty())

        // 4. Les deux joueurs acceptent la revanche -> le spectateur reste SPECTATOR (localPlayerRole == null) et voit la manche 2
        creatorRepo.requestRematchAsUser("GUARD9", "creator_1")
        creatorRepo.requestRematchAsUser("GUARD9", "opponent_2")
        testScheduler.runCurrent()

        val specStateAtRematch = spectatorVm.uiState.value
        assertEquals(OnlineRole.SPECTATOR, specStateAtRematch.onlineRole)
        assertTrue(specStateAtRematch.isSpectator)
        assertNull(specStateAtRematch.localPlayerRole)
        assertEquals(2, specStateAtRematch.salonGamesPlayed)
        assertEquals(Phase.PLACEMENT, specStateAtRematch.gameState.phase)

        // 5. Le spectateur quitte la partie -> le salon ONGOING n'est ni supprimé ni marqué ABANDONED ni LEFT:
        spectatorVm.leaveOnlineGame()
        testScheduler.runCurrent()

        val salonAfterSpectatorLeft = creatorRepo.games["GUARD9"]!!.value
        assertNotNull(salonAfterSpectatorLeft)
        assertEquals(OnlineGameStatus.ONGOING, salonAfterSpectatorLeft?.status)
        assertNull(salonAfterSpectatorLeft?.abandonedBy)

        spectatorVm.stopTurnTimer()
        spectatorVm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun spectatorChat_sendsMessageWithIsSpectatorFlagAndNullSenderRole() = runOnlineTest {
        val creatorRepo = FakeWaliOnlineRepository("creator_1")
        val created = creatorRepo.createGame(
            code = "CHAT77",
            isPublic = true,
            creatorSide = Player.STICKS,
            initialGameState = WaliEngine.initializeGame()
        ).getOrThrow()

        val spectatorRepo = FakeWaliOnlineRepository("spectator_55").also {
            it.games.putAll(creatorRepo.games)
        }
        val fakeChat = com.example.wali.chat.FakeChatRepository()
        val spectatorVm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            onlineRepository = spectatorRepo,
            chatRepository = fakeChat
        )
        spectatorVm.stopTurnTimer()

        spectatorVm.startSpectateOnlineGame(created.spectatorCode!!)
        testScheduler.runCurrent()

        spectatorVm.sendChatMessage("Belle partie !")
        testScheduler.runCurrent()

        assertEquals(1, fakeChat.sentMessages.size)
        val sent = fakeChat.sentMessages.first()
        assertEquals("Belle partie !", sent.text)
        assertEquals("spectator_55", sent.senderUid)
        assertNull(sent.senderRole)
        assertTrue(sent.isSpectator)

        spectatorVm.stopTurnTimer()
        spectatorVm.stopOnlineInactivityMonitoring()
    }

    // =========================================================================
    // PARTIE G — MATCH NUL (G1) ET VICTOIRE DOUBLE (G2)
    // =========================================================================

    private fun createMovementGameState(
        currentPlayer: Player = Player.STICKS,
        sticksRemaining: Int = 10,
        stonesRemaining: Int = 10,
        capturedBySticks: Int = (Position.PIECES_PER_PLAYER - stonesRemaining).coerceAtLeast(0),
        capturedByStones: Int = (Position.PIECES_PER_PLAYER - sticksRemaining).coerceAtLeast(0),
        moveHistory: List<String> = listOf("A1", "B1", "A2", "B2")
    ): GameState {
        val board = Position.allPositions.associateWith<Position, Player?> { null }.toMutableMap()
        // Place quelques pions pour cohérence du plateau
        board[Position(0, 0)] = Player.STICKS
        board[Position(0, 1)] = Player.STICKS
        board[Position(1, 0)] = Player.STICKS
        board[Position(4, 5)] = Player.STONES
        board[Position(4, 4)] = Player.STONES
        board[Position(3, 5)] = Player.STONES
        return GameState(
            board = board,
            currentPlayer = currentPlayer,
            phase = Phase.MOVEMENT,
            sticksRemaining = sticksRemaining,
            stonesRemaining = stonesRemaining,
            winner = null,
            usedLines = emptySet(),
            moveHistory = moveHistory,
            capturedBySticks = capturedBySticks,
            capturedByStones = capturedByStones
        )
    }

    @Test
    fun draw_proposalAccepted_awardNoPointsToBothPlayers() = runOnlineTest {
        val fakeChat = com.example.wali.chat.FakeChatRepository()
        val scoreboard = com.example.wali.storage.InMemoryWaliScoreboardRepository()
        val vm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            scoreboardRepository = scoreboard,
            onlineRepository = fakeRepo,
            chatRepository = fakeChat
        )
        vm.stopTurnTimer()

        vm.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = vm.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val movementState = createMovementGameState(currentPlayer = Player.STICKS)
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = movementState
        )
        testScheduler.runCurrent()

        assertTrue(vm.uiState.value.canProposeDraw)

        // Le joueur local (STICKS) propose un match nul
        vm.proposeDraw()
        testScheduler.runCurrent()

        assertEquals("local_user_42", flow.value!!.drawOfferedBy)
        assertTrue(vm.uiState.value.isWaitingForDrawResponse)
        assertFalse(vm.uiState.value.canProposeDraw)
        assertTrue(fakeChat.sentMessages.any { it.text == "Bâtonnets propose un match nul" })

        // L'adversaire (opponent_99) accepte le match nul
        fakeRepo.respondToDraw(code, accept = true, uid = "opponent_99")
        testScheduler.runCurrent()

        val finished = flow.value!!
        assertEquals(OnlineGameStatus.FINISHED, finished.status)
        assertNull(finished.winner)
        assertEquals(Phase.GAME_OVER, finished.gameState.phase)
        assertNull(finished.gameState.winner)
        assertEquals(0, finished.statsByUid["local_user_42"])
        assertEquals(0, finished.statsByUid["opponent_99"])
        assertEquals(1, finished.lastScoredGameIndex)

        val uiState = vm.uiState.value
        assertTrue(uiState.isDrawGameOver)
        assertFalse(uiState.isDoubleWin)
        assertEquals("Match nul — Les deux joueurs ont accepté.", uiState.gameOverReason)
        assertEquals(0, scoreboard.getStats().totalWins)

        vm.stopTurnTimer()
        vm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun draw_proposalRefused_gameResumes_andSamePlayerCannotReproposeImmediately() = runOnlineTest {
        // 1. Vérification en mode local 2 joueurs
        val movementState = createMovementGameState(
            currentPlayer = Player.STICKS,
            moveHistory = listOf("m1", "m2")
        )
        val localStorage = InMemoryWaliGameStorage()
        localStorage.saveGame(
            com.example.wali.storage.SavedGame(
                gameState = movementState,
                gameMode = GameMode.TWO_PLAYERS,
                aiDifficulty = com.example.wali.ai.AiDifficulty.MEDIUM,
                humanPlayer = Player.STICKS
            )
        )
        val localVm = WaliViewModel(storage = localStorage)
        testScheduler.runCurrent()
        localVm.resumeSavedGame()
        testScheduler.runCurrent()
        localVm.stopTurnTimer()

        assertTrue(localVm.uiState.value.canProposeDraw)
        localVm.proposeDraw()
        assertTrue(localVm.uiState.value.isDrawOfferedToMe)
        assertEquals(Player.STICKS, localVm.uiState.value.localDrawOfferedBy)

        // L'adversaire refuse : la partie continue, le proposant (STICKS) garde son tour mais ne peut pas reproposer immédiatement
        localVm.refuseDraw()
        assertFalse(localVm.uiState.value.isDrawOfferPending)
        assertEquals(Phase.MOVEMENT, localVm.uiState.value.gameState.phase)
        assertEquals(Player.STICKS, localVm.uiState.value.gameState.currentPlayer)
        assertEquals(Player.STICKS, localVm.uiState.value.localLastDrawProposer)
        assertFalse(localVm.uiState.value.canProposeDraw)
        localVm.proposeDraw()
        assertFalse(localVm.uiState.value.isDrawOfferPending)

        // 2. Vérification en mode en ligne : le proposant garde la main mais ne peut pas reproposer tant qu'un coup n'a pas été joué
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()
        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            gameState = movementState
        )
        testScheduler.runCurrent()

        viewModel.proposeDraw()
        testScheduler.runCurrent()
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)

        // L'adversaire refuse -> le tour reste à STICKS (proposant), mais canProposeDraw et proposeDraw restent bloqués
        fakeRepo.respondToDraw(code, accept = false, uid = "opponent_99")
        testScheduler.runCurrent()

        assertNull(flow.value!!.drawOfferedBy)
        assertEquals(Player.STICKS, flow.value!!.gameState.currentPlayer)
        assertFalse(viewModel.uiState.value.canProposeDraw)
        assertTrue(fakeRepo.proposeDraw(code, "local_user_42").isFailure)

        localVm.stopTurnTimer()
    }

    @Test
    fun draw_proposalRefused_afterOneTurn_samePlayerCanRepropose() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val movementState = createMovementGameState(
            currentPlayer = Player.STICKS,
            moveHistory = listOf("m1", "m2")
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            gameState = movementState
        )
        testScheduler.runCurrent()

        // 1re proposition par local_user_42 (STICKS)
        viewModel.proposeDraw()
        testScheduler.runCurrent()
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)

        // Refus par opponent_99 (STONES) -> le tour reste à STICKS (proposant), qui ne peut pas reproposer avant d'avoir joué
        fakeRepo.respondToDraw(code, accept = false, uid = "opponent_99")
        testScheduler.runCurrent()
        assertNull(flow.value!!.drawOfferedBy)
        assertEquals(Player.STICKS, flow.value!!.gameState.currentPlayer)
        assertFalse(viewModel.uiState.value.canProposeDraw)

        // STICKS joue son coup, puis STONES répond : un tour complet s'est écoulé et le tour revient à STICKS avec moveHistory.size > drawOfferTurnCount
        val afterFullTurn = flow.value!!.gameState.copy(
            currentPlayer = Player.STICKS,
            moveHistory = flow.value!!.gameState.moveHistory + listOf("A1 -> A2", "F5 -> E5")
        )
        fakeRepo.updateGameState(code, afterFullTurn)
        testScheduler.runCurrent()

        // Maintenant STICKS peut à nouveau proposer un match nul
        assertTrue(viewModel.uiState.value.canProposeDraw)
        viewModel.proposeDraw()
        testScheduler.runCurrent()
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)
    }

    @Test
    fun draw_notAvailableInPlacementPhase() = runOnlineTest {
        // En local 2 joueurs (phase de pose par défaut)
        assertEquals(Phase.PLACEMENT, viewModel.uiState.value.gameState.phase)
        assertFalse(viewModel.uiState.value.canProposeDraw)
        viewModel.proposeDraw()
        assertFalse(viewModel.uiState.value.isDrawOfferPending)

        // En ligne en phase de pose
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()
        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES")
        )
        testScheduler.runCurrent()

        assertEquals(Phase.PLACEMENT, viewModel.uiState.value.gameState.phase)
        assertFalse(viewModel.uiState.value.canProposeDraw)
        assertTrue(fakeRepo.proposeDraw(code, "local_user_42").isFailure)
    }

    @Test
    fun draw_notAvailableInVsComputerMode() = runOnlineTest {
        val movementState = createMovementGameState(currentPlayer = Player.STICKS)
        val storage = InMemoryWaliGameStorage()
        storage.saveGame(
            com.example.wali.storage.SavedGame(
                gameState = movementState,
                gameMode = GameMode.VS_COMPUTER,
                aiDifficulty = com.example.wali.ai.AiDifficulty.EASY,
                humanPlayer = Player.STICKS
            )
        )
        val vsAiVm = WaliViewModel(storage = storage)
        testScheduler.runCurrent()
        vsAiVm.resumeSavedGame()
        testScheduler.runCurrent()
        vsAiVm.stopTurnTimer()

        assertEquals(GameMode.VS_COMPUTER, vsAiVm.uiState.value.gameMode)
        assertEquals(Phase.MOVEMENT, vsAiVm.uiState.value.gameState.phase)
        assertFalse(vsAiVm.uiState.value.canProposeDraw)

        vsAiVm.proposeDraw()
        assertFalse(vsAiVm.uiState.value.isDrawOfferPending)
        vsAiVm.stopTurnTimer()
    }

    @Test
    fun doubleWin_loserCapturedZeroPieces_awards2Points() = runOnlineTest {
        val scoreboard = com.example.wali.storage.InMemoryWaliScoreboardRepository()
        val vm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            scoreboardRepository = scoreboard,
            onlineRepository = fakeRepo
        )
        vm.stopTurnTimer()

        vm.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = vm.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0)
        )
        testScheduler.runCurrent()

        // STICKS gagne, STONES (perdant) n'a capturé aucun pion de STICKS -> sticksRemaining == 12
        val winState = createMovementGameState(
            currentPlayer = Player.STICKS,
            sticksRemaining = 12,
            stonesRemaining = 0
        ).copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        assertEquals(0, winState.capturesMadeBy(Player.STONES))
        assertTrue(winState.isDoubleWin(Player.STICKS))

        fakeRepo.updateGameState(code, winState)
        testScheduler.runCurrent()

        assertEquals(2, flow.value!!.statsByUid["local_user_42"])
        assertEquals(0, flow.value!!.statsByUid["opponent_99"])
        assertTrue(vm.uiState.value.isDoubleWin)
        assertEquals(2, scoreboard.getStats().totalWins)
        assertEquals(2, scoreboard.getStats().onlineWins)

        vm.stopTurnTimer()
        vm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun doubleWin_loserCapturedAtLeastOne_awards1Point() = runOnlineTest {
        val scoreboard = com.example.wali.storage.InMemoryWaliScoreboardRepository()
        val vm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            scoreboardRepository = scoreboard,
            onlineRepository = fakeRepo
        )
        vm.stopTurnTimer()

        vm.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = vm.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0)
        )
        testScheduler.runCurrent()

        // STICKS gagne, mais STONES (perdant) a capturé 2 pions de STICKS -> sticksRemaining == 10
        val winState = createMovementGameState(
            currentPlayer = Player.STICKS,
            sticksRemaining = 10,
            stonesRemaining = 0
        ).copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        assertEquals(2, winState.capturesMadeBy(Player.STONES))
        assertFalse(winState.isDoubleWin(Player.STICKS))

        fakeRepo.updateGameState(code, winState)
        testScheduler.runCurrent()

        assertEquals(1, flow.value!!.statsByUid["local_user_42"])
        assertEquals(0, flow.value!!.statsByUid["opponent_99"])
        assertFalse(vm.uiState.value.isDoubleWin)
        assertEquals(1, scoreboard.getStats().totalWins)
        assertEquals(1, scoreboard.getStats().onlineWins)

        vm.stopTurnTimer()
        vm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun doubleWin_forfeitWithZeroCaptures_awards2Points() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        // En phase MOVEMENT, STICKS (qui va déclarer forfait) n'a capturé aucun pion de STONES -> stonesRemaining == 12
        val movementZeroCaptures = createMovementGameState(
            currentPlayer = Player.STICKS,
            sticksRemaining = 9,
            stonesRemaining = 12
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = movementZeroCaptures
        )
        testScheduler.runCurrent()

        // STICKS (local_user_42) déclare forfait avec 0 capture
        viewModel.declareForfeit()
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, session.status)
        assertEquals(Player.STONES, session.winner)
        assertEquals(0, session.statsByUid["local_user_42"])
        assertEquals(2, session.statsByUid["opponent_99"])
        assertTrue(viewModel.uiState.value.isDoubleWin)
    }

    @Test
    fun doubleWin_forfeitWithAtLeastOneCapture_awards1Point() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        // STICKS (qui va déclarer forfait) a capturé 1 pion de STONES -> stonesRemaining == 11
        val movementOneCapture = createMovementGameState(
            currentPlayer = Player.STICKS,
            sticksRemaining = 9,
            stonesRemaining = 11
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = movementOneCapture
        )
        testScheduler.runCurrent()

        // STICKS (local_user_42) déclare forfait après avoir capturé 1 pion
        viewModel.declareForfeit()
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, session.status)
        assertEquals(Player.STONES, session.winner)
        assertEquals(0, session.statsByUid["local_user_42"])
        assertEquals(1, session.statsByUid["opponent_99"])
        assertFalse(viewModel.uiState.value.isDoubleWin)
    }

    @Test
    fun doubleWin_timeoutWithZeroCaptures_awards2Points() = runOnlineTest {
        val scoreboard = com.example.wali.storage.InMemoryWaliScoreboardRepository()
        val vm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            scoreboardRepository = scoreboard,
            onlineRepository = fakeRepo
        )
        vm.stopTurnTimer()

        vm.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = vm.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        // L'adversaire STONES est en timeout/déconnexion et n'a capturé aucun pion de STICKS -> sticksRemaining == 12
        val movementZeroCapturesByLoser = createMovementGameState(
            currentPlayer = Player.STONES,
            sticksRemaining = 12,
            stonesRemaining = 8
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = movementZeroCapturesByLoser
        )
        testScheduler.runCurrent()

        vm.declareOpponentAbandoned()
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, session.status)
        assertEquals(Player.STICKS, session.winner)
        assertEquals(2, session.statsByUid["local_user_42"])
        assertEquals(0, session.statsByUid["opponent_99"])
        assertTrue(vm.uiState.value.isDoubleWin)
        assertEquals(2, scoreboard.getStats().totalWins)

        vm.stopTurnTimer()
        vm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun doubleWin_timeoutWithAtLeastOneCapture_awards1Point() = runOnlineTest {
        val scoreboard = com.example.wali.storage.InMemoryWaliScoreboardRepository()
        val vm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            scoreboardRepository = scoreboard,
            onlineRepository = fakeRepo
        )
        vm.stopTurnTimer()

        vm.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = vm.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        // L'adversaire STONES est en timeout/déconnexion mais avait capturé 3 pions de STICKS -> sticksRemaining == 9
        val movementWithCapturesByLoser = createMovementGameState(
            currentPlayer = Player.STONES,
            sticksRemaining = 9,
            stonesRemaining = 8
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = movementWithCapturesByLoser
        )
        testScheduler.runCurrent()

        vm.declareOpponentAbandoned()
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, session.status)
        assertEquals(Player.STICKS, session.winner)
        assertEquals(1, session.statsByUid["local_user_42"])
        assertEquals(0, session.statsByUid["opponent_99"])
        assertFalse(vm.uiState.value.isDoubleWin)
        assertEquals(1, scoreboard.getStats().totalWins)

        vm.stopTurnTimer()
        vm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun doubleWin_forfeitDuringPlacementPhase_awards2Points() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Simule plusieurs poses en Phase.PLACEMENT (sticksRemaining et stonesRemaining ont diminué à 9 et 10)
        var stateInPlacement = WaliEngine.initializeGame()
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(0, 0)) // STICKS (11)
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(0, 1)) // STONES (11)
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(1, 0)) // STICKS (10)
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(1, 1)) // STONES (10)
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(2, 2)) // STICKS (9)
        assertEquals(Phase.PLACEMENT, stateInPlacement.phase)
        assertEquals(9, stateInPlacement.sticksRemaining)
        assertEquals(10, stateInPlacement.stonesRemaining)

        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = stateInPlacement
        )
        testScheduler.runCurrent()

        // STICKS déclare forfait en pleine phase de pose : aucune capture n'a pu avoir lieu -> +2 points pour STONES
        viewModel.declareForfeit()
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, session.status)
        assertEquals(Player.STONES, session.winner)
        assertEquals(0, viewModel.uiState.value.gameState.capturesMadeBy(Player.STICKS))
        assertTrue(viewModel.uiState.value.isDoubleWin)
        assertEquals(0, session.statsByUid["local_user_42"])
        assertEquals(2, session.statsByUid["opponent_99"])
    }

    @Test
    fun doubleWin_timeoutDuringPlacementPhase_awards2Points() = runOnlineTest {
        val scoreboard = com.example.wali.storage.InMemoryWaliScoreboardRepository()
        val vm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            scoreboardRepository = scoreboard,
            onlineRepository = fakeRepo
        )
        vm.stopTurnTimer()

        vm.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = vm.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Plusieurs pions ont été posés en Phase.PLACEMENT (sticksRemaining = 9, stonesRemaining = 10)
        var stateInPlacement = WaliEngine.initializeGame()
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(0, 0)) // STICKS (11)
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(0, 1)) // STONES (11)
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(1, 0)) // STICKS (10)
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(1, 1)) // STONES (10)
        stateInPlacement = WaliEngine.placePiece(stateInPlacement, Position(2, 2)) // STICKS (9), tour à STONES
        assertEquals(Phase.PLACEMENT, stateInPlacement.phase)
        assertEquals(Player.STONES, stateInPlacement.currentPlayer)

        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = stateInPlacement
        )
        testScheduler.runCurrent()

        // Timeout / abandon de STONES en phase de pose -> +2 points pour STICKS
        vm.declareOpponentAbandoned()
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(OnlineGameStatus.ABANDONED, session.status)
        assertEquals(Player.STICKS, session.winner)
        assertEquals(0, vm.uiState.value.gameState.capturesMadeBy(Player.STONES))
        assertTrue(vm.uiState.value.isDoubleWin)
        assertEquals(2, session.statsByUid["local_user_42"])
        assertEquals(0, session.statsByUid["opponent_99"])
        assertEquals(2, scoreboard.getStats().totalWins)

        vm.stopTurnTimer()
        vm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun doubleWin_blockedAtTransitionToMovement_awards2Points() = runOnlineTest {
        val scoreboard = com.example.wali.storage.InMemoryWaliScoreboardRepository()
        val vm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            scoreboardRepository = scoreboard,
            onlineRepository = fakeRepo
        )
        vm.stopTurnTimer()

        vm.startCreateOnlineGame(isPublic = true, chosenSide = Player.STONES)
        testScheduler.runCurrent()

        val code = vm.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // Construit un plateau où 23 pions sont déjà posés (12 STICKS, 11 STONES) et où toutes les
        // 6 cases vides restantes après la 24e pose (Position(0, 2)) sont sur la ligne 4,
        // entièrement isolées des pions STICKS par la ligne 3 occupée par STONES,
        // de sorte que STICKS (premier joueur en MOVEMENT) est immédiatement bloqué dès transitionToMovement.
        val board = Position.allPositions.associateWith<Position, Player?> { null }.toMutableMap()
        val sticksPositions = listOf(
            Position(0, 0), Position(0, 1), Position(0, 3), Position(0, 4),
            Position(1, 0), Position(1, 2), Position(1, 3), Position(1, 5),
            Position(2, 1), Position(2, 2), Position(2, 4), Position(2, 5)
        )
        val stonesPlacedPositions = listOf(
            Position(0, 5),
            Position(1, 1), Position(1, 4),
            Position(2, 0), Position(2, 3),
            Position(3, 0), Position(3, 1), Position(3, 2), Position(3, 3), Position(3, 4), Position(3, 5)
        )
        sticksPositions.forEach { board[it] = Player.STICKS }
        stonesPlacedPositions.forEach { board[it] = Player.STONES }
        // La 12e pose de STONES en Position(0, 2) est entourée de STICKS ((0,1), (0,3), (1,2)),
        // donc ne forme aucune ligne de 3 pour STONES, et complète les 24 poses.
        val preLastPlacementState = GameState(
            board = board,
            currentPlayer = Player.STONES,
            phase = Phase.PLACEMENT,
            sticksRemaining = 0,
            stonesRemaining = 1,
            winner = null,
            usedLines = emptySet(),
            moveHistory = listOf("STICKS posé en A1")
        )

        val afterLastPlacement = WaliEngine.placePiece(preLastPlacementState, Position(0, 2))
        // Vérifie que transitionToMovement a détecté le blocage immédiat de STICKS et donné la victoire à STONES
        assertEquals(Phase.GAME_OVER, afterLastPlacement.phase)
        assertEquals(Player.STONES, afterLastPlacement.winner)
        assertEquals(12, afterLastPlacement.sticksRemaining)
        assertEquals(12, afterLastPlacement.stonesRemaining)
        assertEquals(0, afterLastPlacement.capturesMadeBy(Player.STICKS))
        assertTrue(afterLastPlacement.isDoubleWin(Player.STONES))

        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            creatorPlayer = Player.STONES,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STICKS,
            players = mapOf("local_user_42" to "STONES", "opponent_99" to "STICKS"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            gameState = preLastPlacementState
        )
        testScheduler.runCurrent()

        fakeRepo.updateGameState(code, afterLastPlacement)
        testScheduler.runCurrent()

        val session = flow.value!!
        assertEquals(OnlineGameStatus.FINISHED, session.status)
        assertEquals(Player.STONES, session.winner)
        assertEquals(2, session.statsByUid["local_user_42"])
        assertEquals(0, session.statsByUid["opponent_99"])
        assertTrue(vm.uiState.value.isDoubleWin)
        assertEquals(2, scoreboard.getStats().totalWins)

        vm.stopTurnTimer()
        vm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun draw_proposerCannotAcceptOwnOffer() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val movementState = createMovementGameState(currentPlayer = Player.STICKS)
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            gameState = movementState
        )
        testScheduler.runCurrent()

        // Le joueur local ("local_user_42") propose un match nul
        viewModel.proposeDraw()
        testScheduler.runCurrent()
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)

        // Le proposant tente d'accepter sa propre offre (via le repository et via le ViewModel)
        val repoAcceptAttempt = fakeRepo.respondToDraw(code, accept = true, uid = "local_user_42")
        assertTrue(repoAcceptAttempt.isFailure)

        viewModel.acceptDraw()
        testScheduler.runCurrent()

        // La partie doit rester ONGOING et l'offre toujours en attente pour l'adversaire
        assertEquals(OnlineGameStatus.ONGOING, flow.value!!.status)
        assertEquals(Phase.MOVEMENT, flow.value!!.gameState.phase)
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)
    }

    @Test
    fun draw_proposerCannotRefuseOwnOffer() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val movementState = createMovementGameState(currentPlayer = Player.STICKS)
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            gameState = movementState
        )
        testScheduler.runCurrent()

        // Le joueur local ("local_user_42") propose un match nul
        viewModel.proposeDraw()
        testScheduler.runCurrent()
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)

        // Le proposant tente de refuser sa propre offre (via le repository et via le ViewModel)
        val repoRefuseAttempt = fakeRepo.respondToDraw(code, accept = false, uid = "local_user_42")
        assertTrue(repoRefuseAttempt.isFailure)

        viewModel.refuseDraw()
        testScheduler.runCurrent()

        // L'offre reste en attente et le tour n'a pas changé
        assertEquals(OnlineGameStatus.ONGOING, flow.value!!.status)
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)
        assertEquals(Player.STICKS, flow.value!!.gameState.currentPlayer)
    }

    @Test
    fun draw_spectatorCannotProposeOrRespond() = runOnlineTest {
        val creatorRepo = FakeWaliOnlineRepository("creator_1")
        val created = creatorRepo.createGame(
            code = "DRAWS9",
            isPublic = true,
            creatorSide = Player.STICKS,
            initialGameState = WaliEngine.initializeGame()
        ).getOrThrow()

        val movementState = createMovementGameState(currentPlayer = Player.STICKS)
        creatorRepo.games["DRAWS9"]!!.value = created.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_2",
            opponentPlayer = Player.STONES,
            players = mapOf("creator_1" to "STICKS", "opponent_2" to "STONES"),
            statsByUid = mapOf("creator_1" to 0, "opponent_2" to 0),
            gameState = movementState
        )

        val spectatorRepo = FakeWaliOnlineRepository("spectator_99").also {
            it.games.putAll(creatorRepo.games)
        }
        val spectatorVm = WaliViewModel(
            storage = InMemoryWaliGameStorage(),
            onlineRepository = spectatorRepo
        )
        spectatorVm.stopTurnTimer()

        spectatorVm.startSpectateOnlineGame(created.spectatorCode!!)
        testScheduler.runCurrent()

        assertTrue(spectatorVm.uiState.value.isSpectator)
        assertFalse(spectatorVm.uiState.value.canProposeDraw)
        assertFalse(spectatorVm.uiState.value.isDrawOfferedToMe)

        // 1. Le spectateur tente de proposer un match nul (via ViewModel et via Repository) -> rejeté
        spectatorVm.proposeDraw()
        testScheduler.runCurrent()
        assertNull(creatorRepo.games["DRAWS9"]!!.value!!.drawOfferedBy)
        assertTrue(spectatorRepo.proposeDraw("DRAWS9", "spectator_99").isFailure)

        // 2. Un joueur actif ("creator_1") propose un match nul
        assertTrue(creatorRepo.proposeDraw("DRAWS9", "creator_1").isSuccess)
        testScheduler.runCurrent()
        assertEquals("creator_1", creatorRepo.games["DRAWS9"]!!.value!!.drawOfferedBy)

        // Le spectateur ne voit pas la boîte de dialogue d'acceptation/refus et ses appels sont rejetés
        assertFalse(spectatorVm.uiState.value.isDrawOfferedToMe)
        spectatorVm.acceptDraw()
        spectatorVm.refuseDraw()
        testScheduler.runCurrent()
        assertTrue(spectatorRepo.respondToDraw("DRAWS9", accept = true, uid = "spectator_99").isFailure)
        assertTrue(spectatorRepo.respondToDraw("DRAWS9", accept = false, uid = "spectator_99").isFailure)

        // La proposition reste intacte et la partie continue en ONGOING
        assertEquals(OnlineGameStatus.ONGOING, creatorRepo.games["DRAWS9"]!!.value!!.status)
        assertEquals("creator_1", creatorRepo.games["DRAWS9"]!!.value!!.drawOfferedBy)

        spectatorVm.stopTurnTimer()
        spectatorVm.stopOnlineInactivityMonitoring()
    }

    @Test
    fun draw_cannotProposeWhileCaptureIsPending() = runOnlineTest {
        val capturePendingState = createMovementGameState(
            currentPlayer = Player.STICKS,
            moveHistory = listOf("m1", "m2")
        ).copy(pendingCaptures = 1)

        // 1. En mode local 2 joueurs : canProposeDraw est false et proposeDraw() est ignoré
        val localStorage = InMemoryWaliGameStorage()
        localStorage.saveGame(
            com.example.wali.storage.SavedGame(
                gameState = capturePendingState,
                gameMode = GameMode.TWO_PLAYERS,
                aiDifficulty = com.example.wali.ai.AiDifficulty.MEDIUM,
                humanPlayer = Player.STICKS
            )
        )
        val localVm = WaliViewModel(storage = localStorage)
        testScheduler.runCurrent()
        localVm.resumeSavedGame()
        testScheduler.runCurrent()
        localVm.stopTurnTimer()

        assertEquals(1, localVm.uiState.value.gameState.pendingCaptures)
        assertFalse(localVm.uiState.value.canProposeDraw)
        localVm.proposeDraw()
        assertNull(localVm.uiState.value.localDrawOfferedBy)
        localVm.stopTurnTimer()

        // 2. En mode en ligne : canProposeDraw est false et proposeDraw côté dépôt rejette l'appel
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()
        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            gameState = capturePendingState
        )
        testScheduler.runCurrent()

        assertEquals(1, viewModel.uiState.value.gameState.pendingCaptures)
        assertFalse(viewModel.uiState.value.canProposeDraw)
        viewModel.proposeDraw()
        testScheduler.runCurrent()
        assertNull(flow.value!!.drawOfferedBy)
        assertTrue(fakeRepo.proposeDraw(code, "local_user_42").isFailure)
    }

    @Test
    fun draw_refusedWhileCapturePending_preservesPendingCapture() = runOnlineTest {
        val capturePendingState = createMovementGameState(
            currentPlayer = Player.STICKS,
            moveHistory = listOf("m1", "m2")
        ).copy(pendingCaptures = 1)

        // 1. En mode local 2 joueurs : si une offre était en attente avec pendingCaptures = 1, un refus préserve pendingCaptures = 1 et currentPlayer = STICKS
        val localStorage = InMemoryWaliGameStorage()
        localStorage.saveGame(
            com.example.wali.storage.SavedGame(
                gameState = capturePendingState.copy(pendingCaptures = 0),
                gameMode = GameMode.TWO_PLAYERS,
                aiDifficulty = com.example.wali.ai.AiDifficulty.MEDIUM,
                humanPlayer = Player.STICKS
            )
        )
        val localVm = WaliViewModel(storage = localStorage)
        testScheduler.runCurrent()
        localVm.resumeSavedGame()
        testScheduler.runCurrent()
        localVm.stopTurnTimer()

        localVm.proposeDraw()
        assertEquals(Player.STICKS, localVm.uiState.value.localDrawOfferedBy)

        // Même si l'état porte une capture en attente au moment du refus, respondToDraw(false) ne détruit ni pendingCaptures ni currentPlayer
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()
        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            gameState = capturePendingState,
            drawOfferedBy = "local_user_42",
            drawOfferTurnCount = 2
        )
        testScheduler.runCurrent()

        val result = fakeRepo.respondToDraw(code, accept = false, uid = "opponent_99")
        testScheduler.runCurrent()
        assertTrue(result.isSuccess)
        assertNull(flow.value!!.drawOfferedBy)
        assertEquals(1, flow.value!!.gameState.pendingCaptures)
        assertEquals(Player.STICKS, flow.value!!.gameState.currentPlayer)
        assertEquals(1, viewModel.uiState.value.gameState.pendingCaptures)

        localVm.refuseDraw()
        assertNull(localVm.uiState.value.localDrawOfferedBy)
        assertEquals(Player.STICKS, localVm.uiState.value.gameState.currentPlayer)
        localVm.stopTurnTimer()
    }

    @Test
    fun draw_cannotReassignOfferToOpponentWhilePending() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!
        val movementState = createMovementGameState(
            currentPlayer = Player.STICKS,
            moveHistory = listOf("m1", "m2")
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            gameState = movementState
        )
        testScheduler.runCurrent()

        // local_user_42 propose un match nul
        assertTrue(fakeRepo.proposeDraw(code, "local_user_42").isSuccess)
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)

        // Tant que l'offre est en attente, toute tentative de réattribuer drawOfferedBy à opponent_99 échoue
        assertTrue(fakeRepo.proposeDraw(code, "opponent_99").isFailure)
        assertTrue(fakeRepo.proposeDraw(code, "local_user_42").isFailure)
        assertEquals("local_user_42", flow.value!!.drawOfferedBy)

        // Et local_user_42 ne peut jamais accepter lui-même l'offre en cours
        assertTrue(fakeRepo.respondToDraw(code, accept = true, uid = "local_user_42").isFailure)
        assertEquals(OnlineGameStatus.ONGOING, flow.value!!.status)
    }

    @Test
    fun onlineSession_missingCreatorOrOpponentPlayer_isHandledCleanlyWithoutBlocking() = runOnlineTest {
        // 1. Vérifie que WaliEngine.capturePiece incrémente explicitement capturedBySticks / capturedByStones
        // et que la sérialisation/désérialisation rétrocompatible charge à 0 si les champs sont absents
        val baseMovement = createMovementGameState(
            currentPlayer = Player.STICKS,
            sticksRemaining = 12,
            stonesRemaining = 12,
            capturedBySticks = 0,
            capturedByStones = 0
        ).copy(pendingCaptures = 1)

        val afterSticksCapture = WaliEngine.capturePiece(baseMovement, Position(4, 5))
        assertEquals(1, afterSticksCapture.capturedBySticks)
        assertEquals(0, afterSticksCapture.capturedByStones)
        assertEquals(1, afterSticksCapture.capturesMadeBy(Player.STICKS))
        assertEquals(0, afterSticksCapture.capturesMadeBy(Player.STONES))

        val serialized = com.example.wali.storage.WaliGameSerializer.serializeGameState(afterSticksCapture)
        val roundTrip = com.example.wali.storage.WaliGameSerializer.deserializeGameState(serialized)!!
        assertEquals(1, roundTrip.capturedBySticks)
        assertEquals(0, roundTrip.capturedByStones)

        val legacyJson = org.json.JSONObject(serialized).apply {
            getJSONObject("gameState").apply {
                remove("capturedBySticks")
                remove("capturedByStones")
            }
        }.toString()
        val deserializedLegacy = com.example.wali.storage.WaliGameSerializer.deserializeGameState(legacyJson)!!
        assertEquals(0, deserializedLegacy.capturedBySticks)
        assertEquals(0, deserializedLegacy.capturedByStones)

        // 2. Partie en ligne dont le document/session ne contient ni opponentPlayer ni entrées dans players :
        // les deux joueurs doivent pouvoir résoudre leur camp, jouer leurs coups et proposer/répondre au match nul sans blocage
        val code = "NOPLYR"
        val now = System.currentTimeMillis()
        val incompletePlayerFieldsSession = OnlineGameSession(
            code = code,
            spectatorCode = "SNOPLYR",
            isPublic = true,
            status = OnlineGameStatus.ONGOING,
            creatorUid = "creator_1",
            creatorPlayer = Player.STICKS,
            opponentUid = "local_user_42",
            opponentPlayer = null,
            players = emptyMap(),
            gameState = WaliEngine.initializeGame().copy(currentPlayer = Player.STONES),
            lastMoveAt = now,
            createdAt = now,
            statsByUid = mapOf("creator_1" to 0, "local_user_42" to 0)
        )
        fakeRepo.games[code] = MutableStateFlow(incompletePlayerFieldsSession)

        // Le joueur local est l'adversaire ("local_user_42") et opponentPlayer est null dans la session :
        // getPlayerRole doit résoudre Player.STONES (creatorPlayer.opponent) sans retourner null
        assertEquals(Player.STICKS, incompletePlayerFieldsSession.getPlayerRole("creator_1"))
        assertEquals(Player.STONES, incompletePlayerFieldsSession.getPlayerRole("local_user_42"))
        assertTrue(incompletePlayerFieldsSession.isPlayerTurn("local_user_42"))
        assertFalse(incompletePlayerFieldsSession.isPlayerTurn("creator_1"))

        // Reprise de la partie par "local_user_42" (opponent) et exécution d'un coup sur le plateau sans blocage
        viewModel.onAppForegrounded()
        testScheduler.runCurrent()
        viewModel.resumePendingOnlineGame()
        testScheduler.runCurrent()

        assertEquals(Player.STONES, viewModel.uiState.value.localPlayerRole)
        val targetPos = Position(1, 1)
        viewModel.onCellClicked(targetPos)
        testScheduler.runCurrent()

        assertEquals(Player.STONES, fakeRepo.games[code]!!.value!!.gameState.board[targetPos])
        assertEquals(Player.STICKS, fakeRepo.games[code]!!.value!!.gameState.currentPlayer)

        // Même avec opponentPlayer == null et players vide, proposeDraw et respondToDraw fonctionnent sans blocage
        fakeRepo.games[code]!!.value = fakeRepo.games[code]!!.value!!.copy(
            opponentPlayer = null,
            players = emptyMap(),
            gameState = createMovementGameState(currentPlayer = Player.STICKS)
        )
        testScheduler.runCurrent()

        assertTrue(fakeRepo.proposeDraw(code, "creator_1").isSuccess)
        assertTrue(fakeRepo.respondToDraw(code, accept = false, uid = "local_user_42").isSuccess)
        assertNull(fakeRepo.games[code]!!.value!!.drawOfferedBy)
        assertEquals(Player.STICKS, fakeRepo.games[code]!!.value!!.gameState.currentPlayer)
    }

    @Test
    fun rematch_resetsCaptureCountersToZero() = runOnlineTest {
        viewModel.startCreateOnlineGame(isPublic = true, chosenSide = Player.STICKS)
        testScheduler.runCurrent()

        val code = viewModel.uiState.value.waitingForOpponentCode!!
        val flow = fakeRepo.games[code]!!

        // 1re partie en ligne : chaque joueur effectue de vraies captures via WaliEngine.capturePiece
        val stateBeforeSticksCapture = createMovementGameState(
            currentPlayer = Player.STICKS,
            sticksRemaining = 12,
            stonesRemaining = 12,
            capturedBySticks = 0,
            capturedByStones = 0
        ).copy(
            pendingCaptures = 1,
            usedLines = setOf(com.example.wali.model.Line(setOf(Position(0, 0), Position(0, 1), Position(0, 2))))
        )
        val afterSticksCapture = WaliEngine.capturePiece(stateBeforeSticksCapture, Position(4, 5))
        assertEquals(1, afterSticksCapture.capturedBySticks)

        val stateBeforeStonesCapture = afterSticksCapture.copy(
            currentPlayer = Player.STONES,
            pendingCaptures = 1
        )
        val afterStonesCapture = WaliEngine.capturePiece(stateBeforeStonesCapture, Position(0, 0))
        assertEquals(1, afterStonesCapture.capturedBySticks)
        assertEquals(1, afterStonesCapture.capturedByStones)

        // Fin de la manche 1 avec compteurs > 0, usedLines non vide, moveHistory non vide et drawOfferTurnCount > 0
        val finishedRound1 = afterStonesCapture.copy(
            phase = Phase.GAME_OVER,
            winner = Player.STICKS
        )
        flow.value = flow.value!!.copy(
            status = OnlineGameStatus.ONGOING,
            opponentUid = "opponent_99",
            opponentPlayer = Player.STONES,
            players = mapOf("local_user_42" to "STICKS", "opponent_99" to "STONES"),
            statsByUid = mapOf("local_user_42" to 0, "opponent_99" to 0),
            drawOfferTurnCount = 4
        )
        fakeRepo.updateGameState(code, finishedRound1)
        testScheduler.runCurrent()

        assertEquals(OnlineGameStatus.FINISHED, flow.value!!.status)
        assertEquals(1, flow.value!!.gameState.capturedBySticks)
        assertEquals(1, flow.value!!.gameState.capturedByStones)
        // Les deux joueurs avaient capturé >= 1 pion -> victoire classique (+1 point)
        assertEquals(1, flow.value!!.statsByUid["local_user_42"])

        // Les deux joueurs acceptent la revanche
        viewModel.requestOnlineRematch()
        fakeRepo.requestRematchAsUser(code, "opponent_99")
        testScheduler.runCurrent()

        // Vérifie que la manche 2 repart avec capturedBySticks == 0, capturedByStones == 0 et tous les états transitoires remis à zéro
        val rematchSession = flow.value!!
        val rematchState = rematchSession.gameState
        assertEquals(OnlineGameStatus.ONGOING, rematchSession.status)
        assertEquals(2, rematchSession.gamesPlayed)
        assertEquals(Phase.PLACEMENT, rematchState.phase)
        assertEquals(0, rematchState.capturedBySticks)
        assertEquals(0, rematchState.capturedByStones)
        assertEquals(0, rematchState.capturesMadeBy(Player.STICKS))
        assertEquals(0, rematchState.capturesMadeBy(Player.STONES))
        assertEquals(0, rematchState.pendingCaptures)
        assertEquals(0, rematchState.sticksPendingPlacement)
        assertEquals(0, rematchState.stonesPendingPlacement)
        assertTrue(rematchState.moveHistory.isEmpty())
        assertTrue(rematchState.usedLines.isEmpty())
        assertNull(rematchSession.drawOfferedBy)
        assertEquals(-1, rematchSession.drawOfferTurnCount)

        val uiGameState = viewModel.uiState.value.gameState
        assertEquals(0, uiGameState.capturedBySticks)
        assertEquals(0, uiGameState.capturedByStones)
        assertNull(viewModel.uiState.value.localDrawOfferedBy)
        assertEquals(-1, viewModel.uiState.value.localDrawOfferTurnCount)
        assertNull(viewModel.uiState.value.localLastDrawProposer)
    }
}
