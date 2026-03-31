/*
 * Copyright (c) 2026 Chris7X
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

/**
 * Player audio stile WhatsApp per le bubble Voice Burst.
 *
 * [playingFilePathFlow] viene dal ViewModel (audioPlayer.playingFilePath).
 * Quando vale [audioFilePath], questa bubble mostra ■ e la waveform animata.
 * Quando vale altro o null, mostra ▶ e la waveform statica.
 *
 * Così il bug "rimane in play" è impossibile: la sorgente di verità è il player reale,
 * non uno stato locale al composable.
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
    // isPlaying = true solo se questo file è quello ATTUALMENTE in riproduzione nel player
    val currentlyPlayingPath by (playingFilePathFlow
        ?.collectAsState()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) })
    val isPlaying = audioFilePath != null && currentlyPlayingPath == audioFilePath

    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Bottone play/stop
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
                    contentDescription = if (isPlaying) "Stop" else "Play",
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
                text = "Audio non disponibile",
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
