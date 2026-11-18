"""Private pipe protocol; no TCP listener, credentials or raw response logs."""
import argparse
import json
import logging
import os
from pathlib import Path
import sys
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

from accounts import Accounts
import localization
from instagram_archive import InvalidArchive, read_archive
from export_browser import ExportBrowser
import portable_backup
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
    archive = ExportBrowser()
    portable = portable_backup.Archive()
    archive_operations = {
        'export_browser_open': archive.open,
        'export_browser_text': archive.text,
        'export_browser_browse': archive.browse,
        'export_browser_summary': archive.read_summary,
        'export_browser_content': archive.content,
        'export_browser_search': archive.search,
        'export_browser_compare': archive.compare,
        'export_browser_prepare_comparison': archive.prepare_comparison,
        'export_browser_comparison_page': archive.comparison_page,
        'export_browser_comparison_csv': archive.comparison_csv,
        'export_browser_lists': archive.lists,
        'export_browser_media': archive.media,
        'export_browser_save': archive.save,
        'export_browser_close': archive.release,
        'export_browser_portable_open': portable.open,
        'export_browser_portable_page': portable.page,
        'export_browser_portable_close': portable.close,
    }
    archive_tasks = ThreadPoolExecutor(max_workers=1, thread_name_prefix='archive')
    archive_slots = threading.BoundedSemaphore(4)

    def browse(request):
        try:
            value = archive_operations[request['operation']](**(request.get('args') or {}))
        except Exception:
            emit({'type': 'control', 'id': request['id'], 'ok': False, 'error': {'code': 'archive_entry'}})
        else:
            emit({'type': 'control', 'id': request['id'], 'ok': True, 'archiveBrowser': value})
        finally:
            archive_slots.release()
    emit({'type': 'ready', 'state': history.publish(engine.snapshot())})
    images = Thumbnails(emit)

    def execute(request):
        group_id = uuid.uuid4().hex
        original_owner = engine.active_id
        def publish(message):
            if engine.active_id != original_owner:images.cancel(clear_cache=True)
            emit(message)
        try:
            if request['operation'] == 'export_portable_backup':
                portable_backup.export(engine, **request['args'])
                emit({'type': 'result', 'id': request['id'], 'ok': True})
                return
            if request['operation'] == 'import_instagram_zip':
                try:
                    value = read_archive((request.get('args') or {}).get('path'))
                except InvalidArchive as exc:
                    raise AppFailure('instagram_archive', str(exc)) from exc
                emit({'type': 'result', 'id': request['id'], 'ok': True, 'instagramArchive': value})
                return
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
            if request['operation'] == 'export_portable_backup':
                emit({'type': 'result', 'id': request['id'], 'ok': False, 'error': {'code': 'backup'}})
                return
            if request['operation'] == 'import_instagram_zip':
                emit({'type': 'result', 'id': request['id'], 'ok': False, 'error': failure(exc)})
                return
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
            if operation in archive_operations:
                if archive_slots.acquire(blocking=False):
                    archive_tasks.submit(browse, request)
                else:
                    emit({'type': 'control', 'id': request['id'], 'ok': False, 'error': {'code': 'busy'}})
            elif operation in ('history_page', 'history_detail'):
                value = history.page(request.get('args')) if operation == 'history_page' else history.detail(request.get('args'))
                emit({'type':'control', 'id':request['id'], 'ok':True, 'page' if operation == 'history_page' else 'history':value})
            elif operation == 'daily_stats':
                emit({'type':'control', 'id':request['id'], 'ok':True, 'daily':engine.daily_query(request.get('args'))})
            elif operation == 'list_page':
                emit({'type':'control', 'id':request['id'], 'ok':True, 'page':engine.list_pages.page(request.get('args'))})
            elif operation == 'list_membership':
                emit({'type':'control', 'id':request['id'], 'ok':True, 'membership':engine.list_pages.membership(request.get('args'))})
            elif operation == 'search_avatar':
                # Automatic work temporarily activates other vaults; images belong to the UI selection.
                owner=engine.catalog['active']
                source=engine.search_cache.picture(owner,(request.get('args') or {}).get('token'),(request.get('args') or {}).get('id'))
                result,pictures=source if source else (None,{})
                images.request(request,result,pictures,namespace=owner,offline=args.offline)
            elif operation == 'cancel_search_images':
                images.cancel('search')
                emit({'type': 'control', 'id': request['id'], 'ok': True})
            elif operation == 'list_avatar':
                source = engine.list_pages.picture(request.get('args') or {})
                if source:
                    owner, scope, identity, picture = source
                    token = str(owner)+':'+scope
                    normalized = {**request, 'args':{'token':token,'id':identity}}
                    images.request(normalized, {'token':token}, {identity:picture}, namespace=owner, group='list', offline=args.offline)
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
    archive_tasks.shutdown(wait=True, cancel_futures=True)
    archive.close()
    images.close()
    active.acquire(timeout=28)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
