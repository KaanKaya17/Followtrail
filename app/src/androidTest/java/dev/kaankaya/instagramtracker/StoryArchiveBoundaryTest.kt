package dev.kaankaya.instagramtracker

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Real Android Keystore and disk, synthetic media; never opens a network connection. */
class StoryArchiveBoundaryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val vault = SessionVault(context,"storyqatest")
    private val session = Session("100","ds_user_id=100; sessionid=synthetic-story","synthetic")
    private val store = StoryArchiveStore(context,"100","storyqatest")
    private val facts = StoryFacts("300","200",1800000000000,1800086400000,StoryKind.IMAGE,
        listOf(StoryMention("900","qa.tagged")),null,null,null,null)
    private fun picture(): ByteArray {
        val bitmap = Bitmap.createBitmap(36,64,Bitmap.Config.ARGB_8888)
        return java.io.ByteArrayOutputStream().use { out ->
            try { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); out.toByteArray() } finally { bitmap.recycle() }
        }
    }
    @Before fun setup() {
        check(context.packageName.endsWith(".debug")); vault.clear(); store.clearAll()
        vault.write(SavedAccount(session,Profile("100","owner",1,1,1800000000000),
            listOf(TrackedPerson(listOf(Profile("200","friend",1,1,1800000000000))))))
    }
    @After fun cleanup() { vault.clear(); store.clearAll() }
    @Test fun filesAreEncryptedAndRetriedMetadataKeepsFirstCapture() {
        val raw = picture(); store.metadata(facts,1800000100000); store.metadata(facts,1800000200000)
        store.saveMedia(facts,raw)
        assertArrayEquals(raw,store.media("200","300").second)
        assertEquals(1800000100000,store.read("200").items.single().capturedAt)
        val root = java.io.File(context.noBackupFilesDir,"storymedia/storyqatest")
        assertTrue(root.listFiles()!!.isNotEmpty())
        root.listFiles()!!.forEach { assertFalse(it.readBytes().toList().windowed(raw.size).any { window -> window == raw.toList() }) }
        val index = java.io.File(context.noBackupFilesDir,"storyqateststories.enc")
        assertFalse(index.readText(Charsets.ISO_8859_1).contains("qa.tagged"))
        store.clearTarget("200"); assertTrue(store.read("200").items.isEmpty()); assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun profileErasureDuringDownloadCannotRecreateMediaOrIndexRows() {
        var downloads = 0
        val source = object : StoryFeedSource {
            override fun read(session: Session,target: String) = listOf(StoryCandidate(facts,"https://scontent.cdninstagram.com/synthetic.png"))
            override fun cancel() = Unit
        }
        val media = object : StoryMediaSource {
            override fun read(session: Session,url: String): ByteArray { downloads++; vault.erasePerson(session,"200"); return picture() }
            override fun cancel() = Unit
        }
        val gate = RequestGate(context,"storyqatestgate") { 1800000300000 }
        try {
            StoryFetch(vault,store,gate,source,media).read(session,"200",{false}) {_,_->}
            fail("Removed profile resurrected")
        } catch (failure: ApiFailure) { assertEquals(ApiFailure.Kind.CANCELLED,failure.kind) }
        assertEquals(1,downloads); assertTrue(store.read("200").items.isEmpty())
    }
    @Test fun cancellationKeepsAlreadyCommittedMetadataAndNeverDownloads() {
        var cancelled = false
        val source = object : StoryFeedSource {
            override fun read(session: Session,target: String): List<StoryCandidate> { cancelled = true; return listOf(StoryCandidate(facts,null)) }
            override fun cancel() = Unit
        }
        val media = object : StoryMediaSource {
            override fun read(session: Session,url: String): ByteArray = error("Unexpected download")
            override fun cancel() = Unit
        }
        store.metadata(facts,1800000100000)
        try { StoryFetch(vault,store,RequestGate(context,"storyqatestgate"),source,media)
            .read(session,"200",{cancelled}) {_,_->}; fail() }
        catch (failure: ApiFailure) { assertEquals(ApiFailure.Kind.CANCELLED,failure.kind) }
        assertEquals(1,store.read("200").items.size); assertNull(store.read("200").checkedAt)
    }
    @Test fun corruptAccountStillAllowsErasingItsOwnedArchiveWithoutReadingAccountIdentity() {
        store.metadata(facts,1800000100000); store.saveMedia(facts,picture())
        val accountFile = java.io.File(context.noBackupFilesDir,"storyqatest.enc")
        val corrupt = accountFile.readBytes(); corrupt[corrupt.lastIndex] = (corrupt.last().toInt() xor 1).toByte()
        accountFile.writeBytes(corrupt)
        try { vault.read(); fail("Tampered account accepted") } catch (_: java.security.GeneralSecurityException) {}
        vault.clear()
        assertFalse(vault.exists())
        assertFalse(java.io.File(context.noBackupFilesDir,"storyqateststories.enc").exists())
        assertFalse(java.io.File(context.noBackupFilesDir,"storymedia/storyqatest").exists())
    }
}
