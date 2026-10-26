package dev.kaankaya.instagramtracker

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID

/** One encrypted installation identity, reused after failures. Contains no password or account credentials. */
class PhoneIdentity private constructor(val uuid: String, val phoneId: String, val androidId: String,
    val usdid: String, private val kid: String, val privatePem: String, private val publicSpki: String, var registered: Boolean) {
    companion object {
        const val APP_ID = "567067343352427"
        const val BLOKS = "799db3e387d7a21a19c72918b8698b6cd28a1516ade363792c100ed45a0f9f0b"
        private val lock = Any()
        fun load(context: Context, slot: String = "phoneidentity"): PhoneIdentity = synchronized(lock) {
            val record = EncryptedRecord(context, slot)
            val saved = record.read()
            if (saved != null) {
                val j = JSONObject(saved); require(j.getInt("version") == 1)
                fun id(key: String) = j.getString(key).also { require(UUID.fromString(it).toString() == it) }
                return@synchronized PhoneIdentity(id("uuid"), id("phone"), j.getString("android").also {
                    require(it.matches(Regex("android-[a-f0-9]{16}"))) }, id("usdid"), j.getString("kid").also {
                    require(it.matches(Regex("[A-Za-z0-9_-]{43}"))) }, j.getString("private").also {
                    require(it.length <= 4096 && it.startsWith("-----BEGIN PRIVATE KEY-----")) }, j.getString("public").also { require(it.length <= 1024 && it.decodeBase64() != null) }, j.getBoolean("registered"))
            }
            val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            PhoneIdentity(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "android-" + UUID.randomUUID().toString().replace("-", "").take(16), UUID.randomUUID().toString(),
                ByteArray(32).also(SecureRandom()::nextBytes).toByteString().base64Url().trimEnd('='),
                "-----BEGIN PRIVATE KEY-----\n${key.private.encoded.toByteString().base64()}\n-----END PRIVATE KEY-----", key.public.encoded.toByteString().base64(), false)
                .also { it.save(context, slot) }
        }
        fun clear(context: Context) = synchronized(lock) { EncryptedRecord(context, "phoneidentity").clear() }
    }
    fun save(context: Context, slot: String = "phoneidentity") = synchronized(lock) {
        EncryptedRecord(context, slot).write(JSONObject().put("version", 1).put("uuid", uuid).put("phone", phoneId)
            .put("android", androidId).put("usdid", usdid).put("kid", kid).put("private", privatePem)
            .put("public", publicSpki).put("registered", registered).toString())
    }
    private val key by lazy {
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privatePem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "").decodeBase64()!!.toByteArray()))
    }
    private fun sign(text: String) = Signature.getInstance("SHA256withECDSA").run {
        initSign(key); update(text.toByteArray()); sign().toByteString().base64Url().trimEnd('=')
    }
    fun signedDevice(now: Long): String = "$usdid.${now / 1000 + 3600}".let { "$it.${sign(it)}" }
    fun registrationToken(now: Long): String {
        fun b64(text: String) = text.toByteArray().toByteString().base64Url().trimEnd('=')
        val payload = b64(JSONObject().put("sub", usdid).put("iat", now / 1000).put("aud", APP_ID)
            .put("exp", now / 1000 + 3600).put("pub", publicSpki).put("alg", "ES256").toString())
        val protected = b64(JSONObject().put("typ", "JWT").put("alg", "ES256").put("kid", kid).put("aid", APP_ID).put("ver", "1").toString())
        return b64(JSONObject().put("payload", payload).put("signatures", JSONArray().put(JSONObject()
            .put("protected", protected).put("signature", sign("$protected.$payload")))).toString())
    }
    fun headers(): Map<String, String> {
        fun ascii(value: String) = value.filter { it.code in 32..126 && it !in ";()" }.take(80)
        val ua = "Instagram 449.0.0.52.84 Android (${Build.VERSION.SDK_INT}/${ascii(Build.VERSION.RELEASE)}; " +
            "${android.content.res.Resources.getSystem().displayMetrics.densityDpi}dpi; " +
            "${android.content.res.Resources.getSystem().displayMetrics.widthPixels}x${android.content.res.Resources.getSystem().displayMetrics.heightPixels}; " +
            "${ascii(Build.MANUFACTURER)}; ${ascii(Build.MODEL)}; ${ascii(Build.DEVICE)}; ${ascii(Build.HARDWARE)}; en_US; 1079242191)"
        return mapOf("User-Agent" to ua, "X-IG-Device-ID" to uuid, "X-IG-Family-Device-ID" to phoneId,
            "X-IG-Android-ID" to androidId, "X-IG-App-ID" to APP_ID, "X-Bloks-Version-Id" to BLOKS,
            "X-IG-App-Locale" to "en_US", "X-IG-Device-Locale" to "en_US", "X-IG-Mapped-Locale" to "en_US",
            "X-Bloks-Is-Layout-RTL" to "false", "X-Bloks-Is-Panorama-Enabled" to "true",
            "X-IG-Capabilities" to "3brTv10=", "Accept-Language" to "en-US", "X-IG-Timezone-Offset" to
                (java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000).toString())
    }
}
