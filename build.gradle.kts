plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
}

group = "com.github.aralel"
version = "1.0.0"

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.aralel"
            artifactId = "versionfrau"
            version = project.version.toString()
        }
    }
}

kotlin {
    jvmToolchain(17)
}
