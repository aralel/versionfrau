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
    testImplementation("com.android.tools.build:gradle:8.2.0")
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
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
