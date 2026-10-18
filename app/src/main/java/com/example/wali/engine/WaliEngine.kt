package com.example.wali.engine

import com.example.wali.model.GameState
import com.example.wali.model.Line
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.model.WaliRuleOptions

/**
 * Moteur de règles pur pour le jeu Wali Songhaï de Tombouctou (Mali).
 *
 * Implémentation stricte et déterministe des règles officielles :
 * - Plateau 5x6 (30 cases, A1-F5)
 * - 12 Bâtonnets (commence) vs 12 Cailloux (total 24 pions, 6 cases vides après pose)
 * - Phase 1 (Pose) : pose alternée, interdiction absolue de créer une ligne de 3
 * - Phase 2 (Déplacement) : déplacement orthogonal d'une case vers une case vide
 * - Formation de ligne : 3 pions orthogonaux consécutifs (jamais diagonal)
 * - Capture : 1 capture par ligne nouvellement créée
 * - Réutilisation : une ligne doit être cassée puis reformée pour recapturer,
 *   mais une autre ligne distincte formée ailleurs capture même si l'ancienne reste intacte
 * - Cheval double : deux lignes créées en un seul coup = 2 captures distinctes
 * - Victoire / Défaite : par élimination (0 pion) ou par blocage complet (aucun mouvement légal)
 */
object WaliEngine {

    /**
     * Initialise une nouvelle partie de Wali Songhaï.
     */
    fun initializeGame(ruleOptions: WaliRuleOptions = WaliRuleOptions()): GameState {
        val initialBoard = Position.allPositions.associateWith { null as Player? }
        return GameState(
            board = initialBoard,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = Position.PIECES_PER_PLAYER,
            stonesRemaining = Position.PIECES_PER_PLAYER,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            pendingCaptures = 0,
            ruleOptions = ruleOptions
        )
    }

    /**
     * Retourne la liste des cases valides pour poser un pion :
     * - En phase de placement (phase 1).
     * - En phase de déplacement (phase 2) si le joueur courant a au moins un pion en attente de pose.
     * Règle essentielle : INTERDICTION de poser un pion si cela crée immédiatement
     * une ligne de trois pions du même joueur (horizontale ou verticale).
     */
    fun getValidPlacements(state: GameState): List<Position> {
        if (state.winner != null || state.pendingCaptures > 0) {
            return emptyList()
        }

        val player = state.currentPlayer
        val canPlace = when (state.phase) {
            Phase.PLACEMENT -> true
            Phase.MOVEMENT -> state.hasPendingPlacements(player)
            Phase.GAME_OVER -> false
        }
        if (!canPlace) return emptyList()

        return Position.allPositions.filter { pos ->
            state.board[pos] == null && !wouldFormLineOfThree(state.board, pos, player)
        }
    }

    /**
     * Indique si le joueur actif doit obligatoirement poser un pion ce tour-ci :
     * - En phase 1 (PLACEMENT).
     * - En phase 2 (MOVEMENT) si le joueur a au moins un pion en attente et qu'une case de pose légale est disponible.
     */
    fun isPlacementRequired(state: GameState): Boolean {
        if (state.winner != null || state.pendingCaptures > 0) return false
        if (state.phase == Phase.PLACEMENT) return true
        if (state.phase == Phase.MOVEMENT) {
            return state.hasPendingPlacements(state.currentPlayer) && getValidPlacements(state).isNotEmpty()
        }
        return false
    }

    /**
     * Effectue la transition de la phase de pose vers la phase de déplacement.
     * Prend en compte les pions éventuels restés en attente de pose suite à un blocage.
     */
    private fun transitionToMovement(
        newBoard: Map<Position, Player?>,
        firstPlayer: Player,
        sticksPending: Int = 0,
        stonesPending: Int = 0,
        history: List<String>,
        ruleOptions: WaliRuleOptions = WaliRuleOptions()
    ): GameState {
        val transitionMsg = if (sticksPending > 0 || stonesPending > 0) {
            val pendingDesc = mutableListOf<String>()
            if (sticksPending > 0) pendingDesc.add("$sticksPending pion(s) BÂTONNETS en attente")
            if (stonesPending > 0) pendingDesc.add("$stonesPending pion(s) CAILLOUX en attente")
            "Blocage de pose en fin de phase 1 (${pendingDesc.joinToString(", ")}). Début anticipé des déplacements !"
        } else {
            "Fin de la phase de pose. Début des déplacements."
        }
        val transitionHistory = history + transitionMsg

        val sticksOnBoard = newBoard.values.count { it == Player.STICKS }
        val stonesOnBoard = newBoard.values.count { it == Player.STONES }

        val movementState = GameState(
            board = newBoard,
            currentPlayer = firstPlayer,
            phase = Phase.MOVEMENT,
            sticksRemaining = sticksOnBoard + sticksPending,
            stonesRemaining = stonesOnBoard + stonesPending,
            winner = null,
            usedLines = emptySet(),
            moveHistory = transitionHistory,
            pendingCaptures = 0,
            sticksPendingPlacement = sticksPending,
            stonesPendingPlacement = stonesPending,
            ruleOptions = ruleOptions
        )

        // Vérification si le premier joueur est bloqué (ni pose, ni déplacement possible)
        val canAct = isPlacementRequired(movementState) || hasLegalMove(movementState, firstPlayer)
        return if (!canAct) {
            movementState.copy(
                winner = firstPlayer.opponent,
                phase = Phase.GAME_OVER,
                moveHistory = transitionHistory + "$firstPlayer est bloqué dès le départ. Victoire de ${firstPlayer.opponent} !"
            )
        } else {
            movementState
        }
    }

    /**
     * Vérifie si le joueur courant en phase de placement est totalement bloqué (aucune case de pose légale).
     * Si oui, effectue immédiatement la transition anticipée vers la phase de déplacement.
     */
    fun checkAndHandlePlacementBlockage(state: GameState): GameState {
        if (state.phase != Phase.PLACEMENT || state.winner != null) return state
        val player = state.currentPlayer
        val toPlace = if (player == Player.STICKS) state.sticksRemaining else state.stonesRemaining
        if (toPlace > 0 && getValidPlacements(state).isEmpty()) {
            return transitionToMovement(
                newBoard = state.board,
                firstPlayer = player,
                sticksPending = state.sticksRemaining,
                stonesPending = state.stonesRemaining,
                history = state.moveHistory,
                ruleOptions = state.ruleOptions
            )
        }
        return state
    }

    /**
     * Pose un pion pour le joueur courant à la position spécifiée :
     * - En phase de placement (phase 1).
     * - En phase de déplacement (phase 2) si le joueur a au moins un pion en attente.
     */
    fun placePiece(state: GameState, position: Position): GameState {
        require(state.winner == null) { "Action impossible : la partie est déjà terminée" }
        require(state.pendingCaptures == 0) { "Capture en attente : vous devez éliminer un pion adverse avant de rejouer" }

        if (state.phase == Phase.MOVEMENT) {
            // Pose d'un pion en attente en phase de déplacement (CORRECTION 3)
            val player = state.currentPlayer
            require(state.hasPendingPlacements(player)) {
                "Le joueur $player n'a aucun pion en attente de pose"
            }
            val validPlacements = getValidPlacements(state)
            require(validPlacements.isNotEmpty()) {
                "Aucune case de pose légale disponible ce tour-ci"
            }
            require(position in validPlacements) {
                "Case invalide pour la pose du pion en attente : $position"
            }

            val newBoard = state.board + (position to player)
            val newSticksPending = if (player == Player.STICKS) state.sticksPendingPlacement - 1 else state.sticksPendingPlacement
            val newStonesPending = if (player == Player.STONES) state.stonesPendingPlacement - 1 else state.stonesPendingPlacement
            val historyEntry = "$player a posé son pion en attente en $position"
            val updatedHistory = state.moveHistory + historyEntry

            val nextPlayer = player.opponent
            val nextState = state.copy(
                board = newBoard,
                currentPlayer = nextPlayer,
                moveHistory = updatedHistory,
                sticksPendingPlacement = newSticksPending,
                stonesPendingPlacement = newStonesPending
            )

            // Vérification si le joueur adverse est bloqué (ni pose, ni déplacement)
            val opponentCanAct = isPlacementRequired(nextState) || hasLegalMove(nextState, nextPlayer)
            return if (!opponentCanAct) {
                nextState.copy(
                    winner = player,
                    phase = Phase.GAME_OVER,
                    moveHistory = updatedHistory + "$nextPlayer est bloqué. Victoire de $player !"
                )
            } else {
                nextState
            }
        }

        require(state.phase == Phase.PLACEMENT) { "Action impossible : la phase actuelle n'est pas PLACEMENT (${state.phase})" }
        require(state.board[position] == null) { "Case occupée : $position" }
        require(!wouldFormLineOfThree(state.board, position, state.currentPlayer)) {
            "Coup interdit en phase de pose : la pose en $position crée une ligne de 3 pions pour ${state.currentPlayer}"
        }

        val player = state.currentPlayer
        val newBoard = state.board + (position to player)

        val nextSticksToPlace = if (player == Player.STICKS) state.sticksRemaining - 1 else state.sticksRemaining
        val nextStonesToPlace = if (player == Player.STONES) state.stonesRemaining - 1 else state.stonesRemaining
        val historyEntry = "$player posé en $position"
        val updatedHistory = state.moveHistory + historyEntry

        val totalPlaced = (Position.PIECES_PER_PLAYER - nextSticksToPlace) + (Position.PIECES_PER_PLAYER - nextStonesToPlace)
        val allPiecesPlaced = (totalPlaced == Position.PIECES_PER_PLAYER * 2)

        if (allPiecesPlaced) {
            // Fin normale de phase de pose (24 pions posés). Passage en phase de déplacement.
            // BÂTONNETS commence toujours la phase de déplacement standard.
            return transitionToMovement(
                newBoard = newBoard,
                firstPlayer = Player.STICKS,
                sticksPending = 0,
                stonesPending = 0,
                history = updatedHistory,
                ruleOptions = state.ruleOptions
            )
        }

        val nextPlayer = player.opponent
        val nextPlayerToPlace = if (nextPlayer == Player.STICKS) nextSticksToPlace else nextStonesToPlace
        val validPlacementsForNext = Position.allPositions.filter {
            newBoard[it] == null && !wouldFormLineOfThree(newBoard, it, nextPlayer)
        }

        return if (nextPlayerToPlace > 0 && validPlacementsForNext.isEmpty()) {
            // CORRECTION 3 : Blocage de pose en fin de phase 1.
            // Si le joueur suivant n'a AUCUNE case vide légale alors qu'il lui reste au moins un pion à poser,
            // ses pions restants (ainsi que ceux de l'autre joueur s'il en restait) deviennent en attente de pose,
            // et la partie bascule en phase 2 (déplacement).
            transitionToMovement(
                newBoard = newBoard,
                firstPlayer = nextPlayer,
                sticksPending = nextSticksToPlace,
                stonesPending = nextStonesToPlace,
                history = updatedHistory,
                ruleOptions = state.ruleOptions
            )
        } else {
            // Poursuite normale de la pose : tour au joueur suivant
            state.copy(
                board = newBoard,
                currentPlayer = nextPlayer,
                sticksRemaining = nextSticksToPlace,
                stonesRemaining = nextStonesToPlace,
                moveHistory = updatedHistory
            )
        }
    }

    /**
     * Simule chaque déplacement légal de chaque pion du joueur courant en phase MOVEMENT,
     * applique [detectNewLines] en tenant compte de [GameState.usedLines], et ne retient que
     * les déplacements produisant une nouvelle ligne donnant réellement droit à capture.
     */
    fun getObligatoryMoves(state: GameState): Map<Position, List<Position>> {
        if (state.phase != Phase.MOVEMENT || state.winner != null || state.pendingCaptures > 0) {
            return emptyMap()
        }
        if (isPlacementRequired(state)) {
            return emptyMap()
        }

        val player = state.currentPlayer
        val opponent = player.opponent
        val opponentHasPieces = state.board.values.any { it == opponent }
        if (!opponentHasPieces) {
            return emptyMap()
        }

        val obligatory = mutableMapOf<Position, List<Position>>()
        for ((from, owner) in state.board) {
            if (owner != player) continue
            val emptyNeighbors = getOrthogonalNeighbors(from).filter { neighbor ->
                state.board[neighbor] == null
            }
            val capturingTargets = emptyNeighbors.filter { to ->
                val boardAfter = state.board + (from to null) + (to to player)
                val newLines = detectNewLines(state.board, boardAfter, player, movedTo = to)
                    .filter { it !in state.usedLines }
                newLines.isNotEmpty()
            }
            if (capturingTargets.isNotEmpty()) {
                obligatory[from] = capturingTargets
            }
        }
        return obligatory
    }

    /**
     * Retourne les destinations valides pour déplacer le pion situé en [position].
     * Uniquement orthogonal (haut/bas/gauche/droite) d'une seule case vers une case vide.
     * Si l'option [WaliRuleOptions.mandatoryAlignment] est active et qu'au moins un coup
     * capturant existe sur le plateau, seules ces destinations sont autorisées.
     */
    fun getValidMoves(state: GameState, position: Position): List<Position> {
        if (state.phase != Phase.MOVEMENT || state.winner != null || state.pendingCaptures > 0) {
            return emptyList()
        }
        // CORRECTION 3 : Refus de tout déplacement normal tant qu'une case de pose légale
        // existe pour le pion en attente de ce joueur.
        if (isPlacementRequired(state)) {
            return emptyList()
        }
        if (state.board[position] != state.currentPlayer) {
            return emptyList()
        }

        val baseMoves = getOrthogonalNeighbors(position).filter { neighbor ->
            state.board[neighbor] == null
        }

        if (!state.ruleOptions.mandatoryAlignment) {
            return baseMoves
        }

        val obligatoryMoves = getObligatoryMoves(state)
        return if (obligatoryMoves.isNotEmpty()) {
            obligatoryMoves[position] ?: emptyList()
        } else {
            baseMoves
        }
    }

    /**
     * Vérifie si le joueur [player] dispose d'au moins une action légale (pose différée ou déplacement).
     */
    fun hasLegalMove(state: GameState, player: Player): Boolean {
        if (state.phase != Phase.MOVEMENT) return true
        if (state.winner != null) return false

        // CORRECTION 3 : Si le joueur a un pion en attente et une case légale disponible, il n'est pas bloqué
        if (state.hasPendingPlacements(player)) {
            val validPlacements = Position.allPositions.filter { pos ->
                state.board[pos] == null && !wouldFormLineOfThree(state.board, pos, player)
            }
            if (validPlacements.isNotEmpty()) return true
        }

        return state.board.any { (pos, piece) ->
            piece == player && getOrthogonalNeighbors(pos).any { neighbor ->
                state.board[neighbor] == null
            }
        }
    }

    /**
     * Déplace un pion de [from] vers [to].
     * Vérifie la formation de nouvelles lignes de trois (cheval simple ou cheval double).
     */
    fun movePiece(state: GameState, from: Position, to: Position): GameState {
        require(state.phase == Phase.MOVEMENT) { "Action impossible : la phase actuelle n'est pas MOVEMENT (${state.phase})" }
        require(state.winner == null) { "Action impossible : la partie est terminée" }
        require(state.pendingCaptures == 0) { "Capture en attente : vous devez éliminer un pion adverse avant de rejouer" }
        require(!isPlacementRequired(state)) {
            "Action impossible : vous devez poser votre pion en attente sur une case disponible ce tour-ci"
        }
        require(state.board[from] == state.currentPlayer) { "Aucun pion de ${state.currentPlayer} sur la case $from" }
        require(to in getValidMoves(state, from)) { "Déplacement invalide de $from vers $to" }

        val player = state.currentPlayer
        val opponent = player.opponent
        val newBoard = state.board + (from to null) + (to to player)

        // Détection des lignes de 3 nouvellement créées par ce déplacement
        val newLines = detectNewLines(state.board, newBoard, player, movedTo = to)
            .filter { it !in state.usedLines }

        // Nettoyage des lignes de [player] qui ont été cassées par ce mouvement :
        // une ligne déjà utilisée qui est cassée n'est plus dans usedLines
        val allCurrentPlayerLines = detectLines(newBoard, player).toSet()
        val unbrokenUsedLines = state.usedLines.filter { it in allCurrentPlayerLines }.toSet()

        val moveDesc = "$player déplacé de $from vers $to"
        val updatedHistory = state.moveHistory + moveDesc

        return if (newLines.isNotEmpty()) {
            // Une ou plusieurs nouvelles lignes formées !
            // RÈGLE STRICTE WALI : Peu importe le nombre de lignes formées (cheval simple ou double),
            // cela donne droit à UNE SEULE capture au total pour ce coup.
            val opponentPiecesCount = newBoard.values.count { it == opponent }
            val capturesAllowed = if (opponentPiecesCount > 0) 1 else 0

            val updatedUsedLines = unbrokenUsedLines + newLines

            if (capturesAllowed > 0) {
                // Le joueur doit capturer un unique pion adverse
                val lineDescription = if (newLines.size > 1) "Cheval double formé !" else "Ligne de trois formée !"
                state.copy(
                    board = newBoard,
                    usedLines = updatedUsedLines,
                    moveHistory = updatedHistory + "$lineDescription $player doit capturer 1 pion adverse.",
                    pendingCaptures = 1
                )
            } else {
                // L'adversaire n'avait plus de pions (victoire)
                state.copy(
                    board = newBoard,
                    winner = player,
                    phase = Phase.GAME_OVER,
                    usedLines = updatedUsedLines,
                    moveHistory = updatedHistory + "Plus aucun pion adverse. Victoire de $player !",
                    pendingCaptures = 0
                )
            }
        } else {
            // Aucune nouvelle ligne formée : tour au joueur suivant
            val nextPlayer = opponent
            val nextPlayerPieces = newBoard.values.count { it == nextPlayer }

            if (nextPlayerPieces == 0) {
                state.copy(
                    board = newBoard,
                    winner = player,
                    phase = Phase.GAME_OVER,
                    usedLines = unbrokenUsedLines,
                    moveHistory = updatedHistory + "Victoire de $player !",
                    pendingCaptures = 0
                )
            } else {
                val nextState = state.copy(
                    board = newBoard,
                    currentPlayer = nextPlayer,
                    usedLines = unbrokenUsedLines,
                    moveHistory = updatedHistory,
                    pendingCaptures = 0
                )

                // Vérification du blocage pour le prochain joueur
                if (!hasLegalMove(nextState, nextPlayer)) {
                    nextState.copy(
                        winner = player,
                        phase = Phase.GAME_OVER,
                        moveHistory = updatedHistory + "$nextPlayer est bloqué (aucun déplacement possible). Victoire de $player !"
                    )
                } else {
                    nextState
                }
            }
        }
    }

    /**
     * Indique si une capture est requise avant toute autre action.
     */
    fun canCapture(state: GameState): Boolean {
        return state.phase == Phase.MOVEMENT && state.pendingCaptures > 0 && state.winner == null
    }

    /**
     * Capture un pion adverse situé à [targetPosition].
     */
    fun capturePiece(state: GameState, targetPosition: Position): GameState {
        require(canCapture(state)) { "Aucune capture n'est autorisée actuellement" }
        val player = state.currentPlayer
        val opponent = player.opponent
        require(state.board[targetPosition] == opponent) {
            "La cible en $targetPosition ne contient pas de pion adverse ($opponent)"
        }

        val newBoard = state.board + (targetPosition to null)
        val newSticks = if (opponent == Player.STICKS) state.sticksRemaining - 1 else state.sticksRemaining
        val newStones = if (opponent == Player.STONES) state.stonesRemaining - 1 else state.stonesRemaining

        // Si le pion adverse capturé faisait partie d'une ligne adverse enregistrée comme used, elle est maintenant cassée
        val allValidLines = (detectLines(newBoard, Player.STICKS) + detectLines(newBoard, Player.STONES)).toSet()
        val updatedUsedLines = state.usedLines.filter { it in allValidLines }.toSet()

        val historyDesc = "$player a capturé le pion adverse en $targetPosition"
        val updatedHistory = state.moveHistory + historyDesc

        val remainingCaptures = state.pendingCaptures - 1
        val opponentPiecesLeft = newBoard.values.count { it == opponent }

        return if (remainingCaptures > 0 && opponentPiecesLeft > 0) {
            // Encore une capture à effectuer (ex: cheval double)
            state.copy(
                board = newBoard,
                sticksRemaining = newSticks,
                stonesRemaining = newStones,
                usedLines = updatedUsedLines,
                moveHistory = updatedHistory,
                pendingCaptures = minOf(remainingCaptures, opponentPiecesLeft)
            )
        } else {
            // Captures terminées pour ce tour
            // Ordre de vérification :
            // 1. L'adversaire a-t-il encore des pions ?
            if (opponentPiecesLeft == 0) {
                state.copy(
                    board = newBoard,
                    sticksRemaining = newSticks,
                    stonesRemaining = newStones,
                    winner = player,
                    phase = Phase.GAME_OVER,
                    usedLines = updatedUsedLines,
                    moveHistory = updatedHistory + "$opponent n'a plus aucun pion. Victoire de $player !",
                    pendingCaptures = 0
                )
            } else {
                // 2. État après capture et vérification immédiate des conditions de fin de partie
                val nextState = state.copy(
                    board = newBoard,
                    currentPlayer = opponent,
                    sticksRemaining = newSticks,
                    stonesRemaining = newStones,
                    usedLines = updatedUsedLines,
                    moveHistory = updatedHistory,
                    pendingCaptures = 0
                )

                if (nextState.ruleOptions.defeatAtTwoPieces &&
                    nextState.phase == Phase.MOVEMENT &&
                    !nextState.hasPendingPlacements(opponent) &&
                    nextState.piecesOnBoard(opponent) == 2
                ) {
                    nextState.copy(
                        winner = player,
                        phase = Phase.GAME_OVER,
                        moveHistory = updatedHistory + "$opponent est réduit à deux pions. Victoire de $player !"
                    )
                } else if (!hasLegalMove(nextState, opponent)) {
                    nextState.copy(
                        winner = player,
                        phase = Phase.GAME_OVER,
                        moveHistory = updatedHistory + "$opponent est bloqué. Victoire de $player !"
                    )
                } else {
                    nextState
                }
            }
        }
    }

    /**
     * Détecte toutes les lignes de trois pions consécutifs pour [player] sur [board].
     * Orthogonal uniquement (horizontales et verticales). Les diagonales sont exclues.
     * RÈGLE STRICTE WALI : Seuls les alignements d'EXACTEMENT 3 pions consécutifs sont valides.
     * Tout alignement de 4 pions ou plus ne donne droit à aucune ligne ni capture.
     */
    fun detectLines(board: Map<Position, Player?>, player: Player): List<Line> {
        val lines = mutableListOf<Line>()

        // Lignes horizontales : segments contigus de pions pour [player] sur chaque ligne
        for (r in 0 until Position.ROWS) {
            var c = 0
            while (c < Position.COLS) {
                if (board[Position(r, c)] == player) {
                    var end = c
                    while (end < Position.COLS && board[Position(r, end)] == player) {
                        end++
                    }
                    val length = end - c
                    if (length == 3) {
                        lines.add(Line(setOf(Position(r, c), Position(r, c + 1), Position(r, c + 2))))
                    }
                    c = end
                } else {
                    c++
                }
            }
        }

        // Lignes verticales : segments contigus de pions pour [player] sur chaque colonne
        for (c in 0 until Position.COLS) {
            var r = 0
            while (r < Position.ROWS) {
                if (board[Position(r, c)] == player) {
                    var end = r
                    while (end < Position.ROWS && board[Position(end, c)] == player) {
                        end++
                    }
                    val length = end - r
                    if (length == 3) {
                        lines.add(Line(setOf(Position(r, c), Position(r + 1, c), Position(r + 2, c))))
                    }
                    r = end
                } else {
                    r++
                }
            }
        }

        return lines
    }

    /**
     * Compare l'état du plateau avant et après un déplacement pour identifier les lignes
     * nouvellement créées par ce coup pour [player].
     */
    fun detectNewLines(
        boardBefore: Map<Position, Player?>,
        boardAfter: Map<Position, Player?>,
        player: Player,
        movedTo: Position? = null
    ): List<Line> {
        val linesBefore = detectLines(boardBefore, player).toSet()
        val linesAfter = detectLines(boardAfter, player)
        return linesAfter.filter { it !in linesBefore && (movedTo == null || movedTo in it.cells) }
    }

    /**
     * Vérifie la condition de victoire/défaite selon l'ordre :
     * 1. Élimination (un joueur n'a plus aucun pion)
     * 2. Blocage (le joueur courant n'a aucun coup légal au début de son tour)
     */
    fun checkWinCondition(state: GameState): Player? {
        if (state.phase == Phase.GAME_OVER) return state.winner
        if (state.phase == Phase.PLACEMENT) return null

        val sticks = state.piecesOnBoard(Player.STICKS)
        val stones = state.piecesOnBoard(Player.STONES)

        if (sticks == 0) return Player.STONES
        if (stones == 0) return Player.STICKS

        if (state.pendingCaptures == 0 && !hasLegalMove(state, state.currentPlayer)) {
            return state.currentPlayer.opponent
        }

        if (state.ruleOptions.defeatAtTwoPieces && state.phase == Phase.MOVEMENT) {
            if (!state.hasPendingPlacements(Player.STICKS) && sticks == 2) {
                return Player.STONES
            }
            if (!state.hasPendingPlacements(Player.STONES) && stones == 2) {
                return Player.STICKS
            }
        }

        return null
    }

    /**
     * Passe le tour au joueur adverse manuellement, en vérifiant si le joueur suivant est bloqué.
     */
    fun switchPlayer(state: GameState): GameState {
        require(state.pendingCaptures == 0) { "Impossible de passer le tour avec une capture en attente" }
        val nextPlayer = state.currentPlayer.opponent
        val tempState = state.copy(currentPlayer = nextPlayer)

        return if (state.phase == Phase.MOVEMENT && !hasLegalMove(tempState, nextPlayer)) {
            tempState.copy(winner = state.currentPlayer, phase = Phase.GAME_OVER)
        } else {
            tempState
        }
    }

    // --- Fonctions utilitaires internes ---

    /**
     * Vérifie si poser un pion en [pos] pour [player] créerait une ligne de trois.
     */
    private fun wouldFormLineOfThree(
        board: Map<Position, Player?>,
        pos: Position,
        player: Player
    ): Boolean {
        val r = pos.row
        val c = pos.col

        // Vérification horizontale (3 combinaisons possibles où pos est l'une des 3 cases)
        // 1. pos est à droite : (r, c-2), (r, c-1), pos
        if (c >= 2 && board[Position(r, c - 2)] == player && board[Position(r, c - 1)] == player) {
            return true
        }
        // 2. pos est au milieu : (r, c-1), pos, (r, c+1)
        if (c in 1 until (Position.COLS - 1) && board[Position(r, c - 1)] == player && board[Position(r, c + 1)] == player) {
            return true
        }
        // 3. pos est à gauche : pos, (r, c+1), (r, c+2)
        if (c <= Position.COLS - 3 && board[Position(r, c + 1)] == player && board[Position(r, c + 2)] == player) {
            return true
        }

        // Vérification verticale (3 combinaisons possibles où pos est l'une des 3 cases)
        // 1. pos est en bas : (r-2, c), (r-1, c), pos
        if (r >= 2 && board[Position(r - 2, c)] == player && board[Position(r - 1, c)] == player) {
            return true
        }
        // 2. pos est au milieu : (r-1, c), pos, (r+1, c)
        if (r in 1 until (Position.ROWS - 1) && board[Position(r - 1, c)] == player && board[Position(r + 1, c)] == player) {
            return true
        }
        // 3. pos est en haut : pos, (r+1, c), (r+2, c)
        if (r <= Position.ROWS - 3 && board[Position(r + 1, c)] == player && board[Position(r + 2, c)] == player) {
            return true
        }

        return false
    }

    /**
     * Voisins orthogonaux valides dans les limites de la grille 5x6.
     */
    private fun getOrthogonalNeighbors(pos: Position): List<Position> {
        val neighbors = mutableListOf<Position>()
        if (pos.row > 0) neighbors.add(Position(pos.row - 1, pos.col))
        if (pos.row < Position.ROWS - 1) neighbors.add(Position(pos.row + 1, pos.col))
        if (pos.col > 0) neighbors.add(Position(pos.row, pos.col - 1))
        if (pos.col < Position.COLS - 1) neighbors.add(Position(pos.row, pos.col + 1))
        return neighbors
    }
}
