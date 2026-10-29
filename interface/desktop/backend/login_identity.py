"""Encrypted credential-free installation context retained across failed logins."""
import copy
import json
from pathlib import Path
from storage import atomic_write, protect, StorageFailure

FIELDS = ('uuids','device_settings','user_agent','country','country_code','locale',
          'timezone_offset','timezone_name','usdid')


def device_settings(settings):
    return {k: copy.deepcopy(settings[k]) for k in FIELDS if k in settings}


class LoginIdentity:
    def __init__(self, directory):
        self.path = Path(directory) / 'login-identity.vault'

    def load(self):
        if not self.path.exists():
            return None
        try:
            if self.path.stat().st_size > 128 * 1024:
                raise ValueError()
            value = json.loads(protect(self.path.read_bytes(), decrypt=True))
            if (not isinstance(value, dict) or value.get('version') != 1 or not isinstance(value.get('device'), dict) or set(value['device']) - set(FIELDS)):
                raise ValueError()
            return value['device']
        except Exception as exc:
            raise StorageFailure('Giriş cihaz kaydı açılamadı. Mevcut dosya korundu.') from exc

    def save(self, client):
        try:
            raw = json.dumps({'version':1,'device':device_settings(client.get_settings())}, allow_nan=False).encode()
            if len(raw) > 120 * 1024:
                raise ValueError()
            atomic_write(self.path, protect(raw))
        except Exception as exc:
            raise StorageFailure('Giriş cihaz kaydı saklanamadı; istek durduruldu.') from exc
