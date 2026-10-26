package dev.kaankaya.instagramtracker

import android.content.Context
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64
import java.util.UUID

enum class PhoneLoginStep { PREPARING, SIGNING_IN, VERIFYING, PROFILE }
enum class PhoneLoginOperation(val code: String) {
    DEVICE("P01"), CONTEXT("P02"), CHALLENGE("P03"), OAUTH("P04"), PASSWORD_KEY("P05"),
    PASSWORD("P06"), LEGACY("P07"), VERIFICATION("P08"), PROFILE("P09")
}
data class PhoneLoginDiagnostic(val operation: PhoneLoginOperation, val status: Int? = null)
sealed interface PhoneLoginResult {
    data object Method : PhoneLoginResult
    data object Code : PhoneLoginResult
    class Ready(val session: MobileSession, val profile: Profile) : PhoneLoginResult
}

/** Bounded, user-started login. Pinned MIT instagrapi 3.2.0 request contracts, no feed/contact sync or retry. */
class PhoneLoginClient(private val context: Context, private val gate: RequestGate,
    private val http: Call.Factory = MobileReadTransport.client(), private val runtime: ReadRuntime = AndroidReadRuntime,
    private val identitySlot: String = "phoneidentity", private val progress: (PhoneLoginStep, Long) -> Unit = { _, _ -> }) {
    private val templates = JSONObject(context.assets.open("phone_login_contract.json").bufferedReader().use { it.readText() })
    private val journal = PhoneLoginJournal(context)
    private val lock = Any()
    @Volatile private var cancelled = false
    private var active: Call? = null
    private lateinit var device: PhoneIdentity
    private var expires = 0L
    private var first = true
    private var calls = 0
    private var username = ""
    private var aac = ""
    private var challenge = ""
    private var kind = ""
    private var method = "totp"
    private val waterfall = UUID.randomUUID().toString()
    private val inputId = UUID.randomUUID().toString().take(4) + "ig"
    private val cookies = mutableMapOf<String, String>()
    private val rotated = mutableMapOf<String, String>()
    private var step = PhoneLoginStep.PREPARING
    @Volatile var diagnostic: PhoneLoginDiagnostic? = null
        private set
    fun cancel() { cancelled = true; synchronized(lock) { active?.cancel(); active = null } }
    private fun current() {
        if (cancelled || Thread.currentThread().isInterrupted) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        if (expires > 0 && runtime.elapsed() >= expires) throw PhoneLoginFailure(PhoneLoginFailure.Reason.EXPIRED)
    }
    private fun slot() {
        current()
        if (gate.blockedUntil() > runtime.now()) throw ApiFailure(ApiFailure.Kind.LIMITED)
        if (first && gate.nextPrototypeAt() > runtime.now()) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        var lastSecond = -1L
        while (gate.nextAllowedAt() > runtime.now()) {
            current()
            if (gate.blockedUntil() > runtime.now()) throw ApiFailure(ApiFailure.Kind.LIMITED)
            val second = (gate.nextAllowedAt() - runtime.now() + 999) / 1000
            if (lastSecond != second) { progress(step, second); lastSecond = second }
            if (!runtime.pause(minOf(250, gate.nextAllowedAt() - runtime.now()), { cancelled })) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        }
        current(); progress(step, 0)
    }
    private class Reply(val body: JSONObject, val headers: Map<String, List<String>>)
    private fun exchange(request: Request, operation: PhoneLoginOperation): Reply {
        require(request.url.isHttps && request.url.host in setOf("i.instagram.com", "b.i.instagram.com"))
        slot(); gate.claim().use {
            current()
            if (calls >= 18) throw PhoneLoginFailure(PhoneLoginFailure.Reason.EXPIRED)
            if (first) { gate.beginPrototype(); first = false } else gate.begin()
            calls++
            diagnostic = PhoneLoginDiagnostic(operation)
            val attemptedAt = runtime.now()
            journal.write(SavedPhoneDiagnostic(operation, attemptedAt, null))
            val call = http.newCall(request)
            synchronized(lock) { current(); active = call }
            try {
                call.execute().use { response ->
                    current(); gate.observedStatus(response.code)
                    diagnostic = PhoneLoginDiagnostic(operation, response.code)
                    val savedDiagnostic = runCatching { journal.write(SavedPhoneDiagnostic(operation, attemptedAt, response.code)) }
                    if (response.code == 429 || response.code in 300..399)
                        InstagramResponsePolicy.checkHeaders(gate, response.code, response.header("Retry-After"))
                    val body = response.body ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                    if (body.contentLength() > 1024 * 1024) throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
                    val source = body.source(); source.request(1024 * 1024 + 1L)
                    if (source.buffer.size > 1024 * 1024) throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
                    // The password-key endpoint can return HTTP 405 with its usable key in headers.
                    // This is a header lookup, not a successful login or a reason to replay the request.
                    val keyReply = operation == PhoneLoginOperation.PASSWORD_KEY &&
                        (response.code in 200..299 || response.code == 405) &&
                        response.header("ig-set-password-encryption-key-id")?.toIntOrNull()?.let { it in 0..255 } == true &&
                        !response.header("ig-set-password-encryption-pub-key").isNullOrBlank()
                    val json = try { JSONObject(source.readUtf8()) } catch (_: Exception) {
                        if (keyReply) JSONObject() else {
                            InstagramResponsePolicy.checkBody(gate, response.code, null, response.header("Retry-After"))
                            throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
                        }
                    }
                    val issue = InstagramResponsePolicy.issue(response.code, json)
                    if (issue?.restriction != null || issue?.kind == ApiFailure.Kind.VERIFICATION)
                        InstagramResponsePolicy.checkBody(gate, response.code, json, response.header("Retry-After"))
                    if (json.optString("error_type") in setOf("bad_password", "invalid_user", "invalid_credentials"))
                        throw PhoneLoginFailure(PhoneLoginFailure.Reason.CREDENTIALS)
                    if (response.code !in 200..299 && !json.optBoolean("two_factor_required") && !keyReply) {
                        if (step == PhoneLoginStep.VERIFYING && response.code == 400) throw PhoneLoginFailure(PhoneLoginFailure.Reason.CODE)
                        InstagramResponsePolicy.checkBody(gate, response.code, json, response.header("Retry-After"))
                    }
                    // Apply server restrictions before surfacing a diagnostic storage failure.
                    savedDiagnostic.getOrThrow()
                    response.headers.values("Set-Cookie").forEach { line ->
                        okhttp3.Cookie.parse(response.request.url, line)?.let { cookie ->
                            if (cookie.name in MobileSessionCodec.cookieNames) {
                                if (cookie.expiresAt <= runtime.now()) cookies.remove(cookie.name) else cookies[cookie.name] = MobileSessionCodec.cookie(cookie.value)
                            }
                        }
                    }
                    mapOf("ig-set-x-mid" to "X-MID", "x-ig-set-www-claim" to "X-IG-WWW-Claim").forEach { (wire, name) ->
                        response.header(wire)?.let { rotated[name] = MobileSessionCodec.header(it) }
                    }
                    return Reply(json, response.headers.toMultimap())
                }
            } catch (_: java.io.IOException) { current(); throw ApiFailure(ApiFailure.Kind.CONNECTION) }
            finally { synchronized(lock) { if (active === call) active = null } }
        }
    }
    private fun request(address: String, data: Map<String, String>? = null, extra: Map<String, String> = emptyMap()): Request {
        val builder = Request.Builder().url(address).header("Accept", "application/json")
        (device.headers() + rotated + mapOf("X-Meta-Usdid" to device.signedDevice(runtime.now()),
            "X-Pigeon-Session-Id" to "UFS-${device.uuid}-1", "X-Pigeon-Rawclienttime" to (runtime.now() / 1000.0).toString())).forEach { (name, value) -> builder.header(name, value) }
        if (cookies.isNotEmpty()) builder.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        extra.forEach { (name, value) -> builder.header(name, value) }
        return if (data == null) builder.get().build() else builder.post(FormBody.Builder().apply { data.forEach { (k, v) -> add(k, v) } }.build()).build()
    }
    private fun template(name: String, values: Map<String, Any> = emptyMap()): JSONObject {
        val base = mapOf("UUID" to device.uuid, "PHONE_ID" to device.phoneId, "ANDROID_ID" to device.androidId,
            "WATERFALL" to waterfall, "AAC" to aac, "MID" to (rotated["X-MID"] ?: cookies["mid"].orEmpty()),
            "USERNAME" to username, "EMPTY" to "", "NOW" to runtime.now(), "USERNAME_INPUT" to "$inputId:81",
            "PASSWORD_INPUT" to "$inputId:82", "CONTEXT" to challenge, "METHOD" to method, "NETWORK" to network()) + values
        fun replace(value: Any): Any = when (value) {
            is JSONObject -> JSONObject().apply { value.keys().forEach { put(it, replace(value.get(it))) } }
            is JSONArray -> JSONArray().apply { for (i in 0 until value.length()) put(replace(value.get(i))) }
            is String -> if (value.matches(Regex("__[A-Z_]+__"))) base[value.removeSurrounding("__")] ?: error("Missing contract field") else value
            else -> value
        }
        return replace(templates.getJSONObject(name)) as JSONObject
    }
    private fun network(): JSONObject {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return JSONObject().put("active_subscriptions_info", JSONObject.NULL).put("default_subscription_info", JSONObject.NULL)
            .put("is_airplane_mode", android.provider.Settings.Global.getInt(context.contentResolver, "airplane_mode_on", 0))
            .put("is_active_network_cellular", if (capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true) 1 else 0)
            .put("is_wifi", if (capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) 1 else 0)
            .put("sim_count", JSONObject.NULL).put("is_device_sms_capable", JSONObject.NULL)
    }
    private fun bloks(name: String, action: String, app: Boolean = false, extra: Map<String, Any> = emptyMap(),
        headers: Map<String, String> = emptyMap(), domain: String = "b.i.instagram.com"): Reply {
        val data = mapOf("params" to template(name, extra).toString(), "_uuid" to device.uuid,
            "bk_client_context" to JSONObject().put("bloks_version", PhoneIdentity.BLOKS).put("styles_id", "instagram").toString(),
            "bloks_versioning_id" to PhoneIdentity.BLOKS)
        val path = "bloks/${if (app) "apps" else "async_action"}/$action/"
        val operation = when (name) {
            "home" -> PhoneLoginOperation.CONTEXT; "oauth" -> PhoneLoginOperation.OAUTH
            "password" -> PhoneLoginOperation.PASSWORD; else -> PhoneLoginOperation.VERIFICATION
        }
        return exchange(request("https://$domain/api/v1/$path", data, headers + (if (app) emptyMap() else mapOf("X-FB-Friendly-Name" to "IgApi: $path"))), operation)
    }
    private fun register() {
        if (device.registered) return
        val variables = JSONObject().put("input", JSONObject().put("usdid_token", JSONObject().put("sensitive_string_value", device.registrationToken(runtime.now())))
            .put("fdid", JSONObject().put("sensitive_string_value", device.phoneId)))
        val fields = mapOf("method" to "post", "pretty" to "false", "format" to "json", "server_timestamps" to "true", "locale" to "user",
            "fb_api_req_friendly_name" to "IGUSDIDRegistrationMutation", "enable_canonical_naming" to "true",
            "enable_canonical_variable_overrides" to "true", "enable_canonical_naming_ambiguous_type_prefixing" to "true",
            "purpose" to "fetch", "client_doc_id" to "124930351917786857261002920888", "variables" to variables.toString())
        val result = exchange(request("https://b.i.instagram.com/graphql_www", fields, mapOf("X-FB-Friendly-Name" to "IGUSDIDRegistrationMutation",
            "X-Client-Doc-Id" to "124930351917786857261002920888", "X-Root-Field-Name" to "usdid_registration", "X-Graphql-Client-Library" to "pando")), PhoneLoginOperation.DEVICE).body
        val data = result.optJSONObject("data") ?: throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
        if (data.keys().asSequence().none { it.contains("usdid_registration") && data.optJSONObject(it)?.optBoolean("success") == true })
            throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
        current(); device.registered = true; device.save(context, identitySlot)
    }
    fun login(contact: String, password: CharArray): PhoneLoginResult {
        try {
            if (contact.isBlank() || contact.length > 254 || contact.any { it.code < 32 } || password.isEmpty() || password.size > 1024)
                throw PhoneLoginFailure(PhoneLoginFailure.Reason.INPUT)
            if (gate.blockedUntil() > runtime.now()) throw ApiFailure(ApiFailure.Kind.LIMITED)
            if (gate.nextPrototypeAt() > runtime.now()) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
            username = contact.trim(); expires = runtime.elapsed() + 10 * 60_000
            device = try { PhoneIdentity.load(context, identitySlot) } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
            register()
            aac = PhoneBloks.aac(bloks("home", "com.bloks.www.bloks.caa.login.process_client_data_and_redirect").body)
                ?: throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
            val nonce = exchange(request("https://b.i.instagram.com/api/v1/attestation/create_android_keystore/",
                mapOf("app_scoped_device_id" to device.uuid, "key_hash" to ""), mapOf("X-FB-Friendly-Name" to "IgApi: attestation/create_android_keystore/")), PhoneLoginOperation.CHALLENGE)
                .body.optString("challenge_nonce").takeIf { it.isNotBlank() } ?: throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
            bloks("oauth", "com.bloks.www.caa.login.oauth.token.fetch.async")
            val key = exchange(request("https://i.instagram.com/api/v1/qe/sync/"), PhoneLoginOperation.PASSWORD_KEY)
            fun header(name: String) = key.headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.lastOrNull()
            val keyId = header("ig-set-password-encryption-key-id")?.toIntOrNull() ?: throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
            val public = header("ig-set-password-encryption-pub-key") ?: throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
            val encrypted = PhonePassword.encrypt(password, keyId, public, runtime.now())
            step = PhoneLoginStep.SIGNING_IN
            // We do not own Instagram's attestation key. The upstream compatibility state declares it unavailable;
            // it contains no fabricated certificate, hardware proof or signed nonce. Server refusal stops the flow.
            val attest = JSONObject().put("attestation", JSONArray().put(JSONObject().put("version", 2).put("type", "keystore")
                .put("errors", JSONArray().put(-1013)).put("challenge_nonce", nonce).put("signed_nonce", "").put("key_hash", ""))).toString()
            var result = bloks("password", "com.bloks.www.bloks.caa.login.async.send_login_request", extra =
                mapOf("PASSWORD" to encrypted, "NON_ASCII" to password.any { it.code > 127 }.toString()), headers = mapOf("X-IG-Attest-Params" to attest))
            if (PhoneBloks.strings(result.body).any { it.contains("CAA_LOGIN_FALLBACK:") }) {
                // Follow only an explicit server-directed legacy step. Never fall back after a transport/restriction error.
                val legacy = JSONObject().put("jazoest", "2${device.phoneId.sumOf { it.code }}").put("country_codes", "[]")
                    .put("phone_id", device.phoneId).put("enc_password", encrypted).put("username", username).put("guid", device.uuid)
                    .put("device_id", device.androidId).put("google_tokens", "[]").put("login_attempt_count", "0")
                result = exchange(request("https://i.instagram.com/api/v1/accounts/login/", mapOf("signed_body" to "SIGNATURE.$legacy")), PhoneLoginOperation.LEGACY)
            }
            return outcome(result)
        } finally { password.fill('\u0000') }
    }
    private fun outcome(reply: Reply): PhoneLoginResult {
        current()
        val parsed = PhoneBloks.login(reply.body)
        val loginBody = parsed?.body ?: reply.body
        val embedded = parsed?.headers?.keys()?.asSequence()?.associateWith { parsed.headers.getString(it) }.orEmpty()
        val auth = embedded.entries.firstOrNull { it.key.equals("ig-set-authorization", true) }?.value
            ?: reply.headers.entries.firstOrNull { it.key.equals("ig-set-authorization", true) }?.value?.lastOrNull()
        if (auth != null && loginBody.optJSONObject("logged_in_user") != null) {
            val owner = InstagramIdentity.id(loginBody.getJSONObject("logged_in_user").opt("pk"))
            try { MobileSessionCodec.authorization(auth, owner) } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT) }
            val token = JSONObject(auth.removePrefix("Bearer IGT:2:").decodeBase64()!!.utf8())
            cookies["sessionid"] = MobileSessionCodec.cookie(token.getString("sessionid")); cookies["ds_user_id"] = owner
            val headers = device.headers() + rotated + mapOf("Authorization" to auth, "IG-INTENDED-USER-ID" to owner, "IG-U-DS-USER-ID" to owner)
            var state = MobileSession(UUID.randomUUID().toString().replace("-", ""), owner, headers, cookies.toMap(), device.usdid, device.privatePem)
            val updates = reply.headers.toMutableMap()
            embedded.forEach { (k, v) -> updates[k] = listOf(v) }
            state = try { state.updated(updates) } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT) }
            try { MobileSessionCodec.decode(MobileSessionCodec.encode(state)) } catch (_: Exception) { throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED) }
            step = PhoneLoginStep.PROFILE
            val profileReply = exchange(MobileProfileReader.request(state, owner, null, runtime.now()), PhoneLoginOperation.PROFILE)
            val profile = ProfileParser.fromBody(profileReply.body, owner, runtime.now())
            current(); state = try { state.updated(profileReply.headers) } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT) }
            return PhoneLoginResult.Ready(state, profile)
        }
        PhoneBloks.context(reply.body, PhoneBloks.AP_ENTRY)?.let { token ->
            challenge = token; step = PhoneLoginStep.VERIFYING
            val entry = bloks("ap_entry", PhoneBloks.AP_ENTRY).body
            challenge = PhoneBloks.context(entry, PhoneBloks.AP_CODE) ?: throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
            val code = bloks("ap_code", PhoneBloks.AP_CODE, app = true).body
            challenge = PhoneBloks.context(code, PhoneBloks.AP_SUBMIT) ?: throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
            kind = "ap"; return PhoneLoginResult.Code
        }
        PhoneBloks.twoContext(reply.body)?.let { challenge = it; kind = "two"; return PhoneLoginResult.Method }
        throw PhoneLoginFailure(if (step == PhoneLoginStep.VERIFYING) PhoneLoginFailure.Reason.CODE else PhoneLoginFailure.Reason.UNSUPPORTED)
    }
    fun chooseMethod(value: String): PhoneLoginResult {
        current(); require(value in listOf("totp", "sms", "backup_codes") && kind == "two" && challenge.isNotBlank())
        method = value; step = PhoneLoginStep.VERIFYING
        bloks("two_entry", PhoneBloks.TWO_ENTRY, app = true, domain = "i.instagram.com")
        bloks("two_picker", "com.bloks.www.two_step_verification.method_picker", app = true, domain = "i.instagram.com")
        bloks("two_method", "com.bloks.www.two_step_verification.method_picker.navigation.async", domain = "i.instagram.com")
        if (method == "backup_codes") bloks("two_backup", "com.bloks.www.two_factor_login.enter_backup_code", app = true, domain = "i.instagram.com")
        return PhoneLoginResult.Code
    }
    fun submitCode(code: String): PhoneLoginResult {
        current()
        val normalized = code.replace(Regex("[\\s-]+"), "")
        if (normalized.length !in 4..32 || !normalized.all { it.isLetterOrDigit() } || challenge.isBlank())
            throw PhoneLoginFailure(PhoneLoginFailure.Reason.CODE)
        step = PhoneLoginStep.VERIFYING
        return if (kind == "ap") outcome(bloks("ap_submit", PhoneBloks.AP_SUBMIT, extra = mapOf("CODE" to normalized)))
        else outcome(bloks("two_submit", "com.bloks.www.two_step_verification.verify_code.async", extra = mapOf("CODE" to normalized), domain = "i.instagram.com"))
    }
}
