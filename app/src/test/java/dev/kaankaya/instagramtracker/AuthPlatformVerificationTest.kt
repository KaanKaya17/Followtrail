package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthPlatformVerificationTest {
    @Test fun onlyTheObservedIdentityPairsMatchAndSchemaKeepsNamesWithoutValues() {
        for (operation in AuthPlatformVerification.operations) {
            assertEquals(operation, AuthPlatformVerification.find(operation.document, operation.name))
            assertNull(AuthPlatformVerification.find("123", operation.name))
            assertNull(AuthPlatformVerification.find(operation.document, "FollowMutation"))
            val schema=JSONObject().put("document",operation.document).put("operation",operation.name).put("kind","mutation")
                .put("incomplete",false).put("arguments",org.json.JSONArray(listOf("input")))
                .put("rootFields",org.json.JSONArray(listOf("synthetic_verification"))).put("secret","private-code")
            val facts=AuthPlatformSchema.inspect(schema.toString(),operation.document,operation.name,"mutation")
            assertTrue(facts.getBoolean("available"));assertFalse(facts.toString().contains("private-code"))
            assertFalse(AuthPlatformSchema.inspect(schema.put("kind","query").toString(),operation.document,operation.name,"mutation").getBoolean("available"))
        }
    }
}
