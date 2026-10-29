import { chromium } from "playwright-core";
import { mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import assert from "node:assert/strict";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const output = path.join(root, "artifacts/ui-0.1.30");
await mkdir(output, { recursive: true });
const browser = await chromium.launch({
  executablePath:
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  headless: true,
});
const errors = [],
  requests = [],
  checks = [];
const check = (name, condition) => {
  assert.ok(condition, name);
  checks.push(name);
};
try {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1050 },
    reducedMotion: "reduce",
  });
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("request", (request) => requests.push(request.url()));
  await page.goto("http://127.0.0.1:4173/", { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "Çevrende neler oluyor?" }).waitFor();
  await page.screenshot({
    path: path.join(output, "web-overview-desktop.png"),
    fullPage: true,
  });
  check(
    "desktop overview has no horizontal overflow",
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page.getByRole("button", { name: "30 gün", exact: true }).click();
  check(
    "30-day chart exposes actual sample coverage",
    (await page.locator("#chart-caption").innerText()).includes(
      "12 örnek gözlem",
    ),
  );
  await page.getByRole("button", { name: "7 gün", exact: true }).click();
  check(
    "7-day chart narrows sample coverage",
    (await page.locator("#chart-caption").innerText()).includes(
      "7 örnek gözlem",
    ),
  );
  await page
    .getByRole("button", { name: "Şimdi kontrol et", exact: true })
    .click();
  check(
    "refresh explicitly reports no live request",
    await page
      .getByRole("heading", { name: "Canlı yenileme henüz etkin değil." })
      .isVisible(),
  );
  await page
    .getByRole("button", { name: "Örnek kayıtları incele", exact: true })
    .click();
  await page.locator('.sidebar a[href="#people"]').click();
  await page.locator("#people-search").fill("Ece");
  check(
    "people search filters local sample cards",
    (await page.locator(".person-card").count()) === 1,
  );
  await page
    .getByRole("button", { name: "Ece Arslan favorilere ekle", exact: true })
    .click();
  await page.locator("#people-search").fill("");
  await page.getByRole("button", { name: /^Favoriler/ }).click();
  check(
    "favorite state is shared with filters",
    (await page.locator(".person-card").count()) === 3,
  );
  await page
    .getByRole("button", { name: "Ece Arslan kaydını aç", exact: true })
    .click();
  await page.locator("#person-note").fill("Örnek <b>not</b> & özel işaret");
  await page.getByRole("button", { name: "Notu kaydet", exact: true }).click();
  await page.getByRole("button", { name: "Kapat", exact: true }).click();
  await page
    .getByRole("button", { name: "Ece Arslan kaydını aç", exact: true })
    .click();
  check(
    "sample notes roundtrip as plain text",
    (await page.locator("#person-note").inputValue()) ===
      "Örnek <b>not</b> & özel işaret",
  );
  await page.getByRole("button", { name: "Kapat", exact: true }).click();
  await page
    .getByRole("button", { name: "Örnek kişi ekle", exact: true })
    .click();
  await page.locator("#sample-name").fill("İpek Kaya");
  await page.locator("#sample-username").fill("ipek.demo");
  await page
    .getByRole("button", { name: "Örnek kartı ekle", exact: true })
    .click();
  check(
    "sample person form creates a card without an API",
    (await page.locator(".person-card").count()) === 9,
  );
  await page.screenshot({
    path: path.join(output, "web-people-desktop.png"),
    fullPage: true,
  });
  await page.locator('.sidebar a[href="#lists"]').click();
  check(
    "follower sample is present",
    (await page.locator(".list-row").count()) === 8,
  );
  await page.getByRole("button", { name: /^Takip edilen/ }).click();
  check(
    "following tab uses its own sample list",
    (await page.locator(".list-row").count()) === 6,
  );
  await page
    .getByRole("button", { name: "Kontrolleri karşılaştır", exact: true })
    .click();
  check(
    "comparison follows selected list direction",
    (await page
      .locator(".comparison-stat")
      .first()
      .locator("strong")
      .innerText()) === "+1",
  );
  await page.locator("#list-search").fill("ada");
  check(
    "list search does not change source coverage",
    (await page.locator(".list-row").count()) === 1,
  );
  await page.locator("#list-search").fill("");
  await page.screenshot({
    path: path.join(output, "web-lists-desktop.png"),
    fullPage: true,
  });
  await page.locator('.sidebar a[href="#timeline"]').click();
  await page
    .getByRole("button", { name: "Liste karşılaştırmaları", exact: true })
    .click();
  check(
    "timeline filters event kind",
    (await page.locator(".timeline-events .event-row").count()) === 1,
  );
  await page.locator(".timeline-events .event-row").click();
  check(
    "event detail shows comparison scope",
    await page
      .locator("dialog")
      .innerText()
      .then((text) => text.includes("kesin takipten çıkış bildirimi değildir")),
  );
  await page.getByRole("button", { name: "Anladım", exact: true }).click();
  await page
    .getByRole("button", { name: "Tüm kontroller", exact: true })
    .click();
  await page.screenshot({
    path: path.join(output, "web-timeline-desktop.png"),
    fullPage: true,
  });
  await page.locator('.sidebar a[href="#settings"]').click();
  await page
    .getByRole("switch", { name: "Kompakt görünüm", exact: true })
    .click();
  check(
    "density switch updates layout preference",
    (await page.locator("body").getAttribute("data-density")) === "compact",
  );
  await page.reload({ waitUntil: "networkidle" });
  check(
    "only appearance preference survives reload",
    (await page.locator("body").getAttribute("data-density")) === "compact",
  );
  await page
    .getByRole("switch", { name: "Kompakt görünüm", exact: true })
    .click();
  await page.screenshot({
    path: path.join(output, "web-settings-desktop.png"),
    fullPage: true,
  });
  check("desktop console is clean", errors.length === 0);
  check(
    "no external data or asset request",
    requests.every((url) => url.startsWith("http://127.0.0.1:4173/")),
  );
  const mobile = await browser.newPage({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
    deviceScaleFactor: 1,
    reducedMotion: "reduce",
  });
  mobile.on("pageerror", (error) => errors.push(error.message));
  await mobile.goto("http://127.0.0.1:4173/", { waitUntil: "networkidle" });
  for (const route of ["overview", "people", "lists", "timeline", "settings"]) {
    await mobile.locator(`.mobile-nav a[href="#${route}"]`).click();
    await mobile.locator("main h1").waitFor();
    check(
      `390px ${route} has no horizontal overflow`,
      await mobile.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    await mobile.screenshot({
      path: path.join(output, `web-${route}-mobile.png`),
      fullPage: true,
    });
  }
  await mobile.locator('.mobile-nav a[href="#people"]').click();
  await mobile
    .getByRole("button", { name: "Deniz Çelik kaydını aç", exact: true })
    .click();
  check(
    "mobile detail drawer stays inside viewport",
    await mobile
      .locator("dialog")
      .evaluate((node) => node.getBoundingClientRect().width <= innerWidth),
  );
  await mobile.screenshot({
    path: path.join(output, "web-profile-mobile.png"),
  });
  await mobile.keyboard.press("Escape");
  check(
    "escape dismisses modal without navigation",
    (await mobile.locator("dialog[open]").count()) === 0,
  );
  check("no browser script errors", errors.length === 0);
  const summary = {
    checks,
    errors,
    externalRequests: requests.filter(
      (url) => !url.startsWith("http://127.0.0.1:4173/"),
    ),
    browser: "Isolated headless Microsoft Edge",
    viewports: ["1440x1050", "390x844"],
    scope: "Synthetic UI interactions only; no Instagram login or data API.",
  };
  await writeFile(
    path.join(output, "browser-checks.json"),
    JSON.stringify(summary, null, 2),
  );
  console.log(
    JSON.stringify({ passed: checks.length, errors, output }, null, 2),
  );
} finally {
  await browser.close();
}
