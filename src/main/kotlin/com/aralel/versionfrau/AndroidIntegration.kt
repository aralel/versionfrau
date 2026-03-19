package com.aralel.versionfrau

import com.android.build.gradle.AppExtension
import org.gradle.api.Project
import org.gradle.api.Task
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
        incrementBuildTask: Task,
        incrementPatchTask: Task
    ) {
        val androidAppExtension = project.extensions.findByName("android") as? AppExtension
            ?: return

        configureBuildTimeField(androidAppExtension)
        hookIntoAndroidTasks(project, incrementBuildTask, incrementPatchTask)
        configureOutputFileNaming(project, extension, androidAppExtension)
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

    private fun hookIntoAndroidTasks(
        project: Project,
        incrementBuildTask: Task,
        incrementPatchTask: Task
    ) {
        project.tasks.configureEach { task ->
            val taskName = task.name.lowercase()
            when {
                taskName.contains("debug") && (taskName.startsWith("assemble") || taskName.startsWith("bundle")) -> {
                    task.dependsOn(incrementBuildTask)
                    task.mustRunAfter(incrementBuildTask)
                }
                taskName.contains("release") && (taskName.startsWith("assemble") || taskName.startsWith("bundle")) -> {
                    task.dependsOn(incrementPatchTask)
                    task.mustRunAfter(incrementPatchTask)
                }
            }
        }
    }

    /**
     * Renames the produced APK / AAB files to include the version after the build.
     *
     * The file name format is:
     *   Debug:   {projectName}-{buildType}-v{major}.{minor}.{patch}.{build}.apk|aab
     *   Release: {projectName}-{buildType}-v{major}.{minor}.{patch}.apk|aab
     *
     * Renaming is done in a doLast action so it always uses the ALREADY-INCREMENTED
     * version (the increment task runs before assemble/bundle as a dependency).
     */
    private fun configureOutputFileNaming(
        project: Project,
        extension: VersionFrauExtension,
        androidAppExtension: AppExtension
    ) {
        androidAppExtension.applicationVariants.all { variant ->
            val buildTypeName = variant.buildType.name          // "debug" | "release"
            val variantName = variant.name                       // "debug" | "release" | "freeDebug" …
            val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }
            val isDebugVariant = buildTypeName == "debug"

            // ── APK renaming ────────────────────────────────────────────────
            val assembleTaskName = "assemble$capitalizedVariantName"
            project.tasks.configureEach { task ->
                if (task.name == assembleTaskName) {
                    task.doLast {
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
            }

            // ── AAB renaming ────────────────────────────────────────────────
            val bundleTaskName = "bundle$capitalizedVariantName"
            project.tasks.configureEach { task ->
                if (task.name == bundleTaskName) {
                    task.doLast {
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
    }

    private fun buildVersionSuffix(version: VersionData, isDebug: Boolean): String {
        return if (isDebug) {
            "v${version.major}.${version.minor}.${version.patch}.${version.build}"
        } else {
            "v${version.major}.${version.minor}.${version.patch}"
        }
    }
}
