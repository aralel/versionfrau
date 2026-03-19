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

    val major: Int get() = readVersion().major
    val minor: Int get() = readVersion().minor
    val patch: Int get() = readVersion().patch
    val build: Int get() = readVersion().build

    /**
     * Returns the version name for the current build type:
     * - Debug:   "major.minor.patch.build"  (e.g. "1.0.3.42")
     * - Release: "major.minor.patch"         (e.g. "1.0.3")
     */
    val versionName: String get() {
        val version = readVersion()
        return if (isDebugBuild) {
            "${version.major}.${version.minor}.${version.patch}.${version.build}"
        } else {
            "${version.major}.${version.minor}.${version.patch}"
        }
    }

    /**
     * Returns the version code for the current build type:
     * - Debug:   major * 1_000_000 + minor * 10_000 + patch * 100 + build
     * - Release: major * 1_000_000 + minor * 10_000 + patch * 100
     *
     * The release code always ends in 00, leaving room for up to 99 debug builds per patch.
     */
    val versionCode: Int get() {
        val version = readVersion()
        return if (isDebugBuild) {
            version.major * 1_000_000 + version.minor * 10_000 + version.patch * 100 + version.build
        } else {
            version.major * 1_000_000 + version.minor * 10_000 + version.patch * 100
        }
    }

    /** Always returns "major.minor.patch.build" regardless of build type. */
    val fullVersionName: String get() {
        val version = readVersion()
        return "${version.major}.${version.minor}.${version.patch}.${version.build}"
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
