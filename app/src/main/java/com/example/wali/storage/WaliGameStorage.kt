package com.example.wali.storage

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wali.model.Phase
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

val Context.waliDataStore: DataStore<Preferences> by preferencesDataStore(name = "wali_game_storage")

/**
 * Interface d'abstraction pour la persistance locale de la partie en cours.
 */
interface WaliGameStorage {
    /**
     * Sauvegarde l'état complet de la partie active.
     */
    suspend fun saveGame(savedGame: SavedGame)

    /**
     * Charge la partie sauvegardée, ou retourne null si aucune sauvegarde valide n'existe.
     */
    suspend fun loadSavedGame(): SavedGame?

    /**
     * Supprime définitivement la sauvegarde de la partie en cours.
     */
    suspend fun clearSavedGame()

    /**
     * Retourne vrai si une partie EN COURS (non achevée, sans vainqueur) existe en sauvegarde.
     */
    suspend fun hasActiveSavedGame(): Boolean
}

/**
 * Implémentation officielle basée sur Jetpack DataStore Preferences.
 * 100% hors-ligne, sans serveur ni dépendance réseau.
 */
class DataStoreWaliGameStorage(
    private val context: Context
) : WaliGameStorage {

    companion object {
        val KEY_SAVED_GAME_JSON = stringPreferencesKey("saved_game_state_json")
    }

    override suspend fun saveGame(savedGame: SavedGame) {
        try {
            val json = WaliGameSerializer.serialize(savedGame)
            context.waliDataStore.edit { preferences ->
                preferences[KEY_SAVED_GAME_JSON] = json
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliGameStorage", "Failed to save game state safely ignored: ${e.message}")
        }
    }

    override suspend fun loadSavedGame(): SavedGame? {
        return try {
            val preferences = context.waliDataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        Log.w("DataStoreWaliGameStorage", "Error reading saved game: ${exception.message}")
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .first()
            val json = preferences[KEY_SAVED_GAME_JSON] ?: return null
            WaliGameSerializer.deserialize(json)
        } catch (e: Exception) {
            Log.w("DataStoreWaliGameStorage", "Failed to load saved game safely ignored: ${e.message}")
            null
        }
    }

    override suspend fun clearSavedGame() {
        try {
            context.waliDataStore.edit { preferences ->
                preferences.remove(KEY_SAVED_GAME_JSON)
            }
        } catch (e: Exception) {
            Log.w("DataStoreWaliGameStorage", "Failed to clear saved game safely ignored: ${e.message}")
        }
    }

    override suspend fun hasActiveSavedGame(): Boolean {
        val saved = loadSavedGame() ?: return false
        // Une partie terminée (avec vainqueur ou en phase GAME_OVER) n'est jamais active
        return saved.gameState.winner == null && saved.gameState.phase != Phase.GAME_OVER
    }
}

/**
 * Implémentation en mémoire pour les tests unitaires et les scénarios sans Context.
 */
class InMemoryWaliGameStorage : WaliGameStorage {
    private var storedJson: String? = null

    override suspend fun saveGame(savedGame: SavedGame) {
        storedJson = WaliGameSerializer.serialize(savedGame)
    }

    override suspend fun loadSavedGame(): SavedGame? {
        return WaliGameSerializer.deserialize(storedJson)
    }

    override suspend fun clearSavedGame() {
        storedJson = null
    }

    override suspend fun hasActiveSavedGame(): Boolean {
        val saved = loadSavedGame() ?: return false
        return saved.gameState.winner == null && saved.gameState.phase != Phase.GAME_OVER
    }
}
