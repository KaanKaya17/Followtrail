package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileComparisonTest {
    @Test fun exactObservationsKeepMissingFieldsAndUseMatchingArchivedPhoto() {
        val archive = listOf(
            JSONObject("""{"at":100,"lastSeen":120,"bio":"archived text","picture":"id:7","image":"thumb"}"""),
            JSONObject("""{"at":150,"lastSeen":150,"bio":"","picture":"id:8"}""")
        )
        val details = ProfileDetails(pictureId = ProfileText.known("7"))
        val profile = Profile("100", "new.name", 0, 2, 110, details = details)
        val check = CheckEvent("100", "old.name", CheckKind.PROFILE, null, 90, 90, afterFollowers = 12)
        val records = ProfileComparison.records(listOf(profile), archive, listOf(check))
        assertEquals(listOf(90L, 100L, 110L, 120L, 150L), records.map { it.at })
        assertEquals(12L, records.first().followers)
        assertNull(records.first().bio)
        assertNull(records[2].bio)
        assertEquals("thumb", records[2].image)
        assertEquals(0L, records[2].followers)
        assertEquals("", records.last().bio)
        assertNull(records.last().image)
    }
}
