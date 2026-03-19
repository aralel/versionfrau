package com.aralel.versionfrau

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

class DebugBuildIncrementTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File
    private lateinit var versionFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(projectDir, "settings.gradle.kts")
        settingsFile.writeText("""rootProject.name = "test-project"""")

        buildFile = File(projectDir, "build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("com.aralel.versionfrau")
            }
            """.trimIndent()
        )

        versionFile = File(projectDir, "version.properties")
    }

    private fun readVersionProperties(): Properties {
        val properties = Properties()
        versionFile.inputStream().use { properties.load(it) }
        return properties
    }

    @Test
    fun `incrementBuildVersion task increments build number`() {
        // Seed initial version
        val initialProperties = Properties()
        initialProperties.setProperty("major", "1")
        initialProperties.setProperty("minor", "0")
        initialProperties.setProperty("patch", "0")
        initialProperties.setProperty("build", "0")
        versionFile.outputStream().use { initialProperties.store(it, null) }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("incrementBuildVersion")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":incrementBuildVersion")?.outcome)

        val updatedProperties = readVersionProperties()
        assertEquals("1", updatedProperties.getProperty("build"))
    }

    @Test
    fun `consecutive debug increments increase build number each time`() {
        // Seed initial version
        val initialProperties = Properties()
        initialProperties.setProperty("major", "1")
        initialProperties.setProperty("minor", "0")
        initialProperties.setProperty("patch", "0")
        initialProperties.setProperty("build", "0")
        versionFile.outputStream().use { initialProperties.store(it, null) }

        // Run incrementBuildVersion three times
        repeat(3) {
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("incrementBuildVersion")
                .build()
        }

        val updatedProperties = readVersionProperties()
        assertEquals("3", updatedProperties.getProperty("build"))
    }

    @Test
    fun `build number increments from non-zero starting point`() {
        val initialProperties = Properties()
        initialProperties.setProperty("major", "2")
        initialProperties.setProperty("minor", "1")
        initialProperties.setProperty("patch", "3")
        initialProperties.setProperty("build", "10")
        versionFile.outputStream().use { initialProperties.store(it, null) }

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("incrementBuildVersion")
            .build()

        val updatedProperties = readVersionProperties()
        assertEquals("11", updatedProperties.getProperty("build"))
        // major, minor, patch should remain unchanged
        assertEquals("2", updatedProperties.getProperty("major"))
        assertEquals("1", updatedProperties.getProperty("minor"))
        assertEquals("3", updatedProperties.getProperty("patch"))
    }

    @Test
    fun `build number starts at 1 when version file does not exist`() {
        // Do NOT create version.properties — plugin should create it with defaults
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("incrementBuildVersion")
            .build()

        assertTrue(versionFile.exists())
        val updatedProperties = readVersionProperties()
        assertEquals("1", updatedProperties.getProperty("build"))
        assertEquals("1", updatedProperties.getProperty("major"))
        assertEquals("0", updatedProperties.getProperty("minor"))
        assertEquals("0", updatedProperties.getProperty("patch"))
    }

    @Test
    fun `standard build task triggers build increment`() {
        // Need java plugin so the build lifecycle task exists
        buildFile.writeText(
            """
            plugins {
                java
                id("com.aralel.versionfrau")
            }
            """.trimIndent()
        )

        val initialProperties = Properties()
        initialProperties.setProperty("major", "1")
        initialProperties.setProperty("minor", "0")
        initialProperties.setProperty("patch", "0")
        initialProperties.setProperty("build", "5")
        versionFile.outputStream().use { initialProperties.store(it, null) }

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("build")
            .build()

        val updatedProperties = readVersionProperties()
        assertEquals("6", updatedProperties.getProperty("build"))
    }

    @Test
    fun `jar task triggers build increment`() {
        // Add java plugin so jar task exists
        buildFile.writeText(
            """
            plugins {
                java
                id("com.aralel.versionfrau")
            }
            """.trimIndent()
        )

        val initialProperties = Properties()
        initialProperties.setProperty("major", "1")
        initialProperties.setProperty("minor", "0")
        initialProperties.setProperty("patch", "0")
        initialProperties.setProperty("build", "2")
        versionFile.outputStream().use { initialProperties.store(it, null) }

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("jar")
            .build()

        val updatedProperties = readVersionProperties()
        assertEquals("3", updatedProperties.getProperty("build"))
    }
}
