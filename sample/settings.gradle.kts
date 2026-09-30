pluginManagement {
    // Use the plugin from the parent project (composite build)
    includeBuild("..")
}

dependencyResolutionManagement {
    // Share the plugin project's version catalog so AGP and friends are pinned in one place.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "sample"
