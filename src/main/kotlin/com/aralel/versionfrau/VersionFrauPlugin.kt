package com.aralel.versionfrau

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

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

        // Register increment tasks eagerly so they are available for dependency wiring.
        val incrementTasks = registerIncrementTasks(project, extension)

        // Inject BUILD_TIME as soon as the Android plugin is available.
        // Must happen BEFORE afterEvaluate so it's included when AGP finalizes variants.
        project.pluginManager.withPlugin("com.android.application") {
            try {
                AndroidIntegration.injectBuildTime(project)
            } catch (_: NoClassDefFoundError) {
                // AGP classes not visible — skip silently
            }
        }

        project.afterEvaluate {
            // Wire task dependencies using task names — no AGP class references needed.
            wireTaskDependencies(project, incrementTasks.first, incrementTasks.second)

            // Configure output renaming (needs variants, so must be in afterEvaluate).
            val hasAndroidPlugin = project.extensions.findByName("android") != null
            if (hasAndroidPlugin) {
                try {
                    AndroidIntegration.configureOutputRenaming(project, extension)
                } catch (e: NoClassDefFoundError) {
                    project.logger.warn("VersionFrau: AGP classes not on classpath — output renaming disabled")
                }
            }
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

    private fun registerIncrementTasks(
        project: Project,
        extension: VersionFrauExtension
    ): Pair<TaskProvider<DefaultTask>, TaskProvider<DefaultTask>> {
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

        return Pair(incrementBuildTask, incrementPatchTask)
    }

    /**
     * Wires increment tasks as dependencies of assemble/bundle/build/jar tasks.
     * Uses [project.tasks.all] with name matching — no AGP class references, so this
     * works reliably regardless of classloader isolation or plugin load order.
     *
     * [tasks.all] fires for both already-realized tasks AND tasks added later,
     * which solves the timing issues that [configureEach] had with AGP tasks.
     */
    private fun wireTaskDependencies(
        project: Project,
        incrementBuildTask: TaskProvider<DefaultTask>,
        incrementPatchTask: TaskProvider<DefaultTask>
    ) {
        project.tasks.all { task ->
            val taskName = task.name.lowercase()
            when {
                // Debug assemble/bundle → increment build
                taskName.contains("debug") &&
                    (taskName.startsWith("assemble") || taskName.startsWith("bundle")) -> {
                    task.dependsOn(incrementBuildTask)
                    task.mustRunAfter(incrementBuildTask)
                }
                // Release assemble/bundle → increment patch
                taskName.contains("release") &&
                    (taskName.startsWith("assemble") || taskName.startsWith("bundle")) -> {
                    task.dependsOn(incrementPatchTask)
                    task.mustRunAfter(incrementPatchTask)
                }
                // Standard Java/Kotlin builds (no Android)
                taskName == "build" || taskName == "jar" -> {
                    task.dependsOn(incrementBuildTask)
                    task.mustRunAfter(incrementBuildTask)
                }
            }
        }
    }
}
