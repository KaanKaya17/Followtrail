package dev.kaankaya.instagramtracker

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Story CDN media lane: separate pacing/ledger, shared restriction. Real SharedPreferences, no network. */
class MediaLaneTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "medialanetestgate"
    private var now = 1_800_000_000_000L
    private fun gate() = RequestGate(context, name, { now }, { RequestDefaults.MIN_SPACING_MS }, { 2_000L })
    private val vault = SessionVault(context, "medialanetest")
    private val store = StoryArchiveStore(context, "100", "medialanetest")
    private val session = Session("100", "ds_user_id=100; sessionid=synthetic-media", "synthetic")
    private fun reset() { check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) }
    @Before fun setup() {
        check(context.packageName.endsWith(".debug")); reset(); vault.clear(); store.clearAll()
        vault.write(SavedAccount(session, Profile("100", "owner", 1, 1, now),
            listOf(TrackedPerson(listOf(Profile("200", "friend", 1, 1, now))))))
    }
    @After fun cleanup() { reset(); vault.clear(); store.clearAll() }

    @Test fun mediaDoesNotWaitForOrSpendTheApiReservation() {
        val gate = gate().forAccount("100")
        gate.begin(now)
        val apiNext = gate.nextAllowedAt()
        val apiBudget = gate.budgetNextAt(now)
        assertEquals(now + RequestDefaults.MIN_SPACING_MS, apiNext)
        assertTrue(gate.mediaNextAllowedAt(now) <= now)
        gate.beginMedia(now)
        assertEquals(1L, gate.attempts())
        assertEquals(1, gate.mediaReservations())
        assertEquals(apiNext, gate.nextAllowedAt())
        assertEquals(apiBudget, gate.budgetNextAt(now))
        assertThrows(ApiFailure::class.java) { gate.beginMedia(now) }
        now += 2_000L
        gate.beginMedia(now)
        assertEquals(2, gate.mediaReservations())
    }

    @Test fun apiRestrictionStopsMediaAndFullMediaLedgerStopsOnlyMedia() {
        val gate = gate().forAccount("100")
        gate.limited("60", now)
        val limited = assertThrows(ApiFailure::class.java) { gate.beginMedia(now) }
        assertEquals(ApiFailure.Kind.LIMITED, limited.kind)
        reset()
        check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("media_times",
            org.json.JSONArray(List(RequestDefaults.MEDIA_DEVICE_HOURLY) { now - 1 }).toString()).commit())
        val full = assertThrows(ApiFailure::class.java) { gate.beginMedia(now) }
        assertEquals(ApiFailure.Kind.BUDGET, full.kind)
        gate.begin(now)
        assertEquals(1L, gate.attempts())
    }

    @Test fun storyFetchSpendsOneApiSlotAndPacesMediaOnItsOwnLane() {
        val gate = gate()
        val items = (0 until 3).map { index ->
            StoryCandidate(StoryFacts("${300 + index}", "200", now, now + 86_400_000, StoryKind.IMAGE,
                emptyList(), null, null, null, null), "https://scontent.cdninstagram.com/s$index.png")
        }
        val source = object : StoryFeedSource {
            override fun read(session: Session, target: String): List<StoryCandidate> { gate.forAccount("100").begin(now); return items }
            override fun cancel() = Unit
        }
        val pixel = android.graphics.Bitmap.createBitmap(36, 64, android.graphics.Bitmap.Config.ARGB_8888)
        val png = java.io.ByteArrayOutputStream().use { pixel.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        val media = object : StoryMediaSource {
            override fun read(session: Session, url: String): ByteArray { gate.forAccount("100").beginMedia(now); return png }
            override fun cancel() = Unit
        }
        var paused = 0L
        val runtime = object : ReadRuntime {
            override fun now() = now
            override fun elapsed() = now
            override fun pause(milliseconds: Long, cancelled: () -> Boolean): Boolean { paused += milliseconds; now += milliseconds; return true }
        }
        val start = now
        val summary = StoryFetch(vault, store, gate, source, media, runtime).read(session, "200", { false }) { _, _ -> }
        assertEquals(3, summary.saved)
        assertEquals(1L, gate.attempts())
        assertEquals(3, gate.mediaReservations())
        // Two 2 s media gaps; never the 6 s API gap per file.
        assertEquals(4_000L, paused)
        assertEquals(start + 4_000L, now)
    }
}
