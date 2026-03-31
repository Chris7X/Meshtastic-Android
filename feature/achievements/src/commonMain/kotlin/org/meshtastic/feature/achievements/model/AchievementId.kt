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

package org.meshtastic.feature.achievements.model

/**
 * Achievement identifiers for MVP (Epic B).
 * Add new IDs here — engine and repository handle the rest.
 */
enum class AchievementId {
    FIRST_NODE,           // First BLE connection to a node
    FIRST_MESSAGE_SENT,   // First text message sent on the mesh
    FIRST_DM_RECEIVED,    // First direct message received
    TEN_NODES,            // 10+ distinct nodes discovered in the nodeDB
    TRACEROUTE,           // Traceroute completed successfully
    PROFILE_EXPORTED,     // Profile/QR exported
    FIRMWARE_UPDATED,     // Firmware update applied
    NODE_7DAYS,           // Node continuously active for 7+ days (WorkManager)
    TELEMETRY_RECEIVED,   // First device telemetry received
    SENSOR_CONNECTED,     // First ambient sensor detected
}
