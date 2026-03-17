# VersionFrau

A Gradle plugin that automatically manages version numbers for your Android (or JVM) project using a `version.properties` file.

- **Debug build** (`assembleDebug` / `bundleDebug`) → increments **build** version
- **Release build** (`assembleRelease` / `bundleRelease`) → increments **patch** version (resets build to 0)

## Setup

### 1. Add JitPack repository

In your root `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.aralel.versionfrau") {
                useModule("com.github.aralel:versionfrau:${requested.version}")
            }
        }
    }
}
```

### 2. Apply the plugin

In your app-level `build.gradle.kts`:

```kotlin
plugins {
    id("com.aralel.versionfrau") version "1.0.0"
}
```

### 3. Use version values

```kotlin
android {
    defaultConfig {
        versionCode = versionFrau.versionCode
        versionName = versionFrau.versionName
    }
}
```

That's it. Two lines of config in `defaultConfig`.

## How It Works

A `version.properties` file is created in your project root:

```properties
major=1
minor=0
patch=0
build=0
```

| Build type | What happens |
|---|---|
| `./gradlew assembleDebug` | `build` incremented (e.g. `0 → 1`) |
| `./gradlew assembleRelease` | `patch` incremented, `build` reset to 0 |

## Extension Properties

| Property | Type | Example | Description |
|---|---|---|---|
| `versionFrau.major` | `Int` | `1` | Major version |
| `versionFrau.minor` | `Int` | `0` | Minor version |
| `versionFrau.patch` | `Int` | `3` | Patch version |
| `versionFrau.build` | `Int` | `7` | Build version |
| `versionFrau.versionName` | `String` | `"1.0.3"` | `major.minor.patch` |
| `versionFrau.fullVersionName` | `String` | `"1.0.3.7"` | `major.minor.patch.build` |
| `versionFrau.versionCode` | `Int` | `1000307` | Computed integer for Android |

## Manual Tasks

You can also run version bumps manually:

```bash
./gradlew incrementBuildVersion
./gradlew incrementPatchVersion
./gradlew incrementMinorVersion   # resets patch & build
./gradlew incrementMajorVersion   # resets minor, patch & build
```

## Custom Version File Location

```kotlin
versionFrau {
    versionFile = file("config/version.properties")
}
```

## License

MIT
