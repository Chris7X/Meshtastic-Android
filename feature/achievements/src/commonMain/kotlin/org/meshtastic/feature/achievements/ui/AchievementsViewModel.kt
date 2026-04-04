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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.feature.achievements.model.AchievementId
import org.meshtastic.feature.achievements.repository.AchievementRepository

/**
 * ViewModel for the Achievements screen and global unlock snackbars.
 *
 * One-shot pattern identical to scrollToTopEventFlow in UIViewModel:
 * SharedFlow with extraBufferCapacity + DROP_OLDEST.
 *
 * Aggregation: instead of one event per achievement, emits one event
 * with the total count of the unlock batch — a single generic message.
 */
@KoinViewModel
class AchievementsViewModel(
    private val repository: AchievementRepository,
) : ViewModel() {

    /** Full achievement list, updated reactively. */
    val achievements = repository.achievements.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * One-shot event emitted when one or more achievements are unlocked.
     * Collect with LaunchedEffect in any Scaffold host.
     */
    private val _unlockEvents = MutableSharedFlow<UnlockEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val unlockEvents = _unlockEvents.asSharedFlow()

    init {
        repository.achievements
            .onEach { list ->
                val newlyUnlocked = list.filter { it.isUnlocked && !it.seen }
                if (newlyUnlocked.isNotEmpty()) {
                // Mark all as seen before emitting the aggregated event
                newlyUnlocked.forEach { repository.markSeen(it.id) }
                // Emit a single event with the count
                _unlockEvents.emit(UnlockEvent(count = newlyUnlocked.size))
                }
            }
            .launchIn(viewModelScope)
    }

    fun markSeen(id: AchievementId) = viewModelScope.launch { repository.markSeen(id) }
}

/** One-shot event: one or more achievements have been unlocked. */
data class UnlockEvent(val count: Int)
