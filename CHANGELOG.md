# Changelog

## [Unreleased]

### Docs
- README and website: JitPack removed. The plugin is installed from Maven Central (`com.aralel:versionfrau` 0.0.18 + plugin marker); setup only needs `mavenCentral()` in `pluginManagement.repositories`, no `resolutionStrategy`
- Website added in `docs/` (GitHub Pages, `https://versionfrau.aralel.com`, with `CNAME` and `.nojekyll`); the README links to it
- README: Xcode link now points to the real repository `aralel/versionfrau-ios` (was `aralel/versionfrau-xcode`)
- README: plugin version in the setup example is `0.0.18` (the `1.0.0` shown before was never released)
- README: `version.properties` is created in the module folder (usually `app/version.properties`), not the project root
- README: `versionCode` examples match the code (`1000003042` / `1000003000`, formula `major*1_000_000_000 + minor*1_000_000 + patch*1_000 + build`)
- README: custom `versionFile` block must be placed above `android { }`; link to the website https://versionfrau.codehospital.com

### Xcode
- VersionFrau for Xcode (added here as `xcode/`) moved to its own repository, [aralel/versionfrau-xcode](https://github.com/aralel/versionfrau-xcode), installable with Homebrew or a curl installer. The README links to it

### Publishing
- Added `scripts/publish.sh`: picks the GPG key, uploads its public key to keyserver.ubuntu.com, exports the private key in Gradle's single-line form (no header/footer/checksum) with the matching 8-character key id, writes the signing and Central token properties to `~/.gradle/gradle.properties` (backup in `gradle.properties.bak`, `chmod 600`), verifies every artifact gets a `.asc` via `publishToMavenLocal`, then uploads (`--release` auto-releases, `--local-only` skips the upload, `--key <ID>` selects a key)
- Added Maven Central publishing via `com.vanniktech.maven.publish` (version in the catalog): sources + javadoc jars, full POM (name, description, license, developer, SCM) and GPG signing
- Group changed from `com.github.aralel` to `com.aralel` (matches the plugin id; requires the `com.aralel` namespace to be verified on the Sonatype Central Portal). JitPack still serves `com.github.aralel`
- Added the missing `LICENSE` file (MIT, Aralel GmbH), which Maven Central requires
- Removed the manual `maven-publish` publication block; the plugin creates the publication
- Fixed `The value for this property is final and cannot be changed any further` on `publishToMavenCentral`: Maven Central target and signing are now enabled only through `gradle.properties` (`mavenCentralPublishing=true`, `signAllPublications=true`); the duplicate `publishToMavenCentral()` / `signAllPublications()` calls were removed from `build.gradle.kts`. Signing is therefore always on — every publish, including `publishToMavenLocal`, needs the GPG credentials

### Build
- Added a Gradle version catalog (`gradle/libs.versions.toml`) — AGP, Kotlin and JUnit versions are now declared once and referenced as `libs.*` in `build.gradle.kts`
- Removed the duplicate `com.android.tools.build:gradle` declaration ("declared multiple times"): AGP is only `compileOnly`; the Android functional tests get their AGP from the generated test project's `buildscript`, whose version is now passed in from the catalog (`versionfrau.agpVersion` system property) instead of being hardcoded
- `sample/` imports the same catalog (`../gradle/libs.versions.toml`) and uses `libs.android.gradle.plugin`

### Changed
- **APK and AAB files are now generated with the versioned name instead of being renamed** — `AndroidIntegration` sets AGP's per-output `outputFileName` (APK, incl. every ABI/density split) and the `sign<Variant>Bundle` task's `finalBundleFile` (AAB) to e.g. `app-free-debug-v1.2.3.4.apk` / `app-free-release-v1.2.4.aab`. AGP's `output-metadata.json` points at the versioned file, and old files in the output directories are never touched. Works with and without product flavors; verified on AGP 8.2.0 and 9.4.1
- Output renaming in `VersionFrauPlugin` is now only a fallback for variants AGP could not name directly (the two AGP internals above are accessed reflectively), and it only renames files modified during the current build — never older files
- `versionName`, `versionCode` and `fullVersionName` now return the version the current build produces (the version on disk plus the planned increment, predicted from the requested tasks), snapshotted so reading them after the increment task ran does not double-count. `major` / `minor` / `patch` / `build` still return the raw values on disk

### Fixed
- **Debug variants embedded the release-style version in mixed builds** — with `assembleDebug bundleRelease` in one invocation, `versionFrau.versionName` resolves to the release form (`1.0.4`), and since `defaultConfig` holds one value for all variants, debug APKs embedded `1.0.4` instead of `1.0.4.0`. `AndroidIntegration` now rewrites each variant output's `versionName` / `versionCode` (public `VariantOutput` API) to its own build type's form, keeping any `versionNameSuffix` and leaving values not set from VersionFrau untouched. Debug file names and embedded versions now both use the four-part form
- **Version inside the APK/AAB was one behind the file name** — `versionName`/`versionCode` were read at configuration time, before `incrementBuildVersion` / `incrementPatchVersion` ran, so e.g. `app-debug-v1.0.2.1.apk` contained `versionName=1.0.2.0`. Both now carry the same version
- **Flavored projects renamed stale un-flavored outputs** — aggregate tasks (`assembleDebug` / `bundleRelease`) detected "flavored project" by the absence of `outputs/apk/debug/`; a leftover dir from a build made before flavors were added made them rename old files. Flavors are now detected from the task graph (a flavored lifecycle task like `assembleFreeDebug` exists), so the same plugin setup behaves correctly with and without flavors, and when switching between the two
- **Mixed debug + release builds renamed flavors with different versions** — e.g. `assembleDebug bundleFreeRelease` ran `incrementPatchVersion` mid-build, so some flavor APKs got `v1.0.0.2` and others `v1.0.1.0`. Every lifecycle task now runs after both increment tasks, so all outputs carry the same final version
- **Build broken on Gradle 9.7.1** — Kotlin 1.9.22 cannot read Gradle 9's Kotlin 2.4 stdlib metadata; bumped the Kotlin JVM plugin to 2.4.20 and added the `junit-platform-launcher` test runtime dependency Gradle 9 requires

### Added
- Functional tests: AAB renaming with and without flavors, stale un-flavored outputs left untouched after adding flavors, and consistent versions across all variants in a mixed debug + release build
- Functional tests: APK and AAB generated with the versioned name (no renaming) with and without flavors, embedded `versionName`/`versionCode` matching the file name (via `output-metadata.json`), and old output files never renamed; unit tests for the planned-increment target version
- Verified manually against AGP 9.4.1 (plugins DSL, Kotlin DSL, two flavor dimensions) in addition to the AGP 8.2.0 test suite

- **Product flavors: only one APK/AAB was renamed per build** — output renaming previously picked the single most-recently-modified file under `outputs/`, so with flavors (e.g. `assembleDebug` building `free` + `paid`) only one flavor's APK got renamed, chosen by timestamp. Renaming is now scoped per variant: each lifecycle task (`assembleFreeDebug`, `bundleFreeRelease`, …) renames all fresh outputs in its own variant directory (`outputs/apk/<flavor>/<buildType>/`, `outputs/bundle/<flavorBuildType>/`)
- **Product flavors: renamed files lost the flavor name** — the new name was always `{project}-{buildType}-v…`; renaming now keeps AGP's original base name (e.g. `app-free-debug` → `app-free-debug-v1.2.3.4.apk`), which also keeps ABI-split APKs distinct
- **Product flavors: rename action ran multiple times per build** — both the aggregate task (`assembleDebug`) and every per-flavor task matched, each re-renaming the same "last modified" file. Aggregate tasks on flavored projects now skip silently (no variant dir of their own) while per-flavor tasks handle their own outputs
- **Crash when an output dir existed but contained no APK/AAB** — `maxByOrNull { … }!!` threw an NPE and failed the build; renaming now simply skips when there is nothing to rename
- **Stale outputs from previous builds re-renamed** — files already carrying a `-vX.Y.Z(.B)` suffix are now left untouched instead of competing in the "last modified" pick (replaces the previous last-file-created heuristic with a deterministic rule)
- **Flavored AAB directory layout not found** — bundle renaming now looks in `outputs/bundle/<flavorBuildType>/` (e.g. `freeRelease`), matching AGP's flavored layout

### Added
- Android functional tests covering product flavors: dry-run wiring for `assembleFreeDebug`/`assembleFreeRelease`, per-flavor APK renaming, all-flavor renaming with a single increment on `assembleDebug`, and `BUILD_TIME` in flavored `BuildConfig`

### Changed
- Rewrote `AndroidIntegration` to use the stable AGP Variant API (`AndroidComponentsExtension`) instead of reflection
  - `finalizeDsl` enables `buildFeatures.buildConfig = true` at the correct lifecycle point
  - `onVariants` injects `BUILD_TIME` via `variant.buildConfigFields.put()` with `BuildConfigField`
  - No reflection, no try/catch — uses the documented, type-safe public API

## [1.0.2] - 2026-03-19

### Added
- Automatic `BUILD_TIME` buildConfigField injection for every Android build (formatted as "MMM dd, yyyy")
- `buildTime` property on `VersionFrauExtension` for direct access to the formatted build timestamp
- Unit tests for `VersionFrauExtension` (version read/write, version name/code formatting, build time)
- Functional tests using Gradle TestKit verifying debug build increments on every build
- Android functional tests (7 tests) with real AGP: dry-run task wiring, actual increment, consecutive increments, BUILD_TIME injection, APK renaming
- Sample Android project (`sample/`) for manual integration testing via composite build

### Fixed
- **`incrementBuildVersion` not running on `assembleDebug`** — root cause was classloader isolation: `AndroidIntegration` referenced AGP classes that weren't visible to the plugin's classloader. Task wiring now uses `project.tasks.all { }` with name matching directly in `VersionFrauPlugin` (no AGP imports), which works reliably regardless of classloader setup or plugin load order
- **AAB/APK output renaming not working** — same classloader issue as above; output renaming moved to `VersionFrauPlugin` using conventional file paths instead of AGP variant API
- **`BUILD_TIME` not appearing in `BuildConfig`** — `defaultConfig.buildConfigField()` in `afterEvaluate` was too late (AGP already finalized variants). Now injected via `pluginManager.withPlugin("com.android.application")` during the configuration phase
- **`versionCode` formula producing values exceeding `Int.MAX_VALUE`** — changed from `major * 1_000_000_000` to `major * 1_000_000 + minor * 10_000 + patch * 100 + build`
- **Spurious task matching** — internal AGP tasks like `bundleReleaseResources` no longer matched; lifecycle task detection now requires name to end with "debug" or "release"

### Changed
- All task wiring AND output renaming handled in `VersionFrauPlugin` — zero AGP class references
- `AndroidIntegration` reduced to BUILD_TIME injection only (the one feature that truly needs `AppExtension`)
- Increment tasks registered eagerly in `apply()` instead of lazily in `afterEvaluate`

## [1.0.1] - 2026-03-18

### Changed
- `versionName` now returns `major.minor.patch.build` for debug builds and `major.minor.patch` for release builds
- `versionCode` now includes the build component for debug builds only; release builds always end in `00` (e.g. `1000300`)
- `fullVersionName` is unchanged — always returns `major.minor.patch.build` for both build types
- Build-type is resolved at plugin apply-time from Gradle start parameters so user code in `android { defaultConfig { } }` sees the correct values with no extra configuration

### Added
- Automatic APK and AAB output file renaming after each build
  - Debug:   `{project}-debug-v{major}.{minor}.{patch}.{build}.apk|aab`
  - Release: `{project}-release-v{major}.{minor}.{patch}.apk|aab`
- Renaming uses the post-increment version so the filename always reflects the actual build version
- Product flavors supported — each variant is handled independently

## [1.0.0] - 2026-03-17

### Added
- Initial release of VersionFrau Gradle plugin
- Automatic `version.properties` file management with major, minor, patch, and build versions
- Auto-increment build version on debug builds (assembleDebug, bundleDebug)
- Auto-increment patch version on release builds (assembleRelease, bundleRelease)
- Manual increment tasks: `incrementBuildVersion`, `incrementPatchVersion`, `incrementMinorVersion`, `incrementMajorVersion`
- Extension properties: `versionName`, `versionCode`, `fullVersionName`, `major`, `minor`, `patch`, `build`
- Support for both Android and standard Gradle projects
- JitPack-compatible publishing configuration
- Gradle wrapper for consistent builds
- .gitignore with relevant entries
