"""On-demand access to one user-selected export. Never extracts archive paths."""
import base64
from collections import Counter
from datetime import datetime, timezone
from html.parser import HTMLParser
import json
from pathlib import Path
import os
import re
from tempfile import NamedTemporaryFile
from uuid import uuid4
from zipfile import BadZipFile, ZipFile, _EndRecData, _ECD_SIZE, _ECD_ENTRIES_TOTAL

import csv
from instagram_archive import ENTRY, archive_name, read_relationships

MAX_ARCHIVE = 8 * 1024**3
MAX_DIRECTORY = 32 * 1024**2
MAX_ENTRIES = 50_000
MAX_JSON = 24 * 1024**2
MAX_METADATA = 256 * 1024
MAX_PREVIEW = 32 * 1024**2
MAX_COPY = 512 * 1024**2
PAGE = 24_000
ARCHIVE_ERRORS = (OSError, ValueError, TypeError, AttributeError, RuntimeError, NotImplementedError, BadZipFile, RecursionError)
# Each metric lists (folder, file stem) pairs: current exports first, then
# the older layout without the your_instagram_activity/connections prefix.
SUMMARY_FILES = {
    'profileChanges': (('personal_information/personal_information/', 'profile_changes'),
                       ('personal_information/', 'profile_changes')),
    'likes': (('your_instagram_activity/likes/', 'liked_posts'), ('likes/', 'liked_posts')),
    'saved': (('your_instagram_activity/saved/', 'saved_posts'), ('saved/', 'saved_posts')),
    'comments': (('your_instagram_activity/comments/', 'post_comments'), ('your_instagram_activity/comments/', 'reels_comments'),
                 ('comments/', 'post_comments'), ('comments/', 'reels_comments')),
    'stories': (('your_instagram_activity/media/', 'stories'), ('content/', 'stories'), ('media/', 'stories')),
    'contacts': (('connections/contacts/', 'synced_contacts'), ('contacts/', 'synced_contacts')),
}
TREND_METRICS = ('Followers', 'Accounts Reached', 'Accounts reached', 'Content Interactions')
PROFILE_FILE = 'personal_information/personal_information/personal_information.json'
EXPORT_NAME = re.compile(r'instagram-([A-Za-z0-9._]{1,30})-(\d{4}-\d{2}-\d{2})-[A-Za-z0-9]{8}\.zip', re.I)
MESSAGE_FILE = re.compile(r'(?:your_instagram_activity/)?messages/(?:[^/]+/)+message_\d+\.(?:json|html)')
CONTENT_FILES = re.compile(r'(your_instagram_activity/media|logged_information/past_instagram_insights)/(stories|reels|posts)(?:_[1-9]\d{0,3})?\.json')
CONTENT_METRICS = {
    'views': ('Instagram Views', 'Views', 'views', 'view_count'),
    'plays': ('Instagram Plays', 'Plays', 'play_count'),
    'likes': ('Instagram Likes', 'Likes', 'like_count'),
    'comments': ('Instagram Comments', 'Comments', 'comment_count'),
    'reactions': ('Reactions',),
}


def content_fields(row):
    fields = row.get('string_map_data')
    return fields if isinstance(fields, dict) else {}


def content_count(value):
    if isinstance(value, dict):
        value = value.get('value')
    if isinstance(value, str) and re.fullmatch(r'[0-9]{1,15}', value):
        value = int(value)
    return value if type(value) is int and 0 <= value < 10**15 else None


def content_timestamp(*values):
    for value in values:
        if type(value) is int:
            stamp = value // 1000 if value >= 10**12 else value
            if 946684800 <= stamp < 4102444800:
                return stamp
    return None


def content_item(row, kind, insights, files):
    if not isinstance(row, dict):
        return None
    fields = content_fields(row)
    media = row.get('media')
    assets = [x for x in media if isinstance(x, dict)] if isinstance(media, list) else [row]
    thumbnail = row.get('media_map_data')
    thumbnail = thumbnail.get('Media Thumbnail') if isinstance(thumbnail, dict) else None
    if isinstance(thumbnail, dict):
        assets.append(thumbnail)
    refs = list(dict.fromkeys(asset['uri'] for asset in assets if isinstance(asset.get('uri'), str)
                             and 0 < len(asset['uri']) <= 1024))[:16]
    date_fields = [fields.get(key) for key in ('Creation Timestamp', 'Upload Timestamp')]
    stamp = content_timestamp(*(v.get('timestamp') for v in date_fields if isinstance(v, dict)),
                              row.get('creation_timestamp'), row.get('timestamp'),
                              *(asset.get('creation_timestamp') for asset in assets))
    if stamp is None:
        return None
    caption = fields.get('Caption')
    caption = caption.get('value') if isinstance(caption, dict) else None
    caption = next((text for text in (caption, row.get('title'), *(a.get('title') for a in assets))
                    if isinstance(text, str) and text.strip()), '')
    metrics = {}
    for key, aliases in CONTENT_METRICS.items():
        for alias in aliases:
            value = content_count(fields.get(alias, row.get(alias)))
            if value is not None:
                metrics[key] = value
                break
    return {'kind': kind, 'at': stamp, 'caption': readable(caption[:500]), 'metrics': metrics,
            'assets': [files[uri] for uri in refs if uri in files]}, refs, insights
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


def record_card(row, *, preview=True):
    if not isinstance(row, dict):
        title = str(row)
        return {'title': title[:240] if preview else title, 'fields': [], 'when': None}
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
            fields.setdefault(label, readable(str(value)))
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
    return {'title': title[:240] if preview else title,
            'fields': [(label, value[:8000 if label == 'content' else 800] if preview else value)
                       for label, value in fields.items()], 'when': when,
            'media': url[:800] if not url.startswith(('http:', 'https:')) else '', 'attachments': attachments}


def monthly_likes(months):
    if len(months) < 2:
        return []
    def ordinal(month):
        year, number = map(int, month.split('-'))
        return year * 12 + number - 1
    first = ordinal(min(months))
    last = ordinal(max(months))
    result = []
    for ordinal_month in range(max(first, last - 11), last + 1):
        year, month = divmod(ordinal_month, 12)
        label = f'{year:04d}-{month + 1:02d}'
        result.append((label, months.get(label)))
    return result


class ExportFooter(HTMLParser):
    def __init__(self):
        super().__init__()
        self.in_footer = False
        self.text = []
        self.records = []
        self.ranges = []
        self.range_start = None
        self.in_time = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'aside' and attrs.get('role') == 'contentinfo':
            self.in_footer = True
            self.text.clear()
            self.range_start = None
        elif self.in_footer and tag == 'time':
            label = ' '.join(''.join(self.text).split())
            match = re.fullmatch(r'Generated by ([A-Za-z0-9._]{1,30}) on', label, re.I)
            stamp = attrs.get('datetime')
            if match:
                self.records.append((archive_name(match[1]), stamp))
            elif label.casefold() == 'contains data you requested from':
                self.range_start = stamp
            elif label.casefold() == 'to' and self.range_start is not None:
                self.ranges.append((self.range_start, stamp))
                self.range_start = None
            self.text.clear()
            self.in_time = True

    def handle_endtag(self, tag):
        if tag == 'aside':
            self.in_footer = False
            self.in_time = False
        elif tag == 'time':
            self.in_time = False

    def handle_data(self, data):
        if self.in_footer and not self.in_time:
            self.text.append(data)


def export_stamp(value):
    if isinstance(value, str) and re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?(?:Z|[+-]\d{2}:\d{2})', value):
        try:
            return datetime.fromisoformat(value)
        except ValueError:
            pass
    return None


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
        self.metadata = None
        self.content_data = None
        self.comparison_candidate = None

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
        self.metadata = self.export_metadata()
        return {'token': self.token, 'name': path.name, 'metadata': self.metadata, 'size': os.fstat(stream.fileno()).st_size, 'files': [
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

    def export_metadata(self):
        sources = {}
        for file in self.files:
            if file.filename in ('start_here.html', PROFILE_FILE):
                sources.setdefault(file.filename, []).append(file)
        account = exported_at = date_range = footer_account = None
        try:
            entry, = sources.get('start_here.html', ())
            footer = ExportFooter()
            footer.feed(self.read(entry, MAX_METADATA).decode('utf-8-sig'))
            footer.close()
            if len(footer.records) == 1:
                account, stamp = footer.records[0]
                footer_account = account
                exported_at = export_stamp(stamp)
            if len(footer.ranges) == 1:
                start, end = map(export_stamp, footer.ranges[0])
                if start is not None and end is not None and start <= end:
                    date_range = [start.date().isoformat(), end.date().isoformat()]
        except ARCHIVE_ERRORS:
            pass
        try:
            entry, = sources.get(PROFILE_FILE, ())
            profile, = json.loads(self.read(entry, MAX_METADATA))['profile_user']
            profile_account = archive_name(profile['string_map_data']['Username']['value'])
        except ARCHIVE_ERRORS + (KeyError,):
            profile_account = None
        if profile_account:
            account = profile_account if account in (None, profile_account) else None
        match = EXPORT_NAME.fullmatch(self.path.name)
        named = archive_name(match[1]) if match else None
        result = {'account': account, 'exportedAt': exported_at.isoformat() if exported_at else None}
        if named and not footer_account and not profile_account:
            # Instagram names downloads instagram-<account>-<date>-<id>.zip; this is
            # used only when the archive itself names no account, and is labelled.
            result.update(account=named, accountSource='filename')
        if date_range:
            result['dateRange'] = date_range
        if not exported_at and match:
            try:
                result['filenameDate'] = datetime.strptime(match[2], '%Y-%m-%d').date().isoformat()
            except ValueError:
                pass
        return result

    def read_summary(self, token):
        self.validate_token(token)
        if self.summary_data is not None:
            return self.summary_data
        metrics = {}
        scope = dict.fromkeys(('followers', 'following', 'messages'), 'missing')
        for file in self.files:
            name = Path(file.filename)
            if name.parent.name == 'followers_and_following' and (match := ENTRY.fullmatch(name.name)):
                scope[match[1]] = 'unreadable'
            if MESSAGE_FILE.fullmatch(file.filename):
                scope['messages'] = 'present'
            elif scope['messages'] == 'missing' and file.filename in (
                    'your_instagram_activity/messages/no-data.txt', 'messages/no-data.txt'):
                scope['messages'] = 'empty'
        relations = {}
        lists = self.read_lists()
        if lists:
            indexes = {file.filename: index for index, file in enumerate(self.files)}
            source = lambda key: (indexes.get(lists['files'].get(key))
                                  if str(lists['files'].get(key, '')).endswith('.json') else None)
            for name in ('followers', 'following'):
                quality = lists['quality'].get(name)
                if quality is None:
                    continue
                if lists[name] is None:
                    scope[name] = 'unreadable'
                    continue
                scope[name] = 'present' if quality['complete'] else 'partial'
                metrics[name] = {'count': len(lists[name]), 'index': source(name), 'complete': quality['complete'],
                                 'skipped': quality['skipped'], 'missingParts': quality['missingParts']}
            for key, value in lists['extras'].items():
                relations[key] = {'count': len(value['rows']), 'index': source(key), 'complete': value['quality']['complete']}
        threads = {str(Path(file.filename).parent) for file in self.files if MESSAGE_FILE.fullmatch(file.filename)}
        if threads:
            metrics['messages'] = {'count': len(threads), 'index': None}
        months = Counter()
        for key, layouts in SUMMARY_FILES.items():
            groups = []
            for folder, base in layouts:
                matched = []
                for index, file in enumerate(self.files):
                    name = file.filename.removeprefix(folder)
                    if name == file.filename:
                        continue
                    part = re.fullmatch(base + r'(?:_([1-9][0-9]{0,3}))?\.json', name)
                    if part:
                        matched.append((int(part[1] or '1'), index, file))
                matched.sort(key=lambda item: item[:2])
                if matched and [part for part, _, _ in matched] == list(range(1, len(matched) + 1)):
                    groups.append(matched)
            if not groups:
                continue
            # Comments come from post and reels files; other metrics use the first layout found.
            matched = [item for group in (groups if key == 'comments' else groups[:1]) for item in group]
            if sum(file.file_size for _, _, file in matched) > MAX_JSON:
                metrics[key] = {'count': None, 'index': matched[0][1], 'tooLarge': True}
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
        self.summary_data = {'metrics': metrics, 'months': monthly_likes(months), 'relations': relations,
                             'scope': scope, 'metadata': self.metadata}
        return self.summary_data

    def read_lists(self):
        if self.relationships is None:
            try:
                self.relationships = read_relationships(self.zip)
            except ARCHIVE_ERRORS:
                self.relationships = False
        return self.relationships or None

    def lists(self, token):
        """The open ZIP's lists in the shape of the single-file relationship view."""
        self.validate_token(token)
        found = self.read_lists()
        if not found:
            raise ValueError('archive_entry')
        return {'fileName': self.path.name, **found}

    @staticmethod
    def list_summary(found):
        if not found:
            return {'followers': None, 'following': None}
        return {name: ({'count': len(found[name]), 'complete': found['quality'][name]['complete']}
                       if found[name] is not None else ({'unreadable': True} if name in found['quality'] else None))
                for name in ('followers', 'following')}

    def content(self, token, month=None, day=None, offset=0):
        self.validate_token(token)
        if (month is not None and (not isinstance(month, str) or not re.fullmatch(r'\d{4}-(0[1-9]|1[0-2])', month))
                or day is not None and (month is None or not isinstance(day, str) or not re.fullmatch(re.escape(month) + r'-[0-3][0-9]', day))
                or type(offset) is not int or offset < 0):
            raise ValueError('archive_entry')
        if self.content_data is None:
            self.content_data = self.build_content()
        dates, skipped = self.content_data
        rows = dates.get(day, []) if day else []
        return {'latest': max(dates, default=None), 'skipped': skipped,
                'days': {date: len(items) for date, items in dates.items() if month and date.startswith(month + '-')},
                'items': rows[offset:offset + 24], 'total': len(rows)}

    def build_content(self):
        files = {file.filename: i for i, file in enumerate(self.files)
                 if self.kind(file) in ('image', 'video') and file.file_size <= MAX_PREVIEW}
        items, by_ref = [], {}
        remaining = MAX_JSON
        skipped = 0
        sources = [(match, index, file) for index, file in enumerate(self.files)
                   if (match := CONTENT_FILES.fullmatch(file.filename))]
        for match, index, file in sorted(sources, key=lambda source: source[0][1].startswith('logged_information')):
            if file.file_size > remaining:
                skipped += 1
                continue
            remaining -= file.file_size
            try:
                rows = self.load_document(index, file)[3]
            except ARCHIVE_ERRORS:
                skipped += 1
                continue
            for row in rows:
                parsed = content_item(row, match[2], match[1].startswith('logged_information'), files)
                if parsed is None:
                    continue
                item, refs, insights = parsed
                matches = {by_ref[(item['kind'], ref)] for ref in refs if (item['kind'], ref) in by_ref}
                if insights and len(matches) == 1 and -1 not in matches:
                    original = items[matches.pop()]
                    original['metrics'].update(item['metrics'])
                    original['assets'] = list(dict.fromkeys(original['assets'] + item['assets']))[:16]
                    if not original['caption']:
                        original['caption'] = item['caption']
                    continue
                if not insights:
                    for ref in refs:
                        key = item['kind'], ref
                        by_ref[key] = len(items) if key not in by_ref else -1
                items.append(item)
        dates = {}
        for item in sorted(items, key=lambda value: value['at'], reverse=True):
            day = datetime.fromtimestamp(item['at'], timezone.utc).date().isoformat()
            dates.setdefault(day, []).append(item)
        return dates, skipped

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
                match = self.search_match(rows[start], needle)
                if match is not None:
                    results.append({'index': index, 'record': start, 'preview': self.search_excerpt(*match)})
                start += 1
                examined += 1
            if start < len(rows):
                break
            index += 1
            start = -1
        return page()

    @staticmethod
    def search_match(row, needle):
        card = record_card(row, preview=False)
        for value in (card['title'], *(value for _, value in card['fields'])):
            folded = value.casefold()
            position = folded.find(needle)
            if position >= 0:
                return value, position, len(folded)
        return None

    @staticmethod
    def search_excerpt(value, position, folded_length):
        if folded_length != len(value):
            folded_offset = 0
            for index, character in enumerate(value):
                folded_offset += len(character.casefold())
                if folded_offset > position:
                    position = index
                    break
        start = max(0, position - 20)
        end = start + 180
        return ('…' if start else '') + value[start:end] + ('…' if end < len(value) else '')

    def prepare_comparison(self, token, path):
        self.validate_token(token)
        self.comparison = None
        self.comparison_candidate = None
        other = ExportBrowser()
        other.path, other.stream, other.zip, other.files = self.open_zip(path)
        try:
            first = {'name': self.path.name, **self.metadata}
            second = {'name': other.path.name, **other.export_metadata()}
            try:
                lists = read_relationships(other.zip)
            except ARCHIVE_ERRORS:
                lists = None
        finally:
            other.close()
        first['lists'] = self.list_summary(self.read_lists())
        second['lists'] = self.list_summary(lists)
        candidate = uuid4().hex
        different_accounts = bool(first['account'] and second['account'] and first['account'] != second['account'])
        unknown_account = not (first['account'] and second['account'])
        self.comparison_candidate = (candidate, lists)
        return {'candidate': candidate, 'first': first, 'second': second, 'differentAccounts': different_accounts,
                'unknownAccount': unknown_account,
                'comparable': any(first['lists'][d] and 'count' in first['lists'][d] and second['lists'][d] and 'count' in second['lists'][d]
                                  for d in ('followers', 'following'))}

    def compare(self, token, candidate, reverse=False):
        self.validate_token(token)
        if (type(reverse) is not bool or not self.comparison_candidate
                or self.comparison_candidate[0] != candidate):
            raise ValueError('archive_entry')
        other = self.comparison_candidate[1]
        if other is None:
            raise ValueError('archive_entry')
        self.comparison = None
        mine = self.read_lists()
        if not mine:
            raise ValueError('archive_entry')
        before, after = (other, mine) if reverse else (mine, other)
        data, missing = {}, {}
        for direction in ('followers', 'following'):
            rows, newer = before[direction], after[direction]
            if rows is None or newer is None:
                missing[direction] = 'both' if rows is None and newer is None else 'first' if rows is None else 'second'
                continue
            old = {row['username']: row['timestamp'] for row in rows}
            new = {row['username']: row['timestamp'] for row in newer}
            added = sorted(set(new) - set(old))
            removed = sorted(set(old) - set(new))
            # The export has no account IDs. A follow date survives a username
            # change, so a single removed/added pair with the same date is
            # reported as a probable rename, never silently merged.
            by_date = lambda names, source: {stamp: [n for n in names if source[n] == stamp]
                                             for stamp in {source[n] for n in names} if stamp is not None}
            gone, came = by_date(removed, old), by_date(added, new)
            renamed = [{'from': gone[stamp][0], 'to': came[stamp][0], 'timestamp': stamp}
                       for stamp in sorted(set(gone) & set(came)) if len(gone[stamp]) == 1 and len(came[stamp]) == 1]
            paired_old = {pair['from'] for pair in renamed}
            paired_new = {pair['to'] for pair in renamed}
            data[direction] = {
                'before': len(old), 'after': len(new),
                'added': [{'username': n, 'timestamp': new[n]} for n in added if n not in paired_new],
                'removed': [{'username': n, 'timestamp': old[n]} for n in removed if n not in paired_old],
                'renamed': renamed,
                'complete': before['quality'][direction]['complete'] and after['quality'][direction]['complete'],
            }
        self.comparison = data
        return {'directions': {
            direction: {key: len(value) if isinstance(value, list) else value for key, value in values.items()}
            for direction, values in data.items()
        }, 'missing': missing}

    def comparison_page(self, token, direction, change, offset=0, query='', order='name'):
        self.validate_token(token)
        if (not self.comparison or direction not in self.comparison or change not in ('added', 'removed', 'renamed')
                or type(offset) is not int or offset < 0 or not isinstance(query, str) or len(query) > 60
                or order not in ('name', 'newest', 'oldest')):
            raise ValueError('archive_entry')
        rows = self.comparison[direction][change]
        needle = query.strip().casefold().lstrip('@')
        if needle:
            rows = [row for row in rows if needle in (row.get('username') or row['from'] + ' ' + row['to'])]
        if order != 'name':
            rows = sorted(rows, key=lambda row: (row['timestamp'] is None, (row['timestamp'] or 0) * (-1 if order == 'newest' else 1)))
        return {'rows': rows[offset:offset + 50], 'total': len(rows)}

    def comparison_csv(self, token, path):
        self.validate_token(token)
        if not self.comparison:
            raise ValueError('archive_entry')
        def cell(value):
            value = '' if value is None else str(value)
            return "'" + value if value.startswith(('=', '+', '-', '@', '\t', '\r', '\n')) else value
        def day(stamp):
            return datetime.fromtimestamp(stamp, timezone.utc).date().isoformat() if stamp else ''
        path = Path(path).resolve()
        if path.suffix.lower() != '.csv' or path == self.path:
            raise ValueError('archive_entry')
        with open(path, 'w', encoding='utf-8-sig', newline='') as stream:
            writer = csv.writer(stream, quoting=csv.QUOTE_ALL)
            writer.writerow(['list', 'change', 'username', 'previous_username', 'follow_date_utc'])
            for direction, values in self.comparison.items():
                for change in ('added', 'removed'):
                    writer.writerows([cell(direction), cell(change), cell(row['username']), '', day(row['timestamp'])]
                                     for row in values[change])
                writer.writerows([cell(direction), 'renamed', cell(row['to']), cell(row['from']), day(row['timestamp'])]
                                 for row in values['renamed'])
        return {'saved': True}

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
        for row, card in zip(records, self.cards[1]):
            stamp = card['when']
            if since is not None and (stamp is None or stamp < since):
                continue
            if until is not None and (stamp is None or stamp >= until):
                continue
            if not needle or self.search_match(row, needle) is not None:
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
