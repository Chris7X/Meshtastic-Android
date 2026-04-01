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
package org.meshtastic.feature.voiceburst.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.feature.voiceburst.audio.AudioPlayer
import org.meshtastic.feature.voiceburst.audio.AudioRecorder
import org.meshtastic.feature.voiceburst.codec.Codec2Encoder
import org.meshtastic.feature.voiceburst.model.VoiceBurstError
import org.meshtastic.feature.voiceburst.model.VoiceBurstPayload
import org.meshtastic.feature.voiceburst.model.VoiceBurstState
import org.meshtastic.feature.voiceburst.repository.VoiceBurstRepository

private const val TAG = "VoiceBurstViewModel"

/**
 * ViewModel for the lifecycle control of a Voice Burst.
 *
 * Full pipeline:
 *   MIC → [AudioRecorder] → PCM → [Codec2Encoder.encode] → bytes → [VoiceBurstRepository.sendBurst]
 *   RADIO → [VoiceBurstRepository.incomingBursts] → bytes → [Codec2Encoder.decode] → PCM → [AudioPlayer]
 *
 * Rate limiting: minimum [RATE_LIMIT_MS] between two consecutive bursts.
 *
 * @param repository  manages feature flags, sending and receiving
 * @param encoder     Codec2 encoding/decoding (can be a sine wave stub)
 * @param audioPlayer plays the decoded PCM
 * @param audioRecorder records from the microphone (8kHz mono PCM16)
 * @param destNodeId  contactKey of the conversation (e.g. "0!42424243")
 */
@KoinViewModel
class VoiceBurstViewModel(
    private val repository: VoiceBurstRepository,
    private val encoder: Codec2Encoder,
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    @InjectedParam private val destNodeId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<VoiceBurstState>(VoiceBurstState.Idle)
    val state = _state.asStateFlow()

    val isFeatureEnabled = repository.isFeatureEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val incomingBursts = repository.incomingBursts

    /**
     * Path of the audio file currently playing.
     * Observed by [VoiceBurstPlayer] to show the correct ▶/■ icon.
     * Null when no audio is playing.
     */
    val playingFilePath = audioPlayer.playingFilePath

    private val resolvedNodeId: String = run {
        val channelDigit = destNodeId.firstOrNull()?.digitToIntOrNull()
        if (channelDigit != null) destNodeId.substring(1) else destNodeId
    }

    /** UI timer Job (updates elapsedMs every 100ms during recording). */
    private var uiTimerJob: Job? = null

    private var lastSentTimestamp = 0L

    init {
        // Listen for incoming bursts and play them
        repository.incomingBursts
            .onEach { payload -> onBurstReceived(payload) }
            .catch { e -> Logger.w(TAG) { "incomingBursts flow error: ${e.message}" } }
            .launchIn(viewModelScope)
    }

    // ─── Receiver-side playback ───────────────────────────────────────────────

    private fun onBurstReceived(payload: VoiceBurstPayload) {
        Logger.i(TAG) {
            "Burst received from ${payload.senderNodeId}: " +
                "${payload.durationMs}ms, ${payload.audioData.size} bytes"
        }
        _state.update { VoiceBurstState.Received(payload) }

        val pcmData = encoder.decode(payload.audioData)
        if (pcmData == null || pcmData.isEmpty()) {
            Logger.e(TAG) { "Decoding failed — no PCM to play" }
            _state.update { VoiceBurstState.Idle }
            return
        }

        Logger.d(TAG) { "Starting playback: ${pcmData.size} samples @ 8kHz" }
        // empty filePath for autoplay — not associated with a specific bubble
        audioPlayer.play(pcmData, filePath = "") {
            if (_state.value is VoiceBurstState.Received) {
                _state.update { VoiceBurstState.Idle }
            }
        }
    }

    // ─── Sender-side recording ──────────────────────────────────────────

    /**
     * Starts recording from the microphone.
     * No-op if not in [VoiceBurstState.Idle].
     * The caller must have verified the RECORD_AUDIO permission before invoking.
     */
    fun startRecording() {
        if (_state.value !is VoiceBurstState.Idle) return

        // Rate limiting
        val now = System.currentTimeMillis()
        val remaining = RATE_LIMIT_MS - (now - lastSentTimestamp)
        if (remaining > 0) {
            Logger.w(TAG) { "Rate limit: wait ${remaining / 1000}s" }
            _state.update { VoiceBurstState.Error(VoiceBurstError.RATE_LIMITED) }
            viewModelScope.launch {
                delay(remaining)
                if (_state.value is VoiceBurstState.Error) {
                    _state.update { VoiceBurstState.Idle }
                }
            }
            return
        }

        Logger.d(TAG) { "Starting recording to $resolvedNodeId (contactKey=$destNodeId)" }
        _state.update { VoiceBurstState.Recording(elapsedMs = 0L) }

        // UI timer: updates elapsedMs every TIMER_TICK_MS for the progress ring
        val startTime = System.currentTimeMillis()
        uiTimerJob = viewModelScope.launch {
            while (_state.value is VoiceBurstState.Recording) {
                delay(TIMER_TICK_MS)
                val elapsed = System.currentTimeMillis() - startTime
                _state.update {
                    if (it is VoiceBurstState.Recording)
                        VoiceBurstState.Recording(elapsedMs = minOf(elapsed, MAX_DURATION_MS.toLong()))
                    else it
                }
            }
        }

        // Start actual recording from the microphone
        audioRecorder.startRecording(
            onComplete = { pcmData, durationMs ->
                uiTimerJob?.cancel()
                uiTimerJob = null
                Logger.d(TAG) { "Recording complete: ${pcmData.size} samples, ${durationMs}ms" }
                onRecordingComplete(pcmData, durationMs)
            },
            onError = { error ->
                uiTimerJob?.cancel()
                uiTimerJob = null
                Logger.e(TAG) { "Recording error: ${error.message}" }
                _state.update { VoiceBurstState.Error(VoiceBurstError.ENCODING_FAILED) }
            },
            maxDurationMs = MAX_DURATION_MS,
        )
    }

    /**
     * Stops recording early.
     * AudioRecorder will call onComplete with the data recorded so far.
     */
    fun stopRecording() {
        if (_state.value !is VoiceBurstState.Recording) return
        Logger.d(TAG) { "Early stop recording" }
        uiTimerJob?.cancel()
        uiTimerJob = null
        audioRecorder.stopRecording()
        // onComplete will be called by AudioRecorder with the partial PCM
    }

    // ─── Encode and send ───────────────────────────────────────────────────────

    internal fun onRecordingComplete(pcmData: ShortArray, durationMs: Int) {
        _state.update { VoiceBurstState.Encoding }

        viewModelScope.launch {
            val audioBytes = encoder.encode(pcmData)
            if (audioBytes == null) {
                Logger.e(TAG) { "Codec2 encoding failed" }
                _state.update { VoiceBurstState.Error(VoiceBurstError.ENCODING_FAILED) }
                return@launch
            }

            if (encoder.isStub) {
                Logger.w(TAG) { "Codec2 stub — audio not intelligible on the receiver" }
            } else {
                Logger.i(TAG) { "Encode JNI OK: ${pcmData.size} samples → ${audioBytes.size} bytes" }
            }

            val payload = VoiceBurstPayload(
                durationMs = durationMs.toShort(),
                audioData = audioBytes,
            )

            _state.update { VoiceBurstState.Sending }
            val success = repository.sendBurst(payload, destNodeId)

            if (success) {
                lastSentTimestamp = System.currentTimeMillis()
                Logger.i(TAG) { "Burst sent: ${audioBytes.size} bytes, ${durationMs}ms" }
                _state.update { VoiceBurstState.Sent }
                delay(SENT_DISPLAY_MS)
                _state.update { VoiceBurstState.Idle }
            } else {
                Logger.e(TAG) { "Burst send failed to $destNodeId" }
                _state.update { VoiceBurstState.Error(VoiceBurstError.SEND_FAILED) }
            }
        }
    }

    fun reset() {
        _state.update { VoiceBurstState.Idle }
    }

    /**
     * Plays a saved voice message from disk.
     * Called by tapping the Voice Burst bubble in the chat.
     *
     * @param relativePath path relative to filesDir (from [Message.audioFilePath])
     *                     e.g. "voice_bursts/12345678.c2"
     */
    fun playBurst(relativePath: String) {
        if (audioPlayer.isPlaying) {
            audioPlayer.stop()
            return  // secondo tap = stop
        }
        viewModelScope.launch {
            val codec2Bytes = repository.readAudioFile(relativePath)
            if (codec2Bytes == null || codec2Bytes.isEmpty()) {
                Logger.e(TAG) { "Audio file not found: $relativePath" }
                return@launch
            }
            val pcmData = encoder.decode(codec2Bytes)
            if (pcmData == null || pcmData.isEmpty()) {
                Logger.e(TAG) { "Decoding failed for: $relativePath" }
                return@launch
            }
            Logger.d(TAG) { "Playing from file: $relativePath (${pcmData.size} samples)" }
            audioPlayer.play(pcmData, filePath = relativePath)
        }
    }

    companion object {
        const val RATE_LIMIT_MS   = 30_000L
        const val MAX_DURATION_MS = 1000
        const val SAMPLE_RATE_HZ  = 8000
        private const val SENT_DISPLAY_MS = 1500L
        private const val TIMER_TICK_MS   = 100L
    }
}
