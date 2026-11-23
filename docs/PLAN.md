# Plan

## Current prompt (2026-11-23, Maven Central)

> we are not using jitpack.io anymore, remove it from documentation, plugin is published in mavencentral now

### Status: done (no commits)

- Verified on repo1.maven.org: `com.aralel:versionfrau` 0.0.18 and the plugin marker `com.aralel.versionfrau.gradle.plugin` 0.0.18.
- README, website and llms files: only `mavenCentral()` in `pluginManagement`; JitPack and `resolutionStrategy` removed.

## Current prompt (2026-11-23, domain)

> i moved the website to docs un versionfrau-android, change the domain to versionfrau.aralel.com

### Status: done (no commits, not deployed)

- Domain `versionfrau.aralel.com` in the site, both READMEs, `docs/CNAME`; `.nojekyll` added; DEPLOYMENT.md: Pages from `main` `/docs`.

## Current prompt (2026-11-23, follow-up)

> fix the problems, domain will be versionfrau.codehospital.com

### Status: done (not deployed)

- Domain replaced everywhere; DNS: CNAME `versionfrau` in the codehospital.com zone.
- Android README fixed and both READMEs link to the site. Still open: Impressum details, hosting.

## Current prompt (2026-11-23)

> create a website introducing these two plugins so when i pass the website to ai, it knows what to do, also others can find them and use them

### Status: done (not deployed)

- Static site (now in `versionfrau-android/docs/`): `index.html` (humans + JSON-LD for search), `llms.txt` / `llms-full.txt` (AI agents).
- Assumed domain `https://versionfrau.codehospital.com/` (see README to change it).
- Facts taken from both repos' READMEs and source. Android: the version file defaults to the module folder
  (`project.file`), no write locking; Xcode writes are locked.
- Open: fill in `impressum.html`; choose hosting; the Android README still links to `aralel/versionfrau-xcode`
  (the repo is `aralel/versionfrau-ios`) and shows version `1.0.0` / versionCode `1000342`.
