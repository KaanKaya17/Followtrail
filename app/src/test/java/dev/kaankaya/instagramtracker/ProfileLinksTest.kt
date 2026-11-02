package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileLinksTest {
    @Test fun linksRejectActiveContentAndDoNotConfuseBrandLookalikes() {
        for (url in listOf("javascript:alert(1)", "file:///tmp/a", "https://u:p@example.com", "https://example.com/\nq", "https://example.com\\x")) assertNull(ProfileLinks.webUrl(url))
        assertEquals("YouTube" to "youtube", ProfileLinks.brand("https://www.youtube.com/@demo"))
        assertEquals("youtube.com.evil.example" to "globe", ProfileLinks.brand("https://youtube.com.evil.example/"))
        assertNull(ProfileLinks.threadsName("https://threads.com.evil.example/@demo"))
        assertNull(ProfileLinks.threads(emptyList()))
        assertEquals("https://threads.com/@linked?from=ig", ProfileLinks.threads(listOf(ProfileSite("https://threads.com/@linked?from=ig"))))
        assertEquals("https://threads.net/@linked", ProfileLinks.threads(listOf(ProfileSite("https://threads.net/@linked"))))
    }
    @Test fun parserKeepsSafeLinksDeduplicatesAndOldAccountsStillDecode() {
        val body = JSONObject("""{"pk":"123","username":"qa.owner","follower_count":2,"following_count":1,"external_url":"https://site.example/","bio_links":[{"url":"javascript:bad"},{"url":"https://youtube.com/@qa"},{"url":"https://site.example/"}]}""")
        val profile = ProfileParser.user(body, 1000)
        assertEquals(2, profile.details.links.size)
        val saved = SavedAccount(Session("123", "ds_user_id=123; sessionid=synthetic", "QA"), profile)
        assertEquals(profile.details.links, AccountCodec.decode(AccountCodec.encode(saved)).profile.details.links)
        val old = saved.copy(profile = profile.copy(details = ProfileDetails()))
        assertTrue(AccountCodec.decode(AccountCodec.encode(old)).profile.details.links.isEmpty())
        body.put("bio_links", "invalid"); body.remove("external_url"); assertTrue(ProfileDetailsParser.parse(body).links.isEmpty())
    }

}
