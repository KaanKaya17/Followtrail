const { spawn } = require("node:child_process");
class Backend {
  constructor({ executable, dataDir, onEvent, offline = false, appVersion = null }) {
    this.appVersion = appVersion;
    this.offline = offline;
    this.executable = executable;
    this.dataDir = dataDir;
    this.onEvent = onEvent;
    this.state = null;
    this.pending = new Map();
    this.counter = 0;
    this.busy = false;
    this.ready = false;
    this.failure = null;
    this.progress = null;
  }
  start() {
    const env = Object.fromEntries(
      ["SYSTEMROOT", "WINDIR", "LOCALAPPDATA", "TEMP", "TMP"]
        .filter((k) => process.env[k])
        .map((k) => [k, process.env[k]]),
    );
    env.PYTHONUTF8 = "1";
    env.PYTHONIOENCODING = "utf-8";
    this.child = spawn(
      this.executable,
      ["--data-dir", this.dataDir, ...(this.offline ? ["--offline"] : [])],
      {
        windowsHide: true,
        stdio: ["pipe", "pipe", "pipe"],
        env,
      },
    );
    this.child.stdin.on("error", () =>
      this.fail("Yerel veri hizmeti bağlantısı kesildi."),
    );
    this.child.stderr.resume();
    let buffer = "";
    this.child.stdout.setEncoding("utf8");
    this.child.stdout.on("data", (chunk) => {
      buffer += chunk;
      // Account for derived ID differences on top of the 64 MiB vault budget.
      if (Buffer.byteLength(buffer) > 96 * 1024 * 1024) {
        this.fail("Yerel yanıt boyut sınırını aştı.");
        this.child.kill();
        return;
      }
      let end;
      while ((end = buffer.indexOf("\n")) !== -1) {
        const line = buffer.slice(0, end);
        buffer = buffer.slice(end + 1);
        try {
          this.message(JSON.parse(line));
        } catch {
          this.fail("Yerel veri yanıtı okunamadı.");
        }
      }
    });
    this.child.on("error", () =>
      this.fail(
        "Yerel veri hizmeti başlatılamadı. Uygulama klasörünü tam olarak çıkart.",
      ),
    );
    this.child.on("exit", () =>
      this.fail("Yerel veri hizmeti kapandı. Uygulamayı yeniden açabilirsin."),
    );
  }
  fail(message) {
    // A worker can send a specific fatal cause before exiting. Keep that
    // cause instead of replacing it with the subsequent generic exit event.
    if (this.failure) return;
    this.failure = message;
    this.ready = false;
    this.busy = false;
    this.progress = null;
    for (const resolve of this.pending.values())
      resolve({
        ok: false,
        error: { code: "service", message },
        state: this.state,
      });
    this.pending.clear();
    this.onEvent({ type: "fatal", message });
  }
  message(message) {
    if (message.type === "fatal") {
      this.fail(message.message);
      return;
    }
    if (message.state) {
      const seen = new Set(
        (this.state?.notificationLog || []).map((entry) => entry.id),
      );
      const entries = message.notifications ?? (
        message.type === "result" && this.state
          ? (message.state.notificationLog || []).filter(
              (entry) =>
                entry.id === message.notificationGroupId &&
                !seen.has(entry.id) &&
                entry.desktopEligible,
            )
          : []);
      // Package identity belongs to Electron; the worker must not duplicate it.
      if (this.appVersion !== null) message.state = {...message.state, version:this.appVersion};
      this.state = message.state;
      this.onNotifications?.(entries);
      if (Array.isArray(message.readAlerts) && message.readAlerts.length) this.onReadAlerts?.(message.readAlerts);
    }
    if (message.type === "ready") this.ready = true;
    if (message.type === "progress") {
      if (this.progress?.phase === 'cancelling') message = {...message,phase:'cancelling'};
      this.progress = message;
    }
    if (message.type === "result") {
      this.busy = false;
      this.progress = null;
    }
    if (message.type === "result" || message.type === "control") {
      const resolve = this.pending.get(message.id);
      this.pending.delete(message.id);
      resolve?.(message);
    }
    const { storyMedia, instagramArchive, archiveBrowser, ...event } = message;
    this.onEvent(event);
  }
  async command(operation, args) {
    if (operation === "state")
      return {
        ok: !this.failure,
        state: this.state,
        busy: this.busy,
        progress: this.progress,
        error: this.failure ? { message: this.failure } : null,
        starting: !this.ready && !this.failure,
      };
    if (!this.ready)
      return {
        ok: false,
        error: { message: this.failure || "Yerel hizmet hazırlanıyor." },
      };
    const control = operation.startsWith("export_browser_") || ["history_page", "history_detail", "daily_stats", "list_page", "list_membership", "cancel", "code", "search_avatar", "cancel_search_images", "list_avatar", "cancel_list_images"].includes(operation);
    if (!control && this.busy)
      return {
        ok: false,
        error: {
          code: "busy",
          message: "Önce devam eden işlemi bitir veya iptal et.",
        },
      };
    if (!control) this.busy = true;
    if (operation === 'cancel' && this.busy) {
      this.progress={...(this.progress || {}),type:'progress',phase:'cancelling'};
      this.onEvent(this.progress);
    }
    const id = ++this.counter;
    return new Promise((resolve) => {
      this.pending.set(id, resolve);
      this.child.stdin.write(
        JSON.stringify({ id, operation, args }) + "\n",
        "utf8",
        (error) => {
          if (error) this.fail("Yerel veri hizmetine ulaşılamadı.");
        },
      );
    });
  }
  async stop() {
    if (!this.child?.pid || this.child.exitCode !== null) return;
    await this.command("cancel", {});
    this.child.stdin.end();
    await new Promise((resolve) => {
      const timer = setTimeout(() => {
        this.child.kill();
        resolve();
      }, 28000);
      this.child.once("exit", () => {
        clearTimeout(timer);
        resolve();
      });
    });
  }
}
module.exports = { Backend };
