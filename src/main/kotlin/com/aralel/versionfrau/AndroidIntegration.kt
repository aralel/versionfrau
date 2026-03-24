package com.aralel.versionfrau

import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Injects BUILD_TIME into the Android BuildConfig using reflection instead of direct
 * AGP class imports. This avoids NoClassDefFoundError caused by Gradle classloader
 * isolation — the plugin JAR's classloader may not see AGP classes even when the user's
 * project has AGP applied.
 */
internal object AndroidIntegration {

    fun injectBuildTime(project: Project) {
        val androidExtension = project.extensions.findByName("android") ?: return

        val buildTimeValue = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))

        // Enable BuildConfig generation — AGP 8.x+ has it disabled by default.
        // Reflective call equivalent to: android.buildFeatures.buildConfig = true
            val buildFeatures = androidExtension.javaClass
                .getMethod("getBuildFeatures")
                .invoke(androidExtension)
            buildFeatures.javaClass
                .getMethod("setBuildConfig", Boolean::class.javaPrimitiveType)
                .invoke(buildFeatures, true)

        // Reflective call equivalent to:
        // android.defaultConfig.buildConfigField("String", "BUILD_TIME", "\"$buildTimeValue\"")
            val defaultConfig = androidExtension.javaClass
                .getMethod("getDefaultConfig")
                .invoke(androidExtension)
            defaultConfig.javaClass
                .getMethod("buildConfigField", String::class.java, String::class.java, String::class.java)
                .invoke(defaultConfig, "String", "BUILD_TIME", "\"$buildTimeValue\"")
            project.logger.lifecycle("VersionFrau: BUILD_TIME = \"$buildTimeValue\"")
    }
}
