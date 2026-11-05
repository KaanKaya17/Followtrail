package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoriesTest {
    private fun item(id: String = "300") = JSONObject().put("pk",id).put("media_type",1L)
        .put("taken_at",1800000000L).put("reel_mentions",JSONArray())
        .put("image_versions2",JSONObject().put("candidates",JSONArray().put(JSONObject()
            .put("url","https://scontent.cdninstagram.com/sample.jpg?token=temporary")
            .put("width",1080L).put("height",1920L))))
    private fun feed(vararg items: JSONObject) = JSONObject().put("status","ok").put("reel",JSONObject()
        .put("user",JSONObject().put("pk","200")).put("items",JSONArray(items.toList())))
    private fun facts() = StoryParser.parse(feed(item()),"200").single().facts
    private fun rejected(block: () -> Unit) { try { block(); fail("Invalid input accepted") } catch (_: IllegalArgumentException) {} }

    @Test fun parsedFactsKeepOmissionSeparateFromEmptyAndNeverPersistCdnUrls() {
        val candidate = StoryParser.parse(feed(item()),"200").single()
        assertNotNull(candidate.mediaUrl); assertEquals(StoryKind.IMAGE,candidate.facts.kind)
        assertEquals(emptyList<StoryMention>(),candidate.facts.mentions); assertNull(candidate.facts.links)
        val archive = StoryArchive("100",mapOf("200" to StoryTarget(listOf(ArchivedStory(candidate.facts,1800000100000)))))
        val raw = StoryArchiveCodec.encode(archive)
        assertFalse(raw.contains("temporary")); assertFalse(raw.contains("cdninstagram"))
        assertEquals(archive,StoryArchiveCodec.decode(raw)); assertFalse(candidate.toString().contains("temporary"))
    }
    @Test fun malformedOptionalMetadataRetainsKnownFactsAndRecordsCoverage() {
        val known = JSONObject().put("user",JSONObject().put("pk","900").put("username","qa.tagged"))
        val result = StoryParser.parse(feed(item().put("reel_mentions",JSONArray().put(known).put("bad"))),"200").single().facts
        assertEquals(listOf(StoryMention("900","qa.tagged")),result.mentions)
        assertEquals(setOf("reel_mentions"),result.incomplete)
        assertNull(result.shared)
    }
    @Test fun missingOrForeignIdentityFailsEvenOnDuplicateItems() {
        val duplicate = item().put("user",JSONObject().put("pk","999"))
        try { StoryParser.parse(feed(item(),duplicate),"200"); fail() }
        catch (e: ApiFailure) { assertEquals(ApiFailure.Kind.INVALID_RESPONSE,e.kind) }
        assertEquals(1,StoryParser.parse(feed(item(),item()),"200").size)
        assertTrue(StoryParser.parse(JSONObject().put("status","ok").put("reel",JSONObject.NULL),"200").isEmpty())
        try { StoryParser.parse(JSONObject().put("status","ok"),"200"); fail() } catch (_: ApiFailure) {}
    }
    @Test fun mediaSelectionRejectsForeignHostsAndUnboundedDimensionsWithoutLosingMetadata() {
        for (url in listOf("https://example.com/a.jpg","https://scontent.cdninstagram.com/a.jpg#fragment","http://scontent.cdninstagram.com/a.jpg")) {
            val bad = item(); bad.getJSONObject("image_versions2").getJSONArray("candidates").getJSONObject(0).put("url",url)
            assertNull(StoryParser.parse(feed(bad),"200").single().mediaUrl)
        }
        val bad = item(); bad.getJSONObject("image_versions2").getJSONArray("candidates").getJSONObject(0).put("width",Long.MAX_VALUE)
        assertNull(StoryParser.parse(feed(bad),"200").single().mediaUrl)
    }
    @Test fun codecRejectsIncompleteMediaProofDuplicateIdsAndForeignTargets() {
        val row = ArchivedStory(facts(),1800000100000)
        fun archive(rows: List<ArchivedStory>) = StoryArchive("100",mapOf("200" to StoryTarget(rows)))
        rejected { StoryArchiveCodec.encode(archive(listOf(row.copy(mime="image/png")))) }
        rejected { StoryArchiveCodec.encode(archive(listOf(row,row))) }
        rejected { StoryArchiveCodec.encode(archive(listOf(row.copy(facts=row.facts.copy(target="999"))))) }
        rejected { StoryArchiveCodec.encode(archive(listOf(row.copy(bytes=0,mime="image/png",digest="a".repeat(64))))) }
    }
    @Test fun calendarNavigatesYearBoundariesLeapDaysAndMondayFirstWithoutChangingTimezone() {
        assertEquals("2027-01",StoryCalendar.shift("2026-12",1))
        assertEquals("2026-12",StoryCalendar.shift("2027-01",-1))
        assertEquals(29,StoryCalendar.days("2024-02").count { it != null })
        assertTrue(StoryCalendar.validDay("2024-02-29")); assertFalse(StoryCalendar.validDay("2026-02-29"))
        assertFalse(StoryCalendar.validDay("2026-13-01")); assertEquals("1970-01-01",StoryCalendar.day(0))
        assertEquals("2026-11-01",StoryCalendar.days("2026-11")[6])
    }
    @Test fun playbackReadsExactWindowsAndFailsInvalidOffsetsWithoutAllocatingCopies() {
        val target = ByteArray(6); val source = byteArrayOf(1,2,3,4)
        assertEquals(2,StoryByteWindow.read(source,2,target,1,5)); assertArrayEquals(byteArrayOf(0,3,4,0,0,0),target)
        assertEquals(-1,StoryByteWindow.read(source,Long.MAX_VALUE,target,0,1))
        assertEquals(-1,StoryByteWindow.read(null,0,target,0,1)); assertEquals(0,StoryByteWindow.read(null,0,target,0,0))
        rejected { StoryByteWindow.read(source,-1,target,0,1) }
        rejected { StoryByteWindow.read(source,0,target,4,3) }
    }
    @Test fun storyRequestsAreReadOnlyAndShareTheCanonicalCapabilities() {
        for (host in listOf("www.instagram.com","i.instagram.com")) {
            val url = "https://$host/api/v1/feed/user/200/story/"
            assertTrue(AccountRequestPolicy.readAllowed("GET",url))
            assertFalse(AccountRequestPolicy.readAllowed("GET",url,true))
            assertFalse(AccountRequestPolicy.readAllowed("POST",url,true))
            assertFalse(AccountRequestPolicy.readAllowed("GET",url.replace("200","name"),true))
        }
        assertEquals(4,JSONArray(StoryLimits.CAPABILITIES_JSON).length())
    }
}
