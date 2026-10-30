package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AuthPlatformCodeEntryTest {
    private fun schema()=JSONObject().put("document",AuthPlatformCodeEntry.DOCUMENT).put("operation",AuthPlatformCodeEntry.OPERATION)
        .put("kind","query").put("incomplete",false).put("arguments",JSONArray(listOf("context","scale")))
        .put("rootFields",JSONArray(listOf("security_view"))).put("ignored","private-token")
    @Test fun staticSchemaIsBoundToTheKnownQueryAndOmitsUnselectedValues() {
        val result=AuthPlatformCodeEntry.schema(schema().toString())
        assertTrue(result.getBoolean("available"));assertFalse(result.getBoolean("incomplete"))
        assertEquals("context",result.getJSONArray("arguments").getString(0));assertFalse(result.toString().contains("private-"))
    }
    @Test fun unknownMalformedWrongKindAndOversizedSchemasRemainUnavailable() {
        for(raw in listOf("null","not-json","x".repeat(2049),schema().put("document","123").toString(),schema().put("kind","mutation").toString(),
            schema().put("arguments",JSONArray(listOf("bad@field"))).toString(),schema().put("rootFields",JSONArray((1..9).map {"field$it"})).toString()))
            assertFalse(AuthPlatformCodeEntry.schema(raw).getBoolean("available"))
        assertTrue(AuthPlatformCodeEntry.schema(schema().put("incomplete",true).toString()).getBoolean("incomplete"))
    }
}
