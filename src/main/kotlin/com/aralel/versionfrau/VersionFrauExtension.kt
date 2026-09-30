package com.aralel.versionfrau

import org.gradle.api.Project
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class VersionFrauExtension(private val project: Project) {

    var versionFile: File = project.file("version.properties")

    /**
     * Returns the current build time formatted as "MMM dd, yyyy" (e.g. "Mar 19, 2026").
     * Intended for use with Android's buildConfigField.
     */
    val buildTime: String
        get() = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))

    /**
     * Set by the plugin at apply-time by inspecting Gradle's requested task names.
     * true  → debug build  (versionName includes build, versionCode includes build)
     * false → release build (versionName is major.minor.patch, versionCode excludes build)
     */
    internal var isDebugBuild: Boolean = false

    /**
     * Set by the plugin at apply-time: the increment this invocation's requested tasks will
     * perform. [versionName], [versionCode] and output file names use the version AFTER this
     * increment, so the APK/AAB contents and file name match the version written to disk.
     */
    internal var plannedIncrement: PlannedIncrement = PlannedIncrement.NONE

    /** First computed target version — stays stable after the increment task has run. */
    private var targetVersionSnapshot: VersionData? = null

    val major: Int get() = readVersion().major
    val minor: Int get() = readVersion().minor
    val patch: Int get() = readVersion().patch
    val build: Int get() = readVersion().build

    /**
     * Returns the version name for the current build type:
     * - Debug:   "major.minor.patch.build"  (e.g. "1.0.3.42")
     * - Release: "major.minor.patch"         (e.g. "1.0.3")
     */
    val versionName: String get() = versionNameFor(isDebugVariant = isDebugBuild)

    /**
     * Returns the version code for the current build type:
     * - Debug:   major * 1_000_000_000 + minor * 1_000_000 + patch * 1_000 + build
     * - Release: major * 1_000_000_000 + minor * 1_000_000 + patch * 1_000
     *
     * The release code always ends in 000, leaving room for up to 999 debug builds per patch.
     */
    val versionCode: Int get() = versionCodeFor(isDebugVariant = isDebugBuild)

    /** Always returns "major.minor.patch.build" regardless of build type. */
    val fullVersionName: String get() {
        val version = targetVersion()
        return "${version.major}.${version.minor}.${version.patch}.${version.build}"
    }

    /**
     * Version name for one variant's build type, independent of [isDebugBuild]. Used to give debug
     * variants the four-part name even when release variants are built in the same invocation.
     */
    internal fun versionNameFor(isDebugVariant: Boolean): String {
        val version = targetVersion()
        return if (isDebugVariant) {
            "${version.major}.${version.minor}.${version.patch}.${version.build}"
        } else {
            "${version.major}.${version.minor}.${version.patch}"
        }
    }

    /** Version code for one variant's build type, independent of [isDebugBuild]. */
    internal fun versionCodeFor(isDebugVariant: Boolean): Int {
        val version = targetVersion()
        return if (isDebugVariant) {
            version.major * 1_000_000_000 + version.minor * 1_000_000 + version.patch * 1_000 + version.build
        } else {
            version.major * 1_000_000_000 + version.minor * 1_000_000 + version.patch * 1_000
        }
    }

    /**
     * Version suffix used in APK / AAB file names, e.g. "v1.0.3.42" (debug) or "v1.0.3" (release).
     */
    internal fun outputVersionSuffix(isDebugVariant: Boolean): String = "v${versionNameFor(isDebugVariant)}"

    /**
     * The version this build produces: the version file plus [plannedIncrement]. Snapshotted on
     * first use so reading it after the increment task ran does not apply the increment twice.
     */
    internal fun targetVersion(): VersionData {
        if (plannedIncrement == PlannedIncrement.NONE) return readVersion()
        return targetVersionSnapshot ?: plannedIncrement.applyTo(readVersion()).also { targetVersionSnapshot = it }
    }

    internal fun readVersion(): VersionData {
        if (!versionFile.exists()) {
            val defaultVersion = VersionData(1, 0, 0, 0)
            writeVersion(defaultVersion)
            return defaultVersion
        }

        val properties = java.util.Properties()
        versionFile.inputStream().use { properties.load(it) }

        return VersionData(
            major = properties.getProperty("major", "1").toInt(),
            minor = properties.getProperty("minor", "0").toInt(),
            patch = properties.getProperty("patch", "0").toInt(),
            build = properties.getProperty("build", "0").toInt()
        )
    }

    internal fun writeVersion(versionData: VersionData) {
        val properties = java.util.Properties()
        properties.setProperty("major", versionData.major.toString())
        properties.setProperty("minor", versionData.minor.toString())
        properties.setProperty("patch", versionData.patch.toString())
        properties.setProperty("build", versionData.build.toString())
        versionFile.outputStream().use { properties.store(it, "VersionFrau - Auto-managed version file") }
    }
}

data class VersionData(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: Int
)

/** The version increment a Gradle invocation will perform, resolved from the requested task names. */
internal enum class PlannedIncrement {
    NONE,
    BUILD,
    PATCH;

    fun applyTo(currentVersion: VersionData): VersionData = when (this) {
        NONE -> currentVersion
        BUILD -> currentVersion.copy(build = currentVersion.build + 1)
        PATCH -> currentVersion.copy(patch = currentVersion.patch + 1, build = 0)
    }
}
