package com.example.wali.storage

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.wali.ai.AiDifficulty
import com.example.wali.model.GameMode
import com.example.wali.model.Player
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Résultat d'une partie terminée pour le joueur local.
 */
enum class GameOutcome {
    WIN,
    LOSS,
    COMPLETED // Pour les parties 2 joueurs locales (sans camp attribué au joueur unique)
}

/**
 * Enregistrement immuable d'une partie terminée.
 */
data class GameResultRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val outcome: GameOutcome,
    val gameMode: GameMode,
    val aiDifficulty: AiDifficulty? = null,
    val durationSeconds: Long,
    val winner: Player,
    val reason: String = ""
) {
    val formattedDuration: String
        get() = formatDuration(durationSeconds)

    val formattedDate: String
        get() {
            return try {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(timestampMs))
            } catch (e: Exception) {
                ""
            }
        }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("timestampMs", timestampMs)
        obj.put("outcome", outcome.name)
        obj.put("gameMode", gameMode.name)
        if (aiDifficulty != null) {
            obj.put("aiDifficulty", aiDifficulty.name)
        }
        obj.put("durationSeconds", durationSeconds)
        obj.put("winner", winner.name)
        obj.put("reason", reason)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): GameResultRecord? {
            return try {
                GameResultRecord(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                    outcome = GameOutcome.valueOf(obj.getString("outcome")),
                    gameMode = GameMode.valueOf(obj.getString("gameMode")),
                    aiDifficulty = if (obj.has("aiDifficulty") && !obj.isNull("aiDifficulty")) {
                        AiDifficulty.valueOf(obj.getString("aiDifficulty"))
                    } else null,
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    winner = Player.valueOf(obj.getString("winner")),
                    reason = obj.optString("reason", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Statistiques cumulées du tableau des scores persisté via DataStore.
 */
data class ScoreboardStats(
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val totalGamesPlayed: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val fastestWinSeconds: Long? = null,
    val longestGameSeconds: Long? = null,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    // Détails mode Contre la Machine
    val vsComputerWins: Int = 0,
    val vsComputerLosses: Int = 0,
    val vsComputerDurationSeconds: Long = 0L,
    val easyAiWins: Int = 0,
    val easyAiLosses: Int = 0,
    val mediumAiWins: Int = 0,
    val mediumAiLosses: Int = 0,
    val hardAiWins: Int = 0,
    val hardAiLosses: Int = 0,
    // Détails mode En Ligne
    val onlineWins: Int = 0,
    val onlineLosses: Int = 0,
    val onlineDurationSeconds: Long = 0L,
    // Détails mode 2 Joueurs Local
    val twoPlayersGames: Int = 0,
    val twoPlayersDurationSeconds: Long = 0L
) {
    /**
     * Taux de victoire en pourcentage (0.0 à 100.0).
     */
    val winRatePercent: Float
        get() {
            val totalDecisive = totalWins + totalLosses
            return if (totalDecisive > 0) {
                (totalWins.toFloat() / totalDecisive) * 100f
            } else 0f
        }

    /**
     * Durée moyenne par partie en secondes.
     */
    val averageGameDurationSeconds: Long
        get() = if (totalGamesPlayed > 0) totalDurationSeconds / totalGamesPlayed else 0L

    val formattedTotalDuration: String
        get() = formatDuration(totalDurationSeconds)

    val formattedAverageDuration: String
        get() = formatDuration(averageGameDurationSeconds)

    val formattedFastestWin: String
        get() = fastestWinSeconds?.let { formatDuration(it) } ?: "—"

    val formattedLongestGame: String
        get() = longestGameSeconds?.let { formatDuration(it) } ?: "—"
}

/**
 * Formate une durée en secondes en texte lisible (ex: "4m 12s", "1h 25m", "< 1m").
 */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

/**
 * Formate une durée au format chronomètre (ex: "03:45", "1:12:05").
 */
fun formatTimerDigital(seconds: Long): String {
    if (seconds < 0) return "00:00"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}

/**
 * Interface d'abstraction pour la persistance et l'observation du tableau des scores.
 */
interface WaliScoreboardRepository {
    val statsFlow: Flow<ScoreboardStats>
    val recentGamesFlow: Flow<List<GameResultRecord>>

    suspend fun getStats(): ScoreboardStats
    suspend fun recordGameResult(result: GameResultRecord)
    suspend fun resetScoreboard()
}

/**
 * Implémentation officielle basée sur Jetpack DataStore Preferences.
 */
class DataStoreWaliScoreboardRepository(
    private val context: Context
) : WaliScoreboardRepository {

    companion object {
        val KEY_TOTAL_WINS = intPreferencesKey("scoreboard_total_wins")
        val KEY_TOTAL_LOSSES = intPreferencesKey("scoreboard_total_losses")
        val KEY_TOTAL_GAMES = intPreferencesKey("scoreboard_total_games")
        val KEY_TOTAL_DURATION_SEC = longPreferencesKey("scoreboard_total_duration_sec")
        val KEY_FASTEST_WIN_SEC = longPreferencesKey("scoreboard_fastest_win_sec")
        val KEY_LONGEST_GAME_SEC = longPreferencesKey("scoreboard_longest_game_sec")
        val KEY_CURRENT_WIN_STREAK = intPreferencesKey("scoreboard_current_win_streak")
        val KEY_BEST_WIN_STREAK = intPreferencesKey("scoreboard_best_win_streak")

        val KEY_VS_COMP_WINS = intPreferencesKey("scoreboard_vs_comp_wins")
        val KEY_VS_COMP_LOSSES = intPreferencesKey("scoreboard_vs_comp_losses")
        val KEY_VS_COMP_DURATION = longPreferencesKey("scoreboard_vs_comp_duration")
        val KEY_EASY_AI_WINS = intPreferencesKey("scoreboard_easy_ai_wins")
        val KEY_EASY_AI_LOSSES = intPreferencesKey("scoreboard_easy_ai_losses")
        val KEY_MEDIUM_AI_WINS = intPreferencesKey("scoreboard_medium_ai_wins")
        val KEY_MEDIUM_AI_LOSSES = intPreferencesKey("scoreboard_medium_ai_losses")
        val KEY_HARD_AI_WINS = intPreferencesKey("scoreboard_hard_ai_wins")
        val KEY_HARD_AI_LOSSES = intPreferencesKey("scoreboard_hard_ai_losses")

        val KEY_ONLINE_WINS = intPreferencesKey("scoreboard_online_wins")
        val KEY_ONLINE_LOSSES = intPreferencesKey("scoreboard_online_losses")
        val KEY_ONLINE_DURATION = longPreferencesKey("scoreboard_online_duration")

        val KEY_TWO_PLAYERS_GAMES = intPreferencesKey("scoreboard_two_players_games")
        val KEY_TWO_PLAYERS_DURATION = longPreferencesKey("scoreboard_two_players_duration")

        val KEY_RECENT_GAMES_JSON = stringPreferencesKey("scoreboard_recent_games_json")

        private const val MAX_RECENT_GAMES = 25
    }

    override val statsFlow: Flow<ScoreboardStats> = context.waliDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.w("WaliScoreboardRepo", "Error reading scoreboard: ${exception.message}")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            ScoreboardStats(
                totalWins = prefs[KEY_TOTAL_WINS] ?: 0,
                totalLosses = prefs[KEY_TOTAL_LOSSES] ?: 0,
                totalGamesPlayed = prefs[KEY_TOTAL_GAMES] ?: 0,
                totalDurationSeconds = prefs[KEY_TOTAL_DURATION_SEC] ?: 0L,
                fastestWinSeconds = prefs[KEY_FASTEST_WIN_SEC]?.takeIf { it > 0 },
                longestGameSeconds = prefs[KEY_LONGEST_GAME_SEC]?.takeIf { it > 0 },
                currentWinStreak = prefs[KEY_CURRENT_WIN_STREAK] ?: 0,
                bestWinStreak = prefs[KEY_BEST_WIN_STREAK] ?: 0,
                vsComputerWins = prefs[KEY_VS_COMP_WINS] ?: 0,
                vsComputerLosses = prefs[KEY_VS_COMP_LOSSES] ?: 0,
                vsComputerDurationSeconds = prefs[KEY_VS_COMP_DURATION] ?: 0L,
                easyAiWins = prefs[KEY_EASY_AI_WINS] ?: 0,
                easyAiLosses = prefs[KEY_EASY_AI_LOSSES] ?: 0,
                mediumAiWins = prefs[KEY_MEDIUM_AI_WINS] ?: 0,
                mediumAiLosses = prefs[KEY_MEDIUM_AI_LOSSES] ?: 0,
                hardAiWins = prefs[KEY_HARD_AI_WINS] ?: 0,
                hardAiLosses = prefs[KEY_HARD_AI_LOSSES] ?: 0,
                onlineWins = prefs[KEY_ONLINE_WINS] ?: 0,
                onlineLosses = prefs[KEY_ONLINE_LOSSES] ?: 0,
                onlineDurationSeconds = prefs[KEY_ONLINE_DURATION] ?: 0L,
                twoPlayersGames = prefs[KEY_TWO_PLAYERS_GAMES] ?: 0,
                twoPlayersDurationSeconds = prefs[KEY_TWO_PLAYERS_DURATION] ?: 0L
            )
        }
        .distinctUntilChanged()

    override val recentGamesFlow: Flow<List<GameResultRecord>> = context.waliDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.w("WaliScoreboardRepo", "Error reading recent games: ${exception.message}")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val json = prefs[KEY_RECENT_GAMES_JSON]
            parseRecentGamesJson(json)
        }
        .distinctUntilChanged()

    override suspend fun getStats(): ScoreboardStats {
        return try {
            val prefs = context.waliDataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        Log.w("WaliScoreboardRepo", "getStats IO error: ${exception.message}")
                        emit(emptyPreferences())
                    } else throw exception
                }
                .first()

            ScoreboardStats(
                totalWins = prefs[KEY_TOTAL_WINS] ?: 0,
                totalLosses = prefs[KEY_TOTAL_LOSSES] ?: 0,
                totalGamesPlayed = prefs[KEY_TOTAL_GAMES] ?: 0,
                totalDurationSeconds = prefs[KEY_TOTAL_DURATION_SEC] ?: 0L,
                fastestWinSeconds = prefs[KEY_FASTEST_WIN_SEC]?.takeIf { it > 0 },
                longestGameSeconds = prefs[KEY_LONGEST_GAME_SEC]?.takeIf { it > 0 },
                currentWinStreak = prefs[KEY_CURRENT_WIN_STREAK] ?: 0,
                bestWinStreak = prefs[KEY_BEST_WIN_STREAK] ?: 0,
                vsComputerWins = prefs[KEY_VS_COMP_WINS] ?: 0,
                vsComputerLosses = prefs[KEY_VS_COMP_LOSSES] ?: 0,
                vsComputerDurationSeconds = prefs[KEY_VS_COMP_DURATION] ?: 0L,
                easyAiWins = prefs[KEY_EASY_AI_WINS] ?: 0,
                easyAiLosses = prefs[KEY_EASY_AI_LOSSES] ?: 0,
                mediumAiWins = prefs[KEY_MEDIUM_AI_WINS] ?: 0,
                mediumAiLosses = prefs[KEY_MEDIUM_AI_LOSSES] ?: 0,
                hardAiWins = prefs[KEY_HARD_AI_WINS] ?: 0,
                hardAiLosses = prefs[KEY_HARD_AI_LOSSES] ?: 0,
                onlineWins = prefs[KEY_ONLINE_WINS] ?: 0,
                onlineLosses = prefs[KEY_ONLINE_LOSSES] ?: 0,
                onlineDurationSeconds = prefs[KEY_ONLINE_DURATION] ?: 0L,
                twoPlayersGames = prefs[KEY_TWO_PLAYERS_GAMES] ?: 0,
                twoPlayersDurationSeconds = prefs[KEY_TWO_PLAYERS_DURATION] ?: 0L
            )
        } catch (e: Exception) {
            Log.w("WaliScoreboardRepo", "getStats fallback: ${e.message}")
            ScoreboardStats()
        }
    }

    override suspend fun recordGameResult(result: GameResultRecord) {
        try {
            context.waliDataStore.edit { prefs ->
                val prevTotalGames = prefs[KEY_TOTAL_GAMES] ?: 0
                val prevTotalWins = prefs[KEY_TOTAL_WINS] ?: 0
                val prevTotalLosses = prefs[KEY_TOTAL_LOSSES] ?: 0
                val prevDuration = prefs[KEY_TOTAL_DURATION_SEC] ?: 0L
                val prevFastest = prefs[KEY_FASTEST_WIN_SEC]
                val prevLongest = prefs[KEY_LONGEST_GAME_SEC] ?: 0L
                val prevCurrentStreak = prefs[KEY_CURRENT_WIN_STREAK] ?: 0
                val prevBestStreak = prefs[KEY_BEST_WIN_STREAK] ?: 0

                prefs[KEY_TOTAL_GAMES] = prevTotalGames + 1
                prefs[KEY_TOTAL_DURATION_SEC] = prevDuration + result.durationSeconds

                if (result.durationSeconds > prevLongest) {
                    prefs[KEY_LONGEST_GAME_SEC] = result.durationSeconds
                }

                when (result.outcome) {
                    GameOutcome.WIN -> {
                        prefs[KEY_TOTAL_WINS] = prevTotalWins + 1
                        val newStreak = prevCurrentStreak + 1
                        prefs[KEY_CURRENT_WIN_STREAK] = newStreak
                        if (newStreak > prevBestStreak) {
                            prefs[KEY_BEST_WIN_STREAK] = newStreak
                        }
                        if (prevFastest == null || result.durationSeconds < prevFastest) {
                            prefs[KEY_FASTEST_WIN_SEC] = result.durationSeconds
                        }
                    }
                    GameOutcome.LOSS -> {
                        prefs[KEY_TOTAL_LOSSES] = prevTotalLosses + 1
                        prefs[KEY_CURRENT_WIN_STREAK] = 0
                    }
                    GameOutcome.COMPLETED -> {
                        // Pas de camp unique en 2 joueurs local
                    }
                }

                when (result.gameMode) {
                    GameMode.VS_COMPUTER -> {
                        val vsCompWins = prefs[KEY_VS_COMP_WINS] ?: 0
                        val vsCompLosses = prefs[KEY_VS_COMP_LOSSES] ?: 0
                        val vsCompDuration = prefs[KEY_VS_COMP_DURATION] ?: 0L
                        prefs[KEY_VS_COMP_DURATION] = vsCompDuration + result.durationSeconds

                        if (result.outcome == GameOutcome.WIN) {
                            prefs[KEY_VS_COMP_WINS] = vsCompWins + 1
                        } else if (result.outcome == GameOutcome.LOSS) {
                            prefs[KEY_VS_COMP_LOSSES] = vsCompLosses + 1
                        }

                        when (result.aiDifficulty) {
                            AiDifficulty.EASY -> {
                                if (result.outcome == GameOutcome.WIN) {
                                    prefs[KEY_EASY_AI_WINS] = (prefs[KEY_EASY_AI_WINS] ?: 0) + 1
                                } else {
                                    prefs[KEY_EASY_AI_LOSSES] = (prefs[KEY_EASY_AI_LOSSES] ?: 0) + 1
                                }
                            }
                            AiDifficulty.MEDIUM -> {
                                if (result.outcome == GameOutcome.WIN) {
                                    prefs[KEY_MEDIUM_AI_WINS] = (prefs[KEY_MEDIUM_AI_WINS] ?: 0) + 1
                                } else {
                                    prefs[KEY_MEDIUM_AI_LOSSES] = (prefs[KEY_MEDIUM_AI_LOSSES] ?: 0) + 1
                                }
                            }
                            AiDifficulty.HARD, null -> {
                                if (result.outcome == GameOutcome.WIN) {
                                    prefs[KEY_HARD_AI_WINS] = (prefs[KEY_HARD_AI_WINS] ?: 0) + 1
                                } else {
                                    prefs[KEY_HARD_AI_LOSSES] = (prefs[KEY_HARD_AI_LOSSES] ?: 0) + 1
                                }
                            }
                        }
                    }
                    GameMode.ONLINE -> {
                        val onlineWins = prefs[KEY_ONLINE_WINS] ?: 0
                        val onlineLosses = prefs[KEY_ONLINE_LOSSES] ?: 0
                        val onlineDuration = prefs[KEY_ONLINE_DURATION] ?: 0L
                        prefs[KEY_ONLINE_DURATION] = onlineDuration + result.durationSeconds

                        if (result.outcome == GameOutcome.WIN) {
                            prefs[KEY_ONLINE_WINS] = onlineWins + 1
                        } else if (result.outcome == GameOutcome.LOSS) {
                            prefs[KEY_ONLINE_LOSSES] = onlineLosses + 1
                        }
                    }
                    GameMode.TWO_PLAYERS -> {
                        val twoGames = prefs[KEY_TWO_PLAYERS_GAMES] ?: 0
                        val twoDuration = prefs[KEY_TWO_PLAYERS_DURATION] ?: 0L
                        prefs[KEY_TWO_PLAYERS_GAMES] = twoGames + 1
                        prefs[KEY_TWO_PLAYERS_DURATION] = twoDuration + result.durationSeconds
                    }
                }

                // Mise à jour de la liste des parties récentes (jusqu'à MAX_RECENT_GAMES)
                val currentRecent = parseRecentGamesJson(prefs[KEY_RECENT_GAMES_JSON]).toMutableList()
                currentRecent.add(0, result)
                val trimmed = currentRecent.take(MAX_RECENT_GAMES)
                prefs[KEY_RECENT_GAMES_JSON] = serializeRecentGamesJson(trimmed)
            }
        } catch (e: Exception) {
            Log.w("WaliScoreboardRepo", "Failed to record game result: ${e.message}")
        }
    }

    override suspend fun resetScoreboard() {
        try {
            context.waliDataStore.edit { prefs ->
                prefs.remove(KEY_TOTAL_WINS)
                prefs.remove(KEY_TOTAL_LOSSES)
                prefs.remove(KEY_TOTAL_GAMES)
                prefs.remove(KEY_TOTAL_DURATION_SEC)
                prefs.remove(KEY_FASTEST_WIN_SEC)
                prefs.remove(KEY_LONGEST_GAME_SEC)
                prefs.remove(KEY_CURRENT_WIN_STREAK)
                prefs.remove(KEY_BEST_WIN_STREAK)
                prefs.remove(KEY_VS_COMP_WINS)
                prefs.remove(KEY_VS_COMP_LOSSES)
                prefs.remove(KEY_VS_COMP_DURATION)
                prefs.remove(KEY_EASY_AI_WINS)
                prefs.remove(KEY_EASY_AI_LOSSES)
                prefs.remove(KEY_MEDIUM_AI_WINS)
                prefs.remove(KEY_MEDIUM_AI_LOSSES)
                prefs.remove(KEY_HARD_AI_WINS)
                prefs.remove(KEY_HARD_AI_LOSSES)
                prefs.remove(KEY_ONLINE_WINS)
                prefs.remove(KEY_ONLINE_LOSSES)
                prefs.remove(KEY_ONLINE_DURATION)
                prefs.remove(KEY_TWO_PLAYERS_GAMES)
                prefs.remove(KEY_TWO_PLAYERS_DURATION)
                prefs.remove(KEY_RECENT_GAMES_JSON)
            }
        } catch (e: Exception) {
            Log.w("WaliScoreboardRepo", "Failed to reset scoreboard: ${e.message}")
        }
    }

    private fun parseRecentGamesJson(json: String?): List<GameResultRecord> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<GameResultRecord>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                GameResultRecord.fromJson(item)?.let { list.add(it) }
            }
        } catch (e: Exception) {
            Log.w("WaliScoreboardRepo", "Error parsing recent games JSON: ${e.message}")
        }
        return list
    }

    private fun serializeRecentGamesJson(games: List<GameResultRecord>): String {
        val array = JSONArray()
        for (g in games) {
            array.put(g.toJson())
        }
        return array.toString()
    }
}

/**
 * Implémentation en mémoire pour les tests unitaires.
 */
class InMemoryWaliScoreboardRepository(
    initialStats: ScoreboardStats = ScoreboardStats(),
    initialRecent: List<GameResultRecord> = emptyList()
) : WaliScoreboardRepository {

    private var currentStats = initialStats
    private var currentRecent = initialRecent.toMutableList()

    private val _statsFlow = MutableStateFlow(currentStats)
    override val statsFlow: Flow<ScoreboardStats> = _statsFlow

    private val _recentFlow = MutableStateFlow<List<GameResultRecord>>(currentRecent)
    override val recentGamesFlow: Flow<List<GameResultRecord>> = _recentFlow

    override suspend fun getStats(): ScoreboardStats = currentStats

    override suspend fun recordGameResult(result: GameResultRecord) {
        val prevTotalGames = currentStats.totalGamesPlayed
        val prevTotalWins = currentStats.totalWins
        val prevTotalLosses = currentStats.totalLosses
        val prevDuration = currentStats.totalDurationSeconds
        val prevFastest = currentStats.fastestWinSeconds
        val prevLongest = currentStats.longestGameSeconds ?: 0L
        val prevStreak = currentStats.currentWinStreak
        val prevBestStreak = currentStats.bestWinStreak

        var newWins = prevTotalWins
        var newLosses = prevTotalLosses
        var newStreak = prevStreak
        var newBestStreak = prevBestStreak
        var newFastest = prevFastest

        if (result.outcome == GameOutcome.WIN) {
            newWins++
            newStreak++
            if (newStreak > newBestStreak) newBestStreak = newStreak
            if (newFastest == null || result.durationSeconds < newFastest) {
                newFastest = result.durationSeconds
            }
        } else if (result.outcome == GameOutcome.LOSS) {
            newLosses++
            newStreak = 0
        }

        val newLongest = if (result.durationSeconds > prevLongest) result.durationSeconds else prevLongest

        var easyWins = currentStats.easyAiWins
        var easyLosses = currentStats.easyAiLosses
        var medWins = currentStats.mediumAiWins
        var medLosses = currentStats.mediumAiLosses
        var hardWins = currentStats.hardAiWins
        var hardLosses = currentStats.hardAiLosses

        if (result.gameMode == GameMode.VS_COMPUTER) {
            when (result.aiDifficulty) {
                AiDifficulty.EASY -> {
                    if (result.outcome == GameOutcome.WIN) easyWins++ else easyLosses++
                }
                AiDifficulty.MEDIUM -> {
                    if (result.outcome == GameOutcome.WIN) medWins++ else medLosses++
                }
                AiDifficulty.HARD, null -> {
                    if (result.outcome == GameOutcome.WIN) hardWins++ else hardLosses++
                }
            }
        }

        currentStats = currentStats.copy(
            totalGamesPlayed = prevTotalGames + 1,
            totalWins = newWins,
            totalLosses = newLosses,
            totalDurationSeconds = prevDuration + result.durationSeconds,
            fastestWinSeconds = newFastest,
            longestGameSeconds = newLongest,
            currentWinStreak = newStreak,
            bestWinStreak = newBestStreak,
            vsComputerWins = if (result.gameMode == GameMode.VS_COMPUTER && result.outcome == GameOutcome.WIN) currentStats.vsComputerWins + 1 else currentStats.vsComputerWins,
            vsComputerLosses = if (result.gameMode == GameMode.VS_COMPUTER && result.outcome == GameOutcome.LOSS) currentStats.vsComputerLosses + 1 else currentStats.vsComputerLosses,
            vsComputerDurationSeconds = if (result.gameMode == GameMode.VS_COMPUTER) currentStats.vsComputerDurationSeconds + result.durationSeconds else currentStats.vsComputerDurationSeconds,
            easyAiWins = easyWins,
            easyAiLosses = easyLosses,
            mediumAiWins = medWins,
            mediumAiLosses = medLosses,
            hardAiWins = hardWins,
            hardAiLosses = hardLosses,
            onlineWins = if (result.gameMode == GameMode.ONLINE && result.outcome == GameOutcome.WIN) currentStats.onlineWins + 1 else currentStats.onlineWins,
            onlineLosses = if (result.gameMode == GameMode.ONLINE && result.outcome == GameOutcome.LOSS) currentStats.onlineLosses + 1 else currentStats.onlineLosses,
            onlineDurationSeconds = if (result.gameMode == GameMode.ONLINE) currentStats.onlineDurationSeconds + result.durationSeconds else currentStats.onlineDurationSeconds,
            twoPlayersGames = if (result.gameMode == GameMode.TWO_PLAYERS) currentStats.twoPlayersGames + 1 else currentStats.twoPlayersGames,
            twoPlayersDurationSeconds = if (result.gameMode == GameMode.TWO_PLAYERS) currentStats.twoPlayersDurationSeconds + result.durationSeconds else currentStats.twoPlayersDurationSeconds
        )

        currentRecent.add(0, result)
        _statsFlow.value = currentStats
        _recentFlow.value = currentRecent.toList()
    }

    override suspend fun resetScoreboard() {
        currentStats = ScoreboardStats()
        currentRecent.clear()
        _statsFlow.value = currentStats
        _recentFlow.value = emptyList()
    }
}
