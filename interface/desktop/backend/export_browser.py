"""On-demand access to one user-selected export. Never extracts archive paths."""
import base64
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import os
import re
from tempfile import NamedTemporaryFile
from uuid import uuid4
from zipfile import BadZipFile, ZipFile, _EndRecData, _ECD_SIZE, _ECD_ENTRIES_TOTAL

from instagram_archive import read_relationships

MAX_ARCHIVE = 8 * 1024**3
MAX_DIRECTORY = 32 * 1024**2
MAX_ENTRIES = 50_000
MAX_JSON = 24 * 1024**2
MAX_PREVIEW = 32 * 1024**2
MAX_COPY = 512 * 1024**2
PAGE = 24_000
ARCHIVE_ERRORS = (OSError, ValueError, TypeError, AttributeError, RuntimeError, NotImplementedError, BadZipFile, RecursionError)
SUMMARY_FILES = {
    'profileChanges': ('personal_information/personal_information/', 'profile_changes'),
    'likes': ('your_instagram_activity/likes/', 'liked_posts'),
    'saved': ('your_instagram_activity/saved/', 'saved_posts'),
    'stories': ('your_instagram_activity/media/', 'stories'),
    'contacts': ('connections/contacts/', 'synced_contacts'),
}
TREND_METRICS = ('Followers', 'Accounts Reached', 'Accounts reached', 'Content Interactions')
MEDIA = {
    '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp',
    '.gif': 'image/gif', '.mp4': 'video/mp4', '.webm': 'video/webm', '.ogg': 'audio/ogg',
    '.mp3': 'audio/mpeg', '.m4a': 'audio/mp4', '.wav': 'audio/wav',
}


def readable(value):
    if not isinstance(value, str) or not any(marker in value for marker in ('Ã', 'Ä', 'Å', 'Â', 'â', 'ð')):
        return value
    for encoding in ('latin1', 'cp1252'):
        try:
            return value.encode(encoding).decode('utf-8')
        except UnicodeError:
            continue
    # Mixed records can contain both valid Unicode and legacy UTF-8 bytes.
    return re.sub(r'[ÃÄÅÂâð][\u0080-\u00bf]{1,3}', lambda m: _repair_run(m[0]), value)


def _repair_run(value):
    try:
        return value.encode('latin1').decode('utf-8')
    except UnicodeError:
        return value


def record_card(row):
    if not isinstance(row, dict):
        return {'title': str(row)[:240], 'fields': [], 'when': None}
    fields = {}
    dates = []
    attachments = []
    def add(label, value):
        if isinstance(value, dict):
            for key in ('timestamp', 'timestamp_value', 'timestamp_ms'):
                if type(value.get(key)) is int:
                    dates.append(value[key])
            value = next((value[key] for key in ('value', 'text', 'href', 'url', 'string_value', 'int_value', 'bool_value') if value.get(key) not in (None, '')), None)
        if isinstance(value, (str, int, float, bool)) and value != '' and len(fields) < 18:
            fields.setdefault(label, readable(str(value))[:8000 if label == 'content' else 800])
    for label, value in (row.get('string_map_data') or {}).items() if isinstance(row.get('string_map_data'), dict) else ():
        add(label, value)
    for item in row['label_values'] if isinstance(row.get('label_values'), list) else ():
        if isinstance(item, dict) and isinstance(item.get('label'), str) and item['label']:
            add(item['label'], item)
    for item in row['string_list_data'] if isinstance(row.get('string_list_data'), list) else ():
        if isinstance(item, dict):
            add('Username', item)
            add('URL', item.get('href'))
    for key in ('timestamp_ms', 'creation_timestamp', 'timestamp'):
        if type(row.get(key)) is int:
            dates.insert(0, row[key])
            break
    for key in ('sender_name', 'content', 'title', 'name', 'uri'):
        add(key, row.get(key))
    if not fields:
        for key, value in row.items():
            if key not in ('timestamp', 'fbid'):
                add(key, value)
    for key in ('photos', 'videos', 'audio_files', 'files', 'gifs', 'media', 'media_list_data'):
        entries = row.get(key)
        if isinstance(entries, list):
            for item in entries:
                if isinstance(item, dict):
                    uri = item.get('uri')
                    if isinstance(uri, str) and uri and len(uri) <= 1024 and len(attachments) < 16 and uri not in attachments:
                        attachments.append(uri)
                        if type(item.get('creation_timestamp')) is int:
                            dates.append(item['creation_timestamp'])
    url = fields.get('uri') or fields.get('URL') or ''
    title = next((fields[k] for k in ('sender_name', 'Changed', 'Username', 'Name', 'name', 'title', 'Title', 'Question', 'Author') if fields.get(k)), '')
    if not title:
        title = ' '.join(fields[k] for k in ('First Name', 'Last Name') if fields.get(k))
    if title.startswith('https://') and 'instagram.com/' in title:
        title = '@' + title.split('instagram.com/', 1)[1].removeprefix('_u/').strip('/').split('?')[0]
    seconds = (v // 1000 if v >= 10**12 else v for v in dates)
    when = next((v for v in seconds if 946684800 <= v < 4_102_444_800), None)
    return {'title': title[:240], 'fields': list(fields.items()), 'when': when,
            'media': url if not url.startswith(('http:', 'https:')) else '', 'attachments': attachments}


class ExportBrowser:
    def __init__(self):
        self.zip = None
        self.stream = None
        self.close()

    def close(self):
        if self.zip is not None:
            self.zip.close()
        if self.stream is not None:
            self.stream.close()
        self.zip = None
        self.stream = None
        self.path = None
        self.token = None
        self.files = []
        self.document = None
        self.record = None
        self.cards = None
        self.summary_data = self.relationships = self.comparison = None

    def release(self, token):
        if token == self.token:
            self.close()

    @staticmethod
    def open_zip(path):
        path = Path(path).resolve()
        if path.suffix.lower() != '.zip' or not 0 < path.stat().st_size <= MAX_ARCHIVE:
            raise ValueError('archive_size')
        stream = path.open('rb')
        archive = None
        try:
            # Bound the central directory before ZipFile allocates its index.
            end = _EndRecData(stream)
            if not end or end[_ECD_SIZE] > MAX_DIRECTORY or end[_ECD_ENTRIES_TOTAL] > MAX_ENTRIES:
                raise ValueError('archive_directory')
            archive = ZipFile(stream)
            files = [entry for entry in archive.infolist() if not entry.is_dir()]
            if not files or len(files) > MAX_ENTRIES or sum(len(entry.filename) for entry in files) > 2_000_000:
                raise ValueError('archive_directory')
        except Exception:
            if archive is not None:
                archive.close()
            stream.close()
            raise
        return path, stream, archive, files

    def open(self, path):
        path, stream, archive, files = self.open_zip(path)
        self.close()
        self.stream = stream
        self.zip, self.path, self.files = archive, path, files
        self.token = uuid4().hex
        return {'token': self.token, 'name': path.name, 'size': os.fstat(stream.fileno()).st_size, 'files': [
            {'id': index, 'name': entry.filename, 'size': entry.file_size,
             'kind': self.kind(entry), 'copy': entry.file_size <= MAX_COPY and not entry.flag_bits & 1}
            for index, entry in enumerate(files)
        ]}

    @staticmethod
    def kind(entry):
        suffix = Path(entry.filename).suffix.lower()
        if suffix == '.json':
            return 'json'
        if suffix in ('.txt', '.html', '.htm', '.csv', '.srt', '.vtt', '.xml', '.md'):
            return 'text'
        mime = MEDIA.get(suffix)
        return mime.split('/')[0] if mime else 'file'

    def file(self, token, entry):
        if self.zip is None or token != self.token or type(entry) is not int or not 0 <= entry < len(self.files):
            raise ValueError('archive_entry')
        return self.files[entry]

    def read(self, entry, limit):
        if entry.file_size > limit:
            raise ValueError('archive_size')
        with self.zip.open(entry) as stream:
            raw = stream.read(limit + 1)
        if len(raw) != entry.file_size:
            raise ValueError('archive_entry')
        return raw

    def load_document(self, index, entry):
        if not self.document or self.document[0] != index:
            kind = self.kind(entry)
            if kind not in ('json', 'text'):
                raise ValueError('archive_entry')
            self.document = self.record = self.cards = None
            source = self.read(entry, MAX_JSON).decode('utf-8-sig', errors='replace')
            title = ''
            if kind == 'json':
                root = json.loads(source, parse_constant=lambda _: None)
                metadata = None
                if isinstance(root, dict) and entry.filename.startswith('your_instagram_activity/messages/') and isinstance(root.get('messages'), list):
                    metadata = {key: value for key, value in root.items() if key != 'messages'}
                    title, root = 'messages', root['messages']
                elif isinstance(root, dict) and len(root) == 1:
                    key, value = next(iter(root.items()))
                    if isinstance(value, list):
                        title, root = key, value
                records = root if isinstance(root, list) else [root]
            else:
                records = [source]
                metadata = None
            self.document = index, title, kind, records, metadata
        return self.document

    def validate_token(self, token):
        if self.zip is None or token != self.token:
            raise ValueError('archive_entry')

    def read_summary(self, token):
        self.validate_token(token)
        if self.summary_data is not None:
            return self.summary_data
        metrics = {}
        try:
            self.relationships = read_relationships(self.zip)
        except ARCHIVE_ERRORS:
            pass
        if self.relationships:
            for name, rows in self.relationships.items():
                if rows is not None:
                    candidates = [(index, file.filename) for index, file in enumerate(self.files)
                                  if Path(file.filename).parent.name == 'followers_and_following' and
                                  re.fullmatch(name + r'(?:_1)?\.(json|html)', Path(file.filename).name)]
                    if candidates:
                        index, source = candidates[0]
                        metrics[name] = {'count': len(rows), 'index': index if source.endswith('.json') else None}
        months = Counter()
        for key, (folder, base) in SUMMARY_FILES.items():
            matched = []
            for index, file in enumerate(self.files):
                name = file.filename.removeprefix(folder)
                if name == file.filename:
                    continue
                part = re.fullmatch(base + r'(?:_([1-9][0-9]{0,3}))?\.json', name)
                if part:
                    matched.append((int(part[1] or '1'), index, file))
            matched.sort(key=lambda item: item[:2])
            if not matched or [part for part, _, _ in matched] != list(range(1, len(matched) + 1)):
                continue
            if sum(file.file_size for _, _, file in matched) > MAX_JSON:
                continue
            count = 0
            try:
                for _, index, file in matched:
                    _, title, _, rows, _ = self.load_document(index, file)
                    if not title and len(rows) == 1 and isinstance(rows[0], dict) and not record_card(rows[0])['fields']:
                        raise ValueError('archive_entry')
                    count += len(rows)
                    if key == 'likes':
                        for row in rows:
                            stamp = record_card(row)['when']
                            if stamp is not None:
                                months[datetime.fromtimestamp(stamp, timezone.utc).strftime('%Y-%m')] += 1
            except ARCHIVE_ERRORS:
                if key == 'likes':
                    months.clear()
                continue
            metrics[key] = {'count': count, 'index': matched[0][1]}
        self.summary_data = {'metrics': metrics, 'months': sorted(months.items())[-12:] if len(months) >= 2 else []}
        return self.summary_data

    def search(self, token, query, index=0, start=-1):
        self.validate_token(token)
        if (not isinstance(query, str) or not 2 <= len(query.strip()) <= 160 or
                type(index) is not int or not 0 <= index <= len(self.files) or
                type(start) is not int or start < -1):
            raise ValueError('archive_entry')
        needle = query.strip().casefold()
        results = []
        skipped = inspected = examined = size = 0
        def page():
            return {'rows': results, 'skipped': skipped,
                    'next': {'index': index, 'start': start} if index < len(self.files) else None}
        while index < len(self.files) and inspected < 32 and examined < 20_000 and len(results) < 24:
            entry = self.files[index]
            is_json = self.kind(entry) == 'json'
            if is_json and inspected and size + entry.file_size > MAX_JSON:
                break
            source_match = needle in readable(entry.filename).casefold()
            if is_json:
                inspected += 1
                size += min(MAX_JSON, entry.file_size)
                try:
                    _, _, _, rows, metadata = self.load_document(index, entry)
                except ARCHIVE_ERRORS:
                    rows = ()
                    skipped += 1
                else:
                    source_match = source_match or bool(metadata and needle in str(readable(metadata.get('title', ''))).casefold())
            else:
                rows = ()
                examined += 1
            if start == -1:
                if source_match:
                    results.append({'index': index, 'record': None, 'preview': ''})
                start = 0
            while start < len(rows) and examined < 20_000 and len(results) < 24:
                card = record_card(rows[start])
                value = next((value for value in (card['title'], *(value for _, value in card['fields']))
                              if needle in value.casefold()), None)
                if value:
                    results.append({'index': index, 'record': start, 'preview': self.search_excerpt(value, needle)})
                start += 1
                examined += 1
            if start < len(rows):
                break
            index += 1
            start = -1
        return page()

    @staticmethod
    def search_excerpt(value, needle):
        position = max(0, value.casefold().find(needle) - 40)
        return ('…' if position else '') + value[position:position + 180] + ('…' if position + 180 < len(value) else '')

    def compare(self, token, path):
        self.validate_token(token)
        self.comparison = None
        if self.relationships is None:
            self.relationships = read_relationships(self.zip)
        other_path, stream, archive, _ = self.open_zip(path)
        with stream, archive:
            other = read_relationships(archive)
        data = {}
        for direction, rows in self.relationships.items():
            newer = other[direction]
            if rows is None or newer is None:
                continue
            old_names = {row['username'] for row in rows}
            new_names = {row['username'] for row in newer}
            data[direction] = {
                'before': len(old_names), 'after': len(new_names),
                'added': sorted(new_names - old_names), 'removed': sorted(old_names - new_names),
            }
        self.comparison = data
        return {'name': other_path.name, 'directions': {
            direction: {key: len(value) if isinstance(value, list) else value for key, value in values.items()}
            for direction, values in data.items()
        }}

    def comparison_page(self, token, direction, change, offset=0):
        self.validate_token(token)
        if (not self.comparison or direction not in self.comparison or change not in ('added', 'removed')
                or type(offset) is not int or offset < 0):
            raise ValueError('archive_entry')
        names = self.comparison[direction][change]
        return {'names': names[offset:offset + 50], 'total': len(names)}

    def browse(self, token, index, query='', offset=0, since=None, until=None, order=None, record=None):
        entry = self.file(token, index)
        if self.kind(entry) != 'json' or not isinstance(query, str) or len(query) > 160 or type(offset) is not int or offset < 0:
            raise ValueError('archive_entry')
        if any(value is not None and type(value) is not int for value in (since, until)) or order not in (None, 'newest', 'oldest'):
            raise ValueError('archive_entry')
        _, _, _, records, metadata = self.load_document(index, entry)
        if record is not None:
            if type(record) is not int or not 0 <= record < len(records):
                raise ValueError('archive_entry')
            return {'rows': [record_card(records[record])], 'total': 1, 'title': readable(metadata.get('title', '')) if metadata else ''}
        if not self.cards or self.cards[0] != index:
            self.cards = index, [record_card(row) for row in records]
        needle = query.strip().casefold()
        matches = []
        for card in self.cards[1]:
            stamp = card['when']
            if since is not None and (stamp is None or stamp < since):
                continue
            if until is not None and (stamp is None or stamp >= until):
                continue
            if not needle or needle in card['title'].casefold() or any(needle in value.casefold() for _, value in card['fields']):
                matches.append(card)
        if order:
            matches.sort(key=lambda card: (card['when'] is None, (card['when'] or 0) * (-1 if order == 'newest' else 1)))
        result = {'rows': matches[offset:offset + 24], 'total': len(matches),
                  'title': readable(metadata.get('title', '')) if metadata else ''}
        if '/past_instagram_insights/' in entry.filename and re.match(r'^(audience_insights|profiles_reached|content_interactions)(?:_\d+)?\.json$', Path(entry.filename).name):
            for metric in TREND_METRICS:
                points = {}
                for card in matches:
                    fields = dict(card['fields'])
                    value = fields.get(metric, '')
                    if not card['when'] or fields.get('Date Range') or not re.fullmatch(r'[0-9]{1,12}', value):
                        continue
                    stamp = card['when']
                    if stamp not in points:
                        points[stamp] = int(value)
                    elif points[stamp] != int(value):
                        points[stamp] = None
                valid = sorted((stamp, value) for stamp, value in points.items() if value is not None)
                if len(valid) >= 2:
                    result['trend'] = {'metric': metric, 'points': valid[-12:]}
                    break
        return result

    def text(self, token, index, record, offset, view='records'):
        entry = self.file(token, index)
        if type(record) is not int or type(offset) is not int or record < 0 or offset < 0 or view not in ('records', 'metadata'):
            raise ValueError('archive_entry')
        self.load_document(index, entry)
        _, title, kind, records, metadata = self.document
        if view == 'metadata':
            if metadata is None:
                raise ValueError('archive_entry')
            title, records = '', [metadata]
        if (records and record >= len(records)) or (not records and record != 0):
            raise ValueError('archive_entry')
        if not self.record or self.record[:3] != (index, record, view):
            value = records[record] if records else None
            body = (json.dumps(value, ensure_ascii=False, indent=2) if kind == 'json' else value) if records else ''
            self.record = index, record, view, body
        body = self.record[3]
        return {'section': title, 'total': len(records), 'json': kind == 'json', 'hasMetadata': metadata is not None,
                'text': body[offset:offset + PAGE], 'next': offset + PAGE if offset + PAGE < len(body) else None}

    def media(self, token, index):
        entry = self.file(token, index)
        mime = MEDIA.get(Path(entry.filename).suffix.lower())
        if mime is None:
            raise ValueError('archive_entry')
        return {'mime': mime, 'base64': base64.b64encode(self.read(entry, MAX_PREVIEW)).decode('ascii')}

    def save(self, token, index, path):
        entry = self.file(token, index)
        path = Path(path).resolve()
        if entry.file_size > MAX_COPY or path == self.path or (path.exists() and path.samefile(self.path)):
            raise ValueError('archive_entry')
        temporary = None
        try:
            with NamedTemporaryFile(dir=path.parent, delete=False) as target:
                temporary = Path(target.name)
                size = 0
                with self.zip.open(entry) as source:
                    while chunk := source.read(256 * 1024):
                        size += len(chunk)
                        if size > entry.file_size:
                            raise ValueError('archive_entry')
                        target.write(chunk)
                if size != entry.file_size:
                    raise ValueError('archive_entry')
            os.replace(temporary, path)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
