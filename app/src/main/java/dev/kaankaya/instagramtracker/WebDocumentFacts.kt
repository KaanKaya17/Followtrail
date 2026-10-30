package dev.kaankaya.instagramtracker

import java.net.URLDecoder
import org.json.JSONObject

/** Protocol identifiers only. No variables, headers or field values are retained. */
class WebDocumentFacts private constructor(val document: String?, val operation: String?) {
    fun json() = JSONObject().put("document",document ?: JSONObject.NULL).put("operation",operation ?: JSONObject.NULL)
    companion object {
        fun inspect(raw: String): WebDocumentFacts {
            if(raw.length>WebLoginContract.MAX_BODY) return WebDocumentFacts(null,null)
            return try {
                var document: String?=null;var operation: String?=null
                val seen=hashSetOf<String>()
                for (part in raw.split('&')) {
                    val pair=part.split('=',limit=2)
                    if(pair.size!=2) return WebDocumentFacts(null,null)
                    val key=URLDecoder.decode(pair[0],"UTF-8")
                    if(!seen.add(key)) return WebDocumentFacts(null,null)
                    if(key=="doc_id" || key=="fb_api_req_friendly_name") {
                        val value=URLDecoder.decode(pair[1],"UTF-8")
                        if(key=="doc_id") document=value.takeIf {it.matches(Regex("[1-9][0-9]{0,29}"))}
                        else operation=value.takeIf {it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,119}(Query|Mutation|Subscription)"))}
                    }
                }
                WebDocumentFacts(document,operation)
            } catch (_: Exception) {WebDocumentFacts(null,null)}
        }
    }
}
