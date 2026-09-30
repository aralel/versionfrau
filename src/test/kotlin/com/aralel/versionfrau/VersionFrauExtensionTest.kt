package com.aralel.versionfrau

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VersionFrauExtensionTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var extension: VersionFrauExtension

    @BeforeEach
    fun setup() {
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        extension = VersionFrauExtension(project)
        extension.versionFile = File(projectDir, "version.properties")
    }

    @Test
    fun `readVersion creates default version file when none exists`() {
        val version = extension.readVersion()
        assertEquals(1, version.major)
        assertEquals(0, version.minor)
        assertEquals(0, version.patch)
        assertEquals(0, version.build)
        assertTrue(extension.versionFile.exists())
    }

    @Test
    fun `writeVersion and readVersion round-trip correctly`() {
        val versionData = VersionData(2, 3, 4, 5)
        extension.writeVersion(versionData)

        val readBack = extension.readVersion()
        assertEquals(2, readBack.major)
        assertEquals(3, readBack.minor)
        assertEquals(4, readBack.patch)
        assertEquals(5, readBack.build)
    }

    @Test
    fun `versionName returns four-part format for debug builds`() {
        extension.writeVersion(VersionData(1, 2, 3, 7))
        extension.isDebugBuild = true
        assertEquals("1.2.3.7", extension.versionName)
    }

    @Test
    fun `versionName returns three-part format for release builds`() {
        extension.writeVersion(VersionData(1, 2, 3, 7))
        extension.isDebugBuild = false
        assertEquals("1.2.3", extension.versionName)
    }

    @Test
    fun `versionCode includes build for debug`() {
        extension.writeVersion(VersionData(1, 2, 3, 7))
        extension.isDebugBuild = true
        assertEquals(1_002_003_007, extension.versionCode)
    }

    @Test
    fun `versionCode excludes build for release`() {
        extension.writeVersion(VersionData(1, 2, 3, 7))
        extension.isDebugBuild = false
        assertEquals(1_002_003_000, extension.versionCode)
    }

    @Test
    fun `fullVersionName always returns four-part format`() {
        extension.writeVersion(VersionData(1, 2, 3, 7))
        extension.isDebugBuild = false
        assertEquals("1.2.3.7", extension.fullVersionName)
    }

    @Test
    fun `buildTime returns non-empty formatted date string`() {
        val buildTime = extension.buildTime
        assertTrue(buildTime.isNotEmpty())
        // Format is "MMM dd, yyyy" — verify it matches the pattern
        assertTrue(buildTime.matches(Regex("[A-Z][a-z]{2} \\d{2}, \\d{4}")))
    }

    @Test
    fun `planned build increment is reflected in versionName and stays stable after the increment runs`() {
        extension.writeVersion(VersionData(1, 2, 3, 7))
        extension.isDebugBuild = true
        extension.plannedIncrement = PlannedIncrement.BUILD

        assertEquals("1.2.3.8", extension.versionName)
        assertEquals(1_002_003_008, extension.versionCode)

        // incrementBuildVersion writes the new value — the target must not be incremented twice.
        extension.writeVersion(VersionData(1, 2, 3, 8))
        assertEquals("1.2.3.8", extension.versionName)
        assertEquals("v1.2.3.8", extension.outputVersionSuffix(isDebugVariant = true))
    }

    @Test
    fun `planned patch increment resets build in versionName and output suffix`() {
        extension.writeVersion(VersionData(1, 2, 3, 7))
        extension.isDebugBuild = false
        extension.plannedIncrement = PlannedIncrement.PATCH

        assertEquals("1.2.4", extension.versionName)
        assertEquals(1_002_004_000, extension.versionCode)
        assertEquals("v1.2.4", extension.outputVersionSuffix(isDebugVariant = false))
    }
}
