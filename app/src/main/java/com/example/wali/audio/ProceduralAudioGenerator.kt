package com.example.wali.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Générateur procédural d'échantillons audio PCM 16-bit mono à 44.1 kHz.
 *
 * Permet de produire des effets sonores réalistes sans dépendre de fichiers audio distants :
 * - Clic sec boisé pour les bâtonnets
 * - Clac feutré minéral pour les cailloux
 * - Glissement doux pour le déplacement
 * - Son de capture percutant
 * - Mélodie de victoire et motif de défaite
 * - Clic de sélection
 * - Nappe d'ambiance avec pulsation méditative
 */
object ProceduralAudioGenerator {
    const val SAMPLE_RATE = 44100

    /**
     * Génère un son de pose de bâtonnet : transitoire d'impact sec avec harmonique boisé et résonance courte.
     */
    fun generateStickPlace(): ByteArray {
        val durationSec = 0.12f
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            // Enveloppe d'attaque immédiate et décroissance exponentielle rapide
            val env = Math.exp((-35.0 * t)).toFloat()
            // Fréquences caractéristiques du bois dur (440 Hz avec harmonique 880 Hz et 1320 Hz)
            val wave = (sin(2.0 * PI * 480.0 * t) * 0.6f +
                    sin(2.0 * PI * 960.0 * t) * 0.3f +
                    sin(2.0 * PI * 1440.0 * t) * 0.1f).toFloat()
            val sample = (wave * env * 32767 * 0.85f).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Génère un son de pose de caillou : choc plus sourd et mat, avec basse fréquence et composante bruitée.
     */
    fun generateStonePlace(): ByteArray {
        val durationSec = 0.15f
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = Math.exp((-25.0 * t)).toFloat()
            // Fréquences basses et bruit minéral léger
            val noise = (Math.random() * 2.0 - 1.0).toFloat() * 0.15f
            val tone = (sin(2.0 * PI * 220.0 * t) * 0.7f + sin(2.0 * PI * 340.0 * t) * 0.3f).toFloat()
            val wave = tone + noise
            val sample = (wave * env * 32767 * 0.85f).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Génère un son de déplacement : glissement doux (bruit filtré avec décroissance feutrée).
     */
    fun generateMove(): ByteArray {
        val durationSec = 0.14f
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            // Enveloppe douce (attaque progressive et relâchement)
            val env = (sin(PI * (t / durationSec))).toFloat()
            // Glissando subtil descendant
            val freq = 360.0 - 120.0 * (t / durationSec)
            val wave = (sin(2.0 * PI * freq * t) * 0.5f + (Math.random() * 2.0 - 1.0).toFloat() * 0.15f).toFloat()
            val sample = (wave * env * 32767 * 0.6f).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Génère un son de capture : claquement net suivi d'un ton distinctif.
     */
    fun generateCapture(): ByteArray {
        val durationSec = 0.22f
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = Math.exp((-18.0 * t)).toFloat()
            // Double ton énergique (587 Hz = Ré5, 880 Hz = La5)
            val tone = (sin(2.0 * PI * 587.33 * t) * 0.5f + sin(2.0 * PI * 880.0 * t) * 0.5f).toFloat()
            val sample = (tone * env * 32767 * 0.9f).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Génère un court jingle de victoire (motif pentatonique ascendant évoquant une kora ou balafon).
     * Notes : Ré4 (293 Hz), Fa#4 (370 Hz), La4 (440 Hz), Ré5 (587 Hz).
     */
    fun generateVictory(): ByteArray {
        val noteFreqs = doubleArrayOf(293.66, 369.99, 440.0, 587.33)
        val noteDur = 0.12f
        val totalSamples = (SAMPLE_RATE * noteDur * noteFreqs.size).toInt()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (noteIdx in noteFreqs.indices) {
            val freq = noteFreqs[noteIdx]
            val samplesPerNote = (SAMPLE_RATE * noteDur).toInt()
            for (i in 0 until samplesPerNote) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = Math.exp((-10.0 * t)).toFloat()
                val wave = (sin(2.0 * PI * freq * t) * 0.7f + sin(4.0 * PI * freq * t) * 0.3f).toFloat()
                val sample = (wave * env * 32767 * 0.8f).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(sample)
            }
        }
        return buffer.array()
    }

    /**
     * Génère un son de défaite : motif descendant sobre (Mi4 vers Si3 vers Sol#3).
     */
    fun generateDefeat(): ByteArray {
        val noteFreqs = doubleArrayOf(329.63, 246.94, 207.65)
        val noteDur = 0.18f
        val totalSamples = (SAMPLE_RATE * noteDur * noteFreqs.size).toInt()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (noteIdx in noteFreqs.indices) {
            val freq = noteFreqs[noteIdx]
            val samplesPerNote = (SAMPLE_RATE * noteDur).toInt()
            for (i in 0 until samplesPerNote) {
                val t = i.toFloat() / SAMPLE_RATE
                val env = Math.exp((-8.0 * t)).toFloat()
                val wave = sin(2.0 * PI * freq * t).toFloat()
                val sample = (wave * env * 32767 * 0.65f).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(sample)
            }
        }
        return buffer.array()
    }

    /**
     * Génère un clic sec pour l'interface utilisateur.
     */
    fun generateClick(): ByteArray {
        val durationSec = 0.04f
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = Math.exp((-60.0 * t)).toFloat()
            val wave = sin(2.0 * PI * 1200.0 * t).toFloat()
            val sample = (wave * env * 32767 * 0.5f).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Génère une boucle musicale méditative procédurale de 4 secondes :
     * drone atmosphérique doux en La (110 Hz et 220 Hz) avec pulsation rythmique feutrée.
     */
    fun generateAmbientDroneLoop(durationSec: Float = 4.0f): ByteArray {
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)

        val baseFreq = 110.0 // La2
        val fifthFreq = 164.81 // Mi3 (quinte parfaite méditative)
        val octaveFreq = 220.0 // La3

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            // Pulsation lente de l'atmosphère (0.5 Hz = cycle de 2 secondes)
            val lfo = (0.5f + 0.5f * sin(2.0 * PI * 0.5 * t)).toFloat()
            // Rythme discret toutes les 1 seconde
            val beatEnv = Math.exp(-6.0 * (t % 1.0)).toFloat() * 0.2f

            val drone = (sin(2.0 * PI * baseFreq * t) * 0.45f +
                    sin(2.0 * PI * fifthFreq * t) * 0.30f +
                    sin(2.0 * PI * octaveFreq * t) * 0.15f * lfo).toFloat()
            val beat = (sin(2.0 * PI * 75.0 * t) * beatEnv).toFloat()

            // Fondu parfait en début et fin de boucle pour répétition sans clic
            val fadeSamples = (SAMPLE_RATE * 0.05f).toInt()
            val edgeFade = when {
                i < fadeSamples -> i.toFloat() / fadeSamples
                i > totalSamples - fadeSamples -> (totalSamples - i).toFloat() / fadeSamples
                else -> 1.0f
            }

            val mix = (drone + beat) * edgeFade
            val sample = (mix * 32767 * 0.35f).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Convertit des octets PCM 16-bit mono en fichier WAV canonique avec en-tête RIFF.
     * Indispensable pour injecter dans [SoundPool] via un fichier temporaire ou un asset.
     */
    fun createWavFile(pcmData: ByteArray): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2

        val header = ByteArray(44)
        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // fmt sub-chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // SubChunk1Size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte() // BlockAlign
        header[33] = 0
        header[34] = 16 // BitsPerSample
        header[35] = 0
        // data sub-chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        return header + pcmData
    }
}
