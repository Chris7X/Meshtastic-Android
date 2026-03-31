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

package org.meshtastic.feature.achievements.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.meshtastic.feature.achievements.model.AchievementId
import org.meshtastic.feature.achievements.model.AchievementRecord

/**
 * Contract for achievement persistence.
 * All operations are idempotent.
 */
interface AchievementRepository {
    /** Reactive flow of the full achievement list. */
    val achievements: Flow<List<AchievementRecord>>

    /** Unlocks [id] with [timestamp] (default = now). Returns true if first unlock, false if already unlocked. */
    suspend fun unlock(id: AchievementId, timestamp: Long = System.currentTimeMillis()): Boolean

    /** Marks the unlock notification for [id] as shown. */
    suspend fun markSeen(id: AchievementId)
}

/**
 * Implementation using Preferences DataStore — same flavour already in use
 * in the app (see GoogleMapsPrefs.kt).
 *
 * Keys:
 *   achievement_<id>_unlocked_at  Long    (absent = locked)
 *   achievement_<id>_seen         Boolean (absent = false)
 */
class DefaultAchievementRepository(
    private val dataStore: DataStore<Preferences>,
) : AchievementRepository {

    override val achievements: Flow<List<AchievementRecord>> =
        dataStore.data.map { prefs ->
            AchievementId.entries.map { id ->
                AchievementRecord(
                    id = id,
                    unlockedAt = prefs[unlockedAtKey(id)],
                    seen = prefs[seenKey(id)] ?: false,
                )
            }
        }

    override suspend fun unlock(id: AchievementId, timestamp: Long): Boolean {
        var firstUnlock = false
        dataStore.edit { prefs ->
            // Idempotenza: non sovrascrivere il timestamp originale
            if (prefs[unlockedAtKey(id)] == null) {
                prefs[unlockedAtKey(id)] = timestamp
                firstUnlock = true
            }
        }
        return firstUnlock
    }

    override suspend fun markSeen(id: AchievementId) {
        dataStore.edit { prefs -> prefs[seenKey(id)] = true }
    }

    private fun unlockedAtKey(id: AchievementId) =
        longPreferencesKey("achievement_${id.name.lowercase()}_unlocked_at")

    private fun seenKey(id: AchievementId) =
        booleanPreferencesKey("achievement_${id.name.lowercase()}_seen")
}
