/*
 * Copyright (c) 2026 Chris7X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.meshtastic.feature.voiceburst.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.voiceburst.model.VoiceBurstPayload
import org.meshtastic.proto.PortNum
import java.io.File

private const val TAG = "AndroidVoiceBurstRepository"

/**
 * Implementazione Android di [VoiceBurstRepository].
 *
 * Architettura persistenza audio (WhatsApp/Telegram style):
 *   - Ogni burst ricevuto viene salvato come file <filesDir>/voice_bursts/<packetId>.c2
 *   - Il path relativo ("voice_bursts/<id>.c2") è inserito in Message.audioFilePath
 *   - La UI risolve il path assoluto via context.filesDir al momento della riproduzione
 *   - Il file persiste finché l'utente non cancella la chat o svuota la cache
 *
 * Anche i burst inviati vengono salvati (mittente può riascoltare il proprio messaggio).
 */
class AndroidVoiceBurstRepository(
    private val radioController: RadioController,
    private val dataStore: DataStore<Preferences>,
    private val packetRepository: PacketRepository,
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : VoiceBurstRepository {

    /** Directory dove vengono salvati i file .c2: <filesDir>/voice_bursts/ */
    private val voiceBurstsDir: File by lazy {
        File(context.filesDir, "voice_bursts").also { it.mkdirs() }
    }

    // ─── Feature flag ─────────────────────────────────────────────────────────

    private val featureEnabledFlow = dataStore.data
        .map { prefs -> prefs[KEY_FEATURE_ENABLED] ?: false }

    override val isFeatureEnabled: StateFlow<Boolean> =
        featureEnabledFlow.stateIn(
            scope = scope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = false,
        )

    override suspend fun setFeatureEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_FEATURE_ENABLED] = enabled }
        Logger.i(TAG) { "Feature Voice Burst: ${if (enabled) "abilitata" else "disabilitata"}" }
    }

    // ─── Invio ────────────────────────────────────────────────────────────────

    override suspend fun sendBurst(payload: VoiceBurstPayload, contactKey: String): Boolean {
        val channelDigit = contactKey.firstOrNull()?.digitToIntOrNull()
        val destNodeId = if (channelDigit != null) contactKey.substring(1) else contactKey

        return try {
            val ourNode = nodeRepository.ourNodeInfo.value
            val fromId = ourNode?.user?.id ?: DataPacket.ID_LOCAL
            val myNodeNum = ourNode?.num ?: 0

            val packet = DataPacket(
                to = destNodeId,
                bytes = payload.encode().toByteString(),
                dataType = VoiceBurstPayload.PORT_NUM,
                from = fromId,
                channel = channelDigit ?: DataPacket.PKC_CHANNEL_INDEX,
                wantAck = true,
                status = MessageStatus.ENROUTE,
            )

            // 1. Salva nel DB (appare in chat immediatamente)
            packetRepository.savePacket(
                myNodeNum = myNodeNum,
                contactKey = contactKey,
                packet = packet,
                receivedTime = nowMillis,
                read = true,
            )

            // 2. Salva i bytes audio su disco (mittente può riascoltare)
            // Usiamo il packetId generato dal DB per il nome file
            val savedPacket = packetRepository.findPacketsWithId(packet.id).firstOrNull()
            val packetId = savedPacket?.id ?: packet.id
            if (packetId != 0) {
                saveAudioFile(packetId, payload.audioData)
            }

            // 3. Invia via radio
            radioController.sendMessage(packet)
            Logger.i(TAG) { "Burst inviato a $destNodeId: ${payload.audioData.size} bytes audio" }
            true
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Errore invio burst a $destNodeId (contactKey=$contactKey)" }
            false
        }
    }

    // ─── Ricezione ────────────────────────────────────────────────────────────

    private val _incomingBursts = MutableSharedFlow<VoiceBurstPayload>(replay = 0, extraBufferCapacity = 8)
    override val incomingBursts: Flow<VoiceBurstPayload> = _incomingBursts

    init {
        serviceRepository.meshPacketFlow
            .filter { it.decoded?.portnum == PortNum.PRIVATE_APP }
            .onEach { packet -> processIncomingBurst(packet) }
            .launchIn(scope)
    }

    private suspend fun processIncomingBurst(packet: org.meshtastic.proto.MeshPacket) {
        val decoded = packet.decoded ?: return
        val payloadBytes = decoded.payload.toByteArray()

        val payload = VoiceBurstPayload.decode(payloadBytes)
        if (payload == null) {
            Logger.w(TAG) { "Payload non valido da ${packet.from} (${payloadBytes.size} bytes)" }
            return
        }

        Logger.i(TAG) { "Burst ricevuto da ${packet.from}: ${payload.durationMs}ms, ${payload.audioData.size} bytes" }

        val ourNode = nodeRepository.ourNodeInfo.value
        val myNodeNum = ourNode?.num ?: 0
        val fromId = DataPacket.nodeNumToDefaultId(packet.from)
        val toId = if (packet.to < 0 || packet.to == DataPacket.NODENUM_BROADCAST) {
            DataPacket.ID_BROADCAST
        } else {
            DataPacket.nodeNumToDefaultId(packet.to)
        }

        val channelIndex = if (packet.pki_encrypted == true) DataPacket.PKC_CHANNEL_INDEX else packet.channel
        val contactKey = "${channelIndex}${fromId}"

        val dataPacket = DataPacket(
            to = toId,
            bytes = payloadBytes.toByteString(),
            dataType = VoiceBurstPayload.PORT_NUM,
            from = fromId,
            time = nowMillis,
            id = packet.id,
            status = MessageStatus.RECEIVED,
            channel = channelIndex,
            wantAck = false,
            snr = packet.rx_snr,
            rssi = packet.rx_rssi,
        )

        try {
            // Deduplica
            if (packetRepository.findPacketsWithId(packet.id).isNotEmpty()) {
                Logger.d(TAG) { "Burst duplicato ignorato: packetId=${packet.id}" }
                return
            }

            // Salva nel DB → bubble in chat
            packetRepository.savePacket(
                myNodeNum = myNodeNum,
                contactKey = contactKey,
                packet = dataPacket,
                receivedTime = nowMillis,
                read = false,
            )

            // Salva audio su disco — il nome file corrisponde al packetId
            // così Message.audioFilePath = "voice_bursts/<packetId>.c2" punta al file
            saveAudioFile(packet.id, payload.audioData)
            Logger.i(TAG) { "Burst salvato: contactKey=$contactKey file=voice_bursts/${packet.id}.c2" }

        } catch (e: Exception) {
            Logger.e(TAG, e) { "Errore salvataggio burst da ${packet.from}" }
        }

        // Emette per riproduzione immediata (autoplay all'arrivo)
        _incomingBursts.tryEmit(payload.copy(senderNodeId = fromId))
    }

    // ─── Audio file I/O ───────────────────────────────────────────────────────

    /**
     * Salva i bytes Codec2 compressi in un file .c2.
     * Il file può essere letto in seguito per riprodurre il messaggio.
     */
    private fun saveAudioFile(packetId: Int, audioData: ByteArray) {
        try {
            val file = File(voiceBurstsDir, "$packetId.c2")
            file.writeBytes(audioData)
            Logger.d(TAG) { "Audio salvato: ${file.absolutePath} (${audioData.size} bytes)" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Errore scrittura file audio per packetId=$packetId" }
        }
    }

    /**
     * Legge i bytes Codec2 da disco dato un path relativo.
     * Usato dal ViewModel per riprodurre un messaggio vocale salvato.
     *
     * @param relativePath es. "voice_bursts/12345678.c2"
     * @return ByteArray con i bytes Codec2, o null se il file non esiste
     */
    override fun readAudioFile(relativePath: String): ByteArray? {
        return try {
            val file = File(context.filesDir, relativePath)
            if (file.exists()) file.readBytes() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Errore lettura file audio: $relativePath" }
            null
        }
    }

    companion object {
        private val KEY_FEATURE_ENABLED = booleanPreferencesKey("voice_burst_feature_enabled")
    }
}
