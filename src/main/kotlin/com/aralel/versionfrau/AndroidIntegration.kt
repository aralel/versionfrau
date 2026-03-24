package com.aralel.versionfrau

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Injects BUILD_TIME into the Android BuildConfig using the stable AGP Variant API.
 *
 * - [AndroidComponentsExtension.finalizeDsl] enables `buildFeatures.buildConfig` after
 *   the user's DSL but before variants are created.
 * - [AndroidComponentsExtension.onVariants] adds the BUILD_TIME field to every variant
 *   via the documented [BuildConfigField] API.
 */
internal object AndroidIntegration {

    fun configure(project: Project) {
        val androidComponents = project.extensions
            .getByType(AndroidComponentsExtension::class.java)

        val buildTimeValue = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))

        // Enable BuildConfig generation — AGP 8.x+ has it disabled by default.
        // finalizeDsl runs after the user's android { } block but before variants are created,
        // so this won't conflict with anything the user has set.
        androidComponents.finalizeDsl { extension ->
            extension.buildFeatures.buildConfig = true
        }

        // Inject BUILD_TIME into every variant's BuildConfig using the Variant API.
        androidComponents.onVariants { variant ->
            variant.buildConfigFields.put(
                "BUILD_TIME",
                BuildConfigField(
                    type = "String",
                    value = "\"$buildTimeValue\"",
                    comment = "Build timestamp injected by VersionFrau"
                )
            )
        }

        project.logger.lifecycle("VersionFrau: BUILD_TIME = \"$buildTimeValue\"")
    }
}
