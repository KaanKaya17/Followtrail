// Backend owns current state. Hidden windows need no state copies or DOM work;
// reopening sends one current snapshot, not a replay of intermediate results.
class RendererUpdates {
  constructor({ getWindow, getStatus }) {
    this.getWindow = getWindow;
    this.getStatus = getStatus;
    this.dirty = false;
    this.warning = null;
    this.notificationId = null;
  }
  visible() {
    const window = this.getWindow();
    return window && !window.isDestroyed() && window.isVisible() && !window.isMinimized();
  }
  attach(window) {
    window.on('show', () => this.flush());
    window.on('restore', () => this.flush());
  }
  send(event) {
    if (this.visible()) {
      this.getWindow().webContents.send('tracker:event', event);
      return;
    }
    this.dirty = true;
    if (event.type === 'open_notification') this.notificationId = event.id;
    // Keep one actionable warning, without retaining old full state objects.
    const warning = event.notificationWarning || (event.type === 'notification_warning' ? event.message : null);
    if (warning) this.warning = warning;
  }
  flush() {
    if (!this.dirty || !this.visible()) return;
    this.dirty = false;
    this.getWindow().webContents.send('tracker:event', { type: 'resume', ...this.getStatus() });
    if (this.warning) {
      const message = this.warning;
      this.warning = null;
      this.getWindow().webContents.send('tracker:event', { type: 'notification_warning', message });
    }
    if (this.notificationId) {
      const id = this.notificationId;
      this.notificationId = null;
      this.getWindow().webContents.send('tracker:event', { type: 'open_notification', id });
    }
  }
}
module.exports = { RendererUpdates };
