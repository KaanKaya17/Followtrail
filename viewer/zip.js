// Minimal ZIP reader for a user-selected File or Blob. No dependencies and no
// network: the central directory is parsed from slices of the file, and a
// member is inflated with the platform's DecompressionStream only when read.
// Supports stored and deflated members and ZIP64 (Instagram exports can be
// several gigabytes). Encrypted members and other methods are reported as
// unreadable instead of throwing for the whole archive.

export const MAX_ARCHIVE = 8 * 1024 ** 3;
const MAX_DIRECTORY = 32 * 1024 ** 2;
const MAX_ENTRIES = 50_000;
const EOCD = 0x06054b50;
const ZIP64_LOCATOR = 0x07064b50;
const ZIP64_EOCD = 0x06064b50;
const CENTRAL = 0x02014b50;
const LOCAL = 0x04034b50;

export class ZipError extends Error {}

async function bytes(blob, start, end) {
  if (start < 0 || end > blob.size || start > end) throw new ZipError('out of range');
  return new Uint8Array(await blob.slice(start, end).arrayBuffer());
}

function view(array) {
  return new DataView(array.buffer, array.byteOffset, array.byteLength);
}

function u64(data, offset) {
  const value = data.getBigUint64(offset, true);
  if (value > BigInt(Number.MAX_SAFE_INTEGER)) throw new ZipError('size too large');
  return Number(value);
}

async function endRecord(blob) {
  const tail = Math.min(blob.size, 22 + 65535);
  const data = await bytes(blob, blob.size - tail, blob.size);
  const dv = view(data);
  for (let index = data.length - 22; index >= 0; index--) {
    if (dv.getUint32(index, true) !== EOCD) continue;
    const commentLength = dv.getUint16(index + 20, true);
    if (index + 22 + commentLength !== data.length) continue;
    let entries = dv.getUint16(index + 10, true);
    let size = dv.getUint32(index + 12, true);
    let offset = dv.getUint32(index + 16, true);
    const position = blob.size - tail + index;
    if ((entries === 0xffff || size === 0xffffffff || offset === 0xffffffff) && position >= 20) {
      const locator = view(await bytes(blob, position - 20, position));
      if (locator.getUint32(0, true) === ZIP64_LOCATOR) {
        const recordOffset = u64(locator, 8);
        const record = view(await bytes(blob, recordOffset, recordOffset + 56));
        if (record.getUint32(0, true) !== ZIP64_EOCD) throw new ZipError('bad zip64 record');
        entries = u64(record, 32);
        size = u64(record, 40);
        offset = u64(record, 48);
      }
    }
    return {entries, size, offset};
  }
  throw new ZipError('not a zip');
}

function zip64Extra(dv, start, length, entry) {
  let cursor = start;
  while (cursor + 4 <= start + length) {
    const id = dv.getUint16(cursor, true);
    const size = dv.getUint16(cursor + 2, true);
    if (id === 0x0001) {
      let field = cursor + 4;
      for (const key of ['size', 'compressedSize', 'offset']) {
        if (entry[key] === 0xffffffff && field + 8 <= cursor + 4 + size) {
          entry[key] = u64(dv, field);
          field += 8;
        }
      }
    }
    cursor += 4 + size;
  }
}

const decoder = new TextDecoder('utf-8');

export async function openZip(blob) {
  if (!(blob instanceof Blob) || blob.size <= 0 || blob.size > MAX_ARCHIVE) throw new ZipError('archive size');
  const end = await endRecord(blob);
  if (end.size > MAX_DIRECTORY || end.entries > MAX_ENTRIES || end.offset + end.size > blob.size)
    throw new ZipError('archive directory');
  const directory = await bytes(blob, end.offset, end.offset + end.size);
  const dv = view(directory);
  const entries = [];
  let cursor = 0;
  while (cursor + 46 <= directory.length && entries.length < end.entries) {
    if (dv.getUint32(cursor, true) !== CENTRAL) throw new ZipError('bad central directory');
    const flags = dv.getUint16(cursor + 8, true);
    const nameLength = dv.getUint16(cursor + 28, true);
    const extraLength = dv.getUint16(cursor + 30, true);
    const commentLength = dv.getUint16(cursor + 32, true);
    const nameStart = cursor + 46;
    if (nameStart + nameLength + extraLength + commentLength > directory.length) throw new ZipError('bad central directory');
    const entry = {
      name: decoder.decode(directory.subarray(nameStart, nameStart + nameLength)),
      method: dv.getUint16(cursor + 10, true),
      crc: dv.getUint32(cursor + 16, true),
      encrypted: Boolean(flags & 1),
      compressedSize: dv.getUint32(cursor + 20, true),
      size: dv.getUint32(cursor + 24, true),
      offset: dv.getUint32(cursor + 42, true),
      // Unix mode bits; a symbolic link member is never followed.
      symlink: ((dv.getUint32(cursor + 38, true) >>> 16) & 0o170000) === 0o120000,
    };
    zip64Extra(dv, nameStart + nameLength, extraLength, entry);
    if (!entry.name.endsWith('/')) entries.push(entry);
    cursor = nameStart + nameLength + extraLength + commentLength;
  }
  if (!entries.length) throw new ZipError('empty archive');
  return {
    entries,
    /** The member's bytes, CRC-checked. Throws ZipError for anything unreadable or over `limit` bytes. */
    bytes(entry, limit) {
      return read(blob, entry, limit);
    },
    /** The member as text (invalid UTF-8 becomes U+FFFD). */
    async text(entry, limit) {
      return decoder.decode(await read(blob, entry, limit));
    },
  };
}

async function read(blob, entry, limit) {
  if (entry.encrypted || ![0, 8].includes(entry.method)) throw new ZipError('unsupported member');
  if (entry.size > limit || entry.compressedSize > blob.size) throw new ZipError('member size');
  const header = view(await bytes(blob, entry.offset, entry.offset + 30));
  if (header.getUint32(0, true) !== LOCAL) throw new ZipError('bad local header');
  const start = entry.offset + 30 + header.getUint16(26, true) + header.getUint16(28, true);
  const raw = blob.slice(start, start + entry.compressedSize);
  if (raw.size !== entry.compressedSize) throw new ZipError('truncated member');
  if (entry.method === 0) {
    if (entry.compressedSize !== entry.size) throw new ZipError('member size');
    return checked(new Uint8Array(await raw.arrayBuffer()), entry);
  }
  const output = new Uint8Array(entry.size);
  let length = 0;
  try {
    const reader = raw.stream().pipeThrough(new DecompressionStream('deflate-raw')).getReader();
    for (;;) {
      const {done, value} = await reader.read();
      if (done) break;
      if (length + value.length > entry.size) {
        await reader.cancel();
        throw new ZipError('member size');
      }
      output.set(value, length);
      length += value.length;
    }
  } catch (error) {
    throw error instanceof ZipError ? error : new ZipError('damaged member');
  }
  if (length !== entry.size) throw new ZipError('member size');
  return checked(output, entry);
}

const CRC_TABLE = Array.from({length: 256}, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

export function crc32(data) {
  let crc = 0xffffffff;
  for (let index = 0; index < data.length; index++) crc = CRC_TABLE[(crc ^ data[index]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

// Python's zipfile rejects a member whose CRC does not match; so does this reader.
function checked(data, entry) {
  if (crc32(data) !== entry.crc) throw new ZipError('crc mismatch');
  return data;
}
