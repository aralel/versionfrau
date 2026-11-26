# VersionFrau website

Static one-page site that introduces the VersionFrau tools (Android, Xcode, React Native) and tells AI assistants how to install them.

| File | Purpose |
|---|---|
| `index.html` | Human-readable page (setup for Android, Xcode and React Native, version scheme, FAQ). Contains JSON-LD (`SoftwareApplication` ×3) for search engines |
| `llms.txt` | Short summary in the [llms.txt](https://llmstxt.org) format, linked from `<head>` |
| `llms-full.txt` | Complete plain-text install instructions — the file to hand to an AI agent |
| `robots.txt`, `sitemap.xml` | Crawling / discovery |
| `impressum.html` | Legal notice (required in Germany) — **fill in the TODO fields before going live** |

No build step, no dependencies, no external fonts/scripts/cookies (GDPR-friendly).

## Using it with an AI assistant

> Read https://versionfrau.aralel.com/llms-full.txt and set up VersionFrau in this project.

## Domain

The site assumes `https://versionfrau.aralel.com/`. To use another domain, replace it everywhere:

```bash
grep -rl 'versionfrau.aralel.com' . | xargs sed -i '' 's#versionfrau.aralel.com#your.domain#g'
```

## Deployment

GitHub Pages from the `/docs` folder of `aralel/versionfrau`. See `DEPLOYMENT.md`.

## Keeping it current

When a new plugin version is released, update the version numbers (`0.0.18` Android, `0.0.19` Xcode, `1.0.0` React Native) in
`index.html` (text + JSON-LD), `llms.txt` and `llms-full.txt`, and the `lastmod` dates in `sitemap.xml`.
