package dev.kaankaya.instagramtracker

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ViewedProfilesTest {
    private val session = Session("100", "ds_user_id=100; sessionid=synthetic", "test")
    private val profile = Profile("200", "person", 10, 20, 1000)
    @Before fun setup() = ViewedProfiles.clear()
    @After fun cleanup() = ViewedProfiles.clear()
    @Test fun observationsExpireWithoutSlidingTheirFreshnessAndNeverReplaceNewerData() {
        ViewedProfiles.put(session, profile)
        assertEquals(profile, ViewedProfiles.read(session, "200", 1000 + PeoplePolicy.FRESH_MS - 1))
        ViewedProfiles.put(session, profile.copy(observedAt = 900))
        assertEquals(profile, ViewedProfiles.read(session, "200", 1200))
        assertNull(ViewedProfiles.read(session, "200", 1000 + PeoplePolicy.FRESH_MS))
        ViewedProfiles.put(session, profile); assertNull(ViewedProfiles.read(session, "200", 999))
    }
    @Test fun accountSessionAndBindingChangesDoNotReuseAnotherViewersData() {
        ViewedProfiles.put(session, profile)
        for (other in listOf(session.copy(userId = "300"), session.copy(cookie = "different"), session.copy(mobileBinding = "other")))
            assertNull(ViewedProfiles.read(other, "200", 1200))
        assertEquals(profile, ViewedProfiles.read(session, "200", 1200))
        ViewedProfiles.clear("100", "200"); assertNull(ViewedProfiles.read(session, "200", 1200))
    }
    @Test fun leastRecentlyViewedProfilesAreEvictedAndClearingOneOwnerKeepsTheOther() {
        repeat(20) { ViewedProfiles.put(session, profile.copy(userId = (200 + it).toString())) }
        ViewedProfiles.read(session, "200", 1200)
        ViewedProfiles.put(session, profile.copy(userId = "500"))
        assertNull(ViewedProfiles.read(session, "201", 1200)); assertNotNull(ViewedProfiles.read(session, "200", 1200))
        val other = session.copy(userId = "300"); ViewedProfiles.put(other, profile)
        ViewedProfiles.clear("100"); assertNull(ViewedProfiles.read(session, "200", 1200)); assertNotNull(ViewedProfiles.read(other, "200", 1200))
    }
}
