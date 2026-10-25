package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FollowListBoundaryTest {
    private fun page(body: String, cursor: String? = null, direction: FollowDirection = FollowDirection.FOLLOWERS) =
        FollowPageParser.parse(JSONObject(body), "456", direction, cursor, 1000)
    @Test fun missingNullFalseAndEmptyArePreservedWithoutInventingCompleteCoverage() {
        val missing = page("""{"status":"ok","users":[]}""")
        assertFalse(missing.next.present); assertNull(missing.limited.value)
        assertEquals(FollowCoverage.UNKNOWN, missing.coverage())
        val end = page("""{"status":"ok","users":[],"next_max_id":null,"should_limit_list_of_followers":false}""")
        assertTrue(end.next.present); assertNull(end.next.value); assertEquals(false, end.limited.value)
        assertEquals(FollowCoverage.END_OBSERVED, end.coverage())
        val snapshot = FollowSnapshot("123", "binding", "456").record(end)
        assertEquals(snapshot, FollowListCodec.decode(FollowListCodec.encode(snapshot)))
        assertEquals(FollowField<Boolean>(false, null), FollowListCodec.decode(FollowListCodec.encode(
            FollowSnapshot("123", "binding", "456").record(missing))).followers!!.latest.limited)
    }
    @Test fun invalidListsIdentitiesCursorAndFlagsAreNotTurnedIntoEmptyOrFalse() {
        for (body in listOf("""{"status":"ok"}""", """{"status":"ok","users":null}""",
            """{"status":"ok","users":[{"pk":true}]}""", """{"status":"ok","users":[],"next_max_id":123}""",
            """{"status":"ok","users":[],"has_more":"false"}""", """{"status":"ok","users":[{"pk":"1","username":{}}]}""")) {
            assertEquals(ApiFailure.Kind.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { page(body) }.kind)
        }
    }
    @Test fun restrictionAndContradictoryContinuationNeverOfferAnotherPage() {
        val limited = page("""{"status":"ok","users":[],"next_max_id":"next","should_limit_list_of_followers":true}""")
        assertEquals(FollowCoverage.LIMITED, limited.coverage()); assertNull(FollowRun(listOf(limited)).cursor())
        val contradiction = page("""{"status":"ok","users":[],"next_max_id":"next","has_more":false}""")
        assertEquals(FollowCoverage.UNKNOWN, contradiction.coverage())
        assertEquals(FollowCoverage.UNKNOWN, page("""{"status":"ok","users":[],"has_more":true}""").coverage())
    }
    @Test fun overlappingPagesKeepAllMembersAndOnlyDeduplicateStableIdentity() {
        val first = page("""{"status":"ok","users":[{"pk":"1","username":"before"},{"pk":"2"}],"next_max_id":"page2"}""")
        val second = page("""{"status":"ok","users":[{"pk":"1","username":"after"},{"pk":"3"}],"next_max_id":null}""", "page2")
        val run = FollowRun(listOf(first)).append(second)
        assertEquals(listOf("1", "2", "3"), run.users.map { it.id }); assertEquals(4, run.pages.sumOf { it.users.size })
        assertEquals(FollowCoverage.END_OBSERVED, run.coverage())
        val snapshot = FollowSnapshot("123", "binding", "456", run)
        assertEquals(snapshot, FollowListCodec.decode(FollowListCodec.encode(snapshot)))
        assertFalse(snapshot.matches(Session("123", "", "", "another"), "456"))
    }
    @Test fun repeatedCursorOrNoProgressStopsAndMismatchedContinuationIsRejected() {
        val first = page("""{"status":"ok","users":[{"pk":"1"}],"next_max_id":"page2"}""")
        val loop = page("""{"status":"ok","users":[{"pk":"2"}],"next_max_id":"page2"}""", "page2")
        assertEquals(FollowCoverage.STALLED, FollowRun(listOf(first)).append(loop).coverage())
        assertEquals(FollowCoverage.STALLED, FollowRun(listOf(page("""{"status":"ok","users":[],"next_max_id":"page2"}"""))).coverage())
        assertThrows(IllegalArgumentException::class.java) { FollowRun(listOf(first)).append(loop.copy(inputCursor = "wrong")) }
    }
    @Test fun observedComparisonUsesIdsAndRequiresBothLists() {
        var snapshot = FollowSnapshot("123", "binding", "456").record(page("""{"status":"ok","users":[{"pk":"1","username":"same"},{"pk":"2"}]}"""))
        assertNull(FollowAnalysis.compare(snapshot))
        snapshot = snapshot.record(page("""{"status":"ok","users":[{"pk":"1","username":"renamed"},{"pk":"3"}]}""", direction = FollowDirection.FOLLOWING))
        val comparison = FollowAnalysis.compare(snapshot)!!
        assertEquals(listOf("1"), comparison.mutual.map { it.id })
        assertEquals(listOf("2"), comparison.followersOnly.map { it.id }); assertEquals(listOf("3"), comparison.followingOnly.map { it.id })
    }
    @Test fun mobilePageRequestKeepsSourceDeviceIdentityAndEncodesOpaqueCursor() {
        val state = MobileSession("binding", "123", mapOf("X-IG-Device-ID" to "original-device", "User-Agent" to "fixture"), emptyMap())
        val request = MobileFollowListReader.request(state, "456", FollowDirection.FOLLOWERS, "a/b+=&", 1000)
        assertEquals("i.instagram.com", request.url.host); assertEquals("GET", request.method)
        assertEquals("/api/v1/friendships/456/followers/", request.url.encodedPath)
        assertEquals("123_original-device", request.url.queryParameter("rank_token"))
        assertEquals("a/b+=&", request.url.queryParameter("max_id")); assertEquals("50", request.url.queryParameter("count"))
        assertThrows(ApiFailure::class.java) { MobileFollowListReader.request(state, "../1", FollowDirection.FOLLOWING, null, 1000) }
    }
}
