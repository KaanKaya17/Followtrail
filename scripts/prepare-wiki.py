"""Validate the curated guide offline and prepare a GitHub Wiki export; never publish it.

The guide uses inline Markdown links. Fenced code and inline code are excluded from
link processing. No dependency installation, network access or Git writes are used.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import unicodedata
from urllib.parse import quote, unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / 'docs/wiki'
MANIFEST = '.wiki-export.json'
OWNER = 'InstagramTrackerWikiExport-v1'
LINK = re.compile(r'(!?)\[([^\]\n]*)\]\((<[^>\n]+>|[^\s)]+)\)')


def links(text):
    """Yield supported inline links with original offsets, ignoring code examples."""
    offset = 0
    fence = None
    for line in text.splitlines(keepends=True):
        stripped = line.lstrip()
        marker = re.match(r'(`{3,}|~{3,})', stripped)
        if marker:
            token = marker.group(1)
            if fence is None:
                fence = token
            elif token[0] == fence[0] and len(token) >= len(fence):
                fence = None
        elif fence is None:
            masked = re.sub(r'(`+).*?\1', lambda m: ' ' * len(m.group()), line)
            for match in LINK.finditer(masked):
                start, end = offset + match.start(3), offset + match.end(3)
                yield start, end, text[start:end].strip('<>'), bool(match.group(1)), match.group(2)
        offset += len(line)


def anchors(path):
    text = path.read_text(encoding='utf-8-sig')
    result = set(re.findall(r'<a\s+(?:name|id)="([^"]+)"', text))
    seen = {}
    fence = False
    for line in text.splitlines():
        if line.lstrip().startswith(('```', '~~~')):
            fence = not fence
        if fence:
            continue
        match = re.match(r'^#{1,6}\s+(.+)', line)
        if not match:
            continue
        title = re.sub(r'<[^>]+>|[`*_]', '', match.group(1)).strip().lower()
        base = ''.join(c for c in title if c in ' -_' or unicodedata.category(c)[0] in 'LNM')
        base = base.replace(' ', '-')
        count = seen.get(base, 0)
        result.add(base + (f'-{count}' if count else ''))
        seen[base] = count + 1
    return result


def destination(source, url):
    parsed = urlsplit(url)
    if parsed.scheme or parsed.netloc:
        if parsed.scheme not in ('http', 'https', 'mailto'):
            raise ValueError(f'{source.name}: unsupported link scheme: {url}')
        return None, parsed.fragment
    target = (source.parent / unquote(parsed.path)).resolve() if parsed.path else source
    if not target.is_relative_to(ROOT) or not target.is_file():
        raise ValueError(f'{source.relative_to(ROOT)}: missing local target: {url}')
    return target, unquote(parsed.fragment)


def validate():
    pages = sorted(GUIDE.glob('*.md'))
    if not pages or not all((GUIDE / name).is_file() for name in ('Home.md', '_Sidebar.md', '_Footer.md')):
        raise ValueError('Home, sidebar and footer are required')
    readmes = [ROOT / name for name in ('README.md', 'README.en.md', 'README.es.md', 'README.pt-BR.md')]
    sources = [*pages, *readmes, ROOT / 'docs/README.md', ROOT / 'docs/history/README-2026-11-02.md']
    assets = set()
    count = 0
    for source in sources:
        text = source.read_text(encoding='utf-8-sig')
        if source.parent == GUIDE and re.search(r'\b(?:TODO|FIXME|TBD)\b|\ufffd', text):
            raise ValueError(f'{source.name}: unfinished text or broken Unicode')
        if source.parent == GUIDE and re.search(r'[A-Za-z]:[\\/]Users[\\/]', text):
            raise ValueError(f'{source.name}: machine-specific personal path')
        for _, _, url, image, label in links(text):
            target, fragment = destination(source, url)
            count += 1
            if image and not label.strip():
                raise ValueError(f'{source.name}: image needs alternative text')
            if target and fragment and target.suffix.lower() == '.md' and fragment not in anchors(target):
                raise ValueError(f'{source.name}: missing section #{fragment} in {target.name}')
            if target and image and target.is_relative_to(GUIDE / 'assets'):
                assets.add(target)
    android = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text()).group(1)
    windows = json.loads((ROOT / 'interface/package.json').read_text())['version']
    for source in (ROOT / 'README.md', GUIDE / 'Surum-ve-Destek.md', GUIDE / 'EN-Releases.md'):
        text = source.read_text(encoding='utf-8-sig')
        if android not in text or windows not in text:
            raise ValueError(f'{source.name}: release numbers differ from application sources')
    print(f'Validated {len(pages)} guide/navigation pages, {len(assets)} images and {count} links; no network requests.')
    return pages, sorted(assets)


def current_ref():
    ref = subprocess.check_output(['git', 'branch', '--show-current'], cwd=ROOT, text=True).strip()
    return ref or subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()


def wiki_text(source, repository, ref):
    text = source.read_text(encoding='utf-8')
    replacements = []
    for start, end, url, image, _ in links(text):
        target, fragment = destination(source, url)
        if target is None:
            continue
        if target.parent == GUIDE and target.suffix == '.md' and not image:
            address = f'https://github.com/{repository}/wiki/{quote(target.stem)}'
        else:
            address = f'https://github.com/{repository}/blob/{quote(ref, safe="/")}/{quote(target.relative_to(ROOT).as_posix(), safe="/")}'
            if image:
                address += '?raw=true'
        if fragment:
            address += '#' + quote(fragment, safe='-._')
        replacements.append((start, end, address))
    for start, end, value in reversed(replacements):
        text = text[:start] + value + text[end:]
    return text.encode('utf-8')


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def export(output, pages, assets, repository, ref):
    output = output.resolve()
    if output == ROOT or GUIDE.is_relative_to(output) or output.is_relative_to(GUIDE):
        raise ValueError('Export destination must be separate from the guide source')
    manifest = output / MANIFEST
    if manifest.is_file():
        previous = json.loads(manifest.read_text(encoding='utf-8'))
        if previous.get('owner') != OWNER or not isinstance(previous.get('files'), dict):
            raise ValueError('Existing output has no recognized ownership manifest')
    else:
        if output.exists() and any(output.iterdir()):
            raise ValueError('Refusing to overwrite a non-empty, unmanaged export directory')
        previous = {'files': {}}
    payload = {page.name: wiki_text(page, repository, ref) for page in pages}
    payload.update({asset.relative_to(GUIDE).as_posix(): asset.read_bytes() for asset in assets})
    if set(previous['files']) - set(payload):
        raise ValueError('Source pages were removed: export to a fresh directory; no files were deleted')
    changed = []
    for relative, raw in payload.items():
        target = output / relative
        if target.exists():
            old = target.read_bytes()
            if old == raw:
                continue
            if digest(old) != previous['files'].get(relative):
                raise ValueError(f'Refusing to overwrite edited or unowned file: {relative}')
        changed.append((relative, raw))
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    for relative, raw in changed:
        target = output / relative
        if target.exists():
            backup = output / '.wiki-backups' / stamp / relative
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, backup)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
    record = {'owner': OWNER, 'repository': repository, 'ref': ref, 'files': {name: digest(raw) for name, raw in sorted(payload.items())}}
    serialized = (json.dumps(record, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    if not manifest.exists() or manifest.read_bytes() != serialized:
        if manifest.exists():
            backup = output / '.wiki-backups' / stamp / MANIFEST
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(manifest, backup)
        output.mkdir(parents=True, exist_ok=True)
        manifest.write_bytes(serialized)
    print(f'Prepared {len(payload)} files in {output}; {len(changed)} changed. No Wiki setting, visibility or remote was modified.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--check', action='store_true', help='validate sources without exporting')
    mode.add_argument('--output', type=Path, help='empty or previously owned export directory')
    parser.add_argument('--repository', default='KaanKaya17/Followtrail', help='GitHub OWNER/REPOSITORY')
    parser.add_argument('--ref', help='source reference for repository/image links; defaults to current Git branch')
    args = parser.parse_args()
    try:
        if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repository):
            raise ValueError('Repository must be OWNER/REPOSITORY')
        pages, assets = validate()
        if args.output:
            export(args.output, pages, assets, args.repository, args.ref or current_ref())
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'Wiki preparation failed: {error}\n')


if __name__ == '__main__':
    main()
