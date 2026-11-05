"""Short-lived, connection-scoped facts for automatic list starts only."""
from request_defaults import PROFILE_BASIS_SECONDS


class ProfileBasis:
    def __init__(self):
        self.clear()

    def clear(self):
        self.binding = None
        self.entries = {}

    def bind(self, data):
        session = data.get('session') or {}
        cookies = session.get('cookies') or {}
        authorization = session.get('authorization_data') or {}
        tokens = tuple(value if isinstance(value, str) else None for value in
                       (cookies.get('sessionid'), authorization.get('sessionid')))
        owner = (data.get('account') or {}).get('id')
        binding = (owner, tokens) if owner and any(tokens) else None
        if self.binding != binding:
            self.clear()
            self.binding = binding
        return binding is not None

    def record(self, data, profile):
        if not self.bind(data):
            return
        self.entries.pop(profile['id'], None)
        self.entries[profile['id']] = {key: profile[key] for key in
                                       ('id', 'username', 'followers', 'following', 'observedAt')}
        while len(self.entries) > 20:
            del self.entries[next(iter(self.entries))]

    def recent(self, data, identity, now):
        if not self.bind(data):
            return None
        profile = self.entries.get(identity)
        if profile is None or not 0 <= now - profile['observedAt'] < PROFILE_BASIS_SECONDS:
            self.entries.pop(identity, None)
            return None
        return dict(profile)
