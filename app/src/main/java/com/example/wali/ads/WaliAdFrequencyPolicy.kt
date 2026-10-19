package com.example.wali.ads

/**
 * Gestionnaire pur de la politique de fréquence et de sécurité d'affichage des publicités AdMob.
 *
 * Découplé du SDK Google pour permettre des tests unitaires complets et fiables :
 * - Limite de fréquence des annonces interstitielles (toutes les 2 à 3 parties terminées).
 * - Délai minimal en arrière-plan avant réaffichage d'une annonce App Open (recommandation Google : 4 heures).
 * - Sécurité d'affichage : interdiction formelle pendant une partie active, un calcul IA ou la consultation des règles.
 */
class WaliAdFrequencyPolicy(
    val gamesBetweenAds: Int = 2,
    val minBackgroundDurationHours: Long = 4L
) {
    private var completedGamesCount: Int = 0
    private var lastBackgroundTimestamp: Long = 0L

    /**
     * Nombre actuel de parties terminées depuis la dernière interstitielle affichée.
     */
    val currentCompletedGamesCount: Int
        get() = completedGamesCount

    /**
     * Appelé dès qu'une partie arrive à son terme (victoire ou blocage).
     * Aucune interstitielle ne s'affiche entre la fin d'une partie et le lancement d'une revanche acceptée ([isRematch] = true).
     *
     * @return `true` si le seuil de parties requis est atteint et qu'une interstitielle est éligible, `false` sinon.
     */
    fun onGameFinished(isRematch: Boolean = false): Boolean {
        if (isRematch) return false
        completedGamesCount++
        return if (completedGamesCount >= gamesBetweenAds) {
            completedGamesCount = 0
            true
        } else {
            false
        }
    }

    /**
     * Enregistre le timestamp auquel l'application bascule en arrière-plan.
     */
    fun recordBackgroundTime(timestamp: Long = System.currentTimeMillis()) {
        lastBackgroundTimestamp = timestamp
    }

    /**
     * Réinitialise le marqueur d'arrière-plan une fois l'annonce App Open traitée.
     */
    fun clearBackgroundTime() {
        lastBackgroundTimestamp = 0L
    }

    /**
     * Détermine si une pub App Open doit être affichée lors du retour au premier plan.
     *
     * Google préconise de n'afficher l'App Open qu'après une absence prolongée (4 heures minimum),
     * pour éviter d'interrompre un utilisateur qui répond simplement à une notification ou change brièvement d'app.
     */
    fun shouldShowAppOpenOnForeground(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (lastBackgroundTimestamp <= 0L) return false
        val elapsedMillis = currentTime - lastBackgroundTimestamp
        val requiredMillis = minBackgroundDurationHours * 60L * 60L * 1000L
        return elapsedMillis >= requiredMillis
    }

    /**
     * Vérifie les conditions de sécurité absolues pour autoriser une coupure publicitaire :
     * jamais pendant un tour en cours, un calcul d'IA ou la lecture des règles.
     */
    fun canShowAd(
        isGameActive: Boolean,
        isAiThinking: Boolean,
        isSecondaryScreenOpen: Boolean
    ): Boolean {
        return !isGameActive && !isAiThinking && !isSecondaryScreenOpen
    }

    /**
     * Réinitialise les compteurs (utile pour les tests unitaires et sessions de jeu).
     */
    fun reset() {
        completedGamesCount = 0
        lastBackgroundTimestamp = 0L
    }
}
