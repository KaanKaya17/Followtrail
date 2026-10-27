const {
  app,
  BrowserWindow,
  session,
  ipcMain,
  dialog,
  Notification,
  shell,
} = require("electron");
const path = require("node:path");
const os = require("node:os");
const fs = require("node:fs/promises");
const { existsSync } = require("node:fs");
const { pathToFileURL } = require("node:url");
const { Backend } = require("./service.cjs");
const {
  APP_ID,
  TOAST_ID,
  ensureShortcut,
  showNotification,
} = require("./notifications.cjs");

app.setName("InstagramTracker");
app.setAppUserModelId(APP_ID);
app.setToastActivatorCLSID(TOAST_ID);
const testArg = process.argv.find((x) => x.startsWith("--tracker-test-home="));
let testHome = null;
if (testArg) {
  testHome = path.resolve(testArg.slice("--tracker-test-home=".length));
  if (
    path.dirname(testHome).toLowerCase() !==
      path.resolve(os.tmpdir()).toLowerCase() ||
    !path.basename(testHome).startsWith("TrackerQA-")
  )
    throw new Error("Invalid isolated test home");
}
app.setPath(
  "userData",
  testHome
    ? path.join(testHome, "electron")
    : path.join(app.getPath("appData"), "InstagramTracker-Desktop"),
);
const dataDir =
  testHome || path.join(process.env.LOCALAPPDATA, "InstagramTracker-Desktop");
const entry = path.join(__dirname, "..", "web", "desktop.html");
const entryUrl = pathToFileURL(entry).href;
let window,
  backend,
  quitting = false;
const allowed = new Set([
  "state",
  "login",
  "import_counter",
  "refresh",
  "scan",
  "add_person",
  "save_person",
  "remove_person",
  "settings",
  "disconnect",
  "delete_all",
  "cancel",
  "code",
]);

function senderValid(event) {
  return (
    window &&
    event.sender === window.webContents &&
    event.senderFrame === window.webContents.mainFrame &&
    event.senderFrame.url.split("#")[0] === entryUrl
  );
}
function validate(operation, args) {
  if (
    !allowed.has(operation) ||
    !args ||
    typeof args !== "object" ||
    Array.isArray(args)
  )
    throw new Error("Invalid command");
  if (Buffer.byteLength(JSON.stringify(args)) > 12000)
    throw new Error("Command too large");
  const fields = {
    state: [],
    login: ["username", "password"],
    import_counter: [],
    refresh: ["id"],
    scan: ["id", "direction"],
    add_person: ["username"],
    save_person: ["id", "favorite", "watching", "group", "note"],
    remove_person: ["id"],
    settings: ["compact", "notifications", "automatic", "interval"],
    disconnect: [],
    delete_all: [],
    cancel: [],
    code: ["code"],
  }[operation];
  if (Object.keys(args).some((k) => !fields.includes(k)))
    throw new Error("Unknown command field");
}
function send(event) {
  if (event.type === "ready" && event.state?.settings.notifications) {
    try {
      ensureShortcut({ app, shell, testHome, dataDir });
    } catch {
      event.notificationWarning =
        "Bildirim kısayolu hazırlanamadı; Windows bildirimi ayarlarını kontrol et.";
    }
  }
  if (window && !window.isDestroyed())
    window.webContents.send("tracker:event", event);
}
function open() {
  window = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 960,
    minHeight: 680,
    backgroundColor: "#FAF9F6",
    title: "InstagramTracker",
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true,
      spellcheck: false,
    },
  });
  window.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  window.webContents.on("will-navigate", (event, url) => {
    if (url.split("#")[0] !== entryUrl) event.preventDefault();
  });
  window.webContents.on("will-attach-webview", (event) =>
    event.preventDefault(),
  );
  window.loadFile(entry);
}

if (!testHome && !app.requestSingleInstanceLock()) app.quit();
else
  app.whenReady().then(() => {
    session.defaultSession.setPermissionRequestHandler(
      (_contents, _permission, callback) => callback(false),
    );
    session.defaultSession.setPermissionCheckHandler(() => false);
    session.defaultSession.webRequest.onBeforeRequest((details, callback) => {
      callback({
        cancel:
          !details.url.startsWith("file:") &&
          !details.url.startsWith("data:image/jpeg;base64,") &&
          !details.url.startsWith("devtools:"),
      });
    });
    const packagedWorker = path.join(
      __dirname,
      "..",
      "worker",
      "tracker-worker.exe",
    );
    const worker = existsSync(packagedWorker)
      ? packagedWorker
      : path.join(
          __dirname,
          "..",
          ".cache",
          "worker-dist",
          "tracker-worker",
          "tracker-worker.exe",
        );
    backend = new Backend({
      executable: worker,
      dataDir,
      onEvent: send,
      offline: !!testHome,
    });
    ipcMain.handle("tracker:command", async (event, operation, args = {}) => {
      if (!senderValid(event)) throw new Error("Untrusted sender");
      validate(operation, args);
      if (backend.busy && !["state", "code", "cancel"].includes(operation))
        return backend.command(operation, args);
      if (operation === "settings" && args.notifications === true) {
        try {
          ensureShortcut({ app, shell, testHome, dataDir });
        } catch {
          return {
            ok: false,
            error: {
              message:
                "Windows bildirim kısayolu hazırlanamadı. Kayıt izinlerini kontrol et.",
            },
          };
        }
      }
      return backend.command(operation, args);
    });
    ipcMain.handle("tracker:file", async (event, action, args = {}) => {
      const fileFields =
        action === "export_list" ? ["id", "direction", "snapshotId"] : [];
      if (
        !args ||
        typeof args !== "object" ||
        Array.isArray(args) ||
        Object.keys(args).some((key) => !fileFields.includes(key))
      )
        throw new Error("Invalid file fields");
      if (
        !senderValid(event) ||
        ![
          "export_backup",
          "import_backup",
          "export_list",
          "open_storage",
        ].includes(action)
      )
        throw new Error("Invalid file action");
      if (backend.busy)
        return {
          ok: false,
          error: { message: "Önce devam eden işlemi bitir veya iptal et." },
        };
      if (action === "open_storage") {
        await fs.mkdir(dataDir, { recursive: true });
        await shell.openPath(dataDir);
        return { ok: true };
      }
      if (action === "import_backup") {
        const selection = await dialog.showOpenDialog(window, {
          title: "Şifreli gözlem yedeğini seç",
          properties: ["openFile"],
          filters: [{ name: "Tracker yedeği", extensions: ["trackerbackup"] }],
        });
        if (selection.canceled) return { ok: true, cancelled: true };
        const confirm = await dialog.showMessageBox(window, {
          type: "question",
          buttons: ["Vazgeç", "Yedeği yükle"],
          defaultId: 0,
          cancelId: 0,
          message: "Yerel gözlemler bu yedekle değiştirilsin mi?",
          detail:
            "Önceki depo şifreli önceki kopyada korunur. Oturum aktarılmaz; tekrar hesap bağlantısı gerekir. Mola bilgisi kısaltılmaz.",
        });
        if (confirm.response !== 1) return { ok: true, cancelled: true };
        return backend.command(action, { path: selection.filePaths[0] });
      }
      const backup = action === "export_backup";
      const selection = await dialog.showSaveDialog(window, {
        title: backup
          ? "Şifreli gözlem yedeğini kaydet"
          : "Listeyi CSV olarak dışa aktar",
        defaultPath: backup
          ? "InstagramTracker.trackerbackup"
          : "InstagramTracker-liste.csv",
        filters: [
          {
            name: backup ? "Tracker yedeği" : "CSV",
            extensions: [backup ? "trackerbackup" : "csv"],
          },
        ],
      });
      if (selection.canceled) return { ok: true, cancelled: true };
      if (backup) return backend.command(action, { path: selection.filePath });
      const record =
        backend.state?.lists[`${String(args.id || "")}:${args.direction}`];
      const latest =
        record?.snapshots.find((s) => s.id === args.snapshotId) ||
        record?.snapshots.at(-1);
      if (!latest)
        return {
          ok: false,
          error: { message: "Önce tamamlanmış bir liste kaydı oluştur." },
        };
      const csv = (value) => {
        let text = String(value ?? "");
        if (/^[=+\-@\t\r\n]/.test(text)) text = "'" + text;
        return '"' + text.replaceAll('"', '""') + '"';
      };
      const rows = [
        ["account_id", "username", "name", "observed_at"],
        ...Object.values(latest.members).map((p) => [
          p.id,
          p.username,
          p.name,
          new Date(latest.finishedAt * 1000).toISOString(),
        ]),
      ];
      await fs.writeFile(
        selection.filePath,
        "\ufeff" + rows.map((row) => row.map(csv).join(",")).join("\r\n"),
        { encoding: "utf8" },
      );
      return { ok: true };
    });
    backend.onChanges = (state, fresh) => {
      if (
        fresh.length &&
        state.settings.notifications &&
        Notification.isSupported()
      ) {
        showNotification({
          Notification,
          title: "InstagramTracker · Yeni gözlem",
          body:
            fresh.length === 1
              ? fresh[0].title
              : `${fresh.length} yeni değişiklik gözlendi.`,
          onClick: () => {
            window?.show();
            window?.focus();
          },
          onFailure: () =>
            send({
              type: "notification_warning",
              message:
                "Windows bildirimi gösterilemedi. Gözlem zaman çizelgesine kaydedildi.",
            }),
        });
      }
    };
    open();
    backend.start();
    const timer = setInterval(() => {
      if (!backend.busy && backend.state?.settings.automatic)
        backend.command("automatic", {}).catch(() => {});
    }, 60000);
    timer.unref();
    app.on("activate", () => {
      if (!BrowserWindow.getAllWindows().length) open();
    });
  });
app.on("second-instance", () => {
  window?.show();
  window?.focus();
});
app.on("window-all-closed", () => app.quit());
app.on("before-quit", (event) => {
  if (backend && !quitting) {
    event.preventDefault();
    quitting = true;
    backend.stop().finally(() => app.quit());
  }
});
