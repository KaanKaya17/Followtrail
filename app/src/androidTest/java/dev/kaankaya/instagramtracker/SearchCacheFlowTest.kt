package dev.kaankaya.instagramtracker

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Actual native dialog lifecycle with a synthetic search callback; never sends Instagram requests. */
class SearchCacheFlowTest {
    @Test fun closingAndReopeningReusesResultsUntilExpiryAndDifferentAccountsDoNotShareThem() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        check(context.packageName.endsWith(".debug"))
        val now=AtomicLong();val calls=AtomicInteger();val cache=SearchResultsCache(now::get)
        val first=Session("100","synthetic","test");var dialog: ProfileSearchDialog?=null
        val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("offline_preview",true))
        fun close() {scenario.onActivity {dialog?.close();dialog=null}}
        fun open(session: Session) {
            scenario.onActivity {activity->
                dialog=ProfileSearchDialog(activity,emptyList(),
                    search={calls.incrementAndGet();listOf(ProfileCandidate("200","qa.result","QA Result"))},
                    cancel={},select={},direct={},session=session,cache=cache).also {it.show()}
            }
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if(node==null)return null
                if(node.className?.toString()=="android.widget.EditText")return node
                for(i in 0 until node.childCount)find(node.getChild(i))?.let {return it}
                return null
            }
            val deadline=SystemClock.uptimeMillis()+5000
            var input: AccessibilityNodeInfo?=null
            while(input==null && SystemClock.uptimeMillis()<deadline) {
                input=find(instrumentation.uiAutomation.rootInActiveWindow)
                if(input==null)SystemClock.sleep(25)
            }
            assertNotNull("Search dialog is not visible",input)
            assertTrue(input!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"qa")
            }))
        }
        fun waitCalls(count: Int) {
            val end=SystemClock.uptimeMillis()+5000
            fun visible()=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("QA Result")?.isNotEmpty()==true
            while((calls.get()<count || !visible()) && SystemClock.uptimeMillis()<end)SystemClock.sleep(25)
            assertEquals(count,calls.get());instrumentation.waitForIdleSync()
            assertTrue("Search result was not rendered",visible())
        }
        try {
            open(first);waitCalls(1);close()
            open(first);waitCalls(1);SystemClock.sleep(800);assertEquals(1,calls.get());close()
            now.set(60_000);open(first);waitCalls(2);close()
            open(first.copy(userId="300"));waitCalls(3);close()
        } finally {
            close();scenario.close();cache.clear();PreviewMode.cancel(context)
            SessionVault(context,"previewaccount").clear();FollowListVault(context,"previewlists").clear()
            AvatarCache(context,"previewavatars").clear()
        }
    }
}
