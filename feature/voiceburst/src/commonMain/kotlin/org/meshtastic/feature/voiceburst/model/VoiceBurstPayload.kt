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
package org.meshtastic.feature.voiceburst.model

/**
 * Payload di un Voice Burst pronto per la trasmissione o appena ricevuto.
 *
 * Dimensioni target MVP:
 *   - audioData: ~88 bytes (Codec2 700B, 1 secondo a 700 bps)
 *   - overhead metadata: ~12 bytes
 *   - totale: < 120 bytes → entra in un singolo MeshPacket (max ~240 bytes)
 *
 * PortNum: PRIVATE_APP = 256 (provvisorio — open question nel PRD)
 * TODO: definire proto ufficiale o richiedere portnum registrato upstream.
 *
 * Serializzazione MVP: bytes raw prefissati con un header minimo a lunghezza fissa:
 *   [1 byte version=1][1 byte codecMode][2 bytes durationMs][N bytes audioData]
 * Questo evita la dipendenza da protobuf aggiuntivo nel modulo per MVP.
 */
data class VoiceBurstPayload(
    /**
     * Versione del formato del payload.
     * Incrementare se il formato cambia, per permettere graceful degradation.
     */
    val version: Byte = 1,

    /**
     * Modalità codec usata per l'encoding.
     * 0 = Codec2 700B (unico valore supportato in MVP)
     * TODO: mappare a enum Codec2Mode quando disponibile.
     */
    val codecMode: Byte = 0,

    /**
     * Durata effettiva dell'audio registrato, in millisecondi.
     * MVP: sempre ≤ 1000ms.
     */
    val durationMs: Short,

    /**
     * Bytes audio compressi con Codec2.
     * MVP: ~88 bytes per 1 secondo a 700B.
     */
    val audioData: ByteArray,

    /**
     * ID del nodo mittente (usato lato receiver per il display).
     * Popolato dal receiver con il from del DataPacket.
     */
    val senderNodeId: String = "",
) {

    /**
     * Serializza il payload in un ByteArray da inserire in [DataPacket.bytes].
     * Formato: [version:1][codecMode:1][durationMs:2 BE][audioData:N]
     */
    fun encode(): ByteArray {
        val buf = ByteArray(4 + audioData.size)
        buf[0] = version
        buf[1] = codecMode
        buf[2] = ((durationMs.toInt() shr 8) and 0xFF).toByte()
        buf[3] = (durationMs.toInt() and 0xFF).toByte()
        audioData.copyInto(buf, destinationOffset = 4)
        return buf
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceBurstPayload) return false
        return version == other.version &&
            codecMode == other.codecMode &&
            durationMs == other.durationMs &&
            audioData.contentEquals(other.audioData) &&
            senderNodeId == other.senderNodeId
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + codecMode.toInt()
        result = 31 * result + durationMs.toInt()
        result = 31 * result + audioData.contentHashCode()
        result = 31 * result + senderNodeId.hashCode()
        return result
    }

    companion object {
        /** PortNum provvisorio per MVP. PRIVATE_APP = 256. */
        const val PORT_NUM = 256

        /** Durata massima supportata in MVP (1 secondo). */
        const val MAX_DURATION_MS = 1000

        /**
         * Deserializza un payload ricevuto da un [DataPacket].
         * Restituisce null se il formato non è riconoscibile o la versione non è supportata.
         */
        fun decode(bytes: ByteArray, senderNodeId: String = ""): VoiceBurstPayload? {
            if (bytes.size < 5) return null // minimo: header 4 bytes + 1 byte audio
            val version = bytes[0]
            if (version != 1.toByte()) return null // versione non supportata
            val codecMode = bytes[1]
            val durationMs = (((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)).toShort()
            val audioData = bytes.copyOfRange(4, bytes.size)
            return VoiceBurstPayload(
                version = version,
                codecMode = codecMode,
                durationMs = durationMs,
                audioData = audioData,
                senderNodeId = senderNodeId,
            )
        }
    }
}
