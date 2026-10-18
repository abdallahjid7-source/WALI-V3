package com.example.wali.audio

import com.example.wali.model.Player

/**
 * Interface du gestionnaire audio pour le jeu Wali Songhaï.
 * Découple complètement le ViewModel des technologies d'implémentation audio Android.
 */
interface WaliAudio {
    /**
     * Joue le son de pose d'un pion (bois sec pour bâtonnet, minéral pour caillou).
     */
    fun playPlace(player: Player)

    /**
     * Joue le son de déplacement d'un pion (glissement feutré sur le sable).
     */
    fun playMove()

    /**
     * Joue le son de capture (retrait marqué d'une pièce adverse).
     */
    fun playCapture()

    /**
     * Joue le jingle de victoire (motif ascendant chaleureux).
     */
    fun playVictory()

    /**
     * Joue le son de défaite (motif descendant sobre).
     */
    fun playDefeat()

    /**
     * Joue le clic des boutons de menu / navigation.
     */
    fun playClick()

    /**
     * Joue le son discret de notification lors de la réception d'un message dans le chat.
     */
    fun playChatMessage()

    /**
     * Met à jour le volume des effets sonores (0.0f à 1.0f).
     */
    fun setSfxVolume(volume: Float)

    /**
     * Met à jour le volume de la musique d'ambiance (0.0f à 1.0f).
     */
    fun setMusicVolume(volume: Float)

    /**
     * Active ou désactive le mode muet général.
     */
    fun setMuted(muted: Boolean)

    /**
     * Démarre ou reprend la musique de fond.
     */
    fun resumeMusic()

    /**
     * Met en pause la musique de fond (arrière-plan du cycle de vie).
     */
    fun pauseMusic()

    /**
     * Libère les ressources audio allouées.
     */
    fun release()
}
