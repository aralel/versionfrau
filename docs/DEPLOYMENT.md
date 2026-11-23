# Deployment

The site is plain static files in the `docs/` folder of `aralel/versionfrau` — no build step.

## GitHub Pages (from `/docs`)

1. Repository `aralel/versionfrau` → Settings → Pages → Source: *Deploy from a branch* → `main` / `/docs`.
2. Custom domain: `versionfrau.aralel.com` (already in `docs/CNAME`). Enable *Enforce HTTPS* once the certificate is issued.
3. DNS in the `aralel.com` zone: `CNAME` record `versionfrau` → `aralel.github.io`.

`docs/.nojekyll` turns off Jekyll, so `llms.txt`, `llms-full.txt` and the Markdown files are served as-is.

## Make it findable

- Submit `https://versionfrau.aralel.com/sitemap.xml` in Google Search Console and Bing Webmaster Tools.
- Set `https://versionfrau.aralel.com` as the "Website" of both GitHub repositories
  (`aralel/versionfrau`, `aralel/versionfrau-ios`) and add topics such as `gradle-plugin`, `android`,
  `xcode`, `versioning`, `swift-package`.
- Check that `/llms.txt` is served as `text/plain`.
