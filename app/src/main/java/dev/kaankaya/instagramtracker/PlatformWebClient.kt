package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.NetworkException
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.ResponseBody
import okhttp3.MediaType
import okio.Buffer
import okio.Timeout
import okio.Source
import okio.buffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/** Process-owned platform engine, lazily started on the first web exchange, with no disk cache.
 * Entry is guarded by API 34 OR S SDK extension 7, including eligible Android 12/13 devices.
 * No external SDK/provider installer, telemetry exporter, redirect following or transport fallback.
 */
@SuppressLint("NewApi")
internal class PlatformWebClient(context: Context) : Call.Factory, AutoCloseable {
    private val callbacks=Executors.newSingleThreadExecutor { r->Thread(r,"tracker-web-callback").apply {isDaemon=true} }
    private val async=Executors.newSingleThreadExecutor { r->Thread(r,"tracker-web-async").apply {isDaemon=true} }
    private val running=AtomicReference<Exchange?>()
    @Volatile private var closing=false
    private val engineOwner=lazy {
        HttpEngine.Builder(context).setEnableQuic(false).setEnableHttp2(true).setEnableBrotli(false)
            .setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISABLED,0).build()
    }
    private val engine get()=engineOwner.value
    @Synchronized override fun close() {
        closing=true
        if(running.get()?.finished?.await(5,TimeUnit.SECONDS)==false) throw IOException("Platform cleanup pending")
        if(engineOwner.isInitialized()) engine.shutdown()
        callbacks.shutdown();async.shutdown()
    }
    override fun newCall(request: Request): Call = Exchange(request)

    private inner class Exchange(private val original: Request) : Call {
        private val executed=AtomicBoolean(false)
        private val cancelled=AtomicBoolean(false)
        private val limit=Timeout().timeout(30,TimeUnit.SECONDS)
        private val listeners=CopyOnWriteArrayList<EventListener>()
        private val tags=mutableMapOf<Class<*>,Any>()
        val finished=CountDownLatch(1)
        @Volatile private var active: UrlRequest?=null
        override fun request()=original
        override fun isExecuted()=executed.get()
        override fun isCanceled()=cancelled.get()
        override fun timeout()=limit
        override fun clone(): Call=Exchange(original)
        override fun addEventListener(eventListener: EventListener) {listeners.add(eventListener)}
        override fun <T:Any> tag(type: KClass<T>): T? = tag(type.java)
        @Suppress("UNCHECKED_CAST") // The private map is populated only through its typed compute method.
        override fun <T> tag(type: Class<out T>): T? = synchronized(tags) {tags[type] as T?}
        override fun <T:Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = tag(type.java,computeIfAbsent)
        override fun <T:Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = synchronized(tags) {
            requireNotNull(type.cast(tags.getOrPut(type) {computeIfAbsent()}))
        }
        override fun cancel() {
            if(cancelled.compareAndSet(false,true)) listeners.forEach {it.canceled(this)}
            active?.cancel()
        }
        override fun enqueue(responseCallback: Callback) {
            async.execute task@{
                val result=try { execute() } catch(error: IOException) {responseCallback.onFailure(this,error);return@task}
                responseCallback.onResponse(this,result)
            }
        }
        override fun execute(): Response {
            check(executed.compareAndSet(false,true)) {"Already executed"}
            listeners.forEach {it.callStart(this)}
            try {
                return exchange().also { response ->
                    if(response.body is FailedPlatformBody) listeners.forEach {it.callFailed(this,IOException("Platform response incomplete"))}
                    else listeners.forEach {it.callEnd(this)}
                }
            } catch(failure: IOException) {listeners.forEach {it.callFailed(this,failure)};throw failure}
            catch (_: Exception) {
                val failure=WebTransportFailure(WebTransportFailure.Reason.START)
                listeners.forEach {it.callFailed(this,failure)};throw failure
            }
        }
        private fun exchange(): Response {
            if (cancelled.get()) throw IOException("Cancelled")
            if (closing) throw IOException("Platform client closed")
            if (!WebPlatformRequestPolicy.allowed(original)) throw IOException("Web request policy rejected")
            val payload=original.body?.let {body->
                require(body.contentLength() in 0..WebGraphqlForm.MAX_BODY.toLong())
                Buffer().also {body.writeTo(it)}.readByteArray()
            }
            val max=if(payload==null)1024*1024 else 131072
            val bytes=ByteArrayOutputStream()
            val terminal=CountDownLatch(1)
            val completed=AtomicBoolean(false)
            val response=AtomicReference<Response?>()
            val observedInfo=AtomicReference<UrlResponseInfo?>()
            val error=AtomicReference<IOException?>()
            fun finish(value: Response?=null, failure: IOException?=null) {
                if (completed.compareAndSet(false,true)) {response.set(value);error.set(failure);terminal.countDown()}
            }
            fun response(info: UrlResponseInfo, body: ResponseBody): Response {
                val builder=Response.Builder().request(original).protocol(if(info.negotiatedProtocol=="h2")Protocol.HTTP_2 else Protocol.HTTP_1_1)
                    .code(info.httpStatusCode).message(info.httpStatusText)
                info.headers.asList.forEach { (name,value)->builder.addHeader(name,value) }
                return builder.body(body).build()
            }
            fun release() {active=null;running.compareAndSet(this@Exchange,null);finished.countDown()}
            val callback=object: UrlRequest.Callback {
                override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                    finish(response(info,byteArrayOf().toResponseBody()));request.cancel() // Never follow or expose a redirect URL.
                }
                override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                    if (completed.get() || cancelled.get()) {request.cancel();return}
                    observedInfo.set(info)
                    if(info.httpStatusCode==429) {finish(response(info,byteArrayOf().toResponseBody()));request.cancel();return}
                    if(info.negotiatedProtocol!="h2") {finish(response(info,FailedPlatformBody(IOException("HTTP/2 required"))));request.cancel();return}
                    if(original.url.toString()=="https://www.instagram.com/accounts/login/") {finish(response(info,byteArrayOf().toResponseBody()));request.cancel();return}
                    request.read(ByteBuffer.allocateDirect(8192))
                }
                override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                    if(completed.get() || cancelled.get()) {request.cancel();return}
                    byteBuffer.flip()
                    if(byteBuffer.remaining()>max-bytes.size()) {finish(response(info,FailedPlatformBody(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE))));request.cancel();return}
                    val part=ByteArray(byteBuffer.remaining());byteBuffer.get(part);bytes.write(part)
                    byteBuffer.clear();request.read(byteBuffer)
                }
                override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                    try {finish(response(info,bytes.toByteArray().toResponseBody()))}
                    catch (_: Exception) {finish(failure=IOException("Invalid platform response"))}
                    finally {release()}
                }
                override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, exception: HttpException) {
                    // Do not retain SDK exception text, which may include URLs or request context.
                    val failure=WebTransportFailure(WebTransportFailure.Reason.NETWORK,
                        (exception as? NetworkException)?.errorCode)
                    if(info!=null) finish(response(info,FailedPlatformBody(failure))) else finish(failure=failure)
                    release()
                }
                override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {finish(failure=IOException("Cancelled"));release()}
            }
            val builder=engine.newUrlRequestBuilder(original.url.toString(),callbacks,callback)
                .setHttpMethod(original.method).setCacheDisabled(true)
            original.headers.forEach { (name,value)->builder.addHeader(name,value) }
            if(payload!=null) {
                if(original.header("Content-Type")==null) builder.addHeader("Content-Type",original.body!!.contentType().toString())
                builder.setUploadDataProvider(OneShotWebUpload(payload),callbacks)
            }
            val call=builder.build();active=call
            if(closing || !running.compareAndSet(null,this)) {payload?.fill(0);active=null;throw IOException("Web exchange still finishing")}
            var started=false
            try {
                if(cancelled.get()) throw IOException("Cancelled")
                val began=System.nanoTime()
                val timeoutNanos=minOf(TimeUnit.SECONDS.toNanos(30),limit.timeoutNanos().takeIf {it>0}?:Long.MAX_VALUE)
                val deadline=if(limit.hasDeadline())minOf(began+timeoutNanos,limit.deadlineNanoTime())else began+timeoutNanos
                if(deadline<=began) throw InterruptedIOException("Platform web timeout")
                call.start();started=true
                val waitNanos=(deadline-System.nanoTime()).coerceAtLeast(0)
                if(!terminal.await(waitNanos,TimeUnit.NANOSECONDS)) {
                    completed.set(true);call.cancel()
                    val failure=WebTransportFailure(WebTransportFailure.Reason.TIMEOUT)
                    observedInfo.get()?.let {return response(it,FailedPlatformBody(failure))}
                    throw failure
                }
                if(cancelled.get()) throw IOException("Cancelled")
                error.get()?.let {throw it}
                return response.get() ?: throw IOException("Missing platform response")
            } catch(interrupted: InterruptedException) {
                completed.set(true);call.cancel();Thread.currentThread().interrupt();throw InterruptedIOException("Cancelled")
            } finally {if(!started) {payload?.fill(0);release()}}
        }
    }
}

/** Preserve received HTTP headers when the bounded body could not be delivered; invent no body. */
private class FailedPlatformBody(private val failure: Exception) : ResponseBody() {
    private val stream=object: Source {
        override fun read(sink: Buffer, byteCount: Long): Long = throw failure
        override fun timeout()=Timeout.NONE
        override fun close() {}
    }.buffer()
    override fun contentType(): MediaType?=null
    override fun contentLength()=-1L
    override fun source()=stream
}

/** A bounded immutable upload can be read once. Native retry cannot rewind its credentials. */
@SuppressLint("NewApi")
internal class OneShotWebUpload(private val bytes: ByteArray) : UploadDataProvider() {
    private var offset=0
    override fun getLength()=bytes.size.toLong()
    override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
        val count=minOf(buffer.remaining(),bytes.size-offset)
        buffer.put(bytes,offset,count);offset+=count;sink.onReadSucceeded(false)
    }
    override fun rewind(sink: UploadDataSink) {sink.onRewindError(IOException("Login upload replay disabled"))}
    override fun close() {bytes.fill(0)}
}
