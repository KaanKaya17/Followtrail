"""Check the built APK, not source assumptions. No account/device data is read."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


def verify(apk: Path, sdk: Path) -> dict:
    aapt = sdk / 'build-tools' / '36.0.0' / ('aapt.exe' if os.name == 'nt' else 'aapt')
    def dump(*args):
        return subprocess.run([str(aapt), 'dump', *args], check=True,
                              capture_output=True, text=True, encoding='utf-8').stdout
    badging = dump('badging', str(apk))
    manifest = dump('xmltree', str(apk), 'AndroidManifest.xml')
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package[1] != 'dev.kaankaya.instagramtracker':
        raise ValueError('Expected the production package')
    if re.findall(r"^uses-permission: name='([^']+)'", badging, re.M) != ['android.permission.INTERNET']:
        raise ValueError('Unexpected production permissions')
    for attribute in ('allowBackup', 'usesCleartextTraffic'):
        if not re.search(r'android:' + attribute + r'\([^)]*\)=\(type 0x12\)0x0\s', manifest):
            raise ValueError(attribute + ' must be explicitly disabled')
    if 'application-debuggable' in badging or re.search(r'android:debuggable\([^)]*\)=\(type 0x12\)0xffffffff', manifest):
        raise ValueError('Production APK must not be debuggable')
    if "sdkVersion:'24'" not in badging or "targetSdkVersion:'36'" not in badging:
        raise ValueError('Unexpected Android API range')
    with zipfile.ZipFile(apk) as archive:
        if any(name.startswith('lib/') for name in archive.namelist()):
            raise ValueError('Unexpected native architecture dependency')
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'package': package[1], 'versionCode': int(package[2]), 'version': package[3],
            'bytes': apk.stat().st_size, 'sha256': digest,
            'permissions': ['INTERNET'], 'debuggable': False, 'backup': False,
            'cleartext': False, 'nativeLibraries': 0,
            'scope': 'Packaged manifest and architecture check; not live account or penetration testing.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    args = parser.parse_args()
    if args.sdk is None:
        parser.error('Set ANDROID_HOME or supply --sdk')
    print(json.dumps(verify(args.apk, args.sdk), indent=2))
