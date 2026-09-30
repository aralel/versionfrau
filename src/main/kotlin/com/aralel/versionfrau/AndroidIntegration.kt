package com.aralel.versionfrau

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.BuildConfigField
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android-specific wiring using the AGP Variant API:
 *
 * - [ApplicationAndroidComponentsExtension.finalizeDsl] enables `buildFeatures.buildConfig` after
 *   the user's DSL but before variants are created.
 * - [ApplicationAndroidComponentsExtension.onVariants] adds the BUILD_TIME field to every variant
 *   and makes AGP generate the APK / AAB directly with the versioned file name
 *   (e.g. `app-free-debug-v1.2.3.4.apk`) — nothing is renamed after the fact, so older files in
 *   the output directories are never touched.
 *
 * Output naming relies on two AGP internals that have been stable for years but are not public
 * API, so they are reached reflectively: `VariantOutputImpl.outputFileName` (APK) and
 * `FinalizeBundleTask.finalBundleFile` (AAB, task `sign<Variant>Bundle`). Variants that could be
 * named this way are recorded in `variantsNamedAtGeneration`; any other variant falls back to
 * the rename step in [VersionFrauPlugin].
 */
internal object AndroidIntegration {

    fun namedAtGenerationKey(outputExtension: String, variantName: String): String = "$outputExtension:$variantName"

    fun configure(
        project: Project,
        extension: VersionFrauExtension,
        variantsNamedAtGeneration: MutableSet<String>
    ) {
        val androidComponents = project.extensions
            .getByType(ApplicationAndroidComponentsExtension::class.java)

        val buildTimeValue = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))

        // Enable BuildConfig generation — AGP 8.x+ has it disabled by default.
        // finalizeDsl runs after the user's android { } block but before variants are created,
        // so this won't conflict with anything the user has set.
        @Suppress("UnstableApiUsage")
        androidComponents.finalizeDsl { androidExtension ->
            androidExtension.buildFeatures.buildConfig = true
        }

        androidComponents.onVariants { variant ->
            variant.buildConfigFields.put(
                "BUILD_TIME",
                BuildConfigField(
                    type = "String",
                    value = "\"$buildTimeValue\"",
                    comment = "Build timestamp injected by VersionFrau"
                )
            )

            val isDebugVariant = variant.buildType != "release"
            alignEmbeddedVersionWithBuildType(variant, extension, isDebugVariant)

            val versionSuffix = extension.outputVersionSuffix(isDebugVariant)

            if (nameApkOutputsAtGeneration(project, variant, versionSuffix)) {
                variantsNamedAtGeneration += namedAtGenerationKey("apk", variant.name)
            }
            nameBundleAtGeneration(project, variant.name, versionSuffix) {
                variantsNamedAtGeneration += namedAtGenerationKey("aab", variant.name)
            }
        }

        project.logger.lifecycle("VersionFrau: BUILD_TIME = \"$buildTimeValue\"")
    }

    /**
     * `versionFrau.versionName` / `versionCode` in `defaultConfig` are one value for all variants,
     * chosen from the requested tasks. In a mixed invocation (e.g. `assembleDebug bundleRelease`)
     * that is the release form, so debug variants would embed "1.0.4" instead of "1.0.4.0".
     * This rewrites each output's version to its own build type's form — but only where the
     * configured value came from VersionFrau (a `versionNameSuffix` such as "-dev" is kept);
     * values the user set to something else are left alone.
     */
    private fun alignEmbeddedVersionWithBuildType(
        variant: ApplicationVariant,
        extension: VersionFrauExtension,
        isDebugVariant: Boolean
    ) {
        val invocationVersionName = extension.versionName
        val invocationVersionCode = extension.versionCode
        val variantVersionName = extension.versionNameFor(isDebugVariant)
        val variantVersionCode = extension.versionCodeFor(isDebugVariant)
        if (invocationVersionName == variantVersionName && invocationVersionCode == variantVersionCode) return

        for (variantOutput in variant.outputs) {
            val configuredVersionName = variantOutput.versionName.orNull
            if (configuredVersionName != null && configuredVersionName.startsWith(invocationVersionName)) {
                val versionNameSuffix = configuredVersionName.removePrefix(invocationVersionName)
                // A remaining ".<digit>" means the configured name is a longer version (e.g.
                // fullVersionName), not versionName + suffix — leave it untouched.
                if (!versionNameSuffix.matches(Regex("\\.\\d.*"))) {
                    variantOutput.versionName.set(variantVersionName + versionNameSuffix)
                }
            }
            if (variantOutput.versionCode.orNull == invocationVersionCode) {
                variantOutput.versionCode.set(variantVersionCode)
            }
        }
    }

    /**
     * Sets the file name of every APK output of [variant] (one per ABI/density split) by adding
     * the version suffix to AGP's default name. Returns false when any output could not be named.
     */
    private fun nameApkOutputsAtGeneration(project: Project, variant: ApplicationVariant, versionSuffix: String): Boolean {
        if (variant.outputs.isEmpty()) return false

        return variant.outputs.all { variantOutput ->
            val outputFileNameProperty = invokeGetter(variantOutput, "getOutputFileName") as? Property<*>
            val defaultOutputFileName = outputFileNameProperty?.orNull as? String
            if (outputFileNameProperty == null || defaultOutputFileName == null) {
                project.logger.info("VersionFrau: cannot set APK name for ${variant.name} at generation — will rename after build")
                return@all false
            }
            @Suppress("UNCHECKED_CAST")
            (outputFileNameProperty as Property<String>).set(versionedFileName(defaultOutputFileName, versionSuffix))
            true
        }
    }

    /**
     * Points the AAB produced by `sign<Variant>Bundle` (AGP's final bundle task, present for every
     * variant) at the versioned file name in the same directory. [onNamed] runs once it succeeded.
     *
     * AGP assigns the default bundle location only after the task's own configuration, so the
     * property is adjusted once the task graph is ready — after all configuration, before execution.
     */
    private fun nameBundleAtGeneration(project: Project, variantName: String, versionSuffix: String, onNamed: () -> Unit) {
        val finalizeBundleTaskName = "sign${variantName.replaceFirstChar { it.uppercase() }}Bundle"

        project.gradle.taskGraph.whenReady { taskGraph ->
            val finalizeBundleTask = taskGraph.allTasks.firstOrNull { scheduledTask: Task ->
                scheduledTask.project == project && scheduledTask.name == finalizeBundleTaskName
            } ?: return@whenReady

            val finalBundleFileProperty = invokeGetter(finalizeBundleTask, "getFinalBundleFile") as? RegularFileProperty
            val defaultBundleFile = finalBundleFileProperty?.orNull?.asFile
            if (finalBundleFileProperty == null || defaultBundleFile == null) {
                project.logger.info("VersionFrau: cannot set AAB name for $variantName at generation — will rename after build")
                return@whenReady
            }
            if (!versionedFileNamePattern.matches(defaultBundleFile.nameWithoutExtension)) {
                finalBundleFileProperty.set(File(defaultBundleFile.parentFile, versionedFileName(defaultBundleFile.name, versionSuffix)))
            }
            onNamed()
        }
    }

    /** Matches base names that already carry a VersionFrau suffix, e.g. "app-free-debug-v1.2.3.4". */
    private val versionedFileNamePattern = Regex(".*-v\\d+\\.\\d+\\.\\d+(\\.\\d+)?")

    /** "app-free-debug.apk" + "v1.2.3.4" → "app-free-debug-v1.2.3.4.apk". */
    private fun versionedFileName(defaultFileName: String, versionSuffix: String): String {
        val baseName = defaultFileName.substringBeforeLast('.')
        val fileExtension = defaultFileName.substringAfterLast('.', missingDelimiterValue = "")
        return if (fileExtension.isEmpty()) "$baseName-$versionSuffix" else "$baseName-$versionSuffix.$fileExtension"
    }

    private fun invokeGetter(target: Any, getterName: String): Any? = try {
        target.javaClass.getMethod(getterName).invoke(target)
    } catch (reflectionFailure: ReflectiveOperationException) {
        null
    }
}
