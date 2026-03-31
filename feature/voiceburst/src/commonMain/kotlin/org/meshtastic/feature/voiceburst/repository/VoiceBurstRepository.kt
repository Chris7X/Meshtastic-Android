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
package org.meshtastic.feature.voiceburst.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.feature.voiceburst.model.VoiceBurstPayload

/**
 * Interfaccia platform-agnostica per l'invio e la ricezione di Voice Burst.
 *
 * L'implementazione Android ([AndroidVoiceBurstRepository]) usa [RadioController]
 * per inviare [DataPacket] con dataType = [VoiceBurstPayload.PORT_NUM].
 */
interface VoiceBurstRepository {

    /**
     * Feature flag: Voice Burst experimental abilitato dall'utente.
     * Default: false. Leggibile come StateFlow per reattività nella UI.
     */
    val isFeatureEnabled: StateFlow<Boolean>

    /**
     * Abilita o disabilita la feature Voice Burst.
     * Persiste in DataStore.
     */
    suspend fun setFeatureEnabled(enabled: Boolean)

    /**
     * Invia un [VoiceBurstPayload] al nodo destinatario via BLE/RadioController
     * e lo salva nel DB locale per mostrarlo nella chat.
     *
     * @param payload    il payload già encodato
     * @param contactKey chiave contatto nel formato "<channel>!<nodeId>" (es. "0!42424243", "8!42424243")
     * @return true se il pacchetto è stato consegnato al RadioController, false altrimenti
     */
    suspend fun sendBurst(payload: VoiceBurstPayload, contactKey: String): Boolean

    /**
     * Flow di burst ricevuti da altri nodi.
     * Emette ogni volta che arriva un DataPacket con PORT_NUM = 256 e
     * il payload è decodificabile.
     */
    val incomingBursts: Flow<VoiceBurstPayload>

    /**
     * Legge i bytes Codec2 da disco dato il path relativo salvato in [Message.audioFilePath].
     * Usato per riprodurre un messaggio vocale precedentemente ricevuto/inviato.
     *
     * @param relativePath path relativo a filesDir, es. "voice_bursts/12345678.c2"
     * @return ByteArray con i bytes Codec2, o null se il file non esiste o errore I/O
     */
    fun readAudioFile(relativePath: String): ByteArray?
}
