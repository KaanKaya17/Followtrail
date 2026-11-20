"""Read relationship lists from a user-selected Instagram ZIP.

Reading is tolerant: an unreadable row is skipped and counted, an unreadable
file marks only its own list as incomplete, and one list never hides the
other. The result always says what was skipped, so a partial list is never
presented as complete.
"""
import json
import re
import stat
from html.parser import HTMLParser
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit
from zipfile import BadZipFile, ZipFile

MAX_ZIP = 8 * 1024**3
MAX_ENTRY = 16 * 1024 * 1024
MAX_TOTAL_ENTRIES = 64 * 1024 * 1024
MAX_NAMES = 100_000
FOLDER = 'followers_and_following'
ENTRY = re.compile(r'(followers|following)(?:_([1-9][0-9]{0,3}))?\.(json|html)')
USERNAME = re.compile(r'[A-Za-z0-9._]{1,30}')
INVALID = 'Instagram ZIP okunamadı. Dosyada HTML veya JSON biçiminde takipçi ya da takip edilen listesi bulunmalı.'
# Other lists Instagram puts next to followers/following. Names vary between
# export versions; each key lists the file stems that carry the same list.
EXTRAS = {
    'recentlyUnfollowed': ('recently_unfollowed_profiles', 'recently_unfollowed_accounts'),
    'pendingRequests': ('pending_follow_requests',),
    'recentRequests': ('recent_follow_requests',),
    'receivedRequests': ("follow_requests_you've_received", 'follow_requests_received'),
    'closeFriends': ('close_friends',),
    'blocked': ('blocked_profiles', 'blocked_accounts'),
    'restricted': ('restricted_profiles', 'restricted_accounts'),
    'hideStoryFrom': ('hide_story_from',),
    'removedSuggestions': ('removed_suggestions',),
}
EXTRA_ENTRY = re.compile(r'(.+?)(?:_([1-9][0-9]{0,3}))?\.json')
# Files Instagram puts in the relationships folder that are not account lists.
NOT_LISTS = {'following_hashtags'}
LIST_FILE = re.compile(r"([A-Za-z0-9_'.-]{1,80}?)(?:_[1-9][0-9]{0,3})?\.(json|html)")
MAX_NOTES = 20


def format_notes(names):
    """Signs that Instagram changed the relationship export layout.

    Returns file names only, never contents: list files in the relationships
    folder that this reader does not recognise, and follower or following
    files found outside that folder (for example after a folder rename).
    """
    known = {stem for stems in EXTRAS.values() for stem in stems} | NOT_LISTS
    unknown, elsewhere = set(), set()
    for name in names:
        relative = PurePosixPath(name)
        if '..' in relative.parts or relative.is_absolute() or '\\' in name:
            continue
        if ENTRY.fullmatch(relative.name):
            if relative.parent.name != FOLDER and len(str(relative.parent)) <= 160:
                elsewhere.add(str(relative.parent))
            continue
        match = LIST_FILE.fullmatch(relative.name)
        if relative.parent.name == FOLDER and match and match[1] not in known:
            unknown.add(match[1] + '.' + match[2])
    notes = {}
    if unknown:
        notes['unrecognized'] = sorted(unknown)[:MAX_NOTES]
    if elsewhere:
        notes['elsewhere'] = sorted(elsewhere)[:MAX_NOTES]
    return notes


def archive_name(value):
    return value.lower() if isinstance(value, str) and USERNAME.fullmatch(value) and value.strip('.') else None


def link_name(value):
    if not isinstance(value, str):
        return None
    url = urlsplit(value)
    if url.scheme != 'https' or url.netloc.lower() not in ('instagram.com', 'www.instagram.com'):
        return None
    segments = url.path.strip('/').removeprefix('_u/')
    return archive_name(segments)


class RelationshipHtml(HTMLParser):
    def __init__(self):
        super().__init__()
        self.main_count = 0
        self.in_main = False
        self.names = []
        self.skipped = 0

    def handle_starttag(self, tag, attrs):
        if tag == 'main':
            self.main_count += 1
            self.in_main = True
        elif tag == 'a' and self.in_main:
            names = [link_name(value) for attr, value in attrs if attr == 'href']
            if len(names) == 1 and names[0] is not None and len(self.names) < MAX_NAMES:
                self.names.append(names[0])
            elif not any(attr == 'href' and isinstance(value, str) and not value.startswith('https://www.instagram.com')
                         for attr, value in attrs):
                # Help or navigation links are not members; malformed profile links are.
                self.skipped += 1

    def handle_endtag(self, tag):
        if tag == 'main':
            self.in_main = False


def html_members(text):
    parser = RelationshipHtml()
    parser.feed(text)
    parser.close()
    if parser.main_count != 1 or parser.in_main:
        raise ValueError()
    return [(name, None) for name in parser.names], parser.skipped


def rows_of(document, keys):
    if isinstance(document, list):
        return document
    if isinstance(document, dict):
        for key in keys:
            if isinstance(document.get(key), list):
                return document[key]
        lists = [value for key, value in document.items() if key.startswith('relationships_') and isinstance(value, list)]
        if len(lists) == 1:
            return lists[0]
    raise ValueError()


def stamp(value):
    if type(value) is not int or value <= 0:
        return None
    value = value // 1000 if value >= 10**12 else value
    return value if value <= 253402300799 else None


def label_member(row):
    """Current exports write the extra lists as label/value rows with a row timestamp."""
    labels = {}
    for item in row['label_values']:
        if isinstance(item, dict) and isinstance(item.get('label'), str):
            labels.setdefault(item['label'].casefold(), item.get('value') or item.get('href'))
    name = archive_name(labels.get('username')) or link_name(labels.get('url'))
    return (name, stamp(row.get('timestamp'))) if name else None


def row_member(row):
    """(username, timestamp) for one export row, or None when it cannot be trusted."""
    if isinstance(row, dict) and isinstance(row.get('label_values'), list) and 'string_list_data' not in row:
        return label_member(row)
    values = row.get('string_list_data') if isinstance(row, dict) else None
    if not isinstance(values, list) or len(values) != 1 or not isinstance(values[0], dict):
        return None
    item = values[0]
    names = {name for name in (archive_name(item.get('value')), archive_name(row.get('title')),
                               link_name(item.get('href'))) if name}
    if len(names) != 1:
        return None
    timestamp = item.get('timestamp')
    return names.pop(), timestamp if type(timestamp) is int and 0 < timestamp <= 253402300799 else None


def json_members(text, keys):
    rows = rows_of(json.loads(text), keys)
    if len(rows) > MAX_NAMES:
        raise ValueError()
    found, skipped = [], 0
    for row in rows:
        member = row_member(row)
        if member is None:
            skipped += 1
        else:
            found.append(member)
    return found, skipped


class InvalidArchive(ValueError):
    pass


def read_archive(path):
    from export_browser import ExportBrowser
    try:
        path, stream, archive, _ = ExportBrowser.open_zip(path)
        try:
            return {'fileName': path.name, **read_relationships(archive)}
        finally:
            archive.close()
            stream.close()
    except (OSError, ValueError, TypeError, RuntimeError, NotImplementedError, BadZipFile, RecursionError) as exc:
        raise InvalidArchive(INVALID) from exc


def safe(item):
    relative = PurePosixPath(item.filename)
    return not (relative.is_absolute() or '..' in relative.parts or '\\' in item.filename or item.is_dir()
                or item.flag_bits & 1 or stat.S_ISLNK(item.external_attr >> 16))


def read_part(archive, item, budget):
    if not 0 < item.file_size <= MAX_ENTRY or item.file_size > budget[0]:
        raise ValueError()
    budget[0] -= item.file_size
    with archive.open(item) as content:
        raw = content.read(MAX_ENTRY + 1)
    if len(raw) != item.file_size:
        raise ValueError()
    return raw.decode('utf-8-sig')


def collect(parts, archive, budget, parse):
    """Merge numbered parts. Returns (rows, quality) or (None, quality) when nothing was readable."""
    quality = {'skipped': 0, 'missingParts': [], 'unreadableParts': 0}
    expected = range(1, max(parts) + 1)
    quality['missingParts'] = [part for part in expected if part not in parts]
    dates, readable = {}, False
    for part in sorted(parts):
        try:
            found, skipped = parse(read_part(archive, parts[part], budget))
        except (OSError, ValueError, TypeError, RuntimeError, NotImplementedError, BadZipFile, RecursionError, UnicodeError):
            quality['unreadableParts'] += 1
            continue
        readable = True
        quality['skipped'] += skipped
        for name, timestamp in found:
            if len(dates) >= MAX_NAMES and name not in dates:
                quality['skipped'] += 1
                continue
            if name in dates and dates[name] != timestamp:
                timestamp = None
            dates[name] = timestamp
    quality['complete'] = readable and not quality['skipped'] and not quality['missingParts'] and not quality['unreadableParts']
    rows = [{'username': name, 'timestamp': dates[name]} for name in sorted(dates)] if readable else None
    return rows, quality


def read_relationships(archive):
    sources = {}
    extras = {}
    for item in archive.infolist():
        relative = PurePosixPath(item.filename)
        if relative.parent.name != FOLDER or not safe(item):
            continue
        match = ENTRY.fullmatch(relative.name)
        if match:
            direction, part, format_name = match.groups()
            sources.setdefault(str(relative.parent), {}).setdefault((direction, format_name), {})[int(part or 1)] = item
            continue
        match = EXTRA_ENTRY.fullmatch(relative.name)
        key = next((key for key, stems in EXTRAS.items() if match and match[1] in stems), None)
        if key:
            extras.setdefault(str(relative.parent), {}).setdefault(key, {})[int(match[2] or 1)] = item
    # A ZIP that holds two exports keeps the folder with the most list files.
    parents = sorted(sources, key=lambda parent: (-sum(len(p) for p in sources[parent].values()), len(parent), parent))
    parent = parents[0] if parents else None
    chosen = sources.get(parent, {})
    budget = [MAX_TOTAL_ENTRIES]
    result = {'quality': {}, 'extras': {}, 'files': {}}
    if len(parents) > 1:
        result['ignoredSources'] = len(parents) - 1
    for direction in ('followers', 'following'):
        json_parts, html_parts = chosen.get((direction, 'json')), chosen.get((direction, 'html'))
        if not json_parts and not html_parts:
            result[direction] = None
            continue
        if json_parts:
            rows, quality = collect(json_parts, archive, budget,
                                    lambda text, d=direction: json_members(text, ('relationships_' + d,)))
            quality['format'] = 'json'
        else:
            rows, quality = collect(html_parts, archive, budget, html_members)
            quality['format'] = 'html'
        result[direction] = rows
        result['quality'][direction] = quality
        result['files'][direction] = (json_parts or html_parts)[min(json_parts or html_parts)].filename
    for key, parts in extras.get(parent if parent in extras else next(iter(extras), None), {}).items():
        rows, quality = collect(parts, archive, budget, lambda text: json_members(text, ()))
        if rows is not None:
            result['extras'][key] = {'rows': rows, 'quality': quality}
            result['files'][key] = parts[min(parts)].filename
    # Nothing readable at all is an invalid relationship archive; a single
    # unreadable list is reported through its quality instead.
    if result['followers'] is None and result['following'] is None and not result['extras']:
        raise ValueError()
    return result
