"""Atomic user-scoped Windows DPAPI vault. No plaintext account files."""
import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path
import tempfile

MAX_VAULT = 64 * 1024 * 1024


class StorageFailure(Exception):
    pass


class Blob(ctypes.Structure):
    _fields_ = [('cbData', wintypes.DWORD), ('pbData', ctypes.POINTER(ctypes.c_ubyte))]


def protect(data, decrypt=False):
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    ptr = ctypes.POINTER(Blob)
    crypt.CryptProtectData.argtypes = [ptr, wintypes.LPCWSTR, ptr, ctypes.c_void_p,
                                      ctypes.c_void_p, wintypes.DWORD, ptr]
    crypt.CryptUnprotectData.argtypes = [ptr, ctypes.c_void_p, ptr, ctypes.c_void_p,
                                        ctypes.c_void_p, wintypes.DWORD, ptr]
    crypt.CryptProtectData.restype = crypt.CryptUnprotectData.restype = wintypes.BOOL
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    buffer = (ctypes.c_ubyte * len(data)).from_buffer_copy(data)
    source, result = Blob(len(data), buffer), Blob()
    fn = crypt.CryptUnprotectData if decrypt else crypt.CryptProtectData
    if not fn(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(result)):
        raise StorageFailure('Windows şifreli depoyu açamadı.')
    try:
        return ctypes.string_at(result.pbData, result.cbData)
    finally:
        kernel.LocalFree(result.pbData)


def atomic_write(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary:
            temporary.unlink(missing_ok=True)


class Vault:
    def __init__(self, directory, *, catalog=False):
        self.catalog = catalog
        name = 'accounts' if catalog else 'tracker'
        self.path = Path(directory) / (name + '.vault')
        self.backup = Path(directory) / (name + '.previous.vault')

    def load(self):
        if not self.path.exists():
            return None
        try:
            if self.path.stat().st_size > MAX_VAULT:
                raise StorageFailure('Şifreli depo boyut sınırını aşıyor.')
            value = json.loads(protect(self.path.read_bytes(), decrypt=True))
            fields = ('accounts', 'gate', 'preferences') if self.catalog else ('people', 'lists', 'gate')
            if (not isinstance(value, dict) or value.get('version') != 1
                    or any(not isinstance(value.get(key), dict) for key in fields)):
                raise StorageFailure('Şifreli depo biçimi desteklenmiyor.')
            return value
        except StorageFailure:
            raise
        except Exception as exc:
            raise StorageFailure('Şifreli depo okunamadı. Mevcut dosya korundu.') from exc

    def save(self, value, *, replace_previous=False):
        try:
            raw = json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode()
            if len(raw) > MAX_VAULT - 4096:
                raise StorageFailure('Yerel depo doldu; kayıtları dışa aktararak yer aç.')
            encrypted = protect(raw)
            if self.path.exists():
                # Explicit erasure must not retain deleted archive content in
                # the automatic recovery copy. Both files remain encrypted.
                atomic_write(self.backup, encrypted if replace_previous else self.path.read_bytes())
            atomic_write(self.path, encrypted)
        except StorageFailure:
            raise
        except Exception as exc:
            raise StorageFailure('Şifreli kayıt yazılamadı; disk alanını kontrol et.') from exc
