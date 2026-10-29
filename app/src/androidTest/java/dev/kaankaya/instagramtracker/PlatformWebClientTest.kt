package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.net.http.UploadDataSink
import android.webkit.CookieManager
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Request
import okhttp3.Protocol
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

@SuppressLint("NewApi")
class PlatformWebClientTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun encryptedUploadIsByteExactAcrossBuffersAndCannotRewind() {
        assumeTrue(WebRequestClients.platformAvailable())
        val body=WebLoginContract.authorize(SyntheticWebLogin.body()).body
        val expected=Buffer().also {body.writeTo(it)}.readByteArray()
        val owned=expected.copyOf();val upload=OneShotWebUpload(owned)
        var rewindFailed=false
        val sink=object: UploadDataSink() {
            override fun onReadSucceeded(finalChunk: Boolean) {assertFalse(finalChunk)}
            override fun onReadError(error: Exception) {throw AssertionError("Unexpected upload error")}
            override fun onRewindSucceeded() {fail("Credential replay was accepted")}
            override fun onRewindError(error: Exception) {rewindFailed=true}
        }
        val actual=ByteArrayOutputStream()
        while(actual.size()<expected.size) {
            val part=ByteBuffer.allocate(17);upload.read(sink,part);part.flip()
            val bytes=ByteArray(part.remaining());part.get(bytes);actual.write(bytes)
        }
        assertEquals(expected.size.toLong(),upload.length);assertArrayEquals(expected,actual.toByteArray())
        upload.rewind(sink);assertTrue(rewindFailed);upload.close();assertTrue(owned.all {it==0.toByte()})
    }
    @Test fun cancellationAndPolicyRejectionCannotStartAPlatformExchange() {
        assumeTrue(WebRequestClients.platformAvailable())
        PlatformWebClient(context).use { client->
            val cancelled=client.newCall(Request.Builder().url("https://www.instagram.com/accounts/login/").build())
            assertNull(cancelled.tag(String::class))
            val marker=cancelled.tag(String::class) {"synthetic-call-metadata"}
            assertEquals(marker,cancelled.tag(String::class.java))
            assertEquals(marker,cancelled.tag(String::class) {throw AssertionError("Computed twice")})
            assertNull(cancelled.clone().tag(String::class))
            var starts=0;var failures=0
            cancelled.addEventListener(object: okhttp3.EventListener() {
                override fun callStart(call: okhttp3.Call) {starts++}
                override fun callFailed(call: okhttp3.Call, ioe: IOException) {failures++}
            })
            cancelled.cancel();assertThrows(IOException::class.java) {cancelled.execute()}
            assertEquals(1,starts);assertEquals(1,failures)
            val social=client.newCall(Request.Builder().url("https://www.instagram.com/api/v1/friendships/123/follow/")
                .post(okhttp3.FormBody.Builder().build()).build())
            assertThrows(IOException::class.java) {social.execute()}
        }
    }
    /** One opt-in anonymous GET, headers only. Never enters credentials or calls an account API. */
    @Test fun publicPlatformHttp2SmokeRequiresExplicitOptIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live_platform_transport")=="true")
        assumeTrue(WebRequestClients.platformAvailable())
        val before=CookieManager.getInstance().getCookie("https://www.instagram.com/")
        PlatformWebClient(context).use { client ->
            client.newCall(Request.Builder().url("https://www.instagram.com/accounts/login/")
                .header("User-Agent","InstagramTracker transport check").build()).execute().use {response->
                assertEquals("Public transport HTTP status",200,response.code)
                assertEquals(Protocol.HTTP_2,response.protocol);assertEquals(0,response.body!!.bytes().size)
            }
        }
        assertEquals(before,CookieManager.getInstance().getCookie("https://www.instagram.com/"))
    }
}
