"""Encrypted local backups of one account's vault and daily counts.

The service holds the engine and calls everything through it, so a subclass
that overrides ``prepare_restore``, ``save`` or ``daily_store`` still applies.
"""
import copy
import io
import json
from pathlib import Path

import access_checks
import daily_stats
import data_privacy
import list_preparation
import localization
import notification_log
import wayback
from archive_schema import initialize_schedule, validate_archive
from network import AppFailure
from storage import atomic_write


class BackupService:
    def __init__(self, engine):
        self.e = engine

    def export(self, path):
        value = copy.deepcopy(self.e.data)
        own = (value['account'] or {}).get('id')
        value['gate']['accountRequestTimes'] = {key:times for key,times in value['gate'].get('accountRequestTimes', {}).items() if key == own}
        value['session'] = None
        for record in value['lists'].values():
            if record.get('draft'):
                record['draft'].pop('nextCursor', None)
                record['draft'].pop('seenCursors', None)
        value['settings']['automatic'] = False
        value['lastError'] = None
        value.pop('lastLoginError', None)
        # Preserve the established backup bound while streaming archive years.
        # No large all-years JSON tree is allocated and an old destination survives refusal.
        stream = io.BytesIO()
        encoder = json.JSONEncoder(ensure_ascii=False,allow_nan=False,separators=(',',':'))
        def write(raw):
            if stream.tell()+len(raw)>self.e.max_vault-4096:
                raise AppFailure('backup','Yedek dosyası çok büyük. Mevcut dosya korundu.')
            stream.write(raw)
        def encode(value):
            for chunk in encoder.iterencode(value):write(chunk.encode('utf-8'))
        write(b'{"format":"InstagramTracker-observations","version":1,"data":')
        encode(value)
        write(b',"daily":[')
        if own:
            for index,document in enumerate(self.e.daily_store().export_iter()):
                if index:write(b',')
                encode(document)
        write(b']}')
        atomic_write(Path(path),self.e.protect(stream.getvalue()))

    def restore(self, path):
        path = Path(path)
        if path.stat().st_size > self.e.max_vault:
            raise AppFailure('backup', 'Yedek dosyası çok büyük.')
        try:
            value = json.loads(self.e.protect(path.read_bytes(), decrypt=True))
            restored = value['data']
            if value.get('format') != 'InstagramTracker-observations' or value.get('version') != 1:
                raise ValueError()
            # Round-trip through the exact vault schema validator before replace.
            access_checks.initialize(restored)
            notification_log.initialize(restored)
            localization.initialize(restored)
            restored['settings'].setdefault('theme', 'system')
            initialize_schedule(restored)
            list_preparation.initialize(restored)
            wayback.initialize(restored)
            data_privacy.initialize(restored)
            validate_archive(restored)
            documents = value.get('daily', [])
            owner = (restored['account'] or {}).get('id')
            if not isinstance(documents,list) or (documents and not owner):raise ValueError('daily backup')
            unique = set()
            for document in documents:
                daily_stats.validate(document,owner)
                key = (document['target'],document['year'])
                if key in unique:raise ValueError('duplicate daily backup')
                unique.add(key)
        except Exception as exc:
            raise AppFailure('backup', 'Yedek geçersiz veya başka bir Windows kullanıcısına ait. Mevcut kayıtlar korundu.') from exc
        self.e.prepare_restore(restored)
        # Loading an old backup cannot silently disable current retention preferences.
        for key in data_privacy.FIELDS:restored['settings'][key]=self.e.data['settings'][key]
        restored['privacyMaintenance']={'at':0,'failed':False}
        if documents:self.e.daily_store(owner).restore(documents)
        old_gate = self.e.data['gate']
        gate = restored['gate']
        for key in ('blockedUntil', 'nextAt', 'loginAfter', 'attempts', 'streak'):
            gate[key] = max(old_gate.get(key, 0), gate.get(key, 0))
        gate['manualOnly'] = True
        gate['requestTimes'] = old_gate.get('requestTimes', [])
        gate['accountRequestTimes'] = old_gate.get('accountRequestTimes', {})
        gate['mediaTimes'] = old_gate.get('mediaTimes', [])
        gate['mediaNextAt'] = max(old_gate.get('mediaNextAt', 0), gate.get('mediaNextAt', 0))
        restored['session'] = None
        restored['settings']['automatic'] = False
        self.e.data = restored
        self.e.save()
        self.e.maintain_privacy(force=True)
