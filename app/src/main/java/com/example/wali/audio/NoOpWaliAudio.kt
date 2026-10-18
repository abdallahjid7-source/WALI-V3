package com.example.wali.audio

import com.example.wali.model.Player

/**
 * Implémentation factice de [WaliAudio] pour les tests unitaires et environnements sans audio.
 */
class NoOpWaliAudio : WaliAudio {
    var lastPlayedAction: String? = null
    var currentSfxVolume: Float = 1.0f
    var currentMusicVolume: Float = 0.5f
    var currentIsMuted: Boolean = false
    var isMusicPlaying: Boolean = false

    override fun playPlace(player: Player) {
        lastPlayedAction = if (player == Player.STICKS) "place_stick" else "place_stone"
    }

    override fun playMove() {
        lastPlayedAction = "move"
    }

    override fun playCapture() {
        lastPlayedAction = "capture"
    }

    override fun playVictory() {
        lastPlayedAction = "victory"
    }

    override fun playDefeat() {
        lastPlayedAction = "defeat"
    }

    override fun playClick() {
        lastPlayedAction = "click"
    }

    override fun playChatMessage() {
        lastPlayedAction = "chat_message"
    }

    override fun setSfxVolume(volume: Float) {
        currentSfxVolume = volume.coerceIn(0f, 1f)
    }

    override fun setMusicVolume(volume: Float) {
        currentMusicVolume = volume.coerceIn(0f, 1f)
    }

    override fun setMuted(muted: Boolean) {
        currentIsMuted = muted
    }

    override fun resumeMusic() {
        if (!currentIsMuted) isMusicPlaying = true
    }

    override fun pauseMusic() {
        isMusicPlaying = false
    }

    override fun release() {
        isMusicPlaying = false
    }
}
