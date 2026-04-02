/*
 * Copyright (c) 2026 Chris7X
 */
package org.meshtastic.feature.voiceburst.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {

    /**
     * Plays the provided PCM buffer.
     * @param pcmData  PCM 16-bit mono 8000 Hz
     * @param filePath logical path of the file being played (used by the UI to know which bubble is active)
     * @param onComplete invoked on natural completion or after stop
     */
    fun play(pcmData: ShortArray, filePath: String = "", onComplete: () -> Unit = {})

    /** Stops the current playback. */
    fun stop()

    /** True if audio is currently playing. */
    val isPlaying: Boolean

    /**
     * Path of the file currently being played, null if none.
     * Allows the UI to know which bubble to display as "playing".
     * Emits null on completion/stop.
     */
    val playingFilePath: StateFlow<String?>
}
