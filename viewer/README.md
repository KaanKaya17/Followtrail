# Followtrail web viewer

A single static page that opens an Instagram data export (ZIP) in the browser, without installing anything and without uploading the file.

- **No network.** The page's Content-Security-Policy is `connect-src 'none'`; it cannot send the file or anything else to a server. `test/browser.check.mjs` proves it in a real Chrome, including a request the page itself attempts.
- **No dependencies.** `zip.js` reads the ZIP central directory from slices of the file and inflates members with the browser's `DecompressionStream` (stored, deflate, ZIP64, CRC-checked).
- **Same results as the desktop app.** `relationships.js` is a port of `interface/desktop/backend/instagram_archive.py` and the comparison in `export_browser.py`. `test/parity.test.mjs` reads seeded random exports with both and requires identical output; change both readers together.

Features: followers and following with dates, who doesn't follow back, mutuals, close friends and the other relationship lists, search, date order, CSV, and a comparison of two exports (new, lost, probable renames). Turkish and English.

## Run and test

Serve the folder with any static server (ES modules do not load from `file://`):

```sh
npx --yes http-server viewer -p 8080
```

```sh
PYTHON=python node --test "viewer/test/*.test.mjs"
CHROME_PATH=/path/to/chrome node viewer/test/browser.check.mjs [screenshot-dir]
```

`PARITY_CASES` sets the number of random exports (default 120), `PARITY_SEED` reproduces a failing one. The browser check needs `npm ci --prefix interface --ignore-scripts` for `playwright-core`.

## Publishing

`.github/workflows/pages.yml` publishes the page (without `test/`) to GitHub Pages on every change to `viewer/` on main, once Pages uses **GitHub Actions** as its source and the repository variable `PAGES_ENABLED` is `true`.
