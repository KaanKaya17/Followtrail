(() => {
  'use strict';
  if (window.top !== window || location.origin !== 'https://www.instagram.com' || !window.trackerLoginRelay) return;
  const bridge = window.trackerLoginRelay, pending = new Map();
  let next = 0;
  const target = value => { try { const u = new URL(value, location.href); return u.href === 'https://www.instagram.com/api/graphql'; } catch (_) { return false; } };
  const send = (body, headers) => {
    let cancel = () => {};
    const promise = new Promise((resolve, reject) => {
    if (typeof body !== 'string' || body.length > 65536 || pending.size) { reject(new TypeError('Login request rejected')); return; }
    const id = String(++next);
    cancel = () => { if (pending.delete(id)) { clearTimeout(timer); bridge.postMessage(JSON.stringify({cancel:id})); reject(new DOMException('Aborted', 'AbortError')); } };
    const timer = setTimeout(cancel, 35000);
    pending.set(id, {resolve, reject, timer});
    bridge.postMessage(JSON.stringify({id, body, headers}));
    });
    promise.cancel = () => cancel(); return promise;
  };
  bridge.onmessage = event => {
    let reply; try { reply = JSON.parse(event.data); } catch (_) { return; }
    const p = pending.get(reply.id); if (!p) return;
    pending.delete(reply.id); clearTimeout(p.timer);
    if (reply.error) p.reject(new TypeError('Login request failed')); else p.resolve(reply);
  };
  const originalFetch = window.fetch;
  window.fetch = function(input, init) {
    const url = input instanceof Request ? input.url : input;
    const method = init && init.method || (input instanceof Request ? input.method : 'GET');
    if (String(method).toUpperCase() !== 'POST' || !target(url)) return originalFetch.apply(this, arguments);
    const request = new Request(input, init);
    if (request.signal.aborted) return Promise.reject(new DOMException('Aborted', 'AbortError'));
    const headers = Object.fromEntries(request.headers.entries());
    return request.clone().text().then(body => {
      if (request.signal.aborted) throw new DOMException('Aborted', 'AbortError');
      const promise = send(body, headers); const cancel = () => promise.cancel();
      request.signal.addEventListener('abort', cancel, {once:true});
      return promise.finally(() => request.signal.removeEventListener('abort', cancel));
    }).then(r => new Response(r.body, {status:r.status, headers:r.headers}));
  };
  const NativeXHR = window.XMLHttpRequest;
  window.XMLHttpRequest = function() {
    const xhr = new NativeXHR();
    const open = xhr.open, nativeSend = xhr.send, setHeader = xhr.setRequestHeader, abort = xhr.abort;
    const responseHeader = xhr.getResponseHeader, allHeaders = xhr.getAllResponseHeaders;
    let scoped = false, handled = false, result = null, state = 0, headers = {}, cancelled = false, operation = null;
    const emit = type => xhr.dispatchEvent(new Event(type));
    for (const key of ['readyState','status','statusText','responseText','response','responseURL']) {
      Object.defineProperty(xhr, key, {configurable:true, get() {
        if (!handled) return Reflect.get(NativeXHR.prototype, key, xhr);
        if (key === 'readyState') return state;
        if (key === 'status') return result ? result.status : 0;
        if (key === 'statusText') return '';
        if (key === 'responseURL') return 'https://www.instagram.com/api/graphql';
        if (key === 'responseText') return result ? result.body : '';
        if (!result) return null;
        return xhr.responseType === 'json' ? JSON.parse(result.body) : result.body;
      }});
    }
    xhr.open = function(method, url) {
      scoped = String(method).toUpperCase() === 'POST' && target(url);
      headers = {}; handled = false; cancelled = false; result = null;
      return open.apply(xhr, arguments);
    };
    xhr.setRequestHeader = function(name, value) { headers[String(name).toLowerCase()] = String(value); return setHeader.apply(xhr, arguments); };
    xhr.getResponseHeader = function(name) { return handled ? result && result.headers[String(name).toLowerCase()] || null : responseHeader.apply(xhr, arguments); };
    xhr.getAllResponseHeaders = function() { return handled ? result ? Object.entries(result.headers).map(([k,v])=>k+': '+v+'\r\n').join('') : '' : allHeaders.apply(xhr, arguments); };
    xhr.send = function(body) {
      if (!scoped) return nativeSend.apply(xhr, arguments);
      handled = true; state = 1;
      const encoded = body instanceof URLSearchParams ? body.toString() : body;
      emit('loadstart');
      operation = send(encoded, headers);
      operation.then(reply => {
        if (cancelled) return;
        result = reply; state = 2; emit('readystatechange'); state = 3; emit('readystatechange'); state = 4;
        emit('readystatechange'); emit('load'); emit('loadend');
      }).catch(() => { if (!cancelled) { state = 4; emit('readystatechange'); emit('error'); emit('loadend'); } });
    };
    xhr.abort = function() { if (!handled) return abort.apply(xhr, arguments); cancelled = true; if(operation)operation.cancel(); state = 0; emit('abort'); emit('loadend'); };
    return xhr;
  };
  window.XMLHttpRequest.prototype = NativeXHR.prototype;
  for (const key of ['UNSENT','OPENED','HEADERS_RECEIVED','LOADING','DONE']) window.XMLHttpRequest[key] = NativeXHR[key];
})();
