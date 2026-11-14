package dev.kaankaya.instagramtracker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Real encrypted journal and Activities; synthetic preview data and no Instagram calls. */
class TimelineFlowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val vault=SessionVault(context,"previewaccount")
    private var scenario: ActivityScenario<TimelineActivity>?=null
    private var language="tr"
    @Before fun setup() {
        check(context.packageName.endsWith(".debug"));language=AppLanguage.selected(context)
        instrumentation.runOnMainSync {AppLanguage.set(context,"tr")};instrumentation.waitForIdleSync()
        vault.clear();vault.write(PreviewMode.seed(true)!!)
        context.getSharedPreferences("preview_gate",0).edit().clear().commit()
    }
    @After fun cleanup() {
        scenario?.close();vault.clear();PreviewMode.cancel(context)
        context.getSharedPreferences("preview_gate",0).edit().clear().commit()
        instrumentation.runOnMainSync {AppLanguage.set(context,language)};instrumentation.waitForIdleSync()
    }
    private fun views(view: View): List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap {views(view.getChildAt(it))}else emptyList()
    private fun texts(): List<String> {
        var result=emptyList<String>();scenario!!.onActivity {result=views(it.window.decorView).filterIsInstance<TextView>().map {view->view.text.toString()}};return result
    }
    private fun await(part: String) {
        val until=android.os.SystemClock.elapsedRealtime()+7000
        while(android.os.SystemClock.elapsedRealtime()<until) {if(texts().any {it.contains(part)})return;Thread.sleep(30)}
        fail("Missing label: $part; "+texts().joinToString(" | "))
    }
    private fun click(label: String) {scenario!!.onActivity {activity -> views(activity.window.decorView).filterIsInstance<TextView>().single {it.text==label && it.isClickable}.performClick()};instrumentation.waitForIdleSync()}
    private fun launch(owner: String=vault.read()!!.session.userId,target: String?="201",notifications: Boolean=false) {
        scenario=ActivityScenario.launch(Intent(context,TimelineActivity::class.java).putExtra("viewer_id",owner)
            .putExtra("target_id",target).putExtra("offline_preview",true).putExtra("notifications_only",notifications))
    }
    @Test fun routineChecksCollapseWithoutUnknownComparisonsOrNetworkRequests() {
        val saved=vault.read()!!;val now=System.currentTimeMillis()
        vault.record(saved.session,CheckEvent("201","ada.demo",CheckKind.PROFILE,now-10,now,now,now-1000,0,0))
        vault.record(saved.session,CheckEvent("200","deniz.demo",CheckKind.PROFILE,null,now,now))
        val gate=RequestGate(context,"preview_gate");gate.limited(null);val pause=gate.blockedUntil()
        launch();await("Rutin kontroller · 1")
        scenario!!.onActivity {activity -> assertFalse(views(activity.window.decorView).any {it.contentDescription?.toString()?.startsWith("@ada.demo.")==true})}
        click("Rutin kontroller · 1 ▾");await("Profil kontrol edildi")
        assertFalse(texts().any {it.contains("Bilinmiyor")});assertFalse(texts().contains("@deniz.demo"))
        scenario!!.recreate();await("Profil kontrol edildi")
        assertEquals(0L,gate.attempts());assertEquals(pause,gate.blockedUntil());assertEquals(saved,vault.read())
    }
    @Test fun notificationGroupsExactFactsAndPersistsReadStateThroughRecreation() {
        val saved=vault.read()!!;val now=System.currentTimeMillis()
        val edit=ProfileEdit("201","old_name",ProfileChangeField.BIOGRAPHY,"before\nline","after\nline",now-1000,now)
        val event=CheckEvent("201","old_name",CheckKind.PROFILE,null,now,now,now-1000,2,-1,setOf(ProfileChangeField.BIOGRAPHY),
            beforeFollowers=10,afterFollowers=12,beforeFollowing=5,afterFollowing=4,edits=listOf(edit))
        assertTrue(vault.journal.append(saved.session,event))
        launch(notifications=true);await("10 → 12");await("5 → 4")
        assertEquals(1,vault.journal.unreadCount(saved.session))
        scenario!!.onActivity {activity -> views(activity.window.decorView).single {it.contentDescription?.toString()?.startsWith("@old_name.")==true}.performClick()}
        val until=android.os.SystemClock.elapsedRealtime()+5000
        while(vault.journal.unreadCount(saved.session)!=0 && android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(30)
        assertEquals(0,vault.journal.unreadCount(saved.session));assertEquals(event,vault.journal.read(saved.session).single())
        scenario!!.recreate();await("Okundu");click("Okunmamış");await("Okunmamış bildirim yok.")
        assertEquals(0L,RequestGate(context,"preview_gate").attempts())
    }
    @Test fun markAllReadIncludesNotificationsOutsideTheSelectedProfile() {
        val saved = vault.read()!!
        val now = System.currentTimeMillis()
        for ((id, username) in listOf("201" to "ada.demo", "200" to "deniz.demo")) {
            assertTrue(vault.journal.append(saved.session,
                CheckEvent(id, username, CheckKind.PROFILE, null, now, now, now - 1000, 1, 0)))
        }
        launch(notifications = true)
        await("@ada.demo")
        click("Okunmamış")
        click("Tümünü okundu olarak işaretle")
        await("Okunmamış bildirim yok.")
        assertEquals(0, vault.journal.unreadCount(saved.session))
        assertEquals(2, vault.journal.read(saved.session).size)
        scenario!!.recreate()
        await("Okunmamış bildirim yok.")
    }
    @Test fun journalPreservesLegacyFactsAndRulesAcrossNewInstancesAndForeignOwners() {
        val saved=vault.read()!!;val legacy=CheckEvent("201","ada.demo",CheckKind.PROFILE,null,2000,3000,1000,1,0)
        assertTrue(vault.journal.append(saved.session,legacy))
        val rules=NoticeRule(fields=setOf(NoticeField.USERNAME))
        vault.journal.setRule(saved.session,"201",rules);vault.journal.markRead(saved.session,legacy.key)
        val reloaded=SessionVault(context,"previewaccount").journal
        assertEquals(rules,reloaded.rule(saved.session,"201"));assertEquals(setOf(legacy.key),reloaded.readKeys(saved.session))
        assertNull(reloaded.read(saved.session).single().beforeFollowers)
        assertTrue(reloaded.read(saved.session.copy(userId="999")).isEmpty())
        assertTrue(reloaded.readKeys(saved.session.copy(userId="999")).isEmpty())
        reloaded.clearTarget(saved.session,"201");assertEquals(NoticeRule(),reloaded.rule(saved.session,"201"));assertTrue(reloaded.readKeys(saved.session).isEmpty())
    }
    @Test fun englishTimelineShowsListChangesWithoutInventingMissingNames() {
        instrumentation.runOnMainSync {AppLanguage.set(context,"en")};instrumentation.waitForIdleSync()
        val saved=vault.read()!!
        vault.record(saved.session,CheckEvent("201","ada.demo",CheckKind.LIST,null,2000,3000,1000,
            direction=FollowDirection.FOLLOWERS,coverage=FollowCoverage.END_OBSERVED,pages=2,accounts=100,added=1,missing=1))
        launch();await("Newly seen: 1");assertTrue(texts().contains("Timeline"));assertEquals(0L,RequestGate(context,"preview_gate").attempts())
    }
    @Test fun aForeignViewerCannotOpenLocalHistory() {
        launch("999");val until=android.os.SystemClock.elapsedRealtime()+5000
        while(scenario!!.state!=androidx.lifecycle.Lifecycle.State.DESTROYED && android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(30)
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,scenario!!.state)
    }
}
