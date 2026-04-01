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

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "AndroidAudioRecorder"

/**
 * Android implementation of [AudioRecorder] based on [AudioRecord].
 *
 * Fixed parameters for Codec2 700B:
 *   - Source:   MIC
 *   - Rate:     8000 Hz
 *   - Channel:  CHANNEL_IN_MONO
 *   - Encoding: PCM_16BIT
 *
 * minSdk: 26 (verified in config.properties) — AudioRecord available since API 3. ✅
 *
 * PREREQUISITE: the caller must have obtained android.permission.RECORD_AUDIO
 * before invoking [startRecording].
 */
class AndroidAudioRecorder(
    private val scope: CoroutineScope,
) : AudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    override val isRecording: Boolean
        get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    override fun startRecording(
        onComplete: (pcmData: ShortArray, durationMs: Int) -> Unit,
        onError: (Throwable) -> Unit,
        maxDurationMs: Int,
    ) {
        if (isRecording) {
            Logger.w(TAG) { "startRecording called while already recording — ignored" }
            return
        }

        val sampleRate = 8000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onError(IllegalStateException("AudioRecord not supported on this device"))
            return
        }

        // Buffer sized for maxDurationMs + 20% margin
        val totalSamples = (sampleRate * maxDurationMs / 1000.0 * 1.2).toInt()
        val bufferSize = maxOf(minBufferSize, totalSamples * 2 /* bytes per short */)

        try {
            @Suppress("MissingPermission") // permesso verificato dal chiamante
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
        } catch (e: SecurityException) {
            onError(e)
            return
        }

        val record = audioRecord ?: run {
            onError(IllegalStateException("AudioRecord not initialized"))
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            onError(IllegalStateException("AudioRecord initialization failed"))
            record.release()
            audioRecord = null
            return
        }

        recordingJob = scope.launch(Dispatchers.IO) {
            val pcmBuffer = ShortArray(sampleRate * maxDurationMs / 1000)
            var samplesRead = 0
            val startTime = System.currentTimeMillis()

            try {
                record.startRecording()
                Logger.d(TAG) { "Recording started (max ${maxDurationMs}ms, ${sampleRate}Hz mono PCM16)" }

                while (samplesRead < pcmBuffer.size && isRecording) {
                    val chunkSize = minOf(minBufferSize / 2, pcmBuffer.size - samplesRead)
                    val read = record.read(pcmBuffer, samplesRead, chunkSize)
                    if (read < 0) {
                        Logger.e(TAG) { "AudioRecord.read error: $read" }
                        break
                    }
                    samplesRead += read
                }

                val durationMs = (System.currentTimeMillis() - startTime).toInt()
                    .coerceAtMost(maxDurationMs)

                Logger.d(TAG) { "Recording complete: $samplesRead samples, ${durationMs}ms" }
                onComplete(pcmBuffer.copyOf(samplesRead), durationMs)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Error during recording" }
                onError(e)
            } finally {
                record.stop()
                record.release()
                audioRecord = null
            }
        }
    }

    override fun stopRecording() {
        if (!isRecording) return
        Logger.d(TAG) { "Early stop recording" }
        // Stop AudioRecord — the loop in startRecording will terminate naturally
        audioRecord?.stop()
        recordingJob?.cancel()
    }
}
