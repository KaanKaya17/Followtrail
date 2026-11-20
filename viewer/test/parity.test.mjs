// The browser viewer must read exports exactly like the desktop app. This test
// builds seeded random exports, reads each one with the Python reader
// (python_reference.py) and with relationships.js, and requires equal results.
//
// PYTHON selects the interpreter (default: python). PARITY_CASES sets the
// number of random exports (default 120); PARITY_SEED reproduces one case.
import test from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdtempSync, writeFileSync, readFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {openZip} from '../zip.js';
import {readRelationships, compareLists} from '../relationships.js';
import {writeZip} from './zip-writer.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const python = process.env.PYTHON || 'python';
const COUNT = Number(process.env.PARITY_CASES || 120);
const ONLY = process.env.PARITY_SEED;
const F = 'connections/followers_and_following/';

function random(seed) {
  let state = seed >>> 0 || 1;
  const next = () => {
    state ^= state << 13; state >>>= 0;
    state ^= state >>> 17;
    state ^= state << 5; state >>>= 0;
    return state / 4294967296;
  };
  return {
    next,
    int: (low, high) => low + Math.floor(next() * (high - low + 1)),
    pick: list => list[Math.floor(next() * list.length)],
    chance: probability => next() < probability,
  };
}

const NAMES = ['ada', 'Ada', 'efe', 'mert.eski', 'mert.yeni', 'selin_', 'kitap', 'a.b.c', 'zeynep99', 'can', 'irem',
  '.', '..x', 'x'.repeat(30), 'x'.repeat(31), 'bad name', 'ğül', '', 'ADA'];

function timestampText(r) {
  return r.pick(['1700000000', '1700000000000', '1690000000', '0', '-5', '253402300799', '253402300800',
    '1700000000.0', '1.7e9', 'true', 'null', '"1700000000"', '99999999999999']);
}

function rowText(r) {
  const name = r.pick(NAMES);
  const other = r.chance(0.8) ? name : r.pick(NAMES);
  const href = r.pick([`https://www.instagram.com/${name}`, `https://www.instagram.com/_u/${name}/`,
    `https://instagram.com/${name}?igsh=x`, `http://www.instagram.com/${name}`, `https://evil.com/${name}`,
    `https://www.instagram.com/${name}/extra`, ` https://www.instagram.com/${name}`, 'not a url']);
  const shape = r.int(0, 9);
  const ts = timestampText(r);
  if (shape <= 3) return `{"title": ${JSON.stringify(r.chance(0.5) ? other : '')}, "string_list_data": [{"href": ${JSON.stringify(href)}, "value": ${JSON.stringify(name)}, "timestamp": ${ts}}]}`;
  if (shape === 4) return `{"title": ${JSON.stringify(name)}, "string_list_data": [{"href": ${JSON.stringify(href)}, "timestamp": ${ts}}]}`;
  if (shape <= 6) return `{"timestamp": ${ts}, "label_values": [{"label": ${JSON.stringify(r.pick(['Username', 'username', 'USERNAME', 'Name']))}, "value": ${JSON.stringify(r.chance(0.8) ? name : '')}, "href": ${JSON.stringify(href)}}, {"label": "URL", "href": ${JSON.stringify(href)}}]}`;
  if (shape === 7 && r.chance(0.5)) return `{"timestamp": ${ts}, "label_values": [{"label": "Username", "value": ${JSON.stringify(name)}}, {"label": "username", "value": ${JSON.stringify(other === name ? r.pick(NAMES) : other)}}]}`;
  if (shape === 7) return `{"string_list_data": [{"value": ${JSON.stringify(name)}}, {"value": "two"}]}`;
  if (shape === 8) return r.pick(['null', '[]', '"ada"', '{"string_list_data": "x"}', '{}', '{"label_values": [], "string_list_data": []}']);
  return `{"title": ${JSON.stringify(name)}, "string_list_data": [{"value": ${JSON.stringify(other)}, "timestamp": ${ts}}]}`;
}

function documentText(r, direction) {
  const rows = Array.from({length: r.int(0, 14)}, () => rowText(r)).join(',\n');
  const key = direction ? `relationships_${direction}` : r.pick(['relationships_close_friends', 'relationships_x']);
  return r.pick([
    `[${rows}]`, `{"${key}": [${rows}]}`, `{"${key}": [${rows}], "relationships_other": []}`, `{"other": [${rows}]}`,
    `﻿[${rows}]`, `[${rows}`, '', '{"a": 1}',
  ]);
}

function htmlText(r) {
  const links = Array.from({length: r.int(0, 8)}, () => {
    const name = r.pick(NAMES);
    return r.pick([
      `<div><a href="https://www.instagram.com/${name}">${name}</a></div>`,
      `<div><a target="_blank" href='https://www.instagram.com/_u/${name}'>x</a></div>`,
      `<div><a href="https://www.instagram.com/explore/tags/x/">x</a></div>`,
      '<div><a href="https://help.instagram.com/123">help</a></div>', '<div><a>no link</a></div>',
      `<div><A HREF="https://www.instagram.com/${name}?a=1&amp;b=2">x</A></div>`,
    ]);
  }).join('');
  return r.pick([
    `<html><body><header><a href="https://www.instagram.com/ignored/">n</a></header><main>${links}</main></body></html>`,
    `<html><body><main>${links}</main><main></main></body></html>`, `<html><body><main>${links}</body></html>`,
    `<html><body>${links}</body></html>`, `<!-- <main> --><main>${links}<script>"<main>"</script></main>`,
  ]);
}

function exportEntries(r) {
  const entries = [];
  const parent = r.pick([F, F, F, 'followers_and_following/', 'old/' + F, 'connections/other/']);
  const add = (name, data, extra = {}) => entries.push({name, data, ...extra});
  for (const direction of ['followers', 'following']) {
    if (r.chance(0.15)) continue;
    const parts = r.chance(0.7) ? [1] : r.pick([[1, 2], [1, 3], [2], [1, 2, 3]]);
    for (const part of parts) {
      const stem = direction === 'following' && part === 1 && r.chance(0.7) ? direction : `${direction}_${part}`;
      if (r.chance(0.2)) add(parent + stem + '.html', htmlText(r));
      else add(parent + stem + '.json', documentText(r, direction));
    }
    if (r.chance(0.1)) add(parent + direction + '_1.html', htmlText(r));
  }
  for (const stem of ['close_friends', 'recently_unfollowed_profiles', 'pending_follow_requests_2', "follow_requests_you've_received", 'blocked_accounts', 'something_new'])
    if (r.chance(0.3)) add(parent + stem + '.json', documentText(r, null));
  if (r.chance(0.15)) add('other_export/' + F + 'followers_1.json', documentText(r, 'followers'));
  if (r.chance(0.08)) add(parent + 'followers_9.json', new Uint8Array([0xff, 0xfe, 0x00, 0x41]));
  if (r.chance(0.06)) add(parent + 'close_friends.json', '[]', {crc: 1});
  if (r.chance(0.05)) add(parent + 'following.json', '', {method: 0});
  return entries;
}

test('the browser reader matches the desktop Python reader', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'TrackerQA-viewer-'));
  try {
    const seeds = ONLY ? [Number(ONLY)] : Array.from({length: COUNT}, (_, index) => index + 1);
    const cases = [];
    for (const seed of seeds) {
      const r = random(seed * 2654435761);
      const zip = path.join(directory, `case-${seed}.zip`);
      writeFileSync(zip, writeZip(exportEntries(r)));
      let other = null;
      if (r.chance(0.5)) {
        other = path.join(directory, `case-${seed}-other.zip`);
        writeFileSync(other, writeZip(exportEntries(r)));
      }
      cases.push({seed, zip, other});
    }
    // Hand-made pairs for what random data rarely produces: a probable rename,
    // an ambiguous same-date pair, and a list missing on one side.
    const follower = (name, at) => ({title: '', string_list_data: [{href: `https://www.instagram.com/${name}`, value: name, timestamp: at}]});
    const crafted = [
      [[follower('ada', 1), follower('mert.eski', 5), follower('efe', 6)], [follower('ada', 1), follower('mert.yeni', 5), follower('selin', 7)]],
      [[follower('a', 5), follower('b', 5)], [follower('c', 5), follower('d', 5)]],
      [[follower('x', 9)], null],
    ];
    if (!ONLY) for (const [index, [before, after]] of crafted.entries()) {
      const zip = path.join(directory, `crafted-${index}.zip`), other = path.join(directory, `crafted-${index}-other.zip`);
      writeFileSync(zip, writeZip([{name: F + 'followers_1.json', data: JSON.stringify(before)},
        {name: F + 'following.json', data: JSON.stringify({relationships_following: [follower('kitap', 3)]})}]));
      writeFileSync(other, writeZip(after ? [{name: F + 'followers_1.json', data: JSON.stringify(after)}]
        : [{name: F + 'following.json', data: '{"relationships_following": []}'}]));
      cases.push({seed: `crafted-${index}`, zip, other});
    }
    const list = path.join(directory, 'cases.json');
    writeFileSync(list, JSON.stringify(cases));
    const expected = JSON.parse(execFileSync(python, ['-X', 'utf8', path.join(here, 'python_reference.py'), list],
      {encoding: 'utf8', maxBuffer: 256 * 1024 * 1024}));
    for (const [index, item] of cases.entries()) {
      const label = `PARITY_SEED=${item.seed}`;
      const read = async file => {
        try {
          return await readRelationships(await openZip(new Blob([readFileSync(file)])));
        } catch {
          return {error: true};
        }
      };
      const mine = await read(item.zip);
      assert.deepEqual(mine, expected[index].relationships, `${label}: relationships differ`);
      if (item.other) {
        const theirs = await read(item.other);
        let comparison;
        if (mine.error || theirs.error) comparison = {error: true};
        else {
          const directions = {}, missing = {};
          for (const direction of ['followers', 'following']) {
            const before = mine[direction], after = theirs[direction];
            if (before === null || after === null) {
              missing[direction] = before === null && after === null ? 'both' : before === null ? 'first' : 'second';
              continue;
            }
            directions[direction] = {...compareLists(before, after),
              complete: mine.quality[direction].complete && theirs.quality[direction].complete};
          }
          comparison = {missing, directions};
        }
        assert.deepEqual(comparison, expected[index].comparison, `${label}: comparison differs`);
      }
    }
  } finally {
    rmSync(directory, {recursive: true, force: true});
  }
});
