package dev.kaankaya.instagramtracker

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VisualHistoryScreenTest {
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun encryptedGalleryAndDiffSurviveRecreationWithoutNetwork() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext;check(c.packageName.endsWith(".debug"))
        val vault=SessionVault(c)
        val session=Session("900","sessionid=synthetic; ds_user_id=900; csrftoken=synthetic","Synthetic QA")
        val p=Profile("900","qa.owner",2,1,1800000000000,details=ProfileDetails(biography=ProfileText.known("Istanbul books"),pictureId=ProfileText.known("one")))
        try {
            vault.clear();vault.write(SavedAccount(session,p));vault.visual.configure(vault.read()!!,"900",true)
            val red=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            vault.visual.avatar("900",p,red)
            val next=p.copy(observedAt=p.observedAt+1000,details=p.details.copy(biography=ProfileText.known("Ankara travel"),pictureId=ProfileText.known("two")))
            vault.write(SavedAccount(session,next));val blue=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) };vault.visual.avatar("900",next,blue)
            val encrypted=File(c.noBackupFilesDir,AccountCatalog(c).storage("900")+"visual.enc").readBytes();assertFalse(String(encrypted,Charsets.ISO_8859_1).contains("Istanbul"))
            val before=RequestGate(c).attempts()
            ActivityScenario.launch<VisualHistoryActivity>(Intent(c,VisualHistoryActivity::class.java).putExtra("viewer_id","900").putExtra("target_id","900")).use { scenario ->
                scenario.onActivity { a ->
                    val views=all(a.window.decorView);assertEquals(2,views.filterIsInstance<ImageView>().size)
                    assertTrue(views.filterIsInstance<TextView>().any { val text=it.text; text is Spanned && text.getSpans(0,text.length,StrikethroughSpan::class.java).isNotEmpty() })
                    views.filterIsInstance<TextView>().single { it.text.toString()==a.getString(R.string.visual_stop) }.performClick()
                    assertEquals(2,all(a.window.decorView).filterIsInstance<ImageView>().size)
                    all(a.window.decorView).filterIsInstance<TextView>().single { it.text.toString()==a.getString(R.string.visual_enable) }.performClick()
                }
                scenario.recreate();scenario.onActivity { a -> assertEquals(2,all(a.window.decorView).filterIsInstance<ImageView>().size) }
                val instrumentation=InstrumentationRegistry.getInstrumentation()
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.takeScreenshot().let { screenshot ->
                    File(c.getExternalFilesDir(null),"wayback-android.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }; screenshot.recycle()
                }
                scenario.onActivity { a -> all(a.window.decorView).filterIsInstance<ScrollView>().single().fullScroll(View.FOCUS_DOWN) }
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.waitForIdle(300,3000)
                instrumentation.uiAutomation.takeScreenshot().let { screenshot ->
                    File(c.getExternalFilesDir(null),"wayback-android-bio.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }; screenshot.recycle()
                }
            }
            assertEquals(before,RequestGate(c).attempts());assertEquals(2,vault.visual.read("900").getJSONObject("images").length())
            vault.visual.clear("900","900");assertEquals(0,vault.visual.read("900").getJSONObject("images").length())
        } finally { vault.clear() }
    }
}
