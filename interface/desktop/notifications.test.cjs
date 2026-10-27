const test = require("node:test");
const assert = require("node:assert/strict");
const { EventEmitter } = require("node:events");
const { Backend } = require("./service.cjs");
const { changeNotification, showNotification } = require("./notifications.cjs");

test("a suspicion is routed through fresh-change delivery once and survives unrelated profile changes", () => {
  const received = [];
  const service = new Backend({ onEvent: () => {} });
  service.onChanges = (_state, events) => {
    if (events.length) received.push(changeNotification(events));
  };
  const event = {
    id: "access1",
    personId: "200",
    type: "profile",
    topic: "access",
    title: "Engellenme şüphesi · @qa.arda. Hesap kapanmış da olabilir.",
    new: { status: "suspected" },
  };
  service.message({
    type: "ready",
    state: { events: [], settings: { notifications: true } },
  });
  service.message({
    type: "result",
    state: {
      events: [event, { id: "count1", type: "profile", title: "Sayı değişti" }],
      settings: { notifications: true },
    },
  });
  service.message({
    type: "result",
    state: {
      events: [event, { id: "count1", type: "profile", title: "Sayı değişti" }],
      settings: { notifications: true },
    },
  });
  assert.equal(received.length, 1);
  assert.equal(received[0].title, "InstagramTracker · Engellenme şüphesi");
  assert.equal(received[0].accessId, "200");
  assert.equal(received[0].body, event.title);
  assert.equal(received[0].body.includes("engelledi"), false);
});
test("native notification callbacks open the evidence and report delivery failure", () => {
  let notification,
    clicked = 0,
    failed = 0;
  class Notification extends EventEmitter {
    static isSupported() {
      return true;
    }
    constructor(options) {
      super();
      this.options = options;
      notification = this;
    }
    show() {
      this.shown = true;
    }
  }
  showNotification({
    Notification,
    title: "Engellenme şüphesi",
    body: "Kanıtları incele",
    onClick: () => clicked++,
    onFailure: () => failed++,
  });
  assert.equal(notification.shown, true);
  notification.emit("click");
  notification.emit("failed");
  assert.equal(clicked, 1);
  assert.equal(failed, 1);
});
