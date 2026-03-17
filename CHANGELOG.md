# Changelog

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
