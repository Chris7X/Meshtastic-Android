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
package org.meshtastic.feature.achievements.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.achievements.engine.AchievementRulesEngine
import org.meshtastic.feature.achievements.repository.AchievementRepository
import org.meshtastic.feature.achievements.repository.DefaultAchievementRepository

@Module
class FeatureAchievementsAndroidModule {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Single
    @Named("AchievementsDataStore")
    fun provideAchievementsDataStore(context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.preferencesDataStoreFile("achievements") }
        )

    @Single
    fun provideAchievementRepository(
        @Named("AchievementsDataStore") dataStore: DataStore<Preferences>
    ): AchievementRepository = DefaultAchievementRepository(dataStore)

    @Single
    fun provideAchievementRulesEngine(
        nodeRepository: NodeRepository,
        radioInterfaceService: RadioInterfaceService,
        serviceRepository: ServiceRepository,
        packetRepository: PacketRepository,
        repository: AchievementRepository,
        @Named("ProcessLifecycle") processLifecycle: Lifecycle,
    ): AchievementRulesEngine = AchievementRulesEngine(
        nodeRepository = nodeRepository,
        radioInterfaceService = radioInterfaceService,
        serviceRepository = serviceRepository,
        packetRepository = packetRepository,
        repository = repository,
        scope = processLifecycle.coroutineScope,
    )
}
