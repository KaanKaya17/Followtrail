package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class WebDocumentFactsTest {
    @Test fun onlyProtocolIdentitySurvivesPrivateVariablesAndUnknownFields() {
        val facts=WebDocumentFacts.inspect("doc_id=123456789&fb_api_req_friendly_name=PolarisSecurityScreenQuery&variables=private-token&password=private-password&code=private-code")
        assertEquals("123456789",facts.document);assertEquals("PolarisSecurityScreenQuery",facts.operation)
        assertFalse(facts.json().toString().contains("private"));assertFalse(facts.json().has("variables"))
    }
    @Test fun malformedDuplicateAndOversizedFieldsDoNotBecomeDocumentFacts() {
        for (raw in listOf("doc_id=1&doc_id=2","doc_id=%xx","doc_id=abc","doc_id=0","doc_id=1&bad",
            "doc_id=1&variables="+"x".repeat(WebLoginContract.MAX_BODY))) assertNull(WebDocumentFacts.inspect(raw).document)
        assertNull(WebDocumentFacts.inspect("fb_api_req_friendly_name=private%40example.com").operation)
    }
}
