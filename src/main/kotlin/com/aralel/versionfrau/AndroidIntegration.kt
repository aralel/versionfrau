package com.aralel.versionfrau

import com.android.build.gradle.AppExtension
import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encapsulates the one piece of Android-specific logic that truly requires AGP classes:
 * injecting BUILD_TIME into the android defaultConfig.
 *
 * All other Android integration (task wiring, output renaming) is handled in
 * [VersionFrauPlugin] using plain task-name matching and file conventions, so it
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
}
