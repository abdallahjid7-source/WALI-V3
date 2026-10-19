package com.example.wali.storage

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.wali.ui.theme.WaliThemePalette
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Modèle de données pour les préférences utilisateur audio et visuelles.
 */
data class WaliUserPreferences(
    val sfxVolume: Float = 0.8f,
    val musicVolume: Float = 0.5f,
    val isMuted: Boolean = false,
    val themePalette: WaliThemePalette = WaliThemePalette.SAHEL_JOUR,
    val isTimerEnabled: Boolean = true,
    val timerDurationSeconds: Int = 30,
    val isMandatoryAlignmentEnabled: Boolean = false,
    val isDefeatAtTwoPiecesEnabled: Boolean = false,
    val isCellNumbersEnabled: Boolean = true
)

/**
 * Interface pour la persistance des préférences utilisateur.
 */
interface WaliPreferencesRepository {
    val preferencesFlow: Flow<WaliUserPreferences>
    suspend fun getPreferences(): WaliUserPreferences
    suspend fun updateSfxVolume(volume: Float)
    suspend fun updateMusicVolume(volume: Float)
    suspend fun updateMuted(muted: Boolean)
    suspend fun updateThemePalette(palette: WaliThemePalette)
    suspend fun updateTimerEnabled(enabled: Boolean)
    suspend fun updateTimerDuration(durationSeconds: Int)
    suspend fun updateMandatoryAlignment(enabled: Boolean) {}
    suspend fun updateDefeatAtTwoPieces(enabled: Boolean) {}
    suspend fun updateCellNumbersEnabled(enabled: Boolean) {}
}

/**
 * Implémentation basée sur le même DataStore que le jeu, avec des clés isolées.
 */
class DataStoreWaliPreferencesRepository(
    private val context: Context
) : WaliPreferencesRepository {

    companion object {
        val KEY_SFX_VOLUME = floatPreferencesKey("pref_sfx_volume")
        val KEY_MUSIC_VOLUME = floatPreferencesKey("pref_music_volume")
        val KEY_IS_MUTED = booleanPreferencesKey("pref_is_muted")
        val KEY_THEME_PALETTE = stringPreferencesKey("pref_theme_palette")
        val KEY_TIMER_ENABLED = booleanPreferencesKey("pref_timer_enabled")
        val KEY_TIMER_DURATION = androidx.datastore.preferences.core.intPreferencesKey("pref_timer_duration")
        val KEY_MANDATORY_ALIGNMENT = booleanPreferencesKey("pref_mandatory_alignment")
        val KEY_DEFEAT_AT_TWO_PIECES = booleanPreferencesKey("pref_defeat_at_two_pieces")
        val KEY_CELL_NUMBERS_ENABLED = booleanPreferencesKey("pref_cell_numbers_enabled")
    }

    override val preferencesFlow: Flow<WaliUserPreferences> = context.waliDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.w("DataStoreWaliPrefs", "Error reading preferences safely caught: ${exception.message}")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            WaliUserPreferences(
                sfxVolume = prefs[KEY_SFX_VOLUME] ?: 0.8f,
                musicVolume = prefs[KEY_MUSIC_VOLUME] ?: 0.5f,
                isMuted = prefs[KEY_IS_MUTED] ?: false,
                themePalette = WaliThemePalette.fromName(prefs[KEY_THEME_PALETTE]),
                isTimerEnabled = prefs[KEY_TIMER_ENABLED] ?: true,
                timerDurationSeconds = prefs[KEY_TIMER_DURATION] ?: 30,
                isMandatoryAlignmentEnabled = prefs[KEY_MANDATORY_ALIGNMENT] ?: false,
                isDefeatAtTwoPiecesEnabled = prefs[KEY_DEFEAT_AT_TWO_PIECES] ?: false,
                isCellNumbersEnabled = prefs[KEY_CELL_NUMBERS_ENABLED] ?: true
            )
        }
        .distinctUntilChanged()

    override suspend fun getPreferences(): WaliUserPreferences {
        return try {
            val prefs = context.waliDataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        Log.w("DataStoreWaliPrefs", "Error reading preferences: ${exception.message}")
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .first()
            WaliUserPreferences(
                sfxVolume = prefs[KEY_SFX_VOLUME] ?: 0.8f,
                musicVolume = prefs[KEY_MUSIC_VOLUME] ?: 0.5f,
                isMuted = prefs[KEY_IS_MUTED] ?: false,
                themePalette = WaliThemePalette.fromName(prefs[KEY_THEME_PALETTE]),
                isTimerEnabled = prefs[KEY_TIMER_ENABLED] ?: true,
                timerDurationSeconds = prefs[KEY_TIMER_DURATION] ?: 30,
                isMandatoryAlignmentEnabled = prefs[KEY_MANDATORY_ALIGNMENT] ?: false,
                isDefeatAtTwoPiecesEnabled = prefs[KEY_DEFEAT_AT_TWO_PIECES] ?: false,
                isCellNumbersEnabled = prefs[KEY_CELL_NUMBERS_ENABLED] ?: true
            )
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "Fallback to default preferences: ${e.message}")
            WaliUserPreferences()
        }
    }

    override suspend fun updateSfxVolume(volume: Float) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_SFX_VOLUME] = volume.coerceIn(0f, 1f)
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateSfxVolume failed safely ignored: ${e.message}")
        }
    }

    override suspend fun updateMusicVolume(volume: Float) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_MUSIC_VOLUME] = volume.coerceIn(0f, 1f)
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateMusicVolume failed safely ignored: ${e.message}")
        }
    }

    override suspend fun updateMuted(muted: Boolean) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_IS_MUTED] = muted
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateMuted failed safely ignored: ${e.message}")
        }
    }

    override suspend fun updateThemePalette(palette: WaliThemePalette) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_THEME_PALETTE] = palette.name
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateThemePalette failed safely ignored: ${e.message}")
        }
    }

    override suspend fun updateTimerEnabled(enabled: Boolean) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_TIMER_ENABLED] = enabled
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateTimerEnabled failed safely ignored: ${e.message}")
        }
    }

    override suspend fun updateTimerDuration(durationSeconds: Int) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_TIMER_DURATION] = durationSeconds
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateTimerDuration failed safely ignored: ${e.message}")
        }
    }

    override suspend fun updateMandatoryAlignment(enabled: Boolean) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_MANDATORY_ALIGNMENT] = enabled
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateMandatoryAlignment failed safely ignored: ${e.message}")
        }
    }

    override suspend fun updateDefeatAtTwoPieces(enabled: Boolean) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_DEFEAT_AT_TWO_PIECES] = enabled
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateDefeatAtTwoPieces failed safely ignored: ${e.message}")
        }
    }

    override suspend fun updateCellNumbersEnabled(enabled: Boolean) {
        try {
            context.waliDataStore.edit { prefs ->
                prefs[KEY_CELL_NUMBERS_ENABLED] = enabled
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliPrefs", "updateCellNumbersEnabled failed safely ignored: ${e.message}")
        }
    }
}

/**
 * Implémentation en mémoire pour les tests unitaires.
 */
class InMemoryWaliPreferencesRepository(
    initial: WaliUserPreferences = WaliUserPreferences()
) : WaliPreferencesRepository {
    private var current = initial
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(initial)

    override val preferencesFlow: Flow<WaliUserPreferences> = flow

    override suspend fun getPreferences(): WaliUserPreferences = current

    override suspend fun updateSfxVolume(volume: Float) {
        current = current.copy(sfxVolume = volume.coerceIn(0f, 1f))
        flow.value = current
    }

    override suspend fun updateMusicVolume(volume: Float) {
        current = current.copy(musicVolume = volume.coerceIn(0f, 1f))
        flow.value = current
    }

    override suspend fun updateMuted(muted: Boolean) {
        current = current.copy(isMuted = muted)
        flow.value = current
    }

    override suspend fun updateThemePalette(palette: WaliThemePalette) {
        current = current.copy(themePalette = palette)
        flow.value = current
    }

    override suspend fun updateTimerEnabled(enabled: Boolean) {
        current = current.copy(isTimerEnabled = enabled)
        flow.value = current
    }

    override suspend fun updateTimerDuration(durationSeconds: Int) {
        current = current.copy(timerDurationSeconds = durationSeconds)
        flow.value = current
    }

    override suspend fun updateMandatoryAlignment(enabled: Boolean) {
        current = current.copy(isMandatoryAlignmentEnabled = enabled)
        flow.value = current
    }

    override suspend fun updateDefeatAtTwoPieces(enabled: Boolean) {
        current = current.copy(isDefeatAtTwoPiecesEnabled = enabled)
        flow.value = current
    }

    override suspend fun updateCellNumbersEnabled(enabled: Boolean) {
        current = current.copy(isCellNumbersEnabled = enabled)
        flow.value = current
    }
}
