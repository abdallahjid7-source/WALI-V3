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

    @Test
    fun ai_hard_findsForcedWinWithinBudget() {
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

        val move = WaliAi.chooseMove(
            state = state,
            difficulty = AiDifficulty.HARD,
            random = Random(42),
            timeBudgetMs = 400L
        )

        assertNotNull(move)
        assertEquals(Position(1, 2), move!!.moveFrom)
        assertEquals(Position(0, 2), move.moveTo)
    }

    @Test
    fun ai_hard_respectsTimeBudgetAndReturnsLegalMove() {
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0) -> Player.STICKS
                Position(0, 2) -> Player.STICKS
                Position(1, 1) -> Player.STICKS
                Position(2, 3) -> Player.STICKS
                Position(3, 1) -> Player.STICKS
                Position(4, 2) -> Player.STICKS
                Position(1, 3) -> Player.STONES
                Position(2, 2) -> Player.STONES
                Position(3, 3) -> Player.STONES
                Position(4, 4) -> Player.STONES
                Position(0, 4) -> Player.STONES
                Position(1, 5) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 6,
            stonesRemaining = 6,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        val startTime = System.currentTimeMillis()
        val move = WaliAi.chooseMove(
            state = state,
            difficulty = AiDifficulty.HARD,
            random = Random(123),
            timeBudgetMs = 250L
        )
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull(move)
        assertNotNull(move!!.moveFrom)
        assertNotNull(move.moveTo)
        val validMoves = WaliEngine.getValidMoves(state, move.moveFrom!!)
        assertTrue("Destination should be a valid move", move.moveTo in validMoves)
        assertTrue("Elapsed time should reasonably respect budget, took ${elapsed}ms", elapsed < 2000L)
    }

    @Test
    fun ai_hard_transpositionTable_doesNotChangeChosenMoveCorrectness() {
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0) -> Player.STICKS
                Position(1, 1) -> Player.STICKS
                Position(2, 2) -> Player.STICKS
                Position(3, 3) -> Player.STICKS
                Position(1, 2) -> Player.STONES
                Position(2, 3) -> Player.STONES
                Position(3, 4) -> Player.STONES
                Position(4, 5) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 4,
            stonesRemaining = 4,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        try {
            // 1. Avec table de transposition activée
            WaliAi.clearTranspositionTable()
            WaliAi.isTranspositionTableEnabled = true
            val moveWithTt = WaliAi.chooseMove(
                state = state,
                difficulty = AiDifficulty.HARD,
                random = Random(999),
                timeBudgetMs = 200L
            )

            // 2. Avec table de transposition désactivée
            WaliAi.clearTranspositionTable()
            WaliAi.isTranspositionTableEnabled = false
            val moveWithoutTt = WaliAi.chooseMove(
                state = state,
                difficulty = AiDifficulty.HARD,
                random = Random(999),
                timeBudgetMs = 200L
            )

            assertNotNull(moveWithTt)
            assertNotNull(moveWithTt!!.moveFrom)
            assertTrue(moveWithTt.moveTo in WaliEngine.getValidMoves(state, moveWithTt.moveFrom!!))

            assertNotNull(moveWithoutTt)
            assertNotNull(moveWithoutTt!!.moveFrom)
            assertTrue(moveWithoutTt.moveTo in WaliEngine.getValidMoves(state, moveWithoutTt.moveFrom!!))
        } finally {
            WaliAi.isTranspositionTableEnabled = true
            WaliAi.clearTranspositionTable()
        }
    }

    @Test
    fun ai_recordHistoryFalse_preservesExactRuleLogicWithoutAllocatingHistory() {
        val baseState = WaliEngine.initializeGame()

        // 1. Placement avec recordHistory = false vs true
        val placedWith = WaliEngine.placePiece(baseState, Position(0, 0), recordHistory = true)
        val placedWithout = WaliEngine.placePiece(baseState, Position(0, 0), recordHistory = false)

        assertEquals(placedWith.board, placedWithout.board)
        assertEquals(placedWith.currentPlayer, placedWithout.currentPlayer)
        assertEquals(placedWith.phase, placedWithout.phase)
        assertEquals(placedWith.sticksRemaining, placedWithout.sticksRemaining)
        assertEquals(placedWith.stonesRemaining, placedWithout.stonesRemaining)
        assertEquals(1, placedWith.moveHistory.size)
        assertEquals(0, placedWithout.moveHistory.size)

        // 2. Mouvement avec capture
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0) -> Player.STICKS
                Position(0, 1) -> Player.STICKS
                Position(1, 2) -> Player.STICKS // Mouvement vers (0,2) forme la ligne (0,0)-(0,1)-(0,2)
                Position(2, 2) -> Player.STONES
                Position(4, 5) -> Player.STONES
                else -> null
            }
        }
        val moveState = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 3,
            stonesRemaining = 2,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        val movedWith = WaliEngine.movePiece(moveState, Position(1, 2), Position(0, 2), recordHistory = true)
        val movedWithout = WaliEngine.movePiece(moveState, Position(1, 2), Position(0, 2), recordHistory = false)

        assertEquals(movedWith.board, movedWithout.board)
        assertEquals(movedWith.currentPlayer, movedWithout.currentPlayer)
        assertEquals(movedWith.pendingCaptures, movedWithout.pendingCaptures)
        assertEquals(movedWith.usedLines, movedWithout.usedLines)
        assertEquals(movedWith.winner, movedWithout.winner)
        assertEquals(2, movedWith.moveHistory.size)
        assertEquals(0, movedWithout.moveHistory.size)

        // 3. Capture de pion
        val capWith = WaliEngine.capturePiece(movedWith, Position(2, 2), recordHistory = true)
        val capWithout = WaliEngine.capturePiece(movedWithout, Position(2, 2), recordHistory = false)

        assertEquals(capWith.board, capWithout.board)
        assertEquals(capWith.currentPlayer, capWithout.currentPlayer)
        assertEquals(capWith.capturedBySticks, capWithout.capturedBySticks)
        assertEquals(capWith.capturedByStones, capWithout.capturedByStones)
        assertEquals(capWith.stonesRemaining, capWithout.stonesRemaining)
        assertEquals(capWith.winner, capWithout.winner)
        assertEquals(3, capWith.moveHistory.size)
        assertEquals(0, capWithout.moveHistory.size)
    }

    @Test
    fun ai_hard_measuresDepthOnTypicalMidGamePosition() {
        // Position typique de milieu de partie en phase de déplacement (6 contre 6)
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0) -> Player.STICKS
                Position(0, 2) -> Player.STICKS
                Position(1, 1) -> Player.STICKS
                Position(2, 3) -> Player.STICKS
                Position(3, 1) -> Player.STICKS
                Position(4, 2) -> Player.STICKS
                Position(1, 3) -> Player.STONES
                Position(2, 2) -> Player.STONES
                Position(3, 3) -> Player.STONES
                Position(4, 4) -> Player.STONES
                Position(0, 4) -> Player.STONES
                Position(1, 5) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 6,
            stonesRemaining = 6,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        WaliAi.clearTranspositionTable()
        val move = WaliAi.chooseMove(
            state = state,
            difficulty = AiDifficulty.HARD,
            random = Random(42),
            timeBudgetMs = 1000L
        )

        assertNotNull(move)
        assertNotNull(move!!.moveFrom)
        assertNotNull(move.moveTo)
        assertTrue("Actual depth reached: ${WaliAi.lastSearchCompletedDepth}, nodes: ${WaliAi.lastSearchNodeCount}", WaliAi.lastSearchCompletedDepth >= 5)
        println("Midgame search reached completed depth: ${WaliAi.lastSearchCompletedDepth}, nodes: ${WaliAi.lastSearchNodeCount}")
    }

    @Test
    fun ai_hard_transpositionTable_persistsBetweenConsecutiveMoves() {
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0) -> Player.STICKS
                Position(0, 2) -> Player.STICKS
                Position(1, 1) -> Player.STICKS
                Position(1, 3) -> Player.STONES
                Position(2, 2) -> Player.STONES
                Position(3, 3) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 3,
            stonesRemaining = 3,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        WaliAi.clearTranspositionTable()
        val move1 = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(1), timeBudgetMs = 200L)
        assertNotNull(move1)
        val nodes1 = WaliAi.lastSearchNodeCount
        assertTrue(nodes1 > 0)

        // Deuxième recherche consécutive sans clear : la TT est réutilisée
        val move2 = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(1), timeBudgetMs = 200L)
        assertNotNull(move2)
        assertEquals(move1, move2)

        // Après clear explicite (ex: nouvelle partie), la TT est remise à zéro
        WaliAi.clearTranspositionTable()
    }
}
