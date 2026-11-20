import test from 'node:test';
import assert from 'node:assert/strict';
import {exportInfo, listViews, filterRows, toCsv, utcDay} from '../views.js';
import {MESSAGES} from '../i18n.js';

const row = (username, timestamp = null) => ({username, timestamp});

test('export info comes only from Instagram download names', () => {
  assert.deepEqual(exportInfo('instagram-Ada.Lovelace-2026-11-01-AbCdEf12.zip'), {account: 'ada.lovelace', date: '2026-11-01'});
  assert.deepEqual(exportInfo('my-export.zip'), {account: null, date: null});
  assert.deepEqual(exportInfo('instagram-ada-2026-13-45-AbCdEf12.zip'), {account: 'ada', date: null});
});

test('derived lists: not back, you not back, mutual and extras', () => {
  const views = Object.fromEntries(listViews({
    followers: [row('ada'), row('efe')], following: [row('ada'), row('kitap')],
    extras: {closeFriends: {rows: [row('ada')]}}, quality: {},
  }));
  assert.deepEqual(views.notBack.map(r => r.username), ['kitap']);
  assert.deepEqual(views.youNotBack.map(r => r.username), ['efe']);
  assert.deepEqual(views.mutual.map(r => r.username), ['ada']);
  assert.deepEqual(views.closeFriends.map(r => r.username), ['ada']);
  const onlyFollowers = Object.fromEntries(listViews({followers: [row('ada')], following: null, extras: {}, quality: {}}));
  assert.equal(onlyFollowers.following, null);
  assert.equal('notBack' in onlyFollowers, false);
});

test('search and date order keep undated rows last', () => {
  const rows = [row('b', 5), row('a', null), row('c', 9)];
  assert.deepEqual(filterRows(rows, '@B', 'name').map(r => r.username), ['b']);
  assert.deepEqual(filterRows(rows, '', 'newest').map(r => r.username), ['c', 'b', 'a']);
  assert.deepEqual(filterRows(rows, '', 'oldest').map(r => r.username), ['b', 'c', 'a']);
  assert.deepEqual(rows.map(r => r.username), ['b', 'a', 'c'], 'input is not mutated');
});

test('CSV quotes every cell and defuses formulas', () => {
  const csv = toCsv(['username', 'date'], [['=cmd', utcDay(1700000000)], ['a"b', utcDay(null)]]);
  assert.equal(csv, '﻿"username","date"\r\n"\'=cmd","2023-11-14"\r\n"a""b",""\r\n');
});

test('both languages define the same messages', () => {
  const keys = language => Object.keys(MESSAGES[language]).sort();
  assert.deepEqual(keys('tr'), keys('en'));
  for (const language of ['tr', 'en']) for (const [key, value] of Object.entries(MESSAGES[language]))
    assert.ok(typeof value === 'string' ? value.length : Object.keys(value).length, `${language}.${key}`);
});
