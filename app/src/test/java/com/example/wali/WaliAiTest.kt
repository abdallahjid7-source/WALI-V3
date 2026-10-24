package com.example.wali

import com.example.wali.ai.AiDifficulty
import com.example.wali.ai.WaliAi
import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameState
import com.example.wali.model.Line
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
        WaliAi.resetProfiling()
        WaliAi.isProfilingEnabled = true
        val startWallTime = System.currentTimeMillis()
        val move = WaliAi.chooseMove(
            state = state,
            difficulty = AiDifficulty.HARD,
            random = Random(42),
            timeBudgetMs = 1000L
        )
        val elapsedWallTime = System.currentTimeMillis() - startWallTime
        WaliAi.isProfilingEnabled = false

        val totalNodes = WaliAi.profileTotalNodes
        val nps = if (elapsedWallTime > 0) (totalNodes * 1000) / elapsedWallTime else 0
        val tOrderMs = WaliAi.profileTimeOrderMovesNanos / 1_000_000
        val tMoveMs = WaliAi.profileTimeEngineMoveNanos / 1_000_000
        val tEvalMs = WaliAi.profileTimeEvalNanos / 1_000_000
        val tTtMs = WaliAi.profileTimeTtNanos / 1_000_000
        val tLegalMs = WaliAi.profileTimeLegalMovesNanos / 1_000_000

        val breakdown = "PROFILING_BASELINE: nodes=$totalNodes, wallTime=${elapsedWallTime}ms, nps=$nps, depth=${WaliAi.lastSearchCompletedDepth} | orderMoves=${tOrderMs}ms, movePiece=${tMoveMs}ms, eval=${tEvalMs}ms, TT=${tTtMs}ms, legalMoves=${tLegalMs}ms"
        System.err.println(breakdown)
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

    @Test
    fun ai_hard_choosesCaptureLeadingToBestOutcome_notGreedy() {
        // Position avec pendingCaptures = 1 pour STICKS.
        // STONES possède des pions dont :
        // - targetA en (2, 2) : pion central très mobile (4 libertés, score heuristique glouton très élevé).
        // - targetB en (4, 2) : pion excentré (1 liberté), mais qui bloque une ligne gagnante de STICKS en (4, 0)-(4, 1)-(4, 2).
        // STICKS possède un pion en (3, 2) prêt à s'engouffrer en (4, 2) au tour suivant pour former la ligne et gagner.
        // Si STICKS capture targetB, (4, 2) est libéré et STICKS gagne au coup suivant.
        // Si STICKS capture targetA (choix glouton), targetB reste en place et bloque la victoire de STICKS.
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                // STICKS : ligne préparée en bas (4, 0) et (4, 1), avec feeder en (3, 2)
                Position(4, 0) -> Player.STICKS
                Position(4, 1) -> Player.STICKS
                Position(3, 2) -> Player.STICKS
                Position(0, 0) -> Player.STICKS
                // STONES : targetA au centre, targetB qui bloque la ligne en (4, 2)
                Position(2, 2) -> Player.STONES // targetA : mobile et central
                Position(4, 2) -> Player.STONES // targetB : bloque la ligne gagnante de STICKS
                Position(0, 5) -> Player.STONES // autre pion STONES passif
                else -> null
            }
        }

        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 4,
            stonesRemaining = 3,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 1
        )

        val capturables = setOf(Position(2, 2), Position(4, 2), Position(0, 5))

        // Vérification que le niveau Difficile choisit targetB (4, 2) grâce à la recherche en profondeur
        WaliAi.clearTranspositionTable()
        val chosen = WaliAi.chooseCaptureTarget(state, capturables, AiDifficulty.HARD, Random(42))
        assertEquals(Position(4, 2), chosen)
    }

    @Test
    fun ai_hard_captureBranching_doesNotCollapseSearchDepth() {
        // Vérifie que le branchement explicite des captures ne fait pas s'effondrer la profondeur
        // sur la position de référence (doit rester >= 7 demi-coups en 1000ms).
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
        val move = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42), timeBudgetMs = 1000L)
        assertNotNull(move)
        assertTrue(
            "La profondeur complétée doit rester >= 7 demi-coups (obtenu: ${WaliAi.lastSearchCompletedDepth})",
            WaliAi.lastSearchCompletedDepth >= 7
        )
    }

    @Test
    fun ai_hard_capturePlayedMatchesSearchPrincipalVariation() {
        // Position où STICKS effectue un coup formant un alignement avec plusieurs cibles capturables :
        // L'IA choisit le coup (from, to) et la variante principale associe à ce coup un captureTarget.
        // On vérifie que aiMove.captureTarget n'est pas null, et que chooseCaptureTarget consomme
        // exactement cette même cible sans divergence.
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                // STICKS forme la ligne (0,0)-(0,1)-(0,2) en déplaçant (1,2) vers (0,2)
                Position(0, 0) -> Player.STICKS
                Position(0, 1) -> Player.STICKS
                Position(1, 2) -> Player.STICKS
                Position(3, 0) -> Player.STICKS
                // STONES possède plusieurs cibles :
                Position(2, 2) -> Player.STONES
                Position(4, 2) -> Player.STONES
                Position(0, 5) -> Player.STONES
                else -> null
            }
        }
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 4,
            stonesRemaining = 3,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        WaliAi.clearTranspositionTable()
        val aiMove = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42), timeBudgetMs = 500L)
        assertNotNull(aiMove)
        assertEquals(Position(1, 2), aiMove!!.moveFrom)
        assertEquals(Position(0, 2), aiMove.moveTo)
        assertNotNull("La recherche principale doit porter la cible de capture dans la PV", aiMove.captureTarget)

        // Simule la transition moteur vers l'état suivant
        val nextState = WaliEngine.movePiece(state, aiMove.moveFrom!!, aiMove.moveTo!!)
        assertTrue(WaliEngine.canCapture(nextState))
        val capturables = setOf(Position(2, 2), Position(4, 2), Position(0, 5))

        // chooseCaptureTarget consomme la cible issue de la variante principale
        val captured = WaliAi.chooseCaptureTarget(nextState, capturables, AiDifficulty.HARD, Random(42))
        assertEquals(aiMove.captureTarget, captured)
    }

    @Test
    fun benchmark_versionedReferencePosition_meetsDepthAndSanityGuarantees() {
        val result = com.example.wali.ai.WaliAiBenchmark.runBenchmark(budgetMs = 1000L)
        System.err.println("VERSIONED_BENCHMARK_RESULT: ${result.summary()}")

        assertTrue("Profondeur complétée >= 8 (obtenu: ${result.completedDepth})", result.completedDepth >= 8)
        assertTrue("Nœuds totaux > 0 (obtenu: ${result.totalNodes})", result.totalNodes > 0)
        assertEquals(
            "Somme des temps égale au temps mural",
            result.wallTimeMs,
            result.timeOrderMovesMs + result.timeEngineMoveMs + result.timeCaptureMs + result.timeEvalMs + result.timeTtMs + result.timeLegalMovesMs + result.timeOtherMs
        )
    }

    @Test
    fun benchmark_placementEarlyPosition_reportsMetrics() {
        val result = com.example.wali.ai.WaliAiBenchmark.runPlacementEarlyBenchmark(budgetMs = 1000L)
        System.err.println("BENCHMARK_PLACEMENT_EARLY: ${result.summary()}")
        assertTrue(result.completedDepth >= 3)
        assertTrue(result.totalNodes > 0)
        assertEquals(
            result.wallTimeMs,
            result.timeOrderMovesMs + result.timeEngineMoveMs + result.timeCaptureMs + result.timeEvalMs + result.timeTtMs + result.timeLegalMovesMs + result.timeOtherMs
        )
    }

    @Test
    fun benchmark_placementMidPosition_reportsMetrics() {
        val result = com.example.wali.ai.WaliAiBenchmark.runPlacementMidBenchmark(budgetMs = 1000L)
        System.err.println("BENCHMARK_PLACEMENT_MID: ${result.summary()}")
        assertTrue(result.completedDepth >= 3)
        assertTrue(result.totalNodes > 0)
        assertEquals(
            result.wallTimeMs,
            result.timeOrderMovesMs + result.timeEngineMoveMs + result.timeCaptureMs + result.timeEvalMs + result.timeTtMs + result.timeLegalMovesMs + result.timeOtherMs
        )
    }

    @Test
    fun benchmark_placementTransitionPosition_reportsMetrics() {
        val result = com.example.wali.ai.WaliAiBenchmark.runPlacementTransitionBenchmark(budgetMs = 1000L)
        System.err.println("BENCHMARK_PLACEMENT_TRANSITION: ${result.summary()}")
        assertTrue(result.completedDepth >= 4)
        assertTrue(result.totalNodes > 0)
        assertEquals(
            result.wallTimeMs,
            result.timeOrderMovesMs + result.timeEngineMoveMs + result.timeCaptureMs + result.timeEvalMs + result.timeTtMs + result.timeLegalMovesMs + result.timeOtherMs
        )
    }

    @Test
    fun eval_flatArrayOptimization_producesIdenticalScores() {
        // Garde-fou 2 : Prouve l'équivalence mathématique stricte de evaluateStateDeep
        // entre la version optimisée par tableau plat et la version logique de référence.
        val states = listOf(
            com.example.wali.ai.WaliAiBenchmark.referenceMidGameState,
            com.example.wali.ai.WaliAiBenchmark.placementEarlyState,
            com.example.wali.ai.WaliAiBenchmark.placementMidState,
            com.example.wali.ai.WaliAiBenchmark.placementTransitionState
        )

        for (state in states) {
            for (player in listOf(Player.STICKS, Player.STONES)) {
                val legacyScore = evaluateStateDeepLegacy(state, player)
                val fastScore = WaliAi.evaluateStateDeep(state, player)
                assertEquals("Score d'évaluation strictement identique pour $player", legacyScore, fastScore)
            }
        }

        // Test sur 20 positions aléatoires supplémentaires
        val rng = Random(999)
        for (i in 1..20) {
            var simState = WaliEngine.initializeGame()
            val plies = rng.nextInt(2, 40)
            for (p in 1..plies) {
                if (simState.winner != null) break
                val placements = WaliEngine.getValidPlacements(simState)
                if (placements.isNotEmpty()) {
                    simState = WaliEngine.placePiece(simState, placements.random(rng), recordHistory = false)
                } else {
                    val moves = WaliAi.getAllLegalMoves(simState)
                    if (moves.isEmpty()) break
                    val m = moves.random(rng)
                    simState = WaliEngine.movePiece(simState, m.first, m.second, recordHistory = false)
                    if (WaliEngine.canCapture(simState)) {
                        val opp = simState.currentPlayer.opponent
                        val caps = simState.board.filter { it.value == opp }.keys
                        if (caps.isNotEmpty()) {
                            simState = WaliEngine.capturePiece(simState, caps.random(rng), recordHistory = false)
                        }
                    }
                }
            }

            for (player in listOf(Player.STICKS, Player.STONES)) {
                val legacyScore = evaluateStateDeepLegacy(simState, player)
                val fastScore = WaliAi.evaluateStateDeep(simState, player)
                assertEquals("Score d'évaluation strictement identique position $i pour $player", legacyScore, fastScore)
            }
        }
    }

    @Test
    fun ai_hard_placementUsesDeepSearch_notTwoPlyHeuristic() {
        val state = com.example.wali.ai.WaliAiBenchmark.placementEarlyState
        WaliAi.clearTranspositionTable()
        val move = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42), timeBudgetMs = 300L)

        assertNotNull(move)
        assertNotNull(move!!.placement)
        assertTrue(move.placement in WaliEngine.getValidPlacements(state))
        assertTrue(
            "La phase de pose en niveau Difficile doit utiliser la recherche profonde (profondeur complétée >= 3, obtenu: ${WaliAi.lastSearchCompletedDepth})",
            WaliAi.lastSearchCompletedDepth >= 3
        )
        assertTrue(
            "Le nombre de nœuds explorés doit attester d'une recherche profonde (nœuds >= 30, obtenu: ${WaliAi.lastSearchNodeCount})",
            WaliAi.lastSearchNodeCount >= 30
        )
    }

    @Test
    fun ai_hard_placementAvoidsBeingBlockedAtTransition() {
        // Position de fin de pose (22 pions posés : 11 STICKS et 11 STONES).
        // Il reste 1 pion à poser pour STICKS (trait à STICKS) puis 1 pion pour STONES,
        // après quoi transitionToMovement donne le premier coup de déplacement à STICKS (et teste si STICKS est bloqué).
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                // 11 pions STICKS existants tous enfermés (0 voisin vide)
                Position(0, 0), Position(0, 1),
                Position(1, 0), Position(1, 2), Position(1, 3),
                Position(2, 1), Position(2, 2),
                Position(3, 0), Position(3, 1),
                Position(4, 0), Position(4, 2) -> Player.STICKS

                // 11 pions STONES formant la frontière hermétique (sans alignement de 3)
                Position(0, 2), Position(0, 3),
                Position(1, 1), Position(1, 4), Position(1, 5),
                Position(2, 0), Position(2, 3),
                Position(3, 2),
                Position(4, 1), Position(4, 3), Position(4, 5) -> Player.STONES

                // 8 cases vides :
                // - Poche-piège (2 cases) : (0, 4), (0, 5)
                // - Poche de survie (6 cases) : (2, 4), (2, 5), (3, 3), (3, 4), (3, 5), (4, 4)
                else -> null
            }
        }

        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = 1,
            stonesRemaining = 1,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        // Vérification préalable : si STICKS joue dans la poche-piège (0, 4) et que STONES répond en (0, 5),
        // transitionToMovement détecte bien le blocage immédiat de STICKS !
        val afterTrap = WaliEngine.placePiece(state, Position(0, 4), recordHistory = false)
        val afterStonesReply = WaliEngine.placePiece(afterTrap, Position(0, 5), recordHistory = false)
        assertEquals(Phase.GAME_OVER, afterStonesReply.phase)
        assertEquals(Player.STONES, afterStonesReply.winner)

        // L'IA Difficile voit la transition dans son arbre de recherche, écarte les cases pièges (0, 4) et (0, 5),
        // et choisit une case de la poche de survie où STONES ne peut pas l'étouffer au 24e coup.
        WaliAi.clearTranspositionTable()
        val move = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42), timeBudgetMs = 500L)
        assertNotNull(move)
        val chosenPlacement = move!!.placement!!
        val trapPocket = setOf(Position(0, 4), Position(0, 5))
        assertTrue(
            "L'IA Difficile doit éviter la poche-piège $trapPocket menant au blocage à la transition (choisi: $chosenPlacement)",
            chosenPlacement !in trapPocket
        )

        // Vérifie que peu importe la réponse légale de STONES au 24e coup, STICKS n'est JAMAIS bloqué à la transition
        val afterAiMove = WaliEngine.placePiece(state, chosenPlacement, recordHistory = false)
        for (stonesLastPos in WaliEngine.getValidPlacements(afterAiMove)) {
            val transitioned = WaliEngine.placePiece(afterAiMove, stonesLastPos, recordHistory = false)
            assertEquals("La partie doit entrer en phase MOVEMENT sans défaite par blocage", Phase.MOVEMENT, transitioned.phase)
            assertNull("Aucun vainqueur par blocage à la transition", transitioned.winner)
            assertTrue("STICKS doit avoir au moins un coup légal en déplacement", WaliEngine.hasLegalMove(transitioned, Player.STICKS))
        }
    }

    @Test
    fun ai_hard_avoidsHorizonEffect_onPendingCapture() {
        // Phase 6a : Position où STICKS (7 pions) a deux coups de capture immédiate :
        // 1. Le coup piège (2,1) -> (2,0) complète (0,0)-(1,0)-(2,0) tout en conservant la ligne (0,0)-(0,1)-(0,2),
        //    ce qui crée un Double Moulin de pivot (0,0) (+4500 pts) et capture 1 pion STONES.
        //    À profondeur fixe 1 sans extension anti-horizon (maxSearchExtensions = 0), la recherche s'arrête
        //    juste après cette capture (depth = 0) et évalue (2,1) -> (2,0) très au-dessus du coup sûr.
        //    Mais juste après l'horizon, la case (2,1) libérée permet à STONES (en (1,1) ou (2,2)/(3,1))
        //    de jouer en (2,1), formant immédiatement (2,1)-(2,2)-(2,3) ou (2,1)-(3,1)-(4,1) et capturant
        //    le pivot (0,0) de STICKS (destruction du double moulin et perte matérielle immédiate) !
        // 2. Le coup sûr (0,2) -> (1,2) complète (1,2)-(1,3)-(1,4), capture 1 pion STONES, maintient le verrou
        //    en (2,1) ET enferme complètement le pion STONES en (1,1) sur ses 4 côtés.
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0), Position(0, 1), Position(0, 2),
                Position(1, 0), Position(2, 1),
                Position(1, 3), Position(1, 4) -> Player.STICKS
                Position(1, 1),
                Position(2, 2), Position(2, 3),
                Position(3, 1), Position(4, 1) -> Player.STONES
                else -> null
            }
        }
        val existingRow0Line = Line(setOf(Position(0, 0), Position(0, 1), Position(0, 2)))
        val state = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 7,
            stonesRemaining = 5,
            winner = null,
            usedLines = setOf(existingRow0Line),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        val trapMoveState = WaliEngine.movePiece(state, Position(2, 1), Position(2, 0), recordHistory = false)
        val safeMoveState = WaliEngine.movePiece(state, Position(0, 2), Position(1, 2), recordHistory = false)
        assertTrue("Le coup piège forme un alignement avec capture en attente", trapMoveState.pendingCaptures > 0)
        assertTrue("Le coup sûr forme un alignement avec capture en attente", safeMoveState.pendingCaptures > 0)

        try {
            // 1. Preuve de l'effet d'horizon à profondeur fixe 1 sans extension (maxSearchExtensions = 0) :
            WaliAi.maxSearchExtensions = 0
            WaliAi.clearTranspositionTable()
            val nodesNoExt = IntArray(1)
            val deadline = System.currentTimeMillis() + 5000L
            val trapScoreNoExt = WaliAi.minimaxAlphaBeta(
                state = trapMoveState,
                depth = 1,
                alpha = Int.MIN_VALUE + 1000,
                beta = Int.MAX_VALUE,
                isMaximizing = true,
                aiPlayer = Player.STICKS,
                deadline = deadline,
                isCancelled = { false },
                nodeCount = nodesNoExt,
                plyFromRoot = 1,
                extensionsLeft = 0,
                lastMoveWasCapture = false
            )
            WaliAi.clearTranspositionTable()
            val safeScoreNoExt = WaliAi.minimaxAlphaBeta(
                state = safeMoveState,
                depth = 1,
                alpha = Int.MIN_VALUE + 1000,
                beta = Int.MAX_VALUE,
                isMaximizing = true,
                aiPlayer = Player.STICKS,
                deadline = deadline,
                isCancelled = { false },
                nodeCount = nodesNoExt,
                plyFromRoot = 1,
                extensionsLeft = 0,
                lastMoveWasCapture = false
            )
            assertTrue(
                "Sans extension anti-horizon à profondeur 1, le coup piège ($trapScoreNoExt) paraît meilleur que le coup sûr ($safeScoreNoExt)",
                trapScoreNoExt > safeScoreNoExt
            )

            // 2. Avec extensions anti-horizon actives (maxSearchExtensions = 2), même à profondeur nominale 1,
            // l'extension prolonge la feuille instable et voit la contre-capture immédiate juste après l'horizon :
            WaliAi.maxSearchExtensions = 2
            WaliAi.clearTranspositionTable()
            val nodesWithExt = IntArray(1)
            val trapScoreWithExt = WaliAi.minimaxAlphaBeta(
                state = trapMoveState,
                depth = 1,
                alpha = Int.MIN_VALUE + 1000,
                beta = Int.MAX_VALUE,
                isMaximizing = true,
                aiPlayer = Player.STICKS,
                deadline = deadline,
                isCancelled = { false },
                nodeCount = nodesWithExt,
                plyFromRoot = 1,
                extensionsLeft = 2,
                lastMoveWasCapture = false
            )
            assertTrue(
                "Avec extension anti-horizon, la perte de pion juste après l'horizon est détectée et inverse le jugement (trapScoreWithExt=$trapScoreWithExt < safeScoreNoExt=$safeScoreNoExt, trapScoreNoExt=$trapScoreNoExt)",
                trapScoreWithExt < safeScoreNoExt && trapScoreWithExt <= trapScoreNoExt - 1200
            )

            // 3. L'IA Difficile rejette le coup piège (2,1)->(2,0) et joue le coup sûr (0,2)->(1,2)
            WaliAi.clearTranspositionTable()
            val chosen = WaliAi.chooseMove(state, AiDifficulty.HARD, Random(42), timeBudgetMs = 300L)
            assertNotNull(chosen)
            assertEquals(Position(0, 2), chosen!!.moveFrom)
            assertEquals(Position(1, 2), chosen.moveTo)
        } finally {
            WaliAi.maxSearchExtensions = 2
        }
    }

    @Test
    fun ai_hard_placementPrefersStructureWithFutureLiberties() {
        // Phase 6b : Vérifie que l'évaluation et la recherche en Phase.PLACEMENT privilégient fortement
        // les structures à fortes libertés futures et forte connectivité, et pénalisent l'agglutination
        // en zone fermée (risque d'auto-étouffement à la transition).
        val crampedBoard = Position.allPositions.associateWith { pos ->
            when (pos) {
                // 4 pions STICKS agglutinés dans le coin supérieur gauche sans libertés
                Position(0, 0), Position(0, 1), Position(1, 0), Position(2, 0) -> Player.STICKS
                // 4 pions STONES entourant la zone
                Position(0, 2), Position(1, 1), Position(2, 1), Position(3, 0) -> Player.STONES
                else -> null
            }
        }
        val openBoard = Position.allPositions.associateWith { pos ->
            when (pos) {
                // 4 pions STICKS sur des carrefours centraux à haute connectivité et nombreuses libertés futures
                Position(1, 2), Position(2, 3), Position(3, 2), Position(2, 4) -> Player.STICKS
                // Les 4 mêmes pions STONES
                Position(0, 2), Position(1, 1), Position(2, 1), Position(3, 0) -> Player.STONES
                else -> null
            }
        }

        val crampedState = GameState(
            board = crampedBoard,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = 8,
            stonesRemaining = 8,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )
        val openState = crampedState.copy(board = openBoard)

        val crampedEval = WaliAi.evaluateStateDeep(crampedState, Player.STICKS)
        val openEval = WaliAi.evaluateStateDeep(openState, Player.STICKS)
        assertTrue(
            "La structure ouverte à fortes libertés futures ($openEval) doit surpasser largement la structure agglutinée ($crampedEval)",
            openEval > crampedEval + 2500
        )

        // Test de décision en milieu de pose : STICKS ne doit pas s'auto-enfermer dans la poche fermée (0,1)
        val decisionBoard = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0), Position(1, 0) -> Player.STICKS
                Position(0, 2), Position(1, 1), Position(2, 0) -> Player.STONES
                Position(1, 3) -> Player.STICKS
                else -> null
            }
        }
        val decisionState = GameState(
            board = decisionBoard,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = 9,
            stonesRemaining = 9,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )

        WaliAi.clearTranspositionTable()
        val move = WaliAi.chooseMove(decisionState, AiDifficulty.HARD, Random(42), timeBudgetMs = 300L)
        assertNotNull(move)
        val chosenPos = move!!.placement!!
        assertTrue("L'IA Difficile doit rejeter la case d'auto-étouffement (0,1)", chosenPos != Position(0, 1))

        val afterPlacement = WaliEngine.placePiece(decisionState, chosenPos, recordHistory = false)
        val chosenLiberties = getNeighborsLegacy(chosenPos).count { afterPlacement.board[it] == null }
        assertTrue(
            "La case choisie ($chosenPos) doit préserver au moins 2 libertés orthogonales futures (obtenu: $chosenLiberties)",
            chosenLiberties >= 2
        )
    }

    private fun getNeighborsLegacy(pos: Position): List<Position> {
        val list = mutableListOf<Position>()
        if (pos.row > 0) list.add(Position(pos.row - 1, pos.col))
        if (pos.row < Position.ROWS - 1) list.add(Position(pos.row + 1, pos.col))
        if (pos.col > 0) list.add(Position(pos.row, pos.col - 1))
        if (pos.col < Position.COLS - 1) list.add(Position(pos.row, pos.col + 1))
        return list
    }

    private fun getEmptyNeighborsLegacy(board: Map<Position, Player?>, pos: Position): Boolean {
        for (n in getNeighborsLegacy(pos)) {
            if (board[n] == null) return true
        }
        return false
    }

    private fun evaluateStateDeepLegacy(state: GameState, aiPlayer: Player): Int {
        if (state.winner != null) {
            return if (state.winner == aiPlayer) 100_000 else -100_000
        }

        val opponent = aiPlayer.opponent
        val aiCount = state.piecesOnBoard(aiPlayer)
        val oppCount = state.piecesOnBoard(opponent)

        var score = (aiCount - oppCount) * 1200
        if (state.phase == Phase.MOVEMENT) {
            if (oppCount <= 3) score += (4 - oppCount) * 600
            if (aiCount <= 3) score -= (4 - aiCount) * 600
            if (oppCount <= 2) score += (3 - oppCount) * 2000
            if (aiCount <= 2) score -= (3 - aiCount) * 2000
        }

        var aiLiberties = 0
        var oppLiberties = 0
        var aiBlockedPieces = 0
        var oppBlockedPieces = 0
        var aiCenterPieces = 0
        var oppCenterPieces = 0
        val aiDistinctEmptySet = mutableSetOf<Position>()
        val oppDistinctEmptySet = mutableSetOf<Position>()
        var aiOneLibertyPieces = 0
        var oppOneLibertyPieces = 0
        var aiSafeMobilePieces = 0
        var oppSafeMobilePieces = 0
        var aiConnectivityScore = 0
        var oppConnectivityScore = 0
        var aiAgglutinationPenalty = 0
        var oppAgglutinationPenalty = 0

        for ((pos, piece) in state.board) {
            if (piece == null) continue
            var liberties = 0
            var friendlyNeighbors = 0
            val neighbors = getNeighborsLegacy(pos)
            for (i in 0 until neighbors.size) {
                val nPos = neighbors[i]
                val nPiece = state.board[nPos]
                if (nPiece == null) {
                    liberties++
                    if (piece == aiPlayer) aiDistinctEmptySet.add(nPos) else oppDistinctEmptySet.add(nPos)
                } else if (nPiece == piece) {
                    friendlyNeighbors++
                }
            }

            val idx = pos.row * Position.COLS + pos.col
            val conn = WaliAi.fastSquareConnectivityScore[idx]
            val agglutination = if (friendlyNeighbors >= 2) {
                val base = (friendlyNeighbors - 1) * 95
                val closedZoneExtra = if (liberties == 0) 220 else if (liberties == 1) 120 else 0
                base + closedZoneExtra
            } else if (friendlyNeighbors == 1 && liberties == 0) {
                130
            } else {
                0
            }

            val isCenter = pos.row in 1..3 && pos.col in 1..4
            if (piece == aiPlayer) {
                aiLiberties += liberties
                if (liberties == 0) aiBlockedPieces++
                else if (liberties == 1) aiOneLibertyPieces++
                else aiSafeMobilePieces++
                if (isCenter) aiCenterPieces++
                aiConnectivityScore += conn
                aiAgglutinationPenalty += agglutination
            } else {
                oppLiberties += liberties
                if (liberties == 0) oppBlockedPieces++
                else if (liberties == 1) oppOneLibertyPieces++
                else oppSafeMobilePieces++
                if (isCenter) oppCenterPieces++
                oppConnectivityScore += conn
                oppAgglutinationPenalty += agglutination
            }
        }

        if (state.phase == Phase.MOVEMENT) {
            if (oppLiberties == 0 && state.currentPlayer == opponent) {
                return 90_000
            }
            if (aiLiberties == 0 && state.currentPlayer == aiPlayer) {
                return -90_000
            }
        }

        var aiDoubleMills = 0
        var oppDoubleMills = 0

        if (aiCount >= 5 || oppCount >= 5) {
            for (i in 0 until WaliAi.fastDoubleMillPairs.size) {
                val pair = WaliAi.fastDoubleMillPairs[i]
                val pivotPos = Position(pair.pivot / Position.COLS, pair.pivot % Position.COLS)
                val pivotOwner = state.board[pivotPos] ?: continue

                val a1 = state.board[Position(pair.a1 / Position.COLS, pair.a1 % Position.COLS)] == pivotOwner
                val a2 = state.board[Position(pair.a2 / Position.COLS, pair.a2 % Position.COLS)] == pivotOwner
                val b1 = state.board[Position(pair.b1 / Position.COLS, pair.b1 % Position.COLS)] == pivotOwner
                val b2 = state.board[Position(pair.b2 / Position.COLS, pair.b2 % Position.COLS)] == pivotOwner

                if (a1 && a2 && b1 && b2) {
                    if (pivotOwner == aiPlayer) aiDoubleMills++ else oppDoubleMills++
                }
            }
        }

        score += aiDoubleMills * 4500
        score -= oppDoubleMills * 4800

        score += (aiLiberties - oppLiberties) * 35
        if (oppLiberties in 1..2) score += 2000
        if (aiLiberties in 1..2) score -= 2500

        score += oppBlockedPieces * 160
        score -= aiBlockedPieces * 200

        var aiThreats = 0
        var oppThreats = 0
        var aiShuttles = 0
        var oppShuttles = 0

        for (i in 0 until WaliAi.fast38LinePatterns.size) {
            val pat = WaliAi.fast38LinePatterns[i]
            val pos0 = Position(pat.c0 / Position.COLS, pat.c0 % Position.COLS)
            val pos1 = Position(pat.c1 / Position.COLS, pat.c1 % Position.COLS)
            val pos2 = Position(pat.c2 / Position.COLS, pat.c2 % Position.COLS)
            val p0 = state.board[pos0]
            val p1 = state.board[pos1]
            val p2 = state.board[pos2]

            val c0HasEmpty = getEmptyNeighborsLegacy(state.board, pos0)
            val c1HasEmpty = getEmptyNeighborsLegacy(state.board, pos1)
            val c2HasEmpty = getEmptyNeighborsLegacy(state.board, pos2)

            if (p0 == aiPlayer && p1 == aiPlayer && p2 == aiPlayer) {
                if (c0HasEmpty || c1HasEmpty || c2HasEmpty) {
                    aiShuttles++
                }
                continue
            } else if (p0 == opponent && p1 == opponent && p2 == opponent) {
                if (c0HasEmpty || c1HasEmpty || c2HasEmpty) {
                    oppShuttles++
                }
                continue
            }

            if ((p0 == aiPlayer && p1 == aiPlayer && p2 == null) ||
                (p0 == aiPlayer && p2 == aiPlayer && p1 == null) ||
                (p1 == aiPlayer && p2 == aiPlayer && p0 == null)
            ) {
                val emptyCell = if (p0 == null) pos0 else if (p1 == null) pos1 else pos2
                val bBefore = if (pat.boundaryBefore != -1) Position(pat.boundaryBefore / Position.COLS, pat.boundaryBefore % Position.COLS) else null
                val bAfter = if (pat.boundaryAfter != -1) Position(pat.boundaryAfter / Position.COLS, pat.boundaryAfter % Position.COLS) else null
                if ((bBefore == null || state.board[bBefore] != aiPlayer) &&
                    (bAfter == null || state.board[bAfter] != aiPlayer)
                ) {
                    val neighbors = getNeighborsLegacy(emptyCell)
                    var hasFeeder = false
                    for (k in 0 until neighbors.size) {
                        val n = neighbors[k]
                        if (n != pos0 && n != pos1 && n != pos2 && state.board[n] == aiPlayer) {
                            hasFeeder = true
                            break
                        }
                    }
                    if (hasFeeder) aiThreats++
                }
            } else if ((p0 == opponent && p1 == opponent && p2 == null) ||
                       (p0 == opponent && p2 == opponent && p1 == null) ||
                       (p1 == opponent && p2 == opponent && p0 == null)
            ) {
                val emptyCell = if (p0 == null) pos0 else if (p1 == null) pos1 else pos2
                val bBefore = if (pat.boundaryBefore != -1) Position(pat.boundaryBefore / Position.COLS, pat.boundaryBefore % Position.COLS) else null
                val bAfter = if (pat.boundaryAfter != -1) Position(pat.boundaryAfter / Position.COLS, pat.boundaryAfter % Position.COLS) else null
                if ((bBefore == null || state.board[bBefore] != opponent) &&
                    (bAfter == null || state.board[bAfter] != opponent)
                ) {
                    val neighbors = getNeighborsLegacy(emptyCell)
                    var hasFeeder = false
                    for (k in 0 until neighbors.size) {
                        val n = neighbors[k]
                        if (n != pos0 && n != pos1 && n != pos2 && state.board[n] == opponent) {
                            hasFeeder = true
                            break
                        }
                    }
                    if (hasFeeder) oppThreats++
                }
            }
        }

        score += aiThreats * 400
        score -= oppThreats * 600
        if (aiThreats >= 2) score += 800
        if (oppThreats >= 2) score -= 1100

        score += aiShuttles * 300
        score -= oppShuttles * 400

        score += (aiCenterPieces - oppCenterPieces) * 15

        if (state.phase == Phase.PLACEMENT || state.sticksPendingPlacement > 0 || state.stonesPendingPlacement > 0) {
            val aiDistinctLiberties = aiDistinctEmptySet.size
            val oppDistinctLiberties = oppDistinctEmptySet.size

            score += (aiDistinctLiberties - oppDistinctLiberties) * 85
            score -= aiBlockedPieces * 450
            score += oppBlockedPieces * 400
            score -= aiOneLibertyPieces * 140
            score += oppOneLibertyPieces * 120
            score += (aiSafeMobilePieces - oppSafeMobilePieces) * 90

            if (aiCount >= 3) {
                val aiMobilePieces = aiCount - aiBlockedPieces
                if (aiMobilePieces == 0) score -= 3000
                else if (aiSafeMobilePieces == 0 && aiDistinctLiberties <= 1) score -= 1800
            }
            if (oppCount >= 3) {
                val oppMobilePieces = oppCount - oppBlockedPieces
                if (oppMobilePieces == 0) score += 2500
                else if (oppSafeMobilePieces == 0 && oppDistinctLiberties <= 1) score += 1500
            }

            score += (aiConnectivityScore - oppConnectivityScore)
            score -= aiAgglutinationPenalty
            score += oppAgglutinationPenalty
        }

        return score
    }

    @Test
    fun zobrist_distinguishesPositionsDifferingOnlyByUsedLines() {
        // Correctif A : Deux GameState au plateau, trait, phase et compteurs strictement identiques,
        // mais dont l'un contient une ligne dans usedLines et l'autre non.
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0), Position(0, 1), Position(1, 2), Position(3, 0) -> Player.STICKS
                Position(2, 2), Position(3, 3), Position(4, 4), Position(1, 5) -> Player.STONES
                else -> null
            }
        }
        val lineRow0 = Line(setOf(Position(0, 0), Position(0, 1), Position(0, 2)))
        val lineCol0 = Line(setOf(Position(0, 0), Position(1, 0), Position(2, 0)))

        val stateWithoutUsedLine = GameState(
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
        val stateWithUsedLine = stateWithoutUsedLine.copy(
            usedLines = setOf(lineRow0)
        )

        // 1. Les empreintes Zobrist doivent différer
        val hashWithout = WaliAi.computeZobristHash(stateWithoutUsedLine)
        val hashWith = WaliAi.computeZobristHash(stateWithUsedLine)
        assertTrue(
            "computeZobristHash doit distinguer deux positions ne différant que par usedLines ($hashWithout vs $hashWith)",
            hashWithout != hashWith
        )

        // Vérifie aussi l'indépendance à l'ordre d'insertion dans usedLines (propriété du XOR)
        val stateMulti1 = stateWithoutUsedLine.copy(usedLines = linkedSetOf(lineRow0, lineCol0))
        val stateMulti2 = stateWithoutUsedLine.copy(usedLines = linkedSetOf(lineCol0, lineRow0))
        assertEquals(
            "L'ordre des lignes dans usedLines ne doit pas modifier le hachage Zobrist",
            WaliAi.computeZobristHash(stateMulti1),
            WaliAi.computeZobristHash(stateMulti2)
        )
        assertTrue(WaliAi.computeZobristHash(stateMulti1) != hashWith)

        // 2. Preuve que WaliEngine leur attribue des captures légales différentes sur le même coup (1,2)->(0,2)
        val afterWithout = WaliEngine.movePiece(stateWithoutUsedLine, Position(1, 2), Position(0, 2), recordHistory = false)
        val afterWith = WaliEngine.movePiece(stateWithUsedLine, Position(1, 2), Position(0, 2), recordHistory = false)
        assertEquals("Sans ligne dans usedLines, (1,2)->(0,2) donne 1 capture", 1, afterWithout.pendingCaptures)
        assertTrue(WaliEngine.canCapture(afterWithout))
        assertEquals("Avec la ligne déjà dans usedLines, (1,2)->(0,2) donne 0 capture", 0, afterWith.pendingCaptures)
        assertTrue(!WaliEngine.canCapture(afterWith))
    }

    @Test
    fun ai_hard_isSideAgnostic_mirroredPositionYieldsMirroredDecision() {
        // Correctif B : Vérifie que l'évaluation et la décision de recherche profonde sont
        // strictement symétriques et indépendantes du camp joué (STICKS vs STONES).

        // --- 1. Milieu de partie en Phase.MOVEMENT ---
        val movBoardSticks = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 0), Position(0, 1), Position(1, 2), Position(2, 4), Position(3, 1) -> Player.STICKS
                Position(1, 0), Position(2, 2), Position(3, 3), Position(4, 1), Position(4, 4), Position(0, 5) -> Player.STONES
                else -> null
            }
        }
        val movStateSticks = GameState(
            board = movBoardSticks,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 5,
            stonesRemaining = 6,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0,
            sticksPendingPlacement = 0,
            stonesPendingPlacement = 0,
            capturedBySticks = 6,
            capturedByStones = 7
        )

        val movBoardStones = movBoardSticks.mapValues { (_, piece) -> piece?.opponent }
        val movStateStones = GameState(
            board = movBoardStones,
            currentPlayer = Player.STONES,
            phase = Phase.MOVEMENT,
            sticksRemaining = 6,
            stonesRemaining = 5,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0,
            sticksPendingPlacement = 0,
            stonesPendingPlacement = 0,
            capturedBySticks = 7,
            capturedByStones = 6
        )

        val evalMovSticks = WaliAi.evaluateStateDeep(movStateSticks, Player.STICKS)
        val evalMovStones = WaliAi.evaluateStateDeep(movStateStones, Player.STONES)
        assertEquals(
            "evaluateStateDeep en MOVEMENT doit être strictement invariant par échange des camps",
            evalMovSticks,
            evalMovStones
        )

        WaliAi.clearTranspositionTable()
        val moveSticks = WaliAi.chooseMove(movStateSticks, AiDifficulty.HARD, Random(42), timeBudgetMs = 400L)
        WaliAi.clearTranspositionTable()
        val moveStones = WaliAi.chooseMove(movStateStones, AiDifficulty.HARD, Random(42), timeBudgetMs = 400L)

        assertNotNull(moveSticks)
        assertNotNull(moveStones)
        assertEquals("Origine géométrique identique en MOVEMENT", moveSticks!!.moveFrom, moveStones!!.moveFrom)
        assertEquals("Destination géométrique identique en MOVEMENT", moveSticks.moveTo, moveStones.moveTo)
        assertEquals("Cible de capture géométrique identique en MOVEMENT", moveSticks.captureTarget, moveStones.captureTarget)

        // --- 2. Position en Phase.PLACEMENT (couvre l'évaluation de pose Phase 5/6) ---
        val placeBoardSticks = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(1, 1), Position(1, 2), Position(3, 1), Position(2, 4), Position(4, 2) -> Player.STICKS
                Position(0, 2), Position(2, 1), Position(2, 2), Position(3, 3), Position(4, 4) -> Player.STONES
                else -> null
            }
        }
        val placeStateSticks = GameState(
            board = placeBoardSticks,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = 7,
            stonesRemaining = 7,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0,
            sticksPendingPlacement = 0,
            stonesPendingPlacement = 0,
            capturedBySticks = 0,
            capturedByStones = 0
        )

        val placeBoardStones = placeBoardSticks.mapValues { (_, piece) -> piece?.opponent }
        val placeStateStones = GameState(
            board = placeBoardStones,
            currentPlayer = Player.STONES,
            phase = Phase.PLACEMENT,
            sticksRemaining = 7,
            stonesRemaining = 7,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0,
            sticksPendingPlacement = 0,
            stonesPendingPlacement = 0,
            capturedBySticks = 0,
            capturedByStones = 0
        )

        val evalPlaceSticks = WaliAi.evaluateStateDeep(placeStateSticks, Player.STICKS)
        val evalPlaceStones = WaliAi.evaluateStateDeep(placeStateStones, Player.STONES)
        assertEquals(
            "evaluateStateDeep en PLACEMENT doit être strictement invariant par échange des camps",
            evalPlaceSticks,
            evalPlaceStones
        )

        WaliAi.clearTranspositionTable()
        val placementMoveSticks = WaliAi.chooseMove(placeStateSticks, AiDifficulty.HARD, Random(42), timeBudgetMs = 400L)
        WaliAi.clearTranspositionTable()
        val placementMoveStones = WaliAi.chooseMove(placeStateStones, AiDifficulty.HARD, Random(42), timeBudgetMs = 400L)

        assertNotNull(placementMoveSticks)
        assertNotNull(placementMoveStones)
        assertEquals(
            "Case de pose géométrique identique en PLACEMENT",
            placementMoveSticks!!.placement,
            placementMoveStones!!.placement
        )
    }

    @Test
    fun benchmark_tacticalPosition_reportsMetricsWithAndWithoutExtensions() {
        // Correctif C : Mesure sur la 5e position tactiquement instable avec et sans extensions anti-horizon
        val resNoExt = com.example.wali.ai.WaliAiBenchmark.runTacticalBenchmark(budgetMs = 1000L, maxExtensions = 0)
        val extCount0 = WaliAi.profileExtensionsTriggered
        System.err.println("BENCHMARK_TACTICAL_EXT0: ${resNoExt.summary()} | extTriggered=$extCount0")

        val resWithExt = com.example.wali.ai.WaliAiBenchmark.runTacticalBenchmark(budgetMs = 1000L, maxExtensions = 2)
        val extCount2 = WaliAi.profileExtensionsTriggered
        System.err.println("BENCHMARK_TACTICAL_EXT2: ${resWithExt.summary()} | extTriggered=$extCount2")

        assertEquals(0L, extCount0)
        assertTrue("Les extensions anti-horizon doivent se déclencher sur la position tactique (extCount2=$extCount2)", extCount2 > 0L)
        assertTrue(resNoExt.completedDepth >= 5)
        assertTrue(resWithExt.completedDepth >= 5)
        assertEquals(
            resNoExt.wallTimeMs,
            resNoExt.timeOrderMovesMs + resNoExt.timeEngineMoveMs + resNoExt.timeCaptureMs + resNoExt.timeEvalMs + resNoExt.timeTtMs + resNoExt.timeLegalMovesMs + resNoExt.timeOtherMs
        )
        assertEquals(
            resWithExt.wallTimeMs,
            resWithExt.timeOrderMovesMs + resWithExt.timeEngineMoveMs + resWithExt.timeCaptureMs + resWithExt.timeEvalMs + resWithExt.timeTtMs + resWithExt.timeLegalMovesMs + resWithExt.timeOtherMs
        )
    }
}

