const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld(
  "TrackerDesktop",
  Object.freeze({
    command: (operation, args = {}) =>
      ipcRenderer.invoke("tracker:command", operation, args),
    file: (action, args = {}) =>
      ipcRenderer.invoke("tracker:file", action, args),
    openLink: url => ipcRenderer.invoke('tracker:open-link',url),
    subscribe: (callback) => {
      const listener = (_event, data) => callback(data);
      ipcRenderer.on("tracker:event", listener);
      return () => ipcRenderer.removeListener("tracker:event", listener);
    },
  }),
);
