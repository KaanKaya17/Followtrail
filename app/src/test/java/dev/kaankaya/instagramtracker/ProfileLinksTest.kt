package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileLinksTest {
    @Test fun threadsBadgeRequiresAnExplicitEnabledFlagAndItsOwnValidHandle() {
        val body = JSONObject("""{"username":"ig.name","show_text_post_app_badge":true,"text_post_app_badge_label":"threads.name"}""")
        assertEquals("https://www.threads.com/@threads.name", ProfileLinks.threads(ProfileLinks.parse(body)))
        body.put("bio_links",org.json.JSONArray("""[{"url":"https://threads.net/@explicit.name?from=ig"}]"""))
        assertEquals(listOf(ProfileSite("https://threads.net/@explicit.name?from=ig")), ProfileLinks.parse(body))
        body.remove("bio_links")
        for (enabled in listOf(false, JSONObject.NULL, "true")) {
            body.put("show_text_post_app_badge", enabled); assertTrue(ProfileLinks.parse(body).isEmpty())
        }
        body.put("show_text_post_app_badge",true)
        for (label in listOf(JSONObject.NULL,42,"","two words","https://threads.com/@name","x/../other","a".repeat(31))) {
            body.put("text_post_app_badge_label",label);assertTrue(ProfileLinks.parse(body).isEmpty())
        }
        body.remove("text_post_app_badge_label");assertTrue(ProfileLinks.parse(body).isEmpty())
    }
    @Test fun badgeDoesNotDisplaceFiveWebsitesAndSurvivesAccountCodec() {
        val body = JSONObject("""{"pk":"123","username":"qa.owner","follower_count":2,"following_count":1,"show_text_post_app_badge":true,"text_post_app_badge_label":"@badge.name"}""")
        body.put("bio_links",org.json.JSONArray((0..4).map { JSONObject().put("url","https://example.com/$it") }))
        val profile=ProfileParser.user(body,1000)
        assertEquals(6,profile.details.links.size)
        assertEquals("https://www.threads.com/@badge.name",ProfileLinks.threads(profile.details.links))
        val saved=SavedAccount(Session("123","ds_user_id=123; sessionid=synthetic","QA"),profile)
        assertEquals(profile.details.links,AccountCodec.decode(AccountCodec.encode(saved)).profile.details.links)
    }
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
