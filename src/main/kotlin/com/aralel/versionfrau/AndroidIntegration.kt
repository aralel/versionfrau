package com.aralel.versionfrau

import com.android.build.gradle.AppExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encapsulates all Android-specific plugin logic. This class is loaded only when
 * the Android Gradle Plugin is on the classpath, keeping the main VersionFrauPlugin
 * class loadable in non-Android (pure Gradle) environments.
 */
internal object AndroidIntegration {

    fun configure(
        project: Project,
        extension: VersionFrauExtension,
        incrementBuildTask: TaskProvider<DefaultTask>,
        incrementPatchTask: TaskProvider<DefaultTask>
    ) {
        val androidAppExtension = project.extensions.findByName("android") as? AppExtension
            ?: return

        configureBuildTimeField(androidAppExtension)
        hookIntoAndroidTasks(project, extension, androidAppExtension, incrementBuildTask, incrementPatchTask)
    }

    /**
     * Injects a BUILD_TIME buildConfigField into every Android build variant.
     * The value is the build timestamp formatted as "MMM dd, yyyy".
     */
    private fun configureBuildTimeField(androidAppExtension: AppExtension) {
        val buildTimeValue = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        androidAppExtension.defaultConfig.buildConfigField(
            "String",
            "BUILD_TIME",
            "\"$buildTimeValue\""
        )
    }

    /**
     * Uses the AGP variant API to wire increment tasks as dependencies of
     * assemble/bundle tasks. This avoids the timing issues with configureEach
     * where AGP tasks may already be realized before the callback is registered.
     */
    private fun hookIntoAndroidTasks(
        project: Project,
        extension: VersionFrauExtension,
        androidAppExtension: AppExtension,
        incrementBuildTask: TaskProvider<DefaultTask>,
        incrementPatchTask: TaskProvider<DefaultTask>
    ) {
        androidAppExtension.applicationVariants.all { variant ->
            val buildTypeName = variant.buildType.name          // "debug" | "release"
            val variantName = variant.name                       // "debug" | "release" | "freeDebug" …
            val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }
            val isDebugVariant = buildTypeName == "debug"

            val incrementTask = if (isDebugVariant) incrementBuildTask else incrementPatchTask

            // Wire the assemble task via variant.assembleProvider (AGP's own TaskProvider)
            variant.assembleProvider.configure { assembleTask ->
                assembleTask.dependsOn(incrementTask)
                assembleTask.mustRunAfter(incrementTask)
            }

            // Wire the bundle task by name (no direct provider in the legacy AGP variant API)
            val bundleTaskName = "bundle$capitalizedVariantName"
            project.tasks.matching { it.name == bundleTaskName }.all { bundleTask ->
                bundleTask.dependsOn(incrementTask)
                bundleTask.mustRunAfter(incrementTask)
            }

            // ── APK renaming ────────────────────────────────────────────────
            variant.assembleProvider.configure { assembleTask ->
                assembleTask.doLast {
                    val freshVersion = extension.readVersion()
                    val versionSuffix = buildVersionSuffix(freshVersion, isDebugVariant)
                    val newApkBaseName = "${project.name}-${buildTypeName}-${versionSuffix}"

                    variant.outputs.forEach { variantOutput ->
                        val originalApkFile = variantOutput.outputFile
                        if (originalApkFile.exists()) {
                            val renamedApkFile = File(originalApkFile.parentFile, "${newApkBaseName}.apk")
                            if (originalApkFile.renameTo(renamedApkFile)) {
                                project.logger.lifecycle("VersionFrau: APK → ${renamedApkFile.name}")
                            } else {
                                project.logger.warn("VersionFrau: could not rename ${originalApkFile.name}")
                            }
                        }
                    }
                }
            }

            // ── AAB renaming ────────────────────────────────────────────────
            project.tasks.matching { it.name == bundleTaskName }.all { bundleTask ->
                bundleTask.doLast {
                    val freshVersion = extension.readVersion()
                    val versionSuffix = buildVersionSuffix(freshVersion, isDebugVariant)
                    val newAabBaseName = "${project.name}-${buildTypeName}-${versionSuffix}"

                    val aabOutputDir = project.layout.buildDirectory
                        .dir("outputs/bundle/${variantName}")
                        .get().asFile
                    if (aabOutputDir.exists()) {
                        aabOutputDir.listFiles { file -> file.extension == "aab" }
                            ?.forEach { originalAabFile ->
                                val renamedAabFile = File(aabOutputDir, "${newAabBaseName}.aab")
                                if (originalAabFile.renameTo(renamedAabFile)) {
                                    project.logger.lifecycle("VersionFrau: AAB → ${renamedAabFile.name}")
                                } else {
                                    project.logger.warn("VersionFrau: could not rename ${originalAabFile.name}")
                                }
                            }
                    }
                }
            }
        }
    }

    private fun buildVersionSuffix(version: VersionData, isDebug: Boolean): String {
        return if (isDebug) {
            "v${version.major}.${version.minor}.${version.patch}.${version.build}"
        } else {
            "v${version.major}.${version.minor}.${version.patch}"
        }
    }
}
