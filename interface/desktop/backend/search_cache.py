"""Bounded, process-only search facts and their private thumbnail sources."""
from collections import OrderedDict
import copy
import threading
import time
import uuid
from search_cache_policy import TTL_SECONDS, MAX_QUERIES, MAX_RESULTS


class SearchCache:
    def __init__(self,clock=time.monotonic):
        self.clock=clock;self.lock=threading.RLock();self.owner=None
        self.entries=OrderedDict();self.revision=uuid.uuid4().hex

    def clear(self):
        with self.lock:
            self.owner=None;self.entries.clear();self.revision=uuid.uuid4().hex

    def bind(self,owner):
        with self.lock:
            if self.owner!=owner:
                if self.owner is not None:self.clear()
                self.owner=owner

    def _get(self,owner,query):
        if owner!=self.owner:return None
        entry=self.entries.get(query)
        if entry is None:return None
        age=self.clock()-entry['at']
        if age<0 or age>=TTL_SECONDS:
            del self.entries[query];return None
        return entry

    def get(self,owner,query):
        with self.lock:
            entry=self._get(owner,query)
            if not entry:return None
            return dict(copy.deepcopy(entry['result']),remainingMs=max(0,int((TTL_SECONDS-(self.clock()-entry['at']))*1000)))

    def put(self,owner,query,users,pictures):
        with self.lock:
            self.bind(owner)
            result=dict(query=query,token=uuid.uuid4().hex,users=copy.deepcopy(users[:MAX_RESULTS]))
            self.entries.pop(query,None)
            self.entries[query]=dict(at=self.clock(),result=result,pictures=copy.deepcopy(pictures))
            while len(self.entries)>MAX_QUERIES:self.entries.popitem(last=False)
            return dict(copy.deepcopy(result),remainingMs=int(TTL_SECONDS*1000))

    def picture(self,owner,token,identity):
        with self.lock:
            for query in list(self.entries):
                entry=self._get(owner,query)
                if entry and entry['result']['token']==token and entry['pictures'].get(identity):
                    return {'token':token},{identity:entry['pictures'][identity]}
            return None
