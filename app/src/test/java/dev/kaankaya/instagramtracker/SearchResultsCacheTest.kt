package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class SearchResultsCacheTest {
    private var now=0L
    private val cache=SearchResultsCache {now}
    private val session=Session("100","synthetic","test")
    private val users=listOf(ProfileCandidate("200","qa.person","QA Person"))
    @Test fun reopeningSearchUsesSameResultsButReadingDoesNotExtendSixtySeconds() {
        val ticket=cache.bind(session);cache.put(ticket,"qa",users)
        now=59_999;assertEquals(users,cache.get(session,"qa"))
        cache.bind(session);assertEquals(users,cache.get(session,"qa"))
        now=60_000;assertNull(cache.get(session,"qa"))
    }
    @Test fun emptyResultsAreCachedAndOnlyLastSixDistinctQueriesRemain() {
        val ticket=cache.bind(session);cache.put(ticket,"empty",emptyList())
        assertEquals(emptyList<ProfileCandidate>(),cache.get(session,"empty"))
        repeat(6){cache.put(ticket,"query$it",users)}
        assertNull(cache.get(session,"empty"))
        assertEquals(users,cache.get(session,"query5"))
    }
    @Test fun clearAndAccountOrSessionChangesRejectOlderReplies() {
        val old=cache.bind(session);cache.put(old,"qa",users);cache.clear()
        val other=session.copy(userId="300");cache.bind(other);cache.put(old,"late",users)
        assertNull(cache.get(other,"late"));assertNull(cache.get(session,"qa"))
        val fresh=cache.bind(session);cache.put(fresh,"qa",users)
        cache.bind(session.copy(cookie="new-synthetic-session"))
        assertNull(cache.get(session,"qa"))
    }
    @Test fun returnedListsDoNotOwnCachedStorageAndResultCountIsBounded() {
        val ticket=cache.bind(session)
        cache.put(ticket,"qa",(1..25).map {ProfileCandidate(it.toString(),"qa.$it","QA")})
        val returned=cache.get(session,"qa")!!;assertEquals(20,returned.size)
        (returned as MutableList).clear()
        assertEquals(20,cache.get(session,"qa")!!.size)
    }
    @Test fun clockRollbackExpiresEntryRatherThanKeepingItIndefinitely() {
        now=10;val ticket=cache.bind(session);cache.put(ticket,"qa",users)
        now=9;assertNull(cache.get(session,"qa"))
    }
}
