package com.example.wali.chat

import com.example.wali.model.Player

/**
 * Type d'un message dans le chat de partie en ligne.
 */
enum class ChatMessageType {
    FREE_TEXT,
    QUICK_MESSAGE,
    SYSTEM
}

/**
 * Modèle de données immuable représentant un message de chat dans une partie en ligne.
 *
 * Conforme aux exigences :
 * - Expéditeur identifié par son rôle de joueur (STICKS ou STONES) et son UID Firebase.
 * - Texte limité à 200 caractères.
 * - Horodatage millisecondes.
 * - Éphémère (non sauvegardé dans DataStore).
 */
data class WaliChatMessage(
    val id: String = "",
    val senderRole: Player? = null,
    val senderUid: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: ChatMessageType = ChatMessageType.FREE_TEXT
) {
    companion object {
        const val MAX_TEXT_LENGTH = 200

        /**
         * Messages rapides prédéfinis accessibles en un tap.
         */
        val QUICK_MESSAGES = listOf(
            "Bonjour !",
            "Bien joué !",
            "Belle capture !",
            "Je réfléchis…",
            "Dépêche-toi 🙂",
            "Merci pour la partie !",
            "Revanche ?"
        )

        /**
         * Valide un texte de message libre :
         * - Ne doit pas être vide ni composé uniquement d'espaces.
         * - Ne doit pas dépasser [MAX_TEXT_LENGTH] caractères.
         */
        fun validateText(rawText: String?): String? {
            if (rawText == null) return null
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.length > MAX_TEXT_LENGTH) return null
            return trimmed
        }
    }
}

/**
 * Modèle de données pour un signalement d'adversaire abusif dans Firestore.
 */
data class WaliReport(
    val reportId: String = "",
    val gameCode: String = "",
    val reportedUid: String = "",
    val reportedRole: String = "",
    val reporterUid: String = "",
    val reporterRole: String = "",
    val reason: String = "",
    val recentMessages: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
