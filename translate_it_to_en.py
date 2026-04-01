#!/usr/bin/env python3
"""
translate_it_to_en.py
=====================
Translates Italian KDoc, Logger calls and inline comments to English
across the 8 VoiceBurst / AI feature Kotlin files.

Usage (on Windows, from any directory):
    python translate_it_to_en.py

Each file is read as UTF-8, all replacements are applied in order,
then the file is written back (UTF-8, LF line endings preserved).
A WARN line is printed for any pattern not found in the file
(already translated or typo in this script).
"""

import os
import sys

BASE = r"C:\AI\LoRa\repos\Meshtastic-Android"

def p(*parts):
    return os.path.join(BASE, *parts)

FILES = {
    "VoiceBurstViewModel":       p(r"feature\voiceburst\src\commonMain\kotlin\org\meshtastic\feature\voiceburst\ui\VoiceBurstViewModel.kt"),
    "VoiceBurstPayload":         p(r"feature\voiceburst\src\commonMain\kotlin\org\meshtastic\feature\voiceburst\model\VoiceBurstPayload.kt"),
    "VoiceBurstState":           p(r"feature\voiceburst\src\commonMain\kotlin\org\meshtastic\feature\voiceburst\model\VoiceBurstState.kt"),
    "AndroidCodec2Encoder":      p(r"feature\voiceburst\src\androidMain\kotlin\org\meshtastic\feature\voiceburst\codec\AndroidCodec2Encoder.kt"),
    "AndroidVoiceBurstRepository": p(r"feature\voiceburst\src\androidMain\kotlin\org\meshtastic\feature\voiceburst\repository\AndroidVoiceBurstRepository.kt"),
    "AndroidAudioRecorder":      p(r"feature\voiceburst\src\androidMain\kotlin\org\meshtastic\feature\voiceburst\audio\AndroidAudioRecorder.kt"),
    "SmartReplyGenerator":       p(r"feature\ai\src\commonMain\kotlin\org\meshtastic\feature\ai\llm\SmartReplyGenerator.kt"),
    "LiteRtLlmRepository":       p(r"feature\ai\src\androidMain\kotlin\org\meshtastic\feature\ai\llm\LiteRtLlmRepository.kt"),
}

# ─────────────────────────────────────────────────────────────────────────────
# Replacement tables  –  (italian_string, english_string)
# ─────────────────────────────────────────────────────────────────────────────

REPLACEMENTS = {

# ══════════════════════════════════════════════════════════════════════════════
"VoiceBurstViewModel": [

    # ── KDoc class header ─────────────────────────────────────────────────────
    (" * ViewModel per il controllo del ciclo di vita di un Voice Burst.",
     " * ViewModel for the lifecycle control of a Voice Burst."),
    (" * Pipeline completa:",
     " * Full pipeline:"),
    (" * Rate limiting: minimo [RATE_LIMIT_MS] tra due burst consecutivi.",
     " * Rate limiting: minimum [RATE_LIMIT_MS] between two consecutive bursts."),
    (" * @param repository  gestisce feature flag, invio e ricezione",
     " * @param repository  manages feature flags, sending and receiving"),
    (" * @param encoder     encoding/decoding Codec2 (può essere stub sinusoide)",
     " * @param encoder     Codec2 encoding/decoding (can be a sine wave stub)"),
    (" * @param audioPlayer riproduce il PCM decodificato",
     " * @param audioPlayer plays the decoded PCM"),
    (" * @param audioRecorder registra dal microfono (8kHz mono PCM16)",
     " * @param audioRecorder records from the microphone (8kHz mono PCM16)"),
    (' * @param destNodeId  contactKey della conversazione (es. "0!42424243")',
     ' * @param destNodeId  contactKey of the conversation (e.g. "0!42424243")'),

    # ── KDoc playingFilePath ───────────────────────────────────────────────────
    ("     * Path del file audio attualmente in riproduzione.",
     "     * Path of the audio file currently playing."),
    ("     * Osservato da [VoiceBurstPlayer] per mostrare l'icona \u25b6/\u25a0 corretta.",
     "     * Observed by [VoiceBurstPlayer] to show the correct \u25b6/\u25a0 icon."),
    ("     * Null quando nessun audio \u00e8 in play.",
     "     * Null when no audio is playing."),

    # ── KDoc uiTimerJob ───────────────────────────────────────────────────────
    ("    /** Job del timer UI (aggiorna elapsedMs ogni 100ms durante la registrazione). */",
     "    /** UI timer Job (updates elapsedMs every 100ms during recording). */"),

    # ── init block comment ────────────────────────────────────────────────────
    ("        // Ascolta burst in arrivo e riproducili",
     "        // Listen for incoming bursts and play them"),

    # ── Section divider labels ────────────────────────────────────────────────
    ("Playback lato ricevente",
     "Receiver-side playback"),
    ("Registrazione lato mittente",
     "Sender-side recording"),
    ("Encode e invio",
     "Encode and send"),

    # ── KDoc startRecording() ─────────────────────────────────────────────────
    ("     * Avvia la registrazione dal microfono.",
     "     * Starts recording from the microphone."),
    ("     * No-op se non siamo in [VoiceBurstState.Idle].",
     "     * No-op if not in [VoiceBurstState.Idle]."),
    ("     * Il chiamante deve aver verificato il permesso RECORD_AUDIO prima di invocare.",
     "     * The caller must have verified the RECORD_AUDIO permission before invoking."),

    # ── KDoc stopRecording() ──────────────────────────────────────────────────
    ("     * Ferma la registrazione anticipatamente.",
     "     * Stops recording early."),
    ("     * L'AudioRecorder chiamer\u00e0 onComplete con i dati registrati finora.",
     "     * AudioRecorder will call onComplete with the data recorded so far."),

    # ── KDoc playBurst() ──────────────────────────────────────────────────────
    ("     * Riproduce un messaggio vocale salvato su disco.",
     "     * Plays a saved voice message from disk."),
    ("     * Chiamato dal tap sulla bubble Voice Burst nella chat.",
     "     * Called by tapping the Voice Burst bubble in the chat."),
    ("     * @param relativePath path relativo a filesDir (da [Message.audioFilePath])",
     "     * @param relativePath path relative to filesDir (from [Message.audioFilePath])"),
    ('     *                     es. "voice_bursts/12345678.c2"',
     '     *                     e.g. "voice_bursts/12345678.c2"'),

    # ── Logger strings ────────────────────────────────────────────────────────
    ('"Burst ricevuto da ${payload.senderNodeId}: "',
     '"Burst received from ${payload.senderNodeId}: "'),
    ('"Decoding fallito \u2014 nessun PCM da riprodurre"',
     '"Decoding failed \u2014 no PCM to play"'),
    ('"Avvio riproduzione: ${pcmData.size} samples @ 8kHz"',
     '"Starting playback: ${pcmData.size} samples @ 8kHz"'),
    ('"Rate limit: attendere ${remaining / 1000}s"',
     '"Rate limit: wait ${remaining / 1000}s"'),
    ('"Inizio registrazione verso $resolvedNodeId (contactKey=$destNodeId)"',
     '"Starting recording to $resolvedNodeId (contactKey=$destNodeId)"'),
    ('"Registrazione completata: ${pcmData.size} samples, ${durationMs}ms"',
     '"Recording complete: ${pcmData.size} samples, ${durationMs}ms"'),
    ('"Errore registrazione: ${error.message}"',
     '"Recording error: ${error.message}"'),
    ('"Encoding Codec2 fallito"',
     '"Codec2 encoding failed"'),
    ('"Codec2 stub \u2014 audio non intelligibile sul ricevente"',
     '"Codec2 stub \u2014 audio not intelligible on the receiver"'),
    ('"Burst inviato: ${audioBytes.size} bytes, ${durationMs}ms"',
     '"Burst sent: ${audioBytes.size} bytes, ${durationMs}ms"'),
    ('"Invio burst fallito verso $destNodeId"',
     '"Burst send failed to $destNodeId"'),
    ('"File audio non trovato: $relativePath"',
     '"Audio file not found: $relativePath"'),
    ('"Decodifica fallita per: $relativePath"',
     '"Decoding failed for: $relativePath"'),
    ('"Riproduzione da file: $relativePath (${pcmData.size} samples)"',
     '"Playing from file: $relativePath (${pcmData.size} samples)"'),
    ('        Logger.d(TAG) { "Stop anticipato registrazione" }',
     '        Logger.d(TAG) { "Early stop recording" }'),

    # ── Inline comments ───────────────────────────────────────────────────────
    ("        // Timer UI: aggiorna elapsedMs ogni TIMER_TICK_MS per l'anello di progresso",
     "        // UI timer: updates elapsedMs every TIMER_TICK_MS for the progress ring"),
    ("        // Avvia registrazione reale dal microfono",
     "        // Start actual recording from the microphone"),
    ("        // onComplete verr\u00e0 invocato dall'AudioRecorder con il PCM parziale",
     "        // onComplete will be called by AudioRecorder with the partial PCM"),
    ("        // filePath vuoto per autoplay \u2014 non associato a una bubble specifica",
     "        // empty filePath for autoplay \u2014 not associated with a specific bubble"),
],

# ══════════════════════════════════════════════════════════════════════════════
"VoiceBurstPayload": [

    # ── KDoc class ────────────────────────────────────────────────────────────
    (" * Payload di un Voice Burst pronto per la trasmissione o appena ricevuto.",
     " * Payload of a Voice Burst ready for transmission or just received."),
    (" * Dimensioni target MVP:",
     " * Target MVP sizes:"),
    (" *   - audioData: ~88 bytes (Codec2 700B, 1 secondo a 700 bps)",
     " *   - audioData: ~88 bytes (Codec2 700B, 1 second at 700 bps)"),
    (" *   - totale: < 120 bytes \u2192 entra in un singolo MeshPacket (max ~240 bytes)",
     " *   - total: < 120 bytes \u2192 fits in a single MeshPacket (max ~240 bytes)"),
    (" * PortNum: PRIVATE_APP = 256 (provvisorio \u2014 open question nel PRD)",
     " * PortNum: PRIVATE_APP = 256 (provisional \u2014 open question in the PRD)"),
    (" * TODO: definire proto ufficiale o richiedere portnum registrato upstream.",
     " * TODO: define official proto or request a registered portnum upstream."),
    (" * Serializzazione MVP: bytes raw prefissati con un header minimo a lunghezza fissa:",
     " * MVP serialization: raw bytes prefixed with a minimal fixed-length header:"),
    (" * Questo evita la dipendenza da protobuf aggiuntivo nel modulo per MVP.",
     " * This avoids an additional protobuf dependency in the module for MVP."),

    # ── Field KDocs ───────────────────────────────────────────────────────────
    ("     * Versione del formato del payload.",
     "     * Version of the payload format."),
    ("     * Incrementare se il formato cambia, per permettere graceful degradation.",
     "     * Increment if the format changes, to allow graceful degradation."),
    ("     * Modalit\u00e0 codec usata per l'encoding.",
     "     * Codec mode used for encoding."),
    ("     * 0 = Codec2 700B (unico valore supportato in MVP)",
     "     * 0 = Codec2 700B (only supported value in MVP)"),
    ("     * TODO: mappare a enum Codec2Mode quando disponibile.",
     "     * TODO: map to Codec2Mode enum when available."),
    ("     * Durata effettiva dell'audio registrato, in millisecondi.",
     "     * Actual duration of the recorded audio, in milliseconds."),
    ("     * MVP: sempre \u2264 1000ms.",
     "     * MVP: always \u2264 1000ms."),
    ("     * Bytes audio compressi con Codec2.",
     "     * Audio bytes compressed with Codec2."),
    ("     * MVP: ~88 bytes per 1 secondo a 700B.",
     "     * MVP: ~88 bytes per 1 second at 700B."),
    ("     * ID del nodo mittente (usato lato receiver per il display).",
     "     * Sender node ID (used on the receiver side for display)."),
    ("     * Popolato dal receiver con il from del DataPacket.",
     "     * Populated by the receiver with the from field of the DataPacket."),
    ("     * Serializza il payload in un ByteArray da inserire in [DataPacket.bytes].",
     "     * Serializes the payload into a ByteArray to insert into [DataPacket.bytes]."),

    # ── Companion KDocs ───────────────────────────────────────────────────────
    ("        /** PortNum provvisorio per MVP. PRIVATE_APP = 256. */",
     "        /** Provisional PortNum for MVP. PRIVATE_APP = 256. */"),
    ("        /** Durata massima supportata in MVP (1 secondo). */",
     "        /** Maximum duration supported in MVP (1 second). */"),
    ("         * Deserializza un payload ricevuto da un [DataPacket].",
     "         * Deserializes a payload received from a [DataPacket]."),
    ("         * Restituisce null se il formato non \u00e8 riconoscibile o la versione non \u00e8 supportata.",
     "         * Returns null if the format is unrecognizable or the version is not supported."),

    # ── Inline comments ───────────────────────────────────────────────────────
    ("if (bytes.size < 5) return null // minimo: header 4 bytes + 1 byte audio",
     "if (bytes.size < 5) return null // minimum: 4-byte header + 1 byte audio"),
    ("if (version != 1.toByte()) return null // versione non supportata",
     "if (version != 1.toByte()) return null // unsupported version"),
],

# ══════════════════════════════════════════════════════════════════════════════
"VoiceBurstState": [

    # ── KDoc class ────────────────────────────────────────────────────────────
    (" * Stati del ciclo di vita di un Voice Burst.",
     " * States of the lifecycle of a Voice Burst."),
    (" * Transizioni valide:",
     " * Valid transitions:"),
    (" *   Qualsiasi stato \u2192 Error",
     " *   Any state \u2192 Error"),
    (" *   Qualsiasi stato \u2192 Unsupported (se preset incompatibile rilevato)",
     " *   Any state \u2192 Unsupported (if incompatible preset detected)"),

    # ── State KDocs ───────────────────────────────────────────────────────────
    ("    /** Pronto per registrare. Nessuna operazione in corso. */",
     "    /** Ready to record. No operation in progress. */"),
    ("     * Registrazione audio in corso.",
     "     * Audio recording in progress."),
    ("     * @param elapsedMs millisecondi trascorsi dall'inizio della registrazione.",
     "     * @param elapsedMs milliseconds elapsed since the start of recording."),
    ("    /** Encoding Codec2 in corso (operazione veloce, tipicamente < 50ms). */",
     "    /** Codec2 encoding in progress (fast operation, typically < 50ms). */"),
    ("     * Pacchetto in coda per l'invio via RadioController.",
     "     * Packet queued for sending via RadioController."),
    ("     * Entra in questo stato se il nodo \u00e8 temporaneamente disconnesso.",
     "     * Enters this state if the node is temporarily disconnected."),
    ("    /** Pacchetto consegnato al nodo via BLE. In attesa di ACK (opzionale). */",
     "    /** Packet delivered to the node via BLE. Waiting for ACK (optional). */"),
    ("    /** Invio completato con successo. */",
     "    /** Send completed successfully. */"),
    ("    /** Burst ricevuto da remoto. Pronto per il playback. */",
     "    /** Burst received from remote. Ready for playback. */"),
    ("     * Errore durante il ciclo di vita del burst.",
     "     * Error during the burst lifecycle."),
    ("     * @param reason causa dell'errore.",
     "     * @param reason cause of the error."),
    ("     * Feature non disponibile nel contesto corrente.",
     "     * Feature not available in the current context."),
    ("     * Mostrato quando: preset sub-1GHz lento, feature flag disabilitato,",
     "     * Shown when: slow sub-1GHz preset, feature flag disabled,"),
    ("     * o destinatario non supporta il portnum.",
     "     * or recipient does not support the portnum."),

    # ── Enum KDocs ────────────────────────────────────────────────────────────
    ("/** Cause di errore per [VoiceBurstState.Error]. */",
     "/** Error causes for [VoiceBurstState.Error]. */"),
    ("    /** Permesso microfono negato dall'utente. */",
     "    /** Microphone permission denied by the user. */"),
    ("    /** Errore durante la registrazione audio. */",
     "    /** Error during audio recording. */"),
    ("    /** Encoding Codec2 fallito (stub o libreria non disponibile). */",
     "    /** Codec2 encoding failed (stub or library not available). */"),
    ("    /** Nodo destinatario non raggiungibile. */",
     "    /** Destination node not reachable. */"),
    ("    /** Rate limit: troppi burst in poco tempo. Attendere almeno 30s. */",
     "    /** Rate limit: too many bursts in a short time. Wait at least 30s. */"),
],

# ══════════════════════════════════════════════════════════════════════════════
"AndroidCodec2Encoder": [

    # ── KDoc class ────────────────────────────────────────────────────────────
    (" * Implementazione Android di [Codec2Encoder].",
     " * Android implementation of [Codec2Encoder]."),
    (" * Quando [Codec2JNI.isAvailable] = true usa libcodec2 via JNI (audio vocale reale).",
     " * When [Codec2JNI.isAvailable] = true, uses libcodec2 via JNI (real voice audio)."),
    (" * Altrimenti cade in modalit\u00e0 STUB (sinusoide 440Hz) per sviluppo/CI/build senza .so.",
     " * Otherwise falls back to STUB mode (440Hz sine wave) for development/CI/builds without .so."),
    (" * Codec2 700B parametri:",
     " * Codec2 700B parameters:"),
    (" *   - Frame:              40ms = 320 campioni",
     " *   - Frame:              40ms = 320 samples"),
    (" *   - 1 secondo:          25 frame \u00d7 4 bytes = 100 bytes",
     " *   - 1 second:           25 frames \u00d7 4 bytes = 100 bytes"),
    (" * Preprocessing applicato prima dell'encode (solo modalit\u00e0 JNI):",
     " * Preprocessing applied before encoding (JNI mode only):"),
    (" *   1. Normalizzazione ampiezza (porta al 70% di Short.MAX_VALUE)",
     " *   1. Amplitude normalization (brings to 70% of Short.MAX_VALUE)"),
    (" *   2. VAD semplice: se RMS < soglia, ritorna silenzio senza encode",
     " *   2. Simple VAD: if RMS < threshold, returns silence without encoding"),
    ("     *   L'handle Codec2 viene creato nel costruttore e distrutto in [close()].",
     "     *   The Codec2 handle is created in the constructor and destroyed in [close()]."),
    ("     *   Usare [use { }] o chiamare [close()] esplicitamente.",
     "     *   Use [use { }] or call [close()] explicitly."),

    # ── Logger init ───────────────────────────────────────────────────────────
    ('"Codec2JNI.create() ritornato 0 \u2014 cado in modalit\u00e0 stub"',
     '"Codec2JNI.create() returned 0 \u2014 falling back to stub mode"'),
    ('"Codec2 JNI non disponibile \u2014 modalit\u00e0 stub (sinusoide 440Hz)"',
     '"Codec2 JNI not available \u2014 stub mode (440Hz sine wave)"'),
    ('"Codec2 handle rilasciato"',
     '"Codec2 handle released"'),

    # ── KDoc encode() ─────────────────────────────────────────────────────────
    ("     * Codifica PCM 16-bit mono 8000Hz in bytes Codec2 700B.",
     "     * Encodes 16-bit mono 8000Hz PCM into Codec2 700B bytes."),
    ("     * Accetta un array di qualsiasi lunghezza \u2014 viene suddiviso in frame",
     "     * Accepts an array of any length \u2014 it is split into frames"),
    ("     * da [SAMPLES_PER_FRAME] campioni. L'ultimo frame incompleto viene",
     "     * of [SAMPLES_PER_FRAME] samples. The last incomplete frame is"),
    ("     * completato con zeri (zero-padding).",
     "     * padded with zeros (zero-padding)."),
    ("     * @param pcmData  Campioni PCM dal microfono (8000 Hz, mono, signed 16-bit)",
     "     * @param pcmData  PCM samples from the microphone (8000 Hz, mono, signed 16-bit)"),
    ("     * @return         ByteArray con i bytes Codec2, null se input vuoto",
     "     * @return         ByteArray with Codec2 bytes, null if input is empty"),

    # ── encodeJni Logger ──────────────────────────────────────────────────────
    ('"VAD: silenzio rilevato (RMS=$rms) \u2014 skip encode"',
     '"VAD: silence detected (RMS=$rms) \u2014 skipping encode"'),
    ('"Encode fallito al frame $frameIdx"',
     '"Encode failed at frame $frameIdx"'),
    # multi-line encode log (two separate Kotlin string literals)
    ('"Encode JNI: ${pcmData.size} campioni \u2192 ${output.size} bytes "',
     '"Encode JNI: ${pcmData.size} samples \u2192 ${output.size} bytes "'),
    ('"($frameCount frame \u00d7 $bytesPerFrame bytes)"',
     '"($frameCount frames \u00d7 $bytesPerFrame bytes)"'),

    # ── KDoc decode() ─────────────────────────────────────────────────────────
    ("     * Decodifica bytes Codec2 700B in campioni PCM 16-bit mono 8000Hz.",
     "     * Decodes Codec2 700B bytes into 16-bit mono 8000Hz PCM samples."),
    ("     * @param codec2Data  ByteArray di bytes Codec2 (multiplo di bytesPerFrame)",
     "     * @param codec2Data  ByteArray of Codec2 bytes (multiple of bytesPerFrame)"),
    ("     * @return            ShortArray di campioni PCM, null se input vuoto/invalido",
     "     * @return            ShortArray of PCM samples, null if input is empty/invalid"),

    # ── decodeJni Logger ──────────────────────────────────────────────────────
    ('"Decode: dimensione input (${codec2Data.size}) non multipla di "',
     '"Decode: input size (${codec2Data.size}) not a multiple of "'),
    ('"bytesPerFrame ($bytesPerFrame) \u2014 troncamento al frame completo"',
     '"bytesPerFrame ($bytesPerFrame) \u2014 truncating to complete frame"'),
    ('"Decode fallito al frame $frameIdx"',
     '"Decode failed at frame $frameIdx"'),
    ('"Decode JNI: ${codec2Data.size} bytes \u2192 ${output.size} campioni "',
     '"Decode JNI: ${codec2Data.size} bytes \u2192 ${output.size} samples "'),
    ('"($frameCount frame \u00d7 $samplesPerFrame campioni)"',
     '"($frameCount frames \u00d7 $samplesPerFrame samples)"'),

    # ── KDoc normalize() ──────────────────────────────────────────────────────
    ("     * Normalizza l'ampiezza del segnale a [TARGET_AMPLITUDE] \u00d7 Short.MAX_VALUE.",
     "     * Normalizes the signal amplitude to [TARGET_AMPLITUDE] \u00d7 Short.MAX_VALUE."),
    ("     * Previene clipping e migliora la qualit\u00e0 Codec2 su voci basse.",
     "     * Prevents clipping and improves Codec2 quality on low-volume voices."),

    # ── KDoc computeRms() ─────────────────────────────────────────────────────
    ("     * Calcola il Root Mean Square del segnale.",
     "     * Computes the Root Mean Square of the signal."),
    ("     * Usato per il VAD semplice: RMS < [SILENCE_RMS_THRESHOLD] = silenzio.",
     "     * Used for simple VAD: RMS < [SILENCE_RMS_THRESHOLD] = silence."),

    # ── Section divider (stub) ────────────────────────────────────────────────
    ("fallback quando JNI non \u00e8 disponibile",
     "fallback when JNI is not available"),

    # ── encodeStub / decodeStub Logger ────────────────────────────────────────
    ('"Codec2 STUB encode: ${pcmData.size} campioni \u2192 ${frameCount * BYTES_PER_FRAME} bytes (zeri)"',
     '"Codec2 STUB encode: ${pcmData.size} samples \u2192 ${frameCount * BYTES_PER_FRAME} bytes (zeros)"'),
    ('"Codec2 STUB decode: ${codec2Data.size} bytes \u2192 $totalSamples campioni (sinusoide 440Hz)"',
     '"Codec2 STUB decode: ${codec2Data.size} bytes \u2192 $totalSamples samples (440Hz sine wave)"'),

    # ── Inline comments ───────────────────────────────────────────────────────
    ("        // Genera sinusoide 440Hz (La4) \u2014 udibile e riconoscibile",
     "        // Generate 440Hz sine wave (A4) \u2014 audible and recognizable"),
    ("                    // restante gi\u00e0 0 per default",
     "                    // remaining already 0 by default"),
    ("        // Limita il gain massimo a 10x per evitare amplificazione eccessiva del rumore",
     "        // Limit maximum gain to 10x to avoid excessive noise amplification"),

    # ── Companion KDocs ───────────────────────────────────────────────────────
    ("        /** Codec2 700B: 320 campioni per frame (40ms @ 8000 Hz). */",
     "        /** Codec2 700B: 320 samples per frame (40ms @ 8000 Hz). */"),
    ("        /** Codec2 700B: 4 bytes per frame (700 bps arrotondati). */",
     "        /** Codec2 700B: 4 bytes per frame (700 bps rounded). */"),
    ("        /** Ampiezza target per la normalizzazione (70% di Short.MAX_VALUE). */",
     "        /** Target amplitude for normalization (70% of Short.MAX_VALUE). */"),
    ("        /** Gain massimo applicato dalla normalizzazione (10\u00d7). */",
     "        /** Maximum gain applied by normalization (10\u00d7). */"),
    ("         * Soglia RMS sotto cui il frame viene considerato silenzio (VAD semplice).",
     "         * RMS threshold below which the frame is considered silence (simple VAD)."),
    ("         * 200.0 su scala 0-32767 \u00e8 circa -44 dBFS \u2014 voce normale \u00e8 2000-8000.",
     "         * 200.0 on the 0-32767 scale is approximately -44 dBFS \u2014 normal voice is 2000-8000."),
],

# ══════════════════════════════════════════════════════════════════════════════
"AndroidVoiceBurstRepository": [

    # ── KDoc class ────────────────────────────────────────────────────────────
    (" * Implementazione Android di [VoiceBurstRepository].",
     " * Android implementation of [VoiceBurstRepository]."),
    (" * Architettura persistenza audio (WhatsApp/Telegram style):",
     " * Audio persistence architecture (WhatsApp/Telegram style):"),
    (" *   - Ogni burst ricevuto viene salvato come file <filesDir>/voice_bursts/<packetId>.c2",
     " *   - Each received burst is saved as a file <filesDir>/voice_bursts/<packetId>.c2"),
    (' *   - Il path relativo ("voice_bursts/<id>.c2") \u00e8 inserito in Message.audioFilePath',
     ' *   - The relative path ("voice_bursts/<id>.c2") is inserted in Message.audioFilePath'),
    (" *   - La UI risolve il path assoluto via context.filesDir al momento della riproduzione",
     " *   - The UI resolves the absolute path via context.filesDir at playback time"),
    (" *   - Il file persiste finch\u00e9 l'utente non cancella la chat o svuota la cache",
     " *   - The file persists until the user deletes the chat or clears the cache"),
    (" * Anche i burst inviati vengono salvati (mittente pu\u00f2 riascoltare il proprio messaggio).",
     " * Sent bursts are also saved (the sender can replay their own message)."),

    # ── voiceBurstsDir KDoc ───────────────────────────────────────────────────
    ("    /** Directory dove vengono salvati i file .c2: <filesDir>/voice_bursts/ */",
     "    /** Directory where .c2 files are saved: <filesDir>/voice_bursts/ */"),

    # ── Section dividers ──────────────────────────────────────────────────────
    ("\u2500\u2500\u2500 Invio ",
     "\u2500\u2500\u2500 Send \u2500"),
    ("\u2500\u2500\u2500 Ricezione ",
     "\u2500\u2500\u2500 Receive "),

    # ── Logger setFeatureEnabled ──────────────────────────────────────────────
    ('"Feature Voice Burst: ${if (enabled) "abilitata" else "disabilitata"}"',
     '"Voice Burst feature: ${if (enabled) "enabled" else "disabled"}"'),

    # ── Logger / comments sendBurst ───────────────────────────────────────────
    ('"Burst inviato a $destNodeId: ${payload.audioData.size} bytes audio"',
     '"Burst sent to $destNodeId: ${payload.audioData.size} audio bytes"'),
    ('"Errore invio burst a $destNodeId (contactKey=$contactKey)"',
     '"Error sending burst to $destNodeId (contactKey=$contactKey)"'),
    ("            // 1. Salva nel DB (appare in chat immediatamente)",
     "            // 1. Save to DB (appears in chat immediately)"),
    ("            // 2. Salva i bytes audio su disco (mittente pu\u00f2 riascoltare)",
     "            // 2. Save audio bytes to disk (sender can replay)"),
    ("            // Usiamo il packetId generato dal DB per il nome file",
     "            // Use the packetId generated by the DB for the file name"),
    ("            // 3. Invia via radio",
     "            // 3. Send via radio"),

    # ── Logger / comments processIncomingBurst ────────────────────────────────
    ('"Payload non valido da ${packet.from} (${payloadBytes.size} bytes)"',
     '"Invalid payload from ${packet.from} (${payloadBytes.size} bytes)"'),
    ('"Burst ricevuto da ${packet.from}: ${payload.durationMs}ms, ${payload.audioData.size} bytes"',
     '"Burst received from ${packet.from}: ${payload.durationMs}ms, ${payload.audioData.size} bytes"'),
    ("            // Deduplica",
     "            // Deduplicate"),
    ("            // Salva nel DB \u2192 bubble in chat",
     "            // Save to DB \u2192 chat bubble"),
    ("            // Salva audio su disco \u2014 il nome file corrisponde al packetId",
     "            // Save audio to disk \u2014 the file name matches the packetId"),
    ('            // cos\u00ec Message.audioFilePath = "voice_bursts/<packetId>.c2" punta al file',
     '            // so that Message.audioFilePath = "voice_bursts/<packetId>.c2" points to the file'),
    ('"Burst salvato: contactKey=$contactKey file=voice_bursts/${packet.id}.c2"',
     '"Burst saved: contactKey=$contactKey file=voice_bursts/${packet.id}.c2"'),
    ('"Errore salvataggio burst da ${packet.from}"',
     '"Error saving burst from ${packet.from}"'),
    ("        // Emette per riproduzione immediata (autoplay all'arrivo)",
     "        // Emit for immediate playback (autoplay on arrival)"),
    ('"Burst duplicato ignorato: packetId=${packet.id}"',
     '"Duplicate burst ignored: packetId=${packet.id}"'),

    # ── Logger saveAudioFile ──────────────────────────────────────────────────
    ('"Audio salvato: ${file.absolutePath} (${audioData.size} bytes)"',
     '"Audio saved: ${file.absolutePath} (${audioData.size} bytes)"'),
    ('"Errore scrittura file audio per packetId=$packetId"',
     '"Error writing audio file for packetId=$packetId"'),

    # ── Logger readAudioFile ──────────────────────────────────────────────────
    ('"Errore lettura file audio: $relativePath"',
     '"Error reading audio file: $relativePath"'),

    # ── KDoc saveAudioFile ────────────────────────────────────────────────────
    ("     * Salva i bytes Codec2 compressi in un file .c2.",
     "     * Saves the compressed Codec2 bytes to a .c2 file."),
    ("     * Il file pu\u00f2 essere letto in seguito per riprodurre il messaggio.",
     "     * The file can be read later to replay the message."),

    # ── KDoc readAudioFile ────────────────────────────────────────────────────
    ("     * Legge i bytes Codec2 da disco dato un path relativo.",
     "     * Reads the Codec2 bytes from disk given a relative path."),
    ("     * Usato dal ViewModel per riprodurre un messaggio vocale salvato.",
     "     * Used by the ViewModel to play a saved voice message."),
    ('     * @param relativePath es. "voice_bursts/12345678.c2"',
     '     * @param relativePath e.g. "voice_bursts/12345678.c2"'),
    ("     * @return ByteArray con i bytes Codec2, o null se il file non esiste",
     "     * @return ByteArray with the Codec2 bytes, or null if the file does not exist"),
],

# ══════════════════════════════════════════════════════════════════════════════
"AndroidAudioRecorder": [

    # ── KDoc class ────────────────────────────────────────────────────────────
    (" * Implementazione Android di [AudioRecorder] basata su [AudioRecord].",
     " * Android implementation of [AudioRecorder] based on [AudioRecord]."),
    (" * Parametri fissi per Codec2 700B:",
     " * Fixed parameters for Codec2 700B:"),
    (" * minSdk: 26 (verificato in config.properties) \u2014 AudioRecord disponibile da API 3. \u2705",
     " * minSdk: 26 (verified in config.properties) \u2014 AudioRecord available since API 3. \u2705"),
    (" * PREREQUISITO: il chiamante deve aver ottenuto android.permission.RECORD_AUDIO",
     " * PREREQUISITE: the caller must have obtained android.permission.RECORD_AUDIO"),
    (" * prima di invocare [startRecording].",
     " * before invoking [startRecording]."),

    # ── Logger ────────────────────────────────────────────────────────────────
    ('"startRecording chiamato mentre gi\u00e0 in registrazione \u2014 ignorato"',
     '"startRecording called while already recording \u2014 ignored"'),
    ("        // Buffer dimensionato per maxDurationMs + margine 20%",
     "        // Buffer sized for maxDurationMs + 20% margin"),
    ('"AudioRecord non supportato su questo dispositivo"',
     '"AudioRecord not supported on this device"'),
    ('"AudioRecord non inizializzato"',
     '"AudioRecord not initialized"'),
    ('"AudioRecord inizializzazione fallita"',
     '"AudioRecord initialization failed"'),
    ('"Registrazione avviata (max ${maxDurationMs}ms, ${sampleRate}Hz mono PCM16)"',
     '"Recording started (max ${maxDurationMs}ms, ${sampleRate}Hz mono PCM16)"'),
    ('"AudioRecord.read errore: $read"',
     '"AudioRecord.read error: $read"'),
    ('"Registrazione completata: $samplesRead samples, ${durationMs}ms"',
     '"Recording complete: $samplesRead samples, ${durationMs}ms"'),
    ('"Errore durante la registrazione"',
     '"Error during recording"'),
    ('        Logger.d(TAG) { "Stop anticipato registrazione" }',
     '        Logger.d(TAG) { "Early stop recording" }'),
    ("        // Ferma AudioRecord \u2014 il loop in startRecording terminer\u00e0 naturalmente",
     "        // Stop AudioRecord \u2014 the loop in startRecording will terminate naturally"),
],

# ══════════════════════════════════════════════════════════════════════════════
"SmartReplyGenerator": [

    # ── Single Italian inline comment ─────────────────────────────────────────
    ("            // Aggiungi il messaggio ricevuto come ultimo messaggio del contesto",
     "            // Add the received message as the last message in the context"),
],

# ══════════════════════════════════════════════════════════════════════════════
"LiteRtLlmRepository": [

    # ── Two Italian inline comments ───────────────────────────────────────────
    ("        // Seed deterministico: stesso messaggio -> stesse 3 risposte nella sessione,",
     "        // Deterministic seed: same message -> same 3 replies in the session,"),
    ("        // messaggi diversi -> ordine diverso dal pool di 6 -> varieta' percepita.",
     "        // different messages -> different order from pool of 6 -> perceived variety."),
],

}  # end REPLACEMENTS


# ─────────────────────────────────────────────────────────────────────────────

def translate_file(name: str, filepath: str, pairs: list) -> None:
    if not os.path.isfile(filepath):
        print(f"[SKIP]  {name}: file not found\n        {filepath}")
        return

    with open(filepath, encoding="utf-8") as fh:
        content = fh.read()

    original = content
    applied = 0
    for old, new in pairs:
        if old in content:
            content = content.replace(old, new)
            applied += 1
        else:
            print(f"  [WARN] {name}: pattern not found ->\n"
                  f"         {old[:80]!r}")

    if content != original:
        with open(filepath, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(content)
        print(f"[OK]    {name}: {applied}/{len(pairs)} replacements applied")
    else:
        print(f"[NOOP]  {name}: no changes (already translated or all patterns missing)")


def main() -> None:
    print("=" * 72)
    print("  Italian -> English  |  VoiceBurst & AI feature files  |  8 files")
    print("=" * 72)
    for name, filepath in FILES.items():
        translate_file(name, filepath, REPLACEMENTS[name])
    print("=" * 72)
    print("Done.")


if __name__ == "__main__":
    main()
