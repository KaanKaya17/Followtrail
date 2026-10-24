package dev.kaankaya.instagramtracker

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "account.enc"))
    private val alias = "instagramtracker.account.v1"
    private val aad = "InstagramTracker/session/v1".toByteArray()
    fun exists(): Boolean = file.baseFile.exists()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized fun write(account: SavedAccount) {
        val s = account.session
        val p = account.profile
        val json = JSONObject().put("version", 1).put("id", s.userId).put("cookie", s.cookie)
            .put("ua", s.userAgent).put("username", p.username).put("followers", p.followers)
            .put("following", p.following).put("observedAt", p.observedAt).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(cipher.iv); output.write(encrypted); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
    @Synchronized fun read(): SavedAccount? {
        if (!exists()) return null
        val bytes = file.readFully()
        require(bytes.size in 29..65536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        require(json.getInt("version") == 1)
        val session = SessionCookies.fromHeader(json.getString("cookie"), json.getString("ua"))
            ?: throw IllegalArgumentException("Invalid session")
        require(session.userId == json.getString("id"))
        val profile = Profile(session.userId, json.getString("username"), json.getLong("followers"),
            json.getLong("following"), json.getLong("observedAt"))
        require(profile.username.isNotBlank() && profile.followers >= 0 && profile.following >= 0 && profile.observedAt > 0)
        return SavedAccount(session, profile)
    }
    @Synchronized fun clear() {
        file.delete()
        check(!file.baseFile.exists())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}
