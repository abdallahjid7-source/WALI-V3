package com.example.wali

import com.example.wali.ai.AiDifficulty
import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Line
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.model.WaliRuleOptions
import com.example.wali.storage.InMemoryWaliGameStorage
import com.example.wali.storage.SavedGame
import com.example.wali.storage.WaliGameSerializer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WaliSerializationTest {

    @Test
    fun initialGameState_roundTrip_isIdentical() {
        val initialGameState = WaliEngine.initializeGame()
        val original = SavedGame(
            gameState = initialGameState,
            gameMode = GameMode.TWO_PLAYERS,
            humanPlayer = Player.STICKS,
            aiDifficulty = AiDifficulty.EASY
        )

        val json = WaliGameSerializer.serialize(original)
        assertNotNull(json)
        assertTrue(json.isNotBlank())

        val restored = WaliGameSerializer.deserialize(json)
        assertNotNull(restored)

        assertEquals(original.gameMode, restored!!.gameMode)
        assertEquals(original.humanPlayer, restored.humanPlayer)
        assertEquals(original.aiDifficulty, restored.aiDifficulty)

        val oState = original.gameState
        val rState = restored.gameState

        assertEquals(oState.currentPlayer, rState.currentPlayer)
        assertEquals(oState.phase, rState.phase)
        assertEquals(oState.sticksRemaining, rState.sticksRemaining)
        assertEquals(oState.stonesRemaining, rState.stonesRemaining)
        assertEquals(oState.winner, rState.winner)
        assertEquals(oState.pendingCaptures, rState.pendingCaptures)
        assertEquals(oState.usedLines, rState.usedLines)
        assertEquals(oState.moveHistory, rState.moveHistory)

        // Vérification case par case du plateau (30 cases)
        for (pos in Position.allPositions) {
            assertEquals("Case $pos", oState.board[pos], rState.board[pos])
        }
    }

    @Test
    fun complexGameState_withUsedLinesAndHistory_isStrictlyIdentical() {
        val board = Position.allPositions.associateWith { null as Player? }.toMutableMap()
        // Placement de quelques pièces
        board[Position(0, 0)] = Player.STICKS
        board[Position(0, 1)] = Player.STICKS
        board[Position(0, 2)] = Player.STICKS
        board[Position(1, 0)] = Player.STONES
        board[Position(2, 3)] = Player.STONES

        val line1 = Line(setOf(Position(0, 0), Position(0, 1), Position(0, 2)))
        val line2 = Line(setOf(Position(1, 1), Position(2, 1), Position(3, 1)))
        val usedLines = setOf(line1, line2)

        val history = listOf(
            "POSE: BÂTONNETS en A1",
            "POSE: CAILLOUX en A2",
            "DÉPLACEMENT: A1 -> B1",
            "CAPTURE: BÂTONNETS capture le pion en C3"
        )

        val gameState = GameState(
            board = board,
            currentPlayer = Player.STONES,
            phase = Phase.MOVEMENT,
            sticksRemaining = 8,
            stonesRemaining = 7,
            winner = null,
            usedLines = usedLines,
            moveHistory = history,
            pendingCaptures = 1
        )

        val original = SavedGame(
            gameState = gameState,
            gameMode = GameMode.VS_COMPUTER,
            humanPlayer = Player.STONES,
            aiDifficulty = AiDifficulty.HARD
        )

        val json = WaliGameSerializer.serialize(original)
        val restored = WaliGameSerializer.deserialize(json)

        assertNotNull(restored)
        assertEquals(original.gameMode, restored!!.gameMode)
        assertEquals(original.humanPlayer, restored.humanPlayer)
        assertEquals(original.aiDifficulty, restored.aiDifficulty)

        val rState = restored.gameState
        assertEquals(gameState.currentPlayer, rState.currentPlayer)
        assertEquals(gameState.phase, rState.phase)
        assertEquals(gameState.sticksRemaining, rState.sticksRemaining)
        assertEquals(gameState.stonesRemaining, rState.stonesRemaining)
        assertEquals(gameState.winner, rState.winner)
        assertEquals(gameState.pendingCaptures, rState.pendingCaptures)
        assertEquals(gameState.usedLines, rState.usedLines)
        assertEquals(gameState.moveHistory, rState.moveHistory)

        for (pos in Position.allPositions) {
            assertEquals("Case $pos", gameState.board[pos], rState.board[pos])
        }
    }

    @Test
    fun finishedGame_isNeverOfferedForResume() = runBlocking {
        val storage = InMemoryWaliGameStorage()

        // Partie avec un vainqueur
        val finishedState = GameState(
            board = Position.allPositions.associateWith { null as Player? },
            currentPlayer = Player.STICKS,
            phase = Phase.GAME_OVER,
            sticksRemaining = 5,
            stonesRemaining = 0,
            winner = Player.STICKS,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        val savedGame = SavedGame(
            gameState = finishedState,
            gameMode = GameMode.TWO_PLAYERS
        )

        storage.saveGame(savedGame)

        // Une partie terminée ne doit JAMAIS être proposée à la reprise
        assertFalse(storage.hasActiveSavedGame())

        // Après effacement
        storage.clearSavedGame()
        assertNull(storage.loadSavedGame())
        assertFalse(storage.hasActiveSavedGame())
    }

    @Test
    fun inProgressGame_isOfferedForResume_andClearsCorrectly() = runBlocking {
        val storage = InMemoryWaliGameStorage()
        assertFalse(storage.hasActiveSavedGame())

        val inProgressState = WaliEngine.placePiece(WaliEngine.initializeGame(), Position(0, 0))
        val savedGame = SavedGame(
            gameState = inProgressState,
            gameMode = GameMode.VS_COMPUTER,
            humanPlayer = Player.STICKS,
            aiDifficulty = AiDifficulty.MEDIUM
        )

        storage.saveGame(savedGame)
        assertTrue(storage.hasActiveSavedGame())

        val loaded = storage.loadSavedGame()
        assertNotNull(loaded)
        assertEquals(Player.STICKS, loaded!!.gameState.board[Position(0, 0)])

        // Effacement lors d'une nouvelle partie
        storage.clearSavedGame()
        assertFalse(storage.hasActiveSavedGame())
        assertNull(storage.loadSavedGame())
    }

    @Test
    fun savedGame_duration_roundTrip_preservesElapsedSeconds() {
        val original = SavedGame(
            gameState = WaliEngine.initializeGame(),
            gameMode = GameMode.VS_COMPUTER,
            humanPlayer = Player.STONES,
            aiDifficulty = AiDifficulty.HARD,
            elapsedDurationSeconds = 145L
        )

        val json = WaliGameSerializer.serialize(original)
        val restored = WaliGameSerializer.deserialize(json)
        assertNotNull(restored)
        assertEquals(145L, restored!!.elapsedDurationSeconds)
    }

    @Test
    fun ruleOptions_roundTrip_andLegacyJsonBackwardCompatibility() {
        val withVariants = WaliEngine.initializeGame(
            ruleOptions = WaliRuleOptions(
                mandatoryAlignment = true,
                defeatAtTwoPieces = true
            )
        )
        val json = WaliGameSerializer.serializeGameState(withVariants)
        val restored = WaliGameSerializer.deserializeGameState(json)
        assertNotNull(restored)
        assertTrue(restored!!.ruleOptions.mandatoryAlignment)
        assertTrue(restored.ruleOptions.defeatAtTwoPieces)

        // Un JSON ancien sans les champs mandatoryAlignment et defeatAtTwoPieces se recharge sans planter, variantes à false
        val rootObj = org.json.JSONObject(json)
        val stateObj = rootObj.getJSONObject("gameState")
        stateObj.remove("mandatoryAlignment")
        stateObj.remove("defeatAtTwoPieces")
        val legacyJson = rootObj.toString()

        val restoredLegacy = WaliGameSerializer.deserializeGameState(legacyJson)
        assertNotNull(restoredLegacy)
        assertFalse(restoredLegacy!!.ruleOptions.mandatoryAlignment)
        assertFalse(restoredLegacy.ruleOptions.defeatAtTwoPieces)
    }
}
