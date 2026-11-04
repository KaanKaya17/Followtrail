package dev.kaankaya.instagramtracker

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real bitmap decoding and private disk cache. Every image exchange is synthetic. */
class AvatarImagesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cache = AvatarCache(context, "avatarstest")
    private val loaders = mutableListOf<AvatarImages>()
    private val source = AvatarSource("100", "200", "https://cdninstagram.com/photo.png", "one")
    @Before fun setup() { check(context.packageName.endsWith(".debug")); cache.clear() }
    @After fun cleanup() {
        loaders.forEach { it.close() }; cache.clear(); PreviewMode.cancel(context)
        SessionVault(context, "previewaccount").clear(); FollowListVault(context, "previewlists").clear()
        AvatarCache(context, "previewavatars").clear(); context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
    }
    private fun png(color: Int, size: Int = 80): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); bitmap.eraseColor(color)
        return java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
    private fun loader(fetcher: AvatarFetcher) = AvatarImages(context, fetcher, "avatarstest").also { loaders += it }
    private fun result(images: AvatarImages, source: AvatarSource): Bitmap {
        val done = CountDownLatch(1); var result: Bitmap? = null
        assertNotNull(images.load(source) { result = it; done.countDown() })
        assertTrue("No image delivered", done.await(5, TimeUnit.SECONDS)); return result!!
    }
    private fun fetcher(bytes: ByteArray, count: AtomicInteger) = AvatarFetcher { object : AvatarDownload {
        override fun cancel() = Unit
        override fun read(): ByteArray { count.incrementAndGet(); return bytes }
    } }
    private fun drain() {
        val idle = CountDownLatch(1); Handler(Looper.getMainLooper()).post { idle.countDown() }; assertTrue(idle.await(5, TimeUnit.SECONDS))
    }
    @Test fun diskCacheSurvivesLoaderRecreationAndPhotoRevisionAndOwnerStaySeparate() {
        val calls = AtomicInteger(); val first = loader(fetcher(png(Color.RED, 1024), calls))
        val image = result(first, source); assertTrue(image.width <= AvatarPolicy.MAX_SIDE); assertEquals(Color.RED, image.getPixel(10, 10))
        result(first, source.copy(url="https://cdninstagram.com/rotated.png?signature=next")); assertEquals(1, calls.get()); first.close()
        val second = loader(fetcher(png(Color.BLUE), calls))
        assertEquals(Color.RED, result(second, source).getPixel(10, 10)); assertEquals(1, calls.get())
        assertEquals(Color.BLUE, result(second, source.copy(revision = "two")).getPixel(10, 10))
        result(second, source.copy(owner = "300")); assertEquals(3, calls.get())
        second.remove("100", "200"); second.close()
        val third = loader(fetcher(png(Color.GREEN), calls))
        result(third, source.copy(owner = "300")); assertEquals(3, calls.get())
        result(third, source); assertEquals(4, calls.get())
    }
    @Test fun aFreshSignedUrlCanRecoverAfterAnExpiredUrlForTheSamePhoto() {
        val calls=AtomicInteger();val bytes=png(Color.GREEN)
        val images=loader(AvatarFetcher { url -> object : AvatarDownload {
            override fun cancel()=Unit
            override fun read(): ByteArray? {calls.incrementAndGet();return if(url.queryParameter("valid")=="yes")bytes else null}
        } })
        images.load(source){fail("Expired URL")}!!.future.get(5,TimeUnit.SECONDS);drain()
        images.load(source){fail("Repeated expired URL")}!!.future.get(5,TimeUnit.SECONDS);drain();assertEquals(1,calls.get())
        assertEquals(Color.GREEN,result(images,source.copy(url="https://cdninstagram.com/photo.png?valid=yes")).getPixel(10,10));assertEquals(2,calls.get())
    }
    @Test fun aCancelledLateDownloadCannotDeliverOrRecreateDeletedCache() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val cancelled = CountDownLatch(1)
        val calls = AtomicInteger(); val delivered = AtomicInteger()
        val images = loader(AvatarFetcher { object : AvatarDownload {
            override fun cancel() { cancelled.countDown(); release.countDown() }
            override fun read(): ByteArray { entered.countDown(); try { release.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }; return png(Color.RED) }
        } })
        images.load(source) { delivered.incrementAndGet() }
        assertTrue(entered.await(5, TimeUnit.SECONDS)); images.remove("100", "200")
        assertTrue(cancelled.await(5, TimeUnit.SECONDS)); images.close(); drain()
        assertEquals(0, delivered.get()); assertNull(cache.read(source.key))
        val next = loader(fetcher(png(Color.BLUE), calls)); result(next, source); assertEquals(1, calls.get())
    }
    @Test fun missingMalformedOversizeAndUntrustedImagesStayOptionalWithoutRetryLoops() {
        val calls = AtomicInteger(); val images = loader(fetcher(byteArrayOf(1, 2, 3), calls))
        assertNull(images.load(source.copy(url = "https://instagram.com/api/")) { fail("Untrusted image") })
        val first = images.load(source) { fail("Malformed image") }!!; first.future.get(5, TimeUnit.SECONDS); drain()
        images.load(source) { fail("Malformed cached image") }!!.future.get(5, TimeUnit.SECONDS); drain(); assertEquals(1, calls.get())
        val oversize = loader(fetcher(ByteArray(AvatarPolicy.MAX_BYTES + 1), calls))
        oversize.load(source) { fail("Oversize image") }!!.future.get(5, TimeUnit.SECONDS); drain()
        assertNull(cache.read(source.key))
    }
    @Test fun diskBudgetAndTargetRemovalPreserveOtherCachedPeople() {
        val bytes = ByteArray(AvatarPolicy.MAX_BYTES)
        repeat(10) { cache.write(source.copy(target = (200 + it).toString()).key, bytes) { true } }
        val files = context.cacheDir.resolve("avatarstest").listFiles().orEmpty()
        assertTrue(files.sumOf { it.length() } <= AvatarCache.MAX_DISK_BYTES)
        assertTrue(files.size <= AvatarCache.MAX_FILES)
        val retained = source.copy(owner = "300"); cache.write(retained.key, bytes) { true }
        cache.clear("100"); assertNotNull(cache.read(retained.key)); assertNull(cache.read(source.key))
    }
    @Test fun offscreenAvatarsWaitUntilTheyBecomeVisible() {
        val calls = AtomicInteger(); val entered = CountDownLatch(1)
        val images = loader(AvatarFetcher { object : AvatarDownload {
            override fun cancel() = Unit
            override fun read(): ByteArray { calls.incrementAndGet(); entered.countDown(); return png(Color.BLUE) }
        } })
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = androidx.test.core.app.ActivityScenario.launch<MainActivity>(android.content.Intent(context, MainActivity::class.java)
            .putExtra("offline_preview", true))
        try {
            lateinit var scroll: android.widget.ScrollView
            scenario.onActivity { activity ->
                val column = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
                column.addView(android.view.View(activity), android.widget.LinearLayout.LayoutParams(1, AppUi.dp(activity, 600)))
                val avatar = ProfileAvatarView(activity, 48); column.addView(avatar)
                column.addView(android.view.View(activity), android.widget.LinearLayout.LayoutParams(1, AppUi.dp(activity, 400)))
                scroll = android.widget.ScrollView(activity).apply { addView(column) }
                (activity.findViewById<android.view.View>(android.R.id.content) as android.view.ViewGroup).addView(scroll,
                    android.widget.FrameLayout.LayoutParams(-1, AppUi.dp(activity, 200)))
                avatar.bind(images, "100", "200", "offscreen", source.url)
            }
            instrumentation.waitForIdleSync(); assertEquals(0, calls.get())
            scenario.onActivity { scroll.scrollTo(0, AppUi.dp(context, 600)) }
            assertTrue("Visible avatar did not start loading", entered.await(5, TimeUnit.SECONDS)); assertEquals(1, calls.get())
        } finally { scenario.close() }
    }
    @Test fun lifecycleCancellationAndFetcherCleanupNeverRunOnTheUiThread() {
        val entered = CountDownLatch(1); val cancelled = CountDownLatch(1); val closed = CountDownLatch(1)
        val onUi = java.util.concurrent.atomic.AtomicBoolean(false)
        val images = loader(object : AvatarFetcher {
            override fun open(url: okhttp3.HttpUrl) = object : AvatarDownload {
                override fun read(): ByteArray? { entered.countDown(); try { cancelled.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }; return null }
                override fun cancel() { if (Looper.myLooper() == Looper.getMainLooper()) onUi.set(true); cancelled.countDown() }
            }
            override fun close() { if (Looper.myLooper() == Looper.getMainLooper()) onUi.set(true); closed.countDown() }
        })
        images.load(source) { fail("Cancelled image") }; assertTrue(entered.await(5, TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { images.close(); images.close() }
        assertTrue(cancelled.await(5, TimeUnit.SECONDS)); assertTrue(closed.await(5, TimeUnit.SECONDS)); assertFalse(onUi.get())
    }
    @Test fun aReboundAvatarCannotShowThePreviousPersonsLateImage() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val images = loader(AvatarFetcher { url -> object : AvatarDownload {
            override fun cancel() { release.countDown() }
            override fun read(): ByteArray {
                if (url.encodedPath == "/photo.png") { entered.countDown(); try { release.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }; return png(Color.RED) }
                return png(Color.BLUE)
            }
        } })
        val scenario = androidx.test.core.app.ActivityScenario.launch<MainActivity>(android.content.Intent(context, MainActivity::class.java)
            .putExtra("offline_preview", true))
        try {
            lateinit var avatar: ProfileAvatarView
            scenario.onActivity { activity ->
                avatar = ProfileAvatarView(activity, 48)
                (activity.findViewById<android.view.View>(android.R.id.content) as android.view.ViewGroup).addView(avatar)
                avatar.bind(images, "100", "200", "old", source.url)
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            scenario.onActivity { avatar.bind(images, "100", "300", "new", "https://cdninstagram.com/new.png") }
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            var color = Color.TRANSPARENT
            while (android.os.SystemClock.elapsedRealtime() < deadline && color != Color.BLUE) {
                scenario.onActivity {
                    val drawable = (avatar.getChildAt(1) as android.widget.ImageView).drawable as? android.graphics.drawable.BitmapDrawable
                    color = drawable?.bitmap?.getPixel(10, 10) ?: Color.TRANSPARENT
                }
                if (color != Color.BLUE) Thread.sleep(20)
            }
            assertEquals(Color.BLUE, color); drain(); assertNull(cache.read(source.key))
        } finally { release.countDown(); scenario.close(); PreviewMode.cancel(context) }
    }
}
