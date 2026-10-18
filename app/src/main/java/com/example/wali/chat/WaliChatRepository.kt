package com.example.wali.chat

import kotlinx.coroutines.flow.Flow

/**
 * Interface découplée pour la synchronisation des messages du chat multijoueur.
 */
interface WaliChatRepository {

    /**
     * Écoute en temps réel les messages de la partie identifiée par [gameCode].
     */
    fun observeMessages(gameCode: String): Flow<List<WaliChatMessage>>

    /**
     * Envoie un message dans la partie [gameCode].
     *
     * Retourne un [Result] pour une gestion d'erreur douce sans exception non interceptée.
     */
    suspend fun sendMessage(gameCode: String, message: WaliChatMessage): Result<Unit>

    /**
     * Enregistre un signalement d'utilisateur dans Firestore.
     */
    suspend fun submitReport(report: WaliReport): Result<Unit>

    /**
     * Nettoie les messages de la salle fermée (si autorisée côté client).
     */
    suspend fun clearMessages(gameCode: String): Result<Unit>
}
