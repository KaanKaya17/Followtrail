"""Read only relationship entries from a user-selected Instagram ZIP."""
import json
import re
import stat
from html.parser import HTMLParser
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit
from zipfile import BadZipFile, ZipFile

from network import AppFailure

MAX_ZIP = 512 * 1024 * 1024
MAX_ENTRY = 16 * 1024 * 1024
MAX_TOTAL_ENTRIES = 64 * 1024 * 1024
MAX_NAMES = 100_000
ENTRY = re.compile(r'(followers|following)(?:_([1-9][0-9]{0,3}))?\.(json|html)')
USERNAME = re.compile(r'[A-Za-z0-9._]{1,30}')
INVALID = 'Instagram ZIP okunamadı. Dosyada HTML veya JSON biçiminde takipçi ya da takip edilen listesi bulunmalı.'


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

    def handle_starttag(self, tag, attrs):
        if tag == 'main':
            self.main_count += 1
            self.in_main = True
        elif tag == 'a' and self.in_main:
            names = [link_name(value) for attr, value in attrs if attr == 'href']
            if len(names) != 1 or names[0] is None or len(self.names) >= MAX_NAMES:
                raise ValueError()
            self.names.append(names[0])

    def handle_endtag(self, tag):
        if tag == 'main':
            self.in_main = False


def html_members(text):
    parser = RelationshipHtml()
    parser.feed(text)
    parser.close()
    if parser.main_count != 1 or parser.in_main:
        raise ValueError()
    return parser.names


def members(document, direction):
    rows = document.get('relationships_' + direction) if isinstance(document, dict) else document
    if not isinstance(rows, list) or len(rows) > MAX_NAMES:
        raise ValueError()
    for row in rows:
        values = row.get('string_list_data') if isinstance(row, dict) else None
        if not isinstance(values, list) or len(values) != 1 or not isinstance(values[0], dict):
            raise ValueError()
        item = values[0]
        names = {name for name in (archive_name(item.get('value')), archive_name(row.get('title')),
                                   link_name(item.get('href'))) if name}
        if len(names) != 1:
            raise ValueError()
        yield names.pop()


def read_archive(path):
    try:
        return _read_archive(Path(path))
    except (OSError, ValueError, TypeError, RuntimeError, NotImplementedError, BadZipFile, RecursionError) as exc:
        raise AppFailure('instagram_archive', INVALID) from exc


def _read_archive(path):
    with path.open('rb') as stream:
        if path.suffix.lower() != '.zip' or not 0 < stream.seek(0, 2) <= MAX_ZIP:
            raise ValueError()
        with ZipFile(stream) as archive:
            selected = {'followers': {}, 'following': {}}
            parents = set()
            formats = set()
            size = 0
            for item in archive.infolist():
                relative = PurePosixPath(item.filename)
                match = ENTRY.fullmatch(relative.name)
                if not match or relative.parent.name != 'followers_and_following':
                    continue
                if relative.is_absolute() or '..' in relative.parts or '\\' in item.filename or item.is_dir():
                    raise ValueError()
                if item.flag_bits & 1 or stat.S_ISLNK(item.external_attr >> 16):
                    raise ValueError()
                direction, part, format_name = match.groups()
                formats.add(format_name)
                part = int(part or 1)
                if part in selected[direction] or not 0 < item.file_size <= MAX_ENTRY:
                    raise ValueError()
                size += item.file_size
                if size > MAX_TOTAL_ENTRIES:
                    raise ValueError()
                selected[direction][part] = item
                parents.add(relative.parent)
            if len(parents) != 1 or len(formats) != 1:
                raise ValueError()
            format_name = formats.pop()
            result = {}
            for direction, parts in selected.items():
                if not parts:
                    result[direction] = None
                    continue
                if sorted(parts) != list(range(1, len(parts) + 1)):
                    raise ValueError()
                names = set()
                row_count = 0
                for part in sorted(parts):
                    with archive.open(parts[part]) as content:
                        raw = content.read(MAX_ENTRY + 1)
                    if len(raw) != parts[part].file_size:
                        raise ValueError()
                    text = raw.decode('utf-8-sig')
                    parsed = html_members(text) if format_name == 'html' else members(json.loads(text), direction)
                    for name in parsed:
                        row_count += 1
                        if row_count > MAX_NAMES:
                            raise ValueError()
                        names.add(name)
                result[direction] = sorted(names)
            return {'fileName': path.name, **result}
