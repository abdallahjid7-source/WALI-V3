package com.example.wali

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
