package dev.kaankaya.instagramtracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AvatarSource(val owner: String, val target: String, val url: String, val revision: String? = null) {
    val prefix = AvatarPolicy.prefix(owner, target)
    val key = prefix + AvatarPolicy.hash(revision?.takeIf { it.isNotBlank() }?.let { "picture:$it" } ?: "url:$url")
    // A failed signed URL must not suppress a fresh URL for the same photo.
    val requestKey get() = key + "-" + AvatarPolicy.hash(url)
}

object AvatarPolicy {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_SIDE = 256
    fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun prefix(owner: String, target: String? = null) = hash(owner) + "-" + (target?.let { hash(it) + "-" } ?: "")
    fun url(value: String): HttpUrl? = value.toHttpUrlOrNull()?.takeIf { url ->
        url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            listOf("cdninstagram.com", "fbcdn.net").any { url.host == it || url.host.endsWith(".$it") }
    }
}

interface AvatarDownload { fun read(): ByteArray?; fun cancel() }
fun interface AvatarFetcher : AutoCloseable {
    fun open(url: HttpUrl): AvatarDownload
    override fun close() = Unit
}

/** Public CDN images only. Never receives an Instagram session, cookie or account API client. */
class CdnAvatarFetcher : AvatarFetcher {
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).fastFallback(false)
        .followRedirects(false).followSslRedirects(false).connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            if (chain.request().tag(AtomicBoolean::class.java)?.compareAndSet(false, true) != true)
                throw java.io.IOException("Image replay disabled")
            chain.proceed(chain.request())
        }.build()
    override fun close() { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    override fun open(url: HttpUrl): AvatarDownload {
        require(AvatarPolicy.url(url.toString()) != null)
        val call = client.newCall(Request.Builder().url(url).tag(AtomicBoolean::class.java, AtomicBoolean()).build())
        return object : AvatarDownload {
            override fun cancel() = call.cancel()
            override fun read(): ByteArray? = call.execute().use { response ->
                if (response.code != 200 || response.body.contentLength() > AvatarPolicy.MAX_BYTES ||
                    response.header("Content-Type")?.startsWith("image/", true) != true) return null
                val source = response.body.source()
                source.request(AvatarPolicy.MAX_BYTES + 1L)
                if (source.buffer.size > AvatarPolicy.MAX_BYTES) null else source.readByteArray()
            }
        }
    }
}

/** Private, disposable thumbnails. Source URLs and account credentials are never written here. */
class AvatarCache(context: Context, slot: String = "avatars") {
    companion object {
        private val lock = Any()
        private val migrationFailures=mutableSetOf<String>()
        const val MAX_DISK_BYTES = 8L * 1024 * 1024
        const val MAX_FILES = 128
    }
    private val directory: File
    private val identity="${context.packageName}:$slot"
    val cleanupFailed get()=synchronized(lock){identity in migrationFailures}
    private val cipher = LocalCipher("instagramtracker.${slot}cache.v1", AvatarPolicy.MAX_BYTES)
    private val magic = "ITAV1".toByteArray(Charsets.US_ASCII)
    private fun aad(key: String) = "InstagramTracker/avatar/v1:$key".toByteArray()
    private var migrated=false
    init {
        require(slot.matches(Regex("[a-z]+"))); directory = File(context.cacheDir, slot)
    }
    private fun migrate() {
        if(migrated)return
        // Disposable PNGs from older versions must not remain on disk after migration.
        directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[a-f0-9-]+")) }.forEach { file ->
            val header = file.inputStream().use { input -> ByteArray(magic.size).also { bytes ->
                var offset=0
                while(offset<bytes.size) { val count=input.read(bytes,offset,bytes.size-offset);if(count<0)break;offset+=count }
            } }
            if (!header.contentEquals(magic)) check(file.delete())
        }
        migrated=true
    }
    fun migrateLegacy() = synchronized(lock) {
        try {migrate();migrationFailures.remove(identity)}
        catch (failure: Exception) {migrationFailures.add(identity);throw failure}
        Unit
    }
    fun read(key: String): ByteArray? = synchronized(lock) {
        require(key.matches(Regex("[a-f0-9-]+")))
        migrate()
        val file = File(directory, key)
        if (!file.isFile) return null
        if (file.length() !in (magic.size+29).toLong()..(AvatarPolicy.MAX_BYTES+28+magic.size).toLong()) { file.delete(); return null }
        try {
            val bytes = file.readBytes()
            require(bytes.copyOfRange(0,magic.size).contentEquals(magic))
            cipher.open(bytes.copyOfRange(magic.size,bytes.size),aad(key)).also { file.setLastModified(System.currentTimeMillis()) }
        } catch (_: Exception) { file.delete(); null }
    }
    fun write(key: String, bytes: ByteArray, valid: () -> Boolean) = synchronized(lock) {
        require(key.matches(Regex("[a-f0-9-]+")) && bytes.size <= AvatarPolicy.MAX_BYTES)
        if (!valid()) return@synchronized
        migrate()
        check(directory.isDirectory || directory.mkdirs())
        File(directory, key).writeBytes(magic + cipher.seal(bytes,aad(key)))
        val files = directory.listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.lastModified() }
        var total = 0L
        files.forEachIndexed { index, file ->
            total += file.length()
            if (index >= MAX_FILES || total > MAX_DISK_BYTES) file.delete()
        }
    }
    fun clear(owner: String? = null, target: String? = null) = synchronized(lock) {
        val prefix = owner?.let { AvatarPolicy.prefix(it, target) }
        directory.listFiles().orEmpty().filter { prefix == null || it.name.startsWith(prefix) }.forEach { check(it.delete()) }
        if(prefix==null)migrationFailures.remove(identity)
    }
}

/** Activity-owned image work: two downloads, bounded queue, reusable rows and cancellable visibility. */
class AvatarImages(context: Context, private val fetcher: AvatarFetcher = CdnAvatarFetcher(), slot: String = "avatars",
    private val transient: Boolean = false) : AutoCloseable {
    companion object {
        // TLS socket close can write close_notify. Lifecycle callers must never run it on the UI thread.
        private val cleanup = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "avatar-cleanup").apply { isDaemon = true }
        }
    }
    private val main = Handler(Looper.getMainLooper())
    private val executor = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(20))
    private val cache = if(transient)null else AvatarCache(context, slot)
    private val archiveContext=context.applicationContext
    private val previewArchive=slot.startsWith("preview")
    private val memory = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val shared = SharedImageWork<Bitmap>(executor, { main.post(it) }, { cleanup.execute(it) })
    private val failures = LruCache<String, Boolean>(128)
    @Volatile private var closed = false
    init { if(cache!=null)executor.execute {try {cache.migrateLegacy()}catch (_: Exception){ /* Reported in data settings; optional images stay optional. */ }} }
    fun retryCacheCleanup() {cache?.migrateLegacy()}
    inner class Job internal constructor(val source: AvatarSource, private val ticket: SharedImageWork<Bitmap>.Ticket) {
        fun cancel() = ticket.cancel()
        fun active() = ticket.active()
        internal fun await(timeout: Long, unit: TimeUnit) = ticket.await(timeout, unit)
    }
    private fun read(source: AvatarSource, work: SharedImageWork<Bitmap>.Context): Bitmap? {
        try {
            if (!work.active()) return null
            var bitmap = memory.get(source.key)
            if (bitmap == null) {
                bitmap = cache?.read(source.key)?.let(::decode)
                if (bitmap == null && work.active() && failures.get(source.requestKey) != true) {
                    val exchange = fetcher.open(AvatarPolicy.url(source.url) ?: return null)
                    work.onCancel { exchange.cancel() }
                    try {
                        if (!work.active()) return null
                        bitmap = exchange.read()?.let(::decode)
                    } finally { work.release() }
                    if (bitmap != null && work.active() && !transient) {
                        val output = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        cache?.write(source.key, output.toByteArray(), work::active)
                    }
                }
            }
            val image = bitmap ?: run { if (work.active()) failures.put(source.requestKey, true); return null }
            if (!work.active()) return null
            // The shared download owns one archive/cache write; views own only delivery.
            if(!transient)try {
                val archiveVault=SessionVault(archiveContext,if(previewArchive)"previewaccount" else "account",if(previewArchive)null else source.owner)
                val saved = if (archiveVault.visual.exists()) archiveVault.read()?.takeIf { it.session.userId == source.owner } else null
                val profile = saved?.let { if (it.profile.userId == source.target) it.profile else it.people.firstOrNull { p -> p.latest.userId == source.target }?.latest }
                if (profile?.details?.pictureUrl?.value == source.url && profile.details.pictureId.value == source.revision)
                    archiveVault.visual.avatar(source.owner, profile, image)
            } catch (_: Exception) { /* Optional archive cannot break avatar rendering. */ }
            if (!work.active()) return null
            memory.put(source.key, image)
            return image
        } catch (_: Exception) { if (work.active()) failures.put(source.requestKey, true); return null }
    }
    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.size > AvatarPolicy.MAX_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192 || bounds.outWidth.toLong() * bounds.outHeight > 16_777_216) return null
        var sample = 1
        while (bounds.outWidth / sample > AvatarPolicy.MAX_SIDE || bounds.outHeight / sample > AvatarPolicy.MAX_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    fun load(source: AvatarSource, deliver: (Bitmap) -> Unit): Job? {
        if (closed || AvatarPolicy.url(source.url) == null) return null
        val ticket = shared.subscribe(source.requestKey, { read(source, it) }, deliver) ?: return null
        return Job(source, ticket)
    }
    fun cancelAll() = shared.cancelAll()
    fun remove(owner: String? = null, target: String? = null) {
        val prefix = owner?.let { AvatarPolicy.prefix(it, target) }
        shared.cancelWhere { prefix == null || it.startsWith(prefix) }
        memory.snapshot().keys.filter { prefix == null || it.startsWith(prefix) }.forEach { memory.remove(it) }
        failures.snapshot().keys.filter { prefix == null || it.startsWith(prefix) }.forEach { failures.remove(it) }
        cache?.clear(owner, target)
    }
    override fun close() {
        if (closed) return
        closed = true; shared.close(); executor.shutdownNow(); main.removeCallbacksAndMessages(null); memory.evictAll()
        cleanup.execute { fetcher.close() }
    }
}

class ProfileAvatarView(context: Context, size: Int = 44) : FrameLayout(context) {
    private val initial = AppUi.avatar(context, "?", size)
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private var images: AvatarImages? = null
    private var source: AvatarSource? = null
    private var job: AvatarImages.Job? = null
    private var loaded = false
    private val scroll = android.view.ViewTreeObserver.OnScrollChangedListener { start() }
    init {
        layoutParams = android.widget.LinearLayout.LayoutParams(AppUi.dp(context, size), AppUi.dp(context, size))
        background = AppUi.shape(context, AppUi.violetSoft, size / 2); clipToOutline = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(initial, LayoutParams(-1, -1)); addView(image, LayoutParams(-1, -1))
    }
    fun bind(loader: AvatarImages, owner: String, id: String, username: String, url: String?, revision: String? = null,
        tint: Int = AppUi.violetSoft, foreground: Int = AppUi.violet) {
        job?.cancel(); job = null; loaded = false; image.setImageDrawable(null)
        initial.text = username.trimStart('@').take(1).uppercase(AppLanguage.locale(context))
        initial.setTextColor(foreground); initial.background = AppUi.shape(context, tint, 100)
        images = loader; source = url?.let { AvatarSource(owner, id, it, revision) }
        start()
    }
    private fun start() {
        val value = source ?: return
        if (!isAttachedToWindow || windowVisibility != VISIBLE || !isShown || !getGlobalVisibleRect(android.graphics.Rect())) {
            job?.cancel(); job = null; return
        }
        if (loaded || job?.active() == true) return
        job = images?.load(value) { bitmap ->
            if (source == value && isAttachedToWindow && windowVisibility == VISIBLE) { image.setImageBitmap(bitmap); loaded = true }
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); viewTreeObserver.addOnScrollChangedListener(scroll); start() }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom); start()
    }
    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnScrollChangedListener(scroll)
        job?.cancel(); job = null; super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) start() else { job?.cancel(); job = null }
    }
}
