# Plan

## Current prompt (2026-11-23)

> fix the problems, domain will be versionfrau.codehospital.com

### Status: done (no commits)

- README links to the new website (`../website`, https://versionfrau.codehospital.com). Fixed: Xcode repo link (`versionfrau-ios`), version `0.0.18` instead of `1.0.0`, `app/version.properties` location, versionCode examples, `versionFrau {}` placement.

## Current prompt (2026-10-24, follow-up)

> can you move it out to it's own folder and also make it like a package we can install from github?

### Status: done

- `xcode/` moved to `/Users/maysam/Workspace/aralel/versionfrau-xcode` (its own package: Homebrew formula, curl installer,
  release script, CI). Its PLAN.md and CHANGELOG.md carry the details. This repo only links to it from the README.

## Current prompt (2026-10-24)

> can we make a similar plugin for xcode projects?

### Status: done

- `xcode/versionfrau` (bash 3.2, no dependencies): scheme pre-action `increment` runs once per build, and the Run Script phase
  `apply` runs in each app and extension target and patches the built Info.plist. Uses the same version.properties format,
  formulas and debug/release rules as the Gradle plugin.
- Why a pre-action: extensions build before the app, so incrementing inside a target's phase would give the app and its
  extensions different CFBundleVersions, which App Store Connect rejects.
- `install` uses the xcodeproj gem. Xcode 27 shows pre-action output and fails the build on a pre-action error.
- Tests: `xcode/tests/run-tests.sh`, 33 passing (Xcode 27.1, iOS 27.1 simulator SDK); archive verified manually.
- Follow-ups: Homebrew formula / separate repo for distribution; optional .ipa renaming on `-exportArchive`
  (the analogue of the APK/AAB naming); SwiftUI preview builds are not detected and still increment.

## Current prompt (2026-10-18)

> "Cannot find key with id '0x6D58632B' in key data" — can you make a bash file that does everything automatically?

### Status: done

- Added `scripts/publish.sh` (bash 3.2 compatible): key selection, public key upload, loopback export of the private key
  (wrong passphrase → stops), writes signing + Central token properties (backup, chmod 600), `publishToMavenLocal`
  signature check, then `publishToMavenCentral` / `publishAndReleaseToMavenCentral`.
- Cause of the error: key data and key id in ~/.gradle/gradle.properties came from different copies; the script derives both from one key.
- The user's private key (FDD0DD316D58632B) was shared into the chat via an editor selection; recommended rotating it before the first release.

## Current prompt (2026-10-17, follow-up)

> `./gradlew publishToMavenCentral` → build.gradle.kts line 47: "The value for this property is final and cannot be changed any further."

### Status: done (build script); user action needed in ~/.gradle/gradle.properties

- Cause: user added `mavenCentralPublishing=true` / `signAllPublications=true` to project gradle.properties, so the
  Vanniktech plugin configured them already; the explicit calls in `mavenPublishing {}` set them a second time. Removed the calls.
- Remaining (user's file): `signingInMemoryKeyId` has an inline `# ...` comment (not supported in .properties) and
  `signingInMemoryKey` still ends with the armor checksum `=XXXX`. With both corrected on the command line,
  `publishToMavenLocal` signs every artifact of 0.0.18.

## Current prompt (2026-10-17)

> what should i do with build.gradle.kts, also what should i put in component description and other publishing fields to get it approved

### Status: done

- `build.gradle.kts`: Vanniktech publish plugin, POM metadata, conditional signing, group `com.aralel`; added `LICENSE`.
- Verified `publishToMavenLocal` produces jar, sources, javadoc, module, pom and `.asc` for each, plus the plugin marker.
- User's `~/.gradle/gradle.properties` needs `signingInMemoryKeyId=6D58632B` (8 chars) and the key line without the trailing `=XXXX` checksum.

## Current prompt (2026-09-30, follow-up 3)

> fix it yourself, also use libs.version  (re: "Dependency 'com.android.tools.build:gradle:8.2.0' is declared multiple times")

### Status: done

- Added `gradle/libs.versions.toml` (agp, kotlin, junit-jupiter, junit-platform); `build.gradle.kts`
  uses `libs.*` / `alias(libs.plugins.kotlin.jvm)`. AGP declared once (`compileOnly`).
- `AndroidFunctionalTest` reads the AGP version from system property `versionfrau.agpVersion`,
  set by the test task from `libs.versions.agp`.
- `sample/` imports the catalog and uses `libs.android.gradle.plugin`; sample `assembleDebug` builds.
- 37 tests passing; no duplicate-declaration warning.

## Previous prompt (2026-09-30, follow-up 2)

> use the 4-part version name for debug in mixed builds too

### Status: done

- Extension: added `versionNameFor` / `versionCodeFor(isDebugVariant)`; `versionName`, `versionCode`,
  `outputVersionSuffix` delegate to them.
- `AndroidIntegration.alignEmbeddedVersionWithBuildType` rewrites each variant output's
  versionName/versionCode (public VariantOutput API) when it came from VersionFrau; keeps suffixes.
- Added functional test (mixed `assembleFreeDebug bundlePaidRelease` → debug embeds 1.2.4.0);
  verified on AGP 9.4.1 with aapt2. 37 tests passing.

## Previous prompt (2026-09-30, follow-up)

> it should support both apk and aab, it should not rename old file, it should only rename the
> newly generated file, ideally the file should be genarated with the intended name, instead of renaming

### Status: done

- `AndroidIntegration.onVariants` names outputs at generation: APK via `VariantOutputImpl.outputFileName`,
  AAB via `sign<Variant>Bundle.finalBundleFile` (set in `taskGraph.whenReady`, after AGP assigns the
  default). Both internals reached reflectively; successfully named variants are recorded.
- `VersionFrauPlugin` renaming is now a fallback only for unnamed variants and only for files modified
  during the current build.
- Extension computes the target version (disk + planned increment from requested tasks, snapshotted),
  so embedded versionName/versionCode == file name == version.properties after the build.
- Verified on AGP 9.4.1 scratch project (no flavors / 2 dimensions, APK + AAB, no renames) and
  AGP 8.2.0 test suite. 36 tests passing. Updated CHANGELOG.md and README.md.

## Previous prompt (2026-09-30)

> the plugin should work when we have flavors and when we dont

### Status: done

- Toolchain: Kotlin plugin 1.9.22 → 2.4.20 and added `junit-platform-launcher` so the project
  compiles/tests on the (user-upgraded) Gradle 9.7.1 wrapper.
- Aggregate-task detection (`assembleDebug` on a flavored project) now uses task names instead of
  directory existence → stale outputs from an un-flavored build are no longer renamed.
- Lifecycle tasks `mustRunAfter` both increment tasks → consistent version across all variants in
  mixed debug + release invocations.
- Verified with AGP 9.4.1 in a scratch project: no flavors, 2 flavor dimensions, switching both ways.
- Added 4 functional tests (30 total, all passing). Updated CHANGELOG.md and README.md.

## Previous prompt (2026-07-19)

> it's not working when user is using productFlavors, fix all the issues

### Status: done

- Rewrote task matching in `VersionFrauPlugin` to parse lifecycle task names with a regex
  (`assemble|bundle` + flavor part + `Debug|Release`), extracting the flavor.
- Output renaming is now scoped to the exact variant output directory
  (`outputs/apk/<flavor>/<buildType>/`, `outputs/bundle/<flavorBuildType>/`) instead of
  renaming the single most-recently-modified file under `outputs/`.
- Renamed files keep AGP's base name (`app-free-debug-v1.2.3.4.apk`) so flavor identity and
  ABI-split names are preserved; already-versioned files are skipped; empty dirs no longer NPE.
- Aggregate tasks (`assembleDebug` on a flavored project) skip renaming; per-flavor tasks
  each handle their own outputs. Version increments still run once per build.
- Added 5 flavored functional tests to `AndroidFunctionalTest`.
- Updated CHANGELOG.md and README.md.

## Backlog / known follow-ups

- CHANGELOG 1.0.2 claims a `versionCode` formula of `major*1_000_000 + minor*10_000 + patch*100 + build`,
  but the code (and tests) deliberately use `major*1_000_000_000 + minor*1_000_000 + patch*1_000 + build`
  (commit "1000 not 100"). Note: this overflows `Int` for `major >= 3`. Left untouched on purpose.
- `assembleFree` / `assemble` (no build-type suffix) don't trigger increments — ambiguous build type.
- Flavor or build-type names that themselves end in "Debug"/"Release" would confuse task-name parsing.
