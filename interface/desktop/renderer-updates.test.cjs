const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { RendererUpdates } = require('./renderer-updates.cjs');

function setup() {
  const window = new EventEmitter();
  Object.assign(window, { shown: true, minimized: false, destroyed: false,
    isDestroyed() { return this.destroyed; }, isVisible() { return this.shown; }, isMinimized() { return this.minimized; } });
  const sent = [];
  window.webContents = { send: (...args) => sent.push(args) };
  let status = { state: { value: 1 }, busy: false, progress: null, error: null };
  const updates = new RendererUpdates({ getWindow: () => window, getStatus: () => status });
  updates.attach(window);
  return { window, updates, sent, setStatus: value => { status = value; } };
}
test('hidden updates retain no intermediate snapshots and resume exactly once with current backend status', () => {
  const s = setup(); s.window.shown = false;
  for (let n = 0; n < 1000; n++) s.updates.send({ type: 'result', state: { value: n } });
  s.updates.send({ type: 'progress', phase: 'list' });
  assert.equal(s.sent.length, 0);
  s.setStatus({ state: { value: 'latest' }, busy: true, progress: { phase: 'code' }, error: null });
  s.window.shown = true; s.window.emit('show'); s.window.emit('restore');
  assert.deepEqual(s.sent, [['tracker:event', { type: 'resume', visible:true, state: { value: 'latest' }, busy: true, progress: { phase: 'code' }, error: null }]]);
});
test('minimized windows suppress updates; restoring preserves failures and one actionable warning', () => {
  const s = setup(); s.window.minimized = true;
  s.updates.send({ type: 'ready', notificationWarning: 'Old warning', state: {} });
  s.updates.send({ type: 'notification_warning', message: 'Latest warning' });
  s.updates.send({ type: 'fatal', message: 'Service failed' });
  s.setStatus({ state: {}, busy: false, progress: null, error: { message: 'Service failed' } });
  s.window.emit('show'); assert.equal(s.sent.length, 0);
  s.window.minimized = false; s.window.emit('restore');
  assert.equal(s.sent.length, 2);
  assert.equal(s.sent[0][1].error.message, 'Service failed');
  assert.equal(s.sent[1][1].message, 'Latest warning');
  s.window.emit('show'); assert.equal(s.sent.length, 2);
});
test('visible operation results and notification-click events keep their normal path', () => {
  const s = setup();
  const event = { type: 'open_notification', id: 'group' };
  s.updates.send(event);
  assert.deepEqual(s.sent, [['tracker:event', event]]);
  s.window.destroyed = true;
  s.updates.send({ type: 'fatal', message: 'closed' });
  s.updates.flush(); assert.equal(s.sent.length, 1);
});
test('notification click during asynchronous restore follows the fresh snapshot once', () => {
  const s = setup(); s.window.minimized = true;
  s.updates.send({ type: 'open_notification', id: 'group' });
  assert.equal(s.sent.length, 0);
  s.window.minimized = false; s.window.emit('restore'); s.window.emit('show');
  assert.equal(s.sent.length, 2);
  assert.equal(s.sent[0][1].type, 'resume');
  assert.deepEqual(s.sent[1][1], { type: 'open_notification', id: 'group' });
});

test('Native hide and minimize suspend local work and force a fresh resume even with no backend events',()=>{
  for (const [event,field,restore] of [['hide','shown','show'],['minimize','minimized','restore']]) {
    const s=setup();s.window[field]=event==='minimize';s.window.emit(event);
    assert.deepEqual(s.sent,[['tracker:event',{type:'visibility',visible:false}]]);
    s.setStatus({busy:true,progress:{phase:'waiting',until:123}});
    s.window[field]=event==='hide';s.window.emit(restore);
    assert.deepEqual(s.sent[1],['tracker:event',{type:'resume',visible:true,busy:true,progress:{phase:'waiting',until:123}}]);
    s.window.emit(restore);assert.equal(s.sent.length,2);
  }
});
