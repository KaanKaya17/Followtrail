'use strict';
// A reader that Instagram stops answering in the expected way is paused by the
// worker. This module tells the user once (a Windows notification) and lets
// them send the maintainers a report that holds no account data.
const i18n = require('../web/i18n.js');

const ISSUES = 'https://github.com/KaanKaya17/Followtrail/issues/new';
const READERS = {profile: 'Profil okuması', followers: 'Takipçiler', following: 'Takip edilen'};
// Mirrors read_health.ROUTES: the request without its target ID.
const ROUTES = {
  profile: 'GET i.instagram.com/api/v1/users/{id}/info/',
  followers: 'GET i.instagram.com/api/v1/friendships/{id}/followers/',
  following: 'GET i.instagram.com/api/v1/friendships/{id}/following/',
};
const COMPATIBILITY = new Set(['format', 'identity', 'route', 'redirect']);
const FIELDS = new Set(['json', 'status', 'users', 'users.pk', 'users.username', 'user', 'user.pk',
  'user.username', 'user.follower_count', 'user.following_count']);

function readAlertNotification(alert, language = 'tr') {
  const t = (message, ...values) => i18n.translate(message, values, language);
  const title = t("Instagram okuma yolu değişmiş olabilir");
  return {
    title: alert.ownerUsername ? `${title} · @${alert.ownerUsername}` : title,
    body: t("{0}: otomatik okuma duraklatıldı. Eski kayıtların korunuyor. Ayrıntılar İstek bilgileri’nde.",
      t(READERS[alert.reader] || READERS.profile)),
  };
}

function utc(seconds) {
  if (!Number.isFinite(seconds) || seconds <= 0) return 'unknown';
  return new Date(seconds * 1000).toISOString().slice(0, 16).replace('T', ' ') + ' UTC';
}

/** The report for one diagnosed reader, or null. Built only from the worker state. */
function readReport(state, reader, version) {
  const item = state?.readHealth?.[reader];
  if (!Object.hasOwn(READERS, reader) || !item || !COMPATIBILITY.has(item.reason)) return null;
  const http = Number.isInteger(item.http) && item.http >= 100 && item.http <= 599 ? String(item.http) : 'none';
  const missing = Array.isArray(item.missing) ? item.missing.filter(field => FIELDS.has(field)) : [];
  const app = 'Windows ' + (/^[0-9]+\.[0-9]+\.[0-9]+$/.test(String(version)) ? version : 'unknown');
  const text = [
    'Read compatibility report',
    `App: ${app}`,
    `Reader: ${reader}`,
    `Request: ${ROUTES[reader]}`,
    `Reason: ${item.reason}`,
    `HTTP: ${http}`,
    `Missing fields: ${missing.length ? missing.join(', ') : 'none recorded'}`,
    `Consecutive failures: ${Number.isInteger(item.streak) ? item.streak : 0}`,
    `Automatic reads paused: ${item.paused === true ? 'yes' : 'no'}`,
    `Observed: ${utc(item.at)}`,
    '',
    'No username, account ID, cookie or response body is included.',
  ].join('\n');
  const title = `Read compatibility: ${reader} · ${item.reason} · HTTP ${http} · ${app}`;
  const url = `${ISSUES}?${new URLSearchParams({title, body: text})}`;
  return {text, title, url};
}

async function openReadAlert({alert, backend, showWindow, send, onBusy}) {
  showWindow();
  if (alert.ownerId && alert.ownerId !== backend.state?.activeAccountId) {
    const result = await backend.command('switch_account', {id: alert.ownerId});
    if (!result.ok) { onBusy(); return; }
  }
  send({type: 'open_requests'});
}

module.exports = {readAlertNotification, readReport, openReadAlert, ROUTES};
