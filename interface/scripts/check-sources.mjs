import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
for (const file of [
  "web/app.js",
  "web/usage-notes.js",
  "web/profile-search.js",
  "web/list-photos.js",
  "web/appearance.js",
  "web/desktop.js",
  "web/wayback.js",
  "web/command-palette.js",
  "web/operation-progress.js",
  "web/icons.js",
  "web/i18n.js",
  "locales/runtime.js",
  "scripts/generate-i18n.mjs",
  "scripts/check-i18n-web.mjs",
  "scripts/check-profile-search.mjs",
  "desktop/main.cjs",
  "desktop/theme.cjs",
  "desktop/preview.cjs",
  "desktop/preload.cjs",
  "desktop/service.cjs",
  "desktop/notifications.cjs",
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
  "scripts/check-navigation-desktop.mjs",
  "scripts/check-progress-desktop.mjs",
  "scripts/check-appearance.mjs",
  "scripts/check-accounts-desktop.mjs",
]) {
  execFileSync(process.execPath, ["--check", path.join(root, file)], {
    stdio: "inherit",
  });
}
console.log("All interface and desktop JavaScript sources parse.");
