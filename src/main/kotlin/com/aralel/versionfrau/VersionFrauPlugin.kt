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
     *
     * Matches both plain and flavored lifecycle tasks: assembleDebug, bundleRelease,
     * assembleFreeDebug, bundleFreeProRelease — NOT internal AGP tasks like
     * bundleReleaseResources or assembleDebugAndroidTest.
     */
    private fun wireTaskDependencies(
        project: Project,
        extension: VersionFrauExtension,
        incrementBuildTask: TaskProvider<DefaultTask>,
        incrementPatchTask: TaskProvider<DefaultTask>
    ) {
        project.tasks.all { task ->
            val lifecycleMatch = lifecycleTaskPattern.matchEntire(task.name)
            if (lifecycleMatch != null) {
                val (taskPrefix, flavorPart, buildTypePart) = lifecycleMatch.destructured
                val isDebugVariant = buildTypePart == "Debug"
                val incrementTask = if (isDebugVariant) incrementBuildTask else incrementPatchTask

                task.dependsOn(incrementTask)
                task.mustRunAfter(incrementTask)
                configureOutputRenaming(
                    project = project,
                    extension = extension,
                    task = task,
                    isAssemble = taskPrefix == "assemble",
                    flavorName = flavorPart.replaceFirstChar { it.lowercase() },
                    isDebugVariant = isDebugVariant
                )
            } else if (task.name == "build" || task.name == "jar") {
                // Standard Java/Kotlin builds (no Android)
                task.dependsOn(incrementBuildTask)
                task.mustRunAfter(incrementBuildTask)
            }
        }
    }

    /**
     * Adds a doLast action that renames the APK / AAB output files of ONE variant with the
     * version suffix. The variant is identified by the flavor + build type parsed from the
     * task name, so every flavor's outputs are renamed independently:
     *
     * - assembleDebug (no flavors)  → build/outputs/apk/debug/
     * - assembleFreeDebug           → build/outputs/apk/free/debug/
     * - bundleRelease (no flavors)  → build/outputs/bundle/release/
     * - bundleFreeRelease           → build/outputs/bundle/freeRelease/
     *
     * Aggregate tasks on flavored projects (e.g. assembleDebug when flavors exist) have no
     * output directory of their own — they skip silently while the per-flavor tasks each
     * rename their own outputs. All fresh output files in the variant directory are renamed
     * (ABI splits produce several APKs); files already carrying a version suffix from a
     * previous build are left untouched.
     *
     * No AGP class references — works purely on file conventions.
     */
    private fun configureOutputRenaming(
        project: Project,
        extension: VersionFrauExtension,
        task: org.gradle.api.Task,
        isAssemble: Boolean,
        flavorName: String,
        isDebugVariant: Boolean
    ) {
        val buildTypeName = if (isDebugVariant) "debug" else "release"

        task.doLast {
            val freshVersion = extension.readVersion()
            val versionSuffix = if (isDebugVariant) {
                "v${freshVersion.major}.${freshVersion.minor}.${freshVersion.patch}.${freshVersion.build}"
            } else {
                "v${freshVersion.major}.${freshVersion.minor}.${freshVersion.patch}"
            }

            val outputExtension = if (isAssemble) "apk" else "aab"
            val variantOutputDir = if (isAssemble) {
                // APK: build/outputs/apk/<buildType>/ or build/outputs/apk/<flavor>/<buildType>/
                val apkRelativePath = if (flavorName.isEmpty()) {
                    "outputs/apk/$buildTypeName"
                } else {
                    "outputs/apk/$flavorName/$buildTypeName"
                }
                project.layout.buildDirectory.dir(apkRelativePath).get().asFile
            } else {
                // AAB: build/outputs/bundle/<variantName>/ where variantName is the
                // camelCase flavor + build type combination (e.g. "freeRelease").
                val variantDirName = if (flavorName.isEmpty()) {
                    buildTypeName
                } else {
                    flavorName + buildTypeName.replaceFirstChar { it.uppercase() }
                }
                project.layout.buildDirectory.dir("outputs/bundle/$variantDirName").get().asFile
            }

            if (!variantOutputDir.isDirectory) {
                // Aggregate task on a flavored project — per-flavor tasks handle renaming.
                return@doLast
            }

            variantOutputDir.walkTopDown()
                .filter { it.isFile && it.extension == outputExtension }
                .filterNot { versionedFileNamePattern.matches(it.nameWithoutExtension) }
                .forEach { originalFile ->
                    val renamedFile = File(
                        originalFile.parentFile,
                        "${originalFile.nameWithoutExtension}-${versionSuffix}.${outputExtension}"
                    )
                    if (renamedFile.exists()) renamedFile.delete()
                    if (originalFile.renameTo(renamedFile)) {
                        project.logger.lifecycle("VersionFrau: ${outputExtension.uppercase()} → ${renamedFile.name}")
                    } else {
                        project.logger.warn("VersionFrau: could not rename ${originalFile.name} in ${variantOutputDir.absolutePath}")
                    }
                }
        }
    }

    private companion object {
        /**
         * Matches Android lifecycle tasks and captures (prefix, flavorPart, buildType):
         * "assembleDebug" → ("assemble", "", "Debug"), "bundleFreeProRelease" → ("bundle", "FreePro", "Release").
         */
        val lifecycleTaskPattern = Regex("(assemble|bundle)(\\w*?)(Debug|Release)")

        /** Matches base names already renamed by VersionFrau, e.g. "app-free-debug-v1.2.3.4". */
        val versionedFileNamePattern = Regex(".*-v\\d+\\.\\d+\\.\\d+(\\.\\d+)?")
    }
}
