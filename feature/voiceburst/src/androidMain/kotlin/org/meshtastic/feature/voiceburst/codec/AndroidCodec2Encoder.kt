/*
 * Copyright (c) 2026 Chris7X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.voiceburst.codec

import com.geeksville.mesh.voiceburst.Codec2JNI

import co.touchlab.kermit.Logger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "AndroidCodec2Encoder"

/**
 * Implementazione Android di [Codec2Encoder].
 *
 * Quando [Codec2JNI.isAvailable] = true usa libcodec2 via JNI (audio vocale reale).
 * Altrimenti cade in modalità STUB (sinusoide 440Hz) per sviluppo/CI/build senza .so.
 *
 * Codec2 700B parametri:
 *   - Sample rate input:  8000 Hz
 *   - Frame:              40ms = 320 campioni
 *   - Bytes per frame:    4
 *   - 1 secondo:          25 frame × 4 bytes = 100 bytes
 *
 * Preprocessing applicato prima dell'encode (solo modalità JNI):
 *   1. Normalizzazione ampiezza (porta al 70% di Short.MAX_VALUE)
 *   2. VAD semplice: se RMS < soglia, ritorna silenzio senza encode
 *
 * Lifecycle JNI:
 *   L'handle Codec2 viene creato nel costruttore e distrutto in [close()].
 *   Usare [use { }] o chiamare [close()] esplicitamente.
 */
class AndroidCodec2Encoder : Codec2Encoder, AutoCloseable {

    private val codec2Handle: Long
    override val isStub: Boolean

    init {
        Codec2JNI.ensureLoaded()
        if (Codec2JNI.isAvailable) {
            val handle = Codec2JNI.create(Codec2JNI.MODE_700C)
            if (handle != 0L) {
                codec2Handle = handle
                isStub = false
                Logger.i(TAG) {
                    "Codec2 JNI OK: samplesPerFrame=${Codec2JNI.getSamplesPerFrame(Codec2JNI.MODE_700C)}" +
                        " bytesPerFrame=${Codec2JNI.getBytesPerFrame(Codec2JNI.MODE_700C)}"
                }
            } else {
                Logger.e(TAG) { "Codec2JNI.create() ritornato 0 — cado in modalità stub" }
                codec2Handle = 0L
                isStub = true
            }
        } else {
            codec2Handle = 0L
            isStub = true
            Logger.w(TAG) { "Codec2 JNI non disponibile — modalità stub (sinusoide 440Hz)" }
        }
    }

    override fun close() {
        if (codec2Handle != 0L) {
            Codec2JNI.destroy(codec2Handle)
            Logger.d(TAG) { "Codec2 handle rilasciato" }
        }
    }

    // ─── encode ───────────────────────────────────────────────────────────────

    /**
     * Codifica PCM 16-bit mono 8000Hz in bytes Codec2 700B.
     *
     * Accetta un array di qualsiasi lunghezza — viene suddiviso in frame
     * da [SAMPLES_PER_FRAME] campioni. L'ultimo frame incompleto viene
     * completato con zeri (zero-padding).
     *
     * @param pcmData  Campioni PCM dal microfono (8000 Hz, mono, signed 16-bit)
     * @return         ByteArray con i bytes Codec2, null se input vuoto
     */
    override fun encode(pcmData: ShortArray): ByteArray? {
        if (pcmData.isEmpty()) return null

        return if (!isStub && codec2Handle != 0L) {
            encodeJni(pcmData)
        } else {
            encodeStub(pcmData)
        }
    }

    private fun encodeJni(pcmData: ShortArray): ByteArray? {
        val samplesPerFrame = Codec2JNI.getSamplesPerFrame(Codec2JNI.MODE_700C)
        val bytesPerFrame   = Codec2JNI.getBytesPerFrame(Codec2JNI.MODE_700C)

        // Preprocessing: normalizzazione
        val normalized = normalize(pcmData)

        // VAD: non inviare silenzio
        val rms = computeRms(normalized)
        if (rms < SILENCE_RMS_THRESHOLD) {
            Logger.d(TAG) { "VAD: silenzio rilevato (RMS=$rms) — skip encode" }
            return ByteArray(0)
        }

        // Calcola il numero di frame necessari (arrotonda in su)
        val frameCount = (normalized.size + samplesPerFrame - 1) / samplesPerFrame
        val output = ByteArray(frameCount * bytesPerFrame)
        var outOffset = 0

        for (frameIdx in 0 until frameCount) {
            val inStart = frameIdx * samplesPerFrame
            val inEnd   = minOf(inStart + samplesPerFrame, normalized.size)

            // Estrai frame (con zero-padding se incompleto)
            val frame = if (inEnd - inStart == samplesPerFrame) {
                normalized.copyOfRange(inStart, inEnd)
            } else {
                ShortArray(samplesPerFrame).also {
                    normalized.copyInto(it, 0, inStart, inEnd)
                    // restante già 0 per default
                }
            }

            val encoded = Codec2JNI.encode(codec2Handle, frame)
            if (encoded == null || encoded.size != bytesPerFrame) {
                Logger.e(TAG) { "Encode fallito al frame $frameIdx" }
                return null
            }

            encoded.copyInto(output, outOffset)
            outOffset += bytesPerFrame
        }

        Logger.d(TAG) {
            "Encode JNI: ${pcmData.size} campioni → ${output.size} bytes " +
                "($frameCount frame × $bytesPerFrame bytes)"
        }
        return output
    }

    // ─── decode ───────────────────────────────────────────────────────────────

    /**
     * Decodifica bytes Codec2 700B in campioni PCM 16-bit mono 8000Hz.
     *
     * @param codec2Data  ByteArray di bytes Codec2 (multiplo di bytesPerFrame)
     * @return            ShortArray di campioni PCM, null se input vuoto/invalido
     */
    override fun decode(codec2Data: ByteArray): ShortArray? {
        if (codec2Data.isEmpty()) return null

        return if (!isStub && codec2Handle != 0L) {
            decodeJni(codec2Data)
        } else {
            decodeStub(codec2Data)
        }
    }

    private fun decodeJni(codec2Data: ByteArray): ShortArray? {
        val samplesPerFrame = Codec2JNI.getSamplesPerFrame(Codec2JNI.MODE_700C)
        val bytesPerFrame   = Codec2JNI.getBytesPerFrame(Codec2JNI.MODE_700C)

        if (codec2Data.size % bytesPerFrame != 0) {
            Logger.w(TAG) {
                "Decode: dimensione input (${codec2Data.size}) non multipla di " +
                    "bytesPerFrame ($bytesPerFrame) — troncamento al frame completo"
            }
        }

        val frameCount = codec2Data.size / bytesPerFrame
        if (frameCount == 0) return null

        val output = ShortArray(frameCount * samplesPerFrame)
        var outOffset = 0

        for (frameIdx in 0 until frameCount) {
            val inStart = frameIdx * bytesPerFrame
            val frame   = codec2Data.copyOfRange(inStart, inStart + bytesPerFrame)

            val decoded = Codec2JNI.decode(codec2Handle, frame)
            if (decoded == null || decoded.size != samplesPerFrame) {
                Logger.e(TAG) { "Decode fallito al frame $frameIdx" }
                return null
            }

            decoded.copyInto(output, outOffset)
            outOffset += samplesPerFrame
        }

        Logger.d(TAG) {
            "Decode JNI: ${codec2Data.size} bytes → ${output.size} campioni " +
                "($frameCount frame × $samplesPerFrame campioni)"
        }
        return output
    }

    // ─── Preprocessing helpers ────────────────────────────────────────────────

    /**
     * Normalizza l'ampiezza del segnale a [TARGET_AMPLITUDE] × Short.MAX_VALUE.
     * Previene clipping e migliora la qualità Codec2 su voci basse.
     */
    private fun normalize(pcm: ShortArray): ShortArray {
        val maxAmp = pcm.maxOfOrNull { abs(it.toInt()) }?.toFloat() ?: return pcm
        if (maxAmp < 1f) return pcm  // silenzio assoluto

        val gain = (TARGET_AMPLITUDE * Short.MAX_VALUE) / maxAmp
        // Limita il gain massimo a 10x per evitare amplificazione eccessiva del rumore
        val clampedGain = minOf(gain, MAX_GAIN)

        return ShortArray(pcm.size) { i ->
            (pcm[i] * clampedGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Calcola il Root Mean Square del segnale.
     * Usato per il VAD semplice: RMS < [SILENCE_RMS_THRESHOLD] = silenzio.
     */
    private fun computeRms(pcm: ShortArray): Double {
        if (pcm.isEmpty()) return 0.0
        val sumSquares = pcm.fold(0.0) { acc, s -> acc + (s.toDouble() * s.toDouble()) }
        return sqrt(sumSquares / pcm.size)
    }

    // ─── Stub (fallback quando JNI non è disponibile) ─────────────────────────

    private fun encodeStub(pcmData: ShortArray): ByteArray {
        val frameCount = (pcmData.size + SAMPLES_PER_FRAME - 1) / SAMPLES_PER_FRAME
        Logger.w(TAG) {
            "Codec2 STUB encode: ${pcmData.size} campioni → ${frameCount * BYTES_PER_FRAME} bytes (zeri)"
        }
        return ByteArray(frameCount * BYTES_PER_FRAME) { 0x00 }
    }

    private fun decodeStub(codec2Data: ByteArray): ShortArray {
        val frameCount = maxOf(1, codec2Data.size / BYTES_PER_FRAME)
        val totalSamples = frameCount * SAMPLES_PER_FRAME

        Logger.w(TAG) {
            "Codec2 STUB decode: ${codec2Data.size} bytes → $totalSamples campioni (sinusoide 440Hz)"
        }

        // Genera sinusoide 440Hz (La4) — udibile e riconoscibile
        val sampleRate = 8000.0
        val frequency  = 440.0
        val amplitude  = Short.MAX_VALUE * 0.3  // 30% volume

        return ShortArray(totalSamples) { i ->
            val angle = 2.0 * PI * frequency * i / sampleRate
            (sin(angle) * amplitude).toInt().toShort()
        }
    }

    companion object {
        /** Codec2 700B: 320 campioni per frame (40ms @ 8000 Hz). */
        const val SAMPLES_PER_FRAME = 320

        /** Codec2 700B: 4 bytes per frame (700 bps arrotondati). */
        const val BYTES_PER_FRAME = 4

        /** Ampiezza target per la normalizzazione (70% di Short.MAX_VALUE). */
        private const val TARGET_AMPLITUDE = 0.70f

        /** Gain massimo applicato dalla normalizzazione (10×). */
        private const val MAX_GAIN = 10.0f

        /**
         * Soglia RMS sotto cui il frame viene considerato silenzio (VAD semplice).
         * 200.0 su scala 0-32767 è circa -44 dBFS — voce normale è 2000-8000.
         */
        private const val SILENCE_RMS_THRESHOLD = 200.0
    }
}
