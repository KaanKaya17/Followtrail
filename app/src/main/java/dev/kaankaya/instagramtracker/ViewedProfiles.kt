package dev.kaankaya.instagramtracker

/** Bounded process-only cache. A read never extends an observation's freshness or enables watching. */
object ViewedProfiles {
    private data class Key(val session: Session, val id: String)
    private val entries = LinkedHashMap<Key, Profile>(24, .75f, true)
    const val MAX_ENTRIES = 20
    @Synchronized fun read(session: Session, id: String, now: Long = System.currentTimeMillis()): Profile? {
        val key = Key(session, id); val profile = entries[key] ?: return null
        if (profile.observedAt > now || PeoplePolicy.needsRefresh(profile, now)) { entries.remove(key); return null }
        return profile
    }
    @Synchronized fun put(session: Session, profile: Profile) {
        val key = Key(session, profile.userId)
        if ((entries[key]?.observedAt ?: 0) > profile.observedAt) return
        entries[key] = profile
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }
    fun latest(account: SavedAccount, id: String, now: Long = System.currentTimeMillis()): Profile? =
        (listOfNotNull(account.profile.takeIf { it.userId == id }, account.people.firstOrNull { it.latest.userId == id }?.latest,
            read(account.session, id, now))).maxByOrNull { it.observedAt }
    @Synchronized fun clear(owner: String? = null, id: String? = null) {
        entries.keys.removeAll { (owner == null || it.session.userId == owner) && (id == null || it.id == id) }
    }
}
