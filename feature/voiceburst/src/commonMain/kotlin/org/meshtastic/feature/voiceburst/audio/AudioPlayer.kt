/*
 * Copyright (c) 2026 Chris7X
 */
package org.meshtastic.feature.voiceburst.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {

    /**
     * Riproduce il buffer PCM fornito.
     * @param pcmData  PCM 16-bit mono 8000 Hz
     * @param filePath path logico del file in riproduzione (usato da UI per sapere quale bubble è attiva)
     * @param onComplete invocato al termine naturale o dopo stop
     */
    fun play(pcmData: ShortArray, filePath: String = "", onComplete: () -> Unit = {})

    /** Interrompe la riproduzione in corso. */
    fun stop()

    /** True se l'audio è in riproduzione. */
    val isPlaying: Boolean

    /**
     * Path del file attualmente in riproduzione, null se nessuno.
     * Permette alla UI di sapere quale bubble mostrare come "in play".
     * Emette null al termine/stop.
     */
    val playingFilePath: StateFlow<String?>
}
