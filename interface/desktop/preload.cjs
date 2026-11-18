const { contextBridge, ipcRenderer, webUtils } = require("electron");
contextBridge.exposeInMainWorld(
  "TrackerDesktop",
  Object.freeze({
    command: (operation, args = {}) =>
      ipcRenderer.invoke("tracker:command", operation, args),
    file: (action, args = {}) =>
      ipcRenderer.invoke("tracker:file", action, args),
    openDroppedArchive: file => {
      const path = webUtils.getPathForFile(file);
      if (!path) return {ok:false};
      return ipcRenderer.invoke("tracker:open-dropped-archive", path);
    },
    prepareDroppedComparison: (token, file) => {
      const path = webUtils.getPathForFile(file);
      if (!path) return {ok:false};
      return ipcRenderer.invoke("tracker:prepare-dropped-comparison", token, path);
    },
    openLink: url => ipcRenderer.invoke('tracker:open-link',url),
    subscribe: (callback) => {
      const listener = (_event, data) => callback(data);
      ipcRenderer.on("tracker:event", listener);
      return () => ipcRenderer.removeListener("tracker:event", listener);
    },
  }),
);
