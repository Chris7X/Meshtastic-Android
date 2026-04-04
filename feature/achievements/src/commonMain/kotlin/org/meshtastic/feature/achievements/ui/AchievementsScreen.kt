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
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.material3.IconButton
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.achievement_desc_first_dm_received
import org.meshtastic.core.resources.achievement_desc_first_message_sent
import org.meshtastic.core.resources.achievement_desc_first_node
import org.meshtastic.core.resources.achievement_desc_firmware_updated
import org.meshtastic.core.resources.achievement_desc_node_7days
import org.meshtastic.core.resources.achievement_desc_profile_exported
import org.meshtastic.core.resources.achievement_desc_sensor_connected
import org.meshtastic.core.resources.achievement_desc_telemetry_received
import org.meshtastic.core.resources.achievement_desc_ten_nodes
import org.meshtastic.core.resources.achievement_desc_traceroute
import org.meshtastic.core.resources.achievement_name_first_dm_received
import org.meshtastic.core.resources.achievement_name_first_message_sent
import org.meshtastic.core.resources.achievement_name_first_node
import org.meshtastic.core.resources.achievement_name_firmware_updated
import org.meshtastic.core.resources.achievement_name_node_7days
import org.meshtastic.core.resources.achievement_name_profile_exported
import org.meshtastic.core.resources.achievement_name_sensor_connected
import org.meshtastic.core.resources.achievement_name_telemetry_received
import org.meshtastic.core.resources.achievement_name_ten_nodes
import org.meshtastic.core.resources.achievement_name_traceroute
import org.meshtastic.core.resources.achievement_title_screen
import org.meshtastic.core.resources.achievement_unlocked_at
import org.meshtastic.core.resources.achievement_unlocked_multiple
import org.meshtastic.core.resources.achievement_unlocked_single
import org.meshtastic.feature.achievements.model.AchievementId
import org.meshtastic.feature.achievements.model.AchievementRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel = koinViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Aggregate snackbar: one message per batch of unlocks
    val unlockSingleStr = stringResource(Res.string.achievement_unlocked_single)
    val unlockMultipleStr = stringResource(Res.string.achievement_unlocked_multiple)
    LaunchedEffect(Unit) {
        viewModel.unlockEvents.collect { event ->
            val message = when {
                event.count == 1 -> unlockSingleStr
                event.count > 1 -> unlockMultipleStr.replace("%1\$d", event.count.toString())
                else -> return@collect
            }
            snackbarHostState.showSnackbar(message = message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.achievement_title_screen)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
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
    // Locked items: reduced opacity to indicate state without adding visual noise
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
                val date = DateFormatter.formatDate(record.unlockedAt!!)
                Text(
                    text = stringResource(Res.string.achievement_unlocked_at, date),
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
 * Circular monochromatic icon:
 * - Unlocked: full primaryContainer background + themed icon
 * - Locked: outline border on surfaceVariant + themed icon
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
@Composable
fun AchievementId.displayName(): String = when (this) {
    AchievementId.FIRST_NODE         -> stringResource(Res.string.achievement_name_first_node)
    AchievementId.FIRST_MESSAGE_SENT -> stringResource(Res.string.achievement_name_first_message_sent)
    AchievementId.FIRST_DM_RECEIVED  -> stringResource(Res.string.achievement_name_first_dm_received)
    AchievementId.TEN_NODES          -> stringResource(Res.string.achievement_name_ten_nodes)
    AchievementId.TRACEROUTE         -> stringResource(Res.string.achievement_name_traceroute)
    AchievementId.PROFILE_EXPORTED   -> stringResource(Res.string.achievement_name_profile_exported)
    AchievementId.FIRMWARE_UPDATED   -> stringResource(Res.string.achievement_name_firmware_updated)
    AchievementId.NODE_7DAYS         -> stringResource(Res.string.achievement_name_node_7days)
    AchievementId.TELEMETRY_RECEIVED -> stringResource(Res.string.achievement_name_telemetry_received)
    AchievementId.SENSOR_CONNECTED   -> stringResource(Res.string.achievement_name_sensor_connected)
}

/**
 * Technical description shown only when the achievement is locked.
 * Explains the unlock condition — no hyperbole.
 */
@Composable
fun AchievementId.description(): String = when (this) {
    AchievementId.FIRST_NODE         -> stringResource(Res.string.achievement_desc_first_node)
    AchievementId.FIRST_MESSAGE_SENT -> stringResource(Res.string.achievement_desc_first_message_sent)
    AchievementId.FIRST_DM_RECEIVED  -> stringResource(Res.string.achievement_desc_first_dm_received)
    AchievementId.TEN_NODES          -> stringResource(Res.string.achievement_desc_ten_nodes)
    AchievementId.TRACEROUTE         -> stringResource(Res.string.achievement_desc_traceroute)
    AchievementId.PROFILE_EXPORTED   -> stringResource(Res.string.achievement_desc_profile_exported)
    AchievementId.FIRMWARE_UPDATED   -> stringResource(Res.string.achievement_desc_firmware_updated)
    AchievementId.NODE_7DAYS         -> stringResource(Res.string.achievement_desc_node_7days)
    AchievementId.TELEMETRY_RECEIVED -> stringResource(Res.string.achievement_desc_telemetry_received)
    AchievementId.SENSOR_CONNECTED   -> stringResource(Res.string.achievement_desc_sensor_connected)
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
