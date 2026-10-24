package com.example.wali.ai

import com.example.wali.model.GameState
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import kotlin.random.Random

/**
 * Résultat détaillé d'une exécution sur le banc de mesure de référence.
 * La somme des temps instrumentés et du temps "autre / non instrumenté" est
 * garantie égale au temps mural global.
 */
data class BenchmarkResult(
    val totalNodes: Long,
    val wallTimeMs: Long,
    val completedDepth: Int,
    val nodesPerSecond: Long,
    val timeOrderMovesMs: Long,
    val timeEngineMoveMs: Long,
    val timeEvalMs: Long,
    val timeTtMs: Long,
    val timeLegalMovesMs: Long,
    val timeOtherMs: Long
) {
    fun summary(): String {
        return "nodes=$totalNodes, wallTime=${wallTimeMs}ms, nps=$nodesPerSecond, depth=$completedDepth | " +
               "orderMoves=${timeOrderMovesMs}ms, movePiece=${timeEngineMoveMs}ms, eval=${timeEvalMs}ms, " +
               "TT=${timeTtMs}ms, legalMoves=${timeLegalMovesMs}ms, other=${timeOtherMs}ms"
    }
}

/**
 * Banc de mesure versionné officiel pour le moteur d'IA Wali.
 * Position fixe et codée en dur de milieu de partie (6 pions vs 6 pions en phase de déplacement).
 */
object WaliAiBenchmark {

    /**
     * Position de référence fixe codée en dur (milieu de partie équilibré).
     * 6 pions STICKS contre 6 pions STONES sur une grille 5x6 en phase de déplacement.
     */
    val referenceMidGameState: GameState by lazy {
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
        GameState(
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
    }

    /**
     * Position de référence de début de pose (2 pions posés, 28 cases libres).
     * BÂTONNETS a le trait avec 11 pions en réserve, CAILLOUX 11 pions.
     */
    val placementEarlyState: GameState by lazy {
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(1, 2) -> Player.STICKS
                Position(3, 3) -> Player.STONES
                else -> null
            }
        }
        GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = 11,
            stonesRemaining = 11,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )
    }

    /**
     * Position de référence de milieu de pose (12 pions posés, 6 BÂTONNETS vs 6 CAILLOUX, 18 cases libres).
     * BÂTONNETS a le trait avec 6 pions en réserve.
     */
    val placementMidState: GameState by lazy {
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                Position(0, 1) -> Player.STICKS
                Position(1, 1) -> Player.STICKS
                Position(2, 3) -> Player.STICKS
                Position(3, 2) -> Player.STICKS
                Position(4, 0) -> Player.STICKS
                Position(4, 4) -> Player.STICKS
                Position(0, 3) -> Player.STONES
                Position(1, 4) -> Player.STONES
                Position(2, 1) -> Player.STONES
                Position(3, 4) -> Player.STONES
                Position(4, 2) -> Player.STONES
                Position(1, 0) -> Player.STONES
                else -> null
            }
        }
        GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = 6,
            stonesRemaining = 6,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )
    }

    /**
     * Position de référence de transition vers le déplacement (23 pions posés, 7 cases libres).
     * 12 pions BÂTONNETS posés, 11 pions CAILLOUX posés (1 seul pion restant à poser pour CAILLOUX).
     * La prochaine pose de CAILLOUX déclenche la transition vers la phase de déplacement !
     */
    val placementTransitionState: GameState by lazy {
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                // 12 BÂTONNETS (aucun alignement de 3)
                Position(0, 0) -> Player.STICKS
                Position(0, 2) -> Player.STICKS
                Position(0, 5) -> Player.STICKS
                Position(1, 1) -> Player.STICKS
                Position(1, 3) -> Player.STICKS
                Position(2, 0) -> Player.STICKS
                Position(2, 4) -> Player.STICKS
                Position(3, 1) -> Player.STICKS
                Position(3, 3) -> Player.STICKS
                Position(3, 5) -> Player.STICKS
                Position(4, 2) -> Player.STICKS
                Position(4, 4) -> Player.STICKS
                // 11 CAILLOUX
                Position(0, 1) -> Player.STONES
                Position(0, 4) -> Player.STONES
                Position(1, 0) -> Player.STONES
                Position(1, 2) -> Player.STONES
                Position(1, 5) -> Player.STONES
                Position(2, 1) -> Player.STONES
                Position(2, 3) -> Player.STONES
                Position(3, 0) -> Player.STONES
                Position(3, 4) -> Player.STONES
                Position(4, 1) -> Player.STONES
                Position(4, 3) -> Player.STONES
                else -> null
            }
        }
        GameState(
            board = board,
            currentPlayer = Player.STONES,
            phase = Phase.PLACEMENT,
            sticksRemaining = 0,
            stonesRemaining = 1,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0
        )
    }

    /**
     * Exécute une mesure reproductible sur un état donné avec un budget fixé.
     */
    fun runBenchmarkForState(
        state: GameState,
        budgetMs: Long = 1000L,
        clearTt: Boolean = true
    ): BenchmarkResult {
        if (clearTt) {
            WaliAi.clearTranspositionTable()
        }
        WaliAi.resetProfiling()
        WaliAi.isProfilingEnabled = true

        val startWall = System.currentTimeMillis()
        WaliAi.chooseMove(
            state = state,
            difficulty = AiDifficulty.HARD,
            random = Random(42),
            timeBudgetMs = budgetMs
        )
        val elapsedWall = maxOf(1L, System.currentTimeMillis() - startWall)
        WaliAi.isProfilingEnabled = false

        val totalNodes = WaliAi.profileTotalNodes
        val nps = (totalNodes * 1000L) / elapsedWall

        val tOrder = WaliAi.profileTimeOrderMovesNanos / 1_000_000L
        val tMove = WaliAi.profileTimeEngineMoveNanos / 1_000_000L
        val tEval = WaliAi.profileTimeEvalNanos / 1_000_000L
        val tTt = WaliAi.profileTimeTtNanos / 1_000_000L
        val tLegal = WaliAi.profileTimeLegalMovesNanos / 1_000_000L

        val instrumentedSum = tOrder + tMove + tEval + tTt + tLegal
        val tOther = maxOf(0L, elapsedWall - instrumentedSum)

        return BenchmarkResult(
            totalNodes = totalNodes,
            wallTimeMs = elapsedWall,
            completedDepth = WaliAi.lastSearchCompletedDepth,
            nodesPerSecond = nps,
            timeOrderMovesMs = tOrder,
            timeEngineMoveMs = tMove,
            timeEvalMs = tEval,
            timeTtMs = tTt,
            timeLegalMovesMs = tLegal,
            timeOtherMs = tOther
        )
    }

    /**
     * Exécute une mesure reproductible sur la position de référence avec un budget fixé.
     * Réinitialise la table de transposition et le profilage pour obtenir des chiffres nets.
     */
    fun runBenchmark(
        budgetMs: Long = 1000L,
        clearTt: Boolean = true
    ): BenchmarkResult {
        return runBenchmarkForState(referenceMidGameState, budgetMs, clearTt)
    }

    fun runPlacementEarlyBenchmark(budgetMs: Long = 1000L): BenchmarkResult {
        return runBenchmarkForState(placementEarlyState, budgetMs, clearTt = true)
    }

    fun runPlacementMidBenchmark(budgetMs: Long = 1000L): BenchmarkResult {
        return runBenchmarkForState(placementMidState, budgetMs, clearTt = true)
    }

    fun runPlacementTransitionBenchmark(budgetMs: Long = 1000L): BenchmarkResult {
        return runBenchmarkForState(placementTransitionState, budgetMs, clearTt = true)
    }
}
