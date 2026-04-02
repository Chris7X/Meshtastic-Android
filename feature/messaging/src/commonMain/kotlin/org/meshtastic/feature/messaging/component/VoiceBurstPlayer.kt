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
package org.meshtastic.feature.messaging.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sin

import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.voice_burst_player_play
import org.meshtastic.core.resources.voice_burst_player_stop
import org.meshtastic.core.resources.voice_burst_not_available
import org.jetbrains.compose.resources.stringResource

/**
 * WhatsApp-style audio player for Voice Burst bubbles.
 *
 * [playingFilePathFlow] comes from the ViewModel (audioPlayer.playingFilePath).
 * When it equals [audioFilePath], this bubble shows ■ and the animated waveform.
 * Otherwise, it shows ▶ and a static waveform.
 *
 * This design ensures the "stuck in play" bug is impossible: the single source of truth 
 * is the actual player, not a local state in the composable.
 */
@Composable
fun VoiceBurstPlayer(
    audioFilePath: String?,
    durationMs: Int,
    contentColor: Color,
    onPlay: (audioFilePath: String) -> Unit,
    playingFilePathFlow: StateFlow<String?>? = null,
    modifier: Modifier = Modifier,
) {
    // isPlaying = true only if this file is the one CURRENTLY being played by the player
    val currentlyPlayingPath by (playingFilePathFlow
        ?.collectAsState()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) })
    val isPlaying = audioFilePath != null && currentlyPlayingPath == audioFilePath

    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Play/stop button
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clickable(enabled = audioFilePath != null) {
                    audioFilePath?.let { onPlay(it) }
                },
            shape = CircleShape,
            color = contentColor.copy(alpha = 0.15f),
            contentColor = contentColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) 
                        stringResource(Res.string.voice_burst_player_stop) 
                    else 
                        stringResource(Res.string.voice_burst_player_play),
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            }
        }

        if (audioFilePath != null) {
            VoiceWaveform(
                isPlaying = isPlaying,
                contentColor = contentColor,
                modifier = Modifier.weight(1f).height(32.dp),
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
            )
        } else {
            Text(
                text = stringResource(Res.string.voice_burst_not_available),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun VoiceWaveform(
    isPlaying: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val sweepProgress by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sweep",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val barCount = 28
        val barWidth = 3.dp.toPx()
        val gap = if (barCount > 1) (size.width - barCount * barWidth) / (barCount - 1) else 0f
        val centerY = size.height / 2f

        val heights = (0 until barCount).map { i ->
            val t = i.toFloat() / barCount
            val base = abs(sin(t * Math.PI.toFloat() * 3.5f))
            val micro = abs(sin(t * Math.PI.toFloat() * 11f)) * 0.4f
            ((base + micro) * size.height * 0.85f).coerceAtLeast(size.height * 0.1f)
        }

        for (i in 0 until barCount) {
            val x = i * (barWidth + gap) + barWidth / 2f
            val h = heights[i]
            val barFraction = i.toFloat() / barCount
            val alpha = if (isPlaying && barFraction < sweepProgress) 1.0f else 0.45f
            drawLine(
                color = contentColor.copy(alpha = alpha),
                start = Offset(x, centerY - h / 2f),
                end = Offset(x, centerY + h / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatDuration(ms: Int): String {
    if (ms <= 0) return "0:01"
    val totalSec = (ms + 999) / 1000
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}
