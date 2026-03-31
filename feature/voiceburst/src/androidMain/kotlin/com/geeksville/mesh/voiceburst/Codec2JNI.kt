/*
 * Copyright (c) 2026 Chris7X
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.geeksville.mesh.voiceburst

import android.util.Log

/**
 * Binding JNI a libcodec2 prebuilt.
 * Entrambe le .so (libcodec2.so + libcodec2_jni.so) sono in jniLibs/.
 */
internal object Codec2JNI {

    private const val TAG = "Codec2JNI"
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            try {
                System.loadLibrary("codec2")
                Log.i(TAG, "libcodec2.so caricata OK")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Impossibile caricare libcodec2.so: ${e.message}")
                return
            }
            try {
                System.loadLibrary("codec2_jni")
                Log.i(TAG, "libcodec2_jni.so caricata OK — JNI attivo")
                loaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Impossibile caricare libcodec2_jni.so: ${e.message}")
                // loaded rimane false -> fallback stub
            }
        }
    }

    val isAvailable: Boolean
        get() = loaded

    // Modalita' codec2
    const val MODE_3200 = 0
    const val MODE_2400 = 1
    const val MODE_1600 = 2
    const val MODE_1400 = 3
    const val MODE_1300 = 4
    const val MODE_1200 = 5
    const val MODE_700C = 8
    const val MODE_450  = 10

    @JvmStatic external fun getSamplesPerFrame(mode: Int): Int
    @JvmStatic external fun getBytesPerFrame(mode: Int): Int
    @JvmStatic external fun create(mode: Int): Long
    @JvmStatic external fun encode(ptr: Long, pcm: ShortArray): ByteArray
    @JvmStatic external fun decode(ptr: Long, frame: ByteArray): ShortArray
    @JvmStatic external fun destroy(ptr: Long)
}
