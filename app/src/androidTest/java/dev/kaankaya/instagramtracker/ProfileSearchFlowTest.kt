package dev.kaankaya.instagramtracker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ProfileSearchFlowTest {
    @Test fun partialNamesAtNamesAndLinksFilterSavedProfilesWithoutReplacingInputOrSendingRequests() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".debug"))
        val language=AppLanguage.selected(context)
        val vault=SessionVault(context,"previewaccount")
        fun views(view:View):List<View> = listOf(view) + if(view is ViewGroup)(0 until view.childCount).flatMap {views(view.getChildAt(it))} else emptyList()
        instrumentation.runOnMainSync {AppLanguage.set(context,"tr")}
        val now=System.currentTimeMillis()
        val people=listOf(
            TrackedPerson(listOf(Profile("200","ipek.kaya",3,2,now,name="İpek Çağrı Kaya")),group="Yakın Arkadaşlar"),
            TrackedPerson(listOf(Profile("201","deniz.ada",5,2,now,name="Deniz Ada"))))
        vault.write(PreviewMode.seed(true)!!.copy(automation=ListAutomation(),people=people))
        context.getSharedPreferences("preview_gate",0).edit().clear().commit()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("offline_preview",true)).use {scenario->
                fun await(test:(List<View>)->Boolean) {
                    val end=android.os.SystemClock.elapsedRealtime()+7000
                    while(android.os.SystemClock.elapsedRealtime()<end) {
                        var ready=false;scenario.onActivity {ready=test(views(it.window.decorView))}
                        if(ready)return;Thread.sleep(30)
                    }
                    fail("Saved profile search did not reach its expected offline state")
                }
                await {all->all.filterIsInstance<TextView>().any {it.text==context.getString(R.string.ui_people)}}
                scenario.onActivity {activity->views(activity.window.decorView).filterIsInstance<TextView>().single {it.text==context.getString(R.string.ui_people)}.performClick()}
                await {all->all.count {it.tag?.toString()?.startsWith("profile-status:")==true}==2}
                for(query in listOf("ipe ka","@ipek","yakin","https://instagram.com/ipek.kaya/?igsh=abc")) {
                    var field:EditText?=null
                    scenario.onActivity {activity->field=views(activity.window.decorView).filterIsInstance<EditText>().single {it.tag=="saved-profile-search"};field!!.requestFocus();field!!.setText(query)}
                    await {all->all.count {it.tag?.toString()?.startsWith("profile-status:")==true}==1 && all.any {it.tag=="profile-status:200"}}
                    scenario.onActivity {activity->assertSame(field,views(activity.window.decorView).single {it.tag=="saved-profile-search"});assertTrue(field!!.hasFocus())}
                }
                assertEquals(0L,RequestGate(context,"preview_gate").attempts())
                assertEquals(2,vault.read()!!.people.size)
                scenario.onActivity {activity->views(activity.window.decorView).filterIsInstance<TextView>().single {it.text==context.getString(R.string.ui_add_person)}.performClick()}
                instrumentation.waitForIdleSync();Thread.sleep(300)
                instrumentation.uiAutomation.takeScreenshot()?.let {bitmap->
                    java.io.File(context.cacheDir,"profile-search-dialog.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                    bitmap.recycle()
                }
            }
        } finally {
            PreviewMode.cancel(context);vault.clear()
            instrumentation.runOnMainSync {AppLanguage.set(context,language)}
        }
    }
}
