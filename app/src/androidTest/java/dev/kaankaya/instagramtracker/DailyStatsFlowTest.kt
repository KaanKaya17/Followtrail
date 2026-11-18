package dev.kaankaya.instagramtracker

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real Keystore/AtomicFile and native UI, isolated debug preview records only. */
class DailyStatsFlowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val vault=SessionVault(context,"previewaccount")
    private var scenario: ActivityScenario<DailyStatsActivity>?=null
    private var language="tr"
    private lateinit var account: SavedAccount
    @Before fun setup() {
        check(context.packageName.endsWith(".debug"));language=AppLanguage.selected(context)
        instrumentation.runOnMainSync {AppLanguage.set(context,"tr")}
        vault.clear()
        val seed=PreviewMode.seed(true)!!
        val person=seed.people.first()
        val start=1704067200000L
        val history=(0 until 90).map {person.latest.copy(observedAt=start+it*DailyStats.DAY+1,followers=100L+it)}
        account=seed.copy(people=listOf(person.copy(history=history)))
        vault.write(account)
    }
    @After fun cleanup() {scenario?.close();vault.clear();instrumentation.runOnMainSync {AppLanguage.set(context,language)}}
    @Test fun dailyArchiveSurvivesRollingHistoryAndFreshStoreInstance() {
        val previous=account.people.single().latest
        val next=previous.copy(observedAt=previous.observedAt+DailyStats.DAY,followers=500)
        account=vault.update(account.session){it.copy(people=PeoplePolicy.record(it.people,next))}
        assertEquals(90,account.people.single().history.size)
        val reopened=SessionVault(context,"previewaccount")
        val result=reopened.daily.query(reopened.read()!!,next.userId,"followers",0,System.currentTimeMillis())
        assertEquals(91,result.observedDays);assertEquals(500L,result.points.last().value)
        val files=context.noBackupFilesDir.listFiles().orEmpty().filter {it.name.startsWith("previewaccountdaily")}
        assertTrue(files.isNotEmpty())
        files.forEach {assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("followers"))}
    }
    @Test fun clearDoesNotReseedRecentHistoryAndKeepsTheAccount() {
        val target=account.people.single().latest.userId
        vault.daily.clear(account.session.userId,target,System.currentTimeMillis())
        assertTrue(vault.daily.query(vault.read()!!,target,"followers",0,System.currentTimeMillis()).points.isEmpty())
        assertEquals(account.session,vault.read()!!.session)
        assertEquals(90,vault.read()!!.people.single().history.size)
    }
    @Test fun malformedArchiveFailsWithoutBeingSilentlyReplaced() {
        val files=context.noBackupFilesDir.listFiles().orEmpty().filter {it.name.startsWith("previewaccountdaily") && it.name.endsWith(".enc")}
        val file=files.first {org.json.JSONObject(EncryptedRecord(context,it.name.removeSuffix(".enc")).read()!!).getString("target")==account.people.single().latest.userId};file.writeBytes(byteArrayOf(1,2,3))
        assertThrows(Exception::class.java){vault.daily.query(account,account.people.single().latest.userId,"followers",0,System.currentTimeMillis())}
        assertArrayEquals(byteArrayOf(1,2,3),file.readBytes())
    }
    @Test fun committedBackupOnlyFileIsRecoveredBeforeReading() {
        val target=account.people.single().latest.userId
        val file=context.noBackupFilesDir.listFiles().orEmpty().first {it.name.startsWith("previewaccountdaily") && it.name.endsWith(".enc") &&
            org.json.JSONObject(EncryptedRecord(context,it.name.removeSuffix(".enc")).read()!!).getString("target")==target}
        val backup=java.io.File(file.path+".bak")
        check(file.renameTo(backup))
        assertTrue(EncryptedRecord(context,file.name.removeSuffix(".enc")).exists())
        assertEquals(90,vault.daily.query(account,target,"followers",0,System.currentTimeMillis()).observedDays)
        assertTrue(file.exists());assertFalse(backup.exists())
    }
    @Test fun staleReadAfterAccountDeletionCannotRecreateArchive() {
        val target=account.people.single().latest.userId
        val oldStore=vault.daily
        vault.clear()
        assertThrows(ApiFailure::class.java){oldStore.query(account,target,"followers",0,System.currentTimeMillis())}
        assertFalse(context.noBackupFilesDir.listFiles().orEmpty().any {it.name.startsWith("previewaccountdaily")})
    }
    private fun views(view: View): List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap {views(view.getChildAt(it))} else emptyList())
    private fun waitText(part: String) {
        val until=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<until) {
            var found=false
            scenario!!.onActivity {a->found=views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString().contains(part)}}
            if(found)return
            SystemClock.sleep(30)
        }
        var visible=""
        scenario!!.onActivity {a->visible=views(a.window.decorView).filterIsInstance<TextView>().joinToString(" | ") {it.text}}
        fail("Daily UI did not show $part: $visible")
    }
    @Test fun nativeRangeMetricAndRecreationUseLocalArchive() {
        val target=account.people.single().latest.userId
        scenario=ActivityScenario.launch(Intent(context,DailyStatsActivity::class.java).putExtra("offline_preview",true)
            .putExtra("viewer_id",account.session.userId).putExtra("target_id",target))
        waitText(AppLanguage.wrap(context).getString(R.string.daily_scope))
        scenario!!.onActivity {a->views(a.window.decorView).filterIsInstance<TextView>().first {it.text.toString()==AppLanguage.wrap(context).getString(R.string.daily_all)}.performClick()}
        waitText("90 gün")
        scenario!!.onActivity {a->views(a.window.decorView).filterIsInstance<TextView>().first {it.text.toString()==AppLanguage.wrap(context).getString(R.string.ui_following)}.performClick()}
        waitText("90 gün")
        scenario!!.recreate();waitText("90 gün")
    }
}
