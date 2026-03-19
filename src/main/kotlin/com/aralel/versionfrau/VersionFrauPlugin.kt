package com.aralel.versionfrau

import com.android.build.gradle.AppExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

class VersionFrauPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "versionFrau",
            VersionFrauExtension::class.java,
            project
        )

        // Detect build type immediately so the flag is available when the user's
        // build script reads versionFrau.versionName / versionFrau.versionCode
        // inside android { defaultConfig { } } during configuration.
        extension.isDebugBuild = resolveIsDebugBuild(project)

        project.afterEvaluate {
            setupVersionTasks(project, extension)
        }
    }

    /**
     * Inspects the Gradle start parameters to determine whether the user is running
     * a debug or release build.  A task is considered a "debug build" when its
     * (short) name contains "debug" and does NOT contain "release".
     *
     * Examples that return true:
     *   assembleDebug, :app:assembleDebug, bundleDebug, app:bundleDebug
     *
     * Examples that return false (release):
     *   assembleRelease, bundleRelease, build (generic – treated as release-like)
     *
     * If no explicit debug/release task is found we default to false (release-like),
     * which is the safer option for version-name formatting.
     */
    private fun resolveIsDebugBuild(project: Project): Boolean {
        val requestedTaskNames = project.gradle.startParameter.taskNames
        var foundDebug = false
        var foundRelease = false

        for (requestedTaskName in requestedTaskNames) {
            // Strip any leading project path (e.g. ":app:assembleDebug" → "assembleDebug")
            val shortTaskName = requestedTaskName.substringAfterLast(':').lowercase()
            if (shortTaskName.contains("debug")) foundDebug = true
            if (shortTaskName.contains("release")) foundRelease = true
        }

        // Debug wins only if there is an explicit debug task and no release task requested.
        return foundDebug && !foundRelease
    }

    private fun setupVersionTasks(project: Project, extension: VersionFrauExtension) {
        val incrementBuildTask = project.tasks.register("incrementBuildVersion", DefaultTask::class.java) { task ->
            task.group = "versioning"
            task.description = "Increments the build version number"
            task.doLast {
                val currentVersion = extension.readVersion()
                val newVersion = currentVersion.copy(build = currentVersion.build + 1)
                extension.writeVersion(newVersion)
                project.logger.lifecycle("VersionFrau: build version incremented to ${newVersion.major}.${newVersion.minor}.${newVersion.patch}.${newVersion.build}")
            }
        }

        val incrementPatchTask = project.tasks.register("incrementPatchVersion", DefaultTask::class.java) { task ->
            task.group = "versioning"
            task.description = "Increments the patch version number and resets build"
            task.doLast {
                val currentVersion = extension.readVersion()
                val newVersion = currentVersion.copy(patch = currentVersion.patch + 1, build = 0)
                extension.writeVersion(newVersion)
                project.logger.lifecycle("VersionFrau: patch version incremented to ${newVersion.major}.${newVersion.minor}.${newVersion.patch}.${newVersion.build}")
            }
        }

        project.tasks.register("incrementMinorVersion", DefaultTask::class.java) { task ->
            task.group = "versioning"
            task.description = "Increments the minor version number and resets patch and build"
            task.doLast {
                val currentVersion = extension.readVersion()
                val newVersion = currentVersion.copy(minor = currentVersion.minor + 1, patch = 0, build = 0)
                extension.writeVersion(newVersion)
                project.logger.lifecycle("VersionFrau: minor version incremented to ${newVersion.major}.${newVersion.minor}.${newVersion.patch}.${newVersion.build}")
            }
        }

        project.tasks.register("incrementMajorVersion", DefaultTask::class.java) { task ->
            task.group = "versioning"
            task.description = "Increments the major version number and resets minor, patch, and build"
            task.doLast {
                val currentVersion = extension.readVersion()
                val newVersion = currentVersion.copy(major = currentVersion.major + 1, minor = 0, patch = 0, build = 0)
                extension.writeVersion(newVersion)
                project.logger.lifecycle("VersionFrau: major version incremented to ${newVersion.major}.${newVersion.minor}.${newVersion.patch}.${newVersion.build}")
            }
        }

        // Hook into Android build tasks if the Android application plugin is applied
        val androidAppExtension = project.extensions.findByName("android") as? AppExtension

        if (androidAppExtension != null) {
            hookIntoAndroidTasks(project, incrementBuildTask.get(), incrementPatchTask.get())
            configureOutputFileNaming(project, extension, androidAppExtension)
        } else {
            hookIntoStandardTasks(project, incrementBuildTask.get(), incrementPatchTask.get())
        }
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

    private fun hookIntoStandardTasks(
        project: Project,
        incrementBuildTask: Task,
        @Suppress("UNUSED_PARAMETER") incrementPatchTask: Task
    ) {
        project.tasks.configureEach { task ->
            val taskName = task.name.lowercase()
            when {
                taskName == "build" || taskName == "jar" -> {
                    task.dependsOn(incrementBuildTask)
                    task.mustRunAfter(incrementBuildTask)
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
            // doLast on the assemble task: by this point the increment task has
            // already run, so reading version.properties gives the new values.
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
            // The bundle task places its output in build/outputs/bundle/{variantName}/.
            val bundleTaskName = "bundle$capitalizedVariantName"
            project.tasks.configureEach { task ->
                if (task.name == bundleTaskName) {
                    task.doLast {
                        val freshVersion = extension.readVersion()
                        val versionSuffix = buildVersionSuffix(freshVersion, isDebugVariant)
                        val newAabBaseName = "${project.name}-${buildTypeName}-${versionSuffix}"

                        // Standard AGP output location for AABs
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

    /**
     * Returns the version portion of the output filename.
     *   Debug:   "v1.0.3.42"
     *   Release: "v1.0.3"
     */
    private fun buildVersionSuffix(version: VersionData, isDebug: Boolean): String {
        return if (isDebug) {
            "v${version.major}.${version.minor}.${version.patch}.${version.build}"
        } else {
            "v${version.major}.${version.minor}.${version.patch}"
        }
    }
}
