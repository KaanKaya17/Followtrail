package dev.kaankaya.instagramtracker

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class InstagramApi(
    private val gate: RequestGate,
    private val openConnection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) {
    private val mobile = MobileProfileReader(gate, MobileSessions(gate.context))
    private val webReader = WebReadTransport(gate, WebSessions(gate.context))
    private val lock = Any()
    private var generation = 0L
    private var active: HttpsURLConnection? = null
    fun cancel() {
        mobile.cancel()
        webReader.cancel()
        val connection = synchronized(lock) { generation++; active.also { active = null } }
        connection?.disconnect()
    }
    fun read(session: Session): Profile = readId(session, session.userId)
    fun testMobileConnection(session: Session): Profile {
        require(session.mobileBinding != null)
        return mobile.read(session, session.userId, null, connectionTest = true)
    }
    fun testWebConnection(session: Session): Profile {
        require(session.mobileBinding == null && session.userId.matches(Regex("[1-9][0-9]{0,29}")))
        if (session.webBinding != null) return webReader.read(session, connectionTest = true,
            request = { state, _ -> WebRequests.profile(state, session.userId) }) { body, time ->
            ProfileParser.fromBody(body, session.userId, time)
        }
        return request(session, "https://www.instagram.com/api/v1/users/${session.userId}/info/", connectionTest = true) {
            ProfileParser.parse(it, session.userId, gate.now())
        }
    }
    fun readId(session: Session, userId: String): Profile {
        require(userId.matches(Regex("[1-9][0-9]{0,29}")))
        if (session.mobileBinding != null) return mobile.read(session, userId, null)
        if (session.webBinding != null) return webReader.read(session,
            request = { state, _ -> WebRequests.profile(state, userId) }) { body, time -> ProfileParser.fromBody(body, userId, time) }
        return request(session, "https://www.instagram.com/api/v1/users/$userId/info/") {
            ProfileParser.parse(it, userId, gate.now())
        }
    }
    fun readUsername(session: Session, username: String): Profile {
        require(ProfileInput.validUsername(username))
        if (session.mobileBinding != null) return mobile.read(session, null, username)
        if (session.webBinding != null) return webReader.read(session,
            request = { state, _ -> WebRequests.username(state, username) }) { body, time -> ProfileParser.byUsername(body.toString(), username, time) }
        return request(session, "https://www.instagram.com/api/v1/users/web_profile_info/?username=$username") {
            ProfileParser.byUsername(it, username, gate.now())
        }
    }
    private fun request(session: Session, address: String, connectionTest: Boolean = false, parse: (String) -> Profile): Profile {
        val ticket = synchronized(lock) { generation }
        fun ensureCurrent() {
            if (Thread.currentThread().isInterrupted || synchronized(lock) { ticket != generation }) {
                throw ApiFailure(ApiFailure.Kind.CANCELLED)
            }
        }
        var ownedConnection: HttpsURLConnection? = null
        var lease: AutoCloseable? = null
        try {
            ensureCurrent()
            lease = gate.claim()
            if (connectionTest) gate.beginPrototype() else gate.begin()
            val connection = openConnection(URL(address))
            ownedConnection = connection
            synchronized(lock) {
                ensureCurrent()
                active = connection
            }
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.useCaches = false
            connection.setRequestProperty("Cookie", session.cookie)
            connection.setRequestProperty("User-Agent", session.userAgent)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-IG-App-ID", "936619743392459")
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.setRequestProperty("Referer", "https://www.instagram.com/")
            SessionCookies.csrf(session)?.let { connection.setRequestProperty("X-CSRFToken", it) }
            ensureCurrent()
            val status = connection.responseCode
            ensureCurrent()
            gate.observedStatus(status)
            InstagramResponsePolicy.checkHeaders(gate, status, connection.getHeaderField("Retry-After"))
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val data = input?.use { it.readNBytesBounded(1024 * 1024) } ?: byteArrayOf()
            val text = String(data, Charsets.UTF_8)
            val json = try { JSONObject(text) } catch (_: Exception) { null }
            InstagramResponsePolicy.checkBody(gate, status, json, connection.getHeaderField("Retry-After"))
            val profile = parse(text)
            ensureCurrent()
            if (!connectionTest) gate.succeeded()
            return profile
        } catch (failure: ApiFailure) {
            if (failure.kind in setOf(ApiFailure.Kind.SESSION_EXPIRED, ApiFailure.Kind.VERIFICATION)) gate.pauseForConnection()
            throw failure
        }
        catch (_: java.io.IOException) { ensureCurrent(); throw ApiFailure(ApiFailure.Kind.CONNECTION) }
        catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE) }
        finally {
            synchronized(lock) { if (active === ownedConnection) active = null }
            try { ownedConnection?.disconnect() } finally { lease?.close() }
        }
    }
    private fun java.io.InputStream.readNBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() > limit - count) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            output.write(buffer, 0, count)
        }
    }
}
