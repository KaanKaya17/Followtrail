"""Bounded, disposable search thumbnails on the private worker pipe.

The caller supplies only a result token and numeric ID. The current result owns
the URL; no account session or caller-selected address reaches the CDN reader.
"""
from concurrent.futures import ThreadPoolExecutor
import threading
import time
from network import AppFailure, read_avatar


class ImageContext:
    def __init__(self):
        self.cancelled = threading.Event()
        self.deadline = time.monotonic() + 10

    def check_cancel(self):
        if self.cancelled.is_set() or time.monotonic() > self.deadline:
            raise AppFailure('cancelled', 'Image cancelled')

    def progress(self, *_args, **_kwargs):
        pass


class SearchImages:
    def __init__(self, emit, *, reader=read_avatar):
        self.emit, self.reader = emit, reader
        self.pool = ThreadPoolExecutor(max_workers=2, thread_name_prefix='search-avatar')
        self.lock = threading.RLock()
        self.context = ImageContext()
        self.token = None
        self.jobs = {}
        self.closed = False

    def cancel(self):
        with self.lock:
            self.context.cancelled.set()
            self.context = ImageContext()
            self.token = None
            for future in list(self.jobs):
                future.cancel()

    def request(self, request, result, pictures, *, offline=False):
        args = request.get('args') or {}
        token, identity = args.get('token'), args.get('id')
        def reply(avatar=None):
            self.emit({'type':'control', 'id':request['id'], 'ok':True, 'avatar':avatar})
        with self.lock:
            if (self.closed or offline or not result or token != result.get('token') or
                    identity not in pictures or not pictures[identity]):
                reply()
                return
            if token != self.token:
                self.cancel()
                self.token = token
            # Include active and queued work in the bound, including old work
            # whose socket is still finishing cancellation.
            if len(self.jobs) >= 20:
                reply()
                return
            context = self.context
            def load():
                image = {'avatarUrl':pictures[identity]}
                self.reader(image, context)
                return image.get('avatar') if not context.cancelled.is_set() else None
            future = self.pool.submit(load)
            self.jobs[future] = context
            def done(completed):
                try:
                    avatar = completed.result()
                except Exception:
                    avatar = None
                with self.lock:
                    self.jobs.pop(completed, None)
                    reply(avatar if not context.cancelled.is_set() else None)
            future.add_done_callback(done)

    def close(self):
        with self.lock:
            self.closed = True
            self.cancel()
        self.pool.shutdown(wait=True, cancel_futures=True)
