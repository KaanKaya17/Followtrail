package dev.kaankaya.instagramtracker

import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

data class ProfileCandidate(val id: String, val username: String, val name: String)

object ProfileSearch {
    fun fold(value: String): String = Normalizer.normalize(value,Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('ı','i')
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    fun matches(text: String, query: String): Boolean {
        val terms=fold(ProfileInput.username(query) ?: query).split(Regex("\\s+")).filter {it.isNotEmpty()}
        val source=fold(text)
        return terms.all {source.contains(it)}
    }
    fun query(value: String): String {
        val trimmed=value.trim()
        val q=if(trimmed.startsWith("https://") || trimmed.startsWith('@')) ProfileInput.username(trimmed) else trimmed
        return q?.takeIf {it.length in 2..100 && it.none { c->c.code<32 }} ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
    }
    fun parse(body: JSONObject): List<ProfileCandidate> {
        if(body.optString("status")!="ok")throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val users=body.optJSONArray("users") ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        if(users.length()>1000)throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val results=linkedMapOf<String,ProfileCandidate>()
        for(i in 0 until users.length()) {
            val user=users.optJSONObject(i) ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            val id=InstagramIdentity.id(user.opt("pk"))
            val username=(user.opt("username") as? String)?.takeIf(ProfileInput::validUsername) ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            val name=(user.opt("full_name") as? String)?.takeIf {it.length<=1000}.orEmpty()
            if(results.size<20)results[id]=ProfileCandidate(id,username,name)
        }
        return results.values.toList()
    }
}
