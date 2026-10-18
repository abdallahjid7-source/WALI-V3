package com.example.wali.chat

/**
 * Limiteur de débit côté client pour prévenir le spam dans le chat multijoueur.
 *
 * Règles :
 * - Minimum 2 secondes entre deux messages consécutifs.
 * - Maximum 10 messages par minute glissante.
 */
class WaliChatRateLimiter(
    private val minIntervalMs: Long = 2000L,
    private val maxPerMinute: Int = 10,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    private var lastMessageTime: Long = 0L
    private val messageTimestampsInLastMinute = mutableListOf<Long>()

    sealed class Result {
        object Allowed : Result()
        data class TooFast(val waitSecondsRemaining: Long) : Result()
        data class QuotaExceeded(val waitSecondsRemaining: Long) : Result()
    }

    /**
     * Vérifie si un nouveau message peut être envoyé selon les quotas en vigueur.
     */
    fun canSend(): Result {
        val now = timeProvider()

        // 1. Délai minimal de 2 secondes
        val elapsedSinceLast = now - lastMessageTime
        if (lastMessageTime > 0 && elapsedSinceLast < minIntervalMs) {
            val remainingSec = ((minIntervalMs - elapsedSinceLast + 999) / 1000).coerceAtLeast(1)
            return Result.TooFast(remainingSec)
        }

        // Nettoyage des messages de plus de 60 secondes
        val oneMinuteAgo = now - 60_000L
        messageTimestampsInLastMinute.removeAll { it < oneMinuteAgo }

        // 2. Maximum 10 messages par minute
        if (messageTimestampsInLastMinute.size >= maxPerMinute) {
            val oldest = messageTimestampsInLastMinute.firstOrNull() ?: now
            val waitMs = (oldest + 60_000L) - now
            val remainingSec = ((waitMs + 999) / 1000).coerceAtLeast(1)
            return Result.QuotaExceeded(remainingSec)
        }

        return Result.Allowed
    }

    /**
     * Enregistre l'envoi effectif d'un message pour mettre à jour les compteurs.
     */
    fun recordSent() {
        val now = timeProvider()
        lastMessageTime = now
        messageTimestampsInLastMinute.add(now)
    }

    /**
     * Réinitialise les compteurs (ex: nouvelle partie).
     */
    fun reset() {
        lastMessageTime = 0L
        messageTimestampsInLastMinute.clear()
    }
}
