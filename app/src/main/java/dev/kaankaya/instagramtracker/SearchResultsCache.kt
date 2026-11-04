package dev.kaankaya.instagramtracker

/** Activity-owned, memory-only results shared by successive search dialogs. Reads never extend freshness. */
class SearchResultsCache(private val now: () -> Long = {android.os.SystemClock.elapsedRealtime()}) {
    class Ticket internal constructor(internal val session: Session,internal val generation: Long)
    private data class Entry(val at: Long,val users: List<ProfileCandidate>)
    private val entries=linkedMapOf<String,Entry>()
    private var session: Session?=null
    private var generation=0L
    @Synchronized fun clear() {entries.clear();session=null;generation++}
    @Synchronized fun bind(scope: Session): Ticket {
        if(session!=scope){clear();session=scope}
        return Ticket(scope,generation)
    }
    @Synchronized fun get(scope: Session,query: String): List<ProfileCandidate>? {
        if(session!=scope)return null
        val entry=entries[query] ?: return null
        val age=now()-entry.at
        if(age<0 || age>=SearchCachePolicy.TTL_MS){entries.remove(query);return null}
        return entry.users.toList()
    }
    @Synchronized fun put(ticket: Ticket,query: String,users: List<ProfileCandidate>) {
        if(session!=ticket.session || generation!=ticket.generation)return
        entries.remove(query);entries[query]=Entry(now(),users.take(SearchCachePolicy.MAX_RESULTS))
        while(entries.size>SearchCachePolicy.MAX_QUERIES)entries.remove(entries.keys.first())
    }
}
