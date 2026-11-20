import test from 'node:test';
import assert from 'node:assert/strict';
import {openZip, ZipError, crc32} from '../zip.js';
import {writeZip} from './zip-writer.mjs';

const blob = bytes => new Blob([bytes]);

test('reads stored and deflated members and skips directories', async () => {
  const zip = await openZip(blob(writeZip([
    {name: 'folder/', data: '', method: 0},
    {name: 'a.json', data: '{"x": 1}', method: 0},
    {name: 'b/ç.json', data: 'ğ'.repeat(5000)},
  ])));
  assert.deepEqual(zip.entries.map(entry => entry.name), ['a.json', 'b/ç.json']);
  assert.equal(await zip.text(zip.entries[0], 1000), '{"x": 1}');
  assert.equal(await zip.text(zip.entries[1], 100000), 'ğ'.repeat(5000));
});

test('rejects members over the limit, with a bad CRC, encrypted or of another method', async () => {
  const zip = await openZip(blob(writeZip([
    {name: 'big.json', data: 'x'.repeat(2000)},
    {name: 'crc.json', data: 'hello', crc: 1234},
    {name: 'secret.json', data: 'x', encrypted: true},
    {name: 'bzip.json', data: 'x', method: 12},
  ])));
  for (const [entry, limit] of [[0, 100], [1, 100], [2, 100], [3, 100]])
    await assert.rejects(zip.bytes(zip.entries[entry], limit), ZipError);
});

test('a damaged deflate stream is a ZipError, not a crash', async () => {
  const bytes = writeZip([{name: 'a.json', data: JSON.stringify(Array.from({length: 400}, (_, i) => ({i})))}]);
  for (let index = 50; index < 90; index++) bytes[index] ^= 0x5a;
  const zip = await openZip(blob(bytes));
  await assert.rejects(zip.bytes(zip.entries[0], 1e6), ZipError);
});

test('corrupt or non-zip files are rejected with ZipError', async () => {
  const good = writeZip([{name: 'a.json', data: '[]'}]);
  for (const bad of [new Uint8Array(0), new TextEncoder().encode('PK'), good.slice(0, good.length - 5),
    new Uint8Array(400).fill(7)]) {
    await assert.rejects(openZip(blob(bad)), ZipError);
  }
});

test('crc32 matches the zlib reference value', () => {
  assert.equal(crc32(new TextEncoder().encode('123456789')), 0xcbf43926);
});
