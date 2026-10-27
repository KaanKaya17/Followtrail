// Main-process lifetime and scheduler; renderer visibility cannot pause them.
class Background {
  constructor({
    Tray,
    Menu,
    image,
    showWindow,
    quit,
    backend,
    clock = () => Date.now() / 1000,
    schedule = setInterval,
    unschedule = clearInterval,
  }) {
    this.backend = backend;
    this.Menu = Menu;
    this.clock = clock;
    this.unschedule = unschedule;
    this.stopping = false;
    this.tray = new Tray(image);
    this.tray.on("click", showWindow);
    this.tray.on("double-click", showWindow);
    this.showWindow = showWindow;
    this.quit = quit;
    this.update();
    this.timer = schedule(() => this.tick(), 5000);
    this.timer.unref?.();
  }
  attach(window) {
    window.on("close", (event) => {
      if (!this.stopping && !this.tray.isDestroyed()) {
        event.preventDefault();
        window.hide();
      }
    });
  }
  tick() {
    const monitor = this.backend.state?.monitor;
    if (
      !this.stopping &&
      !this.backend.busy &&
      this.backend.ready &&
      monitor?.enabled &&
      !monitor.held &&
      monitor.nextAt != null &&
      monitor.nextAt <= this.clock()
    ) {
      return this.backend.command("automatic", {}).catch(() => {});
    }
  }
  update() {
    if (this.stopping || this.tray.isDestroyed()) return;
    const enabled = !!this.backend.state?.settings.automatic;
    const held = this.backend.state?.monitor?.held;
    const status = !this.backend.ready
      ? "Yerel hizmet hazır değil"
      : !enabled
        ? "Otomatik kontrol kapalı"
        : held
          ? "Otomatik kontrol bekliyor"
          : "Otomatik kontrol açık";
    this.tray.setToolTip(`InstagramTracker — ${status}`);
    this.tray.setContextMenu(
      this.Menu.buildFromTemplate([
        { label: "Tracker'ı aç", click: this.showWindow },
        { label: status, enabled: false },
        {
          label: "Otomatik profil kontrolü",
          type: "checkbox",
          checked: enabled,
          enabled: !!this.backend.ready && !this.backend.busy,
          click: async () => {
            try {
              await this.backend.command("settings", { automatic: !enabled });
            } finally {
              this.update();
            }
          },
        },
        { type: "separator" },
        { label: "Tamamen çık", click: this.quit },
      ]),
    );
  }
  beginQuit() {
    this.stopping = true;
    this.unschedule(this.timer);
  }
  destroy() {
    this.beginQuit();
    if (!this.tray.isDestroyed()) this.tray.destroy();
  }
}
module.exports = { Background };
