package com.aralel.versionfrau

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

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

        // Try to configure Android-specific integration (BUILD_TIME field, task hooks,
        // output file renaming). AndroidIntegration is in a separate class so that
        // VersionFrauPlugin can load even when the Android Gradle Plugin is absent.
        val hasAndroidPlugin = project.extensions.findByName("android") != null
        if (hasAndroidPlugin) {
            try {
                AndroidIntegration.configure(
                    project, extension,
                    incrementBuildTask.get(), incrementPatchTask.get()
                )
            } catch (e: NoClassDefFoundError) {
                project.logger.warn("VersionFrau: Android plugin detected but AGP classes not found — skipping Android integration")
            }
        } else {
            hookIntoStandardTasks(project, incrementBuildTask.get(), incrementPatchTask.get())
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
}
