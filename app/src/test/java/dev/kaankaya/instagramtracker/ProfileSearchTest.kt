package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileSearchTest {
    @Test fun partialSearchMatchesNamesUsernamesGroupsAndTurkishLetters() {
        val text="İpek Çağrı Kaya ipek.kaya Yakın Arkadaşlar"
        for(query in listOf("ipe","@ipek","IPeK","cagri ka","kaya ipek","yakin","https://instagram.com/ipek.kaya/?igsh=abc"))
            assertTrue(query,ProfileSearch.matches(text,query))
        assertFalse(ProfileSearch.matches(text,"deniz"))
    }
    @Test fun searchCandidatesHaveRealIdentityAndNoInventedCounts() {
        val candidates=ProfileSearch.parse(JSONObject("""{"status":"ok","users":[{"pk":"200","username":"ipek.kaya","full_name":"İpek Kaya"}]}"""))
        assertEquals(listOf(ProfileCandidate("200","ipek.kaya","İpek Kaya")),candidates)
        assertThrows(ApiFailure::class.java) {ProfileSearch.parse(JSONObject("""{"status":"ok","users":[{"pk":"0","username":"a"}]}"""))}
    }
    @Test fun namesSurviveTheEncryptedPayloadCodecAndOldRecordsRemainReadable() {
        val session=Session("100","ds_user_id=100; sessionid=SYNTHETIC","QA")
        val saved=SavedAccount(session,Profile("100","qa.owner",1,1,1800000000000),listOf(TrackedPerson(listOf(Profile("200","ipek.kaya",3,2,1800000000000,name="İpek Kaya")))))
        assertEquals(saved,AccountCodec.decode(AccountCodec.encode(saved)))
    }
    @Test fun onlyReadSearchTransportIsAllowed() {
        val url="https://i.instagram.com/api/v1/users/search/?query=ipek&count=20"
        assertTrue(AccountRequestPolicy.readAllowed("GET",url))
        assertFalse(AccountRequestPolicy.readAllowed("POST",url,true))
        assertFalse(AccountRequestPolicy.readAllowed("GET",url.replace("i.instagram.com","evil.test")))
    }
}
