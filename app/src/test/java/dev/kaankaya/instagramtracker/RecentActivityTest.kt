package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RecentActivityTest {
    private val session = Session("123", "ds_user_id=123; sessionid=synthetic", "test-UA")
    private val own = Profile("123", "owner", 20, 5, 1000)
    private fun profile(time: Long = 1000, id: String = "456", followers: Long = 10, following: Long = 5) =
        Profile(id, "person_$id", followers, following, time, 4)
    private fun account(vararg people: TrackedPerson) = SavedAccount(session, own, people.toList())

    @Test fun oldPayloadsDefaultFavoritesWithoutLosingSessionHistoryOrSettings() {
        val person = TrackedPerson(listOf(profile(), profile(2000)), "note", "group", true, 360, 3000, true)
        val saved = account(person)
        assertEquals(saved, AccountCodec.decode(AccountCodec.encode(saved)))
        for (version in listOf(2, 3, 4)) {
            val json = JSONObject(AccountCodec.encode(saved)).put("version", version)
            json.getJSONArray("people").getJSONObject(0).remove("favorite")
            val restored = AccountCodec.decode(json.toString())
            assertFalse(restored.people.single().favorite)
            assertEquals(saved.session, restored.session); assertEquals(person.history, restored.people.single().history)
            assertEquals(person.note, restored.people.single().note)
            if (version >= 3) assertTrue(restored.people.single().watching)
        }
        for (value in listOf("true", 1, JSONObject.NULL)) {
            val json = JSONObject(AccountCodec.encode(saved))
            json.getJSONArray("people").getJSONObject(0).put("favorite", value)
            assertThrows(Exception::class.java) { AccountCodec.decode(json.toString()) }
        }
    }
    @Test fun favoritesUseStableIdentityAndKeepTheirOrderAcrossNewObservations() {
        val people = (1..4).map { TrackedPerson(listOf(profile(id = it.toString())), favorite = it % 2 == 0) }
        assertEquals(listOf("2", "4", "1", "3"), PeoplePolicy.favoritesFirst(people).map { it.latest.userId })
        assertEquals(listOf("1", "2", "3", "4"), people.map { it.latest.userId })
        val updated = PeoplePolicy.record(people, profile(2000, "2").copy(username = "renamed"))
        assertTrue(updated.single { it.latest.userId == "2" }.favorite)
        assertEquals(listOf("2", "4", "1", "3"), PeoplePolicy.favoritesFirst(updated).map { it.latest.userId })
    }
    @Test fun feedUsesCountObservationsAndDoesNotInventEventsForFirstReadsOrPostOnlyChanges() {
        val before = profile(); val after = profile(2000, followers = 12, following = 4).copy(username = "renamed")
        val saved = account(TrackedPerson(listOf(before, after, after.copy(observedAt = 3000, posts = 99))))
        val item = RecentActivity.items(saved).filterIsInstance<ActivityItem.Counts>().single()
        assertEquals("456", item.profileId); assertEquals("renamed", item.username)
        assertEquals(1000L, item.previousAt); assertEquals(2000L, item.observedAt)
        assertEquals(2L, item.followersDelta); assertEquals(-1L, item.followingDelta)
        assertTrue(RecentActivity.items(account(TrackedPerson(listOf(before)))).isEmpty())
    }
    @Test fun pageEventsAreScopedToTheViewerAndRetainedTargetsAndKeepTheirPageTimes() {
        val saved = account(TrackedPerson(listOf(profile())))
        val first = FollowPageParser.parse(JSONObject("""{"status":"ok","users":[],"next_max_id":"next"}"""),
            "456", FollowDirection.FOLLOWERS, null, 2000)
        // Empty MORE stalls; use one observed user to make a valid continuation.
        val page = first.copy(users = listOf(FollowUser("7", "member", null, null, null, null)))
        val last = page.copy(inputCursor = "next", observedAt = 3000, next = FollowField(true, null))
        val snapshot = FollowSnapshot("123", null, "456").record(page).record(last)
        val items = RecentActivity.items(saved, snapshot).filterIsInstance<ActivityItem.ListRead>()
        assertEquals(listOf(2, 1), items.map { it.pageNumber }); assertEquals(listOf(3000L, 2000L), items.map { it.observedAt })
        assertTrue(RecentActivity.items(saved, snapshot.copy(owner = "999")).isEmpty())
        assertTrue(RecentActivity.items(saved, snapshot.copy(binding = "other")).isEmpty())
        assertTrue(RecentActivity.items(account(), snapshot).isEmpty())
    }
    @Test fun feedHasABoundedDeterministicNewestFirstOrder() {
        val history = (1..40).map { profile(it * 1000L, followers = it.toLong()) }
        val saved = account(TrackedPerson(history), TrackedPerson(history.map { it.copy(userId = "789", username = "other") }))
        val items = RecentActivity.items(saved)
        assertEquals(RecentActivity.MAX_ITEMS, items.size); assertEquals(items.size, items.map { it.key }.distinct().size)
        assertTrue(items.zipWithNext().all { (a, b) -> a.observedAt >= b.observedAt })
        assertEquals(items, RecentActivity.items(saved))
    }
}
