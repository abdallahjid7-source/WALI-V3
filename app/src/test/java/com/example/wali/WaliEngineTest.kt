package com.example.wali

import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameState
import com.example.wali.model.Line
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.model.WaliRuleOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite complète de tests unitaires pour le moteur de règles pur du Wali Songhaï.
 *
 * Couvre l'ensemble des 8 catégories spécifiées :
 * 1. Placement (pose valide, case occupée refusée, ligne de trois refusée, fin de phase après 24 poses)
 * 2. Déplacement (orthogonal valide, diagonale refusée, saut/distance refusée, case occupée refusée, pion adverse refusé)
 * 3. Détection de lignes (horizontale, verticale, diagonale ignorée, ligne existante ignorée, nouvelle ligne détectée)
 * 4. Capture (nouvelle ligne autorise capture, pas de ligne = pas de capture, 1 seule capture par formation)
 * 5. Réutilisation de ligne (interdite sans casser/reformer, autorisée après avoir été cassée et reformée, autre ligne ailleurs autorisée même si l'ancienne reste intacte)
 * 6. Cheval double (déplacement créant 2 lignes distinctes donne droit à 2 captures)
 * 7. Blocage (joueur sans mouvement perd, joueur avec mouvement continue)
 * 8. Victoire (élimination = défaite, blocage = défaite, continuation normale)
 */
class WaliEngineTest {

    // =========================================================================
    // 1. TESTS DE PLACEMENT
    // =========================================================================

    @Test
    fun `placement valide pose le pion et alterne le joueur`() {
        val state = WaliEngine.initializeGame()
        assertEquals(Phase.PLACEMENT, state.phase)
        assertEquals(Player.STICKS, state.currentPlayer)
        assertEquals(12, state.sticksRemaining)
        assertEquals(12, state.stonesRemaining)

        val posA1 = Position(0, 0)
        val nextState = WaliEngine.placePiece(state, posA1)

        assertEquals(Player.STICKS, nextState.board[posA1])
        assertEquals(Player.STONES, nextState.currentPlayer)
        assertEquals(11, nextState.sticksRemaining)
        assertEquals(12, nextState.stonesRemaining)
        assertEquals(Phase.PLACEMENT, nextState.phase)
        assertNull(nextState.winner)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pose sur une case deja occupee est refusee`() {
        val state = WaliEngine.initializeGame()
        val posA1 = Position(0, 0)
        val stateAfterSticks = WaliEngine.placePiece(state, posA1)

        // STONES essaie de poser sur la même case A1
        WaliEngine.placePiece(stateAfterSticks, posA1)
    }

    @Test
    fun `pose creant une ligne de trois horizontale est refusee et absente des coups valides`() {
        // STICKS en (0, 0) et (0, 1). La case (0, 2) compléterait une ligne de 3 pour STICKS
        var state = WaliEngine.initializeGame()
        state = WaliEngine.placePiece(state, Position(0, 0)) // STICKS en A1
        state = WaliEngine.placePiece(state, Position(1, 0)) // STONES en A2
        state = WaliEngine.placePiece(state, Position(0, 1)) // STICKS en B1
        state = WaliEngine.placePiece(state, Position(1, 1)) // STONES en B2

        // Tour de STICKS : la case (0, 2) [C1] est interdite
        val forbiddenPos = Position(0, 2)
        val validPlacements = WaliEngine.getValidPlacements(state)
        assertFalse("La case C1 ne doit pas être un placement valide pour STICKS", validPlacements.contains(forbiddenPos))

        var exceptionThrown = false
        try {
            WaliEngine.placePiece(state, forbiddenPos)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("Une exception doit être levée lors de la pose créant une ligne de 3", exceptionThrown)
    }

    @Test
    fun `pose creant une ligne de trois verticale ou au milieu est refusee`() {
        // STICKS en (0, 3) et (2, 3). La case du milieu (1, 3) compléterait une ligne verticale
        var state = WaliEngine.initializeGame()
        state = WaliEngine.placePiece(state, Position(0, 3)) // STICKS
        state = WaliEngine.placePiece(state, Position(0, 0)) // STONES
        state = WaliEngine.placePiece(state, Position(2, 3)) // STICKS
        state = WaliEngine.placePiece(state, Position(0, 1)) // STONES

        val middlePos = Position(1, 3)
        val validPlacements = WaliEngine.getValidPlacements(state)
        assertFalse("La case du milieu ne doit pas être autorisée", validPlacements.contains(middlePos))
    }

    @Test
    fun `fin de la phase de pose apres 24 poses valides et transition vers MOVEMENT`() {
        var state = WaliEngine.initializeGame()

        // Grille de pose 5x6 sans aligner 3 pions du même joueur :
        // Alternance en damier pour 24 pions :
        // Ligne 0 : STICKS (0,0), STONES (0,1), STICKS (0,2), STONES (0,3), STICKS (0,4), STONES (0,5) -> 3 STICKS, 3 STONES
        // Ligne 1 : STONES (1,0), STICKS (1,1), STONES (1,2), STICKS (1,3), STONES (1,4), STICKS (1,5) -> 3 STICKS, 3 STONES
        // Ligne 2 : STICKS (2,0), STONES (2,1), STICKS (2,2), STONES (2,3), STICKS (2,4), STONES (2,5) -> 3 STICKS, 3 STONES
        // Ligne 3 : STONES (3,0), STICKS (3,1), STONES (3,2), STICKS (3,3), STONES (3,4), STICKS (3,5) -> 3 STICKS, 3 STONES
        // Total = 24 pions (12 STICKS et 12 STONES). Ligne 4 reste vide (6 cases vides).

        val sticksPositions = listOf(
            Position(0, 0), Position(0, 2), Position(0, 4),
            Position(1, 1), Position(1, 3), Position(1, 5),
            Position(2, 0), Position(2, 2), Position(2, 4),
            Position(3, 1), Position(3, 3), Position(3, 5)
        )
        val stonesPositions = listOf(
            Position(0, 1), Position(0, 3), Position(0, 5),
            Position(1, 0), Position(1, 2), Position(1, 4),
            Position(2, 1), Position(2, 3), Position(2, 5),
            Position(3, 0), Position(3, 2), Position(3, 4)
        )

        for (i in 0 until 12) {
            state = WaliEngine.placePiece(state, sticksPositions[i])
            state = WaliEngine.placePiece(state, stonesPositions[i])
        }

        assertEquals(Phase.MOVEMENT, state.phase)
        assertEquals(Player.STICKS, state.currentPlayer)
        assertEquals(12, state.piecesOnBoard(Player.STICKS))
        assertEquals(12, state.piecesOnBoard(Player.STONES))
        assertEquals(12, state.sticksRemaining)
        assertEquals(12, state.stonesRemaining)

        val emptyCellsCount = state.board.values.count { it == null }
        assertEquals(6, emptyCellsCount)
    }

    // =========================================================================
    // 2. TESTS DE DÉPLACEMENT
    // =========================================================================

    private fun createMovementState(
        sticks: List<Position>,
        stones: List<Position>,
        currentPlayer: Player = Player.STICKS,
        usedLines: Set<Line> = emptySet(),
        pendingCaptures: Int = 0
    ): GameState {
        val board = Position.allPositions.associateWith { pos ->
            when {
                sticks.contains(pos) -> Player.STICKS
                stones.contains(pos) -> Player.STONES
                else -> null
            }
        }
        return GameState(
            board = board,
            currentPlayer = currentPlayer,
            phase = Phase.MOVEMENT,
            sticksRemaining = sticks.size,
            stonesRemaining = stones.size,
            winner = null,
            usedLines = usedLines,
            moveHistory = emptyList(),
            pendingCaptures = pendingCaptures
        )
    }

    @Test
    fun `deplacement orthogonal d une seule case vers une case vide est valide`() {
        val state = createMovementState(
            sticks = listOf(Position(1, 1)),
            stones = listOf(Position(4, 4))
        )

        val validMoves = WaliEngine.getValidMoves(state, Position(1, 1))
        val expectedMoves = listOf(
            Position(0, 1), // haut
            Position(2, 1), // bas
            Position(1, 0), // gauche
            Position(1, 2)  // droite
        )

        assertEquals(4, validMoves.size)
        assertTrue(validMoves.containsAll(expectedMoves))

        // Déplacement effectif vers la droite
        val nextState = WaliEngine.movePiece(state, Position(1, 1), Position(1, 2))
        assertNull(nextState.board[Position(1, 1)])
        assertEquals(Player.STICKS, nextState.board[Position(1, 2)])
        assertEquals(Player.STONES, nextState.currentPlayer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deplacement en diagonale est refuse`() {
        val state = createMovementState(
            sticks = listOf(Position(1, 1)),
            stones = listOf(Position(4, 4))
        )
        // Diagonale (1,1) -> (2,2)
        WaliEngine.movePiece(state, Position(1, 1), Position(2, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deplacement de plusieurs cases est refuse`() {
        val state = createMovementState(
            sticks = listOf(Position(1, 1)),
            stones = listOf(Position(4, 4))
        )
        // Deux cases vers la droite (1,1) -> (1,3)
        WaliEngine.movePiece(state, Position(1, 1), Position(1, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deplacement vers une case occupee est refuse`() {
        val state = createMovementState(
            sticks = listOf(Position(1, 1)),
            stones = listOf(Position(1, 2)) // occupant adjacent
        )
        WaliEngine.movePiece(state, Position(1, 1), Position(1, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deplacement d un pion adverse est refuse`() {
        val state = createMovementState(
            sticks = listOf(Position(1, 1)),
            stones = listOf(Position(3, 3)),
            currentPlayer = Player.STICKS
        )
        // STICKS essaie de déplacer le pion de STONES
        WaliEngine.movePiece(state, Position(3, 3), Position(3, 4))
    }

    // =========================================================================
    // 3. TESTS DE DÉTECTION DE LIGNES
    // =========================================================================

    @Test
    fun `detection d une ligne horizontale et verticale sans diagonale`() {
        val horizontalSticks = listOf(Position(0, 0), Position(0, 1), Position(0, 2))
        val stateHoriz = createMovementState(sticks = horizontalSticks, stones = emptyList())
        val linesHoriz = WaliEngine.detectLines(stateHoriz.board, Player.STICKS)
        assertEquals(1, linesHoriz.size)
        assertEquals(Line(horizontalSticks.toSet()), linesHoriz[0])

        val verticalSticks = listOf(Position(1, 3), Position(2, 3), Position(3, 3))
        val stateVert = createMovementState(sticks = verticalSticks, stones = emptyList())
        val linesVert = WaliEngine.detectLines(stateVert.board, Player.STICKS)
        assertEquals(1, linesVert.size)
        assertEquals(Line(verticalSticks.toSet()), linesVert[0])

        // Les diagonales ne doivent JAMAIS être détectées
        val diagonalSticks = listOf(Position(0, 0), Position(1, 1), Position(2, 2))
        val stateDiag = createMovementState(sticks = diagonalSticks, stones = emptyList())
        val linesDiag = WaliEngine.detectLines(stateDiag.board, Player.STICKS)
        assertTrue("Les diagonales ne doivent jamais former de ligne", linesDiag.isEmpty())
    }

    @Test
    fun `alignement de 4 pions consecutifs ne forme aucune ligne et ne declenche aucune capture`() {
        // CORRECTION 1 : SEULE une ligne d'EXACTEMENT 3 pions donne droit à une capture.
        // Un alignement de 4 pions consécutifs ne doit former AUCUNE ligne.
        val fourHorizontalSticks = listOf(Position(0, 0), Position(0, 1), Position(0, 2), Position(0, 3))
        val stateHoriz = createMovementState(sticks = fourHorizontalSticks, stones = emptyList())
        val linesHoriz = WaliEngine.detectLines(stateHoriz.board, Player.STICKS)
        assertTrue("Un alignement de 4 pions horizontaux ne doit produire aucune ligne", linesHoriz.isEmpty())

        val fourVerticalSticks = listOf(Position(0, 2), Position(1, 2), Position(2, 2), Position(3, 2))
        val stateVert = createMovementState(sticks = fourVerticalSticks, stones = emptyList())
        val linesVert = WaliEngine.detectLines(stateVert.board, Player.STICKS)
        assertTrue("Un alignement de 4 pions verticaux ne doit produire aucune ligne", linesVert.isEmpty())
    }

    @Test
    fun `alignement de 5 pions consecutifs ne forme aucune ligne`() {
        val fiveSticks = listOf(Position(1, 0), Position(1, 1), Position(1, 2), Position(1, 3), Position(1, 4))
        val state = createMovementState(sticks = fiveSticks, stones = emptyList())
        val lines = WaliEngine.detectLines(state.board, Player.STICKS)
        assertTrue("Un alignement de 5 pions ne doit produire aucune ligne", lines.isEmpty())
    }

    @Test
    fun `deplacement creant 4 pions alignes ne declenche aucune capture`() {
        // CORRECTION 1 : Si un déplacement crée 4 pions alignés, aucune capture ne doit être accordée
        // STICKS a des pions en (0, 0), (0, 1), (0, 3) et (1, 2)
        // STICKS déplace (1, 2) vers (0, 2), créant l'alignement de 4 : (0,0), (0,1), (0,2), (0,3)
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(0, 3), Position(1, 2))
        val stones = listOf(Position(4, 4), Position(4, 5))
        val state = createMovementState(sticks = sticks, stones = stones, currentPlayer = Player.STICKS)

        val afterMove = WaliEngine.movePiece(state, Position(1, 2), Position(0, 2))
        assertFalse("Créer 4 pions alignés ne doit pas autoriser de capture", WaliEngine.canCapture(afterMove))
        assertEquals(0, afterMove.pendingCaptures)
        assertEquals(Player.STONES, afterMove.currentPlayer) // Le tour passe à l'adversaire car 0 capture
    }

    @Test
    fun `detectNewLines ignore une ligne preexistante et detecte uniquement la nouvelle`() {
        // Avant le coup : ligne préexistante en ligne 0 (0,0), (0,1), (0,2) et un pion en (2,0)
        val sticksBefore = listOf(Position(0, 0), Position(0, 1), Position(0, 2), Position(2, 0), Position(3, 1), Position(3, 2))
        val stateBefore = createMovementState(sticks = sticksBefore, stones = listOf(Position(4, 4)))

        // Déplacement du pion (2,0) vers (3,0), formant une NOUVELLE ligne horizontale (3,0), (3,1), (3,2)
        val stateAfter = WaliEngine.movePiece(stateBefore, Position(2, 0), Position(3, 0))

        val newLines = WaliEngine.detectNewLines(stateBefore.board, stateAfter.board, Player.STICKS)
        assertEquals(1, newLines.size)
        val expectedNewLine = Line(setOf(Position(3, 0), Position(3, 1), Position(3, 2)))
        assertEquals(expectedNewLine, newLines[0])
    }

    // =========================================================================
    // 4. TESTS DE CAPTURE
    // =========================================================================

    @Test
    fun `formation d une nouvelle ligne autorise une seule capture`() {
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(1, 2))
        val stones = listOf(Position(3, 3), Position(4, 4))
        val state = createMovementState(sticks = sticks, stones = stones, currentPlayer = Player.STICKS)

        // Déplacement (1,2) -> (0,2) complétant la ligne (0,0), (0,1), (0,2)
        val afterMove = WaliEngine.movePiece(state, Position(1, 2), Position(0, 2))

        assertTrue("canCapture doit être vrai après la formation d'une nouvelle ligne", WaliEngine.canCapture(afterMove))
        assertEquals(1, afterMove.pendingCaptures)
        assertEquals(Player.STICKS, afterMove.currentPlayer) // Le joueur conserve le tour pour capturer

        // Capture du pion en (3,3)
        val afterCapture = WaliEngine.capturePiece(afterMove, Position(3, 3))
        assertFalse("canCapture doit être faux une fois la capture effectuée", WaliEngine.canCapture(afterCapture))
        assertEquals(0, afterCapture.pendingCaptures)
        assertNull(afterCapture.board[Position(3, 3)])
        assertEquals(1, afterCapture.piecesOnBoard(Player.STONES))
        assertEquals(1, afterCapture.stonesRemaining)
        assertEquals(Player.STONES, afterCapture.currentPlayer) // Tour passe maintenant à l'adversaire
    }

    @Test
    fun `deplacement sans formation de ligne ne donne aucune capture`() {
        val sticks = listOf(Position(0, 0), Position(1, 2))
        val stones = listOf(Position(3, 3))
        val state = createMovementState(sticks = sticks, stones = stones, currentPlayer = Player.STICKS)

        val afterMove = WaliEngine.movePiece(state, Position(0, 0), Position(0, 1))
        assertFalse(WaliEngine.canCapture(afterMove))
        assertEquals(0, afterMove.pendingCaptures)
        assertEquals(Player.STONES, afterMove.currentPlayer)
    }

    // =========================================================================
    // 5. TESTS DE RÉUTILISATION DE LIGNE
    // =========================================================================

    @Test
    fun `une ligne deja utilisee ne permet pas de capturer a nouveau sans etre cassee et reformee`() {
        val line = Line(setOf(Position(0, 0), Position(0, 1), Position(0, 2)))
        // Le joueur a déjà formé cette ligne et elle a été utilisée
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(0, 2), Position(2, 0))
        val stones = listOf(Position(4, 0), Position(4, 1), Position(4, 2))

        val state = createMovementState(
            sticks = sticks,
            stones = stones,
            currentPlayer = Player.STICKS,
            usedLines = setOf(line)
        )

        // Déplacement d'un autre pion ailleurs sans former de nouvelle ligne
        val afterMove = WaliEngine.movePiece(state, Position(2, 0), Position(2, 1))
        assertFalse("L'ancienne ligne ne doit pas réautoriser de capture", WaliEngine.canCapture(afterMove))
    }

    @Test
    fun `une ligne redevient utilisable apres avoir ete cassee puis reformee (cheval simple)`() {
        val initialLine = Line(setOf(Position(0, 0), Position(0, 1), Position(0, 2)))
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(0, 2))
        val stones = listOf(Position(4, 0), Position(4, 1), Position(4, 2), Position(4, 3))

        // État où la ligne (0,0)-(0,1)-(0,2) a déjà été utilisée
        var state = createMovementState(
            sticks = sticks,
            stones = stones,
            currentPlayer = Player.STICKS,
            usedLines = setOf(initialLine)
        )

        // Tour 1 (STICKS) : on casse la ligne en déplaçant (0,2) -> (1,2)
        state = WaliEngine.movePiece(state, Position(0, 2), Position(1, 2))
        assertFalse(WaliEngine.canCapture(state))
        assertFalse("La ligne cassee doit avoir ete retiree des usedLines", state.usedLines.contains(initialLine))

        // Tour 1 (STONES) : déplacement neutre
        state = WaliEngine.movePiece(state, Position(4, 3), Position(3, 3))

        // Tour 2 (STICKS) : on reforme la ligne en replaçant (1,2) -> (0,2)
        state = WaliEngine.movePiece(state, Position(1, 2), Position(0, 2))
        assertTrue("La ligne reformee autorise une nouvelle capture", WaliEngine.canCapture(state))
        assertEquals(1, state.pendingCaptures)
    }

    @Test
    fun `une autre ligne formee ailleurs capture meme si l ancienne ligne utilisee est toujours intacte`() {
        val line1 = Line(setOf(Position(0, 0), Position(0, 1), Position(0, 2)))
        // Ligne 1 intacte et déjà utilisée. STICKS a aussi des pions en (2,0), (2,1) et (3,2).
        val sticks = listOf(
            Position(0, 0), Position(0, 1), Position(0, 2),
            Position(2, 0), Position(2, 1), Position(3, 2)
        )
        val stones = listOf(Position(4, 0), Position(4, 1), Position(4, 2))

        val state = createMovementState(
            sticks = sticks,
            stones = stones,
            currentPlayer = Player.STICKS,
            usedLines = setOf(line1)
        )

        // STICKS déplace (3,2) -> (2,2), complétant la LIGNE 2 : (2,0), (2,1), (2,2)
        val afterMove = WaliEngine.movePiece(state, Position(3, 2), Position(2, 2))

        assertTrue("La nouvelle ligne 2 doit autoriser une capture meme si la ligne 1 est intacte", WaliEngine.canCapture(afterMove))
        assertEquals(1, afterMove.pendingCaptures)

        val expectedLine2 = Line(setOf(Position(2, 0), Position(2, 1), Position(2, 2)))
        assertTrue("usedLines doit maintenant contenir line1 et line2", afterMove.usedLines.contains(line1))
        assertTrue("usedLines doit contenir line2", afterMove.usedLines.contains(expectedLine2))
    }

    // =========================================================================
    // 6. TEST CHEVAL DOUBLE (DOUBLE HORSE)
    // =========================================================================

    // CORRECTION 2 (EXCEPTION EXPLICITE) : Le test initial encodait le comportement incorrect de deux captures
    // pour un cheval double. Selon la règle stricte du Wali Songhaï, peu importe le nombre de lignes formées
    // par un coup (même en cas de cheval double créant 2 lignes simultanées), le joueur n'a droit qu'à UNE SEULE
    // capture au total pour ce coup. Le test a été corrigé pour vérifier que pendingCaptures vaut 1 et qu'après
    // cette unique capture, le tour passe immédiatement au joueur adverse sans proposer de seconde capture.
    @Test
    fun `cheval double forme deux lignes distinctes en un seul coup mais ne donne droit qu a une seule capture`() {
        // Configuration en angle/croix :
        // Le pion en (0, 0) se déplace vers (0, 1).
        // Ligne Horizontale formée : (0, 1), (0, 2), (0, 3)
        // Ligne Verticale formée : (0, 1), (1, 1), (2, 1)
        // Deux lignes formées simultanément en 1 seul coup.

        val sticks = listOf(
            Position(0, 0), // pion mobile
            Position(0, 2), Position(0, 3), // complète horizontale
            Position(1, 1), Position(2, 1)  // complète verticale
        )
        val stones = listOf(Position(4, 0), Position(4, 1), Position(4, 2), Position(4, 3))

        val state = createMovementState(sticks = sticks, stones = stones, currentPlayer = Player.STICKS)

        // Déplacement de (0, 0) vers (0, 1)
        val afterMove = WaliEngine.movePiece(state, Position(0, 0), Position(0, 1))

        val horizontalLine = Line(setOf(Position(0, 1), Position(0, 2), Position(0, 3)))
        val verticalLine = Line(setOf(Position(0, 1), Position(1, 1), Position(2, 1)))

        val newLines = WaliEngine.detectNewLines(state.board, afterMove.board, Player.STICKS, movedTo = Position(0, 1))
        assertEquals(2, newLines.size)
        assertTrue(newLines.contains(horizontalLine))
        assertTrue(newLines.contains(verticalLine))

        // Règle Wali : UNE SEULE capture autorisée pour ce coup
        assertEquals("Une seule capture doit être en attente même en cas de cheval double", 1, afterMove.pendingCaptures)
        assertTrue(WaliEngine.canCapture(afterMove))

        // Unique capture effectuée
        val afterCapture = WaliEngine.capturePiece(afterMove, Position(4, 0))
        assertEquals("Plus de capture en attente après l'unique capture", 0, afterCapture.pendingCaptures)
        assertFalse(WaliEngine.canCapture(afterCapture))
        assertEquals(Player.STONES, afterCapture.currentPlayer) // Tour passe directement à l'adversaire
    }

    // =========================================================================
    // 7. TESTS DE BLOCAGE
    // =========================================================================

    @Test
    fun `joueur sans aucun mouvement legal perd immediatement par blocage`() {
        // STONES est entièrement bloqué dans le coin (0,0) entouré par des STICKS en (0,1) et (1,0)
        // et aucune case vide n'est adjacente à STONES.
        val sticks = listOf(Position(0, 1), Position(1, 0), Position(2, 2))
        val stones = listOf(Position(0, 0)) // En coin, ses deux seuls voisins (0,1) et (1,0) sont occupés par STICKS

        val state = createMovementState(sticks = sticks, stones = stones, currentPlayer = Player.STICKS)

        // STICKS déplace (2,2) vers (2,3) (coup neutre)
        val afterMove = WaliEngine.movePiece(state, Position(2, 2), Position(2, 3))

        assertEquals("Phase doit être GAME_OVER suite au blocage", Phase.GAME_OVER, afterMove.phase)
        assertEquals("STICKS gagne par blocage de STONES", Player.STICKS, afterMove.winner)
    }

    @Test
    fun `joueur avec au moins un mouvement legal continue la partie`() {
        val sticks = listOf(Position(0, 1), Position(2, 2))
        val stones = listOf(Position(0, 0)) // Voisin (0,1) occupé, mais voisin (1,0) vide !

        val state = createMovementState(sticks = sticks, stones = stones, currentPlayer = Player.STICKS)
        val afterMove = WaliEngine.movePiece(state, Position(2, 2), Position(2, 3))

        assertEquals(Phase.MOVEMENT, afterMove.phase)
        assertNull(afterMove.winner)
        assertEquals(Player.STONES, afterMove.currentPlayer)
        assertTrue(WaliEngine.hasLegalMove(afterMove, Player.STONES))
    }

    // =========================================================================
    // 8. TESTS DE CONDITIONS DE VICTOIRE
    // =========================================================================

    @Test
    fun `elimination du dernier pion adverse termine la partie par victoire`() {
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(1, 2))
        val stones = listOf(Position(4, 5)) // Seul et unique pion de STONES
        val state = createMovementState(sticks = sticks, stones = stones, currentPlayer = Player.STICKS)

        // Déplacement formant la ligne (0,0), (0,1), (0,2)
        val afterMove = WaliEngine.movePiece(state, Position(1, 2), Position(0, 2))
        // Capture de l'unique pion adverse
        val afterCapture = WaliEngine.capturePiece(afterMove, Position(4, 5))

        assertEquals(Phase.GAME_OVER, afterCapture.phase)
        assertEquals(Player.STICKS, afterCapture.winner)
        assertEquals(0, afterCapture.stonesRemaining)
        assertEquals(Player.STICKS, WaliEngine.checkWinCondition(afterCapture))
    }

    @Test
    fun `checkWinCondition retourne null tant que la partie est active`() {
        val state = WaliEngine.initializeGame()
        assertNull(WaliEngine.checkWinCondition(state))
    }

    // =========================================================================
    // 9. TESTS DE CORRECTION 3 : BLOCAGE DE POSE ET POSE DIFFÉRÉE EN PHASE 2
    // =========================================================================

    @Test
    fun `detection du blocage total de pose et passage anticipe en phase 2 avec pion en attente`() {
        // Construction d'un plateau où toutes les cases vides restantes formeraient une ligne de 3 pour STICKS
        // Imaginons que STICKS a posé 11 pions et STONES 11 pions (ou STONES 12 et STICKS 11).
        // On construit un état PLACEMENT où STICKS a 1 pion restant à poser, mais getValidPlacements est vide.
        // Pour bloquer toutes les cases vides pour STICKS :
        // Si les cases vides restantes sont (0, 2) et (1, 2), et STICKS a des pions en (0,0),(0,1) et (1,0),(1,1).
        val board = Position.allPositions.associateWith { pos ->
            when {
                pos == Position(0, 2) || pos == Position(1, 2) -> null // Les 2 seules cases vides
                pos in listOf(Position(0, 0), Position(0, 1), Position(1, 0), Position(1, 1)) -> Player.STICKS
                else -> Player.STONES // Toutes les autres cases occupées par STONES
            }
        }
        // Vérification : poser en (0,2) ferait la ligne (0,0)-(0,1)-(0,2) pour STICKS
        // poser en (1,2) ferait la ligne (1,0)-(1,1)-(1,2) pour STICKS
        // Donc getValidPlacements pour STICKS est strictement vide !
        val placementState = GameState(
            board = board,
            currentPlayer = Player.STICKS,
            phase = Phase.PLACEMENT,
            sticksRemaining = 1,
            stonesRemaining = 0,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList()
        )

        val validPlacements = WaliEngine.getValidPlacements(placementState)
        assertTrue("Aucune case de pose ne doit être légale pour STICKS", validPlacements.isEmpty())

        // Appel de la gestion du blocage de pose
        val movementState = WaliEngine.checkAndHandlePlacementBlockage(placementState)
        assertEquals("La partie doit basculer en phase MOVEMENT", Phase.MOVEMENT, movementState.phase)
        assertEquals("STICKS doit avoir 1 pion en attente de pose", 1, movementState.sticksPendingPlacement)
        assertEquals("STICKS a 1 pion en attente", 1, movementState.pendingPlacements(Player.STICKS))
        assertEquals(0, movementState.stonesPendingPlacement)
    }

    @Test
    fun `refus de tout deplacement normal pour un joueur ayant un pion en attente tant qu une case de pose legale existe`() {
        // STICKS a un pion en attente de pose (sticksPendingPlacement = 1) en phase MOVEMENT.
        // Une case vide (2, 2) est légale pour STICKS (ne forme pas de ligne de 3).
        // STICKS a aussi un pion en (0, 0) adjacent à une case vide (0, 1).
        val sticks = listOf(Position(0, 0), Position(3, 0), Position(4, 0))
        val stones = listOf(Position(4, 4), Position(4, 5), Position(3, 5))
        val state = GameState(
            board = Position.allPositions.associateWith { pos ->
                when {
                    sticks.contains(pos) -> Player.STICKS
                    stones.contains(pos) -> Player.STONES
                    else -> null
                }
            },
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 4, // 3 sur plateau + 1 en attente
            stonesRemaining = 3,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            sticksPendingPlacement = 1,
            stonesPendingPlacement = 0
        )

        assertTrue("Une pose est obligatoire ce tour-ci pour STICKS", WaliEngine.isPlacementRequired(state))
        assertTrue("Des cases de pose légales existent", WaliEngine.getValidPlacements(state).isNotEmpty())

        // Refus de tout déplacement : getValidMoves doit renvoyer vide
        val moves = WaliEngine.getValidMoves(state, Position(0, 0))
        assertTrue("getValidMoves doit être vide tant qu'un pion en attente doit être posé", moves.isEmpty())

        // Tentative de déplacement via movePiece doit lever une exception
        var exceptionThrown = false
        try {
            WaliEngine.movePiece(state, Position(0, 0), Position(0, 1))
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("movePiece doit refuser le déplacement tant que la pose obligatoire n'est pas faite", exceptionThrown)
    }

    @Test
    fun `pose effective du pion en attente des qu une case redevient legale puis reprise normale du jeu`() {
        // En phase MOVEMENT, STICKS a 1 pion en attente.
        val sticks = listOf(Position(0, 0), Position(1, 0))
        val stones = listOf(Position(3, 3), Position(4, 4))
        val state = GameState(
            board = Position.allPositions.associateWith { pos ->
                when {
                    sticks.contains(pos) -> Player.STICKS
                    stones.contains(pos) -> Player.STONES
                    else -> null
                }
            },
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 3, // 2 sur plateau + 1 en attente
            stonesRemaining = 2,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            sticksPendingPlacement = 1,
            stonesPendingPlacement = 0
        )

        val targetPos = Position(2, 2)
        assertTrue(targetPos in WaliEngine.getValidPlacements(state))

        // STICKS effectue sa pose obligatoire
        val afterPlace = WaliEngine.placePiece(state, targetPos)

        assertEquals("Le pion doit être posé sur le plateau", Player.STICKS, afterPlace.board[targetPos])
        assertEquals("Plus de pion en attente pour STICKS", 0, afterPlace.sticksPendingPlacement)
        assertEquals("Le tour doit passer à l'adversaire (STONES)", Player.STONES, afterPlace.currentPlayer)
        assertFalse("Plus de pose requise pour STICKS", afterPlace.hasPendingPlacements(Player.STICKS))
    }

    @Test
    fun `bon fonctionnement symetrique quand les DEUX joueurs ont un pion en attente de pose`() {
        // Les deux joueurs ont 1 pion en attente de pose en phase MOVEMENT
        val sticks = listOf(Position(0, 0), Position(0, 1))
        val stones = listOf(Position(4, 0), Position(4, 1))
        var state = GameState(
            board = Position.allPositions.associateWith { pos ->
                when {
                    sticks.contains(pos) -> Player.STICKS
                    stones.contains(pos) -> Player.STONES
                    else -> null
                }
            },
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 3,
            stonesRemaining = 3,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList(),
            sticksPendingPlacement = 1,
            stonesPendingPlacement = 1
        )

        // Tour 1 : STICKS doit poser son pion
        assertTrue(WaliEngine.isPlacementRequired(state))
        val posSticks = Position(2, 0)
        state = WaliEngine.placePiece(state, posSticks)

        assertEquals(0, state.sticksPendingPlacement)
        assertEquals(1, state.stonesPendingPlacement)
        assertEquals(Player.STONES, state.currentPlayer)

        // Tour 2 : STONES doit poser son pion
        assertTrue(WaliEngine.isPlacementRequired(state))
        val posStones = Position(2, 4)
        state = WaliEngine.placePiece(state, posStones)

        assertEquals(0, state.sticksPendingPlacement)
        assertEquals(0, state.stonesPendingPlacement)
        assertEquals(Player.STICKS, state.currentPlayer)

        // Tour 3 : STICKS n'a plus de pion en attente, il peut se déplacer normalement
        assertFalse(WaliEngine.isPlacementRequired(state))
        val moves = WaliEngine.getValidMoves(state, Position(0, 0))
        assertTrue("STICKS peut maintenant se déplacer normalement", moves.isNotEmpty())
    }

    // =========================================================================
    // 9. VARIANTES DE RÈGLES OPTIONNELLES (PARTIE B)
    // =========================================================================

    @Test
    fun `variante alignement obligatoire filtre uniquement les coups formant une nouvelle ligne capturante`() {
        // STICKS a deux pions en (0, 0) et (0, 1), et un pion en (1, 2) qui peut monter en (0, 2) pour former A1-B1-C1.
        // STICKS a aussi un pion isolé en (4, 0) qui ne peut former aucune ligne.
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(1, 2), Position(4, 0))
        val stones = listOf(Position(4, 4), Position(4, 5), Position(3, 5))
        val baseState = createMovementState(sticks = sticks, stones = stones)
        val mandatoryState = baseState.copy(ruleOptions = WaliRuleOptions(mandatoryAlignment = true))

        val obligatory = WaliEngine.getObligatoryMoves(mandatoryState)
        assertEquals(mapOf(Position(1, 2) to listOf(Position(0, 2))), obligatory)

        // Avec mandatoryAlignment = true :
        // - Le pion isolé en (4, 0) n'a aucun coup valide
        assertTrue(WaliEngine.getValidMoves(mandatoryState, Position(4, 0)).isEmpty())
        // - Le pion en (1, 2) ne peut aller qu'en (0, 2), et pas en (2, 2), (1, 1) ou (1, 3)
        assertEquals(listOf(Position(0, 2)), WaliEngine.getValidMoves(mandatoryState, Position(1, 2)))

        // Avec mandatoryAlignment = false (par défaut), (4, 0) garde ses déplacements normaux
        assertTrue(WaliEngine.getValidMoves(baseState, Position(4, 0)).isNotEmpty())
    }

    @Test
    fun `variante alignement obligatoire ignore les lignes deja utilisees et non cassees`() {
        // STICKS a une ligne déjà utilisée en (0, 0)-(0, 1)-(0, 2) dans usedLines,
        // et un pion libre en (3, 3) qui ne forme aucune nouvelle ligne.
        val usedLine = Line(setOf(Position(0, 0), Position(0, 1), Position(0, 2)))
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(0, 2), Position(3, 3))
        val stones = listOf(Position(4, 4), Position(4, 5), Position(3, 5))
        val state = createMovementState(
            sticks = sticks,
            stones = stones,
            usedLines = setOf(usedLine)
        ).copy(ruleOptions = WaliRuleOptions(mandatoryAlignment = true))

        // Aucun coup ne forme une NOUVELLE ligne donnant droit à capture
        assertTrue(WaliEngine.getObligatoryMoves(state).isEmpty())
        // Le pion en (3, 3) conserve donc ses 4 mouvements normaux
        assertEquals(4, WaliEngine.getValidMoves(state, Position(3, 3)).size)

        // Si STICKS casse la ligne en déplaçant (0, 2) -> (1, 2), puis qu'au tour suivant de STICKS
        // (1, 2) peut remonter en (0, 2), la ligne cassée a été retirée de usedLines et redevient obligatoire !
        val brokenState = WaliEngine.movePiece(state, Position(0, 2), Position(1, 2))
        assertFalse(usedLine in brokenState.usedLines)
        val sticksNextTurn = brokenState.copy(currentPlayer = Player.STICKS)
        val obligatoryAfterBreak = WaliEngine.getObligatoryMoves(sticksNextTurn)
        assertEquals(mapOf(Position(1, 2) to listOf(Position(0, 2))), obligatoryAfterBreak)
    }

    @Test
    fun `variante defaite a deux pions fait perdre immediatement en phase MOVEMENT sans pion en attente`() {
        // STICKS a une ligne prête à être formée : (0, 0), (0, 1), et (1, 2) -> (0, 2).
        // STONES n'a plus que 3 pions sur le plateau et 0 en attente.
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(1, 2), Position(3, 0))
        val stones = listOf(Position(4, 3), Position(4, 4), Position(4, 5))
        val state = createMovementState(sticks = sticks, stones = stones).copy(
            ruleOptions = WaliRuleOptions(defeatAtTwoPieces = true)
        )

        val afterMove = WaliEngine.movePiece(state, Position(1, 2), Position(0, 2))
        assertTrue(WaliEngine.canCapture(afterMove))

        // STICKS capture un pion de STONES -> STONES tombe à 2 pions sur le plateau
        val afterCapture = WaliEngine.capturePiece(afterMove, Position(4, 3))
        assertEquals(2, afterCapture.piecesOnBoard(Player.STONES))
        assertEquals(Phase.GAME_OVER, afterCapture.phase)
        assertEquals(Player.STICKS, afterCapture.winner)
        assertEquals(Player.STICKS, WaliEngine.checkWinCondition(afterCapture))
    }

    @Test
    fun `variante defaite a deux pions ne s applique jamais en PLACEMENT ni si un pion est en attente`() {
        // 1. En phase PLACEMENT avec seulement 2 pions posés sur le plateau : aucune défaite
        var placementState = WaliEngine.initializeGame(
            ruleOptions = WaliRuleOptions(defeatAtTwoPieces = true)
        )
        placementState = WaliEngine.placePiece(placementState, Position(0, 0)) // STICKS (1)
        placementState = WaliEngine.placePiece(placementState, Position(1, 0)) // STONES (1)
        placementState = WaliEngine.placePiece(placementState, Position(0, 1)) // STICKS (2)
        placementState = WaliEngine.placePiece(placementState, Position(1, 1)) // STONES (2)

        assertEquals(Phase.PLACEMENT, placementState.phase)
        assertEquals(2, placementState.piecesOnBoard(Player.STICKS))
        assertEquals(2, placementState.piecesOnBoard(Player.STONES))
        assertNull(placementState.winner)
        assertNull(WaliEngine.checkWinCondition(placementState))

        // 2. En phase MOVEMENT mais avec 1 pion en attente de pose pour STONES : pas de défaite à 2 pions sur le plateau
        val sticks = listOf(Position(0, 0), Position(0, 1), Position(1, 2), Position(3, 0))
        val stones = listOf(Position(4, 3), Position(4, 4), Position(4, 5))
        val movementWithPending = createMovementState(sticks = sticks, stones = stones).copy(
            stonesRemaining = 4,
            stonesPendingPlacement = 1,
            ruleOptions = WaliRuleOptions(defeatAtTwoPieces = true)
        )
        val afterMove = WaliEngine.movePiece(movementWithPending, Position(1, 2), Position(0, 2))
        val afterCapture = WaliEngine.capturePiece(afterMove, Position(4, 3))
        assertEquals(2, afterCapture.piecesOnBoard(Player.STONES))
        assertTrue(afterCapture.hasPendingPlacements(Player.STONES))
        assertEquals(Phase.MOVEMENT, afterCapture.phase)
        assertNull(afterCapture.winner)
    }

    @Test
    fun engine_validateFalseInSearch_neverProducesIllegalState() {
        // Garde-fou 1 : Prouve formellement que pour tout coup généré par les générateurs de coups légaux
        // (getAllLegalMoves, getValidMoves, getValidPlacements, cibles de capture),
        // movePiece / capturePiece avec validate = false produit un GameState strictement identique
        // à validate = true, sans jamais échouer ni violer la moindre règle.
        val rng = kotlin.random.Random(1337)
        val ruleVariants = listOf(
            WaliRuleOptions(),
            WaliRuleOptions(mandatoryAlignment = true),
            WaliRuleOptions(defeatAtTwoPieces = true),
            WaliRuleOptions(mandatoryAlignment = true, defeatAtTwoPieces = true)
        )

        var totalPlacementsVerified = 0
        var totalMovesVerified = 0
        var totalCapturesVerified = 0

        for (rules in ruleVariants) {
            // Simulation de 30 parties aléatoires complètes pour chaque variante de règle
            for (gameIdx in 1..30) {
                var state = WaliEngine.initializeGame(ruleOptions = rules)
                var plies = 0

                while (state.winner == null && plies < 80) {
                    plies++
                    when (state.phase) {
                        Phase.PLACEMENT -> {
                            val placements = WaliEngine.getValidPlacements(state)
                            if (placements.isEmpty()) break
                            for (pos in placements) {
                                val withVal = WaliEngine.placePiece(state, pos, recordHistory = false, validate = true)
                                val withoutVal = WaliEngine.placePiece(state, pos, recordHistory = false, validate = false)
                                assertEquals(withVal, withoutVal)
                                totalPlacementsVerified++
                            }
                            val chosen = placements.random(rng)
                            state = WaliEngine.placePiece(state, chosen, recordHistory = false, validate = true)
                        }
                        Phase.MOVEMENT -> {
                            if (WaliEngine.isPlacementRequired(state)) {
                                val placements = WaliEngine.getValidPlacements(state)
                                if (placements.isEmpty()) break
                                for (pos in placements) {
                                    val withVal = WaliEngine.placePiece(state, pos, recordHistory = false, validate = true)
                                    val withoutVal = WaliEngine.placePiece(state, pos, recordHistory = false, validate = false)
                                    assertEquals(withVal, withoutVal)
                                    totalPlacementsVerified++
                                }
                                val chosen = placements.random(rng)
                                state = WaliEngine.placePiece(state, chosen, recordHistory = false, validate = true)
                                continue
                            }

                            if (WaliEngine.canCapture(state)) {
                                val opp = state.currentPlayer.opponent
                                val capturables = state.board.filter { it.value == opp }.keys
                                for (target in capturables) {
                                    val withVal = WaliEngine.capturePiece(state, target, recordHistory = false, validate = true)
                                    val withoutVal = WaliEngine.capturePiece(state, target, recordHistory = false, validate = false)
                                    assertEquals(withVal.board, withoutVal.board)
                                    assertEquals(withVal.currentPlayer, withoutVal.currentPlayer)
                                    assertEquals(withVal.winner, withoutVal.winner)
                                    assertEquals(withVal.usedLines, withoutVal.usedLines)
                                    assertEquals(withVal.pendingCaptures, withoutVal.pendingCaptures)
                                    assertEquals(withVal, withoutVal)
                                    totalCapturesVerified++
                                }
                                val chosen = capturables.random(rng)
                                state = WaliEngine.capturePiece(state, chosen, recordHistory = false, validate = true)
                                continue
                            }

                            val legalMoves = com.example.wali.ai.WaliAi.getAllLegalMoves(state)
                            if (legalMoves.isEmpty()) break

                            // Test de TOUS les coups légaux possibles depuis cette position
                            for ((from, to) in legalMoves) {
                                val withVal = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = true)
                                val withoutVal = WaliEngine.movePiece(state, from, to, recordHistory = false, validate = false)
                                assertEquals(withVal.board, withoutVal.board)
                                assertEquals(withVal.currentPlayer, withoutVal.currentPlayer)
                                assertEquals(withVal.winner, withoutVal.winner)
                                assertEquals(withVal.usedLines, withoutVal.usedLines)
                                assertEquals(withVal.pendingCaptures, withoutVal.pendingCaptures)
                                assertEquals(withVal, withoutVal)
                                totalMovesVerified++

                                // Si ce coup a produit une capture, tester également les captures résultantes
                                if (WaliEngine.canCapture(withVal)) {
                                    val opp = withVal.currentPlayer.opponent
                                    val caps = withVal.board.filter { it.value == opp }.keys
                                    for (capTarget in caps) {
                                        val capWith = WaliEngine.capturePiece(withVal, capTarget, recordHistory = false, validate = true)
                                        val capWithout = WaliEngine.capturePiece(withoutVal, capTarget, recordHistory = false, validate = false)
                                        assertEquals(capWith, capWithout)
                                        totalCapturesVerified++
                                    }
                                }
                            }

                            val chosenMove = legalMoves.random(rng)
                            state = WaliEngine.movePiece(state, chosenMove.first, chosenMove.second, recordHistory = false, validate = true)
                        }
                        Phase.GAME_OVER -> break
                    }
                }
            }
        }

        assertTrue("Au moins 2000 transitions de déplacement doivent avoir été validées (validé: $totalMovesVerified)", totalMovesVerified >= 2000)
        assertTrue("Au moins 500 transitions de capture doivent avoir été validées (validé: $totalCapturesVerified)", totalCapturesVerified >= 500)
    }
}
