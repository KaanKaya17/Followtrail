"""Private pipe protocol; no TCP listener, credentials or raw response logs."""
import argparse
import json
import logging
import os
from pathlib import Path
import sys
import threading
import uuid

from accounts import Accounts
import localization
from network import AppFailure, failure
from search_images import SearchImages

logging.disable(logging.CRITICAL)
sys.stdin.reconfigure(encoding='utf-8')
sys.stdout.reconfigure(encoding='utf-8', line_buffering=True)
write_lock = threading.Lock()


def emit(value):
    with write_lock:
        sys.stdout.write(json.dumps(value, ensure_ascii=False, separators=(',', ':'), allow_nan=False) + '\n')
        sys.stdout.flush()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--data-dir', required=True, type=Path)
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    active = threading.Lock()
    try:
        engine = Accounts(args.data_dir, emit)
        engine.offline = args.offline
    except Exception:
        emit({'type': 'fatal', 'code': 'storage', 'message': 'Şifreli depo açılamadı. Dosyalar korundu; otomatik sıfırlanmadı.'})
        return 2
    emit({'type': 'ready', 'state': engine.snapshot()})
    images = SearchImages(emit)

    def execute(request):
        group_id = uuid.uuid4().hex
        try:
            state = engine.run(request['operation'], request.get('args') or {}, group_id=group_id)
            emit({'type': 'result', 'id': request['id'], 'ok': True, 'state': state,
                  'notifications': engine.notifications, 'notificationGroupId': group_id})
        except Exception as exc:
            emit({'type': 'result', 'id': request['id'], 'ok': False,
                  'error': localization.error(failure(exc), engine.data['settings']['language']), 'state': engine.snapshot(),
                  'notifications': engine.notifications, 'notificationGroupId': group_id})
        finally:
            active.release()

    for line in sys.stdin:
        request = None
        try:
            if len(line) > 65536:
                raise ValueError()
            request = json.loads(line)
            if not isinstance(request, dict) or not isinstance(request.get('id'), int):
                raise ValueError()
            operation = request.get('operation')
            if operation == 'search_avatar':
                images.request(request, engine.search_results, engine.search_pictures, offline=args.offline)
            elif operation == 'cancel_search_images':
                images.cancel()
                emit({'type': 'control', 'id': request['id'], 'ok': True})
            elif operation == 'cancel':
                engine.cancel.set()
                emit({'type': 'control', 'id': request['id'], 'ok': True})
            elif operation == 'code':
                engine.submit_code(request.get('args', {}).get('code'))
                emit({'type': 'control', 'id': request['id'], 'ok': True})
            elif not active.acquire(blocking=False):
                emit({'type': 'control', 'id': request['id'], 'ok': False,
                      'error': {'code': 'busy', 'message': 'Önce devam eden işlemi bitir veya iptal et.'}})
            else:
                engine.cancel.clear()
                threading.Thread(target=execute, args=(request,), daemon=True).start()
        except Exception as exc:
            emit({'type': 'control', 'id': request.get('id') if isinstance(request, dict) else None,
                  'ok': False, 'error': localization.error(failure(exc), engine.data['settings']['language'])})
    engine.cancel.set()
    images.close()
    active.acquire(timeout=28)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
