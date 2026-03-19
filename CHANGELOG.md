# Changelog

## [1.0.2] - 2026-03-19

### Added
- Automatic `BUILD_TIME` buildConfigField injection for every Android build (formatted as "MMM dd, yyyy")
- `buildTime` property on `VersionFrauExtension` for direct access to the formatted build timestamp
- Unit tests for `VersionFrauExtension` (version read/write, version name/code formatting, build time)
- Functional tests using Gradle TestKit verifying debug build increments on every build

### Fixed
- `incrementBuildVersion` not running on `assembleDebug` — `configureEach` in `afterEvaluate` missed AGP tasks that were already realized; now uses `variant.assembleProvider.configure` and `tasks.matching().all` for reliable dependency wiring

### Changed
- Extracted Android-specific plugin logic into `AndroidIntegration` class for better separation and testability
- Plugin now loads cleanly in non-Android Gradle projects (no `NoClassDefFoundError` for `AppExtension`)
- Increment tasks registered eagerly in `apply()` instead of lazily in `afterEvaluate`; only dependency wiring deferred to `afterEvaluate`

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
