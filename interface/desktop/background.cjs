// Main-process lifetime and scheduler; renderer visibility cannot pause them.
const i18n = require('../web/i18n.js');
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
    const monitor = this.backend.state?.backgroundMonitor || this.backend.state?.monitor;
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
    const selectedEnabled = !!this.backend.state?.settings.automatic;
    const enabled = this.backend.state?.backgroundMonitor?.enabled ?? selectedEnabled;
    const held = (this.backend.state?.backgroundMonitor || this.backend.state?.monitor)?.held;
    const language = this.backend.state?.settings.language || 'tr';
    const t = (message, ...values) => i18n.translate(message, values, language);
    const menuState = JSON.stringify([enabled, selectedEnabled, this.backend.state?.activeAccountId, !!held, !!this.backend.ready, !!this.backend.busy, language]);
    if (menuState === this.menuState) return;
    const status = !this.backend.ready
      ? t("Yerel hizmet hazır değil")
      : !enabled
        ? t("Otomatik kontrol kapalı")
        : held
          ? t("Otomatik kontrol bekliyor")
          : t("Otomatik kontrol açık");
    this.tray.setToolTip(`Followtrail — ${status}`);
    this.tray.setContextMenu(
      this.Menu.buildFromTemplate([
        { label: t("Followtrail'i aç"), click: this.showWindow },
        { label: status, enabled: false },
        {
          label: this.backend.state?.accounts ? t("Seçili hesabın otomatik kontrolü") : t("Otomatik profil kontrolü"),
          type: "checkbox",
          checked: selectedEnabled,
          enabled: !!this.backend.ready && !this.backend.busy,
          click: async () => {
            try {
              await this.backend.command("settings", { automatic: !selectedEnabled });
            } finally {
              this.update();
            }
          },
        },
        { type: "separator" },
        { label: t("Tamamen çık"), click: this.quit },
      ]),
    );
    this.menuState = menuState;
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
