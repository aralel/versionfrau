# Changelog

## [Unreleased]

### Changed — 2026-11-26 (redesign)
- `index.html` redesigned with a softer, feminine developer look: blush / lavender palette with a rose-to-violet accent, rounded system font, pill navigation, gradient headline and buttons, pastel terminal-style code blocks (window dots, plum background), ✦ section markers and a hero terminal mock-up. Light and dark themes, still no external fonts, scripts or cookies; layout checked at 1200 px and 500 px width. Content, ids and links unchanged

### Added — 2026-11-26 (React Native)
- VersionFrau for React Native (`@aralel/react-native-versionfrau` 1.0.0, https://www.npmjs.com/package/@aralel/react-native-versionfrau): new `#react-native` section, nav link, hero button and card in `index.html`; third `SoftwareApplication` in the JSON-LD; title, description and keywords mention React Native
- `llms.txt` and `llms-full.txt`: React Native / Expo rule, REACT NATIVE section (install, `versionfrau-rn install`, Expo config plugin, JS API, behaviour, commands) and troubleshooting; project detection says React Native apps use only that section
- `sitemap.xml` lastmod 2026-11-26; README lists the React Native version to keep current

### Changed — 2026-11-23 (Maven Central)
- Android setup uses Maven Central instead of JitPack (`index.html`, `llms.txt`, `llms-full.txt`, troubleshooting); `resolutionStrategy` block removed

### Changed — 2026-11-23 (move)
- Site moved to `versionfrau-android/docs/` (GitHub Pages from `/docs` of `aralel/versionfrau`); domain is now `https://versionfrau.aralel.com/` again (replaces versionfrau.codehospital.com)
- Added `CNAME` (`versionfrau.aralel.com`) and `.nojekyll`; DEPLOYMENT.md describes Pages from `/docs`

### Changed — 2026-11-23
- Domain is now `https://versionfrau.codehospital.com/` (canonical, Open Graph, llms files, robots, sitemap, README, DEPLOYMENT)
- Both plugin READMEs link to the website; Android README issues (wrong Xcode repo, version `1.0.0`, file location, versionCode examples) fixed in that repo

### Added — 2026-11-23
- Initial website for VersionFrau for Android (Gradle plugin 0.0.18) and VersionFrau for Xcode (0.0.19): `index.html` with setup steps, behaviour tables, shared version scheme, FAQ, light/dark theme, copy buttons, JSON-LD structured data
- `llms.txt` and `llms-full.txt` with exact, copy-paste instructions for AI assistants (project detection, setup, verification, troubleshooting)
- `robots.txt`, `sitemap.xml`, `impressum.html` (with TODO fields), `README.md`, `DEPLOYMENT.md`
- The version-code examples use the formula in the code (`major*1e9 + minor*1e6 + patch*1e3 + build`, e.g. `1000003042`), not the outdated `1000342` example in the Android README
