package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class AvatarPolicyTest {
    @Test fun onlyPublicHttpsCdnImageLocationsAreEligible() {
        assertNotNull(AvatarPolicy.url("https://scontent.cdninstagram.com/image.jpg?token=source%2Fvalue"))
        assertNotNull(AvatarPolicy.url("https://scontent.xx.fbcdn.net:443/image.webp"))
        for (url in listOf("http://cdninstagram.com/image.jpg", "https://instagram.com/api/v1/users/1/info/",
                "https://cdninstagram.com.evil.example/image.jpg", "https://evilcdninstagram.com/image.jpg",
                "https://name:password@cdninstagram.com/image.jpg", "https://cdninstagram.com:8443/image.jpg", "file:///image.jpg"))
            assertNull(url, AvatarPolicy.url(url))
    }
    @Test fun cacheIdentitySeparatesOwnersTargetsAndPhotoRevisions() {
        val source = AvatarSource("100", "200", "https://cdninstagram.com/photo.jpg", "old")
        val keys = listOf(source, source.copy(owner = "300"), source.copy(target = "400"), source.copy(revision = "new"),
            source.copy(url = "https://cdninstagram.com/other.jpg")).map { it.key }
        assertEquals(4, keys.distinct().size)
        assertEquals(source.key, source.copy(url = "https://cdninstagram.com/rotated.jpg?signature=new").key)
        assertNotEquals(source.copy(revision = null).key, source.copy(revision = null, url = "https://cdninstagram.com/other.jpg").key)
        assertFalse(source.key.contains("photo.jpg")); assertTrue(source.key.startsWith(AvatarPolicy.prefix("100", "200")))
    }
    @Test fun profileLinksPreserveObservedUsernamesAndNeverGuessMissingOnes() {
        assertEquals("https://www.instagram.com/a.B_12/", ProfileLinks.url("a.B_12"))
        for (name in listOf(null, "", "display name", "../accounts", "name?x=1", "@name", "a".repeat(31))) assertNull(ProfileLinks.url(name))
    }
}
