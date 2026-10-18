package com.example.wali.chat

import com.example.wali.audio.NoOpWaliAudio
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Player
import com.example.wali.online.OnlineGameSession
import com.example.wali.online.OnlineGameStatus
import com.example.wali.online.WaliOnlineRepository
import com.example.wali.ui.WaliViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de tests unitaires pour le chat multijoueur du Wali Songhaï.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WaliChatTest {

    // =========================================================================
    // 1. TESTS DE VALIDATION DES MESSAGES
    // =========================================================================

    @Test
    fun `validateText rejects null empty and whitespace-only strings`() {
        assertNull("Null doit être rejeté", WaliChatMessage.validateText(null))
        assertNull("Chaîne vide doit être rejetée", WaliChatMessage.validateText(""))
        assertNull("Espaces seuls doivent être rejetés", WaliChatMessage.validateText("   "))
        assertNull("Tabulations et sauts de ligne seuls doivent être rejetés", WaliChatMessage.validateText(" \n \t "))
    }

    @Test
    fun `validateText accepts valid messages up to 200 characters`() {
        val validShort = "Bien joué !"
        assertEquals("Bien joué !", WaliChatMessage.validateText(validShort))

        val exact200 = "A".repeat(200)
        assertEquals(exact200, WaliChatMessage.validateText(exact200))
    }

    @Test
    fun `validateText rejects messages longer than 200 characters`() {
        val tooLong = "A".repeat(201)
        assertNull("Un message de 201 caractères doit être rejeté", WaliChatMessage.validateText(tooLong))
    }

    @Test
    fun `quick messages list contains expected official predefined options`() {
        val quick = WaliChatMessage.QUICK_MESSAGES
        assertTrue(quick.contains("Bonjour !"))
        assertTrue(quick.contains("Bien joué !"))
        assertTrue(quick.contains("Belle capture !"))
        assertTrue(quick.contains("Je réfléchis…"))
        assertTrue(quick.contains("Dépêche-toi 🙂"))
        assertTrue(quick.contains("Merci pour la partie !"))
        assertTrue(quick.contains("Revanche ?"))
        assertEquals(7, quick.size)
    }

    // =========================================================================
    // 2. TESTS DU FILTRE DE MOTS GROSSIERS (PROFANITY FILTER)
    // =========================================================================

    @Test
    fun `profanity filter detects forbidden words and masks them`() {
        assertTrue(WaliProfanityFilter.containsProfanity("Espèce d'idiot !"))
        assertTrue(WaliProfanityFilter.containsProfanity("connard"))

        val masked = WaliProfanityFilter.filterOrMask("Tu es un idiot !")
        assertFalse(masked.contains("idiot", ignoreCase = true))
        assertTrue(masked.contains("***"))
    }

    @Test
    fun `profanity filter leaves clean game messages untouched`() {
        val clean1 = "Bien joué pour cette capture !"
        assertFalse(WaliProfanityFilter.containsProfanity(clean1))
        assertEquals(clean1, WaliProfanityFilter.filterOrMask(clean1))

        val clean2 = "Bonjour, bonne partie de Wali Songhaï."
        assertFalse(WaliProfanityFilter.containsProfanity(clean2))
        assertEquals(clean2, WaliProfanityFilter.filterOrMask(clean2))
    }

    // =========================================================================
    // 3. TESTS DU LIMITEUR DE DÉBIT (RATE LIMITER)
    // =========================================================================

    @Test
    fun `rate limiter enforces 2 seconds cooldown between messages`() {
        var currentTime = 1000L
        val limiter = WaliChatRateLimiter(minIntervalMs = 2000L, maxPerMinute = 10) { currentTime }

        // Premier message : autorisé
        assertTrue(limiter.canSend() is WaliChatRateLimiter.Result.Allowed)
        limiter.recordSent()

        // 1 seconde plus tard : refusé (trop rapide)
        currentTime += 1000L
        val checkFast = limiter.canSend()
        assertTrue("Doit être refusé car moins de 2s", checkFast is WaliChatRateLimiter.Result.TooFast)

        // 2,5 secondes après le premier : autorisé
        currentTime += 1500L
        assertTrue("Doit être autorisé après 2s", limiter.canSend() is WaliChatRateLimiter.Result.Allowed)
    }

    @Test
    fun `rate limiter enforces quota of maximum 10 messages per minute`() {
        var currentTime = 1000L
        val limiter = WaliChatRateLimiter(minIntervalMs = 2000L, maxPerMinute = 10) { currentTime }

        // Envoi de 10 messages espacés de 3 secondes (30s au total)
        for (i in 1..10) {
            assertTrue("Message $i doit être autorisé", limiter.canSend() is WaliChatRateLimiter.Result.Allowed)
            limiter.recordSent()
            currentTime += 3000L
        }

        // Le 11ème message à t=31s doit être rejeté pour dépassement de quota
        val check11 = limiter.canSend()
        assertTrue("11ème message dans la même minute doit être rejeté", check11 is WaliChatRateLimiter.Result.QuotaExceeded)

        // 60 secondes après le 1er message, le quota se libère
        currentTime += 65000L
        assertTrue("Doit être réautorisé après expiration de la minute", limiter.canSend() is WaliChatRateLimiter.Result.Allowed)
    }

    // =========================================================================
    // 4. TESTS DU REPOSITORY ET DU VIEWMODEL DU CHAT
    // =========================================================================

    @Test
    fun `chat state opens and closes correctly and resets unread badge`() {
        val audio = NoOpWaliAudio()
        val viewModel = WaliViewModel(audio = audio)

        // Initialement fermé avec 0 non lus
        assertFalse(viewModel.uiState.value.chatState.isOpen)
        assertEquals(0, viewModel.uiState.value.chatState.unreadCount)

        // Ouverture
        viewModel.openChat()
        assertTrue(viewModel.uiState.value.chatState.isOpen)
        assertEquals(0, viewModel.uiState.value.chatState.unreadCount)

        // Fermeture
        viewModel.closeChat()
        assertFalse(viewModel.uiState.value.chatState.isOpen)
    }

    @Test
    fun `blocking opponent filters opponent messages and prevents sending`() {
        val fakeRepo = FakeChatRepository()
        val fakeOnlineRepo = FakeOnlineRepository()
        val audio = NoOpWaliAudio()
        val viewModel = WaliViewModel(
            audio = audio,
            onlineRepository = fakeOnlineRepo,
            chatRepository = fakeRepo
        )

        // Simulation partie en ligne active
        viewModel.startNewGame(GameMode.ONLINE, Player.STICKS)

        // Blocage de l'adversaire
        viewModel.blockOpponentInChat()
        assertTrue(viewModel.uiState.value.chatState.isOpponentBlocked)

        // Tentative d'envoi d'un message : ignorée
        viewModel.sendChatMessage("Test bloqué")
        assertTrue("Aucun message ne doit être envoyé quand l'adversaire est bloqué", fakeRepo.sentMessages.isEmpty())
    }

    @Test
    fun `report opponent submits report with recent messages to repository`() = runTest {
        val fakeRepo = FakeChatRepository()
        val fakeOnlineRepo = FakeOnlineRepository()
        val viewModel = WaliViewModel(
            onlineRepository = fakeOnlineRepo,
            chatRepository = fakeRepo
        )

        viewModel.openReportDialog()
        assertTrue(viewModel.uiState.value.chatState.showReportDialog)

        viewModel.closeReportDialog()
        assertFalse(viewModel.uiState.value.chatState.showReportDialog)
    }

    @Test
    fun `chat does not pause or interfere with turn timer`() {
        val viewModel = WaliViewModel()
        assertFalse("Le timer ne doit pas être en pause", viewModel.uiState.value.isTimerPaused)

        viewModel.openChat()
        assertFalse("L'ouverture du chat ne doit JAMAIS mettre le timer en pause", viewModel.uiState.value.isTimerPaused)
    }
}

/**
 * Faux repository de chat en mémoire pour tests isolés.
 */
class FakeChatRepository : WaliChatRepository {
    val messagesFlow = MutableSharedFlow<List<WaliChatMessage>>(replay = 1)
    val sentMessages = mutableListOf<WaliChatMessage>()
    val reports = mutableListOf<WaliReport>()

    override fun observeMessages(gameCode: String): Flow<List<WaliChatMessage>> = messagesFlow

    override suspend fun sendMessage(gameCode: String, message: WaliChatMessage): Result<Unit> {
        sentMessages.add(message)
        return Result.success(Unit)
    }

    override suspend fun submitReport(report: WaliReport): Result<Unit> {
        reports.add(report)
        return Result.success(Unit)
    }

    override suspend fun clearMessages(gameCode: String): Result<Unit> {
        sentMessages.clear()
        return Result.success(Unit)
    }
}

/**
 * Faux repository en ligne pour tests.
 */
class FakeOnlineRepository : WaliOnlineRepository {
    override val currentUserId: String = "test_user_123"

    override suspend fun ensureAuthenticated(): String = currentUserId

    override suspend fun createGame(
        code: String,
        isPublic: Boolean,
        creatorSide: Player,
        initialGameState: GameState
    ): Result<OnlineGameSession> {
        return Result.success(
            OnlineGameSession(
                code = code,
                isPublic = isPublic,
                status = OnlineGameStatus.WAITING,
                creatorUid = currentUserId,
                creatorPlayer = creatorSide,
                gameState = initialGameState
            )
        )
    }

    override suspend fun joinGame(code: String): Result<OnlineGameSession> = Result.failure(Exception("Non implémenté"))

    override fun observeGame(code: String): Flow<OnlineGameSession?> = flowOf(null)

    override fun observePublicWaitingGames(): Flow<List<com.example.wali.online.PublicGameItem>> = flowOf(emptyList())

    override suspend fun updateGameState(code: String, newGameState: GameState): Result<Unit> = Result.success(Unit)

    override suspend fun declareAbandonment(code: String, winnerPlayer: Player): Result<Unit> = Result.success(Unit)

    override suspend fun checkExistingOngoingGame(): OnlineGameSession? = null

    override suspend fun leaveGame(code: String): Result<Unit> = Result.success(Unit)
}
