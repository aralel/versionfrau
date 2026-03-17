plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "1.9.22"
}

group = "com.github.aralel"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(gradleApi())
    compileOnly("com.android.tools.build:gradle:8.2.0")
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
