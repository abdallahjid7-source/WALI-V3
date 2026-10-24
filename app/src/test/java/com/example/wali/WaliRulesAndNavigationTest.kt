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
import org.junit.Assert.assertEquals
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

        // Vérification du titre principal et de l'introduction
        composeTestRule.onNodeWithText("Règles du Wali").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jeu traditionnel Songhaï de Tombouctou").assertIsDisplayed()
        composeTestRule.onNodeWithText("Un trésor du désert malien").assertIsDisplayed()

        // Vérification de la première section
        composeTestRule.onNodeWithText("Le Plateau et l'Orientation").assertIsDisplayed()

        // Défilement et vérification des sections suivantes dans la LazyColumn
        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Les Pions et le Premier Coup"))
        composeTestRule.onNodeWithText("Les Pions et le Premier Coup").assertIsDisplayed()

        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Phase 1 : La Pose des Pions"))
        composeTestRule.onNodeWithText("Phase 1 : La Pose des Pions").assertIsDisplayed()

        composeTestRule.onNodeWithTag("rules_lazy_column")
            .performScrollToNode(hasText("Phase 2 : Le Déplacement"))
        composeTestRule.onNodeWithText("Phase 2 : Le Déplacement").assertIsDisplayed()

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
}


