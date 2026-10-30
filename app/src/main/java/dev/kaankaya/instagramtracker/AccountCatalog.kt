package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The encrypted catalog owns account identity and selection; payloads stay in independent vaults. */
class AccountCatalog(context: Context) {
    data class Entry(val id: String, val username: String, val slot: String)
    private val context=context.applicationContext
    private val record=EncryptedRecord(context,"accounts",128*1024)
    companion object {
        internal val lock=Any()
        private val selected=java.util.concurrent.ConcurrentHashMap<String,String>()
        internal fun selected(context: Context): String?=selected[context.packageName]?.takeIf {it.isNotEmpty()}
        internal fun slot(id: String): String = "user"+InstagramIdentity.id(id).map { 'a'+(it-'0') }.joinToString("")
    }
    private fun read(): JSONObject {
        val body=record.read()?.let(::JSONObject) ?: run {
            // Add only a catalog. The existing encrypted account and its auxiliary files stay in place.
            val old=EncryptedRecord(context,"account",SessionVault.ACCOUNT_BYTES).read()?.let(AccountCodec::decode)
            JSONObject().put("version",1).put("active",old?.session?.userId ?: JSONObject.NULL)
                .put("cursor",JSONObject.NULL).put("accounts",JSONArray().apply {old?.let {
                    put(JSONObject().put("id",it.session.userId).put("username",it.profile.username).put("slot","account"))
                }}).also {record.write(it.toString())}
        }
        require(body.getInt("version")==1)
        val rows=body.getJSONArray("accounts")
        val ids=mutableSetOf<String>();val slots=mutableSetOf<String>()
        for(index in 0 until rows.length()) {
            val row=rows.getJSONObject(index);val id=InstagramIdentity.id(row.get("id"));val storage=row.getString("slot")
            require(ids.add(id) && slots.add(storage) && (storage=="account" || storage==slot(id)))
            require(ProfileInput.validUsername(row.getString("username")))
            require(!row.has("removing") || row.get("removing") is Boolean)
        }
        val active=if(body.isNull("active"))null else body.getString("active")
        require(active==null || active in ids)
        selected[context.packageName]=active.orEmpty()
        return body
    }
    private fun save(body: JSONObject) {record.write(body.toString());selected[context.packageName]=if(body.isNull("active"))"" else body.getString("active")}
    fun initialize() {synchronized(lock) {
        val rows=read().getJSONArray("accounts")
        val deleting=(0 until rows.length()).map(rows::getJSONObject).filter {it.optBoolean("removing",false)}.map {it.getString("id")}
        deleting.forEach {SessionVault(context,owner=it).clear()}
    }}
    fun entries(): List<Entry> = synchronized(lock) {
        val rows=read().getJSONArray("accounts")
        (0 until rows.length()).map {rows.getJSONObject(it).let {row->Entry(row.getString("id"),row.getString("username"),row.getString("slot"))}}
    }
    fun active(): String? = synchronized(lock) {read();selected(context)}
    internal fun storage(id: String): String = synchronized(lock) {entries().firstOrNull {it.id==id}?.slot ?: slot(id)}
    internal fun stored(id: String): Boolean = synchronized(lock) {entries().any {it.id==id}}
    internal fun remember(account: SavedAccount, activate: Boolean) = synchronized(lock) {
        val body=read();val before=body.toString();val rows=body.getJSONArray("accounts");val id=account.session.userId
        val row=(0 until rows.length()).map(rows::getJSONObject).firstOrNull {it.getString("id")==id}
            ?: JSONObject().put("id",id).put("slot",slot(id)).also {rows.put(it)}
        row.put("username",account.profile.username)
        if(activate) body.put("active",id)
        if(before!=body.toString()) save(body)
    }
    fun select(id: String) = synchronized(lock) {
        val body=read();require(entries().any {it.id==id})
        require((0 until body.getJSONArray("accounts").length()).map(body.getJSONArray("accounts")::getJSONObject).none {it.getString("id")==id && it.optBoolean("removing",false)})
        val saved=SessionVault(context,owner=id).read() ?: throw ApiFailure(ApiFailure.Kind.STORAGE)
        require(saved.session.userId==id)
        if(body.optString("active")!=id) save(body.put("active",id))
    }
    internal fun removing(id: String) = synchronized(lock) {
        val body=read();val rows=body.getJSONArray("accounts")
        (0 until rows.length()).map(rows::getJSONObject).firstOrNull {it.getString("id")==id}?.put("removing",true)
        save(body)
    }
    internal fun forget(id: String) = synchronized(lock) {
        val body=read();val rows=body.getJSONArray("accounts")
        val keep=(0 until rows.length()).map(rows::getJSONObject).filterNot {it.getString("id")==id}
        body.put("accounts",JSONArray().apply {keep.forEach {put(it)}})
        if(body.optString("active")==id) body.put("active",keep.firstOrNull()?.getString("id") ?: JSONObject.NULL)
        save(body)
    }
    fun ordered(): List<Entry> = synchronized(lock) {
        val body=read();val items=entries();val last=items.indexOfFirst {it.id==body.optString("cursor")}
        if(last<0)items else items.drop(last+1)+items.take(last+1)
    }
    fun worked(id: String) = synchronized(lock) {val body=read();save(body.put("cursor",id))}
}
