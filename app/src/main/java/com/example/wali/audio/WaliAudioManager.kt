package com.example.wali.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.wali.model.Player
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gestionnaire audio optimisé du jeu Wali Songhaï.
 *
 * Utilise des pistes [AudioTrack] en mémoire directe (PCM 16-bit 44.1 kHz) pour les effets
 * sonores et la boucle musicale d'ambiance :
 * - Zéro latence : échantillons PCM prêts en mémoire vive sans décodage à la volée.
 * - Évite les décodeurs matériels Codec2/CCodec du système Android, éliminant ainsi les erreurs
 *   "Failed to query component interface for required system resources" observées sur certains émulateurs.
 * - Aucune écriture de fichier temporaire sur le stockage de l'appareil.
 * - Gestion du volume sfx, musique et coupure du son (mute) avec tolérance intégrale aux pannes.
 */
class WaliAudioManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e("WaliAudioManager", "Safe fallback on uncaught audio coroutine error: ${throwable.message}", throwable)
        }
    )
) : WaliAudio {

    private val soundMap = mutableMapOf<String, PcmTrack>()

    @Volatile
    private var sfxVol: Float = 0.8f
    @Volatile
    private var musicVol: Float = 0.5f
    @Volatile
    private var isMutedState: Boolean = false

    private var musicTrack: PcmMusicTrack? = null
    @Volatile
    private var isMusicDesired: Boolean = true
    private val lock = Any()

    init {
        // Pré-génération asynchrone des flux PCM en arrière-plan sans bloquer le thread principal
        scope.launch(Dispatchers.Default) {
            try {
                initSounds()
            } catch (e: Exception) {
                Log.w("WaliAudioManager", "Safe fallback on initSounds: ${e.message}")
            }
            try {
                initMusic()
            } catch (e: Exception) {
                Log.w("WaliAudioManager", "Safe fallback on initMusic: ${e.message}")
            }
        }
    }

    private fun initSounds() {
        loadPcmSound("sfx_place_stick") { ProceduralAudioGenerator.generateStickPlace() }
        loadPcmSound("sfx_place_stone") { ProceduralAudioGenerator.generateStonePlace() }
        loadPcmSound("sfx_move") { ProceduralAudioGenerator.generateMove() }
        loadPcmSound("sfx_capture") { ProceduralAudioGenerator.generateCapture() }
        loadPcmSound("sfx_victory") { ProceduralAudioGenerator.generateVictory() }
        loadPcmSound("sfx_defeat") { ProceduralAudioGenerator.generateDefeat() }
        loadPcmSound("sfx_click") { ProceduralAudioGenerator.generateClick() }
    }

    private fun loadPcmSound(soundName: String, generator: () -> ByteArray) {
        try {
            val pcm = generator()
            val track = PcmTrack(pcm)
            synchronized(lock) {
                soundMap[soundName] = track
            }
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "Could not initialize PCM sound track for $soundName: ${e.message}")
        }
    }

    private fun initMusic() {
        try {
            val droneLoopBytes = ProceduralAudioGenerator.generateAmbientDroneLoop(4.0f)
            synchronized(lock) {
                if (musicTrack != null) return
                val track = PcmMusicTrack(droneLoopBytes)
                musicTrack = track
                if (isMusicDesired && !isMutedState) {
                    val effectiveVol = musicVol.coerceIn(0f, 1f)
                    track.start(effectiveVol)
                }
            }
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "Safe fallback on initMusic: ${e.message}")
        }
    }

    private fun playSfx(soundName: String) {
        if (isMutedState) return
        val effectiveVol = sfxVol.coerceIn(0f, 1f)
        if (effectiveVol <= 0.001f) return

        val track = synchronized(lock) { soundMap[soundName] } ?: return
        track.play(effectiveVol)
    }

    override fun playPlace(player: Player) {
        try {
            if (player == Player.STICKS) {
                playSfx("sfx_place_stick")
            } else {
                playSfx("sfx_place_stone")
            }
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "playPlace safely ignored error", e)
        }
    }

    override fun playMove() {
        try {
            playSfx("sfx_move")
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "playMove safely ignored error", e)
        }
    }

    override fun playCapture() {
        try {
            playSfx("sfx_capture")
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "playCapture safely ignored error", e)
        }
    }

    override fun playVictory() {
        try {
            playSfx("sfx_victory")
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "playVictory safely ignored error", e)
        }
    }

    override fun playDefeat() {
        try {
            playSfx("sfx_defeat")
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "playDefeat safely ignored error", e)
        }
    }

    override fun playClick() {
        try {
            playSfx("sfx_click")
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "playClick safely ignored error", e)
        }
    }

    override fun playChatMessage() {
        try {
            playSfx("sfx_click")
        } catch (e: Exception) {
            Log.w("WaliAudioManager", "playChatMessage safely ignored error", e)
        }
    }

    override fun setSfxVolume(volume: Float) {
        sfxVol = volume.coerceIn(0f, 1f)
    }

    override fun setMusicVolume(volume: Float) {
        musicVol = volume.coerceIn(0f, 1f)
        updateMusicVolumeInternal()
    }

    override fun setMuted(muted: Boolean) {
        isMutedState = muted
        updateMusicVolumeInternal()
        if (muted) {
            pauseMusic()
        } else if (isMusicDesired) {
            resumeMusic()
        }
    }

    private fun updateMusicVolumeInternal() {
        synchronized(lock) {
            val track = musicTrack ?: return
            val effectiveVol = if (isMutedState) 0f else musicVol.coerceIn(0f, 1f)
            track.setVolume(effectiveVol)
        }
    }

    override fun resumeMusic() {
        isMusicDesired = true
        if (isMutedState) return
        synchronized(lock) {
            val effectiveVol = musicVol.coerceIn(0f, 1f)
            musicTrack?.start(effectiveVol)
        }
    }

    override fun pauseMusic() {
        isMusicDesired = false
        synchronized(lock) {
            musicTrack?.pause()
        }
    }

    override fun release() {
        pauseMusic()
        synchronized(lock) {
            musicTrack?.release()
            musicTrack = null
            soundMap.values.forEach { it.release() }
            soundMap.clear()
        }
    }

    /**
     * Enveloppe un [AudioTrack] en mémoire statique pour un effet sonore court.
     */
    private class PcmTrack(pcmData: ByteArray) {
        private var track: AudioTrack? = null
        private val minSize: Int = AudioTrack.getMinBufferSize(
            ProceduralAudioGenerator.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        private val finalData: ByteArray = if (pcmData.size < minSize) {
            ByteArray(minSize).also { System.arraycopy(pcmData, 0, it, 0, pcmData.size) }
        } else {
            pcmData
        }

        init {
            createTrack()
        }

        private fun createTrack() {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ProceduralAudioGenerator.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val t = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(finalData.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                t.write(finalData, 0, finalData.size)
                track = t
            } catch (e: Throwable) {
                Log.w("WaliAudioManager", "Failed to initialize AudioTrack: ${e.message}")
            }
        }

        @Synchronized
        fun play(volume: Float) {
            var t = track
            if (t == null || t.state != AudioTrack.STATE_INITIALIZED) {
                createTrack()
                t = track ?: return
            }

            try {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    t.stop()
                }
                t.reloadStaticData()
                t.setVolume(volume.coerceIn(0f, 1f))
                t.play()
            } catch (e: Throwable) {
                Log.w("WaliAudioManager", "Failed to play AudioTrack: ${e.message}")
            }
        }

        @Synchronized
        fun release() {
            try {
                track?.stop()
                track?.release()
            } catch (_: Throwable) {}
            track = null
        }
    }

    /**
     * Enveloppe un [AudioTrack] en boucle continue pour la musique d'ambiance.
     */
    private class PcmMusicTrack(private val pcmData: ByteArray) {
        private var track: AudioTrack? = null
        private val totalFrames = pcmData.size / 2 // 16-bit mono = 2 octets par échantillon

        init {
            createTrack()
        }

        private fun createTrack() {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ProceduralAudioGenerator.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val t = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(pcmData.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                t.write(pcmData, 0, pcmData.size)
                t.setLoopPoints(0, totalFrames, -1) // Boucle infinie
                track = t
            } catch (e: Throwable) {
                Log.w("WaliAudioManager", "Failed to initialize music AudioTrack: ${e.message}")
            }
        }

        @Synchronized
        fun start(volume: Float) {
            var t = track
            if (t == null || t.state != AudioTrack.STATE_INITIALIZED) {
                createTrack()
                t = track ?: return
            }

            try {
                t.setVolume(volume.coerceIn(0f, 1f))
                if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    t.play()
                }
            } catch (e: Throwable) {
                Log.w("WaliAudioManager", "Failed to start music AudioTrack: ${e.message}")
            }
        }

        @Synchronized
        fun pause() {
            val t = track ?: return
            try {
                if (t.state == AudioTrack.STATE_INITIALIZED && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    t.pause()
                }
            } catch (e: Throwable) {
                Log.w("WaliAudioManager", "Failed to pause music AudioTrack: ${e.message}")
            }
        }

        @Synchronized
        fun setVolume(volume: Float) {
            val t = track ?: return
            try {
                if (t.state == AudioTrack.STATE_INITIALIZED) {
                    t.setVolume(volume.coerceIn(0f, 1f))
                }
            } catch (e: Throwable) {
                Log.w("WaliAudioManager", "Failed to set music AudioTrack volume: ${e.message}")
            }
        }

        @Synchronized
        fun release() {
            try {
                track?.stop()
                track?.release()
            } catch (_: Throwable) {}
            track = null
        }
    }
}
