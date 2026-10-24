package dev.kaankaya.instagramtracker

import android.content.Context

/** Two encrypted banks allow staging without replacing the currently linked account. */
class MobileSessions(context: Context, namespace: String = "mobile") {
    companion object { private val lock = Any() }
    init { require(namespace.matches(Regex("[a-z]{1,30}"))) }
    private val banks = listOf(EncryptedRecord(context, "${namespace}a"), EncryptedRecord(context, "${namespace}b"))
    private val account = SessionVault(context, if (namespace == "mobile") "account" else "${namespace}account")
    private val preferences = context.getSharedPreferences("${namespace}_pending", Context.MODE_PRIVATE)
    private fun values() = banks.map { record -> record.read()?.let(MobileSessionCodec::decode) }
    fun exists(): Boolean = synchronized(lock) { banks.any { it.exists() } }
    fun pending(): Session? = synchronized(lock) {
        val binding = preferences.getString("binding", null) ?: return@synchronized null
        if (account.read()?.session?.mobileBinding == binding) return@synchronized null
        values().firstOrNull { it?.binding == binding }?.reference()
    }
    fun stage(state: MobileSession): Session = synchronized(lock) {
        val current = account.read()?.session?.mobileBinding
        val values = values()
        val target = if (values[0]?.binding == current && current != null) 1 else 0
        banks[target].write(MobileSessionCodec.encode(state))
        if (!preferences.edit().putString("binding", state.binding).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        state.reference()
    }
    fun read(reference: Session): MobileSession = synchronized(lock) {
        values().firstOrNull { it?.binding == reference.mobileBinding && it?.owner == reference.userId }
            ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
    }
    fun discardPending(): Unit = synchronized(lock) {
        val binding = preferences.getString("binding", null)
        if (binding != null && account.read()?.session?.mobileBinding != binding) {
            values().forEachIndexed { index, state -> if (state?.binding == binding) banks[index].clear() }
        }
        check(preferences.edit().remove("binding").commit())
    }
    fun save(reference: Session, state: MobileSession): Unit = synchronized(lock) {
        require(state.reference() == reference)
        val index = values().indexOfFirst { it?.binding == reference.mobileBinding }
        if (index < 0) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        banks[index].write(MobileSessionCodec.encode(state))
    }
    fun clear(): Unit = synchronized(lock) {
        banks.forEach { it.clear() }
        check(preferences.edit().clear().commit())
    }
}
