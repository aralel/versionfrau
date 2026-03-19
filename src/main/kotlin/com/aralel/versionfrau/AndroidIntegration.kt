package com.aralel.versionfrau

import com.android.build.gradle.AppExtension
import org.gradle.api.Project
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encapsulates Android-specific plugin logic that requires AGP classes on the classpath:
 *   - BUILD_TIME buildConfigField injection
 *   - APK / AAB output file renaming
 *
 * Task dependency wiring (incrementBuildVersion → assembleDebug, etc.) is handled
 * in [VersionFrauPlugin.wireTaskDependencies] using plain task-name matching, so it
 * works even when AGP classes are not visible to the plugin's classloader.
 */
internal object AndroidIntegration {

    /**
     * Injects a BUILD_TIME buildConfigField into the android defaultConfig.
     * Must be called during the configuration phase (before afterEvaluate)
     * so AGP picks it up when finalizing variants.
     */
    fun injectBuildTime(project: Project) {
        val androidAppExtension = project.extensions.findByName("android") as? AppExtension
            ?: return

        val buildTimeValue = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        androidAppExtension.defaultConfig.buildConfigField(
            "String",
            "BUILD_TIME",
            "\"$buildTimeValue\""
        )
    }

    /**
     * Renames the produced APK / AAB files to include the version after the build.
     * Must be called in afterEvaluate because it needs applicationVariants.
     *
     * The file name format is:
     *   Debug:   {projectName}-{buildType}-v{major}.{minor}.{patch}.{build}.apk|aab
     *   Release: {projectName}-{buildType}-v{major}.{minor}.{patch}.apk|aab
     */
    fun configureOutputRenaming(
        project: Project,
        extension: VersionFrauExtension
    ) {
        val androidAppExtension = project.extensions.findByName("android") as? AppExtension
            ?: return

        androidAppExtension.applicationVariants.all { variant ->
            val buildTypeName = variant.buildType.name          // "debug" | "release"
            val variantName = variant.name                       // "debug" | "release" | "freeDebug" …
            val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }
            val isDebugVariant = buildTypeName == "debug"

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
            val bundleTaskName = "bundle$capitalizedVariantName"
            project.tasks.matching { it.name == bundleTaskName }.all { bundleTask ->
                bundleTask.doLast {
                    val freshVersion = extension.readVersion()
                    val versionSuffix = buildVersionSuffix(freshVersion, isDebugVariant)
                    val newAabBaseName = "${project.name}-${buildTypeName}-${versionSuffix}"

                    val aabOutputDir = project.layout.buildDirectory
                        .dir("outputs/bundle/${variantName}")
                        .get().asFile

                    if (aabOutputDir.exists()) {
                        val aabFiles = aabOutputDir.listFiles { file -> file.extension == "aab" }
                        if (aabFiles.isNullOrEmpty()) {
                            project.logger.warn("VersionFrau: no .aab files found in ${aabOutputDir.absolutePath}")
                        }
                        aabFiles?.forEach { originalAabFile ->
                            val renamedAabFile = File(aabOutputDir, "${newAabBaseName}.aab")
                            if (originalAabFile.renameTo(renamedAabFile)) {
                                project.logger.lifecycle("VersionFrau: AAB → ${renamedAabFile.name}")
                            } else {
                                project.logger.warn("VersionFrau: could not rename ${originalAabFile.name}")
                            }
                        }
                    } else {
                        project.logger.warn("VersionFrau: AAB output dir does not exist: ${aabOutputDir.absolutePath}")
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
