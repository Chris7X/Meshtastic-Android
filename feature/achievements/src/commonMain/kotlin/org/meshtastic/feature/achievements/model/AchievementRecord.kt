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
 * Persistent state of a single achievement.
 *
 * @param id         Achievement identifier.
 * @param unlockedAt Epoch ms of the unlock, null if still locked.
 * @param seen       True if the unlock snackbar has already been shown.
 */
data class AchievementRecord(
    val id: AchievementId,
    val unlockedAt: Long? = null,
    val seen: Boolean = false,
) {
    val isUnlocked: Boolean get() = unlockedAt != null
}
