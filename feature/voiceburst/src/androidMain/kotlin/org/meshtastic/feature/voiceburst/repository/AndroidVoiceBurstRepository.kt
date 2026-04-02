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
 * Android implementation of [VoiceBurstRepository].
 *
 * Audio persistence architecture (WhatsApp/Telegram style):
 *   - Each received burst is saved as a file <filesDir>/voice_bursts/<packetId>.c2
 *   - The relative path ("voice_bursts/<id>.c2") is inserted in Message.audioFilePath
 *   - The UI resolves the absolute path via context.filesDir at playback time
 *   - The file persists until the user deletes the chat or clears the cache
 *
 * Sent bursts are also saved (the sender can replay their own message).
 *
 * ## Packet ID assignment flow
 *
 * When [sendBurst] is called, the [DataPacket] starts with id = 0 (unassigned).
 * [RadioController.sendMessage] hands the packet to the mesh service, which assigns
 * the real mesh-layer packet ID and updates [DataPacket.id] in-place before returning.
 * The audio file must therefore be saved AFTER [RadioController.sendMessage] returns,
 * using the now-populated [DataPacket.id] as the file name — matching the convention
 * used on the receive side in [processIncomingBurst].
 *
 * If the radio is not connected at send time, the packet is queued with id = 0.
 * In that case we skip saving the sender's audio file; the sender will not be able
 * to replay the message until the device reconnects and the packet is delivered.
 * This is acceptable behaviour for MVP.
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

    /** Directory where .c2 files are saved: <filesDir>/voice_bursts/ */
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
        Logger.i(TAG) { "Voice Burst feature: ${if (enabled) "enabled" else "disabled"}" }
    }

    // ─── Send ─────────────────────────────────────────────────────────────────

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
            // packet.id == 0 here — the radio service has not assigned an ID yet.

            // Step 1: persist to DB so the chat bubble appears immediately.
            val uuid = packetRepository.savePacket(
                myNodeNum = myNodeNum,
                contactKey = contactKey,
                packet = packet,
                receivedTime = nowMillis,
                read = true,
            )

            // Step 2: hand the packet to the radio.
            radioController.sendMessage(packet)

            Logger.i(TAG) { "Burst sent to $destNodeId: ${payload.audioData.size} audio bytes, uuid=$uuid, packetId=${packet.id}" }

            // Step 3: save the audio file for sender-side replay using the stable DB uuid.
            saveAudioFile(uuid.toString(), payload.audioData)
            Logger.d(TAG) { "Sender audio saved: voice_bursts/$uuid.c2" }

            true
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Error sending burst to $destNodeId (contactKey=$contactKey)" }
            false
        }
    }

    // ─── Receive ────────────────────────────────────────────────────────────

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
            Logger.w(TAG) { "Invalid payload from ${packet.from} (${payloadBytes.size} bytes)" }
            return
        }

        Logger.i(TAG) { "Burst received from ${packet.from}: ${payload.durationMs}ms, ${payload.audioData.size} bytes" }

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
            // Deduplicate: ignore packets we have already processed.
            if (packetRepository.findPacketsWithId(packet.id).isNotEmpty()) {
                Logger.d(TAG) { "Duplicate burst ignored: packetId=${packet.id}" }
                return
            }

            // Save to DB so the chat bubble appears.
            val uuid = packetRepository.savePacket(
                myNodeNum = myNodeNum,
                contactKey = contactKey,
                packet = dataPacket,
                receivedTime = nowMillis,
                read = false,
            )

            // Save audio to disk using the stable DB uuid as the file name.
            saveAudioFile(uuid.toString(), payload.audioData)
            Logger.i(TAG) { "Burst saved: contactKey=$contactKey file=voice_bursts/$uuid.c2" }

        } catch (e: Exception) {
            Logger.e(TAG, e) { "Error saving burst from ${packet.from}" }
        }

        // Emit for immediate autoplay on arrival.
        _incomingBursts.tryEmit(payload.copy(senderNodeId = fromId))
    }

    // ─── Audio file I/O ───────────────────────────────────────────────────────

    /**
     * Saves the compressed Codec2 bytes to a .c2 file named after [identifier].
     *
     * Naming convention: voice_bursts/<uuid>.c2
     * This matches the [Message] model expectation.
     */
    private fun saveAudioFile(identifier: String, audioData: ByteArray) {
        try {
            val file = File(voiceBurstsDir, "$identifier.c2")
            file.writeBytes(audioData)
            Logger.d(TAG) { "Audio saved: ${file.absolutePath} (${audioData.size} bytes)" }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Error writing audio file for identifier=$identifier" }
        }
    }

    /**
     * Reads the Codec2 bytes from disk given a relative path.
     * Used by the ViewModel to play a saved voice message.
     *
     * @param relativePath e.g. "voice_bursts/12345678.c2"
     * @return ByteArray with the Codec2 bytes, or null if the file does not exist
     */
    override fun readAudioFile(relativePath: String): ByteArray? {
        return try {
            val file = File(context.filesDir, relativePath)
            if (file.exists()) file.readBytes() else null
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Error reading audio file: $relativePath" }
            null
        }
    }

    companion object {
        private val KEY_FEATURE_ENABLED = booleanPreferencesKey("voice_burst_feature_enabled")
    }
}
