package dev.kaankaya.instagramtracker

import java.net.URLDecoder
import org.json.JSONObject

/** Protocol identifiers only. No variables, headers or field values are retained. */
class WebDocumentFacts private constructor(val document: String?, val operation: String?,
    private val variableTypes: JSONObject? = null, private val incomplete: Boolean = false) {
    fun json() = JSONObject().put("document",document ?: JSONObject.NULL).put("operation",operation ?: JSONObject.NULL)
        .put("variableTypes",variableTypes ?: JSONObject.NULL).put("variablesIncomplete",incomplete)
    companion object {
        fun inspect(raw: String): WebDocumentFacts {
            if(raw.length>WebGraphqlForm.MAX_BODY) return WebDocumentFacts(null,null)
            return try {
                var document: String?=null;var operation: String?=null;var variables: String?=null
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
                    if(key=="variables")variables=pair[1]
                }
                if(document!=AuthPlatformCodeEntry.DOCUMENT || operation!=AuthPlatformCodeEntry.OPERATION)return WebDocumentFacts(document,operation)
                val types=JSONObject();var incomplete=false
                try {
                    val values=JSONObject(URLDecoder.decode(requireNotNull(variables),"UTF-8"))
                    for(key in values.keys()) {
                        if(types.length()>=8 || !AuthPlatformCodeEntry.fieldName.matches(key)){incomplete=true;continue}
                        types.put(key,when(values.get(key)) {
                            JSONObject.NULL->"NULL"
                            is String->"STRING"
                            is Boolean->"BOOLEAN"
                            is Number->"NUMBER"
                            is JSONObject->"OBJECT"
                            is org.json.JSONArray->"ARRAY"
                            else->{incomplete=true;"UNKNOWN"}
                        })
                    }
                }catch(_: Exception){incomplete=true}
                WebDocumentFacts(document,operation,types,incomplete)
            } catch (_: Exception) {WebDocumentFacts(null,null)}
        }
    }
}
