package dev.kaankaya.instagramtracker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File

/** Real Keystore/vaults, production selection and scheduler; all account data and transports synthetic. */
class MultiAccountTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val catalog get()=AccountCatalog(context)
    private var scenario: ActivityScenario<MainActivity>?=null
    private fun saved(id: String,name: String="synthetic$id",at: Long=1000)=SavedAccount(
        Session(id,"ds_user_id=$id; sessionid=synthetic-$id","Synthetic UA"),Profile(id,name,1,1,at),
        listOf(TrackedPerson(listOf(Profile("900","friend$id",2,3,at)),"note-$id","group-$id",favorite=true)))
    @Before fun setup() {check(context.packageName.endsWith(".debug"));cleanup()}
    @After fun cleanup() {
        scenario?.close();scenario=null;MonitorScheduler.cancel(context)
        catalog.entries().forEach {SessionVault(context,owner=it.id).clear()}
        MobileSessions(context).clear();WebSessions(context).clear()
        EncryptedRecord(context,"account",SessionVault.ACCOUNT_BYTES).clear()
        EncryptedRecord(context,"accounts",128*1024).clear()
        context.getSharedPreferences("request_gate",0).edit().clear().commit()
        ChangeNotifications(context).clear()
    }
    @Test fun legacyMigrationAddsCatalogWithoutRewritingExistingPayloadAndKeepsAuxiliaryHistory() {
        val old=saved("100")
        val legacy=EncryptedRecord(context,"account",SessionVault.ACCOUNT_BYTES);legacy.write(AccountCodec.encode(old))
        val bytes=File(context.noBackupFilesDir,"account.enc").readBytes()
        // Simulate upgrading before the catalog exists, including existing device refresh preferences.
        context.getSharedPreferences("request_gate",0).edit().putBoolean("own_auto_refresh",true).commit()
        catalog.initialize()
        assertEquals("account",catalog.entries().single().slot)
        assertEquals(old,SessionVault(context).read());assertArrayEquals(bytes,File(context.noBackupFilesDir,"account.enc").readBytes())
        assertTrue(RequestGate(context).ownAutoRefresh())
        SessionVault(context).write(saved("200"));catalog.select("100")
        assertEquals(old,SessionVault(context).read());assertEquals(2,catalog.entries().size)
    }
    @Test fun accountSelectionAndReconnectKeepIndependentNotesAndUpdateIdentityInsteadOfDuplicating() {
        val vault=SessionVault(context);val a=saved("100");val b=saved("200")
        vault.write(a);vault.write(b);catalog.select("100")
        assertEquals(a,vault.read());catalog.select("200");assertEquals(b,vault.read())
        val newSession=a.session.copy(cookie="ds_user_id=100; sessionid=synthetic-rotated")
        vault.link(newSession,a.profile.copy(username="renamed.synthetic"))
        assertEquals(a.people,vault.read()!!.people);assertEquals(2,catalog.entries().size)
        assertEquals("renamed.synthetic",catalog.entries().first {it.id=="100"}.username)
        catalog.select("200");assertEquals(b,vault.read())
    }
    @Test fun threeWebSessionsAndSameOwnerRotationSurviveAccountSwitching() {
        val sessions=WebSessions(context);val vault=SessionVault(context)
        val references=(100..102).map {number->
            val id=number.toString();val state=WebSessionCodec.fromHeader("ds_user_id=$id; sessionid=synthetic-$id","Synthetic UA")
            val reference=sessions.stage(state)
            vault.link(reference,Profile(id,"synthetic$id",1,1,1000));sessions.discardPending(reference)
            reference
        }
        references.forEach {ref->catalog.select(ref.userId);assertEquals(ref,sessions.read(ref).reference())}
        val old=references.first();val fresh=WebSessionCodec.fromHeader("ds_user_id=100; sessionid=synthetic-new","Synthetic UA")
        val pending=sessions.stage(fresh);assertEquals(old,sessions.read(old).reference())
        vault.linkIfUnchanged(vault.read()?.session,pending,Profile("100","synthetic100",1,1,2000)){false}
        sessions.discardPending(pending)
        assertEquals(fresh,sessions.read(pending));assertEquals(3,catalog.entries().size)
        assertEquals(references[1],sessions.read(references[1]).reference())
    }
    @Test fun legacyWebCredentialsMigrateOnceAndKeepTheirOwnerAfterSwitching() {
        val old=WebSessionCodec.fromHeader("ds_user_id=100; sessionid=synthetic-legacy","Synthetic UA")
        EncryptedRecord(context,"account",SessionVault.ACCOUNT_BYTES).write(AccountCodec.encode(
            SavedAccount(old.reference(),Profile("100","legacy.synthetic",1,1,1000))))
        EncryptedRecord(context,"weba").write(WebSessionCodec.encode(old))
        EncryptedRecord(context,"webmigration").clear()
        val sessions=WebSessions(context)
        assertEquals(old,sessions.read(old.reference()))
        val next=WebSessionCodec.fromHeader("ds_user_id=200; sessionid=synthetic-next","Synthetic UA")
        val reference=sessions.stage(next);SessionVault(context).link(reference,Profile("200","next.synthetic",1,1,2000))
        sessions.discardPending(reference)
        assertEquals(old,sessions.read(old.reference()));assertEquals(next,sessions.read(reference))
        SessionVault(context,owner="100").clear()
        assertThrows(ApiFailure::class.java){sessions.read(old.reference())}
        assertEquals(next,sessions.read(reference))
    }
    @Test fun failedOrCancelledConnectionDoesNotSelectOrRegisterTheStagedAccount() {
        val old=saved("100");SessionVault(context).write(old)
        val sessions=WebSessions(context)
        val staged=sessions.stage(WebSessionCodec.fromHeader("ds_user_id=200; sessionid=synthetic-pending","Synthetic UA"))
        assertEquals("100",catalog.active());assertEquals(listOf("100"),catalog.entries().map {it.id})
        sessions.discardPending(staged)
        assertEquals(old,SessionVault(context).read());assertNull(sessions.pending())
        assertThrows(ApiFailure::class.java) {sessions.read(staged)}
    }
    @Test fun interruptedStagingKeepsCleanupOwnershipAndCannotRemoveTheCommittedSession() {
        val sessions=WebSessions(context)
        val active=WebSessionCodec.fromHeader("ds_user_id=100; sessionid=synthetic-active","Synthetic UA")
        val reference=sessions.stage(active);SessionVault(context).link(reference,Profile("100","active.synthetic",1,1,1000))
        sessions.discardPending(reference)
        val pending=WebSessionCodec.fromHeader("ds_user_id=100; sessionid=synthetic-pending","Synthetic UA")
        // Stop after the payload write, before SharedPreferences receives its secondary pointer.
        val prefix=catalog.storage("100")+"web"
        EncryptedRecord(context,prefix+"b").write(WebSessionCodec.encode(pending))
        EncryptedRecord(context,"webpendingowner").write(JSONObject().put("owner","100").put("binding",pending.binding).toString())
        assertEquals(pending.reference(),WebSessions(context).pending())
        WebSessions(context).discardPending()
        assertNull(sessions.pending());assertEquals(active,sessions.read(reference))
        assertThrows(ApiFailure::class.java){sessions.read(pending.reference())}
    }
    @Test fun lateForegroundCommitCannotWriteToNewSelectionAndPinnedBackgroundCommitKeepsItsOwner() {
        val a=saved("100");val b=saved("200");val foreground=SessionVault(context)
        foreground.write(a);foreground.write(b)
        assertThrows(ApiFailure::class.java) {foreground.update(a.session){it.copy(people=emptyList())}}
        SessionVault(context,owner="100").update(a.session){it.copy(profile=it.profile.copy(followers=9))}
        assertEquals("200",catalog.active());assertEquals(b,foreground.read())
        assertEquals(9L,SessionVault(context,owner="100").read()!!.profile.followers)
    }
    @Test fun removingOneAccountPreservesOtherCredentialsAndTheSharedRestriction() {
        val vault=SessionVault(context);val sessions=WebSessions(context)
        val refs=listOf("100","200").map {id->
            val state=WebSessionCodec.fromHeader("ds_user_id=$id; sessionid=synthetic-$id","Synthetic UA")
            sessions.stage(state).also {vault.link(it,Profile(id,"synthetic$id",1,1,1000));sessions.discardPending(it)}
        }
        val gate=RequestGate(context);gate.limited(null,reason="Synthetic test restriction");val until=gate.blockedUntil()
        SessionVault(context,owner="100").clear()
        assertEquals(listOf("200"),catalog.entries().map {it.id});assertEquals(refs[1],sessions.read(refs[1]).reference())
        assertThrows(ApiFailure::class.java) {sessions.read(refs[0])}
        assertEquals(until,RequestGate(context).blockedUntil());assertTrue(gate.manualOnly())
    }
    @Test fun connectionAndRefreshSettingsArePerAccountWhileBudgetsAnd429AreShared() {
        val vault=SessionVault(context);vault.write(saved("100"));vault.write(saved("200"))
        val gate=RequestGate(context);val a=gate.forAccount("100");val b=gate.forAccount("200")
        a.pauseForConnection();a.setOwnAutoRefresh(true)
        assertTrue(a.requiresConnection());assertFalse(b.requiresConnection());assertFalse(b.ownAutoRefresh())
        a.limited(null,reason="Synthetic restriction");catalog.select("100");catalog.select("200")
        assertEquals(a.blockedUntil(),b.blockedUntil());assertThrows(ApiFailure::class.java){b.begin()}
        assertEquals(0L,gate.attempts())
    }
    @Test fun vaultFilesDoNotContainPlaintextNotesOrSessionValues() {
        SessionVault(context).write(saved("100"));SessionVault(context).write(saved("200"))
        val files=context.noBackupFilesDir.listFiles().orEmpty().filter {it.extension=="enc"}
        assertTrue(files.size>=3)
        for(file in files) {
            val text=file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(text.contains("synthetic-100"));assertFalse(text.contains("note-100"));assertFalse(text.contains("note-200"))
        }
    }
    @Test fun interruptedRemovalIsRetriedWithoutRemovingTheOtherAccount() {
        SessionVault(context).write(saved("100"));SessionVault(context).write(saved("200"))
        catalog.removing("100")
        // A process can stop after any one owned payload has been deleted.
        EncryptedRecord(context,catalog.storage("100"),SessionVault.ACCOUNT_BYTES).clear()
        AccountCatalog(context).initialize()
        assertEquals(listOf("200"),catalog.entries().map {it.id});assertEquals("200",catalog.active())
        assertEquals(saved("200"),SessionVault(context).read())
    }
    @Test fun reconnectKeepsDatedListHistoryButNeverReusesThePreviousSessionCursor() {
        val vault=SessionVault(context);val old=saved("100");vault.write(old)
        val lists=FollowListVault(context)
        val page=FollowPage("100",FollowDirection.FOLLOWERS,null,listOf(FollowUser("900","friend",null,null,null,null)),
            FollowField(true,"synthetic-cursor"),FollowField(true,false),FollowField(true,true),FollowField(false,null),2000)
        lists.commit(old.session,page)
        val updated=old.session.copy(webBinding="a".repeat(32),cookie="")
        vault.link(updated,old.profile.copy(observedAt=3000))
        val archive=lists.archive(updated);val target=archive.targets.single()
        assertEquals(2000L,target.snapshot!!.followers!!.latest.observedAt)
        assertFalse(target.followers.continuing)
        assertNull(AutomaticQueue.page(target,FollowDirection.FOLLOWERS,4000)?.cursor)
    }
    private fun all(view: View): List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap {all(view.getChildAt(it))}else emptyList()
    private fun until(test: (MainActivity)->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+10000
        while(android.os.SystemClock.elapsedRealtime()<end) {var done=false;scenario!!.onActivity {done=test(it)};if(done)return;Thread.sleep(40)}
        fail("Account UI did not reach the expected state")
    }
    @SdkSuppress(minSdkVersion=29)
    @Test fun realAccountPickerChangesOnlyLocalSelectionAndNotificationOpensItsOwnAccount() {
        val vault=SessionVault(context);vault.write(saved("100"));vault.write(saved("200"))
        RequestGate(context).setOwnAutoRefresh(true)
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("local_account_switch",true))
        until {a->all(a.window.decorView).any {it.contentDescription==a.getString(R.string.ui_options)}}
        scenario!!.onActivity {a->all(a.window.decorView).single {it.contentDescription==a.getString(R.string.ui_options)}.performClick()}
        instrumentation.runOnMainSync {
            val list=WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<ListView>().first {v->(0 until v.adapter.count).any {v.adapter.getItem(it)==context.getString(R.string.accounts_title)}}
            val index=(0 until list.adapter.count).first {list.adapter.getItem(it)==context.getString(R.string.accounts_title)}
            list.performItemClick(list.getChildAt(index),index,list.adapter.getItemId(index))
        }
        instrumentation.runOnMainSync {
            val list=WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<ListView>().first {v->(0 until v.adapter.count).any {v.adapter.getItem(it)=="@synthetic100"}}
            val index=(0 until list.adapter.count).first {list.adapter.getItem(it)=="@synthetic100"}
            list.performItemClick(list.getChildAt(index),index,list.adapter.getItemId(index))
        }
        until {catalog.active()=="100"}
        Thread.sleep(300);assertEquals(0L,RequestGate(context).attempts())
        scenario!!.close();scenario=null
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("local_account_switch",true)
            .putExtra("notification_owner","200").putExtra("notification_person","900"))
        until {catalog.active()=="200"}
        assertEquals(0L,RequestGate(context).attempts());assertEquals("200",vault.read()!!.session.userId)
    }
    @Test fun importedConnectionCannotSelectItsLateResultAfterAnotherAccountWasSelected() {
        val vault=SessionVault(context);val a=saved("100");val b=saved("200");vault.write(a);vault.write(b);catalog.select("100")
        val pending=saved("300")
        assertEquals(ApiFailure.Kind.CANCELLED,assertThrows(ApiFailure::class.java) {
            vault.linkAfterReading(pending.session,{false}) {catalog.select("200");pending.profile}
        }.kind)
        assertEquals(b,vault.read());assertEquals(setOf("100","200"),catalog.entries().map {it.id}.toSet())
        assertEquals(a,SessionVault(context,owner="100").read())
    }
    @Test fun importedConnectionCancellationAndSuccessUseTheSameCommitBoundary() {
        val vault=SessionVault(context);val a=saved("100");vault.write(a);val pending=saved("200")
        var cancelled=true;var reads=0
        assertThrows(ApiFailure::class.java) {vault.linkAfterReading(pending.session,{cancelled}) {reads++;pending.profile}}
        assertEquals(0,reads);cancelled=false
        assertThrows(ApiFailure::class.java) {vault.linkAfterReading(pending.session,{cancelled}) {cancelled=true;reads++;pending.profile}}
        assertEquals(a,vault.read());assertEquals(listOf("100"),catalog.entries().map {it.id})
        cancelled=false
        vault.linkAfterReading(pending.session,{cancelled}) {reads++;pending.profile}
        assertEquals("200",catalog.active());assertEquals(a,SessionVault(context,owner="100").read());assertEquals(2,reads)
    }
    @Test fun sharedBackgroundRoundVisitsBothAccountsWithoutChangingVisibleSelection() {
        var now=System.currentTimeMillis();val called=mutableListOf<String>()
        val runtime=object:ReadRuntime {override fun now()=now;override fun elapsed()=now
            override fun pause(milliseconds: Long,cancelled: ()->Boolean): Boolean {now+=milliseconds;return !cancelled()}}
        val vault=SessionVault(context)
        listOf("100","200").forEach {id->vault.write(saved(id,at=now).copy(people=emptyList(),automation=ListAutomation(true,setOf(id))))}
        val gate=RequestGate(context,clock={now})
        AccountMonitor(context,gate,api={InstagramApi(it){throw AssertionError("Only list requests expected")}},lists={scoped->
            object:FollowListSource {
                override fun cancel()=Unit
                override fun read(session: Session,target: String,direction: FollowDirection,cursor: String?): FollowPage=scoped.claim().use {
                    scoped.begin(now);called+=session.userId;scoped.observedStatus(200)
                    FollowPage(target,direction,cursor,listOf(FollowUser("900","friend",null,null,null,null)),FollowField(true,null),
                        FollowField(true,false),FollowField(true,false),FollowField(false,null),now)
                }
            }
        },runtime=runtime).cycle({false})
        assertEquals(listOf("100","200","100","200"),called)
        assertEquals("200",catalog.active());assertEquals(4L,gate.attempts())
        listOf("100","200").forEach {id->assertEquals(id,FollowListVault(context,owner=id).archive(SessionVault(context,owner=id).read()!!.session).owner)}
    }
    @Test fun staleVisualHistoryButtonCannotOverwriteTheNewlySelectedAccountsArchive() {
        val vault=SessionVault(context);val a=saved("100");val b=saved("200")
        vault.write(a);vault.visual.configure(a,"100",true)
        vault.write(b);vault.visual.configure(b,"200",true)
        val before=SessionVault(context,owner="200").visual.read("200").toString()
        catalog.select("100")
        ActivityScenario.launch<VisualHistoryActivity>(Intent(context,VisualHistoryActivity::class.java)
            .putExtra("viewer_id","100").putExtra("target_id","100")).use {activity->
            activity.onActivity {screen->
                val oldButton=all(screen.window.decorView).filterIsInstance<TextView>().single {it.text==screen.getString(R.string.visual_stop)}
                // Change selection between rendering and the old click callback, as a notification can.
                catalog.select("200");oldButton.performClick()
            }
            assertEquals(before,SessionVault(context,owner="200").visual.read("200").toString())
            assertEquals("200",catalog.active())
        }
    }
}
