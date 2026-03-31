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
package org.meshtastic.feature.voiceburst.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Modulo Koin commonMain per il feature Voice Burst.
 *
 * Il @ComponentScan scansiona il package e registra automaticamente tramite KSP:
 *   - VoiceBurstViewModel (@KoinViewModel con @InjectedParam destNodeId)
 *
 * Le dipendenze Android-only (AudioRecorder, Codec2Encoder, DataStore, Repository)
 * sono registrate in [FeatureVoiceBurstAndroidModule] (androidMain).
 */
@Module
@ComponentScan("org.meshtastic.feature.voiceburst")
class FeatureVoiceBurstModule
