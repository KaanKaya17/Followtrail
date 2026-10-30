package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject

/** Observed protocol identity, not permission to send a request. */
internal object AuthPlatformCodeEntry {
    const val DOCUMENT="34414353874878894"
    const val OPERATION="AuthPlatformCodeEntryViewQuery"
    val fieldName=Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
    fun schema(raw: String): JSONObject {
        val unavailable=JSONObject().put("available",false)
        if(raw.length>2048)return unavailable
        return try {
            val value=JSONObject(raw)
            if(value.opt("document")!=DOCUMENT || value.opt("operation")!=OPERATION || value.opt("kind")!="query")return unavailable
            val incomplete=value.get("incomplete");require(incomplete is Boolean)
            fun names(key: String): JSONArray {
                val input=value.getJSONArray(key);require(input.length()<=8)
                return JSONArray((0 until input.length()).map {index->
                    val name=input.get(index);require(name is String && fieldName.matches(name));name
                }.distinct())
            }
            JSONObject().put("available",true).put("document",DOCUMENT).put("operation",OPERATION).put("kind","query")
                .put("arguments",names("arguments")).put("rootFields",names("rootFields")).put("incomplete",incomplete)
        }catch(_: Exception){unavailable}
    }
}
