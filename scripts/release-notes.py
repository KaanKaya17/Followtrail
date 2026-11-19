"""Print the CHANGELOG section for one Windows version, for the GitHub release body.

Usage: python scripts/release-notes.py 0.2.96 [--tag windows-v0.2.96]
Fails when the section is missing or when the tag does not match the version,
so a release is never published with notes or a tag for another version.
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def section(text, version):
    pattern = re.compile(r'^## Windows ' + re.escape(version) + r'(?![0-9.])[^\n]*\n(.*?)(?=^## |\Z)', re.M | re.S)
    match = pattern.search(text)
    if not match or not match[1].strip():
        raise SystemExit(f'CHANGELOG.md has no "## Windows {version}" section')
    return match[1].strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('version')
    parser.add_argument('--tag')
    parser.add_argument('--changelog', type=Path, default=ROOT / 'CHANGELOG.md')
    args = parser.parse_args()
    if not re.fullmatch(r'\d+\.\d+\.\d+', args.version):
        raise SystemExit(f'Invalid version: {args.version}')
    if args.tag is not None and args.tag != f'windows-v{args.version}':
        raise SystemExit(f'Tag {args.tag} does not match interface/package.json version {args.version}')
    notes = section(args.changelog.read_text(encoding='utf-8'), args.version)
    sys.stdout.reconfigure(encoding='utf-8')
    print(notes)
    print()
    print('**Doğrulama:** `SHA256SUMS.txt` dosyasındaki değerle karşılaştır veya kaynak kanıtını denetle:')
    print(f'`gh attestation verify Followtrail-Desktop-{args.version}.zip -R KaanKaya17/Followtrail`')


if __name__ == '__main__':
    main()
