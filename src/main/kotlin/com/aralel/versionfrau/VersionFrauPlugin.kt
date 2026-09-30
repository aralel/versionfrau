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
        extension.plannedIncrement = resolvePlannedIncrement(project)

        // Register increment tasks eagerly so they are available for dependency wiring.
        val incrementTasks = registerIncrementTasks(project, extension)

        // Configure BUILD_TIME injection as soon as the Android plugin is available.
        // Uses the stable AGP Variant API (finalizeDsl + onVariants) to enable
        // buildConfig and inject the field into every variant.
        project.pluginManager.withPlugin("com.android.application") {
            AndroidIntegration.configure(project, extension, variantsNamedAtGeneration)
        }

        project.afterEvaluate {
            // Wire task dependencies using task names — no AGP class references needed.
            wireTaskDependencies(project, extension, incrementTasks.first, incrementTasks.second)
        }
    }

    /**
     * Names of variants whose APK / AAB outputs AGP generates directly with the versioned file
     * name (see [AndroidIntegration]). Only variants missing from this set fall back to renaming.
     */
    private val variantsNamedAtGeneration: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Files modified before this moment belong to earlier builds and are never renamed. */
    private val buildStartTimeMillis = System.currentTimeMillis()

    /**
     * Predicts which increment the requested lifecycle tasks will run. A release task wins over a
     * debug task because incrementPatchVersion runs last and resets the build number.
     */
    private fun resolvePlannedIncrement(project: Project): PlannedIncrement {
        val requestedBuildTypes = project.gradle.startParameter.taskNames
            .mapNotNull { requestedTaskName ->
                lifecycleTaskPattern.matchEntire(requestedTaskName.substringAfterLast(':'))?.groupValues?.get(3)
            }
        return when {
            "Release" in requestedBuildTypes -> PlannedIncrement.PATCH
            "Debug" in requestedBuildTypes -> PlannedIncrement.BUILD
            else -> PlannedIncrement.NONE
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
                // Order after BOTH increment tasks: when debug and release variants are built
                // in one invocation, every variant must be renamed with the same final version
                // instead of whichever increments happened to have run so far.
                task.mustRunAfter(incrementBuildTask, incrementPatchTask)
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
     * FALLBACK ONLY. Normally [AndroidIntegration] makes AGP write the APK / AAB with the versioned
     * name directly, so nothing is renamed. If that was not possible for this variant (an AGP
     * version whose internals differ), this doLast renames the outputs instead — but only files
     * produced by the current build; older files in the output directory are never touched.
     *
     * Variant output directories:
     * - assembleDebug (no flavors)  → build/outputs/apk/debug/
     * - assembleFreeDebug           → build/outputs/apk/free/debug/
     * - bundleRelease (no flavors)  → build/outputs/bundle/release/
     * - bundleFreeRelease           → build/outputs/bundle/freeRelease/
     *
     * Aggregate tasks on flavored projects (e.g. assembleDebug when flavors exist) skip — the
     * per-flavor tasks handle their own outputs.
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
        val outputExtension = if (isAssemble) "apk" else "aab"
        val variantName = if (flavorName.isEmpty()) {
            buildTypeName
        } else {
            flavorName + buildTypeName.replaceFirstChar { it.uppercase() }
        }

        task.doLast {
            if (AndroidIntegration.namedAtGenerationKey(outputExtension, variantName) in variantsNamedAtGeneration) {
                // AGP already generated the file with the versioned name.
                return@doLast
            }

            if (flavorName.isEmpty() && hasFlavoredVariantTasks(project, isAssemble, isDebugVariant)) {
                // Aggregate task on a flavored project — per-flavor tasks handle their own outputs.
                return@doLast
            }

            val variantOutputDir = if (isAssemble) {
                val apkRelativePath = if (flavorName.isEmpty()) {
                    "outputs/apk/$buildTypeName"
                } else {
                    "outputs/apk/$flavorName/$buildTypeName"
                }
                project.layout.buildDirectory.dir(apkRelativePath).get().asFile
            } else {
                project.layout.buildDirectory.dir("outputs/bundle/$variantName").get().asFile
            }
            if (!variantOutputDir.isDirectory) {
                return@doLast
            }

            val versionSuffix = extension.outputVersionSuffix(isDebugVariant)
            variantOutputDir.walkTopDown()
                .filter { it.isFile && it.extension == outputExtension }
                .filter { it.lastModified() >= buildStartTimeMillis }
                .filterNot { versionedFileNamePattern.matches(it.nameWithoutExtension) }
                .forEach { generatedFile ->
                    val renamedFile = File(
                        generatedFile.parentFile,
                        "${generatedFile.nameWithoutExtension}-${versionSuffix}.${outputExtension}"
                    )
                    if (renamedFile.exists()) renamedFile.delete()
                    if (generatedFile.renameTo(renamedFile)) {
                        project.logger.lifecycle("VersionFrau: ${outputExtension.uppercase()} → ${renamedFile.name} (renamed)")
                    } else {
                        project.logger.warn("VersionFrau: could not rename ${generatedFile.name} in ${variantOutputDir.absolutePath}")
                    }
                }
        }
    }

    /**
     * True when the project defines product flavors, detected by the presence of a flavored
     * lifecycle task for the same prefix and build type (e.g. assembleFreeDebug for assembleDebug).
     */
    private fun hasFlavoredVariantTasks(project: Project, isAssemble: Boolean, isDebugVariant: Boolean): Boolean {
        val expectedTaskPrefix = if (isAssemble) "assemble" else "bundle"
        val expectedBuildTypePart = if (isDebugVariant) "Debug" else "Release"
        return project.tasks.names.any { taskName ->
            val lifecycleMatch = lifecycleTaskPattern.matchEntire(taskName) ?: return@any false
            val (taskPrefix, flavorPart, buildTypePart) = lifecycleMatch.destructured
            taskPrefix == expectedTaskPrefix && buildTypePart == expectedBuildTypePart && flavorPart.isNotEmpty()
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
