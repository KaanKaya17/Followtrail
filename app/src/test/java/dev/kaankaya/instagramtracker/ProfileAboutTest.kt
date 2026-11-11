package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class ProfileAboutTest {
    @Test fun enteredInformationSurvivesCodecAndProfileRefresh() {
        val owner = Profile("100", "owner", 5, 4, 1000)
        val person = Profile("200", "saved", 2, 3, 1000)
        val about = ProfileAbout("2019-04", "Türkiye", 0)
        val saved = SavedAccount(Session("100", "sessionid=test; ds_user_id=100", "QA"), owner,
            listOf(TrackedPerson(listOf(person), about = about)), about = ProfileAbout("2020-09"))
        assertEquals(saved, AccountCodec.decode(AccountCodec.encode(saved)))
        assertEquals(about, PeoplePolicy.record(saved.people, person.copy(observedAt = 2000)).single().about)
        assertTrue(runCatching { ProfileAbout("2019-13") }.isFailure)
        assertTrue(runCatching { ProfileAbout(usernameChanges = -1) }.isFailure)
    }
}
