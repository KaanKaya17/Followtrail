const { app, BrowserWindow, session } = require("electron");
const path = require("node:path");
const { pathToFileURL } = require("node:url");

app.setName("InstagramTracker");
app.setPath(
  "userData",
  path.join(app.getPath("appData"), "InstagramTracker-UI-Preview"),
);
const entry = path.join(__dirname, "..", "web", "index.html");
const entryUrl = pathToFileURL(entry).href;
function open() {
  const window = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 960,
    minHeight: 680,
    backgroundColor: "#FAF9F6",
    title: "Tracker — Arayüz önizlemesi",
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true,
    },
  });
  window.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  window.webContents.on("will-navigate", (event, url) => {
    if (url.split("#")[0] !== entryUrl) event.preventDefault();
  });
  window.loadFile(entry);
}
app.whenReady().then(() => {
  session.defaultSession.setPermissionRequestHandler(
    (_webContents, _permission, callback) => callback(false),
  );
  session.defaultSession.setPermissionCheckHandler(() => false);
  open();
  app.on("activate", () => {
    if (!BrowserWindow.getAllWindows().length) open();
  });
});
app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
