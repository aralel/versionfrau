package com.aralel.versionfrau

import org.gradle.api.Project
import java.io.File

open class VersionFrauExtension(private val project: Project) {

    var versionFile: File = project.file("version.properties")

    val major: Int get() = readVersion().major
    val minor: Int get() = readVersion().minor
    val patch: Int get() = readVersion().patch
    val build: Int get() = readVersion().build

    val versionName: String get() {
        val version = readVersion()
        return "${version.major}.${version.minor}.${version.patch}"
    }

    val versionCode: Int get() {
        val version = readVersion()
        return version.major * 1_000_000 + version.minor * 10_000 + version.patch * 100 + version.build
    }

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
