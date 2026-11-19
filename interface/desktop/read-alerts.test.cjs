const test = require("node:test");
const assert = require("node:assert/strict");
const { Backend } = require("./service.cjs");
const { readAlertNotification, readReport, openReadAlert } = require("./read-alerts.cjs");

const paused = {reason: 'format', http: 200, at: 1800000000, streak: 3, paused: true, missing: ['users.pk']};

test('read pause notification names the reader and the owning account in four languages', () => {
  const tr = readAlertNotification({reader: 'followers', reason: 'format', ownerUsername: 'qa.owner'});
  assert.equal(tr.title, 'Instagram okuma yolu değişmiş olabilir · @qa.owner');
  assert.match(tr.body, /^Takipçiler: otomatik okuma duraklatıldı\./);
  for (const language of ['en', 'es', 'pt-BR']) {
    const notice = readAlertNotification({reader: 'profile', reason: 'route'}, language);
    assert.doesNotMatch(notice.title + notice.body, /okuma|duraklatıldı/, language);
    assert.doesNotMatch(notice.title, /@/);
  }
});

test('report holds the route template and diagnosis only, never account data', () => {
  const state = {readHealth: {followers: {...paused, username: 'qa.owner', missing: ['users.pk', 'cookie']}}};
  const report = readReport(state, 'followers', '0.2.94');
  assert.equal(report.text.split('\n')[3], 'Request: GET i.instagram.com/api/v1/friendships/{id}/followers/');
  assert.match(report.text, /^App: Windows 0\.2\.94$/m);
  assert.match(report.text, /^Missing fields: users\.pk$/m);
  assert.match(report.text, /^Observed: 2027-01-15 08:00 UTC$/m);
  assert.doesNotMatch(report.text, /qa\.owner/);
  const url = new URL(report.url);
  assert.equal(url.origin + url.pathname, 'https://github.com/KaanKaya17/InstagramTracker/issues/new');
  assert.equal(url.searchParams.get('title'), 'Read compatibility: followers · format · HTTP 200 · Windows 0.2.94');
  assert.equal(url.searchParams.get('body'), report.text);
  assert.deepEqual([...url.searchParams.keys()], ['title', 'body']);
});

test('only a compatibility diagnosis of a known reader can be reported', () => {
  assert.equal(readReport({readHealth: {followers: {...paused, reason: 'limited'}}}, 'followers', '1.0.0'), null);
  assert.equal(readReport({readHealth: {followers: paused}}, 'stories', '1.0.0'), null);
  assert.equal(readReport({readHealth: {}}, 'followers', '1.0.0'), null);
  assert.equal(readReport(null, 'followers', '1.0.0'), null);
  assert.equal(readReport({readHealth: {['__proto__']: paused}}, '__proto__', '1.0.0'), null);
  const odd = readReport({readHealth: {profile: {reason: 'route', http: 'x', at: -1, streak: 1, paused: false}}}, 'profile', 'dev');
  assert.match(odd.text, /^HTTP: none$/m);
  assert.match(odd.text, /^App: Windows unknown$/m);
  assert.match(odd.text, /^Missing fields: none recorded$/m);
  assert.match(odd.text, /^Automatic reads paused: no$/m);
});

test('service hands worker read alerts to the main process once per result', () => {
  const service = new Backend({onEvent: () => {}}), received = [];
  service.onReadAlerts = alerts => received.push(...alerts);
  service.message({type: 'ready', state: {activeAccountId: '100', notificationLog: []}});
  service.message({type: 'result', state: {activeAccountId: '100', notificationLog: []}, readAlerts: []});
  const alert = {reader: 'following', reason: 'route', ownerId: '200', ownerUsername: 'qa.other'};
  service.message({type: 'result', state: {activeAccountId: '100', notificationLog: []}, readAlerts: [alert]});
  assert.deepEqual(received, [alert]);
});

test('clicking the alert switches to the owning account and opens request details', async () => {
  const calls = [];
  const backend = {state: {activeAccountId: '100'}, command: async (operation, args) => {
    calls.push([operation, args]); backend.state.activeAccountId = args.id; return {ok: true};
  }};
  const options = {backend, showWindow: () => calls.push('show'), send: value => calls.push(value), onBusy: () => calls.push('busy')};
  await openReadAlert({...options, alert: {reader: 'profile', ownerId: '200'}});
  assert.deepEqual(calls, ['show', ['switch_account', {id: '200'}], {type: 'open_requests'}]);
  calls.length = 0;
  backend.command = async () => ({ok: false});
  await openReadAlert({...options, alert: {reader: 'profile', ownerId: '300'}});
  assert.deepEqual(calls, ['show', 'busy']);
});
