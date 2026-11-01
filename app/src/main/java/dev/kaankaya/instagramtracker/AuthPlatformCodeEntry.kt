package dev.kaankaya.instagramtracker

import org.json.JSONObject

/** Observed protocol identity, not permission to send a request. */
internal object AuthPlatformCodeEntry {
    const val DOCUMENT="34414353874878894"
    const val OPERATION="AuthPlatformCodeEntryViewQuery"
    val fieldName=Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
    fun schema(raw: String): JSONObject = AuthPlatformSchema.inspect(raw, DOCUMENT, OPERATION, "query")
}
