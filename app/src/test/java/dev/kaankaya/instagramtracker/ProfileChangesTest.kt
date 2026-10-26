package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileChangesTest {
    private val session = Session("123", "ds_user_id=123; sessionid=synthetic", "test-UA")
    private fun p(time: Long = 1000, bio: ProfileText = ProfileText(), picture: String? = null,
        url: String? = null, anonymous: Boolean? = null, username: String = "original") =
        Profile("456", username, 20, 8, time, 3, ProfileDetails(bio,
            picture?.let(ProfileText::known) ?: ProfileText(), url?.let(ProfileText::known) ?: ProfileText(), anonymous))
    private fun user() = JSONObject("""{"pk":"456","username":"original","follower_count":20,"following_count":8}""")
    private fun account(history: List<Profile>) = SavedAccount(session, p().copy(userId = "123"),
        listOf(TrackedPerson(history, "note", "group", true, 360, 5000, true)))

    @Test fun optionalFieldsPreserveAbsentNullEmptyAndMalformedWithoutBreakingCounts() {
        val body = user(); assertEquals(ProfileTextState.MISSING, ProfileParser.user(body, 1000).details.biography.state)
        body.put("biography", JSONObject.NULL)
        assertEquals(ProfileTextState.NULL, ProfileParser.user(body, 1000).details.biography.state)
        body.put("biography", ""); assertEquals(ProfileText.known(""), ProfileParser.user(body, 1000).details.biography)
        for (value in listOf(123, true, "🙂".repeat(3000))) {
            body.put("biography", value); val profile = ProfileParser.user(body, 1000)
            assertEquals(ProfileTextState.UNAVAILABLE, profile.details.biography.state); assertEquals(20L, profile.followers)
        }
        body.put("biography", "Satır 1\n☀️ @hesap"); body.put("profile_pic_id", "pic_123")
        body.put("profile_pic_url", "https://cdn.example/pic.jpg?token=one").put("has_anonymous_profile_picture", false)
        val profile = ProfileParser.user(body, 1000)
        assertEquals("Satır 1\n☀️ @hesap", profile.details.biography.value); assertEquals("pic_123", profile.details.pictureId.value)
    }
    @Test fun firstKnownValuesAreBaselinesAndUnknownFieldsNeverBecomeDeletions() {
        val initial = p(); val known = p(2000, ProfileText.known("hello"), "photo1")
        assertTrue(ProfileChanges.history(listOf(initial, known)).isEmpty())
        val missing = p(3000); val nullBio = p(4000, ProfileText(ProfileTextState.NULL))
        assertTrue(ProfileChanges.history(listOf(known, missing, nullBio)).isEmpty())
        val empty = p(5000, ProfileText.known(""))
        val event = ProfileChanges.history(listOf(known, missing, nullBio, empty)).single()
        assertEquals(ProfileChangeField.BIOGRAPHY, event.field); assertEquals("hello", event.before); assertEquals("", event.after)
        assertEquals(2000L, event.previousAt); assertEquals(5000L, event.observedAt)
    }
    @Test fun onlyPhotoIdentityOrAnonymousStateCanProducePhotoEvents() {
        val before = p(picture = "1", url = "https://cdn.example/a?token=1")
        val rotated = p(2000, picture = "1", url = "https://other.example/b?token=2")
        assertTrue(ProfileChanges.history(listOf(before, rotated)).isEmpty())
        val idChanged = rotated.copy(observedAt = 3000, details = rotated.details.copy(pictureId = ProfileText.known("2")))
        assertEquals(ProfileChangeField.PICTURE, ProfileChanges.history(listOf(before, rotated, idChanged)).single().field)
        assertTrue(ProfileChanges.history(listOf(p(url = "a"), p(2000, url = "b"))).isEmpty())
        assertEquals(ProfileChangeField.PICTURE, ProfileChanges.history(listOf(before, p(4000, anonymous = true))).single().field)
        assertTrue(ProfileChanges.history(listOf(p(1000, anonymous = false), p(2000, anonymous = false))).isEmpty())
    }
    @Test fun sharedComparisonEmitsMetadataOnlyChangesForFeedAndWatchingWithoutObservedProfileChanges() {
        val before = p(bio = ProfileText.known("old"), picture = "1")
        val after = p(2000, ProfileText.known("new"), "2", username = "renamed")
        val person = account(listOf(before)).people.single()
        val edits = ProfileChanges.latest(person.history, after)
        assertEquals(3, edits.size); assertTrue(edits.all { it.profileId == "456" && it.previousAt == 1000L })
        val notice = MonitorPolicy.change("123", person, after)!!
        assertEquals(0L, notice.followersDelta); assertEquals(0L, notice.followingDelta); assertEquals(edits, notice.edits)
        val feed = RecentActivity.items(account(listOf(before, after))).filterIsInstance<ActivityItem.ProfileChanged>()
        assertEquals(edits.toSet(), feed.map { it.edit }.toSet())
        assertNull(MonitorPolicy.change("123", person.copy(watching = false), after))
        assertTrue(ProfileChanges.latest(listOf(before, after), after.copy(observedAt = 3000)).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { ProfileChanges.latest(listOf(before), after.copy(userId = "789")) }
    }
    @Test fun additiveCodecRoundTripsDetailsAndOldRecordsRemainUsable() {
        val saved = account(listOf(p(), p(2000, ProfileText.known("line\nemoji🙂"), "123_456", "https://cdn.example/a", false)))
        val body = JSONObject(AccountCodec.encode(saved)); assertEquals(4, body.getInt("version"))
        assertEquals(saved, AccountCodec.decode(body.toString()))
        val old = JSONObject(AccountCodec.encode(account(listOf(p()))))
        assertFalse(old.has("profileDetails")); val restored = AccountCodec.decode(old.toString())
        assertEquals(session, restored.session); assertTrue(restored.people.single().favorite); assertTrue(restored.people.single().watching)
        assertEquals(ProfileDetails(), restored.people.single().latest.details)
        val malformed = JSONObject(body.toString()); malformed.getJSONArray("people").getJSONObject(0)
            .getJSONArray("history").getJSONObject(1).put("detailsRef", 100)
        assertThrows(Exception::class.java) { AccountCodec.decode(malformed.toString()) }
        val malformedText = JSONObject(body.toString()); malformedText.getJSONArray("profileDetails").getJSONObject(0)
            .getJSONObject("biography").put("valueRef", "0")
        assertThrows(Exception::class.java) { AccountCodec.decode(malformedText.toString()) }
    }
    @Test fun longBiographyIsStoredOnceEvenWhenUrlsRotateAcrossTwentyFullHistories() {
        val bio = "a".repeat(7500)
        val people = (1..20).map { id -> TrackedPerson((1..90).map { index ->
            p(index * 1000L, ProfileText.known(bio), "photo", "https://cdn.example/pic?token=$index").copy(userId = id.toString())
        }, favorite = id == 1) }
        val saved = account(listOf(p())).copy(people = people)
        val encoded = AccountCodec.encode(saved)
        assertEquals(1, JSONObject(encoded).getJSONArray("profileTexts").let { a -> (0 until a.length()).count { a.getString(it) == bio } })
        assertTrue("Metadata exceeded the existing record budget", encoded.toByteArray().size < 512 * 1024)
        assertEquals(saved, AccountCodec.decode(encoded))
        println("Full 20x90 history with shared 7500-byte biography: ${encoded.toByteArray().size} bytes")
    }
    @Test fun aHundredFullHistoriesFitTheExpandedAccountBudgetAndRoundtrip() {
        val bio = "a".repeat(7500)
        val people = (1..100).map { id -> TrackedPerson((1..90).map { index ->
            p(index * 1000L, ProfileText.known(bio), "photo", "https://cdn.example/pic?token=$index").copy(userId = id.toString())
        }, favorite = id == 1) }
        val saved = account(listOf(p())).copy(people = people)
        val encoded = AccountCodec.encode(saved)
        assertTrue(encoded.toByteArray().size < SessionVault.ACCOUNT_BYTES)
        assertEquals(saved, AccountCodec.decode(encoded))
        println("100x90 observations with shared biography and 90 rotating URLs: ${encoded.toByteArray().size} bytes")
    }
}
