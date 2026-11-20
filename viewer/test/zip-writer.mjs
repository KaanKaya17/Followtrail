// Test helper: writes small ZIP files (stored or deflated) without dependencies.
import {deflateRawSync} from 'node:zlib';
import {crc32} from '../zip.js';

/**
 * entries: [{name, data: string|Uint8Array, method?: 0|8, crc?: number, encrypted?: boolean}]
 * Returns a Uint8Array with a standard (non-ZIP64) archive.
 */
export function writeZip(entries) {
  const locals = [], centrals = [];
  let offset = 0;
  for (const item of entries) {
    const name = Buffer.from(item.name, 'utf8');
    const data = typeof item.data === 'string' ? Buffer.from(item.data, 'utf8') : Buffer.from(item.data);
    const method = item.method ?? 8;
    const body = method === 8 ? deflateRawSync(data) : data;
    const crc = item.crc ?? crc32(data);
    const flags = 0x0800 | (item.encrypted ? 1 : 0);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(flags, 6);
    local.writeUInt16LE(method, 8);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(body.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(name.length, 26);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(flags, 8);
    central.writeUInt16LE(method, 10);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(body.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt32LE(offset, 42);
    locals.push(local, name, body);
    centrals.push(central, name);
    offset += local.length + name.length + body.length;
  }
  const directory = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  return new Uint8Array(Buffer.concat([...locals, directory, end]));
}
