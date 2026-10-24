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
    val moveTo: Position? = null
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
                // Niveau Difficile : recherche minimax sur chaque cible de capture pour retenir celle
                // menant au meilleur résultat en profondeur (et non une simple heuristique gloutonne).
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
                            nodeCount = nodeCount
                        )
                    } catch (e: Exception) {
                        evaluateStateDeep(afterCap, aiPlayer)
                    }

                    if (score > bestScore || bestTarget == null) {
                        bestScore = score
                        bestTarget = target
                    }
                }
                bestTarget ?: capturablePositions.maxByOrNull { evaluateTargetValueDeep(state, it) } ?: capturablePositions.random(random)
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

    var isProfilingEnabled: Boolean = false
    var profileTimeOrderMovesNanos: Long = 0L
    var profileTimeEngineMoveNanos: Long = 0L
    var profileTimeEvalNanos: Long = 0L
    var profileTimeTtNanos: Long = 0L
    var profileTimeLegalMovesNanos: Long = 0L
    var profileTotalNodes: Long = 0L

    fun resetProfiling() {
        profileTimeOrderMovesNanos = 0L
        profileTimeEngineMoveNanos = 0L
        profileTimeEvalNanos = 0L
        profileTimeTtNanos = 0L
        profileTimeLegalMovesNanos = 0L
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

        val bound = when (boundByte) {
            1 -> TtBound.EXACT
            2 -> TtBound.LOWER
            3 -> TtBound.UPPER
            else -> return null
        }
        return TtLookupResult(ttDepths[idx], ttScores[idx], bound, bestMove)
    }

    private fun ttStore(key: Long, depth: Int, score: Int, bound: TtBound, bestMove: Pair<Position, Position>?) {
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
        val bestMove: Pair<Position, Position>?
    )

    // =========================================================================
    // NIVEAU DIFFICILE (MAÎTRE DU WALI / RECHERCHE PROFONDE AVEC BUDGET TEMPS)
    // =========================================================================

    private fun chooseHardMove(
        state: GameState,
        random: Random,
        timeBudgetMs: Long,
        isCancelled: () -> Boolean
    ): AiMove? {
        return when (state.phase) {
            Phase.PLACEMENT -> {
                val validPlacements = WaliEngine.getValidPlacements(state)
                if (validPlacements.isEmpty()) return null

                val aiPlayer = state.currentPlayer
                val oppPlayer = aiPlayer.opponent

                // Évaluation approfondie de chaque pose avec simulation de la réponse adverse
                val scoredPlacements = validPlacements.map { pos ->
                    val baseScore = evaluatePlacementPos(state, aiPlayer, pos)

                    // Simulation du contre adverse le plus menaçant
                    val nextState = WaliEngine.placePiece(state, pos, recordHistory = false)
                    val oppBestResponse = if (nextState.phase == Phase.PLACEMENT) {
                        val oppPlacements = WaliEngine.getValidPlacements(nextState)
                        if (oppPlacements.isEmpty()) 0
                        else oppPlacements.maxOf { oppPos -> evaluatePlacementPos(nextState, oppPlayer, oppPos) }
                    } else 0

                    val netScore = baseScore * 10 - (oppBestResponse * 7)
                    pos to netScore
                }

                val maxScore = scoredPlacements.maxOf { it.second }
                val bestPlacements = scoredPlacements.filter { it.second == maxScore }.map { it.first }
                AiMove(placement = bestPlacements.random(random))
            }
            Phase.MOVEMENT -> {
                val aiPlayer = state.currentPlayer

                if (WaliEngine.isPlacementRequired(state)) {
                    val validPlacements = WaliEngine.getValidPlacements(state)
                    if (validPlacements.isEmpty()) return null

                    val scored = validPlacements.map { pos ->
                        pos to evaluatePlacementPos(state, aiPlayer, pos)
                    }
                    val maxScore = scored.maxOf { it.second }
                    val best = scored.filter { it.second == maxScore }.map { it.first }
                    return AiMove(placement = best.random(random))
                }

                val allMoves = getAllLegalMoves(state)
                if (allMoves.isEmpty()) return null

                // 1. Détection immédiate des coups gagnants à 1 temps
                for ((from, to) in allMoves) {
                    val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false)
                    if (nextState.winner == aiPlayer) {
                        lastSearchCompletedDepth = 1
                        lastSearchNodeCount = 1
                        return AiMove(moveFrom = from, moveTo = to)
                    }
                    if (WaliEngine.canCapture(nextState)) {
                        val oppPieces = nextState.board.filter { it.value == aiPlayer.opponent }.keys
                        for (target in oppPieces) {
                            val afterCapture = WaliEngine.capturePiece(nextState, target, recordHistory = false)
                            if (afterCapture.winner == aiPlayer) {
                                lastSearchCompletedDepth = 1
                                lastSearchNodeCount = 1
                                return AiMove(moveFrom = from, moveTo = to)
                            }
                        }
                    }
                }

                // 2. Approfondissement itératif avec table de transposition Zobrist et budget de temps réel
                val startTime = System.currentTimeMillis()
                val deadline = startTime + timeBudgetMs
                val nodeCounter = IntArray(1)
                resetMoveOrdering()

                var completedBestMove: Pair<Position, Position>? = null
                var completedBestScore = Int.MIN_VALUE
                var lastCompletedDepth = 0

                // Pré-tri initial des coups
                var moveList = orderMovesCheap(state, allMoves, aiPlayer, 1)
                completedBestMove = moveList.firstOrNull()

                for (targetDepth in 1..24) {
                    // S'il reste moins de 20ms de budget, ne pas lancer une nouvelle profondeur
                    val now = System.currentTimeMillis()
                    if (targetDepth > 1 && (now >= deadline - 20L || isCancelled())) {
                        break
                    }

                    var iterationBestMove: Pair<Position, Position>? = null
                    var iterationBestScore = Int.MIN_VALUE
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

                        val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = false)

                        val score = try {
                            evaluateMoveOutcome(
                                nextState = nextState,
                                playerWhoMoved = aiPlayer,
                                depth = targetDepth,
                                alpha = alpha,
                                beta = beta,
                                isMaximizing = true,
                                aiPlayer = aiPlayer,
                                deadline = deadline,
                                isCancelled = isCancelled,
                                nodeCount = nodeCounter
                            )
                        } catch (e: SearchTimeoutException) {
                            interrupted = true
                            break
                        }

                        if (score > iterationBestScore) {
                            iterationBestScore = score
                            iterationBestMove = from to to
                        }
                        alpha = maxOf(alpha, score)
                    }

                    // Ne sauvegarder le meilleur coup QUE si l'itération entière s'est terminée avec succès
                    if (!interrupted && iterationBestMove != null) {
                        completedBestMove = iterationBestMove
                        completedBestScore = iterationBestScore
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

                val chosen = completedBestMove ?: allMoves.first()
                AiMove(moveFrom = chosen.first, moveTo = chosen.second)
            }
            Phase.GAME_OVER -> null
        }
    }

    /**
     * Évalue l'issue d'un déplacement simulé en branchant sur tous les choix possibles
     * de pion capturé si une ligne de trois a été formée (nœud de capture explicite).
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
        nodeCount: IntArray
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
                nodeCount = nodeCount
            )
        }

        val opponent = playerWhoMoved.opponent
        val capturables = nextState.board.filter { it.value == opponent }.keys
        if (capturables.isEmpty()) {
            return minimaxAlphaBeta(
                state = nextState,
                depth = depth - 1,
                alpha = alpha,
                beta = beta,
                isMaximizing = !isMaximizing,
                aiPlayer = aiPlayer,
                deadline = deadline,
                isCancelled = isCancelled,
                nodeCount = nodeCount
            )
        }

        val orderedTargets = orderCaptureTargetsCheap(nextState, capturables, playerWhoMoved)
        val targetsToExplore = if (orderedTargets.size > 3) orderedTargets.take(3) else orderedTargets

        return if (isMaximizing) {
            // L'IA maximise son gain parmi toutes les cibles capturables
            var bestCapScore = Int.MIN_VALUE
            var currentAlpha = alpha
            for (target in targetsToExplore) {
                val afterCap = WaliEngine.capturePiece(nextState, target, recordHistory = false, validate = false)
                val score = minimaxAlphaBeta(
                    state = afterCap,
                    depth = depth - 1,
                    alpha = currentAlpha,
                    beta = beta,
                    isMaximizing = false,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount
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
                val afterCap = WaliEngine.capturePiece(nextState, target, recordHistory = false, validate = false)
                val score = minimaxAlphaBeta(
                    state = afterCap,
                    depth = depth - 1,
                    alpha = alpha,
                    beta = currentBeta,
                    isMaximizing = true,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount
                )
                bestCapScore = minOf(bestCapScore, score)
                currentBeta = minOf(currentBeta, score)
                if (currentBeta <= alpha) break // Coupe Alpha sur le choix de capture
            }
            bestCapScore
        }
    }

    /**
     * Recherche Minimax Alpha-Bêta optimisée avec table de transposition Zobrist.
     * Utilise exclusivement [WaliEngine] pour chaque coup, transition et capture.
     */
    private fun minimaxAlphaBeta(
        state: GameState,
        depth: Int,
        alpha: Int,
        beta: Int,
        isMaximizing: Boolean,
        aiPlayer: Player,
        deadline: Long,
        isCancelled: () -> Boolean,
        nodeCount: IntArray
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
            return if (state.winner == aiPlayer) 100_000 + depth else -100_000 - depth
        }

        // Profondeur atteinte : évaluation heuristique de la position
        if (depth <= 0) {
            val t4 = if (isProfilingEnabled) System.nanoTime() else 0L
            val eval = evaluateStateDeep(state, aiPlayer)
            if (isProfilingEnabled) profileTimeEvalNanos += System.nanoTime() - t4
            return eval
        }

        val t0 = if (isProfilingEnabled) System.nanoTime() else 0L
        val hash = computeZobristHash(state)
        val ttEntry = ttLookup(hash)
        if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - t0

        var currentAlpha = alpha
        var currentBeta = beta

        if (ttEntry != null && ttEntry.depth >= depth) {
            when (ttEntry.bound) {
                TtBound.EXACT -> return ttEntry.score
                TtBound.LOWER -> currentAlpha = maxOf(currentAlpha, ttEntry.score)
                TtBound.UPPER -> currentBeta = minOf(currentBeta, ttEntry.score)
            }
            if (currentAlpha >= currentBeta) {
                return ttEntry.score
            }
        }

        val t1 = if (isProfilingEnabled) System.nanoTime() else 0L
        val moves = getAllLegalMoves(state)
        if (isProfilingEnabled) profileTimeLegalMovesNanos += System.nanoTime() - t1

        if (moves.isEmpty()) {
            // Aucun coup légal : le joueur actif est étouffé (défaite par blocage)
            return if (isMaximizing) -100_000 - depth else 100_000 + depth
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
                    depth = depth,
                    alpha = currentAlpha,
                    beta = currentBeta,
                    isMaximizing = true,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount
                )

                if (eval > maxEval) {
                    maxEval = eval
                    bestMoveThisNode = ttMove
                }
                currentAlpha = maxOf(currentAlpha, eval)
                if (currentBeta <= currentAlpha) {
                    recordBetaCutoff(currentPlayer, ttMove, depth)
                    val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
                    ttStore(hash, depth, maxEval, TtBound.LOWER, bestMoveThisNode)
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
            val orderedMoves = orderMovesCheap(state, remainingMoves, currentPlayer, depth)
            if (isProfilingEnabled) profileTimeOrderMovesNanos += System.nanoTime() - t2

            for ((from, to) in orderedMoves) {
                val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                val eval = evaluateMoveOutcome(
                    nextState = nextState,
                    playerWhoMoved = currentPlayer,
                    depth = depth,
                    alpha = currentAlpha,
                    beta = currentBeta,
                    isMaximizing = true,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount
                )

                if (eval > maxEval) {
                    maxEval = eval
                    bestMoveThisNode = from to to
                }
                currentAlpha = maxOf(currentAlpha, eval)
                if (currentBeta <= currentAlpha) {
                    recordBetaCutoff(currentPlayer, from to to, depth)
                    break // Coupe Bêta
                }
            }

            val bound = when {
                maxEval <= alpha -> TtBound.UPPER
                maxEval >= beta -> TtBound.LOWER
                else -> TtBound.EXACT
            }
            val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
            ttStore(hash, depth, maxEval, bound, bestMoveThisNode)
            if (isProfilingEnabled) profileTimeTtNanos += System.nanoTime() - tStore
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            val oppPlayer = aiPlayer.opponent

            // 1. Essai prioritaire immédiat du coup issu de la table de transposition
            if (ttMove != null && ttMove in moves) {
                val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                val nextState = WaliEngine.movePiece(state, ttMove.first, ttMove.second, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                val eval = evaluateMoveOutcome(
                    nextState = nextState,
                    playerWhoMoved = currentPlayer,
                    depth = depth,
                    alpha = currentAlpha,
                    beta = currentBeta,
                    isMaximizing = false,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount
                )

                if (eval < minEval) {
                    minEval = eval
                    bestMoveThisNode = ttMove
                }
                currentBeta = minOf(currentBeta, eval)
                if (currentBeta <= currentAlpha) {
                    recordBetaCutoff(currentPlayer, ttMove, depth)
                    val tStore = if (isProfilingEnabled) System.nanoTime() else 0L
                    ttStore(hash, depth, minEval, TtBound.UPPER, bestMoveThisNode)
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
            val orderedMoves = orderMovesCheap(state, remainingMoves, currentPlayer, depth)
            if (isProfilingEnabled) profileTimeOrderMovesNanos += System.nanoTime() - t2

            for ((from, to) in orderedMoves) {
                val t3 = if (isProfilingEnabled) System.nanoTime() else 0L
                val nextState = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = false)
                if (isProfilingEnabled) profileTimeEngineMoveNanos += System.nanoTime() - t3
                val eval = evaluateMoveOutcome(
                    nextState = nextState,
                    playerWhoMoved = currentPlayer,
                    depth = depth,
                    alpha = currentAlpha,
                    beta = currentBeta,
                    isMaximizing = false,
                    aiPlayer = aiPlayer,
                    deadline = deadline,
                    isCancelled = isCancelled,
                    nodeCount = nodeCount
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
            ttStore(hash, depth, minEval, bound, bestMoveThisNode)
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

    fun resetMoveOrdering() {
        for (d in 0 until MAX_SEARCH_DEPTH) {
            killerMoves[d][0] = null
            killerMoves[d][1] = null
        }
        historyTable[0].fill(0)
        historyTable[1].fill(0)
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

    /**
     * Test statique, local et ultra-rapide (zéro allocation, zéro appel à WaliEngine)
     * déterminant si le déplacement de [from] vers [to] par [player] forme une ligne de 3.
     */
    fun wouldFormLineStatically(
        board: Map<Position, Player?>,
        from: Position,
        to: Position,
        player: Player
    ): Boolean {
        val r = to.row
        val c = to.col

        // 1. Alignement horizontal sur la rangée r
        if (c >= 2) {
            val p1 = Position(r, c - 2)
            val p2 = Position(r, c - 1)
            if (p1 != from && p2 != from && board[p1] == player && board[p2] == player) {
                val before = if (c >= 3) Position(r, c - 3) else null
                val after = if (c + 1 < Position.COLS) Position(r, c + 1) else null
                val beforePlayer = before != null && before != from && board[before] == player
                val afterPlayer = after != null && after != from && board[after] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }
        if (c in 1 until Position.COLS - 1) {
            val p1 = Position(r, c - 1)
            val p2 = Position(r, c + 1)
            if (p1 != from && p2 != from && board[p1] == player && board[p2] == player) {
                val before = if (c >= 2) Position(r, c - 2) else null
                val after = if (c + 2 < Position.COLS) Position(r, c + 2) else null
                val beforePlayer = before != null && before != from && board[before] == player
                val afterPlayer = after != null && after != from && board[after] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }
        if (c <= Position.COLS - 3) {
            val p1 = Position(r, c + 1)
            val p2 = Position(r, c + 2)
            if (p1 != from && p2 != from && board[p1] == player && board[p2] == player) {
                val before = if (c >= 1) Position(r, c - 1) else null
                val after = if (c + 3 < Position.COLS) Position(r, c + 3) else null
                val beforePlayer = before != null && before != from && board[before] == player
                val afterPlayer = after != null && after != from && board[after] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }

        // 2. Alignement vertical sur la colonne c
        if (r >= 2) {
            val p1 = Position(r - 2, c)
            val p2 = Position(r - 1, c)
            if (p1 != from && p2 != from && board[p1] == player && board[p2] == player) {
                val before = if (r >= 3) Position(r - 3, c) else null
                val after = if (r + 1 < Position.ROWS) Position(r + 1, c) else null
                val beforePlayer = before != null && before != from && board[before] == player
                val afterPlayer = after != null && after != from && board[after] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }
        if (r in 1 until Position.ROWS - 1) {
            val p1 = Position(r - 1, c)
            val p2 = Position(r + 1, c)
            if (p1 != from && p2 != from && board[p1] == player && board[p2] == player) {
                val before = if (r >= 2) Position(r - 2, c) else null
                val after = if (r + 2 < Position.ROWS) Position(r + 2, c) else null
                val beforePlayer = before != null && before != from && board[before] == player
                val afterPlayer = after != null && after != from && board[after] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }
        if (r <= Position.ROWS - 3) {
            val p1 = Position(r + 1, c)
            val p2 = Position(r + 2, c)
            if (p1 != from && p2 != from && board[p1] == player && board[p2] == player) {
                val before = if (r >= 1) Position(r - 1, c) else null
                val after = if (r + 3 < Position.ROWS) Position(r + 3, c) else null
                val beforePlayer = before != null && before != from && board[before] == player
                val afterPlayer = after != null && after != from && board[after] == player
                if (!beforePlayer && !afterPlayer) return true
            }
        }

        return false
    }

    private fun scoreMoveCheap(
        state: GameState,
        move: Pair<Position, Position>,
        player: Player,
        depth: Int
    ): Int {
        var score = 0
        val from = move.first
        val to = move.second

        // 1. Coup formant un alignement (capture directe) détecté statiquement
        if (wouldFormLineStatically(state.board, from, to, player)) {
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
        return targets.sortedByDescending { target ->
            var score = 0
            val neighbors = getNeighbors(target)

            // 1. Appui à une menace d'alignement adverse
            val partners = linePartnersByPos[target] ?: emptyList()
            for (i in 0 until partners.size) {
                val pair = partners[i]
                val p1 = state.board[pair.first]
                val p2 = state.board[pair.second]
                if (p1 == opponent && p2 == opponent) {
                    score += 2000
                } else if (p1 == opponent || p2 == opponent) {
                    score += 500
                }
            }

            // 2. Mobilité (pion mobile dangereux)
            var emptyNeighbors = 0
            for (i in 0 until neighbors.size) {
                if (state.board[neighbors[i]] == null) emptyNeighbors++
            }
            score += emptyNeighbors * 50

            // 3. Centralité
            if (target.row in 1..3 && target.col in 1..4) {
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
        return moves.sortedByDescending { scoreMoveCheap(state, it, player, depth) }
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
     * Évaluation globale complète d'un état de jeu en phase de déplacement.
     */
    private fun evaluateStateDeep(state: GameState, aiPlayer: Player): Int {
        if (state.winner != null) {
            return if (state.winner == aiPlayer) 100_000 else -100_000
        }

        val opponent = aiPlayer.opponent
        val aiCount = state.piecesOnBoard(aiPlayer)
        val oppCount = state.piecesOnBoard(opponent)

        // 1. Différence matérielle (poids prépondérant)
        var score = (aiCount - oppCount) * 1200

        // Bonus d'élimination en fin de partie
        if (oppCount <= 3) {
            score += (4 - oppCount) * 600
        }

        // 2. Mobilité et risques de blocage
        val aiLegalMoves = countPlayerLegalMovesFast(state, aiPlayer)
        val oppLegalMoves = countPlayerLegalMovesFast(state, opponent)

        if (oppLegalMoves == 0 && state.currentPlayer == opponent) {
            return 90_000 // Adversaire étouffé sans aucun coup légal : victoire !
        }
        if (aiLegalMoves == 0 && state.currentPlayer == aiPlayer) {
            return -90_000 // IA étouffée : défaite !
        }

        score += (aiLegalMoves - oppLegalMoves) * 35

        if (oppLegalMoves in 1..2) {
            score += 2500 // Adversaire presque totalement asphyxié
        }
        if (aiLegalMoves in 1..2) {
            score -= 3000 // Danger imminent d'asphyxie pour l'IA
        }

        // 3. Menaces directes d'alignement à 1 temps et fourchettes
        val aiThreats = countImminentLineThreats(state, aiPlayer)
        val oppThreats = countImminentLineThreats(state, opponent)

        score += aiThreats * 450
        score -= oppThreats * 650 // Pénalisation plus forte des menaces adverses pour forcer la défense

        if (aiThreats >= 2) score += 900 // Fourchette alliée
        if (oppThreats >= 2) score -= 1200 // Fourchette adverse critique

        // 4. Potentiel de navette / va-et-vient (moulin réutilisable)
        val aiShuttles = countActiveShuttles(state, aiPlayer)
        val oppShuttles = countActiveShuttles(state, opponent)
        score += aiShuttles * 350
        score -= oppShuttles * 450

        // 5. Qualité positionnelle des pions (libertés & centre)
        for ((pos, piece) in state.board) {
            if (piece == null) continue
            val isCenter = pos.row in 1..3 && pos.col in 1..4
            val liberties = getNeighbors(pos).count { state.board[it] == null }

            if (piece == aiPlayer) {
                if (isCenter) score += 15
                if (liberties == 0) score -= 45 // Pion allié paralysé
                else if (liberties >= 2) score += 20 // Pion allié dynamique
            } else {
                if (isCenter) score -= 15
                if (liberties == 0) score += 35 // Pion adverse paralysé (bénéfique pour l'IA)
            }
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
