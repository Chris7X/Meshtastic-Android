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

package org.meshtastic.feature.achievements.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.Route
import org.meshtastic.feature.achievements.ui.AchievementsScreen

/**
 * Adds the Achievements route to the host navigation graph.
 *
 * Follows the same pattern as settingsGraph(), firmwareGraph() etc.
 * The [backStack] parameter is used to enable back-navigation from the screen.
 *
 * Registered in app/Main.kt alongside the other feature graphs.
 */
fun EntryProviderScope<NavKey>.achievementsGraph(backStack: NavBackStack<NavKey>) {
    entry<AchievementsRoutes.Achievements> {
        AchievementsScreen(
            viewModel = koinViewModel(),
            onBack = { backStack.removeLastOrNull() },
        )
    }
}

object AchievementsRoutes {
    @kotlinx.serialization.Serializable
    data object Achievements : Route  // Route extends NavKey — compatibile con il nav system
}
