/*
 * Copyright (c) 2026 Chris7X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.meshtastic.feature.voiceburst.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

import com.geeksville.mesh.voiceburst.Codec2JNI

/**
 * Test di unità per [AndroidCodec2Encoder].
 *
 * In ambiente CI/JVM (senza libcodec2.so) tutti i test vengono eseguiti contro lo STUB.
 * Quando il JNI è disponibile (device/emulatore), [Codec2JNI.isAvailable] = true e
 * i test verificano il codec reale.
 *
 * I test sono strutturati per passare in entrambe le modalità:
 *   - Stub: verifica dimensioni e proprietà strutturali
 *   - JNI reale: verifica anche la qualità audio (SNR minimo)
 */
class AndroidCodec2EncoderTest {

    // ─── Stub mode tests (sempre eseguiti) ────────────────────────────────────

    @Test
    fun `encode returns non-null for valid PCM input`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(pcm)
        assertNotNull("encode() non deve restituire null per input valido", encoded)
    }

    @Test
    fun `encode returns null for empty input`() {
        val encoder = AndroidCodec2Encoder()
        val result = encoder.encode(ShortArray(0))
        assertEquals("encode() deve restituire null per input vuoto", null, result)
    }

    @Test
    fun `encode output size is within codec2 700B budget`() {
        val encoder = AndroidCodec2Encoder()
        // 1 secondo @ 8000 Hz = 8000 campioni
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(pcm)!!

        // Codec2 700B: max 100 bytes per 1 secondo (25 frame × 4 bytes)
        // Accettiamo fino a 110 bytes per tolleranza frame arrotondamento
        assertTrue(
            "Payload troppo grande per LoRa: ${encoded.size} bytes > 110 (limite budget MVP)",
            encoded.size <= 110,
        )
        assertTrue(
            "Payload inaspettatamente piccolo: ${encoded.size} bytes",
            encoded.size >= 4,
        )
    }

    @Test
    fun `decode returns non-null for valid codec2 input`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(pcm)!!
        val decoded = encoder.decode(encoded)
        assertNotNull("decode() non deve restituire null per input valido", decoded)
    }

    @Test
    fun `decode returns null for empty input`() {
        val encoder = AndroidCodec2Encoder()
        val result = encoder.decode(ByteArray(0))
        assertEquals("decode() deve restituire null per input vuoto", null, result)
    }

    @Test
    fun `encode then decode roundtrip preserves length`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(pcm)!!
        val decoded = encoder.decode(encoded)!!

        // La lunghezza può differire leggermente per via del frame padding
        // ma deve essere vicina a quella originale
        val ratio = decoded.size.toDouble() / pcm.size.toDouble()
        assertTrue(
            "Lunghezza decoded (${ decoded.size}) troppo diversa dall'originale (${pcm.size}). Ratio: $ratio",
            ratio in 0.8..1.2,
        )
    }

    @Test
    fun `VoiceBurstPayload encodes and decodes correctly`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val codec2Bytes = encoder.encode(pcm)!!

        // Simula il ciclo completo di serializzazione del payload
        val payload = org.meshtastic.feature.voiceburst.model.VoiceBurstPayload(
            version = 1,
            codecMode = 0,
            durationMs = 1000,
            audioData = codec2Bytes,
        )
        val wireBytes = payload.encode()
        val decodedPayload = org.meshtastic.feature.voiceburst.model.VoiceBurstPayload.decode(wireBytes)

        assertNotNull("VoiceBurstPayload.decode() non deve restituire null", decodedPayload)
        assertEquals("version", payload.version, decodedPayload!!.version)
        assertEquals("codecMode", payload.codecMode, decodedPayload.codecMode)
        assertEquals("durationMs", payload.durationMs, decodedPayload.durationMs)
        assertArrayEquals("audioData", payload.audioData, decodedPayload.audioData)
    }

    @Test
    fun `payload size fits in single LoRa packet`() {
        val encoder = AndroidCodec2Encoder()
        val pcm = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val codec2Bytes = encoder.encode(pcm)!!

        val payload = org.meshtastic.feature.voiceburst.model.VoiceBurstPayload(
            version = 1,
            codecMode = 0,
            durationMs = 1000,
            audioData = codec2Bytes,
        )
        val wireBytes = payload.encode()

        // MTU LoRa max ~233 bytes. Con overhead mesh: budget sicuro = 200 bytes.
        assertTrue(
            "Payload ${wireBytes.size} bytes supera il budget LoRa (200 bytes)",
            wireBytes.size <= 200,
        )
    }

    // ─── JNI mode tests (eseguiti solo se Codec2Jni.isAvailable) ─────────────

    @Test
    fun `JNI roundtrip SNR above minimum threshold`() {
        Codec2JNI.ensureLoaded()
        if (!Codec2JNI.isAvailable) {
            // Skip gracefully in stub mode
            println("[SKIP] Codec2JNI non disponibile — test SNR saltato (stub mode)")
            return
        }

        val encoder = AndroidCodec2Encoder()
        val original = generateSineWave(freq = 440f, durationSec = 1.0f, sampleRate = 8000)
        val encoded = encoder.encode(original)!!
        val decoded = encoder.decode(encoded)!!

        // Misura SNR approssimativo sul segnale ricostruito
        val snrDb = computeSnrDb(original, decoded)
        println("SNR Codec2 700B roundtrip: $snrDb dB")

        // Codec2 700B a 440Hz sinusoidale: ci aspettiamo almeno 5 dB SNR
        // (soglia bassa — Codec2 700B è un codec vocale, non hi-fi)
        assertTrue(
            "SNR troppo basso per Codec2 700B: $snrDb dB (minimo atteso: 5 dB)",
            snrDb >= 5.0,
        )
    }

    @Test
    fun `JNI handle lifecycle create and destroy`() {
        Codec2JNI.ensureLoaded()
        if (!Codec2JNI.isAvailable) {
            println("[SKIP] Codec2JNI non disponibile")
            return
        }
        val handle = Codec2JNI.create(Codec2JNI.MODE_700C)
        assertTrue("Handle deve essere != 0", handle != 0L)
        assertEquals("samplesPerFrame deve essere 320 per 700B", 320, Codec2JNI.getSamplesPerFrame(Codec2JNI.MODE_700C))
        assertTrue("bytesPerFrame deve essere > 0", Codec2JNI.getBytesPerFrame(Codec2JNI.MODE_700C) > 0)
        Codec2JNI.destroy(handle)
        // Se arriviamo qui senza crash, il lifecycle è corretto
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Genera una sinusoide PCM 16-bit come segnale di test.
     * Ampiezza al 70% di Short.MAX_VALUE per simulare input vocale normalizzato.
     */
    private fun generateSineWave(freq: Float, durationSec: Float, sampleRate: Int): ShortArray {
        val numSamples = (sampleRate * durationSec).toInt()
        val amplitude = Short.MAX_VALUE * 0.7
        return ShortArray(numSamples) { i ->
            val angle = 2.0 * PI * freq * i / sampleRate
            (sin(angle) * amplitude).toInt().toShort()
        }
    }

    /**
     * Calcola il Signal-to-Noise Ratio approssimativo tra due segnali.
     * I segnali devono avere lunghezze simili — tronca al minimo.
     */
    private fun computeSnrDb(original: ShortArray, decoded: ShortArray): Double {
        val len = minOf(original.size, decoded.size)
        if (len == 0) return Double.NEGATIVE_INFINITY

        var signalPower = 0.0
        var noisePower = 0.0

        for (i in 0 until len) {
            val s = original[i].toDouble()
            val d = decoded[i].toDouble()
            signalPower += s * s
            noisePower  += (s - d) * (s - d)
        }

        if (noisePower < 1e-10) return Double.POSITIVE_INFINITY  // decoded perfetto
        return 10.0 * kotlin.math.log10(signalPower / noisePower)
    }
}
