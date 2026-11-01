const {
  app,
  BrowserWindow,
  session,
  ipcMain,
  dialog,
  Notification,
  shell,
  Tray,
  Menu,
  nativeImage,
  nativeTheme,
} = require("electron");
const path = require("node:path");
const os = require("node:os");
const fs = require("node:fs/promises");
const { existsSync } = require("node:fs");
const { pathToFileURL } = require("node:url");
const { Backend } = require("./service.cjs");
const { Background } = require("./background.cjs");
const { RendererUpdates } = require("./renderer-updates.cjs");
const themeBackgrounds = require('./theme.cjs');
const i18n = require('../web/i18n.js');
const t = (message, ...values) => i18n.translate(message, values, backend?.state?.settings.language || 'tr');
const {
  APP_ID,
  TOAST_ID,
  ensureShortcut,
  showNotification,
  changeNotification,
  openAccountNotification,
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
  background,
  quitting = false;
const rendererUpdates = new RendererUpdates({
  getWindow: () => window,
  getStatus: () => ({
    state: backend.state,
    busy: backend.busy,
    progress: backend.progress,
    error: backend.failure ? { message: backend.failure } : null,
  }),
});
const allowed = new Set([
  "state",
  "switch_account",
  "login",
  "import_counter",
  "refresh",
  "scan",
  "add_person",
  "search_people",
  "save_person",
  "remove_person",
  "settings",
  "disconnect",
  "delete_all",
  "cancel",
  "code",
  "check_access",
  "notification_rule",
  "read_notification",
  "wayback",
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
    switch_account: ["id"],
    login: ["username", "password"],
    import_counter: [],
    refresh: ["id"],
    scan: ["id", "direction"],
    add_person: ["username", "id"],
    search_people: ["query"],
    save_person: ["id", "favorite", "watching", "group", "note"],
    remove_person: ["id"],
    settings: [
      "theme",
      "language",
      "compact",
      "notifications",
      "automatic",
      "automaticLists",
      "interval",
      "investigateMissing",
    ],
    check_access: ["id"],
    notification_rule: ["id", "enabled", "fields"],
    read_notification: ["id"],
    wayback: ["id", "enabled", "clear"],
    disconnect: [],
    delete_all: [],
    cancel: [],
    code: ["code"],
  }[operation];
  if (Object.keys(args).some((k) => !fields.includes(k)))
    throw new Error("Unknown command field");
}
function send(event) {
  if (event.state) {
    const mode = event.state.settings.theme || 'system';
    const source = mode === 'oled' ? 'dark' : mode;
    if (nativeTheme.themeSource !== source) nativeTheme.themeSource = source;
    window?.setBackgroundColor(themeBackgrounds[mode === 'oled' ? 'oled' : nativeTheme.shouldUseDarkColors ? 'dark' : 'light']);
  }
  background?.update();
  if (event.type === "ready" && (app.isPackaged || testHome || event.state?.settings.notifications)) {
    try {
      ensureShortcut({ app, shell, testHome, dataDir, language: backend.state?.settings.language || "tr" });
    } catch {
      event.notificationWarning =
        t("Uygulama kısayolu hazırlanamadı; uygulamayı kendi klasöründen açabilirsin.");
    }
  }
  rendererUpdates.send(event);
}
function open() {
  window = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 960,
    minHeight: 680,
    backgroundColor: themeBackgrounds[nativeTheme.shouldUseDarkColors ? 'dark' : 'light'],
    title: "InstagramTracker",
    icon: path.join(__dirname, '..', ...(app.isPackaged ? ['desktop'] : ['.cache']), 'app-icon.png'),
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
  window.webContents.on('before-input-event', (_event, input) => {
    // Suppress only the native Reload accelerator. The renderer owns the
    // profile shortcut and its editing/busy/cooldown rules.
    window.webContents.setIgnoreMenuShortcuts(!!((input.control || input.meta) && !input.alt && !input.shift && input.key.toLowerCase() === 'r'));
  });
  window.webContents.on("will-navigate", (event, url) => {
    if (url.split("#")[0] !== entryUrl) event.preventDefault();
  });
  window.webContents.on("will-attach-webview", (event) =>
    event.preventDefault(),
  );
  window.loadFile(entry);
  background?.attach(window);
  rendererUpdates.attach(window);
}

function showWindow() {
  if (!app.isReady()) {
    app.whenReady().then(showWindow);
    return;
  }
  if (!window || window.isDestroyed()) open();
  if (window.isMinimized()) window.restore();
  window.show();
  window.focus();
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
          ensureShortcut({ app, shell, testHome, dataDir, language: backend.state?.settings.language || "tr" });
        } catch {
          return {
            ok: false,
            error: {
              message:
                t("Windows bildirim kısayolu hazırlanamadı. Kayıt izinlerini kontrol et."),
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
          error: { message: t("Önce devam eden işlemi bitir veya iptal et.") },
        };
      const fileAccountId = backend.state?.activeAccountId;
      const accountChanged = () => backend.state?.activeAccountId !== fileAccountId;
      const accountChangedResult = () => ({ok:false,error:{message:t("Dosya seçimi sırasında hesap değişti. İlgili hesabı seçip yeniden dene.")}});
      if (action === "open_storage") {
        await fs.mkdir(dataDir, { recursive: true });
        await shell.openPath(dataDir);
        return { ok: true };
      }
      if (action === "import_backup") {
        const selection = await dialog.showOpenDialog(window, {
          title: t("Şifreli gözlem yedeğini seç"),
          properties: ["openFile"],
          filters: [{ name: t("Tracker yedeği"), extensions: ["trackerbackup"] }],
        });
        if (selection.canceled) return { ok: true, cancelled: true };
        if (accountChanged()) return accountChangedResult();
        const confirm = await dialog.showMessageBox(window, {
          type: "question",
          buttons: [t("Vazgeç"), t("Yedeği yükle")],
          defaultId: 0,
          cancelId: 0,
          message: t("Yerel gözlemler bu yedekle değiştirilsin mi?"),
          detail:
            t("Önceki depo şifreli önceki kopyada korunur. Oturum aktarılmaz; tekrar hesap bağlantısı gerekir. Mola bilgisi kısaltılmaz."),
        });
        if (confirm.response !== 1) return { ok: true, cancelled: true };
        if (accountChanged()) return accountChangedResult();
        return backend.command(action, { path: selection.filePaths[0] });
      }
      const backup = action === "export_backup";
      const selection = await dialog.showSaveDialog(window, {
        title: backup
          ? t("Şifreli gözlem yedeğini kaydet")
          : t("Listeyi CSV olarak dışa aktar"),
        defaultPath: backup
          ? "InstagramTracker.trackerbackup"
          : "InstagramTracker-liste.csv",
        filters: [
          {
            name: backup ? t("Tracker yedeği") : "CSV",
            extensions: [backup ? "trackerbackup" : "csv"],
          },
        ],
      });
      if (selection.canceled) return { ok: true, cancelled: true };
      if (accountChanged()) return accountChangedResult();
      if (backup) return backend.command(action, { path: selection.filePath });
      const record =
        backend.state?.lists[`${String(args.id || "")}:${args.direction}`];
      const latest =
        record?.snapshots.find((s) => s.id === args.snapshotId) ||
        record?.snapshots.at(-1);
      if (!latest)
        return {
          ok: false,
          error: { message: t("Önce tamamlanmış bir liste kaydı oluştur.") },
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
    backend.onNotifications = (entries) => {
      for (const entry of entries) {
        const content = changeNotification(entry, backend.state?.settings.language || 'tr');
        showNotification({
          Notification,
          title: entry.ownerUsername ? `${content.title} · @${entry.ownerUsername}` : content.title,
          body: content.body,
          onClick: () => openAccountNotification({entry, backend, showWindow, send,
            onBusy: () => send({type:'notification_warning', message:t("Bildirimi açmak için devam eden işlemi bitirip ilgili hesabı seç.")}),
          }),
          onFailure: () =>
            send({
              type: "notification_warning",
              message:
                t("Windows bildirimi gösterilemedi. Gözlem zaman çizelgesine kaydedildi."),
            }),
        });
      }
    };
    open();
    const packagedIcon = path.join(__dirname, "tray.png");
    const image = nativeImage.createFromPath(
      existsSync(packagedIcon)
        ? packagedIcon
        : path.join(__dirname, "..", ".cache", "tray-icon.png"),
    );
    if (image.isEmpty())
      throw new Error(
        "Tracker tray icon is missing. Rebuild the desktop package.",
      );
    background = new Background({
      Tray,
      Menu,
      image,
      showWindow,
      quit: () => app.quit(),
      backend,
    });
    background.attach(window);
    backend.start();
    app.on("activate", showWindow);
  });
app.on("second-instance", showWindow);
app.on("window-all-closed", () => {
  if (!background || quitting) app.quit();
});
app.on("before-quit", (event) => {
  if (backend && !quitting) {
    event.preventDefault();
    quitting = true;
    background?.beginQuit();
    backend.stop().finally(() => app.quit());
  }
});
app.on("will-quit", () => background?.destroy());
