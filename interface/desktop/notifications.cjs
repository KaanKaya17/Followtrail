const path = require("node:path");
const fs = require("node:fs");
const i18n = require('../web/i18n.js');
const APP_ID = "dev.kaankaya.instagramtracker.desktop";
const TOAST_ID = "{D3085DD3-667D-4514-BD5F-1C8C0C8A5927}";

function changeNotification(entry, language = 'tr') {
  const t = (message, ...values) => i18n.translate(message, values, language);
  const selected = entry.changes.filter((change) =>
    entry.selectedChangeIds.includes(change.eventId),
  );
  const access = selected.filter((change) => change.category === "access");
  const strong = access.some(change => change.after?.reason === 'anonymous_visible');
  const accounts = new Set(selected.map((change) => change.username));
  let body =
      selected.length === 1
        ? selected[0].category === "access"
          ? i18n.stored(selected[0].title, language)
          : t("@{0} · {1} değişti. Ayrıntıları aç.", selected[0].username, t(selected[0].label))
        : access.length
          ? t("{0} hesap için engellenme şüphesi. Kontrol yöntemlerini ve kanıtları incele.", access.length)
          : t("{0} · {1} değişiklik. Ayrıntıları aç.", accounts.size === 1 ? "@" + selected[0].username : accounts.size + t(" hesap"), selected.length);
  const lists = selected.find(change => change.category === 'lists' && Array.isArray(change.after?.membersAdded));
  if (lists && !access.length) {
    const names = members => members.slice(0,2).map(person => '@'+person.username).join(', ') +
      (members.length > 2 ? ' · ' + t("ve {0} hesap daha", members.length-2) : '');
    body += '\n' + [lists.after.membersAdded.length ? t("Yeni: {0}",names(lists.after.membersAdded)) : '',
      lists.after.membersRemoved.length ? t("Listede yok: {0}",names(lists.after.membersRemoved)) : ''].filter(Boolean).join('\n');
  }
  return {
    title: access.length
      ? strong ? t("InstagramTracker · Güçlü engellenme şüphesi") : t("InstagramTracker · Engellenme şüphesi")
      : t("InstagramTracker · Yeni gözlem"),
    body,
    logId: entry.id,
  };
}

function ensureShortcut({ app, shell, testHome, dataDir, language = "tr" }) {
  const t = message => i18n.translate(message, [], language);
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
    description: t("InstagramTracker — yerel hesap gözlemleri"),
    appUserModelId: APP_ID,
    toastActivatorClsid: TOAST_ID,
    icon: process.execPath,
    iconIndex: 0,
  };
  fs.mkdirSync(path.dirname(shortcut), { recursive: true });
  if (fs.existsSync(shortcut)) {
    const previous = shell.readShortcutLink(shortcut);
    if (previous.appUserModelId !== APP_ID)
      throw new Error("A shortcut with a different owner exists");
    if (
      previous.target === options.target &&
      previous.args === options.args &&
      previous.cwd === options.cwd &&
      previous.description === options.description &&
      previous.icon === options.icon &&
      previous.iconIndex === options.iconIndex &&
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
    throw new Error(t("Notification shortcut could not be written"));
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

async function openAccountNotification({entry, backend, showWindow, send, onBusy}) {
  showWindow();
  if (entry.ownerId && entry.ownerId !== backend.state?.activeAccountId) {
    const result = await backend.command('switch_account', {id:entry.ownerId});
    if (!result.ok) { onBusy(); return; }
  }
  send({type:'open_notification',id:entry.id});
}
module.exports = {
  APP_ID,
  TOAST_ID,
  ensureShortcut,
  showNotification,
  changeNotification,
  openAccountNotification,
};
