package dev.kaankaya.instagramtracker

import java.net.URLDecoder
import okhttp3.FormBody

/** Shared, format-aware parsing only. Parsing never authorizes a document. */
internal object WebGraphqlForm {
    const val MAX_BODY=65536
    val PATHS=setOf("/api/graphql","/api/graphql/","/graphql/query/","/graphql_www")
    private val fields=setOf("av","__d","__user","__a","__req","__hs","dpr","__ccg","__rev","__s","__hsi",
        "__dyn","__csr","__hsdp","__hblp","__sjsp","__comet_req","lsd","jazoest","__spin_r","__spin_b",
        "__spin_t","__crn","qpl_active_flow_ids","fb_api_caller_class","fb_api_req_friendly_name","server_timestamps",
        "variables","doc_id","fb_api_analytics_tags")
    fun parse(raw: String): LinkedHashMap<String,String> {
        require(raw.length<=MAX_BODY && raw.toByteArray(Charsets.UTF_8).size<=MAX_BODY)
        val pairs=linkedMapOf<String,String>()
        for(part in raw.split('&')) {
            val pair=part.split('=',limit=2);require(pair.size==2)
            val key=URLDecoder.decode(pair[0],"UTF-8");val value=URLDecoder.decode(pair[1],"UTF-8")
            require(key in fields && key !in pairs);pairs[key]=value
        }
        return pairs
    }
    fun body(pairs: Map<String,String>): FormBody=FormBody.Builder().apply {pairs.forEach {(key,value)->add(key,value)}}.build()
}
