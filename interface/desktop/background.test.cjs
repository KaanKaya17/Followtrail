const test = require("node:test");
const assert = require("node:assert/strict");
const { EventEmitter } = require("node:events");
const { Background } = require("./background.cjs");

function setup() {
  class Tray extends EventEmitter {
    isDestroyed() {
      return !!this.destroyed;
    }
    setToolTip(value) {
      this.tooltip = value;
    }
    setContextMenu(value) {
      this.menu = value;
    }
    destroy() {
      this.destroyed = true;
    }
  }
  const calls = [],
    actions = [];
  const backend = {
    ready: true,
    busy: false,
    state: {
      settings: { automatic: true },
      monitor: { enabled: true, held: false, nextAt: 100 },
    },
    command: async (operation, args) => {
      calls.push({ operation, args });
    },
  };
  let scheduled, interval, cleared;
  const owner = new Background({
    Tray,
    Menu: { buildFromTemplate: (x) => x },
    image: {},
    backend,
    clock: () => 100,
    showWindow: () => actions.push("show"),
    quit: () => actions.push("quit"),
    schedule: (callback, ms) => {
      scheduled = callback;
      interval = ms;
      return "timer";
    },
    unschedule: (timer) => {
      cleared = timer;
    },
  });
  return {
    owner,
    backend,
    calls,
    actions,
    tick: () => scheduled(),
    interval: () => interval,
    cleared: () => cleared,
  };
}

test("five-second coordinator runs a due check even when the window is hidden", async () => {
  const s = setup(),
    window = new EventEmitter();
  window.hide = () => {
    window.hidden = true;
  };
  s.owner.attach(window);
  let prevented = false;
  window.emit("close", {
    preventDefault() {
      prevented = true;
    },
  });
  assert.equal(prevented, true);
  assert.equal(window.hidden, true);
  assert.equal(s.interval(), 5000);
  await s.tick();
  assert.deepEqual(s.calls, [{ operation: "automatic", args: {} }]);
});
test("busy, disabled, held, startup, and future states send no automatic command", async () => {
  for (const change of [
    (s) => (s.backend.busy = true),
    (s) => (s.backend.ready = false),
    (s) => (s.backend.state.monitor.enabled = false),
    (s) => (s.backend.state.monitor.held = true),
    (s) => (s.backend.state.monitor.nextAt = 101),
    (s) => (s.backend.state.monitor.nextAt = null),
  ]) {
    const s = setup();
    change(s);
    await s.tick();
    assert.equal(s.calls.length, 0);
  }
});
test("tray open, toggle and explicit quit use the same application callbacks", async () => {
  const s = setup();
  s.owner.tray.emit("click");
  s.owner.tray.emit("double-click");
  s.owner.tray.menu.find((item) => item.label === "Tracker'ı aç").click();
  await s.owner.tray.menu.find((item) => item.type === "checkbox").click();
  s.owner.tray.menu.find((item) => item.label === "Tamamen çık").click();
  assert.deepEqual(s.actions, ["show", "show", "show", "quit"]);
  assert.deepEqual(s.calls, [
    { operation: "settings", args: { automatic: false } },
  ]);
});
test("quitting cancels the coordinator, permits window destruction and removes tray", async () => {
  const s = setup(),
    window = new EventEmitter();
  window.hide = () => assert.fail("must not hide during quit");
  s.owner.attach(window);
  s.owner.beginQuit();
  window.emit("close", {
    preventDefault() {
      assert.fail("quit must close");
    },
  });
  await s.tick();
  assert.equal(s.calls.length, 0);
  assert.equal(s.cleared(), "timer");
  s.owner.destroy();
  assert.equal(s.owner.tray.isDestroyed(), true);
});
