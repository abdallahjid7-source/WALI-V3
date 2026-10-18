package com.example.wali

import com.example.wali.audio.WaliAudio
import com.example.wali.model.Player
import com.example.wali.storage.InMemoryWaliGameStorage
import com.example.wali.storage.WaliPreferencesRepository
import com.example.wali.storage.WaliUserPreferences
import com.example.wali.ui.WaliViewModel
import com.example.wali.ui.theme.WaliThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Fake test double pour WaliAudio enregistrant tous les événements sonores.
 */
class RecordingWaliAudio : WaliAudio {
    var placeCount = 0
    var moveCount = 0
    var captureCount = 0
    var victoryCount = 0
    var defeatCount = 0
    var clickCount = 0
    var chatMessageCount = 0

    var musicStarted = false
    var musicPaused = false
    var isReleased = false

    var currentSfxVol = 0.8f
    var currentMusicVol = 0.5f
    private var _isMuted = false
    val isMuted: Boolean get() = _isMuted

    override fun playPlace(player: Player) { placeCount++ }
    override fun playMove() { moveCount++ }
    override fun playCapture() { captureCount++ }
    override fun playVictory() { victoryCount++ }
    override fun playDefeat() { defeatCount++ }
    override fun playClick() { clickCount++ }
    override fun playChatMessage() { chatMessageCount++ }

    override fun resumeMusic() { musicStarted = true }
    override fun pauseMusic() { musicPaused = true }

    override fun setSfxVolume(volume: Float) { currentSfxVol = volume }
    override fun setMusicVolume(volume: Float) { currentMusicVol = volume }
    override fun setMuted(muted: Boolean) { _isMuted = muted }
    override fun release() { isReleased = true }
}

/**
 * Fake test double pour WaliPreferencesRepository en mémoire.
 */
class FakeWaliPreferencesRepository(
    initialPrefs: WaliUserPreferences = WaliUserPreferences()
) : WaliPreferencesRepository {
    private val state = MutableStateFlow(initialPrefs)

    override val preferencesFlow: Flow<WaliUserPreferences> = state

    override suspend fun getPreferences(): WaliUserPreferences = state.value

    override suspend fun updateSfxVolume(volume: Float) {
        state.value = state.value.copy(sfxVolume = volume)
    }

    override suspend fun updateMusicVolume(volume: Float) {
        state.value = state.value.copy(musicVolume = volume)
    }

    override suspend fun updateMuted(muted: Boolean) {
        state.value = state.value.copy(isMuted = muted)
    }

    override suspend fun updateThemePalette(palette: WaliThemePalette) {
        state.value = state.value.copy(themePalette = palette)
    }

    override suspend fun updateTimerEnabled(enabled: Boolean) {
        state.value = state.value.copy(isTimerEnabled = enabled)
    }

    override suspend fun updateTimerDuration(durationSeconds: Int) {
        state.value = state.value.copy(timerDurationSeconds = durationSeconds)
    }

    override suspend fun updateMandatoryAlignment(enabled: Boolean) {
        state.value = state.value.copy(isMandatoryAlignmentEnabled = enabled)
    }

    override suspend fun updateDefeatAtTwoPieces(enabled: Boolean) {
        state.value = state.value.copy(isDefeatAtTwoPiecesEnabled = enabled)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WaliSettingsAndAudioTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var audio: RecordingWaliAudio
    private lateinit var prefs: FakeWaliPreferencesRepository
    private lateinit var storage: InMemoryWaliGameStorage

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        audio = RecordingWaliAudio()
        prefs = FakeWaliPreferencesRepository()
        storage = InMemoryWaliGameStorage()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultPreferences_areLoadedIntoViewModelState() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(0.8f, state.sfxVolume, 0.001f)
        assertEquals(0.5f, state.musicVolume, 0.001f)
        assertFalse(state.isMuted)
        assertEquals(WaliThemePalette.SAHEL_JOUR, state.themePalette)
    }

    @Test
    fun onSfxVolumeChanged_updatesState_andAudio_andPreferences() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        vm.onSfxVolumeChanged(0.35f)
        testScheduler.advanceUntilIdle()

        assertEquals(0.35f, vm.uiState.value.sfxVolume, 0.001f)
        assertEquals(0.35f, audio.currentSfxVol, 0.001f)
        assertEquals(0.35f, prefs.getPreferences().sfxVolume, 0.001f)
    }

    @Test
    fun onMusicVolumeChanged_updatesState_andAudio_andPreferences() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        vm.onMusicVolumeChanged(0.7f)
        testScheduler.advanceUntilIdle()

        assertEquals(0.7f, vm.uiState.value.musicVolume, 0.001f)
        assertEquals(0.7f, audio.currentMusicVol, 0.001f)
        assertEquals(0.7f, prefs.getPreferences().musicVolume, 0.001f)
    }

    @Test
    fun onMutedChanged_mutesAndUnmutesProperly() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        // Mute
        vm.onMutedChanged(true)
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isMuted)
        assertTrue(audio.isMuted)
        assertTrue(prefs.getPreferences().isMuted)

        // Unmute
        vm.onMutedChanged(false)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isMuted)
        assertFalse(audio.isMuted)
        assertFalse(prefs.getPreferences().isMuted)
    }

    @Test
    fun onThemeSelected_updatesTheme_andPersistsInPreferences() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        vm.onThemeSelected(WaliThemePalette.DESERT_NIGHT)
        testScheduler.advanceUntilIdle()

        assertEquals(WaliThemePalette.DESERT_NIGHT, vm.uiState.value.themePalette)
        assertEquals(WaliThemePalette.DESERT_NIGHT, prefs.getPreferences().themePalette)

        vm.onThemeSelected(WaliThemePalette.MODERN_LIGHT)
        testScheduler.advanceUntilIdle()

        assertEquals(WaliThemePalette.MODERN_LIGHT, vm.uiState.value.themePalette)
        assertEquals(WaliThemePalette.MODERN_LIGHT, prefs.getPreferences().themePalette)
    }

    @Test
    fun initialSavedPreferences_areRestoredCorrectly() = runTest {
        val customPrefs = FakeWaliPreferencesRepository(
            WaliUserPreferences(
                sfxVolume = 0.2f,
                musicVolume = 0.9f,
                isMuted = true,
                themePalette = WaliThemePalette.DESERT_NIGHT
            )
        )

        val vm = WaliViewModel(storage = storage, preferencesRepository = customPrefs, audio = audio)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(0.2f, state.sfxVolume, 0.001f)
        assertEquals(0.9f, state.musicVolume, 0.001f)
        assertTrue(state.isMuted)
        assertEquals(WaliThemePalette.DESERT_NIGHT, state.themePalette)

        assertEquals(0.2f, audio.currentSfxVol, 0.001f)
        assertEquals(0.9f, audio.currentMusicVol, 0.001f)
        assertTrue(audio.isMuted)
    }

    @Test
    fun musicLifecycle_startAndPauseControls() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        vm.startMusic()
        assertTrue(audio.musicStarted)

        vm.pauseMusic()
        assertTrue(audio.musicPaused)
    }

    @Test
    fun audioEvents_triggerOnPiecePlacement() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        val initialPlaceCount = audio.placeCount
        vm.onCellClicked(com.example.wali.model.Position(0, 0))
        testScheduler.advanceUntilIdle()

        assertEquals(initialPlaceCount + 1, audio.placeCount)
    }

    @Test
    fun timerPreferences_updatesArePersistedAndReflectedInViewModel() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isTimerEnabled)
        assertEquals(30, vm.uiState.value.timerDurationSeconds)

        vm.onTimerDurationChanged(60)
        testScheduler.advanceUntilIdle()
        assertEquals(60, vm.uiState.value.timerDurationSeconds)
        assertEquals(60, prefs.getPreferences().timerDurationSeconds)

        vm.onTimerEnabledChanged(false)
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isTimerEnabled)
        assertFalse(prefs.getPreferences().isTimerEnabled)
    }

    @Test
    fun ruleVariantsPreferences_areDisabledByDefault_andApplyToNextGame() = runTest {
        val vm = WaliViewModel(storage = storage, preferencesRepository = prefs, audio = audio)
        testScheduler.advanceUntilIdle()

        // Par défaut à false
        assertFalse(vm.uiState.value.isMandatoryAlignmentEnabled)
        assertFalse(vm.uiState.value.isDefeatAtTwoPiecesEnabled)
        assertFalse(vm.uiState.value.gameState.ruleOptions.mandatoryAlignment)
        assertFalse(vm.uiState.value.gameState.ruleOptions.defeatAtTwoPieces)

        // Activation des deux variantes dans les réglages
        vm.onMandatoryAlignmentChanged(true)
        vm.onDefeatAtTwoPiecesChanged(true)
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isMandatoryAlignmentEnabled)
        assertTrue(vm.uiState.value.isDefeatAtTwoPiecesEnabled)
        assertTrue(prefs.getPreferences().isMandatoryAlignmentEnabled)
        assertTrue(prefs.getPreferences().isDefeatAtTwoPiecesEnabled)

        // La partie déjà en cours reste sur ses règles initiales jusqu'à la prochaine partie
        assertFalse(vm.uiState.value.gameState.ruleOptions.mandatoryAlignment)
        assertFalse(vm.uiState.value.gameState.ruleOptions.defeatAtTwoPieces)

        // Démarrage d'une nouvelle partie -> les variantes s'appliquent
        vm.startNewGame(com.example.wali.model.GameMode.TWO_PLAYERS)
        vm.stopTurnTimer()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.gameState.ruleOptions.mandatoryAlignment)
        assertTrue(vm.uiState.value.gameState.ruleOptions.defeatAtTwoPieces)
    }
}
