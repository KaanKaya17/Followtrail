import { _electron as electron } from "playwright-core";
import { writeFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import assert from "node:assert/strict";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const output = path.join(root, "artifacts/ui-0.1.30");
await mkdir(output, { recursive: true });
const environment = { ...process.env };
delete environment.ELECTRON_RUN_AS_NODE;
const desktop = await electron.launch({
  executablePath: path.join(
    root,
    "artifacts/Tracker-Desktop-UI/Followtrail.exe",
  ),
  env: environment,
});
let passed = false;
const keepOpen = process.argv.includes("--keep-open");
const page = await desktop.firstWindow();
try {
  await page.getByRole("heading", { name: "Çevrende neler oluyor?" }).waitFor();
  const preferences = await desktop.evaluate(({ BrowserWindow }) => {
    const webContents = BrowserWindow.getAllWindows()[0].webContents;
    const value = webContents.getLastWebPreferences();
    return {
      nodeIntegration: value.nodeIntegration,
      contextIsolation: value.contextIsolation,
      sandbox: value.sandbox,
      webSecurity: value.webSecurity,
    };
  });
  assert.equal(preferences.nodeIntegration, false);
  assert.equal(preferences.contextIsolation, true);
  assert.equal(preferences.sandbox, true);
  assert.equal(preferences.webSecurity, true);
  assert.equal(await page.evaluate(() => typeof require), "undefined");
  assert.equal(await page.evaluate(() => typeof process), "undefined");
  await page.locator('.sidebar a[href="#people"]').click();
  await page.locator("#people-search").fill("Deniz");
  assert.equal(await page.locator(".person-card").count(), 1);
  await page
    .getByRole("button", { name: "Deniz Çelik kaydını aç", exact: true })
    .click();
  assert.equal(
    await page
      .getByRole("dialog")
      .getByRole("heading", { name: "Deniz Çelik", exact: true })
      .isVisible(),
    true,
  );
  await page.getByRole("button", { name: "Kapat", exact: true }).click();
  await page.locator('.sidebar nav a[href="#overview"]').click();
  await page.screenshot({
    path: path.join(output, "desktop-native-overview.png"),
    fullPage: true,
  });
  await writeFile(
    path.join(output, "desktop-checks.json"),
    JSON.stringify(
      {
        executable: "artifacts/Tracker-Desktop-UI/Followtrail.exe",
        preferences,
        rendererNodeApi: false,
        localSearch: true,
        profileDrawer: true,
        scope: "Actual packaged Windows Electron preview, synthetic UI only.",
      },
      null,
      2,
    ),
  );
  passed = true;
  console.log(
    `Packaged Windows preview passed.${keepOpen ? " The desktop window is open for review." : ""}`,
  );
} finally {
  if (!passed || !keepOpen) await desktop.close();
}
if (passed && keepOpen) {
  desktop.on("close", () => process.exit(0));
  process.on("SIGINT", async () => {
    await desktop.close();
    process.exit(0);
  });
}
