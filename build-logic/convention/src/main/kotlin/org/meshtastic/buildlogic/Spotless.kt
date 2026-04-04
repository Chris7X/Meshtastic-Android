/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Project

private const val DEFAULT_SPOTLESS_RATCHET_REF = "origin/main"
private const val SPOTLESS_RATCHET_PROPERTY = "spotless.ratchet"
private const val SPOTLESS_RATCHET_FROM_PROPERTY = "spotless.ratchetFrom"
private const val GRADLE_KTS_LICENSE_HEADER_DELIMITER = "(^(?![\\/ ]\\*).*$)"

internal fun Project.configureSpotless(extension: SpotlessExtension) {
    val ktlintVersion = libs.version("ktlint")
    val ktfmtVersion = libs.version("ktfmt")

    val editorConfigPath = rootProject.file("config/spotless/.editorconfig").path
    val kotlinLicenseHeader = rootProject.file("config/spotless/copyright.kt")
    val gradleLicenseHeader = rootProject.file("config/spotless/copyright.kts")

    val ratchetRef = providers.gradleProperty(SPOTLESS_RATCHET_FROM_PROPERTY)
        .orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_SPOTLESS_RATCHET_REF

    extension.apply {
        if (shouldEnableSpotlessRatchet(ratchetRef)) {
            logger.info("Spotless ratchet enabled from '{}'", ratchetRef)
            ratchetFrom(ratchetRef)
        } else {
            logger.info("Spotless ratchet disabled")
        }
        kotlin {
            target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktfmt(ktfmtVersion).kotlinlangStyle().configure { it.setMaxWidth(120) }
            ktlint(ktlintVersion).setEditorConfigPath(editorConfigPath)
            licenseHeaderFile(kotlinLicenseHeader)
        }

        kotlinGradle {
            target("**/*.gradle.kts")
            targetExclude("**/build/**", "**/dependencies/**")
            ktfmt(ktfmtVersion).kotlinlangStyle().configure { it.setMaxWidth(120) }
            ktlint(ktlintVersion).setEditorConfigPath(editorConfigPath)
            licenseHeaderFile(gradleLicenseHeader, GRADLE_KTS_LICENSE_HEADER_DELIMITER)
        }
    }
}

private fun Project.shouldEnableSpotlessRatchet(ratchetRef: String): Boolean {
    val forced = providers.gradleProperty(SPOTLESS_RATCHET_PROPERTY)
        .orNull
        ?.trim()
        ?.toBooleanLenient()

    if (forced == false) return false

    if (!gitRefExists(ratchetRef)) {
        if (forced == true) {
            logger.warn(
                "Spotless ratchet requested, but Git ref '{}' is not available. Ratchet will be disabled.",
                ratchetRef
            )
        }
        return false
    }

    if (forced == true) return true

    val currentBranch = currentBranchName()?.normalizeBranchName() ?: return false
    val expectedBranch = ratchetRef.branchName().normalizeBranchName()

    return currentBranch == expectedBranch
}

private fun Project.currentBranchName(): String? {
    val branchFromEnv = sequenceOf(
        providers.environmentVariable("GITHUB_HEAD_REF").orNull,
        providers.environmentVariable("GITHUB_REF_NAME").orNull,
        providers.environmentVariable("CI_COMMIT_REF_NAME").orNull,
        providers.environmentVariable("CI_COMMIT_BRANCH").orNull,
        providers.environmentVariable("BRANCH_NAME").orNull,
        providers.environmentVariable("BITBUCKET_BRANCH").orNull,
    )
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .map { it.normalizeBranchName() }
        .firstOrNull()

    if (branchFromEnv != null) return branchFromEnv

    return runGitCacheCompatible("symbolic-ref", "--short", "HEAD")
        ?.normalizeBranchName()
        ?: runGitCacheCompatible("rev-parse", "--abbrev-ref", "HEAD")
            ?.takeUnless { it == "HEAD" }
            ?.normalizeBranchName()
}

private fun Project.gitRefExists(ref: String): Boolean =
    runCatching {
        providers.exec {
            commandLine("git", "rev-parse", "--verify", "--quiet", "$ref^{commit}")
            workingDir(rootDir)
            isIgnoreExitValue = true
        }.result.get().exitValue == 0
    }.getOrDefault(false)

private fun Project.runGitCacheCompatible(vararg args: String): String? =
    runCatching {
        val execOutput = providers.exec {
            commandLine("git", *args)
            workingDir(rootDir)
            isIgnoreExitValue = true
        }

        if (execOutput.result.get().exitValue != 0) return null

        execOutput.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

private fun String.branchName(): String = substringAfterLast('/')

private fun String.normalizeBranchName(): String =
    removePrefix("refs/heads/")
        .removePrefix("refs/remotes/")
        .removePrefix("remotes/")
        .removePrefix("origin/")
        .trim()

private fun String.toBooleanLenient(): Boolean? = when (lowercase()) {
    "true", "1", "yes", "y", "on" -> true
    "false", "0", "no", "n", "off" -> false
    else -> null
}