package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Real Keystore and AtomicFile boundaries. Only synthetic debug preview records; no network. */
class ArchivePrivacyFlowTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val vault=SessionVault(context,"previewaccount")
    private val lists=FollowListVault(context,"previewlists")
    @Before fun setup() {check(context.packageName.endsWith(".debug"));vault.clear();lists.clear()}
    @After fun cleanup() {vault.clear();lists.clear();AvatarCache(context,"privacytest").clear()}
    @Test fun profileErasureRemovesEveryOwnedArchiveWhileKeepingOtherProfilesAndSession() {
        val saved=PreviewMode.seed(true)!!;val target=saved.people.first().latest
        vault.write(saved);vault.visual.configure(saved,target.userId,true)
        vault.journal.append(saved.session,CheckEvent(target.userId,target.username,CheckKind.PROFILE,null,target.observedAt,target.observedAt))
        assertTrue(vault.daily.query(saved,target.userId,"followers",0,System.currentTimeMillis()).points.isNotEmpty())
        val next=vault.erasePerson(saved.session,target.userId,lists)
        assertEquals(saved.session,next.session)
        assertFalse(next.people.any {it.latest.userId==target.userId})
        assertEquals(saved.people.size-1,next.people.size)
        assertFalse(vault.visual.read(saved.session.userId).getJSONObject("profiles").has(target.userId))
        assertFalse(vault.journal.read(saved.session).any {it.target==target.userId})
        vault.daily.record(saved.session.userId,target.userId,listOf(target))
        assertFalse(context.noBackupFilesDir.listFiles().orEmpty().filter {it.name.startsWith("previewaccountdaily") && !it.name.contains("reset")}
            .any {file->val body=EncryptedRecord(context,file.name.removeSuffix(".enc")).read();body!=null && org.json.JSONObject(body).getString("target")==target.userId})
    }
    @Test fun retentionSurvivesRestartAndExtendingPeriodDoesNotRestoreExpiredDays() {
        val seed=PreviewMode.seed(true)!!;val person=seed.people.first()
        val now=System.currentTimeMillis();val old=person.latest.copy(observedAt=now-40*ArchiveRetention.DAY)
        val saved=seed.copy(people=listOf(person.copy(history=listOf(old))))
        vault.write(saved);vault.visual.configure(saved,old.userId,true)
        val retained=vault.configurePrivacy(saved.session,ArchiveRetention(30,30))
        assertFalse(vault.privacy.read(saved.session.userId).failed)
        assertEquals(old,retained.people.single().latest) // latest baseline remains, even when old
        assertTrue(vault.daily.query(retained,old.userId,"followers",0,now).points.isEmpty())
        assertEquals(0,vault.visual.read(saved.session.userId).getJSONObject("profiles").getJSONObject(old.userId).getJSONArray("entries").length())
        val reopened=SessionVault(context,"previewaccount")
        assertEquals(30,reopened.privacy.read(saved.session.userId).dailyDays)
        reopened.configurePrivacy(saved.session,ArchiveRetention())
        assertTrue(reopened.daily.query(reopened.read()!!,old.userId,"followers",0,now).points.isEmpty())
    }
    @Test fun encryptedThumbnailEnvelopeBindsIdentityAndRemovesLegacyPlaintext() {
        val source=AvatarSource("100","200","https://cdninstagram.com/synthetic.png","one")
        val directory=File(context.cacheDir,"privacytest").apply {mkdirs()}
        File(directory,source.key).writeBytes(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47))
        val cache=AvatarCache(context,"privacytest");cache.migrateLegacy()
        assertFalse(File(directory,source.key).exists())
        val bytes="synthetic-thumbnail".toByteArray()
        cache.write(source.key,bytes){true}
        assertArrayEquals(bytes,AvatarCache(context,"privacytest").read(source.key))
        assertFalse(File(directory,source.key).readBytes().toString(Charsets.ISO_8859_1).contains("synthetic-thumbnail"))
        val other=source.copy(owner="300").key
        File(directory,source.key).copyTo(File(directory,other),overwrite=true)
        assertNull(cache.read(other));assertArrayEquals(bytes,cache.read(source.key))
    }
    @Test fun unreadableAuxiliaryArchiveReportsFailureAndPreservesProfileRegistry() {
        val saved=PreviewMode.seed(true)!!;vault.write(saved)
        val target=saved.people.first().latest
        File(context.noBackupFilesDir,"previewaccountvisual.enc").writeBytes(byteArrayOf(1,2,3))
        assertThrows(Exception::class.java){vault.erasePerson(saved.session,target.userId,lists)}
        assertTrue(vault.read()!!.people.any {it.latest.userId==target.userId})
        vault.configurePrivacy(saved.session,ArchiveRetention(30,0))
        assertTrue(vault.privacy.read(saved.session.userId).failed)
        assertTrue(vault.read()!!.people.any {it.latest.userId==target.userId})
    }
}
