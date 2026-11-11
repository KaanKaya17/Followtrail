package dev.kaankaya.instagramtracker

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** Owns bounded, atomic Keystore encryption for account and mobile-session records. */
class EncryptedRecord(context: Context, slot: String, private val maxBytes: Int = 512 * 1024) {
    init { require(slot.matches(Regex("[a-z]+"))); require(maxBytes in 1..2 * 1024 * 1024) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "$slot.enc"))
    private val alias = "instagramtracker.$slot.v1"
    private val cipher = LocalCipher(alias,maxBytes)
    private val aad = "InstagramTracker/session/v1".toByteArray()
    fun exists(): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    fun stamp(): String = listOf(file.baseFile, File(file.baseFile.path + ".bak")).joinToString(":") { "${it.lastModified()}:${it.length()}" }
    fun write(value: String) {
        val json = value.toByteArray(Charsets.UTF_8)
        if (json.size > maxBytes) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        val encrypted = cipher.seal(json,aad)
        val output = file.startWrite()
        try { output.write(encrypted); file.finishWrite(output) }
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
        return String(cipher.open(bytes,aad), Charsets.UTF_8)
    }
    fun clear() {
        file.delete()
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() && !File(file.baseFile.path + ".new").exists())
        cipher.destroy()
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
    val privacy get()=PrivacyStore(context,storage())
    internal fun storyStore(session: Session): StoryArchiveStore = synchronized(lock) {
        if (catalog != null && currentOwner() != session.userId) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        StoryArchiveStore(context,session.userId,storage())
    }
    private fun lists()=if(slot=="account")FollowListVault(context,owner=currentOwner()) else FollowListVault(context,if(slot=="previewaccount")"previewlists" else storage()+"lists")
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
    private fun persist(account: SavedAccount, previous: SavedAccount?, destination: String): SavedAccount {
        val retained=PrivacyStore(context,destination).read(account.session.userId).retain(account,System.currentTimeMillis())
        EncryptedRecord(context,destination,ACCOUNT_BYTES).write(AccountCodec.encode(retained))
        catalog?.remember(retained,owner==null)
        VisualHistoryStore(context,destination).observe(retained)
        DailyStatsStore(context,destination).observe(account,previous?.takeIf { it.session.userId==account.session.userId })
        return retained
    }
    fun read(): SavedAccount? = synchronized(lock) {
        val c=catalog
        c?.initialize()
        if(c!=null && (currentOwner()==null || !c.stored(currentOwner()!!)))return@synchronized null
        val saved=record.read()?.let(AccountCodec::decode)?.also {require(c==null || it.session.userId==currentOwner())}
            ?: return@synchronized if(c!=null)throw ApiFailure(ApiFailure.Kind.STORAGE) else null
        maintain(saved)
    }
    private fun maintain(saved: SavedAccount, force: Boolean = false): SavedAccount {
        val now=System.currentTimeMillis(); val policy=privacy.read(saved.session.userId)
        if(!force && !policy.due(now))return saved
        val attempted=policy.copy(attemptedAt=now,failed=false)
        privacy.write(saved.session.userId,attempted)
        return try {
            daily.observe(saved,null)
            daily.prune(saved.session.userId,policy.dailyBefore(now))
            visual.prune(saved.session.userId,policy.historyBefore(now))
            storyStore(saved.session).prune(policy.historyBefore(now))
            journal.prune(saved.session,policy.historyBefore(now))
            lists().prunePast(saved.session,policy.historyBefore(now))
            val retained=policy.retain(saved,now)
            record.write(AccountCodec.encode(retained))
            retained
        } catch (_: Exception) {
            privacy.write(saved.session.userId,attempted.copy(failed=true)); saved
        }
    }
    fun configurePrivacy(expected: Session, value: ArchiveRetention): SavedAccount = synchronized(lock) {
        val current=read()?.takeIf {it.session==expected} ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        privacy.write(expected.userId,value.copy(attemptedAt=0,failed=false))
        maintain(current,true)
    }
    /** Auxiliary owners finish erasure before the saved-profile registry is changed. */
    fun erasePerson(expected: Session, target: String, followLists: FollowListVault = lists()): SavedAccount = synchronized(lock) {
        val current=read()?.takeIf {it.session==expected} ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val person=current.people.firstOrNull {it.latest.userId==target} ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        daily.clear(expected.userId,target,maxOf(System.currentTimeMillis(),person.latest.observedAt))
        visual.clear(expected.userId,target)
        storyStore(expected).clearTarget(target)
        journal.clearTarget(expected,target)
        followLists.clearTarget(expected,target)
        AvatarCache(context,if(slot=="previewaccount")"previewavatars" else "avatars").clear(expected.userId,target)
        val automation = current.automation.copy(targets=current.automation.targets-target,
            initialTargets=current.automation.initialTargets-target)
        val retained=current.copy(people=current.people.filterNot {it.latest.userId==target},
            automation=automation.copy(enabled=automation.enabled && (automation.targets.isNotEmpty() || automation.initialTargets.isNotEmpty())))
        persist(retained,current,storage())
    }
    /** Serialize foreground/background read-modify-write operations across vault instances. */
    fun update(expected: Session, transform: (SavedAccount) -> SavedAccount): SavedAccount = synchronized(lock) {
        val current = read()?.takeIf { it.session == expected } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val next=transform(current); require(next.session==expected)
        if (next.people === current.people) return@synchronized persist(next, current, storage())
        val before = current.people.map { it.latest.userId }.toSet()
        val after = next.people.map { it.latest.userId }.toSet()
        val pending = (next.automation.initialTargets + (after - before)).intersect(after)
        persist(next.copy(automation = next.automation.copy(initialTargets = pending)), current, storage())
    }
    fun finishInitialLists(current: SavedAccount, target: ListTarget) = synchronized(lock) {
        if (target.target in current.automation.initialTargets &&
            FollowDirection.entries.all { ListAutomationPolicy.source(target, it)?.comparable() == true }) {
            update(current.session) { saved ->
                val automation = saved.automation
                val remaining = automation.initialTargets - target.target
                saved.copy(automation = automation.copy(initialTargets = remaining,
                    enabled = automation.enabled && (automation.targets.isNotEmpty() || remaining.isNotEmpty())))
            }
        }
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
        StoryArchiveStore.clearNamespace(context,storage())
        record.clear()
        journal.clear()
        visual.clearAll()
        daily.clearAll()
        privacy.clear()
        id?.let {catalog?.forget(it)}
    }
}
