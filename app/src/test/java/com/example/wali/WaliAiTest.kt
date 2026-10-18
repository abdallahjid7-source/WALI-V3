package com.example.wali

import com.example.wali.ai.AiDifficulty
import com.example.wali.ai.WaliAi
import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameState
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WaliAiTest {

    @Test
    fun ai_easy_placementChoosesValidPlacement() {
        val state = WaliEngine.initializeGame()
        val move = WaliAi.chooseMove(state, AiDifficulty.EASY, Random(42))

        assertNotNull(move)
        assertNotNull(move!!.placement)
        assertNull(move.moveFrom)
        assertNull(move.moveTo)

        val validPlacements = WaliEngine.getValidPlacements(state)
        assertTrue(move.placement in validPlacements)
    }

    @Test
    fun ai_medium_placementChoosesValidPlacement() {
        val state = WaliEngine.initializeGame()
        val move = WaliAi.chooseMove(state, AiDifficulty.MEDIUM, Random(42))

        assertNotNull(move)
        assertNotNull(move!!.placement)
        val validPlacements = WaliEngine.getValidPlacements(state)
        assertTrue(move.placement in validPlacements)
    }

    @Test
    fun ai_hard_placementChoosesValidPlacement() {
        val state = WaliEngine.initializeGame()
        val move = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42))

        assertNotNull(move)
        assertNotNull(move!!.placement)
        val validPlacements = WaliEngine.getValidPlacements(state)
        assertTrue(move.placement in validPlacements)
    }

    @Test
    fun ai_movement_choosesLegalMoveOnly() {
        // Crée un plateau de mouvement minimaliste
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0) -> Player.STICKS
                Position(4, 5) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 1,
            stonesRemaining = 1,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        for (diff in AiDifficulty.values()) {
            val move = WaliAi.chooseMove(state, diff, Random(42))
            assertNotNull("Move for $diff should not be null", move)
            assertEquals(Position(0, 0), move!!.moveFrom)
            assertTrue(move.moveTo in WaliEngine.getValidMoves(state, Position(0, 0)))
        }
    }

    @Test
    fun ai_captureTarget_picksFromCapturables() {
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0) -> Player.STICKS
                Position(2, 2) -> Player.STONES
                Position(3, 3) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 1,
            stonesRemaining = 2,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 1
        )

        val capturable = setOf(Position(2, 2), Position(3, 3))
        for (diff in AiDifficulty.values()) {
            val target = WaliAi.chooseCaptureTarget(state, capturable, diff, Random(42))
            assertNotNull(target)
            assertTrue(target in capturable)
        }
    }

    @Test
    fun ai_detectsImmediateWinIfAvailable() {
        // Cas où déplacer un pion forme une ligne et permet la capture du dernier pion adverse
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0) -> Player.STICKS
                Position(0, 1) -> Player.STICKS
                Position(1, 2) -> Player.STICKS // Déplacement vers (0,2) forme la ligne (0,0)-(0,1)-(0,2)
                Position(4, 5) -> Player.STONES  // Unique pion adverse
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 3,
            stonesRemaining = 1,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        val move = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42))
        assertNotNull(move)
        assertEquals(Position(1, 2), move!!.moveFrom)
        assertEquals(Position(0, 2), move.moveTo)
    }

    @Test
    fun ai_hard_blocksOpponentImminentThreat() {
        // L'adversaire STONES a deux pions en (2,0) et (2,1) et un feeder en (1,2) prêt à descendre en (2,2)
        // L'IA STICKS a un pion en (3,2) qui peut monter en (2,2) pour bloquer la ligne !
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(2, 0) -> Player.STONES
                Position(2, 1) -> Player.STONES
                Position(1, 2) -> Player.STONES // Feeder qui peut descendre en (2,2)
                Position(3, 2) -> Player.STICKS // Pion IA pouvant monter en (2,2) pour interposition
                Position(0, 5) -> Player.STICKS
                Position(4, 5) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 2,
            stonesRemaining = 4,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        val move = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42))
        assertNotNull(move)
        // L'IA doit jouer le blocage en (2,2) avec son pion en (3,2)
        assertEquals(Position(3, 2), move!!.moveFrom)
        assertEquals(Position(2, 2), move.moveTo)
    }

    @Test
    fun ai_hard_prioritizesThreateningCaptureTargetOverTrappedPiece() {
        // L'adversaire a un pion (1,1) formant une menace active de ligne
        // et un pion (4,5) totalement piégé/paralysé sans aucun coup légal.
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                // Menace adverse : (1,1) et (1,2) avec un feeder en (0,3) prêt à glisser en (1,3)
                Position(1, 1) -> Player.STONES
                Position(1, 2) -> Player.STONES
                Position(0, 3) -> Player.STONES
                // Pion adverse paralysé dans un coin entouré par des pions alliés
                Position(4, 5) -> Player.STONES
                Position(4, 4) -> Player.STICKS
                Position(3, 5) -> Player.STICKS
                // Pions IA
                Position(0, 0) -> Player.STICKS
                Position(0, 1) -> Player.STICKS
                Position(0, 2) -> Player.STICKS
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 5,
            stonesRemaining = 4,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 1
        )

        val capturable = setOf(Position(1, 1), Position(4, 5))
        val chosen = WaliAi.chooseCaptureTarget(state, capturable, AiDifficulty.HARD, Random(42))

        // L'IA doit choisir (1,1) pour briser la menace adverse plutôt que le pion déjà mort en (4,5)
        assertEquals(Position(1, 1), chosen)
    }

    @Test
    fun ai_placement_blocksOpponentPair() {
        // L'adversaire STONES a deux pions consécutifs en (2,1) et (2,2)
        // Les cases (2,0) et (2,3) sont vides.
        // L'IA STICKS dont c'est le tour de poser doit privilégier le blocage de la ligne (2,0) ou (2,3)
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(2, 1) -> Player.STONES
                Position(2, 2) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = 12,
            stonesRemaining = 10,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        val move = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42))
        assertNotNull(move)
        assertNotNull(move!!.placement)
        // La pose doit bloquer l'un des deux côtés de la ligne adverse
        assertTrue(
            "AI should block at (2,0) or (2,3), chosen: ${move.placement}",
            move.placement == Position(2, 0) || move.placement == Position(2, 3)
        )
    }
}
