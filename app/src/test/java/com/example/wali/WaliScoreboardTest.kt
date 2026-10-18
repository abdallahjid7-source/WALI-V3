package com.example.wali

import com.example.wali.ai.AiDifficulty
import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameMode
import com.example.wali.model.Player
import com.example.wali.storage.GameOutcome
import com.example.wali.storage.GameResultRecord
import com.example.wali.storage.InMemoryWaliScoreboardRepository
import com.example.wali.storage.ScoreboardStats
import com.example.wali.storage.formatDuration
import com.example.wali.storage.formatTimerDigital
import com.example.wali.ui.WaliViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WaliScoreboardTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scoreboardRepo: InMemoryWaliScoreboardRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        scoreboardRepo = InMemoryWaliScoreboardRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialScoreboard_isEmpty() = runTest {
        val stats = scoreboardRepo.getStats()
        assertEquals(0, stats.totalWins)
        assertEquals(0, stats.totalLosses)
        assertEquals(0, stats.totalGamesPlayed)
        assertEquals(0L, stats.totalDurationSeconds)
        assertNull(stats.fastestWinSeconds)
        assertNull(stats.longestGameSeconds)
        assertEquals(0, stats.currentWinStreak)
        assertEquals(0, stats.bestWinStreak)
        assertEquals(0f, stats.winRatePercent, 0.01f)
    }

    @Test
    fun recordGameResult_updatesWinsAndLossesAndDuration() = runTest {
        // 1ère partie : Victoire de 120 secondes
        val win1 = GameResultRecord(
            outcome = GameOutcome.WIN,
            gameMode = GameMode.VS_COMPUTER,
            aiDifficulty = AiDifficulty.EASY,
            durationSeconds = 120L,
            winner = Player.STICKS,
            reason = "Adversaire éliminé"
        )
        scoreboardRepo.recordGameResult(win1)

        var stats = scoreboardRepo.getStats()
        assertEquals(1, stats.totalWins)
        assertEquals(0, stats.totalLosses)
        assertEquals(1, stats.totalGamesPlayed)
        assertEquals(120L, stats.totalDurationSeconds)
        assertEquals(120L, stats.fastestWinSeconds)
        assertEquals(1, stats.currentWinStreak)
        assertEquals(1, stats.bestWinStreak)
        assertEquals(1, stats.vsComputerWins)
        assertEquals(1, stats.easyAiWins)
        assertEquals(100f, stats.winRatePercent, 0.01f)

        // 2ème partie : Défaite de 180 secondes
        val loss1 = GameResultRecord(
            outcome = GameOutcome.LOSS,
            gameMode = GameMode.VS_COMPUTER,
            aiDifficulty = AiDifficulty.MEDIUM,
            durationSeconds = 180L,
            winner = Player.STONES,
            reason = "Adversaire bloqué"
        )
        scoreboardRepo.recordGameResult(loss1)

        stats = scoreboardRepo.getStats()
        assertEquals(1, stats.totalWins)
        assertEquals(1, stats.totalLosses)
        assertEquals(2, stats.totalGamesPlayed)
        assertEquals(300L, stats.totalDurationSeconds)
        assertEquals(120L, stats.fastestWinSeconds)
        assertEquals(180L, stats.longestGameSeconds)
        assertEquals(0, stats.currentWinStreak)
        assertEquals(1, stats.bestWinStreak)
        assertEquals(1, stats.vsComputerLosses)
        assertEquals(1, stats.mediumAiLosses)
        assertEquals(50f, stats.winRatePercent, 0.01f)
        assertEquals(150L, stats.averageGameDurationSeconds)

        // 3ème partie : Victoire rapide de 75 secondes contre IA difficile
        val win2 = GameResultRecord(
            outcome = GameOutcome.WIN,
            gameMode = GameMode.VS_COMPUTER,
            aiDifficulty = AiDifficulty.HARD,
            durationSeconds = 75L,
            winner = Player.STICKS,
            reason = "Adversaire éliminé"
        )
        scoreboardRepo.recordGameResult(win2)

        stats = scoreboardRepo.getStats()
        assertEquals(2, stats.totalWins)
        assertEquals(1, stats.totalLosses)
        assertEquals(3, stats.totalGamesPlayed)
        assertEquals(375L, stats.totalDurationSeconds)
        assertEquals(75L, stats.fastestWinSeconds) // Nouveau record de vitesse !
        assertEquals(1, stats.currentWinStreak)
        assertEquals(1, stats.bestWinStreak)
        assertEquals(2, stats.vsComputerWins)
        assertEquals(1, stats.hardAiWins)
        assertEquals(66.66f, stats.winRatePercent, 0.5f)
    }

    @Test
    fun winStreak_tracksCurrentAndBestCorrectly() = runTest {
        // 3 victoires d'affilée
        repeat(3) {
            scoreboardRepo.recordGameResult(
                GameResultRecord(
                    outcome = GameOutcome.WIN,
                    gameMode = GameMode.ONLINE,
                    durationSeconds = 60L,
                    winner = Player.STICKS
                )
            )
        }

        var stats = scoreboardRepo.getStats()
        assertEquals(3, stats.currentWinStreak)
        assertEquals(3, stats.bestWinStreak)

        // 1 défaite -> série courante remise à zéro, mais meilleure conservée à 3
        scoreboardRepo.recordGameResult(
            GameResultRecord(
                outcome = GameOutcome.LOSS,
                gameMode = GameMode.ONLINE,
                durationSeconds = 90L,
                winner = Player.STONES
            )
        )

        stats = scoreboardRepo.getStats()
        assertEquals(0, stats.currentWinStreak)
        assertEquals(3, stats.bestWinStreak)

        // 4 victoires d'affilée -> nouvelle meilleure série de 4
        repeat(4) {
            scoreboardRepo.recordGameResult(
                GameResultRecord(
                    outcome = GameOutcome.WIN,
                    gameMode = GameMode.ONLINE,
                    durationSeconds = 50L,
                    winner = Player.STICKS
                )
            )
        }

        stats = scoreboardRepo.getStats()
        assertEquals(4, stats.currentWinStreak)
        assertEquals(4, stats.bestWinStreak)
    }

    @Test
    fun recentGamesHistory_recordsChronologically() = runTest {
        val game1 = GameResultRecord(
            outcome = GameOutcome.WIN,
            gameMode = GameMode.VS_COMPUTER,
            durationSeconds = 100L,
            winner = Player.STICKS
        )
        val game2 = GameResultRecord(
            outcome = GameOutcome.LOSS,
            gameMode = GameMode.ONLINE,
            durationSeconds = 200L,
            winner = Player.STONES
        )

        scoreboardRepo.recordGameResult(game1)
        scoreboardRepo.recordGameResult(game2)

        val recent = scoreboardRepo.recentGamesFlow.first()
        assertEquals(2, recent.size)
        // La plus récente (game2) doit être en premier
        assertEquals(game2.id, recent[0].id)
        assertEquals(game1.id, recent[1].id)
    }

    @Test
    fun resetScoreboard_clearsAllStats() = runTest {
        scoreboardRepo.recordGameResult(
            GameResultRecord(
                outcome = GameOutcome.WIN,
                gameMode = GameMode.VS_COMPUTER,
                durationSeconds = 120L,
                winner = Player.STICKS
            )
        )
        assertEquals(1, scoreboardRepo.getStats().totalWins)

        scoreboardRepo.resetScoreboard()
        val stats = scoreboardRepo.getStats()
        assertEquals(0, stats.totalWins)
        assertEquals(0, stats.totalLosses)
        assertEquals(0L, stats.totalDurationSeconds)
        assertTrue(scoreboardRepo.recentGamesFlow.first().isEmpty())
    }

    @Test
    fun durationFormatters_formatCorrectly() {
        assertEquals("0s", formatDuration(0L))
        assertEquals("45s", formatDuration(45L))
        assertEquals("3m 15s", formatDuration(195L))
        assertEquals("1h 10m", formatDuration(4200L))

        assertEquals("00:00", formatTimerDigital(0L))
        assertEquals("01:25", formatTimerDigital(85L))
        assertEquals("1:05:30", formatTimerDigital(3930L))
    }

    @Test
    fun viewModel_recordsGameOverResult_intoScoreboard() = runTest {
        val vm = WaliViewModel(
            scoreboardRepository = scoreboardRepo
        )
        vm.stopTurnTimer()

        // Définir un état gagnant
        val finalState = WaliEngine.initializeGame().copy(
            winner = Player.STICKS
        )

        vm.checkRecordGameOverResult(finalState)
        advanceUntilIdle()

        val stats = scoreboardRepo.getStats()
        assertEquals(1, stats.totalGamesPlayed)
        // En mode TWO_PLAYERS, l'outcome est COMPLETED (parties amicales partagées)
        assertEquals(1, stats.twoPlayersGames)
    }
}
