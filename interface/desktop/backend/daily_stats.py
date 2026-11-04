"""Encrypted, per-profile/year observed counter summaries. Days use UTC on all devices."""
import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import threading
import uuid
from storage import StorageFailure, atomic_write, protect

DAY = 86400000
MAX_FILE = 512 * 1024
METRICS = ('followers', 'following', 'posts')
_LOCK = threading.RLock()


def identity(value):
    return isinstance(value, str) and re.fullmatch(r'[1-9][0-9]{0,29}', value) is not None


def year_of(at):
    if type(at) is not int or not 0 <= at <= 253402300799999:
        raise ValueError('daily timestamp')
    return datetime.fromtimestamp(at / 1000, timezone.utc).year


def observe(days, profile, after=0):
    at = round(profile['observedAt'] * 1000)
    year_of(at)
    if at <= after:
        return False
    day = at // DAY * DAY
    previous = days.get(str(day))
    if previous and at <= previous['lastAt']:
        return False
    row = copy.deepcopy(previous) if previous and previous['firstAt'] > after else {
        'day':day, 'firstAt':at, 'lastAt':at, 'samples':0}
    row['lastAt'] = at
    row['samples'] += 1
    for metric in METRICS:
        value = profile.get(metric)
        if type(value) is not int or not 0 <= value <= 9007199254740991:
            continue
        old = row.get(metric)
        # first, last, minimum, maximum, first observation time, last observation time
        row[metric] = [old[0], value, min(old[2], value), max(old[3], value), old[4], at] if old else [value,value,value,value,at,at]
    if not any(k in row for k in METRICS):
        return False
    days[str(day)] = row
    return True


def validate(value, owner=None, target=None):
    if (not isinstance(value, dict) or type(value.get('version')) is not int or value.get('version') != 1 or not identity(value.get('owner')) or
            not identity(value.get('target')) or (owner is not None and owner != value['owner']) or
            (target is not None and target != value['target']) or type(value.get('year')) is not int or
            not 1970 <= value['year'] <= 9999 or not isinstance(value.get('days'), dict) or len(value['days']) > 366):
        raise ValueError('daily archive')
    for key,row in value['days'].items():
        if (not isinstance(row,dict) or set(row)-{'day','firstAt','lastAt','samples',*METRICS} or
                type(row.get('day')) is not int or str(row['day']) != key or row['day'] % DAY or
                year_of(row['day']) != value['year'] or type(row.get('samples')) is not int or row['samples'] < 1 or
                type(row.get('firstAt')) is not int or type(row.get('lastAt')) is not int or
                not row['day'] <= row['firstAt'] <= row['lastAt'] < row['day']+DAY or not any(k in row for k in METRICS)):
            raise ValueError('daily row')
        for metric in METRICS:
            if metric not in row:
                continue
            numbers = row[metric]
            if (not isinstance(numbers,list) or len(numbers)!=6 or
                    any(type(n) is not int or n<0 or n>9007199254740991 for n in numbers) or
                    not numbers[2] <= min(numbers[:2]) <= max(numbers[:2]) <= numbers[3] or
                    not row['firstAt'] <= numbers[4] <= numbers[5] <= row['lastAt']):
                raise ValueError('daily metric')
    return value


def series(rows, metric, span, now):
    if metric not in METRICS or span not in (7,30,365,0):
        raise ValueError('daily range')
    cutoff = (now // DAY - span + 1) * DAY if span else 0
    selected = sorted((r for r in rows if r['day'] >= cutoff and metric in r), key=lambda r:r['day'])
    # At most 720 graph points, retaining observed endpoints/extrema and day coverage.
    step = max(1, ((selected[-1]['day']-selected[0]['day'])//DAY+1+719)//720) if selected else 1
    groups = {}
    base = selected[0]['day']//DAY if selected else 0
    for row in selected:
        key = (row['day']//DAY-base)//step
        field = row[metric]
        point = groups.get(key)
        if point is None:
            groups[key] = dict(bucket=key, day=row['day'], at=field[5], first=field[0], value=field[1],
                               minimum=field[2], maximum=field[3], observedDays=1, samples=row['samples'])
        else:
            point.update(at=field[5], value=field[1], minimum=min(point['minimum'],field[2]),
                         maximum=max(point['maximum'],field[3]), observedDays=point['observedDays']+1,
                         samples=point['samples']+row['samples'])
    return dict(points=list(groups.values()), bucketDays=step, observedDays=len(selected),
                samples=sum(r['samples'] for r in selected), timezone='UTC')


class DailyStore:
    def __init__(self, directory, owner):
        if not identity(owner):
            raise StorageFailure('Günlük geçmiş hesabı doğrulanamadı.')
        self.directory = Path(directory) / 'daily'
        self.owner = owner
        self.lock = _LOCK
        self.warning = False
        self.closed = False
        self.revision = uuid.uuid4().hex
        self.revisions = {}
        self.retention_after = 0

    def prefix(self, target):
        if not identity(target):
            raise ValueError('daily target')
        return hashlib.sha256(target.encode()).hexdigest()

    def read(self, path):
        if not path.exists():
            return None
        if path.stat().st_size > MAX_FILE+4096:
            raise StorageFailure('Günlük geçmiş dosyası boyut sınırını aşıyor.')
        return json.loads(protect(path.read_bytes(),decrypt=True))

    def write(self, path, value):
        raw = json.dumps(value,separators=(',',':'),allow_nan=False).encode()
        if len(raw) > MAX_FILE:
            raise StorageFailure('Günlük geçmiş dosyası doldu. Önceki kayıtlar korundu.')
        atomic_write(path,protect(raw))
        self.revisions[value['target']] = uuid.uuid4().hex

    def reset_after(self, target):
        return max(self.retention_after, self.saved_reset_after(target))

    def saved_reset_after(self, target):
        value = self.read(self.directory / (self.prefix(target)+'.reset'))
        if value is None:
            return 0
        if (value.get('version')!=1 or value.get('owner')!=self.owner or value.get('target')!=target or
                type(value.get('after')) is not int or value['after']<0):
            raise ValueError('daily reset')
        return value['after']

    def prune(self, before):
        """UTC day watermark prevents expired summaries being backfilled from old observations."""
        if not before:
            return
        with self.lock:
            for document in self.iter_documents():
                target = document['target']
                after = max(self.saved_reset_after(target), before-1)
                self.write(self.directory/(self.prefix(target)+'.reset'), dict(version=1,owner=self.owner,target=target,after=after))
                days = {key: row for key,row in document['days'].items() if int(key) >= before}
                path = self.directory/f"{self.prefix(target)}-{document['year']}.stats"
                if days != document['days']:
                    if days:self.write(path,dict(document,days=days))
                    else:path.unlink()
            self.revision=uuid.uuid4().hex

    def load_year(self, target, year):
        path = self.directory / f'{self.prefix(target)}-{year}.stats'
        value = self.read(path)
        if value is not None:
            validate(value,self.owner,target)
            if value['year']!=year: raise ValueError('daily year')
        else:
            value = dict(version=1,owner=self.owner,target=target,year=year,days={})
        return path,copy.deepcopy(value)

    def record(self, target, observations):
        with self.lock:
            if self.closed:raise StorageFailure('Günlük geçmiş görünümü değişti.')
            after = self.reset_after(target)
            by_year = {}
            for profile in sorted(observations,key=lambda p:p.get('observedAt',0)):
                at = round(profile['observedAt']*1000)
                if at<=after: continue
                by_year.setdefault(year_of(at),[]).append(profile)
            for year,profiles in by_year.items():
                path,value = self.load_year(target,year)
                changed = False
                for profile in profiles:
                    changed = observe(value['days'],profile,after) or changed
                if changed:
                    validate(value,self.owner,target)
                    self.write(path,value)

    def iter_documents(self, target=None, min_year=1970):
        prefix = self.prefix(target) if target else '[a-f0-9]{64}'
        pattern = re.compile(prefix+r'-([0-9]{4})\.stats')
        if not self.directory.exists(): return
        for path in sorted(self.directory.iterdir()):
            match = pattern.fullmatch(path.name)
            if match and int(match[1]) >= min_year:
                value = validate(self.read(path),self.owner,target)
                if path.name != f"{self.prefix(value['target'])}-{value['year']}.stats":raise ValueError('daily filename')
                yield value

    def documents(self, target=None, min_year=1970):
        return list(self.iter_documents(target,min_year))

    def query(self, target, metric, span, now):
        with self.lock:
            after = self.reset_after(target)
            min_year = year_of(max(0,(now//DAY-span+1)*DAY)) if span else 1970
            rows = [r for doc in self.iter_documents(target,min_year) for r in doc['days'].values() if r['firstAt']>after]
            return series(rows,metric,span,now)

    def clear(self, target, now):
        with self.lock:
            prefix = self.prefix(target)
            cutoff = now
            if self.directory.exists():
                for path in self.directory.iterdir():
                    if re.fullmatch(prefix+r'-[0-9]{4}\.stats',path.name):
                        try:
                            doc = validate(self.read(path),self.owner,target)
                            cutoff = max(cutoff,max((r['lastAt'] for r in doc['days'].values()),default=0))
                        except (ValueError,OSError,StorageFailure):
                            pass  # Explicit erasure also owns malformed files in this namespace.
            self.write(self.directory/(prefix+'.reset'),dict(version=1,owner=self.owner,target=target,after=cutoff))
            if self.directory.exists():
                for path in self.directory.iterdir():
                    if re.fullmatch(prefix+r'-[0-9]{4}\.stats',path.name):path.unlink()

    def clear_all(self, revoke=False):
        with self.lock:
            if revoke:self.closed=True
            if self.directory.exists():
                for path in self.directory.iterdir():
                    if re.fullmatch(r'[a-f0-9]{64}(?:-[0-9]{4}\.stats|\.reset)',path.name):path.unlink()
            self.revision=uuid.uuid4().hex
            self.revisions.clear()

    def export_iter(self):
        with self.lock:
            for doc in self.iter_documents():
                after = self.reset_after(doc['target'])
                yield dict(doc,days={k:r for k,r in doc['days'].items() if r['firstAt']>after})

    def export(self):
        return list(self.export_iter())

    def restore(self, documents):
        # Additive restore: never sum overlapping sample counts or erase newer days.
        with self.lock:
            for document in documents:
                validate(document,self.owner)
            for document in documents:
                target = document['target']
                path,value = self.load_year(target,document['year'])
                after = self.reset_after(target)
                for key,row in document['days'].items():
                    old = value['days'].get(key)
                    if row['firstAt']>after and (old is None or row['lastAt']>old['lastAt']):
                        value['days'][key] = row
                self.write(path,value)
