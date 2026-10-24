"""Normalize resource settings in the disposable CI AVD before its first boot."""
import os
from pathlib import Path
import shutil


SETTINGS = {'disk.dataPartition.size': '2147483648', 'hw.ramSize': '1536', 'hw.cpu.ncore': '2'}


def normalize(text: str) -> str:
    # The runner action appends overrides to an existing flat key=value file.
    # Keep one value per owned key instead of depending on duplicate-key precedence.
    lines = [line for line in text.splitlines() if line.partition('=')[0].strip() not in SETTINGS]
    return '\n'.join(lines + [f'{key}={value}' for key, value in SETTINGS.items()]) + '\n'


if __name__ == '__main__':
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise SystemExit('This script only configures the disposable GitHub Actions AVD.')
    path = Path(os.environ['ANDROID_AVD_HOME']) / 'test.avd' / 'config.ini'
    original = path.read_text()
    backup = path.with_name('config.ini.before-instagramtracker')
    if not backup.exists():
        shutil.copy2(path, backup)
    configured = normalize(original)
    path.write_text(configured)
    for key, value in SETTINGS.items():
        assert [line for line in path.read_text().splitlines() if line.partition('=')[0].strip() == key] == [f'{key}={value}']
        print(f'CI AVD: {key}={value}')
