package com.example.wali

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.wali.ai.AiDifficulty
import com.example.wali.model.GameMode
import com.example.wali.model.Player
import com.example.wali.ui.components.ModeSelectionDialog
import com.example.wali.ui.rules.WaliRulesScreen
import com.example.wali.ui.settings.WaliSettingsScreen
import com.example.wali.ui.theme.WaliThemePalette
import com.example.wali.engine.WaliEngine
import com.example.wali.model.GameState
import com.example.wali.model.Line
import com.example.wali.model.Phase
import com.example.wali.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WaliRulesAndNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `rules screen renders title and all main sections`() {
        var backClicked = false

        composeTestRule.setContent {
            WaliRulesScreen(
                onNavigateBack = { backClicked = true }
            )
        }

        // Vérification de l'en-tête principal
        composeTestRule.onNodeWithText("Règles du Wali").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jeu traditionnel Songhaï de Tombouctou").assertIsDisplayed()

        // Vérification de la présence des 4 onglets
        composeTestRule.onNodeWithTag("tab_rules_basics").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_rules_captures").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_rules_scores").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_rules_modes").assertIsDisplayed()

        // Onglet 1 : Règles de base
        composeTestRule.onNodeWithText("Objectif du jeu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Le Plateau et ses 30 Cases").assertIsDisplayed()

        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Les Pions et le Premier Coup"))
        composeTestRule.onNodeWithText("Les Pions et le Premier Coup").assertIsDisplayed()

        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Phase 1 : La Pose des Pions"))
        composeTestRule.onNodeWithText("Phase 1 : La Pose des Pions").assertIsDisplayed()

        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Phase 2 : Le Déplacement"))
        composeTestRule.onNodeWithText("Phase 2 : Le Déplacement").assertIsDisplayed()

        // Navigation vers Onglet 2 : Captures
        composeTestRule.onNodeWithTag("tab_rules_captures").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Alignement Valide (Le \"Cheval\")").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Alignements de 4 ou 5 Pions : NULS !"))
        composeTestRule.onNodeWithText("Alignements de 4 ou 5 Pions : NULS !").assertIsDisplayed()

        // Navigation vers Onglet 3 : Scores & Variantes
        composeTestRule.onNodeWithTag("tab_rules_scores").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Décompte des Points & Victoire Écrasante").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Proposition de Match Nul"))
        composeTestRule.onNodeWithText("Proposition de Match Nul").assertIsDisplayed()

        // Navigation vers Onglet 4 : Modes & En ligne
        composeTestRule.onNodeWithTag("tab_rules_modes").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Les Modes de Jeu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Mode Spectateur et Chat de Salon"))
        composeTestRule.onNodeWithText("Mode Spectateur et Chat de Salon").assertIsDisplayed()

        // Bouton de retour en haut
        composeTestRule.onNodeWithTag("btn_rules_back").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Le bouton retour en haut doit déclencher la navigation arrière", backClicked)
    }

    @Test
    fun `mode selection dialog shows rules button and triggers callback`() {
        var rulesOpened = false

        composeTestRule.setContent {
            ModeSelectionDialog(
                currentMode = GameMode.TWO_PLAYERS,
                currentHumanPlayer = Player.STICKS,
                currentDifficulty = AiDifficulty.MEDIUM,
                onStartGame = { _, _, _ -> },
                onDismiss = {},
                onOpenRules = { rulesOpened = true }
            )
        }

        composeTestRule.onNodeWithTag("btn_mode_dialog_rules").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_mode_dialog_rules").performClick()
        assertTrue("Le clic sur les règles dans le dialogue doit ouvrir les règles", rulesOpened)
    }

    @Test
    fun `settings screen renders all audio and theme controls and triggers callbacks`() {
        var backClicked = false
        var muteToggled = false
        var chosenTheme: WaliThemePalette? = null

        composeTestRule.setContent {
            WaliSettingsScreen(
                sfxVolume = 0.8f,
                musicVolume = 0.5f,
                isMuted = false,
                selectedPalette = WaliThemePalette.SAHEL_JOUR,
                onSfxVolumeChanged = {},
                onMusicVolumeChanged = {},
                onMutedChanged = { muteToggled = it },
                onThemeSelected = { chosenTheme = it },
                onNavigateBack = { backClicked = true }
            )
        }

        // Vérification des titres et sections
        composeTestRule.onNodeWithText("Réglages").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sons, Minuteur et Thème visuel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ambiance Sonore").assertIsDisplayed()

        // Vérification des sliders et switch mute
        composeTestRule.onNodeWithTag("slider_sfx_volume").assertIsDisplayed()
        composeTestRule.onNodeWithTag("slider_music_volume").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_mute_all").assertIsDisplayed()

        // Interaction switch mute
        composeTestRule.onNodeWithTag("switch_mute_all").performClick()
        assertTrue("Le clic sur le switch mute doit appeler onMutedChanged", muteToggled)

        // Défilement jusqu'au Thème Visuel
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasText("Thème Visuel"))
        composeTestRule.onNodeWithText("Thème Visuel").assertIsDisplayed()

        // Sélection du thème Nuit du Désert
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("theme_option_desert_night"))
        composeTestRule.onNodeWithTag("theme_option_desert_night").performClick()
        assertEquals(WaliThemePalette.DESERT_NIGHT, chosenTheme)

        // Clic sur le bouton de retour
        composeTestRule.onNodeWithTag("btn_settings_back").performClick()
        assertTrue("Le bouton retour doit déclencher onNavigateBack", backClicked)
    }

    @Test
    fun `board view displays 1 to 30 cell numbers, includes number in contentDescription, and respects showCellNumbers toggle`() {
        val posF2 = com.example.wali.model.Position.fromAlgebraic("F2") // case n° 12
        val board = com.example.wali.model.Position.allPositions.associateWith { pos ->
            if (pos == posF2) Player.STICKS else null
        }
        val showNumbersState = androidx.compose.runtime.mutableStateOf(true)
        var clickedPosition: com.example.wali.model.Position? = null

        composeTestRule.setContent {
            com.example.wali.ui.components.WaliBoardView(
                board = board,
                selectedPosition = null,
                highlightedMoves = emptySet(),
                capturablePositions = emptySet(),
                onCellClicked = { clickedPosition = it },
                showCellNumbers = showNumbersState.value
            )
        }

        // Les numéros 1 (A1), 12 (F2 occupée) et 30 (F5) sont affichés
        composeTestRule.onNodeWithTag("cell_number_A1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("cell_number_F2", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("cell_number_F5", useUnmergedTree = true).assertExists()

        // Le contentDescription inclut le numéro de case pour TalkBack
        composeTestRule.onNode(
            androidx.compose.ui.test.hasContentDescription("Case 1 (A1) vide")
        ).assertExists()
        composeTestRule.onNode(
            androidx.compose.ui.test.hasContentDescription("Bâtonnet en case 12 (F2)")
        ).assertExists()

        // Le clic sur la case F2 fonctionne normalement sans être bloqué par le numéro
        composeTestRule.onNodeWithTag("cell_F2").performClick()
        assertEquals(posF2, clickedPosition)

        // Masquage des numéros via le réglage
        showNumbersState.value = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cell_number_A1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("cell_number_F2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `online screen renders spectateurs tab and waiting room displays both player and spectator codes`() {
        var spectatedCode: String? = null
        val waitingCodeState = androidx.compose.runtime.mutableStateOf<String?>(null)

        composeTestRule.setContent {
            com.example.wali.ui.online.WaliOnlineScreen(
                waitingForOpponentCode = waitingCodeState.value,
                waitingSpectatorCode = "S765432",
                publicWaitingGames = emptyList(),
                isLoading = false,
                errorMessage = null,
                onCreateGame = { _, _ -> },
                onJoinGame = {},
                onSpectateGame = { spectatedCode = it },
                onCancelWaiting = {},
                onDismissError = {},
                onNavigateBack = {}
            )
        }

        // L'onglet Spectateurs existe à côté de Créer et Rejoindre
        composeTestRule.onNodeWithTag("tab_spectate_game").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_spectate_game").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("input_spectator_code").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_spectate_by_code").assertIsDisplayed()

        // Retour sur l'onglet Créer et passage en salle d'attente : les deux codes (joueur et spectateur) sont affichés avec leurs boutons copier/partager
        composeTestRule.onNodeWithTag("tab_create_game").performClick()
        waitingCodeState.value = "PL2345"
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("waiting_code_display").assertIsDisplayed()
        composeTestRule.onNodeWithTag("waiting_spectator_code_display").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_copy_spectator_code").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_share_spectator_code").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `twoPlayersFaceToFace topPanelRotated180 hitTestingAndPlayerSpecificDialogsWork`() {
        val vm = com.example.wali.ui.WaliViewModel()
        vm.startNewGame(GameMode.TWO_PLAYERS, Player.STICKS, AiDifficulty.MEDIUM)
        vm.stopTurnTimer()

        composeTestRule.setContent {
            com.example.wali.ui.WaliGameScreen(viewModel = vm)
        }
        composeTestRule.waitForIdle()

        // 1. Les deux panneaux joueurs sont affichés, STICKS en haut et STONES en bas
        val sticksBounds = composeTestRule.onNodeWithTag("player_panel_sticks").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val stonesBounds = composeTestRule.onNodeWithTag("player_panel_stones").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Le panneau STICKS (haut) doit être positionné au-dessus du panneau STONES (bas)",
            sticksBounds.top < stonesBounds.top
        )

        // 2. Test du hit-testing sur le bouton Abandonner pivoté à 180° du joueur du haut (STICKS)
        composeTestRule.onNodeWithTag("btn_forfeit_sticks").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // La boîte de confirmation nomme explicitement Bâtonnets du côté du panneau du haut
        composeTestRule.onNodeWithTag("forfeit_confirm_text_sticks", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Bâtonnets, voulez-vous vraiment abandonner ?", useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals(Player.STICKS, vm.uiState.value.pendingForfeitPlayer)

        // Annulation via le bouton Annuler pivoté à 180° du joueur du haut
        composeTestRule.onNodeWithTag("btn_cancel_forfeit_sticks").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(null, vm.uiState.value.pendingForfeitPlayer)

        // 3. Pendant le tour de STICKS, le joueur du bas (STONES) clique sur SON propre bouton Abandonner
        assertEquals(Player.STICKS, vm.uiState.value.gameState.currentPlayer)
        composeTestRule.onNodeWithTag("btn_forfeit_stones").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("forfeit_confirm_text_stones", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cailloux, voulez-vous vraiment abandonner ?", useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals(Player.STONES, vm.uiState.value.pendingForfeitPlayer)

        composeTestRule.onNodeWithTag("btn_confirm_forfeit_stones").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // C'est STONES qui a abandonné : STICKS gagne
        assertEquals(Player.STICKS, vm.uiState.value.gameState.winner)
    }

    @Test
    fun ruleDiagrams_allIllustratedMovesAreLegalAndProduceStatedEffect() {
        // -------------------------------------------------------------
        // 1. SimpleChevalDiagram : déplacement de 9 vers 15 formant [14-15-16]
        // -------------------------------------------------------------
        // Numérotation 1..30 :
        // 9  = Position(1, 2)
        // 14 = Position(2, 1)
        // 15 = Position(2, 2)
        // 16 = Position(2, 3)
        val pos9 = Position(1, 2)
        val pos14 = Position(2, 1)
        val pos15 = Position(2, 2)
        val pos16 = Position(2, 3)

        val initialBoardSimple = Position.allPositions.associateWith { pos ->
            when (pos) {
                pos9, pos14, pos16 -> Player.STICKS
                Position(0, 0), Position(0, 1), Position(0, 2) -> Player.STONES // Pions adverses capturables
                else -> null
            }
        }
        val stateSimple = GameState(
            board = initialBoardSimple,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 3,
            stonesRemaining = 3,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList()
        )

        // Adjacence orthogonale : pos9 est adjacent à pos15, pos15 est vide
        assertTrue((Math.abs(pos9.row - pos15.row) + Math.abs(pos9.col - pos15.col)) == 1)
        assertEquals(null, stateSimple.board[pos15])

        // Le coup 9 -> 15 est légal
        val validMovesSimple = WaliEngine.getValidMoves(stateSimple, pos9)
        assertTrue("Le déplacement 9 -> 15 doit être légal", validMovesSimple.contains(pos15))

        // Exécution du coup
        val afterSimple = WaliEngine.movePiece(stateSimple, pos9, pos15)
        assertEquals(null, afterSimple.board[pos9]) // pos9 s'est vidée
        assertEquals(Player.STICKS, afterSimple.board[pos15]) // pos15 occupée

        // Exactement 1 ligne formée et 1 capture accordée
        val newLinesSimple = WaliEngine.detectNewLines(stateSimple.board, afterSimple.board, Player.STICKS)
        assertEquals(1, newLinesSimple.size)
        assertEquals(setOf(pos14, pos15, pos16), newLinesSimple.first().cells)
        assertEquals(1, afterSimple.pendingCaptures)
        assertTrue(WaliEngine.canCapture(afterSimple))

        // -------------------------------------------------------------
        // 2. DoubleChevalDiagram : déplacement de 9 vers 15 formant [13-14-15] et [15-21-27]
        // -------------------------------------------------------------
        // 13 = Position(2, 0)
        // 14 = Position(2, 1)
        // 15 = Position(2, 2)
        // 16 = Position(2, 3) (libre !)
        // 21 = Position(3, 2)
        // 27 = Position(4, 2)
        val pos13 = Position(2, 0)
        val pos21 = Position(3, 2)
        val pos27 = Position(4, 2)

        val initialBoardDouble = Position.allPositions.associateWith { pos ->
            when (pos) {
                pos9, pos13, pos14, pos21, pos27 -> Player.STICKS
                Position(0, 0), Position(0, 1), Position(0, 5) -> Player.STONES
                else -> null
            }
        }
        val stateDouble = GameState(
            board = initialBoardDouble,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 5,
            stonesRemaining = 3,
            winner = null,
            usedLines = emptySet(),
            moveHistory = emptyList()
        )

        // Adjacence et case libre
        assertTrue((Math.abs(pos9.row - pos15.row) + Math.abs(pos9.col - pos15.col)) == 1)
        assertEquals(null, stateDouble.board[pos15])
        assertEquals(null, stateDouble.board[pos16])

        // Coup légal
        val validMovesDouble = WaliEngine.getValidMoves(stateDouble, pos9)
        assertTrue("Le déplacement 9 -> 15 doit être légal pour le cheval double", validMovesDouble.contains(pos15))

        // Exécution
        val afterDouble = WaliEngine.movePiece(stateDouble, pos9, pos15)
        assertEquals(null, afterDouble.board[pos9])
        assertEquals(Player.STICKS, afterDouble.board[pos15])
        assertEquals(null, afterDouble.board[pos16])

        // Deux lignes formées simultanément
        val newLinesDouble = WaliEngine.detectNewLines(stateDouble.board, afterDouble.board, Player.STICKS)
        assertEquals(2, newLinesDouble.size)
        val horizLine = newLinesDouble.firstOrNull { it.cells == setOf(pos13, pos14, pos15) }
        val vertLine = newLinesDouble.firstOrNull { it.cells == setOf(pos15, pos21, pos27) }
        assertTrue("Ligne horizontale [13-14-15] formée", horizLine != null)
        assertTrue("Ligne verticale [15-21-27] formée", vertLine != null)

        // RÈGLE STRICTE WALI : UNE SEULE capture accordée malgré les 2 lignes formées !
        assertEquals(1, afterDouble.pendingCaptures)
        assertTrue(WaliEngine.canCapture(afterDouble))

        // -------------------------------------------------------------
        // 3. UsedLineAndResetDiagram : ligne [8-9-10] cassée en 10->16 puis reformée 16->10
        // -------------------------------------------------------------
        // 8  = Position(1, 1)
        // 9  = Position(1, 2)
        // 10 = Position(1, 3)
        // 16 = Position(2, 3)
        val pos8 = Position(1, 1)
        val pos10 = Position(1, 3)
        val line8910 = Line(setOf(pos8, pos9, pos10))

        val boardUsed = Position.allPositions.associateWith { pos ->
            when (pos) {
                pos8, pos9, pos10 -> Player.STICKS
                Position(0, 0), Position(0, 1), Position(0, 2) -> Player.STONES
                else -> null
            }
        }
        val stateUsed = GameState(
            board = boardUsed,
            currentPlayer = Player.STICKS,
            phase = Phase.MOVEMENT,
            sticksRemaining = 3,
            stonesRemaining = 3,
            winner = null,
            usedLines = setOf(line8910), // Ligne déjà consommée
            moveHistory = emptyList()
        )

        // Étape 1 : casser la ligne en déplaçant 10 vers 16
        assertTrue((Math.abs(pos10.row - pos16.row) + Math.abs(pos10.col - pos16.col)) == 1)
        val afterBreak = WaliEngine.movePiece(stateUsed, pos10, pos16)
        assertEquals(null, afterBreak.board[pos10])
        assertEquals(Player.STICKS, afterBreak.board[pos16])
        assertEquals(0, afterBreak.pendingCaptures) // Aucune capture car ligne cassée
        assertFalse("La ligne rompue doit être retirée de usedLines", afterBreak.usedLines.contains(line8910))

        // On simule le coup adverse puis le retour du joueur : déplacement 16 -> 10
        val stateBeforeReturn = afterBreak.copy(currentPlayer = Player.STICKS)
        val afterReformed = WaliEngine.movePiece(stateBeforeReturn, pos16, pos10)
        assertEquals(Player.STICKS, afterReformed.board[pos10])
        assertEquals(null, afterReformed.board[pos16])

        // La ligne [8-9-10] est reformée et donne à nouveau droit à 1 capture
        val newLinesReformed = WaliEngine.detectNewLines(stateBeforeReturn.board, afterReformed.board, Player.STICKS)
        assertEquals(1, newLinesReformed.size)
        assertEquals(setOf(pos8, pos9, pos10), newLinesReformed.first().cells)
        assertEquals(1, afterReformed.pendingCaptures)
    }
}


