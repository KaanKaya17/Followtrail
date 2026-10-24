package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PeopleBoundaryTest {
    private val web = """{"status":"ok","data":{"user":{"id":"456","username":"deniz","edge_followed_by":{"count":12},"edge_follow":{"count":0},"edge_owner_to_timeline_media":{"count":4}}}}"""
    private val session = Session("123", "ds_user_id=123; sessionid=synthetic", "test-UA")
    private fun p(time: Long = 1000, id: String = "456") = Profile(id, "deniz", 12, 0, time, 4)
    @Test fun usernameAndProfileLinksNormalizeWithoutAcceptingOtherResources() {
        for (input in listOf(" @Deniz ", "deniz", "https://www.instagram.com/Deniz/?igsh=abc")) assertEquals("deniz", ProfileInput.username(input))
        for (input in listOf("https://instagram.com.evil.test/deniz", "https://instagram.com@evil.test/deniz", "http://instagram.com/deniz",
            "https://www.instagram.com/p/123/", "deniz/../../", "https://www.instagram.com/%64eniz/", "deniz\nX-Fake: yes", "a".repeat(31))) {
            assertNull(input, ProfileInput.username(input))
        }
    }
    @Test fun profileLookupRequiresExactRequestedUsernameAndExplicitIntegerCounts() {
        val profile = ProfileParser.byUsername(web, "DENIZ", 1000)
        assertEquals(p(), profile)
        assertEquals(ApiFailure.Kind.WRONG_ACCOUNT, assertThrows(ApiFailure::class.java) { ProfileParser.byUsername(web, "other", 1000) }.kind)
        val missing = JSONObject(web); missing.getJSONObject("data").getJSONObject("user").remove("edge_followed_by")
        assertThrows(ApiFailure::class.java) { ProfileParser.byUsername(missing.toString(), "deniz", 1000) }
        val noPosts = JSONObject(web); noPosts.getJSONObject("data").getJSONObject("user").remove("edge_owner_to_timeline_media")
        assertNull(ProfileParser.byUsername(noPosts.toString(), "deniz", 1000).posts)
        val malformed = JSONObject(web); malformed.getJSONObject("data").getJSONObject("user").getJSONObject("edge_follow").put("count", "0")
        assertThrows(ApiFailure::class.java) { ProfileParser.byUsername(malformed.toString(), "deniz", 1000) }
    }
    @Test fun legacySessionMigratesAndPeopleRoundtripWithMetadata() {
        val legacy = """{"version":1,"id":"123","cookie":"ds_user_id=123; sessionid=synthetic","ua":"test-UA","username":"owner","followers":0,"following":7,"observedAt":1000}"""
        val old = AccountCodec.decode(legacy)
        assertTrue(old.people.isEmpty()); assertNull(old.profile.posts)
        val updated = old.copy(people = listOf(TrackedPerson(listOf(p(), p(2000).copy(username = "renamed")), "Özel not", "Arkadaşlar")))
        assertEquals(updated, AccountCodec.decode(AccountCodec.encode(updated)))
    }
    @Test fun identityOwnsHistoryAndRepeatedLookupPreservesMetadata() {
        val person = TrackedPerson(listOf(p()), "note", "group")
        val people = PeoplePolicy.record(listOf(person), p(2000).copy(username = "renamed"))
        assertEquals(1, people.size); assertEquals("note", people.single().note); assertEquals(2, people.single().history.size)
        assertThrows(IllegalArgumentException::class.java) { PeoplePolicy.record(people, p(1000)) }
        var bounded = listOf(person)
        for (i in 1..110) bounded = PeoplePolicy.record(bounded, p(1000 + i.toLong()))
        assertEquals(PeoplePolicy.MAX_HISTORY, bounded.single().history.size)
        val full = (1..PeoplePolicy.MAX_PEOPLE).map { TrackedPerson(listOf(p(id = it.toString()))) }
        assertThrows(IllegalArgumentException::class.java) { PeoplePolicy.record(full, p(id = "999")) }
    }
    @Test fun corruptHistoryAndDuplicateIdentitiesCannotBeSaved() {
        val own = Profile("123", "owner", 0, 0, 1000)
        for (people in listOf(
            listOf(TrackedPerson(emptyList())), listOf(TrackedPerson(listOf(p(2000), p(1000)))),
            listOf(TrackedPerson(listOf(p(), p(2000, "789")))),
            listOf(TrackedPerson(listOf(p())), TrackedPerson(listOf(p()))),
            listOf(TrackedPerson(listOf(p()), "x".repeat(501))),
        )) assertThrows(Exception::class.java) { AccountCodec.encode(SavedAccount(session, own, people)) }
    }
    @Test fun cacheFreshnessDoesNotInventObservationsOrRefreshOnEveryOpen() {
        assertFalse(PeoplePolicy.needsRefresh(p(), 2000))
        assertTrue(PeoplePolicy.needsRefresh(p(), 1000 + PeoplePolicy.FRESH_MS))
        assertFalse(PeoplePolicy.needsRefresh(p(), 500))
    }
}
