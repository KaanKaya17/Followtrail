const path = require("node:path");
const fs = require("node:fs");
const APP_ID = "dev.kaankaya.instagramtracker.desktop";
const TOAST_ID = "{D3085DD3-667D-4514-BD5F-1C8C0C8A5927}";

function changeNotification(entry) {
  const selected = entry.changes.filter((change) =>
    entry.selectedChangeIds.includes(change.eventId),
  );
  const access = selected.filter((change) => change.category === "access");
  const accounts = new Set(selected.map((change) => change.username));
  return {
    title: access.length
      ? "InstagramTracker · Engellenme şüphesi"
      : "InstagramTracker · Yeni gözlem",
    body:
      selected.length === 1
        ? selected[0].category === "access"
          ? selected[0].title
          : `@${selected[0].username} · ${selected[0].label} değişti. Ayrıntıları aç.`
        : access.length
          ? `${access.length} kayıtlı hesap için engellenme şüphesi. Hesaplar kapanmış da olabilir; kanıtları incele.`
          : `${accounts.size === 1 ? "@" + selected[0].username : accounts.size + " hesap"} · ${selected.length} değişiklik. Ayrıntıları aç.`,
    logId: entry.id,
  };
}

function ensureShortcut({ app, shell, testHome, dataDir }) {
  const shortcut = testHome
    ? path.join(testHome, "shortcuts", "InstagramTracker Desktop.lnk")
    : path.join(
        app.getPath("appData"),
        "Microsoft",
        "Windows",
        "Start Menu",
        "Programs",
        "InstagramTracker Desktop.lnk",
      );
  const options = {
    target: process.execPath,
    cwd: path.dirname(process.execPath),
    args: testHome
      ? `--tracker-test-home="${testHome}"`
      : app.isPackaged
        ? ""
        : `"${app.getAppPath()}"`,
    description: "InstagramTracker — yerel hesap gözlemleri",
    appUserModelId: APP_ID,
    toastActivatorClsid: TOAST_ID,
  };
  fs.mkdirSync(path.dirname(shortcut), { recursive: true });
  if (fs.existsSync(shortcut)) {
    const previous = shell.readShortcutLink(shortcut);
    if (previous.appUserModelId !== APP_ID)
      throw new Error("A shortcut with a different owner exists");
    if (
      previous.target === options.target &&
      previous.args === options.args &&
      previous.toastActivatorClsid?.toUpperCase() === TOAST_ID.toUpperCase()
    )
      return;
    fs.mkdirSync(dataDir, { recursive: true });
    fs.copyFileSync(
      shortcut,
      path.join(dataDir, "notification-shortcut.previous.lnk"),
    );
  }
  if (!shell.writeShortcutLink(shortcut, options))
    throw new Error("Notification shortcut could not be written");
}

function showNotification({ Notification, title, body, onClick, onFailure }) {
  if (!Notification.isSupported()) {
    onFailure();
    return;
  }
  try {
    const notification = new Notification({ title, body });
    notification.on("click", onClick);
    notification.on("failed", onFailure);
    notification.show();
  } catch {
    onFailure();
  }
}
module.exports = {
  APP_ID,
  TOAST_ID,
  ensureShortcut,
  showNotification,
  changeNotification,
};
