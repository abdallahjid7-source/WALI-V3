package com.example.wali.storage

import com.example.wali.ai.AiDifficulty
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Line
import com.example.wali.model.Phase
import com.example.wali.model.Player
import com.example.wali.model.Position
import com.example.wali.model.WaliRuleOptions
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sérialiseur / désérialiseur JSON pour la sauvegarde de l'état d'une partie de Wali Songhaï.
 *
 * Garantit la préservation exacte de l'ensemble des données de jeu :
 * - Grille complète de 30 cases (cases occupées et cases vides)
 * - Joueur actif, phase, pions restants
 * - Vainqueur éventuel
 * - Ensemble des lignes déjà utilisées pour une capture ([GameState.usedLines])
 * - Historique textuel complet des coups ([GameState.moveHistory])
 * - Nombre de captures en attente ([GameState.pendingCaptures])
 * - Paramètres de partie : mode (2 joueurs ou machine), camp humain, niveau de difficulté IA
 */
object WaliGameSerializer {

    private const val CURRENT_VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_GAME_MODE = "gameMode"
    private const val KEY_HUMAN_PLAYER = "humanPlayer"
    private const val KEY_AI_DIFFICULTY = "aiDifficulty"
    private const val KEY_ELAPSED_DURATION_SECONDS = "elapsedDurationSeconds"

    private const val KEY_GAME_STATE = "gameState"
    private const val KEY_BOARD = "board"
    private const val KEY_CURRENT_PLAYER = "currentPlayer"
    private const val KEY_PHASE = "phase"
    private const val KEY_STICKS_REMAINING = "sticksRemaining"
    private const val KEY_STONES_REMAINING = "stonesRemaining"
    private const val KEY_WINNER = "winner"
    private const val KEY_USED_LINES = "usedLines"
    private const val KEY_MOVE_HISTORY = "moveHistory"
    private const val KEY_PENDING_CAPTURES = "pendingCaptures"
    private const val KEY_STICKS_PENDING_PLACEMENT = "sticksPendingPlacement"
    private const val KEY_STONES_PENDING_PLACEMENT = "stonesPendingPlacement"
    private const val KEY_MANDATORY_ALIGNMENT = "mandatoryAlignment"
    private const val KEY_DEFEAT_AT_TWO_PIECES = "defeatAtTwoPieces"

    /**
     * Sérialise un [GameState] brut en chaîne JSON formatée (mode en ligne).
     */
    fun serializeGameState(gameState: GameState): String {
        return serialize(
            SavedGame(
                gameState = gameState,
                gameMode = GameMode.ONLINE,
                humanPlayer = Player.STICKS,
                aiDifficulty = AiDifficulty.EASY
            )
        )
    }

    /**
     * Désérialise une chaîne JSON pour reconstituer un [GameState] (mode en ligne).
     */
    fun deserializeGameState(jsonString: String?): GameState? {
        return deserialize(jsonString)?.gameState
    }

    /**
     * Sérialise un [SavedGame] en chaîne de caractères JSON formatée.
     */
    fun serialize(savedGame: SavedGame): String {
        val root = JSONObject()
        root.put(KEY_VERSION, CURRENT_VERSION)
        root.put(KEY_GAME_MODE, savedGame.gameMode.name)
        root.put(KEY_HUMAN_PLAYER, savedGame.humanPlayer.name)
        root.put(KEY_AI_DIFFICULTY, savedGame.aiDifficulty.name)
        root.put(KEY_ELAPSED_DURATION_SECONDS, savedGame.elapsedDurationSeconds)

        val stateObj = JSONObject()
        val gs = savedGame.gameState

        // 1. Plateau : on enregistre les cases occupées
        val boardObj = JSONObject()
        for ((pos, player) in gs.board) {
            if (player != null) {
                boardObj.put(pos.algebraic, player.name)
            }
        }
        stateObj.put(KEY_BOARD, boardObj)

        stateObj.put(KEY_CURRENT_PLAYER, gs.currentPlayer.name)
        stateObj.put(KEY_PHASE, gs.phase.name)
        stateObj.put(KEY_STICKS_REMAINING, gs.sticksRemaining)
        stateObj.put(KEY_STONES_REMAINING, gs.stonesRemaining)

        if (gs.winner != null) {
            stateObj.put(KEY_WINNER, gs.winner.name)
        } else {
            stateObj.put(KEY_WINNER, JSONObject.NULL)
        }

        // 2. Lignes utilisées
        val linesArray = JSONArray()
        for (line in gs.usedLines) {
            val cellArray = JSONArray()
            line.cells.sortedWith(compareBy({ it.row }, { it.col })).forEach { pos ->
                cellArray.put(pos.algebraic)
            }
            linesArray.put(cellArray)
        }
        stateObj.put(KEY_USED_LINES, linesArray)

        // 3. Historique des coups
        val historyArray = JSONArray()
        for (record in gs.moveHistory) {
            historyArray.put(record)
        }
        stateObj.put(KEY_MOVE_HISTORY, historyArray)

        stateObj.put(KEY_PENDING_CAPTURES, gs.pendingCaptures)
        stateObj.put(KEY_STICKS_PENDING_PLACEMENT, gs.sticksPendingPlacement)
        stateObj.put(KEY_STONES_PENDING_PLACEMENT, gs.stonesPendingPlacement)
        stateObj.put(KEY_MANDATORY_ALIGNMENT, gs.ruleOptions.mandatoryAlignment)
        stateObj.put(KEY_DEFEAT_AT_TWO_PIECES, gs.ruleOptions.defeatAtTwoPieces)

        root.put(KEY_GAME_STATE, stateObj)
        return root.toString()
    }

    /**
     * Désérialise une chaîne JSON en instance de [SavedGame].
     * Retourne null si le format est invalide ou corrompu.
     */
    fun deserialize(jsonString: String?): SavedGame? {
        if (jsonString.isNullOrBlank()) return null

        return try {
            val root = JSONObject(jsonString)

            val gameMode = GameMode.valueOf(root.getString(KEY_GAME_MODE))
            val humanPlayer = if (root.has(KEY_HUMAN_PLAYER)) {
                Player.valueOf(root.getString(KEY_HUMAN_PLAYER))
            } else {
                Player.STICKS
            }
            val aiDifficulty = if (root.has(KEY_AI_DIFFICULTY)) {
                AiDifficulty.valueOf(root.getString(KEY_AI_DIFFICULTY))
            } else {
                AiDifficulty.EASY
            }
            val elapsedDurationSeconds = root.optLong(KEY_ELAPSED_DURATION_SECONDS, 0L)

            val stateObj = root.getJSONObject(KEY_GAME_STATE)

            // Reconstitution du plateau complet (30 cases, null par défaut)
            val boardMap = Position.allPositions.associateWith { null as Player? }.toMutableMap()
            val boardObj = stateObj.getJSONObject(KEY_BOARD)
            val keys = boardObj.keys()
            while (keys.hasNext()) {
                val algebraic = keys.next()
                val playerStr = boardObj.getString(algebraic)
                val pos = Position.fromAlgebraic(algebraic)
                val player = Player.valueOf(playerStr)
                boardMap[pos] = player
            }

            val currentPlayer = Player.valueOf(stateObj.getString(KEY_CURRENT_PLAYER))
            val phase = Phase.valueOf(stateObj.getString(KEY_PHASE))
            val sticksRemaining = stateObj.getInt(KEY_STICKS_REMAINING)
            val stonesRemaining = stateObj.getInt(KEY_STONES_REMAINING)

            val winner = if (stateObj.has(KEY_WINNER) && !stateObj.isNull(KEY_WINNER)) {
                Player.valueOf(stateObj.getString(KEY_WINNER))
            } else {
                null
            }

            // Reconstitution des lignes déjà utilisées
            val usedLines = mutableSetOf<Line>()
            if (stateObj.has(KEY_USED_LINES)) {
                val linesArray = stateObj.getJSONArray(KEY_USED_LINES)
                for (i in 0 until linesArray.length()) {
                    val cellArray = linesArray.getJSONArray(i)
                    val positions = mutableSetOf<Position>()
                    for (j in 0 until cellArray.length()) {
                        positions.add(Position.fromAlgebraic(cellArray.getString(j)))
                    }
                    if (positions.size == 3) {
                        usedLines.add(Line(positions))
                    }
                }
            }

            // Reconstitution de l'historique
            val moveHistory = mutableListOf<String>()
            if (stateObj.has(KEY_MOVE_HISTORY)) {
                val historyArray = stateObj.getJSONArray(KEY_MOVE_HISTORY)
                for (i in 0 until historyArray.length()) {
                    moveHistory.add(historyArray.getString(i))
                }
            }

            val pendingCaptures = if (stateObj.has(KEY_PENDING_CAPTURES)) {
                stateObj.getInt(KEY_PENDING_CAPTURES)
            } else {
                0
            }

            val sticksPendingPlacement = if (stateObj.has(KEY_STICKS_PENDING_PLACEMENT)) {
                stateObj.getInt(KEY_STICKS_PENDING_PLACEMENT)
            } else {
                0
            }

            val stonesPendingPlacement = if (stateObj.has(KEY_STONES_PENDING_PLACEMENT)) {
                stateObj.getInt(KEY_STONES_PENDING_PLACEMENT)
            } else {
                0
            }

            val mandatoryAlignment = if (stateObj.has(KEY_MANDATORY_ALIGNMENT)) {
                stateObj.getBoolean(KEY_MANDATORY_ALIGNMENT)
            } else {
                false
            }

            val defeatAtTwoPieces = if (stateObj.has(KEY_DEFEAT_AT_TWO_PIECES)) {
                stateObj.getBoolean(KEY_DEFEAT_AT_TWO_PIECES)
            } else {
                false
            }

            val gameState = GameState(
                board = boardMap,
                currentPlayer = currentPlayer,
                phase = phase,
                sticksRemaining = sticksRemaining,
                stonesRemaining = stonesRemaining,
                winner = winner,
                usedLines = usedLines,
                moveHistory = moveHistory,
                pendingCaptures = pendingCaptures,
                sticksPendingPlacement = sticksPendingPlacement,
                stonesPendingPlacement = stonesPendingPlacement,
                ruleOptions = WaliRuleOptions(
                    mandatoryAlignment = mandatoryAlignment,
                    defeatAtTwoPieces = defeatAtTwoPieces
                )
            )

            SavedGame(
                gameState = gameState,
                gameMode = gameMode,
                humanPlayer = humanPlayer,
                aiDifficulty = aiDifficulty,
                elapsedDurationSeconds = elapsedDurationSeconds
            )
        } catch (e: Exception) {
            null
        }
    }
}
