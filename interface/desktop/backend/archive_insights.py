"""Local insights from the open Instagram export. Reads only the ZIP; sends nothing.

Every section is derived from the user's own exported activity: who they
interact with, when they are active, account logins and what the export says
about ads. Missing or oversized sources are reported, never guessed.
"""
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
import re

from instagram_archive import READ_ERRORS, USERNAME, archive_name

MAX_FILE = 24 * 1024**2
MAX_TOTAL = 96 * 1024**2
MAX_ROWS = 200_000
MAX_LIST = 500
EPOCH_MIN, EPOCH_MAX = 946684800, 4102444800
PART = r'(?:_[1-9][0-9]{0,3})?\.json'
PREFIX = r'(?:your_instagram_activity/)?'

# Activities the account owner performed on someone else's content.
INTERACTIONS = (
    ('likes', re.compile(PREFIX + r'likes/(?:liked_posts|liked_comments)' + PART)),
    ('comments', re.compile(PREFIX + r'comments/(?:post_comments|reels_comments)' + PART)),
    ('stories', re.compile(PREFIX + r'story_interactions/(?:story_likes|polls|quizzes|questions|emoji_sliders|'
                           r'emoji_story_reactions|countdowns|story_reaction_sticker_reactions)' + PART)),
    ('saved', re.compile(PREFIX + r'saved/saved_posts' + PART)),
)
SECURITY = 'security_and_login_information/'
LOGINS = re.compile(SECURITY + r'(?:login_and_profile_creation/)?login_activity' + PART)
LOGOUTS = re.compile(SECURITY + r'(?:login_and_profile_creation/)?logout_activity' + PART)
PASSWORDS = re.compile(SECURITY + r'(?:login_and_profile_creation/)?password_change_activity' + PART)
PRIVACY = re.compile(SECURITY + r'(?:login_and_profile_creation/)?account_privacy_changes' + PART)
DEVICES = re.compile(r'personal_information/device_information/devices' + PART)
ADS = 'ads_information/'
ADVERTISERS = re.compile(ADS + r'(?:[^/]+/)?advertisers_using_your_activity_or_information' + PART)
ADS_VIEWED = re.compile(ADS + r'(?:[^/]+/)?ads_viewed' + PART)
ADS_CLICKED = re.compile(ADS + r'(?:[^/]+/)?ads_clicked' + PART)
TOPICS = re.compile(r'(?:preferences/your_topics/your_topics|' + ADS + r'(?:[^/]+/)?(?:ads_interests|your_topics))' + PART)
OWNER_LABELS = ('Media Owner', 'Owner', 'Username', 'Author', 'Account username')
AD_FLAGS = ('has_data_file_custom_audience', 'has_remarketing_custom_audience', 'has_in_person_store_visit')


def stamp(value):
    if type(value) is int:
        value = value // 1000 if value >= 10**12 else value
        if EPOCH_MIN <= value < EPOCH_MAX:
            return value
    return None


def text(value, limit=200):
    if isinstance(value, str):
        value = ' '.join(value.split())
        return value[:limit] if value else None
    return None


def rows_of(root):
    if isinstance(root, list):
        return root
    if isinstance(root, dict):
        lists = [value for value in root.values() if isinstance(value, list)]
        return [row for value in lists for row in value] if lists else [root]
    return []


def flatten(row, depth=0):
    """Yield (label, value-dict) pairs from string_map_data and nested label_values."""
    if not isinstance(row, dict) or depth > 4:
        return
    fields = row.get('string_map_data')
    if isinstance(fields, dict):
        for label, value in fields.items():
            if isinstance(value, dict):
                yield label, value
    for item in row.get('label_values') or ():
        if not isinstance(item, dict):
            continue
        if isinstance(item.get('label'), str):
            yield item['label'], item
        for nested in item.get('dict') or item.get('vec') or ():
            if isinstance(nested, dict):
                yield from flatten({'label_values': nested.get('dict') or [nested]}, depth + 1)


def field(row, *labels):
    found = dict(flatten(row))
    for label in labels:
        value = found.get(label)
        if value is not None:
            result = text(value.get('value')) or text(value.get('href'))
            if result:
                return result
    return None


def when(row):
    if not isinstance(row, dict):
        return None
    for key in ('timestamp', 'creation_timestamp', 'timestamp_ms'):
        if (value := stamp(row.get(key))) is not None:
            return value
    for _, value in flatten(row):
        for key in ('timestamp', 'timestamp_value', 'timestamp_ms'):
            if (result := stamp(value.get(key))) is not None:
                return result
    for item in row.get('string_list_data') or ():
        if isinstance(item, dict) and (result := stamp(item.get('timestamp'))) is not None:
            return result
    return None


def username(value):
    if not isinstance(value, str):
        return None
    value = value.strip().removeprefix('@')
    if value.startswith('https://') and 'instagram.com/' in value:
        value = value.split('instagram.com/', 1)[1].removeprefix('_u/').split('/')[0].split('?')[0]
    return archive_name(value) if USERNAME.fullmatch(value) and re.search(r'[a-z0-9_]', value, re.I) else None


def target(row):
    """The account whose content was liked, commented on or saved."""
    if not isinstance(row, dict):
        return None
    if name := username(row.get('title')):
        return name
    found = dict(flatten(row))
    for label in OWNER_LABELS:
        value = found.get(label)
        if value and (name := username(value.get('value')) or username(value.get('href'))):
            return name
    return None


def device(agent):
    """A short, human device label from a user agent. Never the full string."""
    if not agent:
        return None
    if match := re.search(r'Android \((\d+)/([\d.]+);[^;]*;[^;]*;\s*([^;]+);\s*([^;]+);', agent):
        return f'Android {match[2]} · {match[3].strip()} {match[4].strip()}'[:80]
    if match := re.search(r'\((iPhone|iPad)[^;]*;\s*iOS (\d+)[_.](\d+)', agent):
        return f'{match[1]} · iOS {match[2]}.{match[3]}'
    system = next((name for key, name in (('Windows', 'Windows'), ('Macintosh', 'Mac'), ('Mac OS X', 'Mac'),
                                          ('CrOS', 'ChromeOS'), ('Android', 'Android'), ('iPhone', 'iPhone'),
                                          ('iPad', 'iPad'), ('Linux', 'Linux')) if key in agent), None)
    browser = next((name for key, name in (('Edg/', 'Edge'), ('OPR/', 'Opera'), ('Firefox/', 'Firefox'),
                                           ('Chrome/', 'Chrome'), ('Safari/', 'Safari')) if key in agent), None)
    if 'Instagram' in agent and not browser:
        browser = 'Instagram'
    label = ' · '.join(part for part in (system, browser) if part)
    return label or agent[:40]


class Reader:
    def __init__(self, browser):
        self.browser = browser
        self.budget = MAX_TOTAL
        self.skipped = set()

    def matching(self, pattern):
        return [file for file in self.browser.files if pattern.fullmatch(file.filename)]

    def rows(self, pattern, section):
        result = []
        for file in self.matching(pattern):
            if file.file_size > MAX_FILE or file.file_size > self.budget or len(result) >= MAX_ROWS:
                self.skipped.add(section)
                continue
            self.budget -= file.file_size
            try:
                raw = self.browser.read(file, MAX_FILE)
                root = json.loads(raw.decode('utf-8-sig', errors='replace'), parse_constant=lambda _: None)
            except READ_ERRORS:
                self.skipped.add(section)
                continue
            result.extend(rows_of(root)[:MAX_ROWS - len(result)])
        return result

    def present(self, *patterns):
        return any(self.matching(pattern) for pattern in patterns)


def local_slot(value, offset):
    moment = datetime.fromtimestamp(value, timezone.utc) - timedelta(minutes=offset)
    return moment


def people(reader, lists):
    counts, last, kinds, events = Counter(), {}, {}, []
    sources = {}
    for kind, pattern in INTERACTIONS:
        rows = reader.rows(pattern, kind)
        sources[kind] = len(rows) if reader.present(pattern) else None
        for row in rows:
            at = when(row)
            if at is not None:
                events.append((at, kind))
            name = target(row)
            if not name:
                continue
            counts[name] += 1
            kinds.setdefault(name, Counter())[kind] += 1
            if at is not None and at > last.get(name, 0):
                last[name] = at
    followers = {row['username']: row['timestamp'] for row in (lists or {}).get('followers') or ()}
    following = {row['username']: row['timestamp'] for row in (lists or {}).get('following') or ()}
    has_followers = bool(lists and lists.get('followers') is not None)
    has_following = bool(lists and lists.get('following') is not None)
    row = lambda name: {'username': name, 'followedAt': following.get(name), 'followsYou': name in followers if has_followers else None,
                        'youFollow': name in following if has_following else None, 'count': counts.get(name, 0),
                        'last': last.get(name), 'kinds': dict(kinds.get(name, {}))}
    top = [row(name) for name, _ in counts.most_common(30)]
    return {
        'sources': sources,
        'lists': {'followers': has_followers, 'following': has_following},
        'following': [row(name) for name in sorted(following)] if has_following else [],
        'top': top,
        'targets': len(counts),
    }, events, followers, following


def heatmap(events, offset):
    grid = [[0] * 24 for _ in range(7)]
    for at, _ in events:
        moment = local_slot(at, offset)
        grid[moment.weekday()][moment.hour] += 1
    return grid


def security(reader):
    logins = []
    for row in reader.rows(LOGINS, 'security'):
        at = when(row)
        if at is None:
            continue
        agent = field(row, 'User Agent', 'User agent')
        logins.append({'at': at, 'ip': field(row, 'IP Address', 'IP address'), 'device': device(agent),
                       'language': field(row, 'Language Code', 'Language')})
    logins.sort(key=lambda item: item['at'], reverse=True)
    first_seen = {}
    for item in reversed(logins):
        if item['device']:
            first_seen.setdefault(item['device'], item['at'])
    newest = logins[0]['at'] if logins else None
    devices = [{'device': name, 'first': first, 'last': max(i['at'] for i in logins if i['device'] == name),
                'count': sum(1 for i in logins if i['device'] == name),
                'recent': bool(newest and first >= newest - 30 * 86400 and len(first_seen) > 1)}
               for name, first in first_seen.items()]
    devices.sort(key=lambda item: item['last'], reverse=True)
    stored = []
    for row in reader.rows(DEVICES, 'security'):
        name = device(field(row, 'User Agent', 'User agent', 'Device'))
        if name:
            stored.append({'device': name, 'last': when(row)})
    changes = lambda pattern: sorted({at for row in reader.rows(pattern, 'security') if (at := when(row)) is not None}, reverse=True)
    privacy = []
    for row in reader.rows(PRIVACY, 'security'):
        at = when(row)
        if at is not None:
            privacy.append({'at': at, 'value': text(row.get('title')) or field(row, 'Privacy setting', 'Setting', 'Value')})
    privacy.sort(key=lambda item: item['at'], reverse=True)
    present = reader.present(LOGINS, LOGOUTS, PASSWORDS, PRIVACY, DEVICES)
    return {
        'present': present,
        'logins': logins[:MAX_LIST], 'loginCount': len(logins),
        'ipCount': len({item['ip'] for item in logins if item['ip']}),
        'devices': devices[:100], 'storedDevices': stored[:100],
        'logouts': len(changes(LOGOUTS)), 'passwordChanges': changes(PASSWORDS)[:100],
        'privacyChanges': privacy[:100],
    }


def ads(reader):
    advertisers, flags = [], Counter()
    for row in reader.rows(ADVERTISERS, 'ads'):
        if not isinstance(row, dict):
            continue
        name = text(row.get('advertiser_name')) or text(row.get('title')) or field(row, 'Advertiser name', 'Name')
        if not name:
            continue
        advertisers.append(name)
        for flag in AD_FLAGS:
            if row.get(flag) is True:
                flags[flag] += 1
    unique = sorted(set(advertisers), key=str.casefold)
    authors = Counter()
    viewed = reader.rows(ADS_VIEWED, 'ads')
    for row in viewed:
        if name := field(row, 'Author', 'Advertiser'):
            authors[name] += 1
    clicked = reader.rows(ADS_CLICKED, 'ads')
    topics = []
    for row in reader.rows(TOPICS, 'ads'):
        name = field(row, 'Name', 'Interest', 'Topic', 'Value') or (text(row.get('title')) if isinstance(row, dict) else None)
        if name and name not in topics:
            topics.append(name)
    return {
        'present': reader.present(ADVERTISERS, ADS_VIEWED, ADS_CLICKED, TOPICS),
        'advertisers': unique[:MAX_LIST], 'advertiserCount': len(unique), 'flags': dict(flags),
        'viewed': len(viewed) if reader.present(ADS_VIEWED) else None,
        'clicked': len(clicked) if reader.present(ADS_CLICKED) else None,
        'topAuthors': [{'name': name, 'count': count} for name, count in authors.most_common(15)],
        'topics': sorted(topics, key=str.casefold)[:MAX_LIST], 'topicCount': len(topics),
    }


def years(events, followers, following, content, offset):
    data = {}
    def year(at):
        return str(local_slot(at, offset).year)
    def bucket(key):
        return data.setdefault(key, {'likes': 0, 'comments': 0, 'stories': 0, 'saved': 0, 'newFollowers': 0,
                                     'newFollowing': 0, 'posts': 0, 'reels': 0, 'storiesShared': 0,
                                     'months': Counter(), 'hours': Counter(), 'days': set()})
    for at, kind in events:
        item = bucket(year(at))
        item[kind] += 1
        moment = local_slot(at, offset)
        item['months'][moment.month] += 1
        item['hours'][moment.hour] += 1
        item['days'].add(moment.date())
    for source, key in ((followers, 'newFollowers'), (following, 'newFollowing')):
        for at in source.values():
            if at is not None:
                bucket(year(at))[key] += 1
    for kind, at in content:
        bucket(year(at))[{'posts': 'posts', 'reels': 'reels', 'stories': 'storiesShared'}[kind]] += 1
    result = {}
    for key, item in sorted(data.items()):
        months, hours, days = item.pop('months'), item.pop('hours'), item.pop('days')
        item['busiestMonth'] = months.most_common(1)[0][0] if months else None
        item['busiestHour'] = hours.most_common(1)[0][0] if hours else None
        item['activeDays'] = len(days)
        result[key] = item
    return result


def build(browser, offset):
    reader = Reader(browser)
    lists = browser.read_lists()
    interaction, events, followers, following = people(reader, lists)
    if browser.content_data is None:
        browser.content_data = browser.build_content()
    content = [(item['kind'], item['at']) for items in browser.content_data[0].values() for item in items]
    events.sort()
    return {
        'people': interaction,
        'activity': {'grid': heatmap(events, offset), 'total': len(events),
                     'first': events[0][0] if events else None, 'last': events[-1][0] if events else None},
        'security': security(reader),
        'ads': ads(reader),
        'years': years(events, followers, following, content, offset),
        'dateRange': (browser.metadata or {}).get('dateRange'),
        'skipped': sorted(reader.skipped),
    }
