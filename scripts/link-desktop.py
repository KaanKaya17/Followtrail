"""One-way USB pairing. No Instagram requests, plaintext session files, or credential output."""
from __future__ import annotations
import argparse
import base64
import json
import logging
from pathlib import Path
import subprocess
import sys
import time

HEADER_NAMES = {
    'Authorization', 'User-Agent', 'X-IG-App-Locale', 'X-IG-Device-Locale', 'X-IG-Mapped-Locale',
    'X-IG-App-Startup-Country', 'X-Bloks-Version-Id', 'X-IG-WWW-Claim', 'X-Bloks-Is-Layout-RTL',
    'X-Bloks-Is-Panorama-Enabled', 'X-IG-Device-ID', 'X-IG-Family-Device-ID', 'X-IG-Android-ID',
    'X-IG-Timezone-Offset', 'X-IG-Connection-Type', 'X-IG-Capabilities', 'X-IG-App-ID', 'Priority', 'Accept-Language',
    'X-MID', 'X-FB-HTTP-Engine', 'X-Tigon-Is-Retry', 'X-FB-Client-IP', 'X-FB-Server-Cluster',
    'IG-INTENDED-USER-ID', 'IG-U-DS-USER-ID', 'IG-U-RUR', 'IG-U-SHBID', 'IG-U-SHBTS',
    'IG-U-IG-DIRECT-REGION-HINT', 'X-IG-Nav-Chain',
}
COOKIE_NAMES = {'sessionid', 'ds_user_id', 'csrftoken', 'mid', 'ig_did', 'rur'}


def payload(client, expires_at):
    # Let the pinned library own interpretation of its saved settings.
    headers = {k: str(v) for k, v in client.private_headers().items() if k in HEADER_NAMES and v is not None}
    owner = str(client.user_id or '')
    if not owner.isascii() or not owner.isdigit() or not 1 <= len(owner) <= 30 or owner.startswith('0'):
        raise ValueError('Invalid owner')
    if not headers.get('Authorization', '').startswith('Bearer IGT:2:'):
        raise ValueError('A mobile authorization session is required')
    state = client.get_usdid_settings() or {}
    private_key = state.get('private_key') or ''
    if private_key:
        from Cryptodome.PublicKey import ECC
        private_key = ECC.import_key(private_key).export_key(format='PEM', use_pkcs8=True)
    return {'version': 1, 'expiresAt': expires_at, 'owner': owner, 'headers': headers,
            'cookies': {k: v for k, v in client.cookie_dict.items() if k in COOKIE_NAMES},
            'usdid': state.get('usdid') or '', 'signingKey': private_key}


def seal(data, request, now):
    from Cryptodome.Cipher import AES, PKCS1_OAEP
    from Cryptodome.Hash import SHA1, SHA256
    from Cryptodome.PublicKey import RSA
    from Cryptodome.Random import get_random_bytes
    from Cryptodome.Signature.pss import MGF1
    nonce = request['requestId']
    if (request['version'] != 1 or len(nonce) != 32 or any(c not in '0123456789abcdef' for c in nonce)
            or not now < request['expiresAt'] <= now + 600):
        raise ValueError('Expired or invalid pairing request')
    public = RSA.import_key(base64.b64decode(request['publicKey'], validate=True))
    if public.has_private() or public.size_in_bits() != 3072:
        raise ValueError('Expected device public key')
    raw = json.dumps(data, separators=(',', ':'), ensure_ascii=False).encode()
    if len(raw) > 128 * 1024:
        raise ValueError('Session exceeds pairing limit')
    key = get_random_bytes(32)
    iv = get_random_bytes(12)
    aes = AES.new(key, AES.MODE_GCM, nonce=iv)
    aes.update(f'InstagramTracker/mobile-link/v1/{nonce}'.encode())
    ciphertext, tag = aes.encrypt_and_digest(raw)
    rsa = PKCS1_OAEP.new(public, hashAlgo=SHA256, mgfunc=lambda x, n: MGF1(x, n, SHA1))
    b64 = lambda value: base64.b64encode(value).decode('ascii')
    return json.dumps({'version': 1, 'requestId': nonce, 'wrappedKey': b64(rsa.encrypt(key)),
                       'iv': b64(iv), 'ciphertext': b64(ciphertext + tag)}, separators=(',', ':')).encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--counter-root', type=Path, default=Path.home() / 'Documents' / 'InstagramCounter')
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial')
    parser.add_argument('--debug', action='store_true')
    args = parser.parse_args()
    logging.disable(logging.CRITICAL)
    if args.serial:
        device = ['-s', args.serial]
    else:
        output = subprocess.run([str(args.adb), 'devices'], check=True, capture_output=True, text=True).stdout
        connected = [line.split()[0] for line in output.splitlines()[1:] if line.endswith('\tdevice')]
        if len(connected) != 1:
            raise ValueError('Connect one authorized device, or supply --serial')
        device = ['-s', connected[0]]
    package = 'dev.kaankaya.instagramtracker' + ('.debug' if args.debug else '')
    folder = f'/sdcard/Android/data/{package}/files'
    def adb(*command, **kwargs):
        return subprocess.run([str(args.adb), *device, *command], check=True, capture_output=True, timeout=30, **kwargs)
    raw = adb('exec-out', 'cat', folder + '/mobile-link-public.json').stdout
    if len(raw) > 8192:
        raise ValueError('Invalid pairing request size')
    request = json.loads(raw)
    if request.get('package') != package:
        raise ValueError('Pairing package mismatch')
    sys.path.insert(0, str(args.counter_root.resolve()))
    from importlib.metadata import version
    if version('instagrapi') != '3.2.0':
        raise ValueError('Use the tested instagrapi 3.2.0 environment')
    from instagrapi import Client
    from session_store import restore_session
    client = Client(session_retry_total=0, public_request_retries_count=0)
    try:
        def no_requests(*args, **kwargs):
            raise RuntimeError('Pairing must not send HTTP requests')
        client.private.request = no_requests
        client.public.request = no_requests
        # These operations only restore local settings. No login(), profile read, or request is called.
        restore_session(client)
        data = payload(client, min(int(time.time()) + 600, request['expiresAt']))
        encrypted = seal(data, request, int(time.time()))
    finally:
        client.private.close(); client.public.close()
    # Only ciphertext crosses the USB channel or touches the device's external files directory.
    adb('shell', '-T', 'cat > ' + folder + '/desktop-session.iglink', input=encrypted)
    print('Sifreli oturum USB ile aktarildi. APK icinde "Sifreli aktarimi al" adimina dokun. Instagram istegi gonderilmedi.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Exception strings/tracebacks can contain request bodies or session material.
        print('Aktarim tamamlanamadi (' + type(error).__name__ + '). APK aktarim ekranini hazirla; USB ve yerel oturumu kontrol et.', file=sys.stderr)
        sys.exit(1)
