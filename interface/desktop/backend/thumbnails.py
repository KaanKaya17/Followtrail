"""Bounded, cookie-free thumbnails for owned search and list observations.

The caller supplies only a result token and numeric ID. The current result owns
the URL; no account session or caller-selected address reaches the CDN reader.
"""
from concurrent.futures import ThreadPoolExecutor
from collections import OrderedDict
import threading
import time
from dataclasses import dataclass, field
from network import AppFailure, read_avatar


class ImageContext:
    def __init__(self, cancelled):
        self.cancelled = cancelled
        self.deadline = time.monotonic() + 10

    def check_cancel(self):
        if self.cancelled.is_set() or time.monotonic() > self.deadline:
            raise AppFailure('cancelled', 'Image cancelled')

    def progress(self, *_args, **_kwargs):
        pass


@dataclass(eq=False)
class Flight:
    key: tuple
    cancelled: threading.Event = field(default_factory=threading.Event)
    subscribers: list = field(default_factory=list)
    future: object = None


class Thumbnails:
    def __init__(self, emit, *, reader=read_avatar):
        self.emit, self.reader = emit, reader
        self.pool = ThreadPoolExecutor(max_workers=2, thread_name_prefix='tracker-thumbnail')
        self.lock = threading.RLock()
        self.groups = {}
        self.cache = OrderedDict()
        self.jobs = {}
        self.inflight = {}
        self.closed = False

    def cancel(self, group=None, *, clear_cache=False):
        with self.lock:
            for key in list(self.groups):
                if group is None or group == key:
                    self.groups.pop(key)[1].set()
            for flight in list(self.jobs.values()):
                remaining = []
                for kind, cancelled, reply in flight.subscribers:
                    if group is None or group == kind:
                        reply()
                    else:
                        remaining.append((kind, cancelled, reply))
                flight.subscribers = remaining
                if not remaining:
                    flight.cancelled.set()
                    if self.inflight.get(flight.key) is flight:
                        self.inflight.pop(flight.key)
                    flight.future.cancel()
            if clear_cache:self.cache.clear()

    def request(self, request, result, pictures, *, namespace=None, group='search', offline=False):
        args = request.get('args') or {}
        token, identity = args.get('token'), args.get('id')
        def reply(avatar=None):
            self.emit({'type':'control', 'id':request['id'], 'ok':True, 'avatar':avatar})
        with self.lock:
            if (self.closed or offline or not result or token != result.get('token') or
                    identity not in pictures or not pictures[identity]):
                reply()
                return
            current = self.groups.get(group)
            if current is None or current[0] != token:
                self.cancel(group)
                current = (token, threading.Event())
                self.groups[group] = current
            cancelled = current[1]
            key = (namespace, identity, pictures[identity])
            cached = self.cache.pop(key, None)
            if cached and time.monotonic() - cached[0] < 900:
                self.cache[key] = cached
                reply(cached[1])
                return
            # Include active and queued work in the bound, including old work
            # whose socket is still finishing cancellation.
            if sum(len(job.subscribers) for job in self.jobs.values()) >= 20:
                reply()
                return
            flight = self.inflight.get(key)
            if flight is not None:
                flight.subscribers.append((group, cancelled, reply))
                return
            if len(self.jobs) >= 20:
                reply()
                return
            flight = Flight(key)
            flight.subscribers.append((group, cancelled, reply))
            def load():
                image = {'avatarUrl':pictures[identity]}
                context = ImageContext(flight.cancelled)
                self.reader(image, context)
                return image.get('avatar') if not flight.cancelled.is_set() else None
            future = self.pool.submit(load)
            flight.future = future
            self.jobs[future] = flight
            self.inflight[key] = flight
            def done(completed):
                try:
                    avatar = completed.result()
                except Exception:
                    avatar = None
                with self.lock:
                    self.jobs.pop(completed, None)
                    if self.inflight.get(key) is flight:
                        self.inflight.pop(key)
                    if not flight.cancelled.is_set() and isinstance(avatar,str) and len(avatar)<=100000:
                        self.cache[key] = (time.monotonic(),avatar)
                        while len(self.cache)>64:self.cache.popitem(last=False)
                    subscribers, flight.subscribers = flight.subscribers, []
                    for _, cancelled, reply_to in subscribers:
                        reply_to(avatar if not cancelled.is_set() else None)
            future.add_done_callback(done)

    def close(self):
        with self.lock:
            self.closed = True
            self.cancel(clear_cache=True)
        self.pool.shutdown(wait=True, cancel_futures=True)
