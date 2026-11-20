// Port of interface/desktop/backend/instagram_archive.py (read_relationships)
// and the comparison in export_browser.py. test/parity.test.mjs reads the same
// ZIPs with the Python reader and requires identical results, so a change here
// must be made in both places.

export const MAX_ENTRY = 16 * 1024 * 1024;
const MAX_TOTAL_ENTRIES = 64 * 1024 * 1024;
export const MAX_NAMES = 100_000;
const FOLDER = 'followers_and_following';
const ENTRY = /^(followers|following)(?:_([1-9][0-9]{0,3}))?\.(json|html)$/;
const USERNAME = /^[A-Za-z0-9._]{1,30}$/;
const MAX_STAMP = 253402300799;
export const EXTRAS = {
  recentlyUnfollowed: ['recently_unfollowed_profiles', 'recently_unfollowed_accounts'],
  pendingRequests: ['pending_follow_requests'],
  recentRequests: ['recent_follow_requests'],
  receivedRequests: ["follow_requests_you've_received", 'follow_requests_received'],
  closeFriends: ['close_friends'],
  blocked: ['blocked_profiles', 'blocked_accounts'],
  restricted: ['restricted_profiles', 'restricted_accounts'],
  hideStoryFrom: ['hide_story_from'],
  removedSuggestions: ['removed_suggestions'],
};
const EXTRA_ENTRY = /^(.+?)(?:_([1-9][0-9]{0,3}))?\.json$/;
const strict = new TextDecoder('utf-8', {fatal: true});

const isInt = value => typeof value === 'number' && Number.isInteger(value);
const isDict = value => value !== null && typeof value === 'object' && !Array.isArray(value);

export function archiveName(value) {
  return typeof value === 'string' && USERNAME.test(value) && value.replace(/^\.+|\.+$/g, '') ? value.toLowerCase() : null;
}

// Mirrors urllib.parse.urlsplit closely enough for profile links: leading
// C0/space stripped, tab/CR/LF removed, no path normalisation.
export function linkName(value) {
  if (typeof value !== 'string') return null;
  const cleaned = value.replace(/^[\u0000- ]+/, '').replace(/[\t\r\n]/g, '');
  const match = /^([A-Za-z][A-Za-z0-9+.-]*):\/\/([^/?#]*)([^?#]*)/.exec(cleaned);
  if (!match || match[1].toLowerCase() !== 'https') return null;
  if (!['instagram.com', 'www.instagram.com'].includes(match[2].toLowerCase())) return null;
  let path = match[3].replace(/^\/+|\/+$/g, '');
  if (path.startsWith('_u/')) path = path.slice(3);
  return archiveName(path);
}

function stamp(value) {
  if (!isInt(value) || value <= 0) return null;
  const seconds = value >= 1e12 ? Math.floor(value / 1000) : value;
  return seconds <= MAX_STAMP ? seconds : null;
}

function labelMember(row) {
  const labels = new Map();
  for (const item of row.label_values) {
    if (isDict(item) && typeof item.label === 'string') {
      const key = item.label.toLowerCase();
      if (!labels.has(key)) labels.set(key, truthy(item.value) ? item.value : item.href);
    }
  }
  const name = archiveName(labels.get('username')) || linkName(labels.get('url'));
  return name ? [name, stamp(row.timestamp)] : null;
}

// Python truthiness for JSON values.
function truthy(value) {
  if (Array.isArray(value)) return value.length > 0;
  if (isDict(value)) return Object.keys(value).length > 0;
  return Boolean(value);
}

export function rowMember(row) {
  if (isDict(row) && Array.isArray(row.label_values) && !Object.hasOwn(row, 'string_list_data')) return labelMember(row);
  const values = isDict(row) ? row.string_list_data : null;
  if (!Array.isArray(values) || values.length !== 1 || !isDict(values[0])) return null;
  const item = values[0];
  const names = new Set([archiveName(item.value), archiveName(row.title), linkName(item.href)].filter(Boolean));
  if (names.size !== 1) return null;
  const timestamp = item.timestamp;
  return [[...names][0], isInt(timestamp) && timestamp > 0 && timestamp <= MAX_STAMP ? timestamp : null];
}

function rowsOf(document, keys) {
  if (Array.isArray(document)) return document;
  if (isDict(document)) {
    for (const key of keys) if (Array.isArray(document[key])) return document[key];
    const lists = Object.entries(document).filter(([key, value]) => key.startsWith('relationships_') && Array.isArray(value));
    if (lists.length === 1) return lists[0][1];
  }
  throw new Error('no rows');
}

// Python's json gives float for 1.0 and int for 1; only ints count as dates.
// Where the engine exposes the source text, non-integer literals stay floats.
class Float {
  constructor(value) { this.value = value; }
}
function reviver(key, value, context) {
  return typeof value === 'number' && context?.source && /[.eE]/.test(context.source) ? new Float(value) : value;
}

export function jsonMembers(text, keys) {
  const rows = rowsOf(JSON.parse(text, reviver), keys);
  if (rows.length > MAX_NAMES) throw new Error('too many rows');
  const found = [];
  let skipped = 0;
  for (const row of rows) {
    const member = rowMember(row);
    if (member === null) skipped++;
    else found.push(member);
  }
  return [found, skipped];
}

// --- HTML exports -------------------------------------------------------

const ENTITIES = {amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' '};
function unescape(value) {
  return value.replace(/&(#x[0-9a-f]+|#[0-9]+|[a-z]+);?/gi, (whole, body) => {
    if (body[0] === '#') {
      const code = body[1] === 'x' || body[1] === 'X' ? parseInt(body.slice(2), 16) : parseInt(body.slice(1), 10);
      return code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : whole;
    }
    return ENTITIES[body.toLowerCase()] ?? whole;
  });
}

function attributes(source) {
  const result = [];
  for (const match of source.matchAll(/([^\s/>="']+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g)) {
    const raw = match[2] ?? match[3] ?? match[4];
    result.push([match[1].toLowerCase(), raw === undefined ? null : unescape(raw)]);
  }
  return result;
}

export function htmlMembers(text) {
  let mainCount = 0, inMain = false, skipped = 0;
  const names = [];
  const pattern = /<!--[\s\S]*?(?:-->|$)|<(\/?)([a-zA-Z][^\t\n\r\f />\u0000]*)([^>]*)>/g;
  let match;
  while ((match = pattern.exec(text))) {
    if (!match[2]) continue;
    const tag = match[2].toLowerCase();
    const closing = match[1] === '/';
    if (!closing && (tag === 'script' || tag === 'style')) {
      const end = text.toLowerCase().indexOf('</' + tag, pattern.lastIndex);
      pattern.lastIndex = end < 0 ? text.length : end;
      continue;
    }
    if (tag === 'main') {
      if (closing) inMain = false;
      else {
        mainCount++;
        inMain = !/\/\s*$/.test(match[3]);
      }
    } else if (tag === 'a' && !closing && inMain) {
      const attrs = attributes(match[3]);
      const links = attrs.filter(([name]) => name === 'href').map(([, value]) => linkName(value));
      if (links.length === 1 && links[0] !== null && names.length < MAX_NAMES) names.push(links[0]);
      else if (!attrs.some(([name, value]) => name === 'href' && typeof value === 'string' && !value.startsWith('https://www.instagram.com')))
        skipped++;
    }
  }
  if (mainCount !== 1 || inMain) throw new Error('not a relationship page');
  return [names.map(name => [name, null]), skipped];
}

// --- Archive -------------------------------------------------------------

function safe(entry) {
  const parts = entry.name.split('/');
  return !(entry.name.startsWith('/') || parts.includes('..') || entry.name.includes('\\') || entry.name.endsWith('/') ||
    entry.encrypted || entry.symlink);
}

function split(name) {
  const index = name.lastIndexOf('/');
  const parent = index < 0 ? '.' : name.slice(0, index);
  return {parent, parentName: parent === '.' ? '' : parent.slice(parent.lastIndexOf('/') + 1), base: name.slice(index + 1)};
}

async function readPart(zip, entry, budget) {
  if (!(entry.size > 0 && entry.size <= MAX_ENTRY) || entry.size > budget.left) throw new Error('size');
  budget.left -= entry.size;
  const text = strict.decode(await zip.bytes(entry, MAX_ENTRY));
  return text;
}

async function collect(parts, zip, budget, parse) {
  const quality = {skipped: 0, missingParts: [], unreadableParts: 0};
  const numbers = [...parts.keys()].sort((a, b) => a - b);
  const highest = numbers.at(-1);
  for (let part = 1; part <= highest; part++) if (!parts.has(part)) quality.missingParts.push(part);
  const dates = new Map();
  let readable = false;
  for (const part of numbers) {
    let found, skipped;
    try {
      [found, skipped] = parse(await readPart(zip, parts.get(part), budget));
    } catch {
      quality.unreadableParts++;
      continue;
    }
    readable = true;
    quality.skipped += skipped;
    for (let [name, timestamp] of found) {
      if (dates.size >= MAX_NAMES && !dates.has(name)) {
        quality.skipped++;
        continue;
      }
      if (dates.has(name) && dates.get(name) !== timestamp) timestamp = null;
      dates.set(name, timestamp);
    }
  }
  quality.complete = readable && !quality.skipped && !quality.missingParts.length && !quality.unreadableParts;
  const rows = readable ? [...dates.keys()].sort(codepoint).map(username => ({username, timestamp: dates.get(username)})) : null;
  return [rows, quality];
}

function codepoint(a, b) {
  return a < b ? -1 : a > b ? 1 : 0;
}

function place(map, key, part, entry) {
  if (!map.has(key)) map.set(key, new Map());
  map.get(key).set(part, entry);
}

/** Same result shape as instagram_archive.read_relationships. Throws when no list is readable. */
export async function readRelationships(zip) {
  const sources = new Map(), extras = new Map();
  for (const entry of zip.entries) {
    const {parent, parentName, base} = split(entry.name);
    if (parentName !== FOLDER || !safe(entry)) continue;
    let match = ENTRY.exec(base);
    if (match) {
      if (!sources.has(parent)) sources.set(parent, new Map());
      place(sources.get(parent), match[1] + ':' + match[3], Number(match[2] || 1), entry);
      continue;
    }
    match = EXTRA_ENTRY.exec(base);
    const key = match && Object.keys(EXTRAS).find(name => EXTRAS[name].includes(match[1]));
    if (key) {
      if (!extras.has(parent)) extras.set(parent, new Map());
      place(extras.get(parent), key, Number(match[2] || 1), entry);
    }
  }
  const weight = parent => [...sources.get(parent).values()].reduce((sum, parts) => sum + parts.size, 0);
  const parents = [...sources.keys()].sort((a, b) => weight(b) - weight(a) || a.length - b.length || codepoint(a, b));
  const parent = parents[0] ?? null;
  const chosen = sources.get(parent) ?? new Map();
  const budget = {left: MAX_TOTAL_ENTRIES};
  const result = {quality: {}, extras: {}, files: {}};
  if (parents.length > 1) result.ignoredSources = parents.length - 1;
  for (const direction of ['followers', 'following']) {
    const json = chosen.get(direction + ':json'), html = chosen.get(direction + ':html');
    if (!json && !html) {
      result[direction] = null;
      continue;
    }
    const parts = json || html;
    const [rows, quality] = json
      ? await collect(json, zip, budget, text => jsonMembers(text, ['relationships_' + direction]))
      : await collect(html, zip, budget, htmlMembers);
    quality.format = json ? 'json' : 'html';
    result[direction] = rows;
    result.quality[direction] = quality;
    result.files[direction] = parts.get(Math.min(...parts.keys())).name;
  }
  const extraParent = extras.has(parent) ? parent : extras.keys().next().value;
  for (const [key, parts] of extras.get(extraParent) ?? new Map()) {
    const [rows, quality] = await collect(parts, zip, budget, text => jsonMembers(text, []));
    if (rows !== null) {
      result.extras[key] = {rows, quality};
      result.files[key] = parts.get(Math.min(...parts.keys())).name;
    }
  }
  if (result.followers === null && result.following === null && !Object.keys(result.extras).length)
    throw new Error('no readable list');
  return result;
}

/** export_browser.ExportBrowser.compare for one direction: before and after are row lists. */
export function compareLists(beforeRows, afterRows) {
  const old = new Map(beforeRows.map(row => [row.username, row.timestamp]));
  const fresh = new Map(afterRows.map(row => [row.username, row.timestamp]));
  const added = [...fresh.keys()].filter(name => !old.has(name)).sort(codepoint);
  const removed = [...old.keys()].filter(name => !fresh.has(name)).sort(codepoint);
  // The export has no account IDs. A follow date survives a username change,
  // so a single removed/added pair with the same date is a probable rename.
  const byDate = (names, source) => {
    const groups = new Map();
    for (const name of names) {
      const at = source.get(name);
      if (at === null || at === undefined) continue;
      if (!groups.has(at)) groups.set(at, []);
      groups.get(at).push(name);
    }
    return groups;
  };
  const gone = byDate(removed, old), came = byDate(added, fresh);
  const renamed = [...gone.keys()].filter(at => came.has(at)).sort((a, b) => a - b)
    .filter(at => gone.get(at).length === 1 && came.get(at).length === 1)
    .map(at => ({from: gone.get(at)[0], to: came.get(at)[0], timestamp: at}));
  const pairedOld = new Set(renamed.map(pair => pair.from)), pairedNew = new Set(renamed.map(pair => pair.to));
  return {
    before: old.size, after: fresh.size,
    added: added.filter(name => !pairedNew.has(name)).map(username => ({username, timestamp: fresh.get(username)})),
    removed: removed.filter(name => !pairedOld.has(name)).map(username => ({username, timestamp: old.get(username)})),
    renamed,
  };
}
