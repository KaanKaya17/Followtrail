import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
for (const file of [
  "web/app.js",
  "web/desktop.js",
  "web/icons.js",
  "desktop/main.cjs",
  "desktop/preview.cjs",
  "desktop/preload.cjs",
  "desktop/service.cjs",
  "desktop/notifications.cjs",
  "desktop/background.cjs",
  "scripts/preview.mjs",
  "scripts/package-desktop.mjs",
  "scripts/package-functional-desktop.mjs",
  "scripts/check-ui.mjs",
  "scripts/check-desktop.mjs",
  "scripts/check-functional-desktop.mjs",
]) {
  execFileSync(process.execPath, ["--check", path.join(root, file)], {
    stdio: "inherit",
  });
}
console.log("All interface and desktop JavaScript sources parse.");
