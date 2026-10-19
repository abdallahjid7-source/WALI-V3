package com.example.wali

import com.example.wali.ads.WaliAdFrequencyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaliAdPolicyTest {

    @Test
    fun interstitialFrequency_triggersEveryTwoGames() {
        val policy = WaliAdFrequencyPolicy(gamesBetweenAds = 2)

        // Partie 1 terminée -> pas d'interstitielle
        val showAfterGame1 = policy.onGameFinished()
        assertFalse(showAfterGame1)
        assertEquals(1, policy.currentCompletedGamesCount)

        // Partie 2 terminée -> interstitielle éligible
        val showAfterGame2 = policy.onGameFinished()
        assertTrue(showAfterGame2)
        assertEquals(0, policy.currentCompletedGamesCount)

        // Partie 3 terminée -> pas d'interstitielle
        val showAfterGame3 = policy.onGameFinished()
        assertFalse(showAfterGame3)
        assertEquals(1, policy.currentCompletedGamesCount)

        // Partie 4 terminée -> interstitielle éligible
        val showAfterGame4 = policy.onGameFinished()
        assertTrue(showAfterGame4)
        assertEquals(0, policy.currentCompletedGamesCount)
    }

    @Test
    fun appOpenDelay_onlyTriggersAfterFourHoursInBackground() {
        val policy = WaliAdFrequencyPolicy(minBackgroundDurationHours = 4L)
        val now = 1_000_000_000L

        // Pas d'arrière-plan enregistré -> pas de pub
        assertFalse(policy.shouldShowAppOpenOnForeground(now))

        policy.recordBackgroundTime(now)

        // 30 minutes plus tard (1 800 000 ms) -> trop court
        val halfHourLater = now + (30 * 60 * 1000L)
        assertFalse(policy.shouldShowAppOpenOnForeground(halfHourLater))

        // 3 heures et 59 minutes plus tard -> encore trop court
        val almostFourHours = now + (3 * 3600 * 1000L) + (59 * 60 * 1000L)
        assertFalse(policy.shouldShowAppOpenOnForeground(almostFourHours))

        // 4 heures exactement -> déclenchement autorisé
        val fourHours = now + (4 * 3600 * 1000L)
        assertTrue(policy.shouldShowAppOpenOnForeground(fourHours))

        // 5 heures plus tard -> toujours éligible
        val fiveHours = now + (5 * 3600 * 1000L)
        assertTrue(policy.shouldShowAppOpenOnForeground(fiveHours))

        // Après réinitialisation / affichage -> remis à zéro
        policy.clearBackgroundTime()
        assertFalse(policy.shouldShowAppOpenOnForeground(fiveHours))
    }

    @Test
    fun safetyChecks_neverAllowAdDuringActiveGameOrThinkingOrRules() {
        val policy = WaliAdFrequencyPolicy()

        // Partie en cours -> interdit
        assertFalse(policy.canShowAd(isGameActive = true, isAiThinking = false, isSecondaryScreenOpen = false))

        // Réflexion IA en cours -> interdit
        assertFalse(policy.canShowAd(isGameActive = false, isAiThinking = true, isSecondaryScreenOpen = false))

        // Écran des règles ou des réglages ouvert -> interdit
        assertFalse(policy.canShowAd(isGameActive = false, isAiThinking = false, isSecondaryScreenOpen = true))

        // Moment neutre / pause autorisée -> permis
        assertTrue(policy.canShowAd(isGameActive = false, isAiThinking = false, isSecondaryScreenOpen = false))
    }

    @Test
    fun policyReset_clearsAllState() {
        val policy = WaliAdFrequencyPolicy(gamesBetweenAds = 2)
        policy.onGameFinished()
        policy.recordBackgroundTime(1000L)

        assertEquals(1, policy.currentCompletedGamesCount)
        policy.reset()

        assertEquals(0, policy.currentCompletedGamesCount)
        assertFalse(policy.shouldShowAppOpenOnForeground(100000000L))
    }

    @Test
    fun interstitialFrequency_acceptedRematchNeverTriggersAd() {
        val policy = WaliAdFrequencyPolicy(gamesBetweenAds = 2)
        assertFalse(policy.onGameFinished(isRematch = false))
        assertEquals(1, policy.currentCompletedGamesCount)

        // Même si le compteur est à 1, une revanche acceptée ne déclenche jamais d'interstitielle
        assertFalse(policy.onGameFinished(isRematch = true))
        assertEquals(1, policy.currentCompletedGamesCount)
    }
}
