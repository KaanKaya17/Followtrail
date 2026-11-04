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
from thumbnails import Thumbnails
from history_pages import HistoryPages, preview_log

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
    history = HistoryPages()
    emit({'type': 'ready', 'state': history.publish(engine.snapshot())})
    images = Thumbnails(emit)

    def execute(request):
        group_id = uuid.uuid4().hex
        original_owner = engine.active_id
        def publish(message):
            if engine.active_id != original_owner:images.cancel(clear_cache=True)
            emit(message)
        try:
            if request['operation'] == 'story_media':
                # On-demand media is never part of a state snapshot or event.
                asset = engine.story_media(request.get('args') or {})
                emit({'type':'result','id':request['id'],'ok':True,'storyMedia':asset})
                return
            if request['operation'] in ('remove_person','delete_all'):images.cancel(clear_cache=True)
            state = engine.run(request['operation'], request.get('args') or {}, group_id=group_id)
            publish({'type': 'result', 'id': request['id'], 'ok': True, 'state': history.publish(state),
                  'notifications': [preview_log(e) for e in engine.notifications], 'notificationGroupId': group_id})
        except Exception as exc:
            publish({'type': 'result', 'id': request['id'], 'ok': False,
                  'error': localization.error(failure(exc), engine.data['settings']['language']), 'state': history.publish(engine.snapshot()),
                  'notifications': [preview_log(e) for e in engine.notifications], 'notificationGroupId': group_id})
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
            if operation in ('history_page', 'history_detail'):
                value = history.page(request.get('args')) if operation == 'history_page' else history.detail(request.get('args'))
                emit({'type':'control', 'id':request['id'], 'ok':True, 'page' if operation == 'history_page' else 'history':value})
            elif operation == 'daily_stats':
                emit({'type':'control', 'id':request['id'], 'ok':True, 'daily':engine.daily_query(request.get('args'))})
            elif operation == 'list_page':
                emit({'type':'control', 'id':request['id'], 'ok':True, 'page':engine.list_pages.page(request.get('args'))})
            elif operation == 'search_avatar':
                owner=engine.active_id
                source=engine.search_cache.picture(owner,(request.get('args') or {}).get('token'),(request.get('args') or {}).get('id'))
                result,pictures=source if source else (None,{})
                images.request(request,result,pictures,namespace=owner,offline=args.offline)
            elif operation == 'cancel_search_images':
                images.cancel('search')
                emit({'type': 'control', 'id': request['id'], 'ok': True})
            elif operation == 'list_avatar':
                source = engine.list_picture(request.get('args') or {})
                if source:
                    scope, identity, picture = source
                    token = str(engine.active_id)+':'+scope
                    normalized = {**request, 'args':{'token':token,'id':identity}}
                    images.request(normalized, {'token':token}, {identity:picture}, namespace=engine.active_id, group='list', offline=args.offline)
                else:
                    emit({'type':'control','id':request['id'],'ok':True,'avatar':None})
            elif operation == 'cancel_list_images':
                images.cancel('list')
                emit({'type':'control','id':request['id'],'ok':True})
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
