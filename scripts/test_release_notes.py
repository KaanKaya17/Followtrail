import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name('release-notes.py')
spec = importlib.util.spec_from_file_location('release_notes', SCRIPT)
release_notes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_notes)

CHANGELOG = """# Değişiklikler

## Yayımlanmamış

- taslak

## Windows 0.2.96 — 2026-11-20

- yeni özellik

## Windows 0.2.9 — 2026-01-01

- eski

## Windows 0.2.90 ve Android 0.1.103 — 2026-11-18

- ortak
"""


class ReleaseNotesTests(unittest.TestCase):
    def test_section_is_exact_version(self):
        self.assertEqual(release_notes.section(CHANGELOG, '0.2.96'), '- yeni özellik')
        self.assertEqual(release_notes.section(CHANGELOG, '0.2.9'), '- eski')
        self.assertEqual(release_notes.section(CHANGELOG, '0.2.90'), '- ortak')
        with self.assertRaises(SystemExit):
            release_notes.section(CHANGELOG, '0.2.97')

    def run_script(self, *args):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'CHANGELOG.md'
            path.write_text(CHANGELOG, encoding='utf-8')
            return subprocess.run([sys.executable, str(SCRIPT), *args, '--changelog', str(path)],
                                  capture_output=True, text=True, encoding='utf-8')

    def test_tag_must_match_version(self):
        ok = self.run_script('0.2.96', '--tag', 'windows-v0.2.96')
        self.assertEqual(ok.returncode, 0, ok.stderr)
        self.assertIn('- yeni özellik', ok.stdout)
        self.assertIn('gh attestation verify Followtrail-Desktop-0.2.96.zip', ok.stdout)
        self.assertNotEqual(self.run_script('0.2.96', '--tag', 'windows-v0.2.95').returncode, 0)
        self.assertNotEqual(self.run_script('latest').returncode, 0)


if __name__ == '__main__':
    unittest.main()
