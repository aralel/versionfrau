package com.aralel.versionfrau

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

/**
 * Functional tests that exercise the plugin with a real Android Gradle Plugin project.
 * These tests are skipped if no Android SDK is found on the machine.
 *
 * Both AGP and VersionFrau are loaded via the buildscript classpath so they share a
 * classloader — matching the real-world setup where AndroidIntegration can see AppExtension.
 */
class AndroidFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var buildFile: File
    private lateinit var versionFile: File

    private val androidSdkDir: String? by lazy {
        System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: listOf(
                "${System.getProperty("user.home")}/Library/Android/sdk",
                "${System.getProperty("user.home")}/Android/Sdk"
            ).firstOrNull { File(it).isDirectory }
    }

    /** Plugin classpath entries from pluginUnderTestMetadata, formatted for Groovy. */
    private val pluginClasspathString: String by lazy {
        val resource = javaClass.classLoader.getResource("plugin-under-test-metadata.properties")
            ?: throw IllegalStateException("plugin-under-test-metadata.properties not found")
        val properties = Properties()
        resource.openStream().use { properties.load(it) }
        val classpath = properties.getProperty("implementation-classpath")
            ?: throw IllegalStateException("implementation-classpath not found in metadata")
        classpath.split(File.pathSeparator)
            .joinToString(", ") { "files('${it.replace("\\", "\\\\").replace("'", "\\'")}')" }
    }

    @BeforeEach
    fun setup() {
        val sdkDir = androidSdkDir
        assumeTrue(sdkDir != null && File(sdkDir).isDirectory, "Android SDK not found — skipping")

        // local.properties with sdk.dir
        File(projectDir, "local.properties").writeText("sdk.dir=$sdkDir\n")

        // settings.gradle.kts
        File(projectDir, "settings.gradle.kts").writeText(
            """rootProject.name = "test-android-app""""
        )

        // build.gradle (Groovy) — both AGP and VersionFrau on the same buildscript classpath
        buildFile = File(projectDir, "build.gradle")
        buildFile.writeText(
            """
            buildscript {
                repositories {
                    google()
                    mavenCentral()
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:8.2.0'
                    classpath $pluginClasspathString
                }
            }

            apply plugin: 'com.android.application'
            apply plugin: 'com.aralel.versionfrau'

            repositories {
                google()
                mavenCentral()
            }

            android {
                namespace 'com.test.app'
                compileSdk 34
                defaultConfig {
                    applicationId 'com.test.app'
                    minSdk 24
                    targetSdk 34
                    versionCode versionFrau.versionCode
                    versionName versionFrau.versionName
                }
                buildFeatures {
                    buildConfig true
                }
            }
            """.trimIndent()
        )

        // Minimal AndroidManifest.xml
        val mainDir = File(projectDir, "src/main")
        mainDir.mkdirs()
        File(mainDir, "AndroidManifest.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application />
            </manifest>
            """.trimIndent()
        )

        // version.properties
        versionFile = File(projectDir, "version.properties")
    }

    private fun seedVersion(major: Int = 1, minor: Int = 0, patch: Int = 0, build: Int = 0) {
        val properties = Properties()
        properties.setProperty("major", major.toString())
        properties.setProperty("minor", minor.toString())
        properties.setProperty("patch", patch.toString())
        properties.setProperty("build", build.toString())
        versionFile.outputStream().use { properties.store(it, null) }
    }

    private fun readVersion(): Properties {
        val properties = Properties()
        versionFile.inputStream().use { properties.load(it) }
        return properties
    }

    /** Runner WITHOUT withPluginClasspath — the plugin is on the buildscript classpath instead. */
    private fun gradleRunner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*args)
        .forwardOutput()

    // ─── assembleDebug triggers incrementBuildVersion ──────────────────────

    @Test
    fun `assembleDebug triggers incrementBuildVersion in dry run`() {
        seedVersion(build = 0)

        val result = gradleRunner("assembleDebug", "--dry-run").build()
        val output = result.output

        assertTrue(
            output.contains(":incrementBuildVersion"),
            "Expected incrementBuildVersion in dry-run output:\n$output"
        )
        // incrementBuildVersion should appear BEFORE assembleDebug
        val incrementIndex = output.indexOf(":incrementBuildVersion")
        val assembleIndex = output.indexOf(":assembleDebug")
        assertTrue(
            incrementIndex < assembleIndex,
            "incrementBuildVersion ($incrementIndex) should run before assembleDebug ($assembleIndex)"
        )
    }

    @Test
    fun `assembleDebug actually increments build number`() {
        seedVersion(major = 2, minor = 1, patch = 3, build = 10)

        val result = gradleRunner("assembleDebug").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":incrementBuildVersion")?.outcome)

        val updatedVersion = readVersion()
        assertEquals("11", updatedVersion.getProperty("build"))
        // major/minor/patch unchanged
        assertEquals("2", updatedVersion.getProperty("major"))
        assertEquals("1", updatedVersion.getProperty("minor"))
        assertEquals("3", updatedVersion.getProperty("patch"))
    }

    @Test
    fun `consecutive assembleDebug builds increment build each time`() {
        seedVersion(build = 0)

        repeat(3) {
            gradleRunner("assembleDebug").build()
        }

        val updatedVersion = readVersion()
        assertEquals("3", updatedVersion.getProperty("build"))
    }

    // ─── assembleRelease triggers incrementPatchVersion ────────────────────

    @Test
    fun `assembleRelease triggers incrementPatchVersion in dry run`() {
        seedVersion(patch = 5)

        val result = gradleRunner("assembleRelease", "--dry-run").build()
        val output = result.output

        assertTrue(
            output.contains(":incrementPatchVersion"),
            "Expected incrementPatchVersion in dry-run output:\n$output"
        )
    }

    // ─── bundleRelease triggers incrementPatchVersion ──────────────────────

    @Test
    fun `bundleRelease triggers incrementPatchVersion in dry run`() {
        seedVersion(patch = 5)

        val result = gradleRunner("bundleRelease", "--dry-run").build()
        val output = result.output

        assertTrue(
            output.contains(":incrementPatchVersion"),
            "Expected incrementPatchVersion in dry-run output:\n$output"
        )
    }

    // ─── BUILD_TIME is injected ────────────────────────────────────────────

    @Test
    fun `BUILD_TIME field is generated in BuildConfig`() {
        seedVersion()

        gradleRunner("assembleDebug").build()

        val buildConfigFile = File(
            projectDir,
            "build/generated/source/buildConfig/debug/com/test/app/BuildConfig.java"
        )
        assertTrue(buildConfigFile.exists(), "BuildConfig.java should exist at: ${buildConfigFile.absolutePath}")

        val buildConfigContent = buildConfigFile.readText()
        assertTrue(
            buildConfigContent.contains("BUILD_TIME"),
            "BuildConfig should contain BUILD_TIME field:\n$buildConfigContent"
        )
    }

    // ─── APK renaming ─────────────────────────────────────────────────────

    @Test
    fun `debug APK is renamed with version suffix`() {
        seedVersion(major = 1, minor = 2, patch = 3, build = 0)

        gradleRunner("assembleDebug").build()

        // After increment, build should be 1
        val apkDir = File(projectDir, "build/outputs/apk/debug")
        assertTrue(apkDir.exists(), "APK output dir should exist: ${apkDir.absolutePath}")

        val apkFiles = apkDir.listFiles { file -> file.extension == "apk" }
        assertTrue(
            apkFiles != null && apkFiles.isNotEmpty(),
            "Should have at least one APK in ${apkDir.absolutePath}: ${apkDir.listFiles()?.map { it.name }}"
        )
        val renamedApk = apkFiles?.firstOrNull { it.name.contains("v1.2.3.1") }
        assertTrue(
            renamedApk != null,
            "Expected APK with version v1.2.3.1, found: ${apkFiles?.map { it.name }}"
        )
    }
}
