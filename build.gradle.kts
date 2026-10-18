plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

group = "com.aralel"
version = "0.0.18"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(gradleApi())
    compileOnly(libs.android.gradle.plugin)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Android functional tests build a real app with the same AGP version the plugin compiles against.
    systemProperty("versionfrau.agpVersion", libs.versions.agp.get())
}

gradlePlugin {
    plugins {
        create("versionfrau") {
            id = "com.aralel.versionfrau"
            implementationClass = "com.aralel.versionfrau.VersionFrauPlugin"
            displayName = "VersionFrau"
            description = "Automatic version management for Android projects"
        }
    }
}

mavenPublishing {
    coordinates(groupId = group.toString(), artifactId = "versionfrau", version = version.toString())
    // Maven Central target and signing are enabled via gradle.properties
    // (mavenCentralPublishing=true, signAllPublications=true); calling them here as well fails.

    pom {
        name.set("VersionFrau")
        description.set(
            "Gradle plugin that automatically manages version names and version codes for Android projects " +
                "from a version.properties file, incrementing build or patch on debug and release builds " +
                "and writing the version into APK and AAB file names."
        )
        inceptionYear.set("2025")
        url.set("https://github.com/aralel/versionfrau")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("maysam")
                name.set("Maysam Torabi")
                email.set("kherad@gmail.com")
                organization.set("Aralel GmbH")
                organizationUrl.set("https://github.com/aralel")
            }
        }
        scm {
            url.set("https://github.com/aralel/versionfrau")
            connection.set("scm:git:git://github.com/aralel/versionfrau.git")
            developerConnection.set("scm:git:ssh://git@github.com/aralel/versionfrau.git")
        }
    }
}

kotlin {
    jvmToolchain(17)
}
