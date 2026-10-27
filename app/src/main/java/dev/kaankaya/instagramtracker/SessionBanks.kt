package dev.kaankaya.instagramtracker

import android.content.Context

interface ReadSessionStore<S> {
    fun read(reference: Session): S
    fun save(reference: Session, state: S)
}

/** One owner for encrypted staging, current-account protection and session rotation. */
open class SessionBanks<S>(context: Context, namespace: String, kind: String,
    private val decode: (String) -> S, private val encode: (S) -> String,
    private val referenceOf: (S) -> Session, private val bindingOf: (Session) -> String?) : ReadSessionStore<S> {
    companion object { private val lock = Any() }
    init { require(namespace.matches(Regex("[a-z]{1,30}"))) }
    private val banks = listOf(EncryptedRecord(context, "${namespace}a"), EncryptedRecord(context, "${namespace}b"))
    private val account = SessionVault(context, if (namespace == kind) "account" else "${namespace}account")
    private val preferences = context.getSharedPreferences("${namespace}_pending", Context.MODE_PRIVATE)
    private fun values() = banks.map { record -> record.read()?.let(decode) }
    fun exists(): Boolean = synchronized(lock) { banks.any { it.exists() } }
    fun pending(): Session? = synchronized(lock) {
        val binding = preferences.getString("binding", null) ?: return@synchronized null
        if (account.read()?.session?.let(bindingOf) == binding) return@synchronized null
        values().filterNotNull().map(referenceOf).firstOrNull { bindingOf(it) == binding }
    }
    fun stage(state: S): Session = synchronized(lock) {
        val reference = referenceOf(state)
        val binding = bindingOf(reference) ?: throw IllegalArgumentException("Missing binding")
        val current = account.read()?.session?.let(bindingOf)
        val values = values()
        val target = if (values[0]?.let(referenceOf)?.let(bindingOf) == current && current != null) 1 else 0
        banks[target].write(encode(state))
        if (!preferences.edit().putString("binding", binding).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        reference
    }
    override fun read(reference: Session): S = synchronized(lock) {
        val binding = bindingOf(reference) ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
        values().filterNotNull().firstOrNull { referenceOf(it) == reference && bindingOf(referenceOf(it)) == binding }
            ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
    }
    fun discardPending(expected: Session? = null): Unit = synchronized(lock) {
        val binding = preferences.getString("binding", null)
        if (expected != null && binding != bindingOf(expected)) return@synchronized
        if (binding != null && account.read()?.session?.let(bindingOf) != binding) {
            values().forEachIndexed { index, state -> if (state != null && bindingOf(referenceOf(state)) == binding) banks[index].clear() }
        }
        check(preferences.edit().remove("binding").commit())
    }
    override fun save(reference: Session, state: S): Unit = synchronized(lock) {
        require(referenceOf(state) == reference)
        val binding = bindingOf(reference) ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val index = values().indexOfFirst { it != null && bindingOf(referenceOf(it)) == binding }
        if (index < 0) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        banks[index].write(encode(state))
    }
    fun clear(): Unit = synchronized(lock) {
        banks.forEach { it.clear() }
        check(preferences.edit().clear().commit())
    }
}
