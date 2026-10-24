package com.example.wali.ai

import com.example.wali.engine.WaliEngine
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
    val timeCaptureMs: Long,
    val timeOtherMs: Long
) {
    fun summary(): String {
        return "nodes=$totalNodes, wallTime=${wallTimeMs}ms, nps=$nodesPerSecond, depth=$completedDepth | " +
               "orderMoves=${timeOrderMovesMs}ms, movePiece=${timeEngineMoveMs}ms, capture=${timeCaptureMs}ms, " +
               "eval=${timeEvalMs}ms, TT=${timeTtMs}ms, legalMoves=${timeLegalMovesMs}ms, other=${timeOtherMs}ms"
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
     * Cinquième position de référence (Correctif C) : position tactiquement instable en déplacement
     * (alignements imminents croisés, contre-captures en chaîne et zones à faible mobilité).
     * Permet de mesurer le coût réel des extensions anti-horizon (maxSearchExtensions = 0 vs 2).
     */
    val tacticalInstabilityState: GameState by lazy {
        val board = Position.allPositions.associateWith { pos ->
            when (pos) {
                // 6 BÂTONNETS : menaces d'alignement immédiates en (0,2) et (4,0) + pions sous pression
                Position(0, 0) -> Player.STICKS
                Position(0, 1) -> Player.STICKS
                Position(1, 2) -> Player.STICKS
                Position(2, 0) -> Player.STICKS
                Position(3, 0) -> Player.STICKS
                Position(4, 1) -> Player.STICKS
                // 6 CAILLOUX : contre-menaces immédiates en (1,2), (4,2) et (4,5)
                Position(1, 0) -> Player.STONES
                Position(2, 2) -> Player.STONES
                Position(3, 2) -> Player.STONES
                Position(4, 3) -> Player.STONES
                Position(4, 4) -> Player.STONES
                Position(3, 5) -> Player.STONES
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
        val tCapture = WaliAi.profileTimeCaptureNanos / 1_000_000L

        val instrumentedSum = tOrder + tMove + tEval + tTt + tLegal + tCapture
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
            timeCaptureMs = tCapture,
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

    fun runTacticalBenchmark(
        budgetMs: Long = 1000L,
        maxExtensions: Int = 2
    ): BenchmarkResult {
        val prevExt = WaliAi.maxSearchExtensions
        return try {
            WaliAi.maxSearchExtensions = maxExtensions
            runBenchmarkForState(tacticalInstabilityState, budgetMs, clearTt = true)
        } finally {
            WaliAi.maxSearchExtensions = prevExt
        }
    }

    /**
     * Exécute la suite complète des 5 positions du banc versionné sur l'appareil courant
     * (avec mesure de la position tactique sans et avec extensions anti-horizon)
     * et retourne un rapport formaté prêt à être affiché ou copié.
     */
    fun runFullDeviceSuite(
        budgetMs: Long = 1000L,
        onProgress: (String) -> Unit = {}
    ): String {
        val sb = StringBuilder()
        val cores = Runtime.getRuntime().availableProcessors()
        sb.appendLine("=== BANC DE MESURE WALI IA (budget=${budgetMs}ms, cores=$cores) ===")

        onProgress("1/6 — Déplacement 6v6 (référence calme)...")
        val refRes = runBenchmark(budgetMs = budgetMs, clearTt = true)
        val refExt = WaliAi.profileExtensionsTriggered
        sb.appendLine("VERSIONED_BENCHMARK_RESULT: ${refRes.summary()} | extTriggered=$refExt")

        onProgress("2/6 — Pose début (2 pions posés)...")
        val earlyRes = runPlacementEarlyBenchmark(budgetMs = budgetMs)
        sb.appendLine("BENCHMARK_PLACEMENT_EARLY: ${earlyRes.summary()}")

        onProgress("3/6 — Pose milieu (12 pions posés)...")
        val midRes = runPlacementMidBenchmark(budgetMs = budgetMs)
        sb.appendLine("BENCHMARK_PLACEMENT_MID: ${midRes.summary()}")

        onProgress("4/6 — Pose transition (23 pions posés)...")
        val transRes = runPlacementTransitionBenchmark(budgetMs = budgetMs)
        sb.appendLine("BENCHMARK_PLACEMENT_TRANSITION: ${transRes.summary()}")

        onProgress("5/6 — Déplacement tactique (maxExtensions=0)...")
        val tac0Res = runTacticalBenchmark(budgetMs = budgetMs, maxExtensions = 0)
        val tac0Ext = WaliAi.profileExtensionsTriggered
        sb.appendLine("BENCHMARK_TACTICAL_EXT0: ${tac0Res.summary()} | extTriggered=$tac0Ext")

        onProgress("6/6 — Déplacement tactique (maxExtensions=2)...")
        val tac2Res = runTacticalBenchmark(budgetMs = budgetMs, maxExtensions = 2)
        val tac2Ext = WaliAi.profileExtensionsTriggered
        sb.appendLine("BENCHMARK_TACTICAL_EXT2: ${tac2Res.summary()} | extTriggered=$tac2Ext")

        return sb.toString().trimEnd()
    }

    /**
     * Configuration d'un agent pour le protocole d'auto-affrontement.
     */
    data class SelfPlayAgentConfig(
        val name: String,
        val difficulty: AiDifficulty = AiDifficulty.HARD,
        val timeBudgetMs: Long = 200L,
        val useOpeningBook: Boolean = false,
        val maxExtensions: Int = 2,
        val randomizeRootTieBreaks: Boolean = true,
        val rootScoreNoiseMargin: Int = 18
    )

    /**
     * Résultat détaillé d'une campagne d'auto-affrontement entre deux configurations du moteur.
     */
    data class SelfPlayMatchResult(
        val agentAName: String,
        val agentBName: String,
        val totalGames: Int,
        val budgetMsA: Long,
        val budgetMsB: Long,
        val winsA: Int,
        val winsB: Int,
        val draws: Int,
        val winsAAsSticks: Int,
        val lossesAAsSticks: Int,
        val drawsWhenAIsSticks: Int,
        val winsAAsStones: Int,
        val lossesAAsStones: Int,
        val drawsWhenAIsStones: Int,
        val totalPlies: Int,
        val uniqueTrajectories: Int,
        val elapsedMs: Long
    ) {
        val scoreA: Double get() = winsA + 0.5 * draws
        val scoreB: Double get() = winsB + 0.5 * draws
        val scoreRateAPercent: Double get() = if (totalGames > 0) (scoreA * 100.0) / totalGames else 0.0
        val scoreRateBPercent: Double get() = if (totalGames > 0) (scoreB * 100.0) / totalGames else 0.0

        val gamesAAsSticks: Int get() = winsAAsSticks + lossesAAsSticks + drawsWhenAIsSticks
        val scoreAAsSticks: Double get() = winsAAsSticks + 0.5 * drawsWhenAIsSticks
        val scoreRateAAsSticksPercent: Double get() = if (gamesAAsSticks > 0) (scoreAAsSticks * 100.0) / gamesAAsSticks else 0.0

        val gamesAAsStones: Int get() = winsAAsStones + lossesAAsStones + drawsWhenAIsStones
        val scoreAAsStones: Double get() = winsAAsStones + 0.5 * drawsWhenAIsStones
        val scoreRateAAsStonesPercent: Double get() = if (gamesAAsStones > 0) (scoreAAsStones * 100.0) / gamesAAsStones else 0.0

        val sticksWinsTotal: Int get() = winsAAsSticks + lossesAAsStones
        val stonesWinsTotal: Int get() = lossesAAsSticks + winsAAsStones

        val standardErrorPercent: Double
            get() {
                if (totalGames <= 1) return 0.0
                val mean = scoreA / totalGames.toDouble()
                val sumSq = winsA * (1.0 - mean) * (1.0 - mean) +
                    draws * (0.5 - mean) * (0.5 - mean) +
                    winsB * (0.0 - mean) * (0.0 - mean)
                val variance = sumSq / (totalGames - 1).toDouble()
                return 100.0 * kotlin.math.sqrt(variance / totalGames.toDouble())
            }

        val ci95Percent: Double get() = 1.96 * standardErrorPercent

        fun summary(): String {
            val avgPlies = if (totalGames > 0) totalPlies.toDouble() / totalGames else 0.0
            return buildString {
                appendLine("=== AUTO-AFFRONTEMENT : $agentAName (${budgetMsA}ms) vs $agentBName (${budgetMsB}ms) — $totalGames parties ===")
                appendLine(
                    "GLOBAL : $agentAName V=$winsA / D=$winsB / N=$draws -> Score=${"%.1f".format( java.util.Locale.US, scoreA )}/$totalGames (${"%.2f".format( java.util.Locale.US, scoreRateAPercent )}% ± ${"%.2f".format( java.util.Locale.US, ci95Percent )}% IC95, SE=${"%.2f".format( java.util.Locale.US, standardErrorPercent )}%)"
                )
                appendLine(
                    "         $agentBName V=$winsB / D=$winsA / N=$draws -> Score=${"%.1f".format( java.util.Locale.US, scoreB )}/$totalGames (${"%.2f".format( java.util.Locale.US, scoreRateBPercent )}%)"
                )
                appendLine(
                    "DÉTAIL PAR CAMP ($agentAName en BÂTONNETS, $gamesAAsSticks parties) : V=$winsAAsSticks / D=$lossesAAsSticks / N=$drawsWhenAIsSticks -> Score=${"%.1f".format( java.util.Locale.US, scoreAAsSticks )}/$gamesAAsSticks (${"%.2f".format( java.util.Locale.US, scoreRateAAsSticksPercent )}%)"
                )
                appendLine(
                    "DÉTAIL PAR CAMP ($agentAName en CAILLOUX,  $gamesAAsStones parties) : V=$winsAAsStones / D=$lossesAAsStones / N=$drawsWhenAIsStones -> Score=${"%.1f".format( java.util.Locale.US, scoreAAsStones )}/$gamesAAsStones (${"%.2f".format( java.util.Locale.US, scoreRateAAsStonesPercent )}%)"
                )
                appendLine(
                    "BILAN COULEURS GLOBAL : Victoires BÂTONNETS (1er joueur)=$sticksWinsTotal | Victoires CAILLOUX (2e joueur)=$stonesWinsTotal | Nuls=$draws"
                )
                append(
                    "MÉTRIQUES PARTIE : plisMoyens=${"%.1f".format( java.util.Locale.US, avgPlies )}, trajectoiresUniques=$uniqueTrajectories/$totalGames, duréeTotale=${elapsedMs / 1000.0}s"
                )
            }
        }
    }

    private data class SingleGameResult(
        val gameIdx: Int,
        val aIsSticks: Boolean,
        val winner: Player?,
        val plies: Int,
        val trajectory: String
    )

    private fun playSingleSelfPlayGame(
        gameIdx: Int,
        agentA: SelfPlayAgentConfig,
        agentB: SelfPlayAgentConfig,
        baseSeed: Long,
        maxMovementPliesWithoutCapture: Int,
        maxTotalPlies: Int
    ): SingleGameResult {
        val aIsSticks = (gameIdx % 2 == 0)
        val gameRandom = Random(baseSeed + gameIdx * 1009L)

        var state = WaliEngine.initializeGame()
        WaliAi.clearTranspositionTable()

        val movementHashCounts = HashMap<Long, Int>()
        var pliesWithoutCapture = 0
        var pliesInGame = 0
        val moveSeq = StringBuilder()

        try {
            while (state.phase != Phase.GAME_OVER && state.winner == null && pliesInGame < maxTotalPlies) {
                val currentPlayer = state.currentPlayer
                val activeAgent = if ((currentPlayer == Player.STICKS) == aIsSticks) agentA else agentB

                if (state.phase == Phase.MOVEMENT && movementHashCounts.isEmpty()) {
                    movementHashCounts[WaliAi.computeZobristHash(state)] = 1
                }

                WaliAi.useOpeningBook = activeAgent.useOpeningBook
                WaliAi.maxSearchExtensions = activeAgent.maxExtensions
                WaliAi.randomizeRootTieBreaks = activeAgent.randomizeRootTieBreaks
                WaliAi.rootScoreNoiseMargin = activeAgent.rootScoreNoiseMargin
                WaliAi.gameMovementHistoryHashes = if (state.phase == Phase.MOVEMENT) {
                    HashSet(movementHashCounts.keys)
                } else {
                    emptySet()
                }
                WaliAi.clearTranspositionTable()

                val move = WaliAi.chooseMove(
                    state = state,
                    difficulty = activeAgent.difficulty,
                    random = gameRandom,
                    timeBudgetMs = activeAgent.timeBudgetMs
                ) ?: break

                if (state.phase == Phase.PLACEMENT || WaliEngine.isPlacementRequired(state)) {
                    val pos = requireNotNull(move.placement) { "Placement attendu pour ${activeAgent.name}" }
                    moveSeq.append("P").append(pos.row).append(pos.col).append(';')
                    state = WaliEngine.placePiece(state, pos, recordHistory = false, validate = true)
                } else {
                    val from = requireNotNull(move.moveFrom) { "moveFrom attendu pour ${activeAgent.name}" }
                    val to = requireNotNull(move.moveTo) { "moveTo attendu pour ${activeAgent.name}" }
                    moveSeq.append("M").append(from.row).append(from.col).append(to.row).append(to.col)
                    state = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = true)

                    var capturedThisTurn = false
                    while (WaliEngine.canCapture(state)) {
                        val capturables = state.board.filter { it.value == currentPlayer.opponent }.keys
                        val target = WaliAi.chooseCaptureTarget(
                            state = state,
                            capturablePositions = capturables,
                            difficulty = activeAgent.difficulty,
                            random = gameRandom
                        ) ?: break
                        moveSeq.append("x").append(target.row).append(target.col)
                        state = WaliEngine.capturePiece(state, target, recordHistory = false, validate = true)
                        capturedThisTurn = true
                    }
                    moveSeq.append(';')

                    if (capturedThisTurn) {
                        pliesWithoutCapture = 0
                        movementHashCounts.clear()
                    } else {
                        pliesWithoutCapture++
                    }

                    if (state.winner == null && state.phase == Phase.MOVEMENT) {
                        val h = WaliAi.computeZobristHash(state)
                        val count = (movementHashCounts[h] ?: 0) + 1
                        movementHashCounts[h] = count
                        if (count >= 3 || pliesWithoutCapture >= maxMovementPliesWithoutCapture) {
                            pliesInGame++
                            break
                        }
                    }
                }

                pliesInGame++
            }
        } finally {
            WaliAi.useOpeningBook = false
            WaliAi.randomizeRootTieBreaks = false
            WaliAi.rootScoreNoiseMargin = 0
            WaliAi.gameMovementHistoryHashes = emptySet()
            WaliAi.clearTranspositionTable()
        }

        return SingleGameResult(
            gameIdx = gameIdx,
            aIsSticks = aIsSticks,
            winner = state.winner,
            plies = pliesInGame,
            trajectory = moveSeq.toString()
        )
    }

    /**
     * Fait s'affronter deux configurations du moteur ([agentA] contre [agentB]) sur [numGames] parties
     * en alternant les camps à chaque partie :
     * - Parties paires (0, 2, 4...) : [agentA] joue BÂTONNETS (1er joueur), [agentB] joue CAILLOUX.
     * - Parties impaires (1, 3, 5...) : [agentB] joue BÂTONNETS (1er joueur), [agentA] joue CAILLOUX.
     *
     * Règles de nulle (draw) :
     * - Triple répétition exacte d'empreinte Zobrist en phase MOVEMENT, ou
     * - [maxMovementPliesWithoutCapture] demi-coups consécutifs sans capture en phase MOVEMENT, ou
     * - Plafond de sécurité [maxTotalPlies] demi-coups au total.
     */
    fun runSelfPlayMatch(
        agentA: SelfPlayAgentConfig,
        agentB: SelfPlayAgentConfig,
        numGames: Int = 200,
        baseSeed: Long = 20261024L,
        maxMovementPliesWithoutCapture: Int = 60,
        maxTotalPlies: Int = 180,
        parallelism: Int = maxOf(1, Runtime.getRuntime().availableProcessors()),
        onGameComplete: (gameIndex: Int, winner: Player?, aIsSticks: Boolean, plies: Int) -> Unit = { _, _, _, _ -> }
    ): SelfPlayMatchResult {
        val prevProfiling = WaliAi.isProfilingEnabled
        WaliAi.isProfilingEnabled = false

        val startWall = System.currentTimeMillis()
        var winsAAsSticks = 0
        var lossesAAsSticks = 0
        var drawsWhenAIsSticks = 0
        var winsAAsStones = 0
        var lossesAAsStones = 0
        var drawsWhenAIsStones = 0
        var totalPlies = 0
        val trajectories = HashSet<String>()

        try {
            val results: List<SingleGameResult> = if (parallelism <= 1 || numGames <= 1) {
                (0 until numGames).map { idx ->
                    val r = playSingleSelfPlayGame(
                        gameIdx = idx,
                        agentA = agentA,
                        agentB = agentB,
                        baseSeed = baseSeed,
                        maxMovementPliesWithoutCapture = maxMovementPliesWithoutCapture,
                        maxTotalPlies = maxTotalPlies
                    )
                    onGameComplete(r.gameIdx, r.winner, r.aIsSticks, r.plies)
                    r
                }
            } else {
                val executor = java.util.concurrent.Executors.newFixedThreadPool(parallelism)
                try {
                    val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
                    val futures = (0 until numGames).map { idx ->
                        executor.submit<SingleGameResult> {
                            val r = playSingleSelfPlayGame(
                                gameIdx = idx,
                                agentA = agentA,
                                agentB = agentB,
                                baseSeed = baseSeed,
                                maxMovementPliesWithoutCapture = maxMovementPliesWithoutCapture,
                                maxTotalPlies = maxTotalPlies
                            )
                            val done = completedCount.incrementAndGet()
                            synchronized(trajectories) {
                                onGameComplete(done - 1, r.winner, r.aIsSticks, r.plies)
                            }
                            r
                        }
                    }
                    futures.map { it.get() }
                } finally {
                    executor.shutdown()
                }
            }

            for (r in results) {
                totalPlies += r.plies
                trajectories.add(r.trajectory)
                if (r.aIsSticks) {
                    when (r.winner) {
                        Player.STICKS -> winsAAsSticks++
                        Player.STONES -> lossesAAsSticks++
                        null -> drawsWhenAIsSticks++
                    }
                } else {
                    when (r.winner) {
                        Player.STONES -> winsAAsStones++
                        Player.STICKS -> lossesAAsStones++
                        null -> drawsWhenAIsStones++
                    }
                }
            }
        } finally {
            WaliAi.isProfilingEnabled = prevProfiling
            WaliAi.clearTranspositionTable()
        }

        val elapsedMs = maxOf(1L, System.currentTimeMillis() - startWall)
        val winsA = winsAAsSticks + winsAAsStones
        val winsB = lossesAAsSticks + lossesAAsStones
        val draws = drawsWhenAIsSticks + drawsWhenAIsStones

        return SelfPlayMatchResult(
            agentAName = agentA.name,
            agentBName = agentB.name,
            totalGames = numGames,
            budgetMsA = agentA.timeBudgetMs,
            budgetMsB = agentB.timeBudgetMs,
            winsA = winsA,
            winsB = winsB,
            draws = draws,
            winsAAsSticks = winsAAsSticks,
            lossesAAsSticks = lossesAAsSticks,
            drawsWhenAIsSticks = drawsWhenAIsSticks,
            winsAAsStones = winsAAsStones,
            lossesAAsStones = lossesAAsStones,
            drawsWhenAIsStones = drawsWhenAIsStones,
            totalPlies = totalPlies,
            uniqueTrajectories = trajectories.size,
            elapsedMs = elapsedMs
        )
    }
}


