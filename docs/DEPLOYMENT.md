# Deployment

The site is plain static files — upload the folder as-is.

## GitHub Pages

1. Create a repository (e.g. `aralel/versionfrau-website`) and add these files at the root.
2. Settings → Pages → Deploy from branch → `main` / root.
3. For the custom domain add a `CNAME` file containing `versionfrau.codehospital.com` and a DNS `CNAME` record
   `versionfrau` → `aralel.github.io` (in the codehospital.com DNS zone).

## Netlify / Cloudflare Pages

Publish directory: this folder. Build command: none.

## Make it findable

- Submit `https://versionfrau.codehospital.com/sitemap.xml` in Google Search Console and Bing Webmaster Tools.
- Link the site from both repository READMEs and set it as the "Website" of both GitHub repositories
  (`aralel/versionfrau`, `aralel/versionfrau-ios`), and add GitHub topics such as `gradle-plugin`, `android`,
  `xcode`, `versioning`, `swift-package`.
- Check that `/llms.txt` is served as `text/plain`.
