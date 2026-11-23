# Changelog

## [Unreleased]

### Changed — 2026-11-23
- Domain is now `https://versionfrau.codehospital.com/` (canonical, Open Graph, llms files, robots, sitemap, README, DEPLOYMENT)
- Both plugin READMEs link to the website; Android README issues (wrong Xcode repo, version `1.0.0`, file location, versionCode examples) fixed in that repo

### Added — 2026-11-23
- Initial website for VersionFrau for Android (Gradle plugin 0.0.18) and VersionFrau for Xcode (0.0.19): `index.html` with setup steps, behaviour tables, shared version scheme, FAQ, light/dark theme, copy buttons, JSON-LD structured data
- `llms.txt` and `llms-full.txt` with exact, copy-paste instructions for AI assistants (project detection, setup, verification, troubleshooting)
- `robots.txt`, `sitemap.xml`, `impressum.html` (with TODO fields), `README.md`, `DEPLOYMENT.md`
- The version-code examples use the formula in the code (`major*1e9 + minor*1e6 + patch*1e3 + build`, e.g. `1000003042`), not the outdated `1000342` example in the Android README
