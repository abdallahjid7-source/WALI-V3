package com.example.wali.storage

import com.example.wali.ai.AiDifficulty
import com.example.wali.model.GameMode
import com.example.wali.model.GameState
import com.example.wali.model.Player

/**
 * Conteneur complet représentant une sauvegarde de partie en cours pour le Wali Songhaï.
 *
 * Contient à la fois l'état pur du jeu ([gameState]) et la configuration de la session
 * ([gameMode], [humanPlayer], [aiDifficulty]).
 */
data class SavedGame(
    val gameState: GameState,
    val gameMode: GameMode,
    val humanPlayer: Player = Player.STICKS,
    val aiDifficulty: AiDifficulty = AiDifficulty.EASY,
    val elapsedDurationSeconds: Long = 0L
)
