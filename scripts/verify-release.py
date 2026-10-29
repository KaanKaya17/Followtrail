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
    permissions = ['INTERNET', 'ACCESS_NETWORK_STATE', 'RECEIVE_BOOT_COMPLETED', 'POST_NOTIFICATIONS']
    if set(re.findall(r"^uses-permission: name='([^']+)'", badging, re.M)) != {'android.permission.' + name for name in permissions}:
        raise ValueError('Unexpected production permissions')
    service_blocks = re.split(r'(?m)^\s*E: service ', manifest)[1:]
    if len(service_blocks) != 1 or 'MonitorJobService' not in service_blocks[0] or 'android.permission.BIND_JOB_SERVICE' not in service_blocks[0]:
        raise ValueError('Expected one system-protected background job service')
    labs = [block for block in re.split(r'(?m)^\s*E: activity ', manifest)[1:] if 'ConnectionLabActivity' in block]
    if len(labs) != 1 or not re.search(r'android:exported\([^)]*\)=\(type 0x12\)0x0\s', labs[0]):
        raise ValueError('Connection prototype must be non-exported')
    links = [block for block in re.split(r'(?m)^\s*E: activity ', manifest)[1:] if 'DesktopLinkActivity' in block]
    if len(links) != 1 or not re.search(r'android:exported\([^)]*\)=\(type 0x12\)0x0\s', links[0]):
        raise ValueError('Desktop pairing must be non-exported')
    phones = [block for block in re.split(r'(?m)^\s*E: activity ', manifest)[1:] if 'PhoneLoginActivity' in block]
    if len(phones) != 1 or not re.search(r'android:exported\([^)]*\)=\(type 0x12\)0x0\s', phones[0]):
        raise ValueError('Phone sign-in must be non-exported')
    lists = [block for block in re.split(r'(?m)^\s*E: activity ', manifest)[1:] if 'FollowListActivity' in block]
    if len(lists) != 1 or not re.search(r'android:exported\([^)]*\)=\(type 0x12\)0x0\s', lists[0]):
        raise ValueError('Follow lists must be non-exported')
    members = [block for block in re.split(r'(?m)^\s*E: activity ', manifest)[1:] if 'MemberProfileActivity' in block]
    if len(members) != 1 or not re.search(r'android:exported\([^)]*\)=\(type 0x12\)0x0\s', members[0]):
        raise ValueError('Member profiles must be non-exported')
    timelines = [block for block in re.split(r'(?m)^\s*E: activity ', manifest)[1:] if 'TimelineActivity' in block]
    if len(timelines) != 1 or not re.search(r'android:exported\([^)]*\)=\(type 0x12\)0x0\s', timelines[0]):
        raise ValueError('Local history must be non-exported')
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
        for name in archive.namelist():
            if name.endswith('.dex') and any(marker in archive.read(name) for marker in (b'offline-demo', b'PreviewMode$DemoConnection', b'DemoMonitorJobService', b'DemoFollowListSource', b'DemoAvatarFetcher', b'demo-page-2', b'tracker-fixture.invalid', b'tracker-web-login-fixture', b'WebRelayFixtureActivity')):
                raise ValueError('Debug preview transport must not be in the production APK')
    with apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return {'package': package[1], 'versionCode': int(package[2]), 'version': package[3],
            'bytes': apk.stat().st_size, 'sha256': digest,
            'permissions': permissions, 'debuggable': False, 'backup': False,
            'cleartext': False, 'nativeLibraries': 0, 'debugPreviewTransport': False,
            'scope': 'Packaged manifest and architecture check; not live account or penetration testing.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    args = parser.parse_args()
    if args.sdk is None:
        parser.error('Set ANDROID_HOME or supply --sdk')
    print(json.dumps(verify(args.apk, args.sdk), indent=2))
