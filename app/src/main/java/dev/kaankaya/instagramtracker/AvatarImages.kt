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
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class AvatarSource(val owner: String, val target: String, val url: String, val revision: String? = null) {
    val prefix = AvatarPolicy.prefix(owner, target)
    val key = prefix + AvatarPolicy.hash(url + "\n" + revision.orEmpty())
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
        const val MAX_DISK_BYTES = 8L * 1024 * 1024
        const val MAX_FILES = 128
    }
    private val directory: File
    init { require(slot.matches(Regex("[a-z]+"))); directory = File(context.cacheDir, slot) }
    fun read(key: String): ByteArray? = synchronized(lock) {
        require(key.matches(Regex("[a-f0-9-]+")))
        val file = File(directory, key)
        if (!file.isFile) return null
        if (file.length() > AvatarPolicy.MAX_BYTES) { file.delete(); return null }
        file.readBytes().also { file.setLastModified(System.currentTimeMillis()) }
    }
    fun write(key: String, bytes: ByteArray, valid: () -> Boolean) = synchronized(lock) {
        require(key.matches(Regex("[a-f0-9-]+")) && bytes.size <= AvatarPolicy.MAX_BYTES)
        if (!valid()) return@synchronized
        check(directory.isDirectory || directory.mkdirs())
        File(directory, key).writeBytes(bytes)
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
    }
}

/** Activity-owned image work: two downloads, bounded queue, reusable rows and cancellable visibility. */
class AvatarImages(context: Context, private val fetcher: AvatarFetcher = CdnAvatarFetcher(), slot: String = "avatars") : AutoCloseable {
    companion object {
        // TLS socket close can write close_notify. Lifecycle callers must never run it on the UI thread.
        private val cleanup = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "avatar-cleanup").apply { isDaemon = true }
        }
    }
    private val main = Handler(Looper.getMainLooper())
    private val executor = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(20))
    private val cache = AvatarCache(context, slot)
    private val memory = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val jobs = mutableSetOf<Job>()
    private val failures = LruCache<String, Boolean>(128)
    @Volatile private var closed = false
    inner class Job internal constructor(val source: AvatarSource, private val deliver: (Bitmap) -> Unit) {
        private val cancelled = AtomicBoolean(false)
        @Volatile private var download: AvatarDownload? = null
        lateinit var future: FutureTask<Unit>
        fun active() = !cancelled.get() && !closed
        fun cancel() {
            cancelled.set(true); download?.let { exchange -> cleanup.execute { exchange.cancel() } }
            future.cancel(true); executor.remove(future)
            synchronized(jobs) { jobs.remove(this) }
        }
        fun run() {
            try {
                if (!active()) return
                var bitmap = memory.get(source.key)
                if (bitmap == null) {
                    bitmap = cache.read(source.key)?.let(::decode)
                    if (bitmap == null && active() && failures.get(source.key) != true) {
                        val exchange = fetcher.open(AvatarPolicy.url(source.url) ?: return)
                        download = exchange
                        if (!active()) { exchange.cancel(); return }
                        bitmap = exchange.read()?.let(::decode)
                        if (bitmap != null && active()) {
                            val output = java.io.ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            cache.write(source.key, output.toByteArray(), ::active)
                        }
                    }
                }
                val image = bitmap ?: run { if (active()) failures.put(source.key, true); return }
                if (active()) {
                    memory.put(source.key, image)
                    main.post { if (active()) deliver(image) }
                }
            } catch (_: Exception) { if (active()) failures.put(source.key, true) }
            finally { download = null; synchronized(jobs) { jobs.remove(this) } }
        }
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
        val job = Job(source, deliver)
        job.future = FutureTask { job.run(); Unit }
        synchronized(jobs) { jobs.add(job) }
        try { executor.execute(job.future) } catch (_: RejectedExecutionException) { job.cancel(); return null }
        return job
    }
    fun cancelAll() { synchronized(jobs) { jobs.toList() }.forEach { it.cancel() } }
    fun remove(owner: String? = null, target: String? = null) {
        synchronized(jobs) { jobs.filter { owner == null || (it.source.owner == owner && (target == null || it.source.target == target)) } }
            .forEach { it.cancel() }
        val prefix = owner?.let { AvatarPolicy.prefix(it, target) }
        memory.snapshot().keys.filter { prefix == null || it.startsWith(prefix) }.forEach { memory.remove(it) }
        failures.snapshot().keys.filter { prefix == null || it.startsWith(prefix) }.forEach { failures.remove(it) }
        cache.clear(owner, target)
    }
    override fun close() {
        if (closed) return
        closed = true; cancelAll(); executor.shutdownNow(); main.removeCallbacksAndMessages(null); memory.evictAll()
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
