package dev.kaankaya.instagramtracker

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** Public request + single-use Keystore decryption. Never exports a private key. */
class DesktopLink(private val context: Context, namespace: String = "") {
    companion object { private val lock = Any() }
    init { require(namespace.matches(Regex("[a-z]{0,20}"))) }
    private val alias = "instagramtracker.desktoplink.${namespace}v1"
    private val preferences = context.getSharedPreferences("${namespace}desktop_link", Context.MODE_PRIVATE)
    private val directory = context.getExternalFilesDir(null) ?: error("USB directory unavailable")
    internal val publicFile = File(directory, "${namespace}mobile-link-public.json")
    val bundleFile = File(directory, "${namespace}desktop-session.iglink")
    fun isPrepared(now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        preferences.getLong("expires", 0) > now && publicFile.isFile &&
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias)
    }
    fun prepare(now: Long = System.currentTimeMillis()): Unit = synchronized(lock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (preferences.getLong("expires", 0) > now && store.containsAlias(alias) && publicFile.isFile) return@synchronized
        clear()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(3072).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).build())
        }.generateKeyPair()
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.toByteString().hex()
        val expiry = now + 10 * 60 * 1000
        check(preferences.edit().putString("request", nonce).putLong("expires", expiry).commit())
        val key = store.getCertificate(alias).publicKey.encoded.toByteString().base64()
        publicFile.writeText(JSONObject().put("version", 1).put("requestId", nonce).put("expiresAt", expiry / 1000)
            .put("package", context.packageName).put("publicKey", key).toString())
    }
    fun importBundle(now: Long = System.currentTimeMillis()): MobileSession = synchronized(lock) {
        require(preferences.getLong("expires", 0) > now)
        val bytes = bundleFile.inputStream().use { it.readBounded(256 * 1024) }
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        require(envelope.getInt("version") == 1)
        val nonce = preferences.getString("request", null) ?: error("No request")
        require(envelope.getString("requestId") == nonce)
        fun field(name: String) = envelope.getString(name).decodeBase64()?.toByteArray() ?: error("Invalid envelope")
        val wrapped = field("wrappedKey"); val iv = field("iv"); val ciphertext = field("ciphertext")
        require(wrapped.size == 384 && iv.size == 12 && ciphertext.size in 17..(160 * 1024))
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.DECRYPT_MODE, store.getKey(alias, null), OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))
        val key = rsa.doFinal(wrapped)
        try {
            require(key.size == 32)
            val aes = Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            aes.updateAAD("InstagramTracker/mobile-link/v1/$nonce".toByteArray())
            val plaintext = aes.doFinal(ciphertext)
            try { MobileSessionCodec.imported(String(plaintext, Charsets.UTF_8)) } finally { plaintext.fill(0) }
        } finally { key.fill(0) }
    }
    fun clear(): Unit = synchronized(lock) {
        if (publicFile.exists()) check(publicFile.delete())
        if (bundleFile.exists()) check(bundleFile.delete())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        check(preferences.edit().clear().commit())
    }
}

internal fun java.io.InputStream.readBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer); if (count < 0) return output.toByteArray()
        require(output.size() <= limit - count); output.write(buffer, 0, count)
    }
}
