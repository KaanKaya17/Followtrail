"""Read and write the shared, session-free Tracker history archive."""
import hashlib
import json
import os
from pathlib import Path
import re

from Cryptodome.Cipher import AES

from daily_stats import validate as validate_daily
from person_text import GROUP_CHARACTERS, NOTE_CHARACTERS
from storage import atomic_write

MAGIC = b'TRKBAK01'
ITERATIONS = 600_000
LIMIT = 16 * 1024 * 1024
HEADER = len(MAGIC) + 16 + 12
ID = re.compile(r'[1-9][0-9]{0,29}')
HANDLE = re.compile(r'[A-Za-z0-9._]{1,30}')


def key(password, salt):
    if not isinstance(password, str) or not 1 <= len(password.encode('utf-8')) <= 512:
        raise ValueError('password')
    return hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, ITERATIONS)


def seal(value, password):
    if len(password) < 12:
        raise ValueError('password length')
    raw = json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')
    if len(raw) > LIMIT:
        raise ValueError('backup size')
    salt, nonce = os.urandom(16), os.urandom(12)
    header = MAGIC + salt + nonce
    cipher = AES.new(key(password, salt), AES.MODE_GCM, nonce=nonce)
    cipher.update(header)
    encrypted, tag = cipher.encrypt_and_digest(raw)
    return header + encrypted + tag


def open_backup(path, password):
    with Path(path).open('rb') as stream:
        raw = stream.read(LIMIT + HEADER + 17)
    if not HEADER + 16 < len(raw) <= LIMIT + HEADER + 16 or not raw.startswith(MAGIC):
        raise ValueError('backup header')
    cipher = AES.new(key(password, raw[8:24]), AES.MODE_GCM, nonce=raw[24:HEADER])
    cipher.update(raw[:HEADER])
    return validate(json.loads(cipher.decrypt_and_verify(raw[HEADER:-16], raw[-16:])))


def validate(value):
    def fields(row, required, optional=()):
        if not isinstance(row, dict) or not set(required) <= row.keys() <= set(required) | set(optional):
            raise ValueError('backup fields')

    def string(value, limit, pattern=None):
        if not isinstance(value, str) or len(value.encode('utf-8')) > limit or (pattern and not pattern.fullmatch(value)):
            raise ValueError('backup text')

    def number(value, maximum=9007199254740991):
        if type(value) is not int or not 0 <= value <= maximum:
            raise ValueError('backup number')

    def identity(row):
        string(row['id'], 30, ID)
        if 'username' in row:
            string(row['username'], 30, HANDLE)
        if 'name' in row:
            string(row['name'], 8192)

    def observation(row):
        fields(row, ('id','username','at'), ('name','bio','followers','following','posts','private'))
        identity(row)
        number(row['at'], 253402300799999)
        if 'bio' in row:
            string(row['bio'], 8192)
        for metric in ('followers','following','posts'):
            if metric in row:
                number(row[metric])
        if 'private' in row and type(row['private']) is not bool:
            raise ValueError('backup visibility')

    fields(value, ('version','account','people','history','lists','daily'))
    if type(value['version']) is not int or value['version'] != 1:
        raise ValueError('backup version')
    observation(value['account'])
    owner = value['account']['id']
    for section, limit in (('people',200), ('history',74000), ('lists',2412), ('daily',10000)):
        if not isinstance(value[section], list) or len(value[section]) > limit:
            raise ValueError('backup section')
    targets = {owner}
    for row in value['people']:
        fields(row, ('profile','note','group','favorite'))
        observation(row['profile'])
        target = row['profile']['id']
        if target in targets or type(row['favorite']) is not bool:
            raise ValueError('backup person')
        targets.add(target)
        for field, limit in (('note', NOTE_CHARACTERS), ('group', GROUP_CHARACTERS)):
            if not isinstance(row[field], str) or len(row[field]) > limit:
                raise ValueError('backup person text')
    for row in value['history']:
        observation(row)
        if row['id'] not in targets:
            raise ValueError('backup history owner')
    for row in value['lists']:
        fields(row, ('target','direction','at','members'))
        if row['target'] not in targets or row['direction'] not in ('followers','following'):
            raise ValueError('backup list owner')
        number(row['at'], 253402300799999)
        if not isinstance(row['members'], list) or len(row['members']) > 20000:
            raise ValueError('backup list size')
        for member in row['members']:
            fields(member, ('id',), ('username','name'))
            identity(member)
        if len({member['id'] for member in row['members']}) != len(row['members']):
            raise ValueError('backup duplicate member')
    for row in value['daily']:
        fields(row, ('version','owner','target','year','days'))
        validate_daily(row, owner)
        if row['target'] not in targets:
            raise ValueError('backup daily owner')
    return value


def project(data, daily):
    def profile(row, identity):
        allowed = ('username','name','bio','followers','following','posts','private')
        return {'id': identity, 'at': round((row.get('observedAt') or 0) * 1000),
                **{key: row[key] for key in allowed if row.get(key) is not None}}

    account = data['account']
    if account is None:
        raise ValueError('no account to export')
    targets = {account['id'], *data['people']}
    people = [{'profile': profile(row, identity), 'note': row.get('note') or '',
               'group': row.get('group') or '', 'favorite': bool(row.get('favorite'))}
              for identity, row in data['people'].items()]
    history = [profile(row, identity) for identity, rows in data['history'].items()
               if identity in targets for row in rows]
    lists = []
    for name, record in data['lists'].items():
        target, _, direction = name.partition(':')
        if target not in targets:
            continue
        for snapshot in record['snapshots']:
            if snapshot.get('complete') and snapshot.get('finishedAt'):
                members = [{key: item[key] for key in ('id','username','name') if item.get(key) is not None}
                           for item in snapshot['members'].values()]
                lists.append(dict(target=target, direction=direction, at=round(snapshot['finishedAt'] * 1000), members=members))
    return validate(dict(version=1, account=profile(account, account['id']), people=people, history=history,
                         lists=lists, daily=[doc for doc in daily if doc['target'] in targets]))


class Archive:
    def __init__(self):
        self.value = None

    def open(self, path, password):
        self.value = open_backup(path, password)
        return self.summary()

    def summary(self):
        account = self.value['account']
        targets = {row['profile']['id']: row['profile']['username'] for row in self.value['people']}
        targets[account['id']] = account['username']
        return {'account': account, 'targets': targets, **{section: len(self.value[section])
                for section in ('people','history','lists','daily')}}

    def page(self, section, offset=0, index=None):
        if self.value is None or section not in ('people','history','lists','daily') or type(offset) is not int or offset < 0:
            raise ValueError('archive page')
        rows = self.value[section]
        if index is not None:
            if type(index) is not int or not 0 <= index < len(rows):
                raise ValueError('archive index')
            if section == 'lists':
                rows = rows[index]['members']
            elif section == 'daily':
                rows = sorted(rows[index]['days'].values(), key=lambda row: row['day'], reverse=True)
            else:
                raise ValueError('archive detail')
        elif section in ('lists','daily'):
            hidden = 'members' if section == 'lists' else 'days'
            return {'total': len(rows), 'rows': [
                {**{k: v for k, v in row.items() if k != hidden}, 'count': len(row[hidden])}
                for row in rows[offset:offset+40]]}
        return {'total': len(rows), 'rows': rows[offset:offset+40]}

    def close(self):
        self.value = None


def export(engine, path, password, account_id):
    if account_id != engine.active_id or engine.data['account'] is None:
        raise ValueError('account selection')
    atomic_write(Path(path), seal(project(engine.data, engine.daily_store().export_iter()), password))
