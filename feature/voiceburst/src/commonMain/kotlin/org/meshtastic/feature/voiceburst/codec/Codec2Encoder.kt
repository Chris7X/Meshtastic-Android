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

/**
 * Interfaccia platform-agnostica per l'encoding/decoding Codec2.
 *
 * L'implementazione Android ([AndroidCodec2Encoder]) usa JNI + libcodec2.
 * Se la libreria non è disponibile ([isStub]=true), cade in modalità stub
 * (sinusoide 440Hz) per permettere sviluppo e CI senza la .so.
 *
 * Implementa [AutoCloseable]: chiamare [close()] (o usare `use {}`) per
 * rilasciare lo stato JNI quando il codec non serve più.
 */
interface Codec2Encoder : AutoCloseable {

    /**
     * Encode di un buffer PCM 16-bit mono 8kHz in bytes Codec2 700B.
     *
     * @param pcmData array di short PCM (16-bit, mono, 8000 Hz)
     * @return bytes compressi Codec2, oppure null in caso di errore
     *
     * Dimensioni attese:
     *   input:  8000 samples/s × 1s = 8000 shorts = 16000 bytes PCM
     *   output: ~88 bytes Codec2 700B per 1 secondo
     */
    fun encode(pcmData: ShortArray): ByteArray?

    /**
     * Decode di bytes Codec2 700B in PCM 16-bit mono 8kHz.
     *
     * @param codec2Data bytes compressi
     * @return array di short PCM, oppure null in caso di errore
     */
    fun decode(codec2Data: ByteArray): ShortArray?

    /**
     * Indica se questa implementazione è funzionante (libreria disponibile)
     * o è uno stub.
     */
    val isStub: Boolean
}
