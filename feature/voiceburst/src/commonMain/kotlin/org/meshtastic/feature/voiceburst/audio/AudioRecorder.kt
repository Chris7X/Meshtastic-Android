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
package org.meshtastic.feature.voiceburst.audio

/**
 * Interfaccia platform-agnostica per la registrazione audio.
 *
 * L'implementazione Android ([AndroidAudioRecorder]) usa [android.media.AudioRecord]
 * con parametri ottimali per Codec2:
 *   - sample rate: 8000 Hz
 *   - encoding: PCM 16-bit
 *   - canale: CHANNEL_IN_MONO
 *   - durata massima: 1000ms (MVP)
 *
 * Richiede il permesso android.permission.RECORD_AUDIO.
 * La UI deve verificare il permesso prima di chiamare [startRecording].
 */
interface AudioRecorder {

    /**
     * Avvia la registrazione.
     *
     * @param onComplete callback invocato al termine con i dati PCM e la durata effettiva.
     *                   Invocato sul thread del chiamante tramite coroutine.
     * @param onError callback invocato in caso di errore di recording.
     * @param maxDurationMs durata massima in millisecondi (default: 1000ms MVP).
     */
    fun startRecording(
        onComplete: (pcmData: ShortArray, durationMs: Int) -> Unit,
        onError: (Throwable) -> Unit,
        maxDurationMs: Int = 1000,
    )

    /**
     * Ferma anticipatamente la registrazione.
     * Se la registrazione non è in corso, è un no-op.
     * Al completamento chiama comunque [onComplete] con i dati raccolti finora.
     */
    fun stopRecording()

    /** True se una registrazione è attualmente in corso. */
    val isRecording: Boolean
}
