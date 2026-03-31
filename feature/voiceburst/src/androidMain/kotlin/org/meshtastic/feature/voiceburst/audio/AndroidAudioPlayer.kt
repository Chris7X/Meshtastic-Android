/*
 * Copyright (c) 2026 Chris7X
 */
package org.meshtastic.feature.voiceburst.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AndroidAudioPlayer"

/**
 * Implementazione Android di [AudioPlayer].
 *
 * Fix rispetto alle versioni precedenti:
 *  - BUG: MODE_STATIC con bufferSize < minBufferSize → STATE_NO_STATIC_DATA (state=2) → silenzio.
 *    FIX: bufferSize = maxOf(minBufferSize, pcmBytes) SEMPRE, anche in static mode.
 *  - Uso MODE_STREAM sempre: più semplice, evita il problema di STATE_NO_STATIC_DATA.
 *    Per 1 secondo a 8kHz (16000 bytes) MODE_STREAM è più che sufficiente.
 *  - USAGE_MEDIA → altoparlante principale (non auricolare).
 *  - [playingFilePath] StateFlow per sincronizzare l'icona play/stop nella UI.
 */
class AndroidAudioPlayer(
    private val scope: CoroutineScope,
) : AudioPlayer {

    private var audioTrack: AudioTrack? = null
    private var playingJob: Job? = null

    private val _playingFilePath = MutableStateFlow<String?>(null)
    override val playingFilePath: StateFlow<String?> = _playingFilePath.asStateFlow()

    override val isPlaying: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    override fun play(pcmData: ShortArray, filePath: String, onComplete: () -> Unit) {
        // Se già in play, ferma prima
        if (isPlaying) {
            Logger.d(TAG) { "Stop traccia precedente prima di avviare nuova" }
            stopInternal()
        }

        if (pcmData.isEmpty()) {
            Logger.w(TAG) { "PCM vuoto — skip" }
            onComplete()
            return
        }

        val sampleRate    = SAMPLE_RATE_HZ
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        if (minBufferSize <= 0) {
            Logger.e(TAG) { "getMinBufferSize errore: $minBufferSize" }
            onComplete()
            return
        }

        // CRITICO: bufferSize deve essere >= minBufferSize SEMPRE.
        // Con MODE_STATIC, se bufferSize < minBufferSize → state=STATE_NO_STATIC_DATA=2 → silenzio.
        // Usiamo MODE_STREAM per semplicità e robustezza.
        val pcmBytes   = pcmData.size * Short.SIZE_BYTES
        val bufferSize = maxOf(minBufferSize, pcmBytes)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(audioEncoding)
            .setChannelMask(channelConfig)
            .build()

        val track = try {
            AudioTrack(attrs, format, bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Errore creazione AudioTrack" }
            onComplete()
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Logger.e(TAG) { "AudioTrack non inizializzato: state=${track.state} (atteso ${AudioTrack.STATE_INITIALIZED})" }
            track.release()
            onComplete()
            return
        }

        audioTrack = track
        _playingFilePath.value = filePath.ifEmpty { null }

        playingJob = scope.launch(Dispatchers.IO) {
            try {
                // MODE_STREAM: play() PRIMA, poi write() in streaming
                track.play()
                Logger.d(TAG) { "Riproduzione avviata: ${pcmData.size} samples @ ${sampleRate}Hz" }

                val written = track.write(pcmData, 0, pcmData.size)
                if (written < 0) {
                    Logger.e(TAG) { "write() errore: $written" }
                } else {
                    Logger.d(TAG) { "Write completato: $written samples" }
                    // Aspetta che il DAC finisca di suonare i campioni nel buffer
                    val drainMs = written.toLong() * 1000L / sampleRate + DRAIN_GUARD_MS
                    kotlinx.coroutines.delay(drainMs)
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Errore riproduzione" }
            } finally {
                releaseTrack(track)
                _playingFilePath.value = null
                scope.launch(Dispatchers.Main) { onComplete() }
            }
        }
    }

    override fun stop() {
        if (!isPlaying && playingJob?.isActive != true) return
        Logger.d(TAG) { "Stop riproduzione" }
        stopInternal()
    }

    private fun stopInternal() {
        playingJob?.cancel()
        playingJob = null
        audioTrack?.let { releaseTrack(it) }
        _playingFilePath.value = null
    }

    private fun releaseTrack(track: AudioTrack) {
        try { track.stop() } catch (_: Exception) {}
        try { track.flush() } catch (_: Exception) {}
        track.release()
        if (audioTrack === track) audioTrack = null
    }

    companion object {
        private const val SAMPLE_RATE_HZ  = 8000
        private const val DRAIN_GUARD_MS  = 150L  // margine extra per il DAC
    }
}
