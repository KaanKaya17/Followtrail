"""Reference results from the desktop app's Python reader, for parity.test.mjs.

  python python_reference.py <cases.json>

cases.json is a list of {"zip": path, "other": path|null}. For each case this
prints read_relationships(zip) (or {"error": true}) and, when "other" is set,
the full comparison that ExportBrowser.compare computes for (zip -> other).
"""
import json
import sys
from pathlib import Path
from zipfile import ZipFile

BACKEND = Path(__file__).resolve().parents[2] / 'interface' / 'desktop' / 'backend'
sys.path.insert(0, str(BACKEND))

from export_browser import ExportBrowser  # noqa: E402
from instagram_archive import read_relationships  # noqa: E402


def relationships(path):
    with ZipFile(path) as archive:
        try:
            return read_relationships(archive)
        except Exception:  # The JS reader reports the same cases as an error.
            return {'error': True}


def comparison(first, second):
    browser = ExportBrowser()
    try:
        token = browser.open(first)['token']
        pair = browser.prepare_comparison(token, second)
        try:
            outcome = browser.compare(token, pair['candidate'])
        except ValueError:
            return {'error': True}
        return {'missing': outcome['missing'], 'directions': browser.comparison}
    except ValueError:
        return {'error': True}
    finally:
        browser.close()


def main():
    cases = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
    results = []
    for case in cases:
        result = {'relationships': relationships(case['zip'])}
        if case.get('other'):
            result['comparison'] = comparison(case['zip'], case['other'])
        results.append(result)
    sys.stdout.reconfigure(encoding='utf-8')
    print(json.dumps(results, ensure_ascii=False))


if __name__ == '__main__':
    main()
