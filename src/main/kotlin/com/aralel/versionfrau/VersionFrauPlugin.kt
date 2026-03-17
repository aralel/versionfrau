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

        project.afterEvaluate {
            setupVersionTasks(project, extension)
        }
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

        // Hook into Android build tasks if the Android plugin is applied
        val hasAndroid = try {
            project.extensions.findByName("android") != null
        } catch (e: Exception) {
            false
        }

        if (hasAndroid) {
            hookIntoAndroidTasks(project, incrementBuildTask.get(), incrementPatchTask.get())
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
}
