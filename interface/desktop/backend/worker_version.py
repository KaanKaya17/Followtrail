"""Write the PyInstaller version resource for tracker-worker.exe.

Code signing (SignPath) requires every signed binary to carry the project's
product name and the release version, the same as Followtrail.exe.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = """VSVersionInfo(
  ffi=FixedFileInfo(filevers={numbers}, prodvers={numbers}, mask=0x3f, flags=0x0, OS=0x40004,
                    fileType=0x1, subtype=0x0, date=(0, 0)),
  kids=[
    StringFileInfo([StringTable('040904B0', [
      StringStruct('CompanyName', 'Followtrail'),
      StringStruct('FileDescription', 'Followtrail local data service'),
      StringStruct('FileVersion', '{version}'),
      StringStruct('InternalName', 'tracker-worker'),
      StringStruct('LegalCopyright', 'MIT License'),
      StringStruct('OriginalFilename', 'tracker-worker.exe'),
      StringStruct('ProductName', 'Followtrail'),
      StringStruct('ProductVersion', '{version}')])]),
    VarFileInfo([VarStruct('Translation', [1033, 1200])])
  ]
)
"""


def render(version):
    if not re.fullmatch(r'\d{1,5}\.\d{1,5}\.\d{1,5}', version):
        raise ValueError(f'Invalid version: {version}')
    numbers = tuple(map(int, version.split('.'))) + (0,)
    if any(number > 65535 for number in numbers):
        raise ValueError(f'Invalid version: {version}')
    return TEMPLATE.format(numbers=numbers, version=version)


def main():
    version = json.loads((ROOT / 'package.json').read_text(encoding='utf-8'))['version']
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / '.cache' / 'worker-version.txt'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(render(version), encoding='utf-8')
    print(target)


if __name__ == '__main__':
    main()
