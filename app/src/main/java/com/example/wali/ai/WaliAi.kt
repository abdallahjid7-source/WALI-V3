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
     * Retourne null si aucun coup légal n'existe (par exemple si la partie est terminée).
     */
    fun chooseMove(
        state: GameState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): AiMove? {
        if (state.phase == Phase.GAME_OVER || state.winner != null) {
            return null
        }

        return when (difficulty) {
            AiDifficulty.EASY -> chooseEasyMove(state, random)
            AiDifficulty.MEDIUM -> chooseMediumMove(state, random)
            AiDifficulty.HARD -> chooseHardMove(state, random)
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
                // Niveau Difficile : évaluation tactique chirurgicale des pions adverses
                capturablePositions.maxByOrNull { pos ->
                    evaluateTargetValueDeep(state, pos)
                } ?: capturablePositions.random(random)
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

        val player = state.currentPlayer
        val playerPieces = state.board.filter { it.value == player }.keys
        val moves = mutableListOf<Pair<Position, Position>>()

        for (from in playerPieces) {
            val destinations = WaliEngine.getValidMoves(state, from)
            for (to in destinations) {
                moves.add(from to to)
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
                    val nextState = WaliEngine.movePiece(state, from, to)
                    nextState.winner == aiPlayer
                }
                if (immediateWinningMoves.isNotEmpty()) {
                    val chosen = immediateWinningMoves.random(random)
                    return AiMove(moveFrom = chosen.first, moveTo = chosen.second)
                }

                val captureMoves = allMoves.filter { (from, to) ->
                    val nextState = WaliEngine.movePiece(state, from, to)
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
                    val nextState = WaliEngine.movePiece(state, from, to)
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
    // NIVEAU DIFFICILE (MAÎTRE DU WALI / REDOUTABLE)
    // =========================================================================

    private fun chooseHardMove(state: GameState, random: Random): AiMove? {
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
                    val nextState = WaliEngine.placePiece(state, pos)
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
                    val nextState = WaliEngine.movePiece(state, from, to)
                    if (nextState.winner == aiPlayer) {
                        return AiMove(moveFrom = from, moveTo = to)
                    }
                    if (WaliEngine.canCapture(nextState)) {
                        val oppPieces = nextState.board.filter { it.value == aiPlayer.opponent }.keys
                        for (target in oppPieces) {
                            val afterCapture = WaliEngine.capturePiece(nextState, target)
                            if (afterCapture.winner == aiPlayer) {
                                return AiMove(moveFrom = from, moveTo = to)
                            }
                        }
                    }
                }

                // 2. Recherche Alpha-Beta approfondie (profondeur 4 à 5 selon le nombre de pions)
                val totalPieces = state.board.values.count { it != null }
                val searchDepth = if (totalPieces <= 14) 5 else 4

                val orderedMoves = orderMoves(state, allMoves, aiPlayer)
                var bestScore = Int.MIN_VALUE
                val bestMoves = mutableListOf<Pair<Position, Position>>()

                for ((from, to) in orderedMoves) {
                    val nextState = WaliEngine.movePiece(state, from, to)
                    val stateAfterAction = resolveAiCapturesForSimulation(nextState, aiPlayer, AiDifficulty.HARD, random)
                    val score = minimax(
                        state = stateAfterAction,
                        depth = searchDepth - 1,
                        alpha = bestScore.coerceAtLeast(Int.MIN_VALUE + 1000),
                        beta = Int.MAX_VALUE,
                        isMaximizing = false,
                        aiPlayer = aiPlayer
                    )

                    if (score > bestScore) {
                        bestScore = score
                        bestMoves.clear()
                        bestMoves.add(from to to)
                    } else if (score == bestScore) {
                        bestMoves.add(from to to)
                    }
                }

                val chosen = (bestMoves.ifEmpty { allMoves }).random(random)
                AiMove(moveFrom = chosen.first, moveTo = chosen.second)
            }
            Phase.GAME_OVER -> null
        }
    }

    // =========================================================================
    // MINIMAX AVEC ÉLAGAGE ALPHA-BETA ET ÉVALUATION HEURISTIQUE RICHE
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
                // Aucun coup légal -> défaite immédiate par blocage
                return -100_000 - depth
            }

            val ordered = orderMoves(state, moves, aiPlayer)
            for ((from, to) in ordered) {
                val nextState = WaliEngine.movePiece(state, from, to)
                val stateAfterAction = resolveAiCapturesForSimulation(nextState, aiPlayer, AiDifficulty.HARD, Random.Default)
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
                // L'adversaire est bloqué -> victoire immédiate de l'IA !
                return 100_000 + depth
            }

            val ordered = orderMoves(state, moves, oppPlayer)
            for ((from, to) in ordered) {
                val nextState = WaliEngine.movePiece(state, from, to)
                val stateAfterAction = resolveAiCapturesForSimulation(nextState, oppPlayer, AiDifficulty.HARD, Random.Default)
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
        random: Random
    ): GameState {
        if (!WaliEngine.canCapture(state)) return state
        val targetOpponent = playerWhoMoved.opponent
        val capturables = state.board.filter { it.value == targetOpponent }.keys
        val bestTarget = chooseCaptureTarget(state, capturables, difficulty, random) ?: return state
        return WaliEngine.capturePiece(state, bestTarget)
    }

    // =========================================================================
    // ORDONNANCEMENT HEURISTIQUE DES COUPS (MOVE ORDERING)
    // =========================================================================

    /**
     * Trie les coups légaux afin de maximiser le taux de coupure Alpha-Beta :
     * 1. Coups formant immédiatement une ligne (capture immédiate).
     * 2. Coups bloquant une menace directe de l'adversaire (interposition défensive).
     * 3. Coups créant une fourchette (double menace).
     * 4. Coups créant une menace directe d'alignement à 1 temps.
     * 5. Coups maintenant ou étendant la mobilité (libertés).
     */
    private fun orderMoves(
        state: GameState,
        moves: List<Pair<Position, Position>>,
        player: Player
    ): List<Pair<Position, Position>> {
        val opponent = player.opponent
        val oppImminentTargets = getImminentLineTargets(state, opponent)

        return moves.sortedByDescending { (from, to) ->
            var priority = 0

            // 1. Capture immédiate permise par ce coup
            val nextState = WaliEngine.movePiece(state, from, to)
            if (WaliEngine.canCapture(nextState) || nextState.winner == player) {
                priority += 25_000
            }

            // 2. Blocage défensif d'une case cible adverse imminente
            if (to in oppImminentTargets) {
                priority += 12_000
            }

            // 3. Menaces créées par ce coup
            val threatsCreated = countImminentLineThreats(nextState, player)
            if (threatsCreated >= 2) {
                priority += 8_000 // Fourchette créée !
            } else if (threatsCreated == 1) {
                priority += 4_000 // Menace créée !
            }

            // 4. Position centrale du point d'arrivée
            if (to.row in 1..3 && to.col in 1..4) {
                priority += 300
            }

            // 5. Préservation des libertés (départ d'une case étouffée)
            val neighborsOfTo = getNeighbors(to).count { nextState.board[it] == null }
            priority += neighborsOfTo * 80

            priority
        }
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
