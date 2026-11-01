import { _electron as electron } from "playwright-core";
import { mkdtemp, mkdir, readFile, writeFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import path from "node:path";
import os from "node:os";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const home = await mkdtemp(path.join(os.tmpdir(), "TrackerQA-"));
const version = JSON.parse(
  await readFile(path.join(root, "interface/package.json"), "utf8"),
).version;
const output = path.join(root, `artifacts/desktop-${version}-qa`);
await mkdir(output, { recursive: true });
const python =
  process.env.TRACKER_BUILD_PYTHON ||
  path.resolve(root, "../InstagramCounter/.venv/Scripts/python.exe");
execFileSync(
  python,
  [path.join(root, "interface/desktop/backend/seed_qa.py"), "--home", home],
  { stdio: "pipe" },
);
const env = { ...process.env };
delete env.ELECTRON_RUN_AS_NODE;
const executablePath = path.join(
  root,
  `artifacts/InstagramTracker-Desktop-${version}/InstagramTracker.exe`,
);
const checks = [],
  errors = [],
  requests = [];
let desktop, page;
async function launch(language = 'tr') {
  desktop = await electron.launch({
    executablePath,
    args: [`--tracker-test-home=${home}`],
    env,
  });
  page = await desktop.firstWindow();
  page.setDefaultTimeout(12000);
  page.on("pageerror", (e) => errors.push(e.message));
  page.on("request", (r) => {
    if (!r.url().startsWith("file:") && !r.url().startsWith("data:"))
      requests.push(r.url());
  });
  await page.getByRole("heading", { name: language === 'en' ? 'What is happening in your circle?' : "Çevrende neler oluyor?" }).waitFor();
}
async function check(name, work) {
  await work();
  checks.push(name);
  console.log("PASS " + name);
}
try {
  await launch();
  await check('Footer help contains disclosure and retention details without starting network work', async () => {
    const before = await page.evaluate(() => window.TrackerDesktop.command('state'));
    await page.locator('.page-footer [data-action="usage-notes"]').click();
    await page.getByRole('dialog').getByRole('heading', {name:'Kullanım notları',exact:true}).waitFor();
    assert.match(await page.getByRole('dialog').innerText(), /2\.000 olay/);
    assert.match(await page.getByRole('dialog').innerText(), /Değişikliğin kesin zamanı/);
    await page.screenshot({path:path.join(output,'usage-notes-tr.png')});
    await page.keyboard.press('Escape');
    assert.equal(await page.getByRole('dialog').count(), 0);
    const after = await page.evaluate(() => window.TrackerDesktop.command('state'));
    assert.deepEqual(after.gate, before.gate);
    assert.deepEqual(after.account, before.account);
    await page.evaluate(() => window.scrollTo(0,0));
    await page.screenshot({path:path.join(output,'overview-tr.png'),fullPage:true});
    await page.locator('.sidebar nav a[href="#timeline"]').click();
    assert.equal(await page.locator('.timeline-explainer').count(), 0);
    await page.screenshot({path:path.join(output,'timeline-tr.png'),fullPage:true});
    await page.locator('.sidebar nav a[href="#overview"]').click();
  });
  await check(
    "Closing the actual window keeps the renderer and local worker alive in the tray",
    async () => {
      const before = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      await desktop.evaluate(({ BrowserWindow }) =>
        BrowserWindow.getAllWindows()[0].close(),
      );
      assert.equal(
        await desktop.evaluate(
          ({ BrowserWindow }) => BrowserWindow.getAllWindows().length,
        ),
        1,
      );
      assert.equal(
        await desktop.evaluate(({ BrowserWindow }) =>
          BrowserWindow.getAllWindows()[0].isVisible(),
        ),
        false,
      );
      const hidden = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(hidden.ok, true);
      assert.equal(hidden.state.account.id, before.state.account.id);
      await desktop.evaluate(({ app }) => app.emit("activate"));
      assert.equal(
        await desktop.evaluate(({ BrowserWindow }) =>
          BrowserWindow.getAllWindows()[0].isVisible(),
        ),
        true,
      );
    },
  );
  await check("Hidden and minimized windows skip updates and resume the latest state once", async () => {
    await page.evaluate(() => {
      window.qaDOMUpdates = 0;
      window.qaResumeCount = 0;
      window.qaObserver = new MutationObserver(records => { window.qaDOMUpdates += records.length; });
      window.qaObserver.observe(document.getElementById('app'), { childList: true, subtree: true });
      window.qaUnsubscribe = window.TrackerDesktop.subscribe(event => { if (event.type === 'resume') window.qaResumeCount++; });
    });
    for (const mode of ['hide', 'minimize']) {
      await page.evaluate(() => { window.qaDOMUpdates = 0; window.qaResumeCount = 0; });
      await desktop.evaluate(({ BrowserWindow }, mode) => BrowserWindow.getAllWindows()[0][mode](), mode);
      for (const compact of [false, true, false, true]) {
        const result = await page.evaluate(compact => window.TrackerDesktop.command('settings', { compact }), compact);
        assert.equal(result.ok, true);
      }
      assert.equal(await page.evaluate(() => window.qaDOMUpdates), 0);
      await desktop.evaluate(({ app }) => app.emit('activate'));
      await page.waitForFunction(() => document.body.dataset.density === 'compact');
      assert.equal(await page.evaluate(() => window.qaResumeCount), 1);
      await page.evaluate(() => window.TrackerDesktop.command('settings', { compact: false }));
      await page.waitForFunction(() => document.body.dataset.density === 'comfortable');
    }
    await page.evaluate(() => { window.qaObserver.disconnect(); window.qaUnsubscribe(); });
  });
  await check("Resuming a code wait or failed service restores actionable UI", async () => {
    const state = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    await desktop.evaluate(({ BrowserWindow }, state) => BrowserWindow.getAllWindows()[0].webContents.send('tracker:event', { type: 'resume', state, busy: true, progress: { phase: 'code' }, error: null }), state);
    await page.locator('#code-form').waitFor();
    await desktop.evaluate(({ BrowserWindow }, state) => BrowserWindow.getAllWindows()[0].webContents.send('tracker:event', { type: 'resume', state, busy: false, progress: null, error: { message: 'Synthetic service failure' } }), state);
    await page.waitForFunction(() => !document.querySelector('#code-form')?.closest('dialog')?.open);
    await page.getByText('Synthetic service failure', { exact: true }).waitFor();
    await desktop.evaluate(({ BrowserWindow }, state) => BrowserWindow.getAllWindows()[0].webContents.send('tracker:event', { type: 'resume', state, busy: false, progress: null, error: null }), state);
  });
  await check("Renderer isolation and no Node access", async () => {
    const prefs = await desktop.evaluate(({ BrowserWindow }) => {
      const p =
        BrowserWindow.getAllWindows()[0].webContents.getLastWebPreferences();
      return {
        nodeIntegration: p.nodeIntegration,
        sandbox: p.sandbox,
        contextIsolation: p.contextIsolation,
        webSecurity: p.webSecurity,
      };
    });
    assert.deepEqual(prefs, {
      nodeIntegration: false,
      sandbox: true,
      contextIsolation: true,
      webSecurity: true,
    });
    assert.equal(await page.evaluate(() => typeof require), "undefined");
    assert.equal(await page.evaluate(() => typeof process), "undefined");
  });
  await check("Secrets absent from renderer state", async () => {
    const state = await page.evaluate(() =>
      window.TrackerDesktop.command("state"),
    );
    assert.equal(state.state.connected, false);
    assert.equal("session" in state.state, false);
    assert.equal("cookies" in state.state, false);
  });
  await check("Unknown IPC operation and file path rejected", async () => {
    const result = await page.evaluate(async () => {
      try {
        await window.TrackerDesktop.command("export_backup", {
          path: "invalid",
        });
        return false;
      } catch {
        return true;
      }
    });
    assert.equal(result, true);
  });
  await check("Subframe cannot invoke privileged IPC", async () => {
    await page.evaluate(() => {
      const f = document.createElement("iframe");
      f.src = location.href;
      f.id = "ipc-test-frame";
      document.body.append(f);
    });
    const frame = page.frameLocator("#ipc-test-frame");
    await frame.locator("#app").waitFor();
    const inner = page.frames().find((f) => f !== page.mainFrame());
    assert.equal(
      await inner.evaluate(async () => {
        if (!window.TrackerDesktop) return true;
        try {
          await window.TrackerDesktop.command("state");
          return false;
        } catch {
          return true;
        }
      }),
      true,
    );
    await page.locator("#ipc-test-frame").evaluate((n) => n.remove());
  });
  await check(
    "Lists show partial coverage without replacing completed snapshot",
    async () => {
      await page.locator('.sidebar a[href="#lists"]').click();
      await page
        .getByText("Tamamlanan kayıt: 3 hesap", { exact: true })
        .waitFor();
      assert.equal(
        await page
          .getByText("Liste henüz tamamlanmadı · 1 hesap kaydedildi", { exact: true })
          .isVisible(),
        true,
      );
      assert.equal(await page.locator("#list-rows .list-row").count(), 3);
      assert.equal(
        await page
          .getByRole("button", { name: "Listeyi yeniden kontrol et", exact: true })
          .isDisabled(),
        true,
      );
    },
  );
  await check(
    "Snapshot comparison uses identities and names both directions",
    async () => {
      await page
        .getByRole("button", { name: "Farkları göster", exact: true })
        .click();
      assert.equal(
        await page
          .locator(".compare-stat")
          .first()
          .locator("strong")
          .textContent(),
        "1",
      );
      assert.equal(
        await page
          .locator(".compare-stat")
          .nth(1)
          .locator("strong")
          .textContent(),
        "1",
      );
      assert.equal(await page.locator("#list-rows .list-row").count(), 2);
      assert.equal(
        await page
          .locator("#list-rows")
          .getByText("Son kayıtta yok", { exact: true })
          .count(),
        1,
      );
      await page.screenshot({
        path: path.join(output, "comparison.png"),
        fullPage: true,
      });
      await page
        .getByRole("button", { name: "Listeye dön", exact: true })
        .click();
    },
  );
  await check(
    "Two-direction relationship filters and local search",
    async () => {
      await page.locator("#relation-filter").selectOption("mutual");
      assert.equal(await page.locator("#list-rows .list-row").count(), 1);
      await page.locator("#relation-filter").selectOption("oneway");
      assert.equal(await page.locator("#list-rows .list-row").count(), 2);
      await page.locator("#relation-filter").selectOption("all");
      await page.locator("#list-search").fill("qa.203");
      assert.equal(await page.locator("#list-rows .list-row").count(), 1);
    },
  );
  await check(
    "List pagination follows search results after clearing a narrow query",
    async () => {
      await page.locator("#list-owner").selectOption("300");
      await page.locator("#list-search").fill("");
      assert.equal(await page.locator("#list-rows .list-row").count(), 100);
      await page.locator("#list-search").fill("qa.301");
      assert.equal(await page.locator("#list-rows .list-row").count(), 1);
      assert.equal(
        await page
          .getByRole("button", { name: "Daha fazla göster", exact: true })
          .count(),
        0,
      );
      await page.locator("#list-search").fill("");
      await page
        .getByRole("button", { name: "Daha fazla göster", exact: true })
        .click();
      assert.equal(await page.locator("#list-rows .list-row").count(), 150);
      await page.locator("#list-owner").selectOption("100");
    },
  );
  await check("Persistent notes and group; text is escaped", async () => {
    await page.locator('.sidebar a[href="#people"]').click();
    await page
      .getByRole("button", { name: "QA qa.deniz kaydını aç", exact: true })
      .click();
    await page.getByRole("dialog").locator(".avatar img").waitFor();
    await page.waitForFunction(
      () => document.querySelector("dialog .avatar img")?.naturalWidth > 0,
    );
    assert.equal(
      await page.locator("#person-note").inputValue(),
      "PRIVATE-QA-NOTE <img src=x onerror=alert(1)>",
    );
    assert.equal(await page.getByRole("dialog").locator("img").count(), 1);
    await page
      .locator("#person-note")
      .fill("Saved QA note <script>window.hacked=true</script>");
    await page.locator("#person-group").fill("QA saved group");
    await page
      .getByRole("button", { name: "Notu ve grubu kaydet", exact: true })
      .click();
    await page
      .getByText("Not ve grup şifreli olarak kaydedildi.", { exact: true })
      .waitFor();
    await page.getByRole("button", { name: "Kapat", exact: true }).click();
  });
  await check("Favorite toggle persists", async () => {
    await page
      .getByRole("button", {
        name: "qa.deniz favori durumunu değiştir",
        exact: true,
      })
      .click();
    await page
      .getByText("Favori tercihi kaydedildi.", { exact: true })
      .waitFor();
    await page.getByRole("button", { name: "Favoriler", exact: true }).click();
    assert.equal(await page.locator(".person-card").count(), 0);
  });
  await check("Compact preference saved in encrypted store", async () => {
    await page.locator('.sidebar a[href="#settings"]').click();
    await page
      .getByRole("switch", { name: "Kompakt görünüm", exact: true })
      .click();
    await page.waitForFunction(
      () => document.body.dataset.density === "compact",
    );
  });
  await check(
    "One- and five-minute intervals persist through the real IPC and encrypted vault",
    async () => {
      const options = await page
        .locator("#auto-interval option")
        .evaluateAll((nodes) => nodes.map((n) => n.value));
      assert.deepEqual(options, ["1", "5", "15", "30", "60", "180", "360"]);
      for (const value of ["1", "5"]) {
        await page.locator("#auto-interval").selectOption(value);
        await page.waitForFunction(
          async (value) =>
            (await window.TrackerDesktop.command("state")).state.settings
              .interval === Number(value),
          value,
        );
        // A persisted worker result precedes the renderer's IPC completion.
        // Observe the public UI before selecting the next value.
        await page.waitForFunction(
          (value) => document.querySelector('.monitor-summary')?.textContent.includes(`Her profil kendi son kontrolünden yaklaşık ${value} dakika`) && !document.querySelector('#auto-interval')?.disabled,
          value,
        );
      }
      assert.equal(await page.locator(".monitor-summary").isVisible(), true);
      assert.match(await page.locator(".monitor-summary").innerText(), /Her profil kendi son kontrolünden yaklaşık 5 dakika/);
      const monitor = (await page.evaluate(() => window.TrackerDesktop.command("state"))).state.monitor;
      assert.equal(monitor.intervalSeconds, 300);
      assert.equal(monitor.estimatedCycleSeconds, Math.max(300, monitor.profileCount * 15));
    },
  );
  await check(
    "Notification opt-in creates an owned isolated Windows shortcut",
    async () => {
      await page
        .getByRole("switch", { name: "Windows bildirimleri", exact: true })
        .click();
      await page.waitForFunction(
        async () =>
          (await window.TrackerDesktop.command("state")).state.settings
            .notifications,
      );
      const details = await desktop.evaluate(
        ({ shell }, qaHome) =>
          shell.readShortcutLink(
            qaHome + "/shortcuts/InstagramTracker Desktop.lnk",
          ),
        home,
      );
      assert.equal(
        details.appUserModelId,
        "dev.kaankaya.instagramtracker.desktop",
      );
      assert.equal(
        details.toastActivatorClsid.toUpperCase(),
        "{D3085DD3-667D-4514-BD5F-1C8C0C8A5927}",
      );
      await page
        .getByRole("switch", { name: "Windows bildirimleri", exact: true })
        .click();
      await page.waitForFunction(
        async () =>
          !(await window.TrackerDesktop.command("state")).state.settings
            .notifications,
      );
    },
  );
  await check(
    "Encrypted export and CSV through native file boundary",
    async () => {
      await desktop.evaluate(({ dialog }, qaHome) => {
        dialog.showSaveDialog = async (_window, options) => ({
          canceled: false,
          filePath: qaHome + "/" + options.defaultPath,
        });
      }, home);
      await page
        .getByRole("button", { name: "Şifreli yedek al", exact: true })
        .click();
      await page
        .getByText("Dosya işlemi tamamlandı.", { exact: true })
        .waitFor();
      const backup = await readFile(
        path.join(home, "InstagramTracker.trackerbackup"),
      );
      assert.equal(backup.includes(Buffer.from("Saved QA note")), false);
      await page.locator('.sidebar a[href="#lists"]').click();
      await page
        .locator("#toast")
        .evaluate((n) => n.classList.remove("visible"));
      await page
        .getByRole("button", { name: "CSV dışa aktar", exact: true })
        .click();
      await page
        .locator("#toast.visible")
        .getByText("Dosya işlemi tamamlandı.", { exact: true })
        .waitFor();
      // Native save writes asynchronously; wait through the invocation boundary.
      await page.waitForFunction(
        async () => !(await window.TrackerDesktop.command("state")).busy,
      );
      const csv = await readFile(
        path.join(home, "InstagramTracker-liste.csv"),
        "utf8",
      );
      assert.equal(csv.includes("qa.203"), true);
      assert.equal(csv.includes("session"), false);
    },
  );
  await check(
    "Restart restores encrypted notes, favorites and preferences",
    async () => {
      await desktop.close();
      desktop = null;
      await launch();
      await page.locator('.sidebar a[href="#people"]').click();
      await page
        .getByRole("button", { name: "QA qa.deniz kaydını aç", exact: true })
        .click();
      assert.equal(
        await page.locator("#person-note").inputValue(),
        "Saved QA note <script>window.hacked=true</script>",
      );
      assert.equal(
        await page.locator("#person-group").inputValue(),
        "QA saved group",
      );
      assert.equal(await page.evaluate(() => window.hacked), undefined);
      assert.equal(
        await page.evaluate(() => document.body.dataset.density),
        "compact",
      );
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
      const raw = await readFile(path.join(home, "tracker.vault"));
      assert.equal(raw.includes(Buffer.from("Saved QA note")), false);
    },
  );
  await check(
    "Encrypted restore through the native confirmation boundary",
    async () => {
      await desktop.evaluate(({ dialog }, qaHome) => {
        dialog.showOpenDialog = async () => ({
          canceled: false,
          filePaths: [qaHome + "/InstagramTracker.trackerbackup"],
        });
        dialog.showMessageBox = async () => ({ response: 1 });
      }, home);
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page
        .getByRole("button", { name: "Yedekten yükle", exact: true })
        .click();
      await page
        .locator("#toast.visible")
        .getByText("Dosya işlemi tamamlandı.", { exact: true })
        .waitFor();
      const state = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(state.state.connected, false);
      assert.equal(
        state.state.people.find((p) => p.id === "200").note,
        "Saved QA note <script>window.hacked=true</script>",
      );
      assert.equal(state.state.gate.status, 429);
    },
  );
  await check("Timeline filters and value details", async () => {
    await page.locator('.sidebar a[href="#timeline"]').click();
    await page
      .getByRole("button", { name: "Profil farkları", exact: true })
      .click();
    assert.equal(await page.locator(".timeline-events .event-row").count(), 4);
    await page.locator(".timeline-events .event-row").first().click();
    assert.equal(
      await page.getByRole("dialog").locator(".change-values").isVisible(),
      true,
    );
    await page.getByRole("button", { name: "Kapat", exact: true }).click();
  });
  await check(
    "Account-specific type preferences save without changing other accounts",
    async () => {
      await page.locator('.sidebar nav a[href="#people"]').click();
      await page.getByRole("button", { name: "Tümü", exact: true }).click();
      await page
        .getByRole("button", { name: "QA qa.deniz kaydını aç", exact: true })
        .click();
      await page
        .getByRole("button", { name: "Bildirim tercihleri", exact: true })
        .click();
      const form = page.locator("#notification-rule-form");
      assert.equal(
        await form.getByLabel("Takipçi sayısı", { exact: true }).isChecked(),
        false,
      );
      assert.equal(
        await form.getByLabel("Kullanıcı adı", { exact: true }).isChecked(),
        true,
      );
      await form.getByLabel("Takip edilen sayısı", { exact: true }).uncheck();
      await form
        .getByRole("button", { name: "Tercihleri kaydet", exact: true })
        .click();
      await page
        .getByText("Bildirim tercihleri kaydedildi.", { exact: true })
        .waitFor();
      const state = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(
        state.state.notificationRules["200"].fields.followers,
        false,
      );
      assert.equal(
        state.state.notificationRules["200"].fields.following,
        false,
      );
      assert.equal(state.state.notificationRules["100"], undefined);
    },
  );
  await check(
    "One notification opens an escaped old/new log including muted changes and marks it read",
    async () => {
      await page
        .getByRole("button", { name: "Bildirim kayıtları", exact: true })
        .click();
      await page
        .getByRole("heading", {
          name: "Bir kontrolde neler değişti?",
          exact: true,
        })
        .waitFor();
      const row = page.locator(
        '.notification-log-row[data-id="qa-multi-change"]',
      );
      assert.equal(await row.count(), 1);
      assert.equal(
        await row
          .getByText("@qa.deniz · 3 değişiklik", { exact: true })
          .isVisible(),
        true,
      );
      // Exercise the exact main-to-renderer event used by the native toast
      // click callback, without claiming Windows delivered a real toast.
      await desktop.evaluate(
        ({ BrowserWindow }, id) =>
          BrowserWindow.getAllWindows()[0].webContents.send("tracker:event", {
            type: "open_notification",
            id,
          }),
        "qa-multi-change",
      );
      await page
        .getByRole("dialog")
        .getByRole("heading", { name: "@qa.deniz · 3 değişiklik", exact: true })
        .waitFor();
      assert.equal(await page.locator(".change-log-details > li").count(), 3);
      const pairWidths = await page
        .locator(".change-log-details > li")
        .first()
        .locator(".change-values > div")
        .evaluateAll((nodes) =>
          nodes.map((node) => node.getBoundingClientRect().width),
        );
      assert.equal(pairWidths.length, 2);
      assert.equal(Math.abs(pairWidths[0] - pairWidths[1]) < 2, true);
      assert.equal(pairWidths[1] > 150, true);
      assert.equal(
        await page
          .locator(".change-log-details")
          .getByText("Yalnız kayıtta", { exact: true })
          .count(),
        1,
      );
      assert.equal(
        await page
          .locator(".change-log-details")
          .getByText("Bildirim için seçili", { exact: true })
          .count(),
        2,
      );
      assert.equal(
        await page
          .locator(".change-log-details")
          .getByText(
            "Biyografi: After <script>window.logInjected=true</script>",
            { exact: true },
          )
          .isVisible(),
        true,
      );
      assert.equal(await page.evaluate(() => window.logInjected), undefined);
      await page.waitForFunction(
        async () =>
          (
            await window.TrackerDesktop.command("state")
          ).state.notificationLog.find((e) => e.id === "qa-multi-change")
            .readAt != null,
      );
      await page.screenshot({
        path: path.join(output, "notification-detail.png"),
        fullPage: true,
      });
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
      await page.getByRole("button", { name: /^Okunmamış/ }).click();
      assert.equal(
        await page
          .locator('.notification-log-row[data-id="qa-multi-change"]')
          .count(),
        0,
      );
      await page.getByRole("button", { name: "Tümü", exact: true }).click();
      await desktop.close();
      desktop = null;
      await launch();
      const reopened = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(
        reopened.state.notificationLog.find(
          (entry) => entry.id === "qa-multi-change",
        ).readAt != null,
        true,
      );
      assert.equal(
        reopened.state.notificationRules["200"].fields.following,
        false,
      );
      await page.locator('.sidebar nav a[href="#notifications"]').click();
      await page
        .locator('.notification-log-row[data-id="qa-multi-change"]')
        .click();
      assert.equal(await page.locator(".change-log-details > li").count(), 3);
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
    },
  );
  await check(
    "Access review shows suspicion and concrete evidence without a definitive blocking claim",
    async () => {
      await page.locator('.sidebar nav a[href="#lists"]').click();
      await page.locator("#list-owner").selectOption("100");
      await page
        .getByRole("button", { name: "Takipçiler", exact: true })
        .click();
      const review = page.locator(".access-review");
      assert.equal(
        await review
          .getByText("Engellenme şüphesi", { exact: true })
          .isVisible(),
        true,
      );
      assert.equal(
        await review
          .getByText("Kullanıcı adı değişmiş; profil açılıyor", {
            exact: true,
          })
          .isVisible(),
        true,
      );
      await review
        .getByRole("button", { name: "Kanıtları gör", exact: true })
        .first()
        .click();
      const evidence = page.getByRole("dialog").locator(".access-evidence");
      assert.equal(await evidence.getByText("404", { exact: true }).isVisible(), false);
      await evidence.getByText("Teknik ayrıntılar", { exact: true }).click();
      assert.equal(
        await evidence
          .getByRole("heading", { name: "Engellenme şüphesi", exact: true })
          .isVisible(),
        true,
      );
      assert.equal(
        await evidence.getByText("404", { exact: true }).isVisible(),
        true,
      );
      assert.equal(
        await evidence.getByText("200", { exact: true }).isVisible(),
        true,
      );
      assert.equal(
        (await evidence.textContent()).includes(
          "kesin engelleme kanıtı değildir",
        ),
        true,
      );
      assert.equal((await evidence.textContent()).includes("engelledi"), false);
      await page.screenshot({
        path: path.join(output, "access-evidence.png"),
        fullPage: true,
      });
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
      await review
        .getByRole("button", { name: "Profil kontrol geçmişi", exact: true })
        .click();
      assert.equal(
        await page.locator(".timeline-events .event-row").count(),
        1,
      );
      assert.equal(
        (
          await page.locator(".timeline-events .event-row").textContent()
        ).includes("Engellenme şüphesi"),
        true,
      );
      await page.locator(".timeline-events .event-row").click();
      assert.equal(
        await page.getByRole("dialog").locator(".access-evidence").isVisible(),
        true,
      );
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
    },
  );
  await check(
    "Missing-follower opt-in persists and manual access command respects offline QA boundary",
    async () => {
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page
        .getByRole("switch", {
          name: "Kaybolan kayıtlı takipçileri incele",
          exact: true,
        })
        .click();
      await page.waitForFunction(
        async () =>
          (await window.TrackerDesktop.command("state")).state.settings
            .investigateMissing,
      );
      const result = await page.evaluate(() =>
        window.TrackerDesktop.command("check_access", { id: "200" }),
      );
      assert.equal(result.ok, false);
      assert.equal(result.error.code, "offline");
      assert.equal(result.state.gate.attempts, 2);
      assert.equal(
        result.state.access.find((c) => c.id === "200").checks.at(-1).status,
        "suspected",
      );
      await page
        .getByRole("switch", {
          name: "Kaybolan kayıtlı takipçileri incele",
          exact: true,
        })
        .click();
      await page.waitForFunction(
        async () =>
          !(await window.TrackerDesktop.command("state")).state.settings
            .investigateMissing,
      );
    },
  );
  await check(
    "Account form and code fields are password protected",
    async () => {
      await page
        .getByRole("button", { name: "Hesap bağlantısı", exact: true })
        .first()
        .click();
      assert.equal(
        await page.locator("#login-password").getAttribute("type"),
        "password",
      );
      assert.equal(
        await page.locator("#login-password").getAttribute("autocomplete"),
        "off",
      );
      await page.getByRole("button", { name: "Kapat", exact: true }).click();
    },
  );
  await check("Request information preserves HTTP and cooldown", async () => {
    await page.locator('.sidebar a[href="#settings"]').click();
    await page.getByRole("button", { name: "İncele", exact: true }).click();
    assert.equal(
      await page
        .getByRole("dialog")
        .getByText("429", { exact: true })
        .isVisible(),
      true,
    );
    await page.getByRole("button", { name: "Kapat", exact: true }).click();
  });
  await check("No horizontal overflow on six desktop routes", async () => {
    await page.setViewportSize({ width: 1100, height: 800 });
    for (const route of [
      "overview",
      "people",
      "lists",
      "timeline",
      "settings",
      "notifications",
    ]) {
      await page.locator(`.sidebar nav a[href="#${route}"]`).click();
      assert.equal(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        true,
        route,
      );
    }
  });
  await check("English and Turkish cover six routes, preserve user text, and persist after restart", async () => {
    await page.locator('.sidebar nav a[href="#settings"]').click();
    const before = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    await page.locator('#app-language').selectOption('en');
    await page.waitForFunction(() => document.documentElement.lang === 'en');
    await page.getByRole('heading', { name: 'Small settings, a smoother experience.', exact: true }).waitFor();
    const after = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    assert.deepEqual(after.gate, before.gate);
    assert.deepEqual(after.people, before.people);
    assert.deepEqual(after.history, before.history);
    assert.deepEqual(after.notificationRules, before.notificationRules);
    assert.deepEqual(after.notificationLog, before.notificationLog);
    assert.equal(after.notificationTypes.followers, 'Follower count');
    assert.equal(await page.evaluate(() => window.TrackerI18n.formats('en').number.format(1200)), '1,200');
    const note = 'Özet · Kaydet · İstanbul <img src=x onerror=alert(1)>';
    await page.evaluate(note => window.TrackerDesktop.command('save_person', { id: '200', note }), note);
    await page.locator('.sidebar nav a[href="#people"]').click();
    await page.getByRole('button', { name: 'Open QA qa.deniz', exact: true }).click();
    assert.equal(await page.locator('#person-note').inputValue(), note);
    await page.getByRole('dialog').getByRole('button', { name: 'Close', exact: true }).click();
    const error = await page.evaluate(() => window.TrackerDesktop.command('refresh', {}));
    assert.equal(error.error.message, 'Instagram connectivity is disabled in this isolated test window.');
    await page.setViewportSize({ width: 1100, height: 850 });
    for (const route of ['overview', 'people', 'lists', 'timeline', 'settings', 'notifications']) {
      await page.locator(`.sidebar nav a[href="#${route}"]`).click();
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, route);
    }
    await page.locator('.notification-log-row[data-id="qa-multi-change"]').click();
    await page.getByRole('heading', { name: 'What changed in this check?', exact: true }).waitFor();
    assert.match(await page.getByRole('dialog').innerText(), /Follower count/);
    await page.getByRole('dialog').getByRole('button', { name: 'Close', exact: true }).click();
    await page.locator('.sidebar nav a[href="#settings"]').click();
    await page.screenshot({ path: path.join(output, 'settings-en.png'), fullPage: true });
    await desktop.close(); desktop = null; await launch('en');
    assert.equal(await page.evaluate(() => document.documentElement.lang), 'en');
    const restored = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    assert.equal(restored.people.find(p => p.id === '200').note, note);
    assert.equal(restored.gate.attempts, before.gate.attempts);
    await page.locator('.sidebar nav a[href="#settings"]').click();
    await page.locator('#app-language').selectOption('tr');
    await page.waitForFunction(() => document.documentElement.lang === 'tr');
    await page.getByRole('heading', { name: 'Küçük ayarlar, daha rahat bir deneyim.', exact: true }).waitFor();
  });
  await check("Spanish and Brazilian Portuguese persist, preserve evidence and render all routes", async () => {
    const before = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
    for (const language of ['es', 'pt-BR']) {
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page.locator('#app-language').selectOption(language);
      await page.waitForFunction(value => document.documentElement.lang === value, language);
      for (const route of ['overview', 'people', 'lists', 'timeline', 'notifications', 'settings']) {
        await page.locator(`.sidebar nav a[href="#${route}"]`).click();
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `${language}/${route}`);
      }
      await page.screenshot({path:path.join(output, `settings-${language}.png`),fullPage:true});
      await desktop.close(); desktop = null;
      desktop = await electron.launch({executablePath,args:[`--tracker-test-home=${home}`],env});
      page = await desktop.firstWindow();
      page.on('pageerror', e => errors.push(e.message));
      page.on('request', r => {if (!r.url().startsWith('file:') && !r.url().startsWith('data:')) requests.push(r.url());});
      await page.waitForFunction(value => document.documentElement.lang === value && !!document.querySelector('.sidebar'), language);
      const after = (await page.evaluate(() => window.TrackerDesktop.command('state'))).state;
      assert.equal(after.settings.language, language);
      assert.deepEqual(after.gate, before.gate);
      assert.deepEqual(after.people, before.people);
      assert.deepEqual(after.account, before.account);
      await page.locator('.sidebar nav a[href="#settings"]').click();
    }
    await page.locator('#app-language').selectOption('tr');
    await page.waitForFunction(() => document.documentElement.lang === 'tr');
  });
  await check("All local renderer assets and no script errors", async () => {
    assert.deepEqual(errors, []);
    assert.deepEqual(requests, []);
  });
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.locator('.sidebar nav a[href="#overview"]').click();
  await page.screenshot({
    path: path.join(output, "overview.png"),
    fullPage: true,
  });
  await check(
    "Explicit local deletion removes records while retaining restriction history",
    async () => {
      await page.locator('.sidebar nav a[href="#settings"]').click();
      await page
        .getByRole("button", { name: "Yerel verileri sil", exact: true })
        .click();
      await page
        .getByRole("dialog")
        .getByRole("button", { name: "Yerel verileri sil", exact: true })
        .click();
      await page
        .getByText("Yerel kayıtlar silindi.", { exact: true })
        .waitFor();
      const state = await page.evaluate(() =>
        window.TrackerDesktop.command("state"),
      );
      assert.equal(state.state.account, null);
      assert.equal(state.state.people.length, 0);
      assert.equal(state.state.gate.status, 429);
      assert.equal(state.state.gate.attempts, 2);
      await page.locator('.sidebar nav a[href="#overview"]').click();
      await page.screenshot({
        path: path.join(output, "empty-overview.png"),
        fullPage: true,
      });
    },
  );
  await writeFile(
    path.join(output, "checks.json"),
    JSON.stringify(
      {
        checks,
        errors,
        externalRequests: requests.length,
        isolatedSyntheticHome: home,
        liveInstagram: false,
      },
      null,
      2,
    ),
  );
  console.log(
    `${checks.length} packaged desktop checks passed; no Instagram requests.`,
  );
} catch (error) {
  if (page) {
    await page.screenshot({path:path.join(output,"failure.png"),fullPage:true}).catch(()=>{});
    await writeFile(path.join(output,"failure.json"),JSON.stringify({checks,errors,requests,
      text:await page.locator("body").innerText().catch(()=>"Window unavailable"),
      home,error:String(error)},null,2));
  }
  throw error;
} finally {
  if (desktop) await desktop.close();
}
