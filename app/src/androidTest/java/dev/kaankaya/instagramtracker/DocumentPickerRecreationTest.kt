package dev.kaankaya.instagramtracker

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Recreate the real screens and supply the document result without using a device-specific picker UI. */
@SdkSuppress(minSdkVersion = 29)
class DocumentPickerRecreationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** Pickers carry a MIME type and the openable category, so the monitor must accept both to see them. */
    private fun picker(action: String) = IntentFilter(action).apply {
        addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*")
    }

    @Test fun backupAsksForPasswordAfterRecreationAndWritesTheSelectedDocument() {
        check(context.packageName.endsWith(".debug"))
        val owner = "999999990001"
        val catalog = AccountCatalog(context)
        val previous = catalog.active()
        val vault = SessionVault(context, owner = owner)
        val output = File.createTempFile("recreated-backup-", ".trackerhistory", context.cacheDir)
        val monitor = instrumentation.addMonitor(picker(Intent.ACTION_CREATE_DOCUMENT), null, true)
        val password = "synthetic-backup-password"
        try {
            vault.write(SavedAccount(Session(owner, "ds_user_id=$owner; sessionid=synthetic", "Synthetic UA"),
                Profile(owner, "picker_test", 1, 2, System.currentTimeMillis())))
            catalog.select(owner)
            ActivityScenario.launch(PortableBackupActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<Button>()
                        .single { it.text == activity.getString(R.string.portable_create) }.performClick()
                }
                assertEquals(1, monitor.hits)
                scenario.recreate()
                scenario.onActivity { deliver(it, 51, output) }
                scenario.onActivity {
                    val fields = WindowInspector.getGlobalWindowViews().flatMap(::views).filterIsInstance<EditText>()
                    assertEquals(2, fields.size)
                    fields.forEach { it.setText(password) }
                }
                scenario.recreate()
                scenario.onActivity {
                    val windows = WindowInspector.getGlobalWindowViews().flatMap(::views)
                    val fields = windows.filterIsInstance<EditText>()
                    assertEquals(2, fields.size)
                    assertTrue(fields.all { it.text.isEmpty() })
                    fields.forEach { it.setText(password) }
                    windows.filterIsInstance<Button>().single { it.id == android.R.id.button1 }.performClick()
                }
                awaitText(scenario, R.string.portable_saved)
                assertEquals(owner, output.inputStream().use { PortableBackup.open(it, password) }.getJSONObject("account").getString("id"))
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            output.delete()
            vault.clear()
            previous?.let(catalog::select)
        }
    }

    @Test fun secondZipResultWaitsForFirstZipToReopenAfterRecreation() {
        val first = zip("first_follower")
        val second = zip("second_follower")
        val monitor = instrumentation.addMonitor(picker(Intent.ACTION_OPEN_DOCUMENT), null, true)
        try {
            ActivityScenario.launch(ExportBrowserActivity::class.java).use { scenario ->
                scenario.onActivity { deliver(it, 21, first) }
                awaitText(scenario, R.string.export_browser_compare)
                lateinit var application: Application
                scenario.onActivity { activity ->
                    application = activity.application
                    views(activity.window.decorView).filterIsInstance<Button>()
                        .single { it.text == activity.getString(R.string.export_browser_compare) }.performClick()
                }
                assertEquals(1, monitor.hits)
                val callback = object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
                        if (activity is ExportBrowserActivity) deliver(activity, 23, second)
                    }
                    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                    override fun onActivityStarted(activity: Activity) = Unit
                    override fun onActivityResumed(activity: Activity) = Unit
                    override fun onActivityPaused(activity: Activity) = Unit
                    override fun onActivityStopped(activity: Activity) = Unit
                    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                    override fun onActivityDestroyed(activity: Activity) = Unit
                }
                application.registerActivityLifecycleCallbacks(callback)
                try { scenario.recreate() } finally { application.unregisterActivityLifecycleCallbacks(callback) }
                val deadline = SystemClock.uptimeMillis() + 10_000
                var confirmed = false
                while (!confirmed && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val button = WindowInspector.getGlobalWindowViews().flatMap(::views).filterIsInstance<Button>()
                            .firstOrNull { it.id == android.R.id.button1 && it.text == activity.getString(R.string.archive_compare_start) }
                        if (button != null) confirmed = button.performClick()
                    }
                    if (!confirmed) SystemClock.sleep(25)
                }
                assertTrue(confirmed)
                awaitText(scenario, R.string.export_browser_compare_second, 1)
                awaitText(scenario, R.string.export_browser_compare_first, 1)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            first.delete()
            second.delete()
        }
    }

    private fun <A : Activity> awaitText(scenario: ActivityScenario<A>, label: Int, vararg format: Any) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            var found = false
            scenario.onActivity { activity ->
                found = views(activity.window.decorView).filterIsInstance<TextView>()
                    .any { it.text == activity.getString(label, *format) }
            }
            if (found) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Missing text resource $label")
    }

    private fun deliver(activity: Activity, code: Int, file: File) {
        activity.javaClass.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Intent::class.java).apply { isAccessible = true }
            .invoke(activity, code, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
    }

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun zip(username: String): File = File.createTempFile("picker-test-", ".zip", context.cacheDir).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("connections/followers_and_following/followers_1.json"))
            zip.write("""[{"string_list_data":[{"value":"$username"}]}]""".toByteArray())
            zip.closeEntry()
        }
    }
}
