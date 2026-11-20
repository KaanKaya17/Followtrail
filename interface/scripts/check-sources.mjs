import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
for (const file of [
  "web/app.js",
  "web/usage-notes.js",
  "web/read-health.js",
  "web/request-quota.js",
  "web/export-browser.js",
  "web/archive-dashboard.js",
  "web/archive-insights.js",
  "web/profile-search.js",
  "web/list-photos.js",
  "web/list-pages.js",
  "web/history-pages.js",
  "web/dom-updates.js",
  "web/daily-chart.js",
  "web/stories.js",
  "web/profile-details.js",
  "web/profile-links.js",
  "web/link-icons.js",
  "web/appearance.js",
  "web/desktop.js",
  "web/wayback.js",
  "web/command-palette.js",
  "web/operation-progress.js",
  "web/icons.js",
  "web/i18n.js",
  "locales/runtime.js",
  "scripts/generate-i18n.mjs",
  "scripts/generate-search-policy.mjs",
  "scripts/check-i18n-web.mjs",
  "scripts/check-profile-search.mjs",
  "desktop/main.cjs",
  "desktop/theme.cjs",
  "desktop/preview.cjs",
  "desktop/preload.cjs",
  "desktop/service.cjs",
  "desktop/notifications.cjs",
  "desktop/read-alerts.cjs",
  "desktop/background.cjs",
  "desktop/renderer-updates.cjs",
  "scripts/profile-desktop.mjs",
  "scripts/preview.mjs",
  "scripts/package-desktop.mjs",
  "scripts/package-functional-desktop.mjs",
  "scripts/brand-windows.mjs",
  "scripts/check-branding-desktop.mjs",
  "scripts/check-ui.mjs",
  "scripts/check-desktop.mjs",
  "scripts/check-functional-desktop.mjs",
  "scripts/check-privacy-desktop.mjs",
  "scripts/check-list-pages-desktop.mjs",
  "scripts/local-list-fixture.mjs",
  "scripts/read-history.mjs",
  "scripts/wait-for-account.mjs",
  "scripts/check-daily-desktop.mjs",
  "scripts/check-navigation-desktop.mjs",
  "scripts/check-progress-desktop.mjs",
  "scripts/check-appearance.mjs",
  "scripts/check-accounts-desktop.mjs",
  "scripts/check-overview-desktop.mjs",
  "scripts/check-list-recovery-desktop.mjs",
  "scripts/check-membership-desktop.mjs",
  "scripts/check-read-alert-desktop.mjs",
  "scripts/capture-social-preview.mjs",
  "scripts/check-notifications-desktop.mjs",
  "scripts/check-timeline-desktop.mjs",
  "scripts/check-resource-desktop.mjs",
  "scripts/capture-readme-demo.mjs",
]) {
  execFileSync(process.execPath, ["--check", path.join(root, file)], {
    stdio: "inherit",
  });
}
console.log("All interface and desktop JavaScript sources parse.");

// These two files are being split into smaller modules. A limit is never raised:
// once a step of the split lands, lower it to the new size so the file cannot grow back.
const { readFileSync } = await import("node:fs");
for (const [file, limit] of [
  ["desktop/backend/engine.py", 1313],
  ["web/desktop.js", 2369],
]) {
  const lines = readFileSync(path.join(root, file), "utf8").split("\n").length - 1;
  if (lines > limit)
    throw new Error(`${file} has ${lines} lines; the limit is ${limit}. Move code out instead of adding it here.`);
}
console.log("Engine and desktop window files stay within their line limits.");
