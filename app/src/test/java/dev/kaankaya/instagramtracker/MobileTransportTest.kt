package dev.kaankaya.instagramtracker

import mockwebserver3.MockWebServer
import mockwebserver3.MockResponse
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Real loopback TLS/HTTP2 exchanges through the production interceptor chain. No Instagram requests. */
class MobileTransportTest {
    private fun exchange(protocols: List<Protocol> = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1),
        operation: (MockWebServer, okhttp3.OkHttpClient) -> Unit) {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverKeys = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverKeys.sslSocketFactory()); server.protocols = protocols; server.start()
            val client = MobileReadTransport.client().newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
            try { operation(server, client) }
            finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }
    @Test fun http2WorksAnd429DoesNotReplay() = exchange { server, client ->
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "0").body("limited").build())
        client.newCall(Request.Builder().url(server.url("/probe")).header("Authorization", "synthetic").build()).execute().use {
            assertEquals(Protocol.HTTP_2, it.protocol); assertEquals(429, it.code)
        }
        assertEquals(1, server.requestCount)
    }
    @Test fun library503FollowupIsBlockedBeforeSecondTransmission() = exchange { server, client ->
        server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "0").body("retry").build())
        server.enqueue(MockResponse.Builder().code(200).body("must not reach").build())
        assertThrows(IOException::class.java) { client.newCall(Request.Builder().url(server.url("/probe")).build()).execute().close() }
        assertEquals(1, server.requestCount)
    }
    @Test fun http1IsRejectedBeforeCredentialRequestIsSent() = exchange(listOf(Protocol.HTTP_1_1)) { server, client ->
        server.enqueue(MockResponse.Builder().body("must not reach").build())
        assertThrows(IOException::class.java) { client.newCall(Request.Builder().url(server.url("/probe")).header("Authorization", "synthetic").build()).execute().close() }
        assertEquals(0, server.requestCount)
    }
    @Test fun redirectResponseNeverReachesOtherPath() = exchange { server, client ->
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/other")).build())
        server.enqueue(MockResponse.Builder().body("must not reach").build())
        client.newCall(Request.Builder().url(server.url("/probe")).build()).execute().use { assertEquals(302, it.code) }
        assertEquals(1, server.requestCount)
    }
}
