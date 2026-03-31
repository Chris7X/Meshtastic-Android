/*
 * Copyright (c) 2026 Chris7X
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package org.meshtastic.feature.achievements.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.feature.achievements.model.AchievementId
import org.meshtastic.feature.achievements.model.AchievementRecord
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel = koinViewModel(),
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar aggregata: un solo messaggio per batch di unlock
    LaunchedEffect(Unit) {
        viewModel.unlockEvents.collect { event ->
            val message = when {
                event.count == 1 -> "Nuovo traguardo sbloccato!"
                event.count > 1 -> "${event.count} nuovi traguardi sbloccati!"
                else -> return@collect
            }
            snackbarHostState.showSnackbar(message = message)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Traguardi Meshtastic") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(achievements, key = { it.id.name }) { record ->
                AchievementItem(record)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AchievementItem(record: AchievementRecord) {
    // Elementi bloccati: ridotta opacità per segnalare stato senza aggiungere rumore visivo
    val itemAlpha = if (record.isUnlocked) 1f else 0.45f

    ListItem(
        modifier = Modifier.alpha(itemAlpha),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        headlineContent = {
            Text(
                text = record.id.displayName(),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            if (record.isUnlocked) {
                val date = DateFormat.getDateInstance().format(Date(record.unlockedAt!!))
                Text(
                    text = "✓ Sbloccato il $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = record.id.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            AchievementIcon(record)
        },
    )
}

/**
 * Icona circolare monocromatica:
 * - Sbloccato: sfondo primaryContainer pieno + icona tematica
 * - Bloccato: solo bordo outline su surfaceVariant + icona tematica
 */
@Composable
private fun AchievementIcon(record: AchievementRecord) {
    val icon = record.id.iconVector()
    val size = 40.dp

    if (record.isUnlocked) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ─── Extension functions on AchievementId ───────────────────────────────────

/** Short, plain name — no childish metaphors. */
fun AchievementId.displayName(): String = when (this) {
    AchievementId.FIRST_NODE         -> "First node connected"
    AchievementId.FIRST_MESSAGE_SENT -> "First message sent"
    AchievementId.FIRST_DM_RECEIVED  -> "First message received"
    AchievementId.TEN_NODES          -> "10 nodes discovered"
    AchievementId.TRACEROUTE         -> "Traceroute completed"
    AchievementId.PROFILE_EXPORTED   -> "Profile exported"
    AchievementId.FIRMWARE_UPDATED   -> "Firmware updated"
    AchievementId.NODE_7DAYS         -> "Node active 7 days"
    AchievementId.TELEMETRY_RECEIVED -> "Telemetry received"
    AchievementId.SENSOR_CONNECTED   -> "Environmental sensor detected"
}

/**
 * Technical description shown only when the achievement is locked.
 * Explains the unlock condition — no hyperbole.
 */
fun AchievementId.description(): String = when (this) {
    AchievementId.FIRST_NODE         -> "Connect a Meshtastic node via Bluetooth."
    AchievementId.FIRST_MESSAGE_SENT -> "Send a message on the mesh network."
    AchievementId.FIRST_DM_RECEIVED  -> "Receive a direct message from another node."
    AchievementId.TEN_NODES          -> "Reach 10 distinct nodes in the local database."
    AchievementId.TRACEROUTE         -> "Run a traceroute to a remote node."
    AchievementId.PROFILE_EXPORTED   -> "Export your node profile via QR or file."
    AchievementId.FIRMWARE_UPDATED   -> "Apply a firmware update to the connected node."
    AchievementId.NODE_7DAYS         -> "Keep the node active for 7 continuous days."
    AchievementId.TELEMETRY_RECEIVED -> "Receive device telemetry data from a remote node."
    AchievementId.SENSOR_CONNECTED   -> "Detect a node with an active environmental sensor."
}

/** Material 3 icon consistent with the technical domain of each achievement. */
fun AchievementId.iconVector(): ImageVector = when (this) {
    AchievementId.FIRST_NODE         -> Icons.Rounded.BluetoothConnected
    AchievementId.FIRST_MESSAGE_SENT -> Icons.Rounded.Forum
    AchievementId.FIRST_DM_RECEIVED  -> Icons.Rounded.Share
    AchievementId.TEN_NODES          -> Icons.Rounded.Group
    AchievementId.TRACEROUTE         -> Icons.Rounded.DeviceHub
    AchievementId.PROFILE_EXPORTED   -> Icons.Rounded.QrCode2
    AchievementId.FIRMWARE_UPDATED   -> Icons.Rounded.Download
    AchievementId.NODE_7DAYS         -> Icons.Rounded.CheckCircle
    AchievementId.TELEMETRY_RECEIVED -> Icons.Rounded.MonitorHeart
    AchievementId.SENSOR_CONNECTED   -> Icons.Rounded.Memory
}
