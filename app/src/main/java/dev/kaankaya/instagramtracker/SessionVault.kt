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
    fun exists(): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
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
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() && !File(file.baseFile.path + ".new").exists())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}

class SessionVault(private val context: Context, private val slot: String = "account", private val owner: String? = null) {
    companion object { private val lock=AccountCatalog.lock; const val ACCOUNT_BYTES = 2 * 1024 * 1024 }
    private val catalog get()=if(slot=="account")AccountCatalog(context) else null
    private fun currentOwner()=owner ?: catalog?.active()
    private fun storage()=catalog?.let {c->currentOwner()?.let(c::storage)} ?: slot
    private val record get()=EncryptedRecord(context,storage(),ACCOUNT_BYTES)
    val daily get()=DailyStatsStore(context,storage())
    val visual get()=VisualHistoryStore(context,storage())
    val journal get()=CheckJournal(context,"${storage()}checks")
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
    fun exists(): Boolean = synchronized(lock) { if(catalog!=null && currentOwner()==null)false else record.exists() }
    fun write(account: SavedAccount): Unit = synchronized(lock) {
        require(owner==null || owner==account.session.userId)
        val destination=catalog?.storage(account.session.userId) ?: slot
        val previous = try { EncryptedRecord(context,destination,ACCOUNT_BYTES).read()?.let(AccountCodec::decode) } catch (_: Exception) { null }
        persist(account,previous,destination)
    }
    private fun persist(account: SavedAccount, previous: SavedAccount?, destination: String) {
        EncryptedRecord(context,destination,ACCOUNT_BYTES).write(AccountCodec.encode(account))
        catalog?.remember(account,owner==null)
        VisualHistoryStore(context,destination).observe(account)
        DailyStatsStore(context,destination).observe(account,previous?.takeIf { it.session.userId==account.session.userId })
    }
    fun read(): SavedAccount? = synchronized(lock) {
        val c=catalog
        c?.initialize()
        if(c!=null && (currentOwner()==null || !c.stored(currentOwner()!!)))return@synchronized null
        record.read()?.let(AccountCodec::decode)?.also {require(c==null || it.session.userId==currentOwner())}
            ?: if(c!=null)throw ApiFailure(ApiFailure.Kind.STORAGE) else null
    }
    /** Serialize foreground/background read-modify-write operations across vault instances. */
    fun update(expected: Session, transform: (SavedAccount) -> SavedAccount): SavedAccount = synchronized(lock) {
        val current = read()?.takeIf { it.session == expected } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        transform(current).also { require(it.session == expected); persist(it,current,storage()) }
    }
    fun link(session: Session, profile: Profile): SavedAccount = synchronized(lock) {
        val previous = if(catalog!=null)SessionVault(context,owner=session.userId).read() else read()
        val sameOwner = previous?.takeIf { it.session.userId == session.userId }
        SavedAccount(session, profile, sameOwner?.people.orEmpty(), sameOwner?.automation ?: ListAutomation(enabled = true, targets = setOf(session.userId)))
            .also { write(it) }
    }
    /** A completed login cannot overwrite a connection changed while the user was authenticating. */
    fun linkIfUnchanged(expected: Session?, session: Session, profile: Profile, cancelled: () -> Boolean): SavedAccount = synchronized(lock) {
        if (cancelled() || read()?.session != expected) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        link(session, profile)
    }
    /** Imported connections take their selection snapshot before I/O and commit against that snapshot. */
    fun linkAfterReading(session: Session, cancelled: () -> Boolean, readProfile: () -> Profile): SavedAccount {
        val expected=synchronized(lock) {
            if(cancelled())throw ApiFailure(ApiFailure.Kind.CANCELLED)
            read()?.session
        }
        val profile=readProfile()
        return linkIfUnchanged(expected,session,profile,cancelled)
    }
    fun clearDaily(expected: Session, target: String, now: Long) = synchronized(lock) {
        val bound=if(catalog!=null)SessionVault(context,owner=expected.userId) else this
        val current=bound.read()?.takeIf {it.session==expected} ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val latest=if(target==current.profile.userId)current.profile.observedAt else current.people.firstOrNull {it.latest.userId==target}?.latest?.observedAt ?: now
        bound.daily.clear(expected.userId,target,maxOf(now,latest))
    }
    fun inspect(action: (SavedAccount?) -> Unit) = synchronized(lock) { action(read()) }
    fun clear(): Unit = synchronized(lock) {
        val id=currentOwner()
        if(catalog!=null && id==null)return@synchronized
        if(catalog!=null) {
            catalog!!.removing(id!!)
            MobileSessions(context).clearOwner(id);WebSessions(context).clearOwner(id)
            FollowListVault(context,owner=id).clear()
            check(context.getSharedPreferences("account_flags_"+AccountCatalog.slot(id),Context.MODE_PRIVATE).edit().clear().commit())
        }
        record.clear()
        journal.clear()
        visual.clearAll()
        daily.clearAll()
        id?.let {catalog?.forget(it)}
    }
}
