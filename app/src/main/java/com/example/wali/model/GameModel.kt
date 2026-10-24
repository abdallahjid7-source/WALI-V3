package com.example.wali.model

/**
 * Joueur du Wali Songhaï.
 * BÂTONNETS (STICKS) commence toujours la partie.
 * CAILLOUX (STONES) joue en second.
 */
enum class Player {
    STICKS,
    STONES;

    val opponent: Player
        get() = if (this == STICKS) STONES else STICKS
}

/**
 * Phase actuelle du jeu :
 * - PLACEMENT : pose alternée des 24 pions (12 chacun), sans former de ligne de trois.
 * - MOVEMENT : déplacement orthogonal d'une case vers une case vide, formation de lignes et captures.
 * - GAME_OVER : partie terminée (par élimination ou par blocage complet).
 */
enum class Phase {
    PLACEMENT,
    MOVEMENT,
    GAME_OVER
}

/**
 * Coordonnées d'une case sur le plateau 5 lignes × 6 colonnes (30 cases).
 * [row] : 0 à 4 (lignes 1 à 5)
 * [col] : 0 à 5 (colonnes A à F)
 */
data class Position(val row: Int, val col: Int) {
    init {
        require(row in 0 until ROWS) { "Row doit être compris entre 0 et ${ROWS - 1}, valeur: $row" }
        require(col in 0 until COLS) { "Col doit être compris entre 0 et ${COLS - 1}, valeur: $col" }
    }

    val algebraic: String
        get() = "${('A'.code + col).toChar()}${row + 1}"

    override fun toString(): String = algebraic

    companion object {
        const val ROWS = 5
        const val COLS = 6
        const val TOTAL_CELLS = ROWS * COLS // 30
        const val PIECES_PER_PLAYER = 12

        fun fromAlgebraic(str: String): Position {
            require(str.length == 2) { "Notation algébrique invalide: $str" }
            val col = str[0].uppercaseChar() - 'A'
            val row = str[1].digitToInt() - 1
            return Position(row, col)
        }

        val allPositions: List<Position> = (0 until ROWS).flatMap { r ->
            (0 until COLS).map { c -> Position(r, c) }
        }
    }
}

/**
 * Ligne orthogonale de trois pions du même joueur (horizontale ou verticale).
 * Les diagonales ne comptent jamais dans le Wali Songhaï.
 */
data class Line(val cells: Set<Position>) {
    init {
        require(cells.size == 3) { "Une ligne doit contenir exactement 3 cases" }
    }

    override fun toString(): String = "Line(${cells.sortedWith(compareBy({ it.row }, { it.col })).joinToString("-")})"
}

/**
 * Variantes de règles optionnelles configurables dans les réglages.
 * Désactivées par défaut pour préserver le comportement classique.
 *
 * @property mandatoryAlignment Option 1 : Alignement et capture obligatoires en phase de déplacement.
 * @property defeatAtTwoPieces Option 2 : Défaite immédiate à deux pions en phase de déplacement (sans pion en attente).
 */
data class WaliRuleOptions(
    val mandatoryAlignment: Boolean = false,
    val defeatAtTwoPieces: Boolean = false
)

/**
 * État immuable de la partie de Wali Songhaï.
 *
 * @property board Grille associant chaque case [Position] à son occupant [Player] ou null si vide.
 * @property currentPlayer Joueur dont c'est le tour d'agir (poser, déplacer, ou capturer).
 * @property phase Phase actuelle du jeu (PLACEMENT, MOVEMENT, GAME_OVER).
 * @property sticksRemaining Pions restants : à poser en PLACEMENT (12 -> 0), ou vivants en MOVEMENT (12 -> 0).
 * @property stonesRemaining Pions restants : à poser en PLACEMENT (12 -> 0), ou vivants en MOVEMENT (12 -> 0).
 * @property winner Vainqueur de la partie, ou null si la partie est toujours en cours.
 * @property usedLines Ensemble des lignes actuellement sur le plateau ayant déjà servi à une capture depuis leur formation.
 * @property moveHistory Historique textuel chronologique des coups joués.
 * @property pendingCaptures Nombre de captures que [currentPlayer] doit effectuer avant que le tour ne passe.
 * @property sticksPendingPlacement Nombre de pions BÂTONNETS en attente de pose suite à un blocage de pose.
 * @property stonesPendingPlacement Nombre de pions CAILLOUX en attente de pose suite à un blocage de pose.
 * @property ruleOptions Variantes de règles appliquées à cette partie.
 */
data class GameState(
    val board: Map<Position, Player?>,
    val currentPlayer: Player,
    val phase: Phase,
    val sticksRemaining: Int,
    val stonesRemaining: Int,
    val winner: Player?,
    val usedLines: Set<Line>,
    val moveHistory: List<String>,
    val pendingCaptures: Int = 0,
    val sticksPendingPlacement: Int = 0,
    val stonesPendingPlacement: Int = 0,
    val ruleOptions: WaliRuleOptions = WaliRuleOptions(),
    val capturedBySticks: Int = 0,
    val capturedByStones: Int = 0
) {
    /**
     * Nombre de pions d'un joueur actuellement présents sur le plateau.
     */
    fun piecesOnBoard(player: Player): Int = board.values.count { it == player }

    /**
     * Nombre de pions d'un joueur en attente de pose différée.
     */
    fun pendingPlacements(player: Player): Int = when (player) {
        Player.STICKS -> sticksPendingPlacement
        Player.STONES -> stonesPendingPlacement
    }

    /**
     * Indique si un joueur a au moins un pion en attente de pose différée.
     */
    fun hasPendingPlacements(player: Player): Boolean = pendingPlacements(player) > 0

    /**
     * Retourne le nombre de pions vivants restants pour [player].
     * En phase PLACEMENT, aucune capture n'étant possible, chaque joueur possède ses 12 pions.
     * En phases MOVEMENT et GAME_OVER, retourne [sticksRemaining] ou [stonesRemaining].
     */
    fun remainingPiecesOf(player: Player): Int {
        if (phase == Phase.PLACEMENT) {
            return Position.PIECES_PER_PLAYER
        }
        return when (player) {
            Player.STICKS -> sticksRemaining
            Player.STONES -> stonesRemaining
        }
    }

    /**
     * Retourne le nombre de captures réalisées par [player] depuis le début de la partie,
     * à partir des compteurs explicites [capturedBySticks] et [capturedByStones].
     */
    fun capturesMadeBy(player: Player): Int = when (player) {
        Player.STICKS -> capturedBySticks
        Player.STONES -> capturedByStones
    }

    /**
     * Indique si la victoire de [winnerRole] est une victoire double (+2 points),
     * c'est-à-dire si le perdant n'a capturé aucun pion adverse (capturesMadeBy(loser) == 0).
     */
    fun isDoubleWin(winnerRole: Player? = winner): Boolean {
        val resolvedWinner = winnerRole ?: return false
        return capturesMadeBy(resolvedWinner.opponent) == 0
    }

    /**
     * Retourne le nombre de points attribués au vainqueur [winnerRole] :
     * 2 points si le perdant a 0 capture, 1 point sinon.
     */
    fun pointsForWin(winnerRole: Player): Int =
        if (capturesMadeBy(winnerRole.opponent) == 0) 2 else 1
}

/**
 * Représente le déplacement d'un pion d'une case [from] vers une case [to] par [player].
 * Utilisé pour animer de manière fluide la transition avec Jetpack Compose.
 */
data class PieceMove(
    val from: Position,
    val to: Position,
    val player: Player,
    val id: Long = System.currentTimeMillis()
)
