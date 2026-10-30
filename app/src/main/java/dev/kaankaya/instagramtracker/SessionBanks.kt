package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONObject

interface ReadSessionStore<S> {
    fun read(reference: Session): S
    fun save(reference: Session, state: S)
}

/** Per-account current/staging banks. The encrypted pending pointer never changes the selected account. */
open class SessionBanks<S>(private val context: Context, private val namespace: String, private val kind: String,
    private val decode: (String) -> S, private val encode: (S) -> String,
    private val referenceOf: (S) -> Session, private val bindingOf: (Session) -> String?) : ReadSessionStore<S> {
    companion object {private val lock=AccountCatalog.lock}
    init {require(namespace.matches(Regex("[a-z]{1,30}")))}
    private val managed=namespace==kind
    private val pointer=EncryptedRecord(context,"${namespace}pendingowner")
    private val migration=EncryptedRecord(context,"${namespace}migration")
    private var migrated=false
    private inner class Bank(val prefix: String,val account: SessionVault) {
        val banks=listOf(EncryptedRecord(context,"${prefix}a"),EncryptedRecord(context,"${prefix}b"))
        val preferences=context.getSharedPreferences("${prefix}_pending",Context.MODE_PRIVATE)
        fun values()=banks.map {it.read()?.let(decode)}
        fun pending(pinnedBinding: String?=null): Session? {
            val binding=pinnedBinding ?: preferences.getString("binding",null) ?: return null
            if(account.read()?.session?.let(bindingOf)==binding)return null
            return values().filterNotNull().map(referenceOf).firstOrNull {bindingOf(it)==binding}
        }
        fun stage(state: S): Session {
            val ref=referenceOf(state)
            val binding=bindingOf(ref) ?: error("Missing binding")
            val current=account.read()?.session?.let(bindingOf)
            val values=values()
            val index = if (current != null && values[0]?.let(referenceOf)?.let(bindingOf) == current) 1 else 0
            banks[index].write(encode(state))
            check(preferences.edit().putString("binding",binding).commit());return ref
        }
        fun read(ref: Session): S = values().filterNotNull().firstOrNull {referenceOf(it)==ref}
            ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
        fun discard(expected: Session?,pinnedBinding: String?=null) {
            val binding=pinnedBinding ?: preferences.getString("binding",null)
            if(expected!=null && binding!=bindingOf(expected))return
            if(binding!=null && account.read()?.session?.let(bindingOf)!=binding) {
                values().forEachIndexed {index,value->if(value!=null && bindingOf(referenceOf(value))==binding)banks[index].clear()}
            }
            check(preferences.edit().remove("binding").commit())
        }
        fun save(ref: Session,state: S) {
            require(referenceOf(state)==ref)
            val index=values().indexOfFirst {it!=null && referenceOf(it)==ref}
            if(index<0)throw ApiFailure(ApiFailure.Kind.CANCELLED)
            banks[index].write(encode(state))
        }
        fun clear() {banks.forEach {it.clear()};check(preferences.edit().clear().commit())}
    }
    private fun legacy()=Bank(namespace,SessionVault(context,if(managed)"account" else "${namespace}account"))
    private fun owned(owner: String)=Bank(AccountCatalog(context).storage(owner)+namespace,SessionVault(context,owner=owner))
    private fun migrate() {
        if(migrated)return
        migration.read()?.let {require(JSONObject(it).getInt("version")==1);migrated=true;return}
        // Copy both legacy owners once. A durable marker prevents stale backups becoming live again.
        val old=legacy();val values=old.values()
        values.forEachIndexed {index,value->
            if(value!=null) {
                val destination=owned(referenceOf(value).userId).banks[index]
                if(!destination.exists())destination.write(encode(value))
                else require(referenceOf(decode(destination.read()!!))==referenceOf(value))
            }
        }
        val binding=old.preferences.getString("binding",null)
        if(binding!=null)values.filterNotNull().firstOrNull {bindingOf(referenceOf(it))==binding}?.let {state->
            val owner=referenceOf(state).userId
            check(owned(owner).preferences.edit().putString("binding",binding).commit())
            point(owner,binding)
        }
        migration.write(JSONObject().put("version",1).toString());migrated=true
    }
    private fun bank(owner: String): Bank {migrate();return owned(owner)}
    private fun pendingIdentity(): Pair<String,String>?=pointer.read()?.let {raw->
        val value=JSONObject(raw);val owner=InstagramIdentity.id(value.get("owner"));val binding=value.getString("binding")
        require(binding.matches(Regex("[a-f0-9]{32}")));owner to binding
    }
    private fun pendingOwner(): String?=pendingIdentity()?.first
    private fun point(owner: String,binding: String) {pointer.write(JSONObject().put("owner",owner).put("binding",binding).toString())}
    fun exists(): Boolean = synchronized(lock) {
        if(!managed)legacy().banks.any {it.exists()} else {migrate()
            AccountCatalog(context).entries().any {bank(it.id).banks.any {file->file.exists()}} || pendingOwner()?.let {bank(it).banks.any {file->file.exists()}}==true
        }
    }
    fun pending(): Session? = synchronized(lock) {
        if(!managed)return@synchronized legacy().pending()
        migrate()
        pendingIdentity()?.let {(owner,binding)->return@synchronized bank(owner).pending(binding)}
        null
    }
    fun stage(state: S): Session = synchronized(lock) {
        if(!managed)return@synchronized legacy().stage(state)
        migrate()
        val owner=referenceOf(state).userId
        point(owner,bindingOf(referenceOf(state)) ?: error("Missing binding"))
        bank(owner).stage(state)
    }
    override fun read(reference: Session): S = synchronized(lock) {
        (if(managed)bank(reference.userId) else legacy()).read(reference)
    }
    override fun save(reference: Session,state: S): Unit = synchronized(lock) {
        (if(managed)bank(reference.userId) else legacy()).save(reference,state)
    }
    fun discardPending(expected: Session? = null): Unit = synchronized(lock) {
        if(!managed) {legacy().discard(expected);return@synchronized}
        migrate()
        val identity=pendingIdentity() ?: return@synchronized
        if(expected!=null && (identity.first!=expected.userId || identity.second!=bindingOf(expected)))return@synchronized
        bank(identity.first).discard(expected,identity.second)
        pointer.clear()
    }
    fun clearOwner(owner: String): Unit = synchronized(lock) {
        require(managed)
        bank(owner).clear()
        val old=legacy()
        old.values().forEachIndexed {index,value->if(value!=null && referenceOf(value).userId==owner)old.banks[index].clear()}
        if(pendingOwner()==owner)pointer.clear()
    }
    fun clear(): Unit = synchronized(lock) {
        if(managed) {
            val owners=AccountCatalog(context).entries().map {it.id}+listOfNotNull(pendingOwner())
            owners.distinct().forEach {bank(it).clear()};pointer.clear()
        }
        legacy().clear()
    }
}
