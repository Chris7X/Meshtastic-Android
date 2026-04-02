/*
 * Copyright (c) 2026 Chris7X (LoRa project contributor)
 *
 * This version of the Codec2 JNI wrapper is licensed under the
 * GNU Lesser General Public License version 2.1 as published by
 * the Free Software Foundation.
 */
package org.meshtastic.codec2

/**
 * JNI wrapper for the Codec2 library.
 * This class is the interface between Kotlin/JVM and the C codec logic.
 */
class Codec2Jni {

    /**
     * Encodes 16-bit mono PCM audio (8kHz) into Codec2 compressed frames.
     * @param pcm Input audio data (ShortArray)
     * @return Compressed byte array or null on error
     */
    external fun encode(pcm: ShortArray): ByteArray?

    /**
     * Decodes Codec2 compressed frames back into 16-bit mono PCM audio (8kHz).
     * @param compressed Compressed audio data (ByteArray)
     * @return Decoded ShortArray or null on error
     */
    external fun decode(compressed: ByteArray): ShortArray?

    /**
     * Gets the current Codec2 mode (e.g., 3200, 2400, etc.).
     */
    external fun getMode(): Int

    companion object {
        init {
            try {
                System.loadLibrary("codec2_jni")
            } catch (e: UnsatisfiedLinkError) {
                // Logger not available in this core-module, using println
                println("Critical: Could not load codec2_jni library")
            }
        }
    }
}
