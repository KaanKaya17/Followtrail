package dev.kaankaya.instagramtracker

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real foreground lifecycle/Keystore/queue, timestamp-only gate; no network. */
class InitialPreparationFlowTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val vault=SessionVault(context,"initialaccount")
    private val lists=FollowListVault(context,"initiallists")
    private val prefs get()=context.getSharedPreferences("initial_gate",0)
    private val gate get()=RequestGate(context,"initial_gate")
    private val session=Session("100","","Synthetic UA","a".repeat(32))
    private var preparation: InitialListPreparation?=null
    private var scenario: ActivityScenario<MainActivity>?=null
    private fun page(direction: FollowDirection,cursor: String?,next: String?,ids: List<String>)=
        FollowPage("100",direction,cursor,ids.map { FollowUser(it,"synthetic$it",null,null,null,null) },
            FollowField(true,next),FollowField(false,null),FollowField(true,next!=null),FollowField(false,null),System.currentTimeMillis())
    private fun waitUntil(test:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+8000
        while(!test()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(30)
        assertTrue(test())
    }
    @Before fun setup(){check(context.packageName.endsWith(".debug"));cleanup();vault.link(session,Profile("100","synthetic",2,1,System.currentTimeMillis()))}
    @After fun cleanup(){instrumentation.runOnMainSync { preparation?.close();preparation=null };scenario?.close();scenario=null
        vault.clear();lists.clear();prefs.edit().clear().commit();PreviewMode.cancel(context)}
    @Test fun linkingOptsIntoOwnListsAndRelinkingPreservesExplicitPause(){
        assertTrue(vault.read()!!.automation.enabled);assertEquals(setOf("100"),vault.read()!!.automation.targets)
        vault.update(session){it.copy(automation=ListAutomation())}
        vault.link(session,Profile("100","renamed",2,1,System.currentTimeMillis()))
        assertEquals(ListAutomation(),vault.read()!!.automation)
    }
    @Test fun foregroundBuildsBothBaselinesWithoutNotificationPermissionAndKeepsCursorAcrossRestart(){
        val calls=mutableListOf<Pair<FollowDirection,String?>>()
        val notices=mutableListOf<ListDifference>()
        val firstDone=CountDownLatch(1)
        val source=object: FollowListSource {
            override fun read(s: Session,target: String,d: FollowDirection,cursor: String?): FollowPage {
                synchronized(calls){calls+=d to cursor}
                gate.begin()
                return if(d==FollowDirection.FOLLOWING)page(d,cursor,null,listOf("300"))
                    else if(cursor==null)page(d,null,"next",listOf("200")) else page(d,cursor,null,listOf("201"))
            }
            override fun cancel(){}
        }
        instrumentation.runOnMainSync {
            preparation=InitialListPreparation(context,vault,lists,gate,{source},{true},{
                if(lists.target(session,"100")?.followers?.continuing==true){preparation?.pause();firstDone.countDown()}
            });preparation!!.resume()
        }
        assertTrue(firstDone.await(8,TimeUnit.SECONDS))
        assertEquals("next",lists.read(session,"100")!!.followers!!.cursor())
        assertTrue(gate.preparationActive().not())
        prefs.edit().putLong("next_read_at",0).commit()
        instrumentation.runOnMainSync {
            preparation!!.close()
            preparation=InitialListPreparation(context,vault,lists,gate,{source},{true},{
                if(lists.read(session,"100")?.followers?.comparable()==true)prefs.edit().putLong("next_read_at",0).commit()
            },notify={_,difference->synchronized(notices){notices+=difference}});preparation!!.resume()
        }
        waitUntil{InitialLists.pending(lists.target(session,"100")).isEmpty()}
        assertEquals(listOf(FollowDirection.FOLLOWERS to null,FollowDirection.FOLLOWERS to "next",FollowDirection.FOLLOWING to null),synchronized(calls){calls.toList()})
        assertNull(ListAutomationPolicy.difference(lists.target(session,"100")!!,FollowDirection.FOLLOWERS))
        assertTrue(synchronized(notices){notices.isEmpty()})
    }
    @Test fun resumedLaterScanEmitsOneDifferenceAndPreservesInitialBaseline(){
        val now=System.currentTimeMillis()
        val followers=FollowDirection.FOLLOWERS
        lists.commitCurrent(session,page(followers,null,null,listOf("200","201")).copy(observedAt=now-2000),null,ListBasis(2,now-2000))
        lists.commitCurrent(session,page(FollowDirection.FOLLOWING,null,null,listOf("300")).copy(observedAt=now-2000),null,ListBasis(1,now-2000))
        val baseline=lists.read(session,"100")!!.followers!!
        lists.commitCurrent(session,page(followers,null,"next",listOf("200")).copy(observedAt=now-1000),baseline,ListBasis(2,now-1000))
        val delivered=CountDownLatch(1)
        val notices=mutableListOf<ListDifference>()
        val source=object: FollowListSource {
            override fun read(s: Session,target: String,d: FollowDirection,cursor: String?): FollowPage {
                assertEquals(followers,d);assertEquals("next",cursor);gate.begin()
                return page(d,cursor,null,listOf("202"))
            }
            override fun cancel(){}
        }
        instrumentation.runOnMainSync {
            preparation=InitialListPreparation(context,vault,lists,gate,{source},{true},{},notify={_,difference->
                synchronized(notices){notices+=difference};delivered.countDown()
            });preparation!!.resume()
        }
        assertTrue(delivered.await(8,TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        val difference=synchronized(notices){notices.single()}
        assertEquals(listOf("202"),difference.added.map{it.id})
        assertEquals(listOf("201"),difference.missing.map{it.id})
        assertEquals(baseline,lists.target(session,"100")!!.previous!!.followers)
    }
    @Test fun retainedRestrictionAndPausedPreparationNeverOpenAReader(){
        var calls=0
        instrumentation.runOnMainSync {
            preparation=InitialListPreparation(context,vault,lists,gate,{calls++;error("Unexpected reader")},{true},{})
            gate.limited(null);preparation!!.resume()
        }
        instrumentation.waitForIdleSync();assertEquals(0,calls)
        assertTrue(gate.blockedUntil()>System.currentTimeMillis())
    }
    @Test fun initialListsCanScheduleWithoutNotificationPermissionAndPauseStopsScheduling(){
        val saved=vault.read()!!
        try {
            assertTrue(MonitorScheduler.sync(context,saved,gate,PreviewMode.JOB_ID,DemoMonitorJobService::class.java))
            assertNotNull(context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID))
            gate.claimPreparation().use {
                assertTrue(MonitorScheduler.sync(context,saved,gate,PreviewMode.JOB_ID,DemoMonitorJobService::class.java))
                assertNotNull(context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID))
            }
            gate.pauseAutomatic()
            assertFalse(MonitorScheduler.sync(context,saved,gate,PreviewMode.JOB_ID,DemoMonitorJobService::class.java))
            assertNull(context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID))
        } finally { PreviewMode.cancel(context) }
    }
    @Test fun incompleteCountSurvivesCodecAndCannotProduceDifference(){
        val now=System.currentTimeMillis()
        var target=ListTarget("100",followers=ListScan(basisCount=2,basisAt=now))
        target=ListAutomationPolicy.record(target,session,page(FollowDirection.FOLLOWERS,null,null,listOf("200","201")))
        target=ListAutomationPolicy.record(target,session,page(FollowDirection.FOLLOWERS,null,null,listOf("201")))
        assertFalse(target.snapshot!!.followers!!.comparable())
        assertNull(ListAutomationPolicy.difference(target,FollowDirection.FOLLOWERS))
        val restored=FollowArchiveCodec.decode(FollowArchiveCodec.encode(ListArchive("100",session.connectionBinding,listOf(target))))
        assertEquals(2L,restored.targets.single().snapshot!!.followers!!.expectedCount)
        assertFalse(restored.targets.single().snapshot!!.followers!!.comparable())
    }
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun mainScreenShowsPausedPreparationAndNoHistoricalUnfollowClaim(){
        val cache=FollowListVault(context,"previewlists");cache.clear()
        val preview=SessionVault(context,"previewaccount");preview.write(PreviewMode.seed(true)!!)
        try {
            scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_preview",true))
            waitUntil {
                var found=false;scenario!!.onActivity { a->found=all(a.window.decorView).filterIsInstance<TextView>().any { it.text==a.getString(R.string.preparation_paused) } };found
            }
            scenario!!.onActivity { a->
                // The scope guidance now lives in usage notes; the paused card must not claim any past unfollow.
                assertTrue(all(a.window.decorView).filterIsInstance<TextView>().none { it.text.toString().contains("takipten çıktı") })
                assertTrue(all(a.window.decorView).filterIsInstance<Button>().any { it.text==a.getString(R.string.preparation_resume) })
            }
        } finally { scenario?.close();scenario=null;preview.clear();cache.clear() }
    }
    @Test fun connectedMainScreenStartsAndFinishesPreparationWithoutAStartButton(){
        val preview=SessionVault(context,"previewaccount")
        val cache=FollowListVault(context,"previewlists")
        val pacing=context.getSharedPreferences("preview_gate",0)
        val demo=PreviewMode.seed(true)!!
        cache.clear();pacing.edit().clear().putLong("next_read_at",System.currentTimeMillis()+10000).commit()
        preview.write(demo.copy(profile=demo.profile.copy(followers=3,following=2),
            automation=ListAutomation(true,setOf(demo.session.userId))))
        fun hasTitle(id: Int): Boolean {
            var result=false
            scenario!!.onActivity { a->result=all(a.window.decorView).filterIsInstance<TextView>().any { it.text==a.getString(id) } }
            return result
        }
        fun capture(name: String) {
            instrumentation.waitForIdleSync();SystemClock.sleep(150)
            instrumentation.uiAutomation.takeScreenshot()?.let { image->
                context.openFileOutput("preparation-$name.png",0).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle()
            }
        }
        try {
            scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_preview",true))
            waitUntil{hasTitle(R.string.preparation_title)};capture("queued")
            pacing.edit().putLong("next_read_at",0).commit()
            waitUntil{cache.read(demo.session,demo.session.userId)?.followers?.pages?.size==1};capture("progress")
            // Only this isolated demo gate is advanced; production pacing is unchanged.
            pacing.edit().putLong("next_read_at",0).commit()
            waitUntil{cache.read(demo.session,demo.session.userId)?.followers?.comparable()==true}
            pacing.edit().putLong("next_read_at",0).commit()
            waitUntil{hasTitle(R.string.preparation_ready)};capture("ready")
            assertEquals(3L,RequestGate(context,"preview_gate").attempts())
        } finally { scenario?.close();scenario=null;PreviewMode.cancel(context);preview.clear();cache.clear();pacing.edit().clear().commit() }
    }
}
