package com.aralel.versionfrau

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
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

        // Register increment tasks eagerly so they are available for dependency wiring.
        val incrementTasks = registerIncrementTasks(project, extension)

        // Configure BUILD_TIME injection as soon as the Android plugin is available.
        // Uses the stable AGP Variant API (finalizeDsl + onVariants) to enable
        // buildConfig and inject the field into every variant.
        project.pluginManager.withPlugin("com.android.application") {
            AndroidIntegration.configure(project)
        }

        project.afterEvaluate {
            // Wire task dependencies using task names — no AGP class references needed.
            wireTaskDependencies(project, extension, incrementTasks.first, incrementTasks.second)
        }
    }

    private fun resolveIsDebugBuild(project: Project): Boolean {
        val requestedTaskNames = project.gradle.startParameter.taskNames
        var foundDebug = false
        var foundRelease = false

        for (requestedTaskName in requestedTaskNames) {
            val shortTaskName = requestedTaskName.substringAfterLast(':').lowercase()
            if (shortTaskName.contains("debug")) foundDebug = true
            if (shortTaskName.contains("release")) foundRelease = true
        }

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
     * Wires increment tasks as dependencies AND output renaming for assemble/bundle tasks.
     * Uses [project.tasks.all] with name matching — zero AGP class references, so this
     * works reliably regardless of classloader isolation or plugin load order.
     */
    private fun wireTaskDependencies(
        project: Project,
        extension: VersionFrauExtension,
        incrementBuildTask: TaskProvider<DefaultTask>,
        incrementPatchTask: TaskProvider<DefaultTask>
    ) {
        project.tasks.all { task ->
            val taskName = task.name.lowercase()
            // Only match lifecycle tasks like assembleDebug, bundleRelease, assembleFreeDebug —
            // NOT internal AGP tasks like bundleReleaseResources, assembleDebugAndroidTest.
            val isDebugLifecycleTask = taskName.endsWith("debug") &&
                (taskName.startsWith("assemble") || taskName.startsWith("bundle"))
            val isReleaseLifecycleTask = taskName.endsWith("release") &&
                (taskName.startsWith("assemble") || taskName.startsWith("bundle"))

            when {
                isDebugLifecycleTask -> {
                    task.dependsOn(incrementBuildTask)
                    task.mustRunAfter(incrementBuildTask)
                    configureOutputRenaming(project, extension, task, isDebugVariant = true)
                }
                isReleaseLifecycleTask -> {
                    task.dependsOn(incrementPatchTask)
                    task.mustRunAfter(incrementPatchTask)
                    configureOutputRenaming(project, extension, task, isDebugVariant = false)
                }
                // Standard Java/Kotlin builds (no Android)
                taskName == "build" || taskName == "jar" -> {
                    task.dependsOn(incrementBuildTask)
                    task.mustRunAfter(incrementBuildTask)
                }
            }
        }
    }

    /**
     * Adds a doLast action that renames APK / AAB output files with the version suffix.
     * Extracts the variant name from the task name (e.g. "assembleDebug" → "debug",
     * "bundleFreeRelease" → "freeRelease") and searches the conventional AGP output dirs.
     *
     * No AGP class references — works purely on file conventions.
     */
    private fun configureOutputRenaming(
        project: Project,
        extension: VersionFrauExtension,
        task: org.gradle.api.Task,
        isDebugVariant: Boolean
    ) {
        val taskName = task.name
        val isAssemble = taskName.lowercase().startsWith("assemble")
        val isBundle = taskName.lowercase().startsWith("bundle")
        // val prefix = if (isAssemble) "assemble" else if (isBundle) "bundle" else return

        // Extract variant name: "assembleDebug" → "Debug" → "debug"
        // val variantName = taskName.removePrefix(prefix).replaceFirstChar { it.lowercase() }
        val buildTypeName = if (isDebugVariant) "debug" else "release"

        task.doLast {
            val freshVersion = extension.readVersion()
            val versionSuffix = if (isDebugVariant) {
                "v${freshVersion.major}.${freshVersion.minor}.${freshVersion.patch}.${freshVersion.build}"
            } else {
                "v${freshVersion.major}.${freshVersion.minor}.${freshVersion.patch}"
            }
            val newBaseName = "${project.name}-${buildTypeName}-${versionSuffix}"

            if (isAssemble) {
                // APK: build/outputs/apk/<variant>/ (simple) or build/outputs/apk/<flavor>/<buildType>/
                val apkBaseDir = project.layout.buildDirectory.dir("outputs/apk").get().asFile
                if (apkBaseDir.exists()) {
                    apkBaseDir.walkTopDown()
                        .filter { it.isFile && it.extension == "apk" && !it.name.startsWith("${newBaseName}.") }
                        .forEach { originalApkFile ->
                            val renamedApkFile = File(originalApkFile.parentFile, "${newBaseName}.apk")
                            if (originalApkFile.renameTo(renamedApkFile)) {
                                project.logger.lifecycle("VersionFrau: APK → ${renamedApkFile.name}")
                            }
                        }
                }
            }

            if (isBundle) {
                // AAB output location varies across AGP versions — search broadly.
                var bundleBaseDir = project.layout.buildDirectory.dir("outputs/bundle").get().asFile
                if (!bundleBaseDir.exists()) {
                    // release
                    bundleBaseDir = project.layout.projectDirectory.dir("release").asFile
                }
                if (bundleBaseDir.exists()) {
                    val aabFiles = bundleBaseDir.walkTopDown()
                        .filter { it.isFile && it.extension == "aab" && it.name != "${newBaseName}.aab" }
                        .toList()
                    if (aabFiles.isEmpty()) {
                        project.logger.warn("VersionFrau: no .aab files found under ${bundleBaseDir.absolutePath}")
                    }
                    aabFiles.forEach { originalAabFile ->
                        val renamedAabFile = File(originalAabFile.parentFile, "${newBaseName}.aab")
                        if (originalAabFile.renameTo(renamedAabFile)) {
                            project.logger.lifecycle("VersionFrau: AAB → ${renamedAabFile.name}")
                        }
                    }
                } else {
                    project.logger.warn("VersionFrau: AAB output dir not found at ${bundleBaseDir.absolutePath}")
                }
            }
        }
    }
}
