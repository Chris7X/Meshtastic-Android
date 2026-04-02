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

package org.meshtastic.feature.achievements.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.feature.achievements.model.AchievementId
import org.meshtastic.feature.achievements.repository.AchievementRepository
import java.util.concurrent.TimeUnit

private val logger = Logger.withTag(NodeActiveWorker.WORK_NAME)

/**
 * Checks daily whether the local node has been active for 7+ days.
 *
 * Strategy: compares the FIRST_NODE unlock timestamp (stored in
 * AchievementRepository) with the current time — no extra fields
 * on MyNodeInfo needed (confirmed: MyNodeInfo has no firstSeen).
 *
 * Same pattern as MeshLogCleanupWorker (PeriodicWork, 1 day, KEEP).
 * Enqueue from Application.onCreate via [NodeActiveWorker.enqueue].
 */
class NodeActiveWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val achievementRepository: AchievementRepository by inject()

    override suspend fun doWork(): Result = try {
        // Collect only the current snapshot — the flow is a DataStore-backed
        // state flow, so first() returns immediately with the current value.
        val records = achievementRepository.achievements.first()
        val firstNodeRecord = records.find { it.id == AchievementId.FIRST_NODE }
        val firstSeenMs = firstNodeRecord?.unlockedAt

        if (firstSeenMs == null) {
            logger.d { "FIRST_NODE not yet unlocked — no node ever connected" }
            return Result.success()
        }

        val daysActive = (System.currentTimeMillis() - firstSeenMs) / MS_PER_DAY
        if (daysActive >= 7) {
            achievementRepository.unlock(AchievementId.NODE_7DAYS)
            logger.i { "NODE_7DAYS unlocked after $daysActive days" }
        } else {
            logger.d { "NODE_7DAYS: $daysActive/7 days — not yet" }
        }

        Result.success()
    } catch (e: Exception) {
        logger.e(e) { "NodeActiveWorker failed" }
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "node_active_7days_worker"
        private const val MS_PER_DAY = 86_400_000L

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<NodeActiveWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
