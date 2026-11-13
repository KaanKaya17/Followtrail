"""Watch a local folder for Instagram relationship exports for one account."""
import argparse
from collections import defaultdict
from datetime import date
import json
from pathlib import Path
import re
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'interface' / 'desktop' / 'backend'))
from instagram_archive import InvalidArchive, archive_name, read_archive


DIRECTIONS = ('followers', 'following')
MAX_EXPORTS = 128


class ExportWatcher:
    def __init__(self, directory, account, *, mode, show_users=False):
        self.directory = directory
        self.filename = re.compile(
            rf'instagram-{re.escape(account)}-(\d{{4}}-\d{{2}}-\d{{2}})-[a-z0-9]+(?: \(\d+\))?\.zip',
            re.IGNORECASE,
        )
        self.mode = mode
        self.show_users = show_users
        self.seen = {}
        self.reported = set()
        self.last_date = None
        self.baselines = {}

    def scan(self, *, once=False):
        current = {}
        groups = defaultdict(list)
        for path in self.directory.iterdir():
            match = self.filename.fullmatch(path.name)
            if not match or path.is_symlink() or not path.is_file():
                continue
            try:
                day = date.fromisoformat(match[1])
                current[path] = self.revision(path)
            except (ValueError, OSError):
                continue
            if len(current) > MAX_EXPORTS:
                raise ValueError('Seçilen hesap için en fazla 128 arşiv içeren bir klasör kullan.')
            groups[day].append(path)

        previous, self.seen = self.seen, current
        self.reported.intersection_update(current.items())
        for day, files in sorted(groups.items()):
            if not once and any(previous.get(path) != current[path] for path in files):
                continue
            pending = [(path, current[path]) for path in files if (path, current[path]) not in self.reported]
            if not pending:
                continue
            if len(files) > 1:
                self.reported.update(pending)
                yield {'status': 'ambiguous_date', 'date': day.isoformat(), 'files': len(files)}
                continue
            path, revision = pending[0]
            if self.last_date is not None and day <= self.last_date:
                self.reported.add((path, revision))
                yield {'status': 'older_or_same_date', 'date': day.isoformat()}
                continue
            try:
                data = read_archive(path)
                if self.revision(path) != revision:
                    continue
            except InvalidArchive as error:
                # A cloud client may hold a file lock briefly after it appears.
                # Retry locked files at the local polling interval.
                if isinstance(error.__cause__, PermissionError):
                    continue
                self.reported.add((path, revision))
                yield {'status': 'unreadable', 'date': day.isoformat()}
                continue
            except OSError:
                continue
            self.reported.add((path, revision))
            yield self.accept(day, data)

    @staticmethod
    def revision(path):
        info = path.stat()
        return info.st_size, info.st_mtime_ns

    def accept(self, day, data):
        self.last_date = day
        summary = {}
        for direction in DIRECTIONS:
            members = data[direction]
            if members is None:
                summary[direction] = {'status': 'missing'}
                continue
            if self.mode == 'incremental':
                summary[direction] = {'status': 'delivered', 'count': len(members)}
                continue
            now = {row['username'] for row in members}
            previous = self.baselines.get(direction)
            self.baselines[direction] = day, now
            if previous is None:
                summary[direction] = {'status': 'baseline', 'count': len(now)}
                continue
            previous_day, then = previous
            added, removed = now - then, then - now
            result = {'status': 'changed' if added or removed else 'unchanged',
                      'previous_date': previous_day.isoformat(), 'count': len(now),
                      'added': len(added), 'removed': len(removed)}
            if self.show_users:
                result.update(added_users=sorted(added), removed_users=sorted(removed))
            summary[direction] = result
        return {'status': 'processed', 'date': day.isoformat(), 'lists': summary}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, help='Local folder with exports from one test account.')
    parser.add_argument('--account', required=True, help='Instagram username shown in the export filename.')
    parser.add_argument('--mode', choices=('snapshot', 'incremental'), required=True,
                        help='Choose full snapshots or scheduled deliveries.')
    parser.add_argument('--once', action='store_true', help='Read existing exports and exit.')
    parser.add_argument('--interval', type=float, default=5, help='Local polling interval in seconds.')
    parser.add_argument('--show-users', action='store_true', help='Include changed usernames in local output.')
    args = parser.parse_args()
    account = archive_name(args.account.removeprefix('@'))
    if not args.directory.is_dir() or not account or not 1 <= args.interval <= 60:
        parser.error('Geçerli bir klasör, kullanıcı adı ve 1–60 saniye kontrol aralığı gir.')
    if args.mode == 'incremental' and args.show_users:
        parser.error('--show-users yalnız tam arşiv karşılaştırmasında kullanılabilir.')
    watcher = ExportWatcher(args.directory, account, mode=args.mode, show_users=args.show_users)
    try:
        while True:
            events = list(watcher.scan(once=args.once))
            for event in events:
                print(json.dumps(event, ensure_ascii=False), flush=True)
            if args.once:
                if not events:
                    print(json.dumps({'status': 'no_matching_exports'}))
                return
            time.sleep(args.interval)
    except KeyboardInterrupt:
        return
    except (OSError, ValueError) as error:
        parser.exit(2, f'{error}\n')


if __name__ == '__main__':
    main()
