package dev.kaankaya.instagramtracker

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Owns bounded, atomic Keystore encryption for account and mobile-session records. */
class EncryptedRecord(context: Context, slot: String, private val maxBytes: Int = 512 * 1024) {
    init { require(slot.matches(Regex("[a-z]+"))); require(maxBytes in 1..2 * 1024 * 1024) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "$slot.enc"))
    private val alias = "instagramtracker.$slot.v1"
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
    fun write(value: String) {
        val json = value.toByteArray(Charsets.UTF_8)
        if (json.size > maxBytes) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(json)
        val output = file.startWrite()
        try { output.write(cipher.iv); output.write(encrypted); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
    fun read(): String? {
        if (!exists()) return null
        val bytes = file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() <= maxBytes + 28 - count)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size >= 29)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    fun clear() {
        file.delete()
        check(!file.baseFile.exists())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}

class SessionVault(context: Context, slot: String = "account") {
    companion object { private val lock = Any(); const val ACCOUNT_BYTES = 2 * 1024 * 1024 }
    private val record = EncryptedRecord(context, slot, ACCOUNT_BYTES)
    val visual = VisualHistoryStore(context, slot)
    val journal = CheckJournal(context, "${slot}checks")
    fun record(session: Session, event: CheckEvent) = synchronized(lock) {
        val current = try { read() } catch (_: Exception) { null }
        if (current?.session == session && (event.target == current.profile.userId || current.people.any { it.latest.userId == event.target }))
            journal.append(session, event)
    }
    fun recordFailure(session: Session, target: String, startedAt: Long?, failure: ApiFailure,
        endedAt: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (failure.kind == ApiFailure.Kind.CANCELLED) return@synchronized
        val current = (try { read() } catch (_: Exception) { null })?.takeIf { it.session == session } ?: return@synchronized
        val profile = if (target == current.profile.userId) current.profile else current.people.firstOrNull { it.latest.userId == target }?.latest ?: return@synchronized
        record(session, CheckEvent(target, profile.username, CheckKind.FAILURE, startedAt, null, endedAt, failure = failure.kind))
    }
    fun exists(): Boolean = synchronized(lock) { record.exists() }
    fun write(account: SavedAccount): Unit = synchronized(lock) {
        record.write(AccountCodec.encode(account))
        visual.observe(account)
    }
    fun read(): SavedAccount? = synchronized(lock) { record.read()?.let(AccountCodec::decode) }
    /** Serialize foreground/background read-modify-write operations across vault instances. */
    fun update(expected: Session, transform: (SavedAccount) -> SavedAccount): SavedAccount = synchronized(lock) {
        val current = read()?.takeIf { it.session == expected } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        transform(current).also { require(it.session == expected); write(it) }
    }
    fun link(session: Session, profile: Profile): SavedAccount = synchronized(lock) {
        val previous = read()
        val sameOwner = previous?.takeIf { it.session.userId == session.userId }
        SavedAccount(session, profile, sameOwner?.people.orEmpty(), sameOwner?.automation ?: ListAutomation(enabled = true, targets = setOf(session.userId)))
            .also { write(it) }
    }
    /** A completed login cannot overwrite a connection changed while the user was authenticating. */
    fun linkIfUnchanged(expected: Session?, session: Session, profile: Profile, cancelled: () -> Boolean): SavedAccount = synchronized(lock) {
        if (cancelled() || read()?.session != expected) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        link(session, profile)
    }
    fun inspect(action: (SavedAccount?) -> Unit) = synchronized(lock) { action(read()) }
    fun clear(): Unit = synchronized(lock) {
        record.clear()
        journal.clear()
        visual.clearAll()
    }
}
