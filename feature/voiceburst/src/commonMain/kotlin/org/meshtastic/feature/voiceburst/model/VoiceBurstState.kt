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
 * Stati del ciclo di vita di un Voice Burst.
 *
 * Transizioni valide:
 *   Idle → Recording → Encoding → Sending → Sent
 *   Qualsiasi stato → Error
 *   Qualsiasi stato → Unsupported (se preset incompatibile rilevato)
 */
sealed class VoiceBurstState {
    /** Pronto per registrare. Nessuna operazione in corso. */
    data object Idle : VoiceBurstState()

    /**
     * Registrazione audio in corso.
     * @param elapsedMs millisecondi trascorsi dall'inizio della registrazione.
     */
    data class Recording(val elapsedMs: Long = 0L) : VoiceBurstState()

    /** Encoding Codec2 in corso (operazione veloce, tipicamente < 50ms). */
    data object Encoding : VoiceBurstState()

    /**
     * Pacchetto in coda per l'invio via RadioController.
     * Entra in questo stato se il nodo è temporaneamente disconnesso.
     */
    data object Queued : VoiceBurstState()

    /** Pacchetto consegnato al nodo via BLE. In attesa di ACK (opzionale). */
    data object Sending : VoiceBurstState()

    /** Invio completato con successo. */
    data object Sent : VoiceBurstState()

    /** Burst ricevuto da remoto. Pronto per il playback. */
    data class Received(val payload: VoiceBurstPayload) : VoiceBurstState()

    /**
     * Errore durante il ciclo di vita del burst.
     * @param reason causa dell'errore.
     */
    data class Error(val reason: VoiceBurstError) : VoiceBurstState()

    /**
     * Feature non disponibile nel contesto corrente.
     * Mostrato quando: preset sub-1GHz lento, feature flag disabilitato,
     * o destinatario non supporta il portnum.
     */
    data class Unsupported(val reason: String) : VoiceBurstState()
}

/** Cause di errore per [VoiceBurstState.Error]. */
enum class VoiceBurstError {
    /** Permesso microfono negato dall'utente. */
    MICROPHONE_PERMISSION_DENIED,

    /** Errore durante la registrazione audio. */
    RECORDING_FAILED,

    /** Encoding Codec2 fallito (stub o libreria non disponibile). */
    ENCODING_FAILED,

    /** Nodo destinatario non raggiungibile. */
    SEND_FAILED,

    /** Rate limit: troppi burst in poco tempo. Attendere almeno 30s. */
    RATE_LIMITED,
}
