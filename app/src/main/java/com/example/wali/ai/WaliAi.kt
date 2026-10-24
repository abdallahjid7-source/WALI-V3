package com.example.wali.ai

import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameState
import com.example.wali.model.Line
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import kotlin.random.Random

/**
 * Niveaux de difficulté disponibles pour l'adversaire contrôlé par l'ordinateur.
 */
enum class AiDifficulty {
    EASY,
    MEDIUM,
    HARD
}

/**
 * Représente une décision de coup prise par l'IA :
 * - [placement] : position choisie lors de la phase de pose (null en phase de déplacement).
 * - [moveFrom] : position d'origine lors du déplacement (null en phase de pose).
 * - [moveTo] : position de destination lors du déplacement (null en phase de pose).
 */
data class AiMove(
    val placement: Position? = null,
    val moveFrom: Position? = null,
    val moveTo: Position? = null,
    val captureTarget: Position? = null
)

/**
 * Module d'Intelligence Artificielle avancé pour le Wali Songhaï de Tombouctou.
 *
 * RÈGLE D'OR :
 * L'IA utilise UNIQUEMENT le moteur [WaliEngine] comme source de vérité des règles.
 * Elle ne choisit JAMAIS un coup en dehors de [WaliEngine.getValidPlacements] ou
 * [WaliEngine.getValidMoves], et n'applique les coups qu'au travers des méthodes du moteur.
 *
 * STRATÉGIE DE HAUT NIVEAU :
 * 1. Phase de pose (Placement) :
 *    - Construction méthodique de paires prêtes à être converties en lignes dès le tour 1 de la phase de déplacement.
 *    - Blocage proactif et systématique des paires adverses (déni de menaces futures).
 *    - Maîtrise des intersections clés (carrefours 2D pour préparer des fourchettes/chevals doubles).
 *    - Préservation critique des libertés (éviter de poser des pions prématurément asphyxiés à 0 ou 1 degré de liberté).
 *    - Anticipation sur 2 demi-coups (lookahead) pour contrer la meilleure réplique adverse.
 *
 * 2. Phase de déplacement (Mouvement) :
 *    - Minimax avec élagage Alpha-Beta et ordonnancement heuristique des coups (Move Ordering).
 *    - Détection et priorisation absolue des coups gagnants immédiats (élimination totale ou mat par étouffement/blocage).
 *    - Analyse fine des menaces à 1 temps (menace directe avec pion d'alimentation adjacent prêt à entrer dans la ligne).
 *    - Création et exploitation de fourchettes tactiques (menaces doubles imparables).
 *    - Maîtrise du va-et-vient (navette / moulin réutilisable).
 *    - Défense d'urgence : interposition sur les cases cibles adverses ou destruction des pions pivots.
 *
 * 3. Sélection tactique des cibles de capture :
 *    - Priorité 1 : Éliminer les pions adverses servant d'appui ou de pivot à une menace imminente d'alignement.
 *    - Priorité 2 : Casser les navettes actives adverses.
 *    - Priorité 3 : Neutraliser les pions adverses les plus mobiles tout en maintenant les pions déjà asphyxiés emprisonnés.
 *    - Priorité 4 : Dégager les axes de passage et libérer les pions alliés entravés.
 */
object WaliAi {

    // =========================================================================
    // GÉOMÉTRIE PRÉCALCULÉE DU PLATEAU 5x6 (30 CASES, 38 LIGNES DE TROIS)
    // =========================================================================

    /**
     * Représente une ligne candidate de 3 cases consécutives orthogonales.
     * Enregistre également les cases immédiatement adjacentes sur le même axe
     * pour vérifier la règle stricte du Wali : tout alignement de 4 ou plus est nul.
     */
    data class BoardLinePattern(
        val cells: List<Position>,
        val isHorizontal: Boolean,
        val boundaryBefore: Position?,
        val boundaryAfter: Position?
    )

    private val all38LinePatterns: List<BoardLinePattern> = buildList {
        // 20 lignes horizontales (5 lignes * 4 triplets par ligne)
        for (r in 0 until Position.ROWS) {
            for (c in 0..Position.COLS - 3) {
                val cells = listOf(Position(r, c), Position(r, c + 1), Position(r, c + 2))
                val before = if (c > 0) Position(r, c - 1) else null
                val after = if (c + 3 < Position.COLS) Position(r, c + 3) else null
                add(BoardLinePattern(cells, isHorizontal = true, before, after))
            }
        }
        // 18 lignes verticales (6 colonnes * 3 triplets par colonne)
        for (c in 0 until Position.COLS) {
            for (r in 0..Position.ROWS - 3) {
                val cells = listOf(Position(r, c), Position(r + 1, c), Position(r + 2, c))
                val before = if (r > 0) Position(r - 1, c) else null
                val after = if (r + 3 < Position.ROWS) Position(r + 3, c) else null
                add(BoardLinePattern(cells, isHorizontal = false, before, after))
            }
        }
    }

    /**
     * Voisins orthogonaux précalculés pour chaque case.
     */
    private val precomputedNeighbors: Map<Position, List<Position>> = Position.allPositions.associateWith { pos ->
        val list = mutableListOf<Position>()
        if (pos.row > 0) list.add(Position(pos.row - 1, pos.col))
        if (pos.row < Position.ROWS - 1) list.add(Position(pos.row + 1, pos.col))
        if (pos.col > 0) list.add(Position(pos.row, pos.col - 1))
        if (pos.col < Position.COLS - 1) list.add(Position(pos.row, pos.col + 1))
        list
    }

    /**
     * Paire de deux motifs d'alignement distincts se croisant sur une case commune [pivot].
     * Utilisé pour la détection et l'évaluation instantanée des doubles moulins (moulins croisés) à coût nul.
     */
    data class DoubleMillPair(
        val pivot: Position,
        val otherA1: Position,
        val otherA2: Position,
        val otherB1: Position,
        val otherB2: Position
    )

    private val allDoubleMillPairs: List<DoubleMillPair> = buildList {
        for (pos in Position.allPositions) {
            val patternsThroughPos = all38LinePatterns.filter { pos in it.cells }
            for (i in 0 until patternsThroughPos.size) {
                for (j in i + 1 until patternsThroughPos.size) {
                    val pA = patternsThroughPos[i]
                    val pB = patternsThroughPos[j]
                    val othersA = pA.cells.filter { it != pos }
                    val othersB = pB.cells.filter { it != pos }
                    add(
                        DoubleMillPair(
                            pivot = pos,
                            otherA1 = othersA[0],
                            otherA2 = othersA[1],
                            otherB1 = othersB[0],
                            otherB2 = othersB[1]
                        )
                    )
                }
            }
        }
    }
    class FastLinePattern(
        val c0: Int,
        val c1: Int,
        val c2: Int,
        val boundaryBefore: Int, // -1 si aucun
        val boundaryAfter: Int   // -1 si aucun
    )

    val fast38LinePatterns: List<FastLinePattern> = all38LinePatterns.map { pat ->
        val c0 = pat.cells[0].row * Position.COLS + pat.cells[0].col
        val c1 = pat.cells[1].row * Position.COLS + pat.cells[1].col
        val c2 = pat.cells[2].row * Position.COLS + pat.cells[2].col
        val bBefore = pat.boundaryBefore?.let { it.row * Position.COLS + it.col } ?: -1
        val bAfter = pat.boundaryAfter?.let { it.row * Position.COLS + it.col } ?: -1
        FastLinePattern(c0, c1, c2, bBefore, bAfter)
    }

    val fastNeighbors: Array<IntArray> = Array(Position.TOTAL_CELLS) { idx ->
        val pos = Position(idx / Position.COLS, idx % Position.COLS)
        val list = precomputedNeighbors[pos] ?: emptyList()
        IntArray(list.size) { i -> list[i].row * Position.COLS + list[i].col }
    }

    val fastIsCenter: BooleanArray = BooleanArray(Position.TOTAL_CELLS) { idx ->
        val r = idx / Position.COLS
        val c = idx % Position.COLS
        r in 1..3 && c in 1..4
    }

    class FastDoubleMillPair(
        val pivot: Int,
        val a1: Int,
        val a2: Int,
        val b1: Int,
        val b2: Int
    )

    val fastDoubleMillPairs: List<FastDoubleMillPair> = allDoubleMillPairs.map { pair ->
        FastDoubleMillPair(
            pivot = pair.pivot.row * Position.COLS + pair.pivot.col,
            a1 = pair.otherA1.row * Position.COLS + pair.otherA1.col,
            a2 = pair.otherA2.row * Position.COLS + pair.otherA2.col,
            b1 = pair.otherB1.row * Position.COLS + pair.otherB1.col,
            b2 = pair.otherB2.row * Position.COLS + pair.otherB2.col
        )
    }

    /**
     * Score de connectivité structurelle précalculé pour chaque case (0..29) :
     * combine le nombre de motifs de lignes de 3 contenant la case (2..6)
     * et le nombre de voisins orthogonaux (2..4).
     */
    val fastSquareConnectivityScore: IntArray = IntArray(Position.TOTAL_CELLS) { idx ->
        val pos = Position(idx / Position.COLS, idx % Position.COLS)
        val lineCount = all38LinePatterns.count { pos in it.cells } // 2 à 6
        val degree = fastNeighbors[idx].size                        // 2 à 4
        (lineCount - 2) * 12 + (degree - 2) * 18
    }

    fun hasEmptyNeighbor(flatBoard: Array<Player?>, cellIdx: Int): Boolean {
        val neighbors = fastNeighbors[cellIdx]
        for (i in 0 until neighbors.size) {
            if (flatBoard[neighbors[i]] == null) return true
        }
        return false
    }

    // =========================================================================
    // API PUBLIQUE DE L'IA
    // =========================================================================

    /**
     * Calcule le prochain coup pour l'IA selon le niveau de difficulté choisi.
     * Pour [AiDifficulty.HARD], utilise l'approfondissement itératif avec table de transposition Zobrist
     * dans la limite du budget de temps [timeBudgetMs], tout en restant interruptible via [isCancelled].
     * Retourne null si aucun coup légal n'existe (par exemple si la partie est terminée).
     */
    fun chooseMove(
        state: GameState,
        difficulty: AiDifficulty,
        random: Random = Random.Default,
        timeBudgetMs: Long = 1000L,
        isCancelled: () -> Boolean = { false }
    ): AiMove? {
        if (state.phase == Phase.GAME_OVER || state.winner != null) {
            return null
        }

        return when (difficulty) {
            AiDifficulty.EASY -> chooseEasyMove(state, random)
            AiDifficulty.MEDIUM -> chooseMediumMove(state, random)
            AiDifficulty.HARD -> chooseHardMove(state, random, timeBudgetMs, isCancelled)
        }
    }

    /**
     * Choisit quel pion adverse capturer lorsqu'une capture est requise.
     * [capturablePositions] : ensemble des pions adverses présents sur le plateau.
     */
    fun chooseCaptureTarget(
        state: GameState,
        capturablePositions: Set<Position>,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): Position? {
        if (capturablePositions.isEmpty()) return null

        return when (difficulty) {
            AiDifficulty.EASY -> capturablePositions.random(random)
            AiDifficulty.MEDIUM -> {
                // Niveau Moyen : cible en priorité les menaces et les pions centraux
                capturablePositions.maxByOrNull { pos ->
                    evaluateTargetValueMedium(state, pos)
                } ?: capturablePositions.random(random)
            }
            AiDifficulty.HARD -> {
                // Niveau Difficile : consomme en priorité la cible retenue par la recherche principale (variante principale du coup)
                if (lastPlannedCaptureTarget != null && lastPlannedCaptureTarget in capturablePositions) {
                    val target = lastPlannedCaptureTarget
                    lastPlannedCaptureTarget = null
                    return target
                }

                // Secours exceptionnel : si la cible n'est pas disponible (ex: appel direct sans recherche préalable),
                // signal explicite et recherche de secours
                System.err.println("SIGNAL: chooseCaptureTarget called without valid precomputed target in PV, using fallback")
                val aiPlayer = state.currentPlayer
                val orderedTargets = orderCaptureTargetsCheap(state, capturablePositions, aiPlayer)
                var bestTarget: Position? = null
                var bestScore = Int.MIN_VALUE
                val nodeCount = IntArray(1)
                val deadline = System.currentTimeMillis() + 400L

                for (target in orderedTargets) {
                    val afterCap = WaliEngine.capturePiece(state, target, recordHistory = false, validate = false)
                    val score = try {
                        minimaxAlphaBeta(
                            state = afterCap,
                            depth = 4,
                            alpha = bestScore,
                            beta = 100_000,
                            isMaximizing = false,
                            aiPlayer = aiPlayer,
                            deadline = deadline,
                            isCancelled = { false },
                            nodeCount = nodeCount,
                            plyFromRoot = 1
                        )
                    } catch (e: Exception) {
                        evaluateStateDeep(afterCap, aiPlayer)
                    }

                    if (score > bestScore || bestTarget == null) {
                        bestScore = score
                        bestTarget = target
                    }
                }
                bestTarget ?: orderedTargets.firstOrNull() ?: capturablePositions.first()
            }
        }
    }

    /**
     * Retourne tous les coups légaux possibles pour le joueur actif dans l'état [state].
     * Paire de (départ, arrivée).
     */
    fun getAllLegalMoves(state: GameState): List<Pair<Position, Position>> {
        if (state.phase != Phase.MOVEMENT || state.winner != null || state.pendingCaptures > 0) {
            return emptyList()
        }

        if (state.ruleOptions.mandatoryAlignment) {
            val obligatory = WaliEngine.getObligatoryMoves(state)
            if (obligatory.isNotEmpty()) {
                val moves = ArrayList<Pair<Position, Position>>()
                for ((from, destinations) in obligatory) {
                    for (to in destinations) {
                        moves.add(Pair(from, to))
                    }
                }
                return moves
            }
        }

        val player = state.currentPlayer
        val moves = ArrayList<Pair<Position, Position>>(32)

        for ((pos, piece) in state.board) {
            if (piece == player) {
                val neighbors = precomputedNeighbors[pos] ?: continue
                for (neighbor in neighbors) {
                    if (state.board[neighbor] == null) {
                        moves.add(Pair(pos, neighbor))
                    }
                }
            }
        }
        return moves
    }

    // =========================================================================
    // NIVEAU FACILE
    // =========================================================================

    private fun chooseEasyMove(state: GameState, random: Random): AiMove? {
        return when (state.phase) {
            Phase.PLACEMENT -> {
                val validPlacements = WaliEngine.getValidPlacements(state)
                if (validPlacements.isEmpty()) null
                else AiMove(placement = validPlacements.random(random))
            }
            Phase.MOVEMENT -> {
                if (WaliEngine.isPlacementRequired(state)) {
                    val validPlacements = WaliEngine.getValidPlacements(state)
                    if (validPlacements.isEmpty()) null
                    else AiMove(placement = validPlacements.random(random))
                } else {
                    val allMoves = getAllLegalMoves(state)
                    if (allMoves.isEmpty()) null
                    else {
                        val selected = allMoves.random(random)
                        AiMove(moveFrom = selected.first, moveTo = selected.second)
                    }
                }
            }
            Phase.GAME_OVER -> null
        }
    }

    // =========================================================================
    // NIVEAU MOYEN (TACTIQUE ET SOLIDE)
    // =========================================================================

    private fun chooseMediumMove(state: GameState, random: Random): AiMove? {
        return when (state.phase) {
            Phase.PLACEMENT -> {
                val validPlacements = WaliEngine.getValidPlacements(state)
                if (validPlacements.isEmpty()) return null

                val aiPlayer = state.currentPlayer
                val scored = validPlacements.map { pos ->
                    val score = evaluatePlacementPos(state, aiPlayer, pos)
                    pos to score
                }

                val maxScore = scored.maxOf { it.second }
                val best = scored.filter { it.second == maxScore }.map { it.first }
                AiMove(placement = best.random(random))
            }
            Phase.MOVEMENT -> {
                if (WaliEngine.isPlacementRequired(state)) {
                    val validPlacements = WaliEngine.getValidPlacements(state)
                    if (validPlacements.isEmpty()) return null
                    val aiPlayer = state.currentPlayer
                    val scored = validPlacements.map { pos ->
                        pos to evaluatePlacementPos(state, aiPlayer, pos)
                    }
                    val maxScore = scored.maxOf { it.second }
                    val best = scored.filter { it.second == maxScore }.map { it.first }
                    return AiMove(placement = best.random(random))
                }

                val allMoves = getAllLegalMoves(state)
                if (allMoves.isEmpty()) return null

                val aiPlayer = state.currentPlayer

                // 1. Coup gagnant direct ou capture immédiate
                val immediateWinningMoves = allMoves.filter { (from, to) ->
                    val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false)
                    nextState.winner == aiPlayer
                }
                if (immediateWinningMoves.isNotEmpty()) {
                    val chosen = immediateWinningMoves.random(random)
                    return AiMove(moveFrom = chosen.first, moveTo = chosen.second)
                }

                val captureMoves = allMoves.filter { (from, to) ->
                    val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false)
                    WaliEngine.canCapture(nextState)
                }
                if (captureMoves.isNotEmpty()) {
                    val chosen = captureMoves.random(random)
                    return AiMove(moveFrom = chosen.first, moveTo = chosen.second)
                }

                // 2. Recherche Alpha-Beta profondeur 3 avec évaluation tactique
                val ordered = orderMoves(state, allMoves, aiPlayer)
                val depth = 3
                var bestScore = Int.MIN_VALUE
                val bestCandidates = mutableListOf<Pair<Position, Position>>()

                for ((from, to) in ordered) {
                    val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false)
                    val stateAfterAction = resolveAiCapturesForSimulation(nextState, aiPlayer, AiDifficulty.MEDIUM, random)
                    val score = minimax(stateAfterAction, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, false, aiPlayer)

                    if (score > bestScore) {
                        bestScore = score
                        bestCandidates.clear()
                        bestCandidates.add(from to to)
                    } else if (score == bestScore) {
                        bestCandidates.add(from to to)
                    }
                }

                val chosen = (bestCandidates.ifEmpty { allMoves }).random(random)
                AiMove(moveFrom = chosen.first, moveTo = chosen.second)
            }
            Phase.GAME_OVER -> null
        }
    }

    // =========================================================================
    // HACHAGE ZOBRIST & TABLE DE TRANSPOSITION (NIVEAU DIFFICILE EXPERT)
    // =========================================================================

    enum class TtBound { EXACT, LOWER, UPPER }

    class SearchTimeoutException : Exception()

    private object Zobrist {
        val cells: Array<LongArray> = Array(Position.TOTAL_CELLS) { LongArray(2) }
        val turnStones: Long
        val phase: LongArray = LongArray(Phase.values().size)
        val pendingCaptures: LongArray = LongArray(4)
        val sticksRemaining: LongArray = LongArray(13)
        val stonesRemaining: LongArray = LongArray(13)
        val sticksPending: LongArray = LongArray(13)
        val stonesPending: LongArray = LongArray(13)

        init {
            val rng = Random(0x504E474841494CL) // "PNGHAI" seed déterministe
            for (i in 0 until Position.TOTAL_CELLS) {
                cells[i][0] = rng.nextLong()
                cells[i][1] = rng.nextLong()
            }
            turnStones = rng.nextLong()
            for (i in phase.indices) phase[i] = rng.nextLong()
            for (i in pendingCaptures.indices) pendingCaptures[i] = rng.nextLong()
            for (i in sticksRemaining.indices) sticksRemaining[i] = rng.nextLong()
            for (i in stonesRemaining.indices) stonesRemaining[i] = rng.nextLong()
            for (i in sticksPending.indices) sticksPending[i] = rng.nextLong()
            for (i in stonesPending.indices) stonesPending[i] = rng.nextLong()
        }
    }

    /**
     * Calcule l'empreinte Zobrist complète 64-bit d'une position de jeu.
     * Prend en compte l'occupation des 30 cases, le trait, la phase, les réserves,
     * les poses différées et l'état de capture en attente pour garantir zéro fausse collision.
     */
    fun computeZobristHash(state: GameState): Long {
        var h = 0L
        for ((pos, piece) in state.board) {
            if (piece != null) {
                val cellIdx = pos.row * Position.COLS + pos.col
                val pieceIdx = if (piece == Player.STICKS) 0 else 1
                h = h xor Zobrist.cells[cellIdx][pieceIdx]
            }
        }
        if (state.currentPlayer == Player.STONES) {
            h = h xor Zobrist.turnStones
        }
        h = h xor Zobrist.phase[state.phase.ordinal]
        h = h xor Zobrist.pendingCaptures[state.pendingCaptures.coerceIn(0, 3)]
        h = h xor Zobrist.sticksRemaining[state.sticksRemaining.coerceIn(0, 12)]
        h = h xor Zobrist.stonesRemaining[state.stonesRemaining.coerceIn(0, 12)]
        h = h xor Zobrist.sticksPending[state.sticksPendingPlacement.coerceIn(0, 12)]
        h = h xor Zobrist.stonesPending[state.stonesPendingPlacement.coerceIn(0, 12)]
        return h
    }

    /**
     * Table de transposition compacte et ultra-rapide (2^18 = 262 144 entrées).
     * Stocke sans aucune allocation d'objet : clé Zobrist, profondeur, score, type de borne et meilleur coup.
     */
    private const val TT_SIZE_BITS = 18
    private const val TT_SIZE = 1 shl TT_SIZE_BITS
    private const val TT_MASK = TT_SIZE - 1

    private val ttKeys = LongArray(TT_SIZE)
    private val ttDepths = IntArray(TT_SIZE)
    private val ttScores = IntArray(TT_SIZE)
    private val ttBounds = ByteArray(TT_SIZE)
    private val ttFromRow = ByteArray(TT_SIZE) { -1 }
    private val ttFromCol = ByteArray(TT_SIZE) { -1 }
    private val ttToRow = ByteArray(TT_SIZE) { -1 }
    private val ttToCol = ByteArray(TT_SIZE) { -1 }

    var isTranspositionTableEnabled: Boolean = true
    var lastSearchCompletedDepth: Int = 0
    var lastSearchNodeCount: Int = 0

    /**
     * Cible de capture planifiée par la recherche principale (variante principale du coup retenu).
     * Consommée directement par [chooseCaptureTarget] en niveau Difficile pour garantir qu'aucune divergence
     * n'existe entre le coup joué et la capture effectuée.
     */
    var lastPlannedCaptureTarget: Position? = null

    /**
     * Plafond de branchement sur les cibles de capture pour les plis profonds (> 2).
     * À la racine et dans les 2 premiers plis, TOUTES les cibles sont explorées exhaustivement.
     */
    var captureBranchingCapDeep: Int = 4

    /**
     * Plafond d'extension anti-horizon cumulée par branche (Phase 6a).
     * Permet de prolonger de 1 à 2 plis une feuille tactiquement instable
     * (alignement formé, capture en attente, asphyxie imminente).
     */
    var maxSearchExtensions: Int = 2

    var isProfilingEnabled: Boolean = false
    var profileTimeOrderMovesNanos: Long = 0L
    var profileTimeEngineMoveNanos: Long = 0L
    var profileTimeEvalNanos: Long = 0L
    var profileTimeTtNanos: Long = 0L
    var profileTimeLegalMovesNanos: Long = 0L
    var profileTimeCaptureNanos: Long = 0L
    var profileTotalNodes: Long = 0L

    fun resetProfiling() {
        profileTimeOrderMovesNanos = 0L
        profileTimeEngineMoveNanos = 0L
        profileTimeEvalNanos = 0L
        profileTimeTtNanos = 0L
        profileTimeLegalMovesNanos = 0L
        profileTimeCaptureNanos = 0L
        profileTotalNodes = 0L
    }

    fun clearTranspositionTable() {
        ttKeys.fill(0L)
        ttDepths.fill(0)
        ttScores.fill(0)
        ttBounds.fill(0)
        ttFromRow.fill(-1)
        ttFromCol.fill(-1)
        ttToRow.fill(-1)
        ttToCol.fill(-1)
        lastPlannedCaptureTarget = null
    }

    private fun ttLookup(key: Long): TtLookupResult? {
        if (!isTranspositionTableEnabled) return null
        val idx = key.toInt() and TT_MASK
        val boundByte = ttBounds[idx].toInt()
        if (boundByte == 0 || ttKeys[idx] != key) return null

        val bestMove = if (ttFromRow[idx] >= 0) {
            Pair(
                Position(ttFromRow[idx].toInt(), ttFromCol[idx].toInt()),
                Position(ttToRow[idx].toInt(), ttToCol[idx].toInt())
            )
        } else null

        val bestPlacement = if (ttFromRow[idx] == (-1).toByte() && ttToRow[idx] >= 0) {
            Position(ttToRow[idx].toInt(), ttToCol[idx].toInt())
        } else null

        val bound = when (boundByte) {
            1 -> TtBound.EXACT
            2 -> TtBound.LOWER
            3 -> TtBound.UPPER
            else -> return null
        }
        return TtLookupResult(ttDepths[idx], ttScores[idx], bound, bestMove, bestPlacement)
    }

    private fun ttStore(
        key: Long,
        depth: Int,
        score: Int,
        bound: TtBound,
        bestMove: Pair<Position, Position>?,
        bestPlacement: Position? = null
    ) {
        if (!isTranspositionTableEnabled) return
        val idx = key.toInt() and TT_MASK
        if (ttBounds[idx].toInt() == 0 || ttKeys[idx] == key || depth >= ttDepths[idx]) {
            ttKeys[idx] = key
            ttDepths[idx] = depth
            ttScores[idx] = score
            ttBounds[idx] = when (bound) {
                TtBound.EXACT -> 1
                TtBound.LOWER -> 2
                TtBound.UPPER -> 3
            }.toByte()

            if (bestMove != null) {
                ttFromRow[idx] = bestMove.first.row.toByte()
                ttFromCol[idx] = bestMove.first.col.toByte()
                ttToRow[idx] = bestMove.second.row.toByte()
                ttToCol[idx] = bestMove.second.col.toByte()
            } else if (bestPlacement != null) {
                ttFromRow[idx] = -1
                ttFromCol[idx] = -1
                ttToRow[idx] = bestPlacement.row.toByte()
                ttToCol[idx] = bestPlacement.col.toByte()
            } else {
                ttFromRow[idx] = -1
                ttFromCol[idx] = -1
                ttToRow[idx] = -1
                ttToCol[idx] = -1
            }
        }
    }

    private data class TtLookupResult(
        val depth: Int,
        val score: Int,
        val bound: TtBound,
        val bestMove: Pair<Position, Position>?,
        val bestPlacement: Position? = null
    )

    // =========================================================================
    // NIVEAU DIFFICILE (MAÎTRE DU WALI / RECHERCHE PROFONDE AVEC BUDGET TEMPS)
    // =========================================================================

    private fun chooseHardPlacementMove(
        state: GameState,
        random: Random,
        timeBudgetMs: Long,
        isCancelled: () -> Boolean
    ): AiMove? {
        val tLegal0 = if (isProfilingEnabled) System.nanoTime() else 0L
        val validPlacements = WaliEngine.getValidPlacements(state)
        if (isProfilingEnabled) profileTimeLegalMovesNanos += System.nanoTime() - tLegal0

        if (validPlacements.isEmpty()) return null
        if (validPlacements.size == 1) {
            lastSearchCompletedDepth = 1
            lastSearchNodeCount = 1
            return AiMove(placement = validPlacements.first())
        }

        val aiPlayer = state.currentPlayer
        val startTime = System.currentTimeMillis()
        val deadline = startTime + timeBudgetMs
        val nodeCounter = IntArray(1)
        resetMoveOrdering()

        var completedBestPlacement: Position? = null
        var completedBestScore = Int.MIN_VALUE
        var lastCompletedDepth = 0

        val tOrd0 = if (isProfilingEnabled) System.nanoTime() else 0L
        var placementList = orderPlacementsCheap(state, validPlacements, aiPlayer, 1)
        if (isProfilingEnabled) profileTimeOrderMovesNanos += System.nanoTime() - tOrd0
        completedBestPlacement = placementList.firstOrNull()

        for (targetDepth in 1..24) {
            val now = System.currentTimeMillis()
            if (targetDepth > 1 && (now >= deadline - 20L || isCancelled())) {
                break
            }

            var iterationBestPlacement: Position? = null
            var iterationBestScore = Int.MIN_VALUE
            var interrupted = false

            if (completedBestPlacement != null) {
                placementList = listOf(completedBestPlacement) + placementList.filter { it != completedBestPlacement }
            }

            var alpha = Int.MIN_VALUE + 1000
            val beta = Int.MAX_VALUE

            for (pos in placementList) {
                if (isCancelled() || System.currentTimeMillis() >= deadline) {
                    interrupted = true
                    break
                }

                val tPlace0 = if (isProfilingEnabled) System.nanoTime() else 0L
                val nextState = WaliEngine.placePiece(state, pos, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - tPlace0

                val score = try {
                    if (nextState.winner != null) {
                        if (nextState.winner == aiPlayer) 100_000 + targetDepth else -100_000 - targetDepth
                    } else {
                        minimaxAlphaBeta(
                            state = nextState,
                            depth = targetDepth - 1,
                            alpha = alpha,
                            beta = beta,
                            isMaximizing = (nextState.currentPlayer == aiPlayer),
                            aiPlayer = aiPlayer,
                            deadline = deadline,
                            isCancelled = isCancelled,
                            nodeCount = nodeCounter,
                            plyFromRoot = 1,
                            extensionsLeft = maxSearchExtensions,
                            lastMoveWasCapture = false
                        )
                    }
                } catch (e: SearchTimeoutException) {
                    interrupted = true
                    break
                }

                if (score > iterationBestScore) {
                    iterationBestScore = score
                    iterationBestPlacement = pos
                }
                alpha = maxOf(alpha, score)
            }

            if (!interrupted && iterationBestPlacement != null) {
                completedBestPlacement = iterationBestPlacement
                completedBestScore = iterationBestScore
                lastCompletedDepth = targetDepth

                if (completedBestScore >= 90_000) {
                    break
                }
            } else {
                break
            }
        }

        lastSearchCompletedDepth = lastCompletedDepth
        lastSearchNodeCount = nodeCounter[0]

        val chosen = completedBestPlacement ?: validPlacements.first()
        return AiMove(placement = chosen)
    }

    private fun chooseHardMove(
        state: GameState,
        random: Random,
        timeBudgetMs: Long,
        isCancelled: () -> Boolean
    ): AiMove? {
        return when (state.phase) {
            Phase.PLACEMENT -> {
                chooseHardPlacementMove(state, random, timeBudgetMs, isCancelled)
            }
            Phase.MOVEMENT -> {
                val aiPlayer = state.currentPlayer

                if (WaliEngine.isPlacementRequired(state)) {
                    return chooseHardPlacementMove(state, random, timeBudgetMs, isCancelled)
                }

                val tLegal0 = if (isProfilingEnabled) System.nanoTime() else 0L
                val allMoves = getAllLegalMoves(state)
                if (isProfilingEnabled) profileTimeLegalMovesNanos += System.nanoTime() - tLegal0
                if (allMoves.isEmpty()) return null

                // 1. Détection immédiate des coups gagnants à 1 temps
                for ((from, to) in allMoves) {
                    val tMove0 = if (isProfilingEnabled) System.nanoTime() else 0L
                    val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = false)
                    if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - tMove0
                    if (nextState.winner == aiPlayer) {
                        lastSearchCompletedDepth = 1
                        lastSearchNodeCount = 1
                        lastPlannedCaptureTarget = null
                        return AiMove(moveFrom = from, moveTo = to, captureTarget = null)
                    }
                    if (WaliEngine.canCapture(nextState)) {
                        val tCap0 = if (isProfilingEnabled) System.nanoTime() else 0L
                        val oppPieces = nextState.board.filter { it.value == aiPlayer.opponent }.keys
                        for (target in oppPieces) {
                            val afterCapture = WaliEngine.capturePiece(nextState, target, recordHistory = false, validate = false)
                            if (afterCapture.winner == aiPlayer) {
                                if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap0
                                lastSearchCompletedDepth = 1
                                lastSearchNodeCount = 1
                                lastPlannedCaptureTarget = target
                                return AiMove(moveFrom = from, moveTo = to, captureTarget = target)
                            }
                        }
                        if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap0
                    }
                }

                // 2. Approfondissement itératif avec table de transposition Zobrist et budget de temps réel
                val startTime = System.currentTimeMillis()
                val deadline = startTime + timeBudgetMs
                val nodeCounter = IntArray(1)
                resetMoveOrdering()

                var completedBestMove: Pair<Position, Position>? = null
                var completedBestScore = Int.MIN_VALUE
                var completedBestCapTarget: Position? = null
                var lastCompletedDepth = 0

                // Pré-tri initial des coups
                val tOrd0 = if (isProfilingEnabled) System.nanoTime() else 0L
                var moveList = orderMovesCheap(state, allMoves, aiPlayer, 1)
                if (isProfilingEnabled) profileTimeOrderMovesNanos += System.nanoTime() - tOrd0
                completedBestMove = moveList.firstOrNull()

                for (targetDepth in 1..24) {
                    // S'il reste moins de 20ms de budget, ne pas lancer une nouvelle profondeur
                    val now = System.currentTimeMillis()
                    if (targetDepth > 1 && (now >= deadline - 20L || isCancelled())) {
                        break
                    }

                    var iterationBestMove: Pair<Position, Position>? = null
                    var iterationBestScore = Int.MIN_VALUE
                    var iterationBestCapTarget: Position? = null
                    var interrupted = false

                    // Si on a un coup préféré de l'itération précédente, le placer impérativement en tête
                    if (completedBestMove != null) {
                        moveList = listOf(completedBestMove) + moveList.filter { it != completedBestMove }
                    }

                    var alpha = Int.MIN_VALUE + 1000
                    val beta = Int.MAX_VALUE

                    for ((from, to) in moveList) {
                        if (isCancelled() || System.currentTimeMillis() >= deadline) {
                            interrupted = true
                            break
                        }

                        val tMove1 = if (isProfilingEnabled) System.nanoTime() else 0L
                        val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = false)
                        if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - tMove1

                        var moveCapTarget: Position? = null
                        val score = try {
                            if (WaliEngine.canCapture(nextState)) {
                                val tCap1 = if (isProfilingEnabled) System.nanoTime() else 0L
                                val opponent = aiPlayer.opponent
                                val capturables = nextState.board.filter { it.value == opponent }.keys
                                if (capturables.isEmpty()) {
                                    if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap1
                                    minimaxAlphaBeta(
                                        state = nextState,
                                        depth = targetDepth - 1,
                                        alpha = alpha,
                                        beta = beta,
                                        isMaximizing = false,
                                        aiPlayer = aiPlayer,
                                        deadline = deadline,
                                        isCancelled = isCancelled,
                                        nodeCount = nodeCounter,
                                        plyFromRoot = 1,
                                        extensionsLeft = maxSearchExtensions,
                                        lastMoveWasCapture = true
                                    )
                                } else {
                                    val orderedTargets = orderCaptureTargetsCheap(nextState, capturables, aiPlayer)
                                    if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap1
                                    // À la racine : exploration exhaustive de TOUTES les cibles capturables
                                    var bestCapScore = Int.MIN_VALUE
                                    var currentAlpha = alpha
                                    for (target in orderedTargets) {
                                        val tCap2 = if (isProfilingEnabled) System.nanoTime() else 0L
                                        val afterCap = WaliEngine.capturePiece(nextState, target, recordHistory = false, validate = false)
                                        if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap2
                                        val s = minimaxAlphaBeta(
                                            state = afterCap,
                                            depth = targetDepth - 1,
                                            alpha = currentAlpha,
                                            beta = beta,
                                            isMaximizing = false,
                                            aiPlayer = aiPlayer,
                                            deadline = deadline,
                                            isCancelled = isCancelled,
                                            nodeCount = nodeCounter,
                                            plyFromRoot = 1,
                                            extensionsLeft = maxSearchExtensions,
                                            lastMoveWasCapture = true
                                        )
                                        if (s > bestCapScore || moveCapTarget == null) {
                                            bestCapScore = s
                                            moveCapTarget = target
                                        }
                                        currentAlpha = maxOf(currentAlpha, s)
                                        if (beta <= currentAlpha) break
                                    }
                                    bestCapScore
                                }
                            } else {
                                minimaxAlphaBeta(
                                    state = nextState,
                                    depth = targetDepth - 1,
                                    alpha = alpha,
                                    beta = beta,
                                    isMaximizing = false,
                                    aiPlayer = aiPlayer,
                                    deadline = deadline,
                                    isCancelled = isCancelled,
                                    nodeCount = nodeCounter,
                                    plyFromRoot = 1,
                                    extensionsLeft = maxSearchExtensions,
                                    lastMoveWasCapture = false
                                )
                            }
                        } catch (e: SearchTimeoutException) {
                            interrupted = true
                            break
                        }

                        if (score > iterationBestScore) {
                            iterationBestScore = score
                            iterationBestMove = from to to
                            iterationBestCapTarget = moveCapTarget
                        }
                        alpha = maxOf(alpha, score)
                    }

                    // Ne sauvegarder le meilleur coup QUE si l'itération entière s'est terminée avec succès
                    if (!interrupted && iterationBestMove != null) {
                        completedBestMove = iterationBestMove
                        completedBestScore = iterationBestScore
                        completedBestCapTarget = iterationBestCapTarget
                        lastCompletedDepth = targetDepth

                        // Si gain forcé absolu trouvé, arrêter la recherche immédiatement
                        if (completedBestScore >= 90_000) {
                            break
                        }
                    } else {
                        break // Interrompu par timeout ou annulation : on conserve completedBestMove de l'itération précédente
                    }
                }

                lastSearchCompletedDepth = lastCompletedDepth
                lastSearchNodeCount = nodeCounter[0]
                lastPlannedCaptureTarget = completedBestCapTarget

                val chosen = completedBestMove ?: allMoves.first()
                AiMove(moveFrom = chosen.first, moveTo = chosen.second, captureTarget = completedBestCapTarget)
            }
            Phase.GAME_OVER -> null
        }
    }

    /**
     * Évalue l'issue d'un déplacement simulé en branchant sur tous les choix possibles
     * de pion capturé si une ligne de trois a été formée (nœud de capture explicite).
     * Instrumenté avec sa propre sonde [profileTimeCaptureNanos] (hors descente récursive).
     */
    private fun evaluateMoveOutcome(
        nextState: GameState,
        playerWhoMoved: Player,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
        aiPlayer: Player,
        deadline: Long,
        isCancelled: () -> Boolean,
        nodeCount: IntArray,
        plyFromRoot: Int,
        extensionsLeft: Int = maxSearchExtensions
    ): Int {
        if (!WaliEngine.canCapture(nextState)) {
            return minimaxAlphaBeta(
                state = nextState,
                depth = depth - 1,
                alpha = alpha,
                beta = beta,
                isMaximizing = !isMaximizing,
                aiPlayer = aiPlayer,
                deadline = deadline,
                isCancelled = isCancelled,
                nodeCount = nodeCount,
                plyFromRoot = plyFromRoot + 1,
                extensionsLeft = extensionsLeft,
                lastMoveWasCapture = false
            )
        }

        val tCap0 = if (isProfilingEnabled) System.nanoTime() else 0L
        val opponent = playerWhoMoved.opponent
        val capturables = nextState.board.filter { it.value == opponent }.keys
        if (capturables.isEmpty()) {
            if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap0
            return minimaxAlphaBeta(
                state = nextState,
                depth = depth - 1,
                alpha = alpha,
                beta = beta,
                isMaximizing = !isMaximizing,
                aiPlayer = aiPlayer,
                deadline = deadline,
                isCancelled = isCancelled,
                nodeCount = nodeCount,
                plyFromRoot = plyFromRoot + 1,
                extensionsLeft = extensionsLeft,
                lastMoveWasCapture = true
            )
        }

        val orderedTargets = orderCaptureTargetsCheap(nextState, capturables, playerWhoMoved)
        // Correctif 1 : À la racine et dans les 2 premiers plis, explore TOUTES les cibles sans plafond.
        // Plus profond dans l'arbre, applique le plafond pour borner le facteur de branchement.
        val targetsToExplore = if (plyFromRoot <= 2 || orderedTargets.size <= captureBranchingCapDeep) {
            orderedTargets
        } else {
            orderedTargets.take(captureBranchingCapDeep)
        }
        if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap0

        return if (isMaximizing) {
            // L'IA maximise son gain parmi toutes les cibles capturables
            var bestCapScore = Int.MIN_VALUE
            var currentAlpha = alpha
            for (target in targetsToExplore) {
                val tCap1 = if (isProfilingEnabled) System.nanoTime() else 0L
                val afterCap = WaliEngine.capturePiece(nextState, target, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap1
                val score = minimaxAlphaBeta(
                    state = afterCap,
                    depth = depth - 1,
                    alpha = currentAlpha,
                    beta = beta,
                    isMaximizing = false,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount,
                    plyFromRoot = plyFromRoot + 1,
                    extensionsLeft = extensionsLeft,
                    lastMoveWasCapture = true
                )
                bestCapScore = maxOf(bestCapScore, score)
                currentAlpha = maxOf(currentAlpha, score)
                if (beta <= currentAlpha) break // Coupe Bêta sur le choix de capture
            }
            bestCapScore
        } else {
            // L'adversaire minimise le score de l'IA parmi toutes les cibles capturables
            var bestCapScore = Int.MAX_VALUE
            var currentBeta = beta
            for (target in targetsToExplore) {
                val tCap1 = if (isProfilingEnabled) System.nanoTime() else 0L
                val afterCap = WaliEngine.capturePiece(nextState, target, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeCaptureNanos += System.nanoTime() - tCap1
                val score = minimaxAlphaBeta(
                    state = afterCap,
                    depth = depth - 1,
                    alpha = alpha,
                    beta = currentBeta,
                    isMaximizing = true,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount,
                    plyFromRoot = plyFromRoot + 1,
                    extensionsLeft = extensionsLeft,
                    lastMoveWasCapture = true
                )
                bestCapScore = minOf(bestCapScore, score)
                currentBeta = minOf(currentBeta, score)
                if (currentBeta <= alpha) break // Coupe Alpha sur le choix de capture
            }
            bestCapScore
        }
    }

    /**
     * Recherche Minimax Alpha-Bêta optimisée avec table de transposition Zobrist
     * et extensions anti-horizon bornées (Phase 6a).
     * Utilise exclusivement [WaliEngine] pour chaque coup, transition et capture.
     */
    internal fun minimaxAlphaBeta(
        state: GameState,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
        aiPlayer: Player,
        deadline: Long,
        isCancelled: () -> Boolean,
        nodeCount: IntArray,
        plyFromRoot: Int = 0,
        extensionsLeft: Int = maxSearchExtensions,
        lastMoveWasCapture: Boolean = false
    ): Int {
        // Vérification coopérative du timeout tous les 64 nœuds pour minimiser le coût système
        nodeCount[0]++
        if (isProfilingEnabled) profileTotalNodes++
        if ((nodeCount[0] and 63) == 0) {
            if (isCancelled() || System.currentTimeMillis() >= deadline) {
                throw SearchTimeoutException()
            }
        }

        // État terminal (victoire ou défaite)
        if (state.winner != null) {
            val bonusDepth = maxOf(0, depth)
            return if (state.winner == aiPlayer) 100_000 + bonusDepth else -100_000 - bonusDepth
        }

        // Si une capture est directement en attente sur l'état courant, la résoudre obligatoirement
        if (state.pendingCaptures > 0) {
            val nextDepth = if (depth <= 0) 1 else depth
            val nextExt = if (depth <= 0) maxOf(0, extensionsLeft - 1) else extensionsLeft
            return evaluateMoveOutcome(
                nextState = state,
                playerWhoMoved = state.currentPlayer,
                depth = nextDepth,
                alpha = alpha,
                beta = beta,
                isMaximizing = (state.currentPlayer == aiPlayer),
                aiPlayer = aiPlayer,
                deadline = deadline,
                isCancelled = isCancelled,
                nodeCount = nodeCount,
                plyFromRoot = plyFromRoot,
                extensionsLeft = nextExt
            )
        }

        // Profondeur nominale atteinte : vérification d'extension anti-horizon (Phase 6a) sans double lecture du plateau
        var effectiveDepth = depth
        var currentExtensionsLeft = extensionsLeft
        if (effectiveDepth <= 0) {
            if (currentExtensionsLeft > 0 && lastMoveWasCapture) {
                // Extension immédiate à coût nul : un alignement vient d'être formé sur le dernier demi-coup
                effectiveDepth = 1
                currentExtensionsLeft -= 1
            } else {
                val t4 = if (isProfilingEnabled) System.nanoTime() else 0L
                val evalOrExtend = evaluateLeafOrSignalLowMobilityExtension(
                    state = state,
                    aiPlayer = aiPlayer,
                    canExtendOnLowMobility = (currentExtensionsLeft > 0 && state.phase == Phase.MOVEMENT)
                )
                if (isProfilingEnabled) profileTimeEvalNanos += System.nanoTime() - t4

                if (evalOrExtend == EXTEND_LOW_MOBILITY_SENTINEL) {
                    effectiveDepth = 1
                    currentExtensionsLeft -= 1
                } else {
                    return evalOrExtend
                }
            }
        }

        val t0 = if (isProfilingEnabled) System.nanoTime() else 0L
        val hash = computeZobristHash(state)
        val ttEntry = ttLookup(hash)
        if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - t0

        var currentAlpha = alpha
        var currentBeta = beta

        if (ttEntry != null && ttEntry.depth >= effectiveDepth) {
            when (ttEntry.bound) {
                TtBound.EXACT -> return ttEntry.score
                TtBound.LOWER -> currentAlpha = maxOf(currentAlpha, ttEntry.score)
                TtBound.UPPER -> currentBeta = minOf(currentBeta, ttEntry.score)
            }
            if (currentAlpha >= currentBeta) {
                return ttEntry.score
            }
        }

        if (state.phase == Phase.PLACEMENT || WaliEngine.isPlacementRequired(state)) {
            val t1 = if (isProfilingEnabled) System.nanoTime() else 0L
            val placements = WaliEngine.getValidPlacements(state)
            if (isProfilingEnabled) profileTimeLegalMovesNanos += System.nanoTime() - t1

            if (placements.isEmpty()) {
                if (state.phase == Phase.PLACEMENT) {
                    val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                    val transState = WaliEngine.checkAndHandlePlacementBlockage(state)
                    if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                    return minimaxAlphaBeta(
                        state = transState,
                        depth = effectiveDepth,
                        alpha = currentAlpha,
                        beta = currentBeta,
                        isMaximizing = (transState.currentPlayer == aiPlayer),
                        aiPlayer = aiPlayer,
                        deadline = deadline,
                        isCancelled = isCancelled,
                        nodeCount = nodeCount,
                        plyFromRoot = plyFromRoot,
                        extensionsLeft = currentExtensionsLeft,
                        lastMoveWasCapture = false
                    )
                } else {
                    return if (isMaximizing) -100_000 - effectiveDepth else 100_000 + effectiveDepth
                }
            }

            val currentPlayer = if (isMaximizing) aiPlayer else aiPlayer.opponent
            val ttPlacement = ttEntry?.bestPlacement
            var bestPlacementThisNode: Position? = null

            if (isMaximizing) {
                var maxEval = Int.MIN_VALUE
                if (ttPlacement != null && ttPlacement in placements) {
                    val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                    val nextState = WaliEngine.placePiece(state, ttPlacement, recordHistory = false, validate = false)
                    if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                    val eval = if (nextState.winner != null) {
                        if (nextState.winner == aiPlayer) 100_000 + effectiveDepth else -100_000 - effectiveDepth
                    } else {
                        minimaxAlphaBeta(
                            state = nextState,
                            depth = effectiveDepth - 1,
                            alpha = currentAlpha,
                            beta = currentBeta,
                            isMaximizing = (nextState.currentPlayer == aiPlayer),
                            aiPlayer = aiPlayer,
                            deadline = deadline,
                            isCancelled = isCancelled,
                            nodeCount = nodeCount,
                            plyFromRoot = plyFromRoot + 1,
                            extensionsLeft = currentExtensionsLeft,
                            lastMoveWasCapture = false
                        )
                    }
                    if (eval > maxEval) {
                        maxEval = eval
                        bestPlacementThisNode = ttPlacement
                    }
                    currentAlpha = maxOf(currentAlpha, eval)
                    if (currentBeta <= currentAlpha) {
                        recordPlacementBetaCutoff(currentPlayer, ttPlacement, effectiveDepth)
                        val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
                        ttStore(hash, effectiveDepth, maxEval, TtBound.LOWER, bestMove = null, bestPlacement = bestPlacementThisNode)
                        if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
                        return maxEval
                    }
                }

                val t2 = if (isProfilingEnabled) System.nanoTime() else 0L
                val remainingPlacements = if (ttPlacement != null && ttPlacement in placements) {
                    placements.filter { it != ttPlacement }
                } else {
                    placements
                }
                val orderedPlacements = orderPlacementsCheap(state, remainingPlacements, currentPlayer, effectiveDepth)
                if (isProfilingEnabled) profileTimeOrderMovesNanos += System.nanoTime() - t2

                for (pos in orderedPlacements) {
                    val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                    val nextState = WaliEngine.placePiece(state, pos, recordHistory = false, validate = false)
                    if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                    val eval = if (nextState.winner != null) {
                        if (nextState.winner == aiPlayer) 100_000 + effectiveDepth else -100_000 - effectiveDepth
                    } else {
                        minimaxAlphaBeta(
                            state = nextState,
                            depth = effectiveDepth - 1,
                            alpha = currentAlpha,
                            beta = currentBeta,
                            isMaximizing = (nextState.currentPlayer == aiPlayer),
                            aiPlayer = aiPlayer,
                            deadline = deadline,
                            isCancelled = isCancelled,
                            nodeCount = nodeCount,
                            plyFromRoot = plyFromRoot + 1,
                            extensionsLeft = currentExtensionsLeft,
                            lastMoveWasCapture = false
                        )
                    }
                    if (eval > maxEval) {
                        maxEval = eval
                        bestPlacementThisNode = pos
                    }
                    currentAlpha = maxOf(currentAlpha, eval)
                    if (currentBeta <= currentAlpha) {
                        recordPlacementBetaCutoff(currentPlayer, pos, effectiveDepth)
                        break
                    }
                }

                val bound = when {
                    maxEval <= alpha -> TtBound.UPPER
                    maxEval >= beta -> TtBound.LOWER
                    else -> TtBound.EXACT
                }
                val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
                ttStore(hash, effectiveDepth, maxEval, bound, bestMove = null, bestPlacement = bestPlacementThisNode)
                if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
                return maxEval
            } else {
                var minEval = Int.MAX_VALUE
                if (ttPlacement != null && ttPlacement in placements) {
                    val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                    val nextState = WaliEngine.placePiece(state, ttPlacement, recordHistory = false, validate = false)
                    if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                    val eval = if (nextState.winner != null) {
                        if (nextState.winner == aiPlayer) 100_000 + effectiveDepth else -100_000 - effectiveDepth
                    } else {
                        minimaxAlphaBeta(
                            state = nextState,
                            depth = effectiveDepth - 1,
                            alpha = currentAlpha,
                            beta = currentBeta,
                            isMaximizing = (nextState.currentPlayer == aiPlayer),
                            aiPlayer = aiPlayer,
                            deadline = deadline,
                            isCancelled = isCancelled,
                            nodeCount = nodeCount,
                            plyFromRoot = plyFromRoot + 1,
                            extensionsLeft = currentExtensionsLeft,
                            lastMoveWasCapture = false
                        )
                    }
                    if (eval < minEval) {
                        minEval = eval
                        bestPlacementThisNode = ttPlacement
                    }
                    currentBeta = minOf(currentBeta, eval)
                    if (currentBeta <= currentAlpha) {
                        recordPlacementBetaCutoff(currentPlayer, ttPlacement, effectiveDepth)
                        val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
                        ttStore(hash, effectiveDepth, minEval, TtBound.UPPER, bestMove = null, bestPlacement = bestPlacementThisNode)
                        if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
                        return minEval
                    }
                }

                val t2 = if (isProfilingEnabled) System.nanoTime() else 0L
                val remainingPlacements = if (ttPlacement != null && ttPlacement in placements) {
                    placements.filter { it != ttPlacement }
                } else {
                    placements
                }
                val orderedPlacements = orderPlacementsCheap(state, remainingPlacements, currentPlayer, effectiveDepth)
                if (isProfilingEnabled) profileTimeOrderMovesNanos += System.nanoTime() - t2

                for (pos in orderedPlacements) {
                    val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                    val nextState = WaliEngine.placePiece(state, pos, recordHistory = false, validate = false)
                    if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                    val eval = if (nextState.winner != null) {
                        if (nextState.winner == aiPlayer) 100_000 + effectiveDepth else -100_000 - effectiveDepth
                    } else {
                        minimaxAlphaBeta(
                            state = nextState,
                            depth = effectiveDepth - 1,
                            alpha = currentAlpha,
                            beta = currentBeta,
                            isMaximizing = (nextState.currentPlayer == aiPlayer),
                            aiPlayer = aiPlayer,
                            deadline = deadline,
                            isCancelled = isCancelled,
                            nodeCount = nodeCount,
                            plyFromRoot = plyFromRoot + 1,
                            extensionsLeft = currentExtensionsLeft,
                            lastMoveWasCapture = false
                        )
                    }
                    if (eval < minEval) {
                        minEval = eval
                        bestPlacementThisNode = pos
                    }
                    currentBeta = minOf(currentBeta, eval)
                    if (currentBeta <= currentAlpha) {
                        recordPlacementBetaCutoff(currentPlayer, pos, effectiveDepth)
                        break
                    }
                }

                val bound = when {
                    minEval <= alpha -> TtBound.UPPER
                    minEval >= beta -> TtBound.LOWER
                    else -> TtBound.EXACT
                }
                val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
                ttStore(hash, effectiveDepth, minEval, bound, bestMove = null, bestPlacement = bestPlacementThisNode)
                if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
                return minEval
            }
        }

        val t1 = if (isProfilingEnabled) System.nanoTime() else 0L
        val moves = getAllLegalMoves(state)
        if (isProfilingEnabled) profileTimeLegalMovesNanos += System.nanoTime() - t1

        if (moves.isEmpty()) {
            // Aucun coup légal : le joueur actif est étouffé (défaite par blocage)
            return if (isMaximizing) -100_000 - effectiveDepth else 100_000 + effectiveDepth
        }

        val currentPlayer = if (isMaximizing) aiPlayer else aiPlayer.opponent
        val ttMove = ttEntry?.bestMove
        var bestMoveThisNode: Pair<Position, Position>? = null

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE

            // 1. Essai prioritaire immédiat du coup issu de la table de transposition
            if (ttMove != null && ttMove in moves) {
                val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                val nextState = WaliEngine.movePiece(state, ttMove.first, ttMove.second, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                val eval = evaluateMoveOutcome(
                    nextState = nextState,
                    playerWhoMoved = currentPlayer,
                    depth = effectiveDepth,
                    alpha = currentAlpha,
                    beta = currentBeta,
                    isMaximizing = true,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount,
                    plyFromRoot = plyFromRoot,
                    extensionsLeft = currentExtensionsLeft
                )

                if (eval > maxEval) {
                    maxEval = eval
                    bestMoveThisNode = ttMove
                }
                currentAlpha = maxOf(currentAlpha, eval)
                if (currentBeta <= currentAlpha) {
                    recordBetaCutoff(currentPlayer, ttMove, effectiveDepth)
                    val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
                    ttStore(hash, effectiveDepth, maxEval, TtBound.LOWER, bestMoveThisNode)
                    if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
                    return maxEval
                }
            }

            // 2. Tri et évaluation des autres coups légaux
            val t2 = if (isProfilingEnabled) System.nanoTime() else 0L
            val remainingMoves = if (ttMove != null && ttMove in moves) {
                moves.filter { it != ttMove }
            } else {
                moves
            }
            val orderedMoves = orderMovesCheap(state, remainingMoves, currentPlayer, effectiveDepth)
            if (isProfilingEnabled) profileTimeOrderMovesNanos += System.nanoTime() - t2

            for ((from, to) in orderedMoves) {
                val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                val eval = evaluateMoveOutcome(
                    nextState = nextState,
                    playerWhoMoved = currentPlayer,
                    depth = effectiveDepth,
                    alpha = currentAlpha,
                    beta = currentBeta,
                    isMaximizing = true,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount,
                    plyFromRoot = plyFromRoot,
                    extensionsLeft = currentExtensionsLeft
                )

                if (eval > maxEval) {
                    maxEval = eval
                    bestMoveThisNode = from to to
                }
                currentAlpha = maxOf(currentAlpha, eval)
                if (currentBeta <= currentAlpha) {
                    recordBetaCutoff(currentPlayer, from to to, effectiveDepth)
                    break // Coupe Bêta
                }
            }

            val bound = when {
                maxEval <= alpha -> TtBound.UPPER
                maxEval >= beta -> TtBound.LOWER
                else -> TtBound.EXACT
            }
            val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
            ttStore(hash, effectiveDepth, maxEval, bound, bestMoveThisNode)
            if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE

            // 1. Essai prioritaire immédiat du coup issu de la table de transposition
            if (ttMove != null && ttMove in moves) {
                val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                val nextState = WaliEngine.movePiece(state, ttMove.first, ttMove.second, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                val eval = evaluateMoveOutcome(
                    nextState = nextState,
                    playerWhoMoved = currentPlayer,
                    depth = effectiveDepth,
                    alpha = currentAlpha,
                    beta = currentBeta,
                    isMaximizing = false,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount,
                    plyFromRoot = plyFromRoot,
                    extensionsLeft = currentExtensionsLeft
                )

                if (eval < minEval) {
                    minEval = eval
                    bestMoveThisNode = ttMove
                }
                currentBeta = minOf(currentBeta, eval)
                if (currentBeta <= currentAlpha) {
                    recordBetaCutoff(currentPlayer, ttMove, effectiveDepth)
                    val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
                    ttStore(hash, effectiveDepth, minEval, TtBound.UPPER, bestMoveThisNode)
                    if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
                    return minEval
                }
            }

            // 2. Tri et évaluation des autres coups légaux
            val t2 = if (isProfilingEnabled) System.nanoTime() else 0L
            val remainingMoves = if (ttMove != null && ttMove in moves) {
                moves.filter { it != ttMove }
            } else {
                moves
            }
            val orderedMoves = orderMovesCheap(state, remainingMoves, currentPlayer, effectiveDepth)
            if (isProfilingEnabled) profileTimeOrderMovesNanos += System.nanoTime() - t2

            for ((from, to) in orderedMoves) {
                val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                val eval = evaluateMoveOutcome(
                    nextState = nextState,
                    playerWhoMoved = currentPlayer,
                    depth = effectiveDepth,
                    alpha = currentAlpha,
                    beta = currentBeta,
                    isMaximizing = false,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount,
                    plyFromRoot = plyFromRoot,
                    extensionsLeft = currentExtensionsLeft
                )

                if (eval < minEval) {
                    minEval = eval
                    bestMoveThisNode = from to to
                }
                currentBeta = minOf(currentBeta, eval)
                if (currentBeta <= currentAlpha) {
                    recordBetaCutoff(currentPlayer, from to to, depth)
                    break // Coupe Alpha
                }
            }

            val bound = when {
                minEval <= alpha -> TtBound.UPPER
                minEval >= beta -> TtBound.LOWER
                else -> TtBound.EXACT
            }
            val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
            ttStore(hash, effectiveDepth, minEval, bound, bestMoveThisNode)
            if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
            return minEval
        }
    }

    // =========================================================================
    // MINIMAX NIVEAU MOYEN (INCHANGÉ — PROFONDEUR FIXE 3)
    // =========================================================================

    private fun minimax(
        state: GameState,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
        aiPlayer: Player
    ): Int {
        // État terminal (victoire ou défaite)
        if (state.winner != null) {
            return if (state.winner == aiPlayer) 100_000 + depth else -100_000 - depth
        }

        // Profondeur atteinte : évaluation heuristique complète de la position
        if (depth <= 0) {
            return evaluateStateDeep(state, aiPlayer)
        }

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            val moves = getAllLegalMoves(state)
            if (moves.isEmpty()) {
                return -100_000 - depth
            }

            val ordered = orderMoves(state, moves, aiPlayer)
            for ((from, to) in ordered) {
                val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false)
                val stateAfterAction = resolveAiCapturesForSimulation(nextState, aiPlayer, AiDifficulty.MEDIUM, Random.Default)
                val eval = minimax(stateAfterAction, depth - 1, currentAlpha, currentBeta, false, aiPlayer)

                maxEval = maxOf(maxEval, eval)
                currentAlpha = maxOf(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break // Coupe Bêta
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            val oppPlayer = aiPlayer.opponent
            val moves = getAllLegalMoves(state)
            if (moves.isEmpty()) {
                return 100_000 + depth
            }

            val ordered = orderMoves(state, moves, oppPlayer)
            for ((from, to) in ordered) {
                val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false)
                val stateAfterAction = resolveAiCapturesForSimulation(nextState, oppPlayer, AiDifficulty.MEDIUM, Random.Default)
                val eval = minimax(stateAfterAction, depth - 1, currentAlpha, currentBeta, true, aiPlayer)

                minEval = minOf(minEval, eval)
                currentBeta = minOf(currentBeta, eval)
                if (currentBeta <= currentAlpha) break // Coupe Alpha
            }
            return minEval
        }
    }

    /**
     * Résout la capture déterministe optimale pour les besoins de la simulation Minimax.
     */
    private fun resolveAiCapturesForSimulation(
        state: GameState,
        playerWhoMoved: Player,
        difficulty: AiDifficulty,
        random: Random,
        recordHistory: Boolean = false
    ): GameState {
        if (!WaliEngine.canCapture(state)) return state
        val targetOpponent = playerWhoMoved.opponent
        val capturables = state.board.filter { it.value == targetOpponent }.keys
        val bestTarget = chooseCaptureTarget(state, capturables, difficulty, random) ?: return state
        return WaliEngine.capturePiece(state, bestTarget, recordHistory = recordHistory, validate = false)
    }

    // =========================================================================
    // ORDONNANCEMENT ULTRA-RAPIDE (KILLER, HISTORIQUE, DÉTECTION STATIQUE)
    // =========================================================================

    private const val MAX_SEARCH_DEPTH = 32
    private val killerMoves = Array(MAX_SEARCH_DEPTH) { arrayOfNulls<Pair<Position, Position>>(2) }
    private val historyTable = Array(2) { IntArray(Position.TOTAL_CELLS * Position.TOTAL_CELLS) }
    private val placementHistoryTable = Array(2) { IntArray(Position.TOTAL_CELLS) }

    fun resetMoveOrdering() {
        for (d in 0 until MAX_SEARCH_DEPTH) {
            killerMoves[d][0] = null
            killerMoves[d][1] = null
        }
        historyTable[0].fill(0)
        historyTable[1].fill(0)
        placementHistoryTable[0].fill(0)
        placementHistoryTable[1].fill(0)
    }

    private fun recordBetaCutoff(player: Player, move: Pair<Position, Position>, depth: Int) {
        if (depth in 0 until MAX_SEARCH_DEPTH) {
            val k = killerMoves[depth]
            if (k[0] != move) {
                k[1] = k[0]
                k[0] = move
            }
        }
        val fromIdx = move.first.row * Position.COLS + move.first.col
        val toIdx = move.second.row * Position.COLS + move.second.col
        val playerIdx = if (player == Player.STICKS) 0 else 1
        historyTable[playerIdx][fromIdx * Position.TOTAL_CELLS + toIdx] += depth * depth
    }

    private fun recordPlacementBetaCutoff(player: Player, pos: Position, depth: Int) {
        val playerIdx = if (player == Player.STICKS) 0 else 1
        val posIdx = pos.row * Position.COLS + pos.col
        placementHistoryTable[playerIdx][posIdx] += depth * depth
    }

    private val fastLinePartnersByIdx: Array<IntArray> = Array(Position.TOTAL_CELLS) { idx ->
        val pos = Position(idx / Position.COLS, idx % Position.COLS)
        val pairs = mutableListOf<Int>()
        for (pattern in all38LinePatterns) {
            if (pos in pattern.cells) {
                val others = pattern.cells.filter { it != pos }
                pairs.add(others[0].row * Position.COLS + others[0].col)
                pairs.add(others[1].row * Position.COLS + others[1].col)
            }
        }
        pairs.toIntArray()
    }

    private fun scorePlacementCheapFast(
        flatBoard: Array<Player?>,
        pos: Position,
        player: Player
    ): Int {
        var score = 0
        val opponent = player.opponent
        val posIdx = pos.row * Position.COLS + pos.col

        // 1. Historique des coupures
        val playerIdx = if (player == Player.STICKS) 0 else 1
        score += placementHistoryTable[playerIdx][posIdx]

        // 2. Contrôle central
        if (pos.row in 1..3 && pos.col in 1..4) {
            score += 300
            if (pos.col in 2..3) {
                score += 200
            }
        }

        // 3. Libertés locales (éviter d'être mort-né)
        val neighbors = fastNeighbors[posIdx]
        var emptyNeighbors = 0
        for (k in 0 until neighbors.size) {
            if (flatBoard[neighbors[k]] == null) {
                emptyNeighbors++
            }
        }
        score += emptyNeighbors * 100
        if (emptyNeighbors == 0) score -= 2000

        // 4. Alignements : blocage d'une paire adverse ou création d'une paire alliée
        val partners = fastLinePartnersByIdx[posIdx]
        var i = 0
        while (i < partners.size) {
            val p1 = flatBoard[partners[i]]
            val p2 = flatBoard[partners[i + 1]]

            if (p1 == opponent && p2 == opponent) {
                score += 3000
            } else if ((p1 == player && p2 == null) || (p2 == player && p1 == null)) {
                score += 1500
            }
            i += 2
        }

        return score
    }

    fun scorePlacementCheap(
        state: GameState,
        pos: Position,
        player: Player,
        depth: Int = 1
    ): Int {
        val flatBoard = arrayOfNulls<Player>(Position.TOTAL_CELLS)
        for ((p, piece) in state.board) {
            if (piece != null) {
                flatBoard[p.row * Position.COLS + p.col] = piece
            }
        }
        return scorePlacementCheapFast(flatBoard, pos, player)
    }

    fun orderPlacementsCheap(
        state: GameState,
        placements: List<Position>,
        player: Player,
        depth: Int = 1
    ): List<Position> {
        if (placements.size <= 1) return placements
        val flatBoard = arrayOfNulls<Player>(Position.TOTAL_CELLS)
        for ((p, piece) in state.board) {
            if (piece != null) {
                flatBoard[p.row * Position.COLS + p.col] = piece
            }
        }
        return placements.sortedByDescending { scorePlacementCheapFast(flatBoard, it, player) }
    }

    /**
     * Test statique, local et ultra-rapide (zéro allocation, zéro appel à WaliEngine)
     * déterminant si le déplacement de [from] vers [to] par [player] forme une ligne de 3.
     */
    /**
     * Test statique, local et ultra-rapide (zéro allocation, zéro appel à WaliEngine)
     * déterminant si le déplacement de [from] vers [to] par [player] forme une ligne de 3.
     */
    fun wouldFormLineStatically(
        flatBoard: Array<Player?>,
        from: Position,
        to: Position,
        player: Player
    ): Boolean {
        val r = to.row
        val c = to.col
        val fromIdx = from.row * Position.COLS + from.col

        // 1. Alignement horizontal sur la rangée r
        if (c >= 2) {
            val idx1 = r * Position.COLS + (c - 2)
            val idx2 = r * Position.COLS + (c - 1)
            if (idx1 != fromIdx && idx2 != fromIdx && flatBoard[idx1] == player && flatBoard[idx2] == player) {
                val idxBefore = if (c >= 3) r * Position.COLS + (c - 3) else -1
                val idxAfter = if (c + 1 < Position.COLS) r * Position.COLS + (c + 1) else -1
                val beforePlayer = idxBefore != -1 && idxBefore != fromIdx && flatBoard[idxBefore] == player
                val afterPlayer = idxAfter != -1 && idxAfter != fromIdx && flatBoard[idxAfter] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }
        if (c in 1 until Position.COLS - 1) {
            val idx1 = r * Position.COLS + (c - 1)
            val idx2 = r * Position.COLS + (c + 1)
            if (idx1 != fromIdx && idx2 != fromIdx && flatBoard[idx1] == player && flatBoard[idx2] == player) {
                val idxBefore = if (c >= 2) r * Position.COLS + (c - 2) else -1
                val idxAfter = if (c + 2 < Position.COLS) r * Position.COLS + (c + 2) else -1
                val beforePlayer = idxBefore != -1 && idxBefore != fromIdx && flatBoard[idxBefore] == player
                val afterPlayer = idxAfter != -1 && idxAfter != fromIdx && flatBoard[idxAfter] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }
        if (c <= Position.COLS - 3) {
            val idx1 = r * Position.COLS + (c + 1)
            val idx2 = r * Position.COLS + (c + 2)
            if (idx1 != fromIdx && idx2 != fromIdx && flatBoard[idx1] == player && flatBoard[idx2] == player) {
                val idxBefore = if (c >= 1) r * Position.COLS + (c - 1) else -1
                val idxAfter = if (c + 3 < Position.COLS) r * Position.COLS + (c + 3) else -1
                val beforePlayer = idxBefore != -1 && idxBefore != fromIdx && flatBoard[idxBefore] == player
                val afterPlayer = idxAfter != -1 && idxAfter != fromIdx && flatBoard[idxAfter] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }

        // 2. Alignement vertical sur la colonne c
        if (r >= 2) {
            val idx1 = (r - 2) * Position.COLS + c
            val idx2 = (r - 1) * Position.COLS + c
            if (idx1 != fromIdx && idx2 != fromIdx && flatBoard[idx1] == player && flatBoard[idx2] == player) {
                val idxBefore = if (r >= 3) (r - 3) * Position.COLS + c else -1
                val idxAfter = if (r + 1 < Position.ROWS) (r + 1) * Position.COLS + c else -1
                val beforePlayer = idxBefore != -1 && idxBefore != fromIdx && flatBoard[idxBefore] == player
                val afterPlayer = idxAfter != -1 && idxAfter != fromIdx && flatBoard[idxAfter] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }
        if (r in 1 until Position.ROWS - 1) {
            val idx1 = (r - 1) * Position.COLS + c
            val idx2 = (r + 1) * Position.COLS + c
            if (idx1 != fromIdx && idx2 != fromIdx && flatBoard[idx1] == player && flatBoard[idx2] == player) {
                val idxBefore = if (r >= 2) (r - 2) * Position.COLS + c else -1
                val idxAfter = if (r + 2 < Position.ROWS) (r + 2) * Position.COLS + c else -1
                val beforePlayer = idxBefore != -1 && idxBefore != fromIdx && flatBoard[idxBefore] == player
                val afterPlayer = idxAfter != -1 && idxAfter != fromIdx && flatBoard[idxAfter] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }
        if (r <= Position.ROWS - 3) {
            val idx1 = (r + 1) * Position.COLS + c
            val idx2 = (r + 2) * Position.COLS + c
            if (idx1 != fromIdx && idx2 != fromIdx && flatBoard[idx1] == player && flatBoard[idx2] == player) {
                val idxBefore = if (r >= 1) (r - 1) * Position.COLS + c else -1
                val idxAfter = if (r + 3 < Position.ROWS) (r + 3) * Position.COLS + c else -1
                val beforePlayer = idxBefore != -1 && idxBefore != fromIdx && flatBoard[idxBefore] == player
                val afterPlayer = idxAfter != -1 && idxAfter != fromIdx && flatBoard[idxAfter] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }

        return false
    }

    fun wouldFormLineStatically(
        board: Map<Position, Player?>,
        from: Position,
        to: Position,
        player: Player
    ): Boolean {
        val flatBoard = arrayOfNulls<Player>(Position.TOTAL_CELLS)
        for ((pos, piece) in board) {
            if (piece != null) {
                flatBoard[pos.row * Position.COLS + pos.col] = piece
            }
        }
        return wouldFormLineStatically(flatBoard, from, to, player)
    }

    private fun scoreMoveCheap(
        state: GameState,
        move: Pair<Position, Position>,
        player: Player,
        depth: Int
    ): Int {
        val flatBoard = arrayOfNulls<Player>(Position.TOTAL_CELLS)
        for ((pos, piece) in state.board) {
            if (piece != null) {
                flatBoard[pos.row * Position.COLS + pos.col] = piece
            }
        }
        return scoreMoveCheapFast(flatBoard, move, player, depth)
    }

    private fun scoreMoveCheapFast(
        flatBoard: Array<Player?>,
        move: Pair<Position, Position>,
        player: Player,
        depth: Int
    ): Int {
        var score = 0
        val from = move.first
        val to = move.second

        // 1. Coup formant un alignement (capture directe) détecté statiquement
        if (wouldFormLineStatically(flatBoard, from, to, player)) {
            score += 1_000_000
        }

        // 2. Coups Killers
        if (depth in 0 until MAX_SEARCH_DEPTH) {
            val k = killerMoves[depth]
            if (k[0] == move) {
                score += 500_000
            } else if (k[1] == move) {
                score += 400_000
            }
        }

        // 3. Heuristique d'historique
        val fromIdx = from.row * Position.COLS + from.col
        val toIdx = to.row * Position.COLS + to.col
        val playerIdx = if (player == Player.STICKS) 0 else 1
        score += historyTable[playerIdx][fromIdx * Position.TOTAL_CELLS + toIdx]

        // 4. Contrôle central
        if (to.row in 1..3 && to.col in 1..4) {
            score += 100
        }

        return score
    }

    private val linePartnersByPos: Map<Position, List<Pair<Position, Position>>> = Position.allPositions.associateWith { pos ->
        val list = mutableListOf<Pair<Position, Position>>()
        for (pattern in all38LinePatterns) {
            if (pos in pattern.cells) {
                val others = pattern.cells.filter { it != pos }
                list.add(others[0] to others[1])
            }
        }
        list
    }

    /**
     * Ordonnancement ultra-rapide des cibles de capture pour la recherche profonde.
     * Cible prioritaire : pion servant d'appui à un alignement adverse, puis pion très mobile, puis centre.
     */
    fun orderCaptureTargetsCheap(
        state: GameState,
        targets: Collection<Position>,
        playerCapturing: Player
    ): List<Position> {
        if (targets.size <= 1) return targets.toList()
        val opponent = playerCapturing.opponent
        val flatBoard = arrayOfNulls<Player>(Position.TOTAL_CELLS)
        for ((pos, piece) in state.board) {
            if (piece != null) {
                flatBoard[pos.row * Position.COLS + pos.col] = piece
            }
        }
        return targets.sortedByDescending { target ->
            var score = 0
            val targetIdx = target.row * Position.COLS + target.col
            val neighbors = fastNeighbors[targetIdx]

            // 1. Appui à une menace d'alignement adverse
            val partners = fastLinePartnersByIdx[targetIdx]
            var i = 0
            while (i < partners.size) {
                val p1 = flatBoard[partners[i]]
                val p2 = flatBoard[partners[i + 1]]
                if (p1 == opponent && p2 == opponent) {
                    score += 2000
                } else if (p1 == opponent || p2 == opponent) {
                    score += 500
                }
                i += 2
            }

            // 2. Mobilité (pion mobile dangereux)
            var emptyNeighbors = 0
            for (k in 0 until neighbors.size) {
                if (flatBoard[neighbors[k]] == null) emptyNeighbors++
            }
            score += emptyNeighbors * 50

            // 3. Centralité
            if (fastIsCenter[targetIdx]) {
                score += 30
            }

            score
        }
    }

    fun orderMovesCheap(
        state: GameState,
        moves: List<Pair<Position, Position>>,
        player: Player,
        depth: Int
    ): List<Pair<Position, Position>> {
        if (moves.size <= 1) return moves
        val flatBoard = arrayOfNulls<Player>(Position.TOTAL_CELLS)
        for ((pos, piece) in state.board) {
            if (piece != null) {
                flatBoard[pos.row * Position.COLS + pos.col] = piece
            }
        }
        return moves.sortedByDescending { scoreMoveCheapFast(flatBoard, it, player, depth) }
    }

    private fun orderMoves(
        state: GameState,
        moves: List<Pair<Position, Position>>,
        player: Player
    ): List<Pair<Position, Position>> {
        return orderMovesCheap(state, moves, player, 1)
    }

    // =========================================================================
    // FONCTION D'ÉVALUATION HEURISTIQUE APPROFONDIE (DEEP EVALUATION)
    // =========================================================================

    /**
     * Évaluation globale experte d'un état de jeu en phase de pose et de déplacement.
     * Hiérarchie stricte des poids :
     * 1. Matériel (1 200 pts/pion, bonus d'élimination en fin de partie)
     * 2. Doubles moulins (deux alignements partageant un pion : 4 500 pts)
     * 3. Mobilité locale (libertés orthogonales : 35 pts/liberté, risque d'asphyxie)
     * 4. Pions adverses bloqués (160 pts par pion adverse paralysé à 0 liberté)
     * 5. Alignements potentiels (menaces directes à 1 temps et fourchettes)
     * 6. Navettes simples (va-et-vient actif)
     * 7. Contrôle positionnel du centre
     * 8. Phase 6b (Phase.PLACEMENT) :
     *    - Libertés futures fortement pondérées (cases vides distinctes accessibles, pions à mobilité garantie >= 2 libertés, pénalité sévère des pions à 0 ou 1 liberté)
     *    - Contrôle des cases à forte connectivité (nombre de lignes de 3 + degré orthogonal)
     *    - Pénalité d'agglutination de pions alliés dans une zone fermée (risque d'auto-étouffement collectif)
     */
    private const val EXTEND_LOW_MOBILITY_SENTINEL = Int.MIN_VALUE + 7

    internal fun evaluateStateDeep(state: GameState, aiPlayer: Player): Int {
        return evaluateLeafOrSignalLowMobilityExtension(state, aiPlayer, canExtendOnLowMobility = false)
    }

    internal fun evaluateLeafOrSignalLowMobilityExtension(
        state: GameState,
        aiPlayer: Player,
        canExtendOnLowMobility: Boolean
    ): Int {
        if (state.winner != null) {
            return if (state.winner == aiPlayer) 100_000 else -100_000
        }

        val opponent = aiPlayer.opponent
        val flatBoard = arrayOfNulls<Player>(Position.TOTAL_CELLS)
        var aiCount = 0
        var oppCount = 0

        for ((pos, piece) in state.board) {
            if (piece != null) {
                val idx = pos.row * Position.COLS + pos.col
                flatBoard[idx] = piece
                if (piece == aiPlayer) aiCount++ else oppCount++
            }
        }

        // 1. Matériel (poids prépondérant + incapacité d'aligner sous 3 pions en déplacement)
        var score = (aiCount - oppCount) * 1200
        if (state.phase == Phase.MOVEMENT) {
            if (oppCount <= 3) score += (4 - oppCount) * 600
            if (aiCount <= 3) score -= (4 - aiCount) * 600
            if (oppCount <= 2) score += (3 - oppCount) * 2000
            if (aiCount <= 2) score -= (3 - aiCount) * 2000
        }

        // 2. Mobilité locale, libertés futures, connectivité & agglutination (calcul direct sans allocation)
        var aiLiberties = 0
        var oppLiberties = 0
        var aiBlockedPieces = 0
        var oppBlockedPieces = 0
        var aiCenterPieces = 0
        var oppCenterPieces = 0
        var aiDistinctEmptyMask = 0
        var oppDistinctEmptyMask = 0
        var aiOneLibertyPieces = 0
        var oppOneLibertyPieces = 0
        var aiSafeMobilePieces = 0
        var oppSafeMobilePieces = 0
        var aiConnectivityScore = 0
        var oppConnectivityScore = 0
        var aiAgglutinationPenalty = 0
        var oppAgglutinationPenalty = 0

        for (idx in 0 until Position.TOTAL_CELLS) {
            val piece = flatBoard[idx] ?: continue
            val neighbors = fastNeighbors[idx]
            var liberties = 0
            var friendlyNeighbors = 0
            var emptyMaskLocal = 0
            for (k in 0 until neighbors.size) {
                val nIdx = neighbors[k]
                val nPiece = flatBoard[nIdx]
                if (nPiece == null) {
                    liberties++
                    emptyMaskLocal = emptyMaskLocal or (1 shl nIdx)
                } else if (nPiece == piece) {
                    friendlyNeighbors++
                }
            }

            val isCenter = fastIsCenter[idx]
            val conn = fastSquareConnectivityScore[idx]
            val agglutination = if (friendlyNeighbors >= 2) {
                val base = (friendlyNeighbors - 1) * 95
                val closedZoneExtra = if (liberties == 0) 220 else if (liberties == 1) 120 else 0
                base + closedZoneExtra
            } else if (friendlyNeighbors == 1 && liberties == 0) {
                130
            } else {
                0
            }

            if (piece == aiPlayer) {
                aiLiberties += liberties
                aiDistinctEmptyMask = aiDistinctEmptyMask or emptyMaskLocal
                if (liberties == 0) aiBlockedPieces++
                else if (liberties == 1) aiOneLibertyPieces++
                else aiSafeMobilePieces++
                if (isCenter) aiCenterPieces++
                aiConnectivityScore += conn
                aiAgglutinationPenalty += agglutination
            } else {
                oppLiberties += liberties
                oppDistinctEmptyMask = oppDistinctEmptyMask or emptyMaskLocal
                if (liberties == 0) oppBlockedPieces++
                else if (liberties == 1) oppOneLibertyPieces++
                else oppSafeMobilePieces++
                if (isCenter) oppCenterPieces++
                oppConnectivityScore += conn
                oppAgglutinationPenalty += agglutination
            }
        }

        // Phase 6a : Si la feuille est en très faible mobilité (asphyxie imminente, 1..2 libertés),
        // signaler l'extension de recherche sans avoir à reconstruire flatBoard deux fois.
        if (canExtendOnLowMobility && ((aiLiberties in 1..2) || (oppLiberties in 1..2))) {
            return EXTEND_LOW_MOBILITY_SENTINEL
        }

        // Détection de mat par étouffement (aucun coup possible) en phase de déplacement
        if (state.phase == Phase.MOVEMENT) {
            if (oppLiberties == 0 && state.currentPlayer == opponent) {
                return 90_000
            }
            if (aiLiberties == 0 && state.currentPlayer == aiPlayer) {
                return -90_000
            }
        }

        // 3. Doubles moulins (deux alignements formés partageant un pivot, permettant de capturer à chaque tour)
        var aiDoubleMills = 0
        var oppDoubleMills = 0

        if (aiCount >= 5 || oppCount >= 5) {
            for (i in 0 until fastDoubleMillPairs.size) {
                val pair = fastDoubleMillPairs[i]
                val pivotOwner = flatBoard[pair.pivot] ?: continue

                val a1 = flatBoard[pair.a1] == pivotOwner
                val a2 = flatBoard[pair.a2] == pivotOwner
                val b1 = flatBoard[pair.b1] == pivotOwner
                val b2 = flatBoard[pair.b2] == pivotOwner

                if (a1 && a2 && b1 && b2) {
                    if (pivotOwner == aiPlayer) aiDoubleMills++ else oppDoubleMills++
                }
            }
        }

        score += aiDoubleMills * 4500
        score -= oppDoubleMills * 4800

        // 4. Mobilité et libertés
        score += (aiLiberties - oppLiberties) * 35
        if (oppLiberties in 1..2) score += 2000
        if (aiLiberties in 1..2) score -= 2500

        // 5. Pions bloqués
        score += oppBlockedPieces * 160
        score -= aiBlockedPieces * 200

        // 6. Alignements potentiels, fourchettes et navettes (parcours direct des 38 motifs)
        var aiThreats = 0
        var oppThreats = 0
        var aiShuttles = 0
        var oppShuttles = 0

        for (i in 0 until fast38LinePatterns.size) {
            val pat = fast38LinePatterns[i]
            val p0 = flatBoard[pat.c0]
            val p1 = flatBoard[pat.c1]
            val p2 = flatBoard[pat.c2]

            // Navette active (ligne formée disposant d'un degré de liberté adjacent)
            if (p0 == aiPlayer && p1 == aiPlayer && p2 == aiPlayer) {
                if (hasEmptyNeighbor(flatBoard, pat.c0) ||
                    hasEmptyNeighbor(flatBoard, pat.c1) ||
                    hasEmptyNeighbor(flatBoard, pat.c2)
                ) {
                    aiShuttles++
                }
                continue
            } else if (p0 == opponent && p1 == opponent && p2 == opponent) {
                if (hasEmptyNeighbor(flatBoard, pat.c0) ||
                    hasEmptyNeighbor(flatBoard, pat.c1) ||
                    hasEmptyNeighbor(flatBoard, pat.c2)
                ) {
                    oppShuttles++
                }
                continue
            }

            // Menace d'alignement à 1 temps pour aiPlayer
            if ((p0 == aiPlayer && p1 == aiPlayer && p2 == null) ||
                (p0 == aiPlayer && p2 == aiPlayer && p1 == null) ||
                (p1 == aiPlayer && p2 == aiPlayer && p0 == null)
            ) {
                val emptyCell = if (p0 == null) pat.c0 else if (p1 == null) pat.c1 else pat.c2
                if ((pat.boundaryBefore == -1 || flatBoard[pat.boundaryBefore] != aiPlayer) &&
                    (pat.boundaryAfter == -1 || flatBoard[pat.boundaryAfter] != aiPlayer)
                ) {
                    val neighbors = fastNeighbors[emptyCell]
                    var hasFeeder = false
                    for (k in 0 until neighbors.size) {
                        val n = neighbors[k]
                        if (n != pat.c0 && n != pat.c1 && n != pat.c2 && flatBoard[n] == aiPlayer) {
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
                val emptyCell = if (p0 == null) pat.c0 else if (p1 == null) pat.c1 else pat.c2
                if ((pat.boundaryBefore == -1 || flatBoard[pat.boundaryBefore] != opponent) &&
                    (pat.boundaryAfter == -1 || flatBoard[pat.boundaryAfter] != opponent)
                ) {
                    val neighbors = fastNeighbors[emptyCell]
                    var hasFeeder = false
                    for (k in 0 until neighbors.size) {
                        val n = neighbors[k]
                        if (n != pat.c0 && n != pat.c1 && n != pat.c2 && flatBoard[n] == opponent) {
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

        // 7. Contrôle central
        score += (aiCenterPieces - oppCenterPieces) * 15

        // 8. Phase 6b : Évaluation de pose orientée libertés futures, connectivité et anti-agglutination
        if (state.phase == Phase.PLACEMENT || state.sticksPendingPlacement > 0 || state.stonesPendingPlacement > 0) {
            val aiDistinctLiberties = Integer.bitCount(aiDistinctEmptyMask)
            val oppDistinctLiberties = Integer.bitCount(oppDistinctEmptyMask)

            // 8a. Terme de liberté future fortement pondéré :
            score += (aiDistinctLiberties - oppDistinctLiberties) * 85
            score -= aiBlockedPieces * 450
            score += oppBlockedPieces * 400
            score -= aiOneLibertyPieces * 140
            score += oppOneLibertyPieces * 120
            score += (aiSafeMobilePieces - oppSafeMobilePieces) * 90

            // Alerte d'asphyxie globale à l'approche de la transition :
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

            // 8b. Contrôle des cases à forte connectivité (lignes de 3 + degré orthogonal)
            score += (aiConnectivityScore - oppConnectivityScore)

            // 8c. Pénalité d'agglutination de pions alliés dans une zone fermée
            score -= aiAgglutinationPenalty
            score += oppAgglutinationPenalty
        }

        return score
    }

    // =========================================================================
    // ÉVALUATION DES POSES EN PHASE DE PLACEMENT (PHASE 1)
    // =========================================================================

    /**
     * Évalue stratégiquement la pertinence de poser un pion en [pos] pour [player].
     */
    private fun evaluatePlacementPos(state: GameState, player: Player, pos: Position): Int {
        var score = 0
        val opponent = player.opponent
        val neighbors = getNeighbors(pos)

        // 1. Maîtrise du centre (lignes 1..3, colonnes 1..4)
        if (pos.row in 1..3 && pos.col in 2..3) {
            score += 45 // Cœur absolu du plateau (cases C2, C3, C4, D2, D3, D4)
        } else if (pos.row in 1..3 && pos.col in 1..4) {
            score += 25
        }

        // 2. Préservation critique des libertés (ne jamais naître asphyxié)
        val emptyNeighbors = neighbors.count { state.board[it] == null }
        when (emptyNeighbors) {
            0 -> score -= 220 // Interdit : le pion sera mort-né à 0 liberté
            1 -> score -= 80
            2 -> score += 30
            3, 4 -> score += 60
        }

        // 3. Formation de paires préparatoires et détection des menaces
        // Examiner tous les motifs de 3 cases passant par [pos]
        for (pattern in all38LinePatterns) {
            if (pos !in pattern.cells) continue

            val otherCells = pattern.cells.filter { it != pos }
            val c1 = otherCells[0]
            val c2 = otherCells[1]
            val p1 = state.board[c1]
            val p2 = state.board[c2]

            // A. Blocage défensif d'une paire adverse :
            // Si l'adversaire a déjà ses 2 pions posés et que [pos] est la 3e case,
            // y poser notre pion neutralise définitivement la ligne adverse !
            if (p1 == opponent && p2 == opponent) {
                score += 180
            }

            // B. Création d'une paire alliée :
            // Un pion allié est déjà présent et l'autre case est vide.
            if ((p1 == player && p2 == null) || (p2 == player && p1 == null)) {
                val emptyCell = if (p1 == null) c1 else c2
                // Vérifier si cette future ligne ne violera pas la règle des 4
                if (isValidLinePlacementPotential(state.board, pattern, player)) {
                    score += 90

                    // Bonus majeur : si un autre pion allié est déjà adjacent à la case vide,
                    // la ligne pourra être fermée dès le tout premier tour de déplacement !
                    val hasFeeder = getNeighbors(emptyCell).any { neighbor ->
                        neighbor != pos && neighbor != c1 && neighbor != c2 && state.board[neighbor] == player
                    }
                    if (hasFeeder) {
                        score += 140
                    }
                }
            }
        }

        // 4. Asphyxie d'un pion adverse adjacent
        val trappedOpponents = neighbors.count { neighbor ->
            state.board[neighbor] == opponent && getNeighbors(neighbor).count { it != pos && state.board[it] == null } == 0
        }
        score += trappedOpponents * 90

        return score
    }

    private fun isValidLinePlacementPotential(
        board: Map<Position, Player?>,
        pattern: BoardLinePattern,
        player: Player
    ): Boolean {
        if (pattern.boundaryBefore != null && board[pattern.boundaryBefore] == player) return false
        if (pattern.boundaryAfter != null && board[pattern.boundaryAfter] == player) return false
        return true
    }

    // =========================================================================
    // ÉVALUATION DES CIBLES DE CAPTURE
    // =========================================================================

    /**
     * Évaluation chirurgicale de la valeur tactique d'un pion adverse à éliminer.
     */
    private fun evaluateTargetValueDeep(state: GameState, target: Position): Int {
        var score = 100
        val opponent = state.board[target] ?: return 0
        val aiPlayer = opponent.opponent

        // 1. PRIORITÉ SUPRÊME : Détruire une menace imminente d'alignement adverse
        // Si [target] participe à une menace d'alignement ou sert de pion pivot/feeder
        for (pattern in all38LinePatterns) {
            if (target in pattern.cells) {
                val others = pattern.cells.filter { it != target }
                val p1 = state.board[others[0]]
                val p2 = state.board[others[1]]
                if (p1 == opponent && p2 == null) {
                    val emptyCell = others[1]
                    val feederExists = getNeighbors(emptyCell).any { it != target && state.board[it] == opponent }
                    if (feederExists) score += 1400 // Détruit une menace imminente adverse !
                    else score += 500
                } else if (p2 == opponent && p1 == null) {
                    val emptyCell = others[0]
                    val feederExists = getNeighbors(emptyCell).any { it != target && state.board[it] == opponent }
                    if (feederExists) score += 1400
                    else score += 500
                }
            }
        }

        // Est-ce que [target] est le feeder qui s'apprête à entrer dans une case vide pour fermer une ligne ?
        val targetNeighbors = getNeighbors(target)
        for (emptyNeighbor in targetNeighbors) {
            if (state.board[emptyNeighbor] != null) continue
            // Vérifier si [emptyNeighbor] ferme une ligne adverse
            for (pattern in all38LinePatterns) {
                if (emptyNeighbor in pattern.cells && target !in pattern.cells) {
                    val others = pattern.cells.filter { it != emptyNeighbor }
                    if (state.board[others[0]] == opponent && state.board[others[1]] == opponent) {
                        score += 1600 // C'est précisément le pion assassin adverse !
                    }
                }
            }
        }

        // 2. Éliminer les pions adverses les plus dynamiques et mobiles
        val legalMoves = getNeighbors(target).count { state.board[it] == null }
        if (legalMoves == 0) {
            // Ne pas gaspiller une capture sur un pion adverse déjà paralysé !
            score -= 300
        } else {
            score += legalMoves * 80
        }

        // 3. Débloquer un pion allié entravé par ce pion adverse
        val freedAiNeighbors = targetNeighbors.count { state.board[it] == aiPlayer }
        score += freedAiNeighbors * 120

        // 4. Contrôle central
        if (target.row in 1..3 && target.col in 1..4) {
            score += 100
        }

        return score
    }

    private fun evaluateTargetValueMedium(state: GameState, target: Position): Int {
        var score = 50
        if (target.row in 1..3 && target.col in 1..4) score += 40
        val freeNeighbors = getNeighbors(target).count { state.board[it] == null }
        score += freeNeighbors * 20
        return score
    }

    // =========================================================================
    // DÉTECTION DES MENACES, FOURCHETTES ET NAVETTES
    // =========================================================================

    /**
     * Compte le nombre de menaces d'alignement directes à 1 temps pour [player].
     */
    private fun countImminentLineThreats(state: GameState, player: Player): Int {
        var count = 0
        for (pattern in all38LinePatterns) {
            var playerCount = 0
            var emptyCell: Position? = null

            for (pos in pattern.cells) {
                val piece = state.board[pos]
                if (piece == player) playerCount++
                else if (piece == null) emptyCell = pos
                else {
                    playerCount = -10
                    break
                }
            }

            if (playerCount == 2 && emptyCell != null) {
                // Vérifier la règle stricte des 4
                if (!isValidLinePlacementPotential(state.board, pattern, player)) continue

                // Vérifier la présence d'un pion d'alimentation allié adjacent
                val hasFeeder = getNeighbors(emptyCell).any { neighbor ->
                    neighbor !in pattern.cells && state.board[neighbor] == player
                }
                if (hasFeeder) {
                    count++
                }
            }
        }
        return count
    }

    /**
     * Retourne l'ensemble des cases vides où [player] a une menace d'alignement directe.
     */
    private fun getImminentLineTargets(state: GameState, player: Player): Set<Position> {
        val targets = mutableSetOf<Position>()
        for (pattern in all38LinePatterns) {
            var playerCount = 0
            var emptyCell: Position? = null

            for (pos in pattern.cells) {
                val piece = state.board[pos]
                if (piece == player) playerCount++
                else if (piece == null) emptyCell = pos
                else {
                    playerCount = -10
                    break
                }
            }

            if (playerCount == 2 && emptyCell != null) {
                if (!isValidLinePlacementPotential(state.board, pattern, player)) continue
                val hasFeeder = getNeighbors(emptyCell).any { neighbor ->
                    neighbor !in pattern.cells && state.board[neighbor] == player
                }
                if (hasFeeder) {
                    targets.add(emptyCell)
                }
            }
        }
        return targets
    }

    /**
     * Détecte les lignes formées qui disposent d'un mécanisme actif de va-et-vient (navette).
     */
    private fun countActiveShuttles(state: GameState, player: Player): Int {
        val existingLines = WaliEngine.detectLines(state.board, player)
        var shuttles = 0
        for (line in existingLines) {
            val canShuttle = line.cells.any { pos ->
                getNeighbors(pos).any { neighbor -> state.board[neighbor] == null }
            }
            if (canShuttle) shuttles++
        }
        return shuttles
    }

    // =========================================================================
    // UTILITAIRES INTERNES DE PERFORMANCE
    // =========================================================================

    private fun countPlayerLegalMovesFast(state: GameState, player: Player): Int {
        var count = 0
        for ((pos, piece) in state.board) {
            if (piece == player) {
                val neighbors = getNeighbors(pos)
                for (n in neighbors) {
                    if (state.board[n] == null) {
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun getNeighbors(pos: Position): List<Position> {
        return precomputedNeighbors[pos] ?: emptyList()
    }
}
