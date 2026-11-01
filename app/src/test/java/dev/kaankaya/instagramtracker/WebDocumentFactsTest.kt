package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class WebDocumentFactsTest {
    @Test fun observedVerificationMutationsRetainOnlyFieldNamesAndTypes() {
        for (operation in AuthPlatformVerification.operations) {
            val variables="""{"input":{"apc":"private-context","code":{"sensitive_string_value":"private-code"},"device_id":null},"scale":2}"""
            val facts=WebDocumentFacts.inspect("doc_id=${operation.document}&fb_api_req_friendly_name=${operation.name}&variables="+
                java.net.URLEncoder.encode(variables,"UTF-8")).json()
            assertEquals("OBJECT",facts.getJSONObject("variableTypes").getString("input"))
            assertEquals("STRING",facts.getJSONObject("inputTypes").getString("input.apc"))
            assertEquals("OBJECT",facts.getJSONObject("inputTypes").getString("input.code"))
            assertFalse(facts.toString().contains("private-"))
            assertFalse(facts.toString().contains("sensitive_string_value"))
            assertFalse(facts.getBoolean("variablesIncomplete"))
        }
    }
    @Test fun recognizedSecurityQueryKeepsOnlyRootVariableTypes() {
        val raw="doc_id="+AuthPlatformCodeEntry.DOCUMENT+"&fb_api_req_friendly_name="+AuthPlatformCodeEntry.OPERATION+
            "&variables="+java.net.URLEncoder.encode("""{"context":"private-token","scale":2,"enabled":true,"input":{"password":"private-password"},"items":["private-code"],"empty":null}""","UTF-8")
        val facts=WebDocumentFacts.inspect(raw).json();val types=facts.getJSONObject("variableTypes")
        assertEquals("STRING",types.getString("context"));assertEquals("NUMBER",types.getString("scale"))
        assertEquals("BOOLEAN",types.getString("enabled"));assertEquals("OBJECT",types.getString("input"))
        assertEquals("ARRAY",types.getString("items"));assertEquals("NULL",types.getString("empty"))
        assertFalse(facts.getBoolean("variablesIncomplete"));assertFalse(facts.toString().contains("private-"));assertFalse(facts.toString().contains("password"))
        assertTrue(WebDocumentFacts.inspect("doc_id="+AuthPlatformCodeEntry.DOCUMENT+"&fb_api_req_friendly_name="+AuthPlatformCodeEntry.OPERATION+"&variables=not-json")
            .json().getBoolean("variablesIncomplete"))
    }
    @Test fun onlyProtocolIdentitySurvivesPrivateVariablesAndUnknownFields() {
        val facts=WebDocumentFacts.inspect("doc_id=123456789&fb_api_req_friendly_name=PolarisSecurityScreenQuery&variables=private-token&password=private-password&code=private-code")
        assertEquals("123456789",facts.document);assertEquals("PolarisSecurityScreenQuery",facts.operation)
        assertFalse(facts.json().toString().contains("private"));assertFalse(facts.json().has("variables"))
    }
    @Test fun malformedDuplicateAndOversizedFieldsDoNotBecomeDocumentFacts() {
        for (raw in listOf("doc_id=1&doc_id=2","doc_id=%xx","doc_id=abc","doc_id=0","doc_id=1&bad",
            "doc_id=1&variables="+"x".repeat(WebGraphqlForm.MAX_BODY))) assertNull(WebDocumentFacts.inspect(raw).document)
        assertNull(WebDocumentFacts.inspect("fb_api_req_friendly_name=private%40example.com").operation)
    }
}
