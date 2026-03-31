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
package org.meshtastic.feature.voiceburst.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import org.meshtastic.feature.voiceburst.model.VoiceBurstState

/**
 * Bottone PTT (Push-To-Talk) per Voice Burst.
 *
 * Visibile solo se [VoiceBurstViewModel.isVisible] == true (feature flag abilitato).
 * Disabilitato durante encoding/sending/rate limit.
 *
 * Stati visivi:
 *   Idle       → icona microfono, colore normale
 *   Recording  → icona microfono pulsante (animazione scale), colore error/rosso
 *   Encoding   → icona microfono, colore secondario, disabilitato
 *   Sending    → icona microfono, colore secondario, disabilitato
 *   Sent       → icona microfono, colore primary (feedback breve)
 *   Error      → icona MicOff, colore error
 *   Unsupported → nascosto (il chiamante non deve renderizzare il composable)
 *
 * @param state    Stato corrente della macchina a stati
 * @param onClick  Callback quando l'utente preme il bottone
 * @param modifier Modifier opzionale
 */
@Composable
fun VoiceBurstButton(
    state: VoiceBurstState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Abilitato in Idle (avvia), Recording (ferma), Sent (avvia subito) ed Error (reset)
    val isEnabled = state is VoiceBurstState.Idle
        || state is VoiceBurstState.Recording
        || state is VoiceBurstState.Sent
        || state is VoiceBurstState.Error
    val isRecording = state is VoiceBurstState.Recording
    val isError = state is VoiceBurstState.Error

    val tint by animateColorAsState(
        targetValue = when (state) {
            is VoiceBurstState.Recording -> MaterialTheme.colorScheme.error
            is VoiceBurstState.Sent      -> MaterialTheme.colorScheme.primary
            is VoiceBurstState.Error     -> MaterialTheme.colorScheme.error
            else                         -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "voiceBurstTint",
    )

    // Pulsazione durante registrazione
    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordingScale",
    )

    IconButton(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Anello di progresso durante recording: mostra quanto manca al secondo
            if (isRecording) {
                val progress = ((state as VoiceBurstState.Recording).elapsedMs / 1000f)
                    .coerceIn(0f, 1f)
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.error,
                    strokeWidth = 2.5.dp,
                    trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                )
            }
            Icon(
                imageVector = when {
                    isRecording                    -> Icons.Rounded.StopCircle
                    state is VoiceBurstState.Sent  -> Icons.Rounded.CheckCircle
                    isError                        -> Icons.Rounded.MicOff
                    else                           -> Icons.Rounded.Mic
                },
                contentDescription = when (state) {
                    is VoiceBurstState.Idle        -> "Registra Voice Burst"
                    is VoiceBurstState.Recording   -> "Registrazione ${(state.elapsedMs / 100) / 10f}s — tocca per inviare"
                    is VoiceBurstState.Encoding    -> "Encoding in corso"
                    is VoiceBurstState.Sending,
                    is VoiceBurstState.Queued      -> "Invio in corso"
                    is VoiceBurstState.Sent        -> "Inviato ✓"
                    is VoiceBurstState.Error       -> "Errore — tocca per riprovare"
                    is VoiceBurstState.Received    -> "Burst ricevuto"
                    is VoiceBurstState.Unsupported -> "Non supportato"
                },
                tint = tint,
                modifier = Modifier.size(24.dp).scale(scale),
            )
        }
    }
}
