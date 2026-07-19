# Plan

## Current prompt (2026-07-19)

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
