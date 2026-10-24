((settings) => {
    'use strict';
    const slot = settings.slot;
    if (location.origin !== settings.origin || window[slot]) return false;
    if (!/^[A-Za-z0-9._]{1,30}$/.test(settings.username)) return false;
    const state = {stage: 'waiting'};
    window[slot] = state;
    if (!window.fetch || !window.AbortController || !window.TextDecoder) {
        state.stage = 'done'; state.reason = 'unsupported'; return true;
    }
    const controller = new AbortController();
    state.cancel = () => controller.abort();
    let timedOut = false;
    const deadline = setTimeout(() => { timedOut = true; controller.abort(); }, 20000);
    const count = value => Number.isSafeInteger(value) && value >= 0;
    const done = result => {
        clearTimeout(deadline);
        delete state.cancel;
        Object.assign(state, result, {stage: 'done'});
    };
    (async () => {
        try {
            const response = await fetch('/api/v1/users/web_profile_info/?username=' + encodeURIComponent(settings.username), {
                method: 'GET', credentials: 'same-origin', mode: 'same-origin', redirect: 'error', cache: 'no-store',
                headers: {'Accept': 'application/json', 'X-IG-App-ID': '936619743392459'}, signal: controller.signal
            });
            const status = response.status;
            const retry = response.headers.get('Retry-After');
            const retryAfter = retry && retry.length <= 160 ? retry : null;
            if (status === 429) { controller.abort(); done({status, reason: 'limited', retryAfter}); return; }
            if (status === 401 || status === 403 || status === 404) {
                controller.abort(); done({status, reason: status === 401 ? 'login_required' : status === 403 ? 'verification' : 'not_found'}); return;
            }
            if (!response.body || !response.body.getReader) { controller.abort(); done({status, reason: 'unsupported'}); return; }
            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8', {fatal: true});
            let bytes = 0, text = '';
            while (true) {
                const part = await reader.read();
                if (part.done) break;
                bytes += part.value.byteLength;
                if (bytes > 1048576) { await reader.cancel(); done({status, reason: 'oversized'}); return; }
                text += decoder.decode(part.value, {stream: true});
            }
            text += decoder.decode();
            let body;
            try { body = JSON.parse(text); } catch (_) { done({status, reason: 'invalid'}); return; }
            const message = body.message;
            if (message === 'feedback_required') { done({status, reason: 'feedback', retryAfter}); return; }
            if (['please_wait_a_few_minutes', 'Please wait a few minutes before you try again.'].includes(message)) {
                done({status, reason: 'wait', retryAfter}); return;
            }
            if (message === 'login_required') { done({status, reason: 'login_required'}); return; }
            if (['challenge_required', 'checkpoint_required', 'consent_required'].includes(message)) {
                done({status, reason: 'verification'}); return;
            }
            const user = body.data && body.data.user;
            if (status !== 200 || body.status !== 'ok' || !user ||
                !(typeof user.id === 'string' || Number.isSafeInteger(user.id)) || !/^[1-9][0-9]{0,29}$/.test(String(user.id)) ||
                typeof user.username !== 'string' || user.username.toLowerCase() !== settings.username.toLowerCase() ||
                !count(user.edge_followed_by && user.edge_followed_by.count) || !count(user.edge_follow && user.edge_follow.count)) {
                done({status, reason: 'invalid'}); return;
            }
            const projected = {id: String(user.id), username: user.username,
                edge_followed_by: {count: user.edge_followed_by.count}, edge_follow: {count: user.edge_follow.count}};
            if (user.edge_owner_to_timeline_media && count(user.edge_owner_to_timeline_media.count)) {
                projected.edge_owner_to_timeline_media = {count: user.edge_owner_to_timeline_media.count};
            }
            done({status, reason: 'ok', payload: {status: 'ok', data: {user: projected}}});
        } catch (error) {
            done({reason: timedOut ? 'timeout' : error.name === 'AbortError' ? 'cancelled' : 'connection'});
        }
    })();
    return true;
})
