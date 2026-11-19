// Retained sample-only preview; the functional app uses main.cjs.
const { app, BrowserWindow, session } = require("electron");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
app.setName("Followtrail UI Preview");
app.setPath(
  "userData",
  path.join(app.getPath("appData"), "Followtrail-UI-Preview"),
);
const entry = path.join(__dirname, "..", "web", "index.html");
app.whenReady().then(() => {
  session.defaultSession.setPermissionRequestHandler(
    (_contents, _permission, callback) => callback(false),
  );
  session.defaultSession.setPermissionCheckHandler(() => false);
  const window = new BrowserWindow({
    width: 1440,
    height: 960,
    minWidth: 960,
    minHeight: 680,
    backgroundColor: "#FAF9F6",
    title: "Followtrail — Arayüz önizlemesi",
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
    if (url.split("#")[0] !== pathToFileURL(entry).href) event.preventDefault();
  });
  window.loadFile(entry);
});
app.on("window-all-closed", () => app.quit());
