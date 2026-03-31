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

package org.meshtastic.feature.achievements.engine

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.achievements.model.AchievementId
import org.meshtastic.feature.achievements.repository.AchievementRepository

private val logger = Logger.withTag("AchievementRulesEngine")

/**
 * Connects mesh events to achievement unlocks.
 *
 * APIs verified on the real codebase (2026-03-21):
 *
 * - MeshActivity: sealed class with ONLY Send and Receive (data object)
 * - MyNodeInfo: no timestamp field — NODE_7DAYS uses Node.lastHeard
 * - meshActivity: SharedFlow<MeshActivity> on RadioInterfaceService
 * - PacketRepository.getUnreadCountTotal(): Flow<Int> — proxy for received messages
 * - Node.environmentMetrics: EnvironmentMetrics — present if sensor detected
 * - ServiceRepository.tracerouteResponse: StateFlow<TracerouteResponse?>
 *
 * Zero network impact: all triggers are derived from already-existing flows.
 */
class AchievementRulesEngine(
    private val nodeRepository: NodeRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val packetRepository: PacketRepository,
    private val repository: AchievementRepository,
    private val scope: CoroutineScope,
) {
    fun start() {
        watchFirstNode()
        watchNodeCount()
        watchMeshActivity()
        watchDmReceived()
        watchTraceroute()
        watchTelemetry()
        watchSensor()
        logger.d { "AchievementRulesEngine started" }
    }

    // FIRST_NODE: myNodeInfo becomes non-null after BLE connect + config received
    private fun watchFirstNode() {
        nodeRepository.myNodeInfo
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { unlock(AchievementId.FIRST_NODE) }
            .launchIn(scope)
    }

    // TEN_NODES: live map of all discovered nodes
    private fun watchNodeCount() {
        nodeRepository.nodeDBbyNum
            .map { it.size }
            .distinctUntilChanged()
            .filter { it >= 10 }
            .onEach { unlock(AchievementId.TEN_NODES) }
            .launchIn(scope)
    }

    // FIRST_MESSAGE_SENT: MeshActivity.Send = any transmission to the radio
    // MeshActivity is a sealed class with data object Send and Receive — no 'is' needed
    private fun watchMeshActivity() {
        radioInterfaceService.meshActivity
            .onEach { activity ->
                when (activity) {
                    MeshActivity.Send    -> unlock(AchievementId.FIRST_MESSAGE_SENT)
                    MeshActivity.Receive -> Unit
                }
            }
            .launchIn(scope)
    }

    // FIRST_DM_RECEIVED: getUnreadCountTotal rises when an unread message arrives
    private fun watchDmReceived() {
        packetRepository.getUnreadCountTotal()
            .distinctUntilChanged()
            .filter { it > 0 }
            .onEach { unlock(AchievementId.FIRST_DM_RECEIVED) }
            .launchIn(scope)
    }

    // TRACEROUTE: tracerouteResponse is null until completion
    private fun watchTraceroute() {
        serviceRepository.tracerouteResponse
            .filterNotNull()
            .onEach { unlock(AchievementId.TRACEROUTE) }
            .launchIn(scope)
    }

    // TELEMETRY_RECEIVED: battery_level > 0 indicates device metrics received
    // battery_level is Int? in generated Wire — safe null check required
    private fun watchTelemetry() {
        nodeRepository.nodeDBbyNum
            .map { nodes -> nodes.values.any { (it.deviceMetrics.battery_level ?: 0) > 0 } }
            .distinctUntilChanged()
            .filter { it }
            .onEach { unlock(AchievementId.TELEMETRY_RECEIVED) }
            .launchIn(scope)
    }

    // SENSOR_CONNECTED: non-default environmentMetrics = environmental sensor present
    private fun watchSensor() {
        nodeRepository.nodeDBbyNum
            .map { nodes -> nodes.values.any { it.environmentMetrics != EnvironmentMetrics() } }
            .distinctUntilChanged()
            .filter { it }
            .onEach { unlock(AchievementId.SENSOR_CONNECTED) }
            .launchIn(scope)
    }

    // ── Explicit triggers (called from the UI/ViewModel layer) ─────────────────────

    /** Called from FirmwareUpdateViewModel when state == FirmwareUpdateState.Success */
    fun onFirmwareUpdated() = scope.launch { unlock(AchievementId.FIRMWARE_UPDATED) }

    /** Called from the profile/QR export action */
    fun onProfileExported() = scope.launch { unlock(AchievementId.PROFILE_EXPORTED) }

    private suspend fun unlock(id: AchievementId) {
        val firstTime = repository.unlock(id)
        if (firstTime) logger.d { "unlock → $id" }
    }
}
