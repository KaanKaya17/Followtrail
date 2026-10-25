package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject

/** Owns the encrypted payload format; v1 accounts migrate without discarding the saved session. */
object AccountCodec {
    private fun profile(p: Profile): JSONObject = JSONObject().put("pk", p.userId).put("username", p.username)
        .put("follower_count", p.followers).put("following_count", p.following).put("observedAt", p.observedAt)
        .apply { p.posts?.let { put("media_count", it) } }
    private fun profile(json: JSONObject): Profile {
        val time = json.getLong("observedAt")
        require(time > 0)
        return ProfileParser.user(json, time)
    }
    fun encode(account: SavedAccount): String {
        val people = JSONArray()
        for (person in account.people) people.put(JSONObject().put("note", person.note).put("group", person.group)
            .put("favorite", person.favorite)
            .put("watching", person.watching).put("intervalHours", person.intervalHours).put("nextCheckAt", person.nextCheckAt)
            .put("history", JSONArray().apply { person.history.forEach { put(profile(it)) } }))
        val result = JSONObject().put("version", 4).put("id", account.session.userId)
            .put("cookie", account.session.cookie).put("ua", account.session.userAgent)
            .apply { account.session.mobileBinding?.let { put("mobileBinding", it) } }
            .put("profile", profile(account.profile)).put("people", people).toString()
        // Validate at the owning boundary on both read and write.
        decode(result)
        return result
    }
    fun decode(text: String): SavedAccount {
        val json = JSONObject(text)
        val version = json.getInt("version")
        require(version in 1..4)
        val binding = if (version >= 4 && json.has("mobileBinding")) json.getString("mobileBinding") else null
        val session = if (binding != null) {
            require(binding.matches(Regex("[a-f0-9]{32}")))
            val id = json.getString("id"); val ua = json.getString("ua")
            require(id.matches(Regex("[1-9][0-9]{0,29}")) && ua.isNotBlank() && ua.none { it.code < 32 || it.code == 127 })
            require(json.getString("cookie").isEmpty())
            Session(id, "", ua, binding)
        } else SessionCookies.fromHeader(json.getString("cookie"), json.getString("ua"))
            ?: throw IllegalArgumentException("Invalid session")
        require(session.userId == json.getString("id"))
        val own = if (version == 1) profile(JSONObject().put("pk", session.userId).put("username", json.get("username"))
            .put("follower_count", json.get("followers")).put("following_count", json.get("following"))
            .put("observedAt", json.get("observedAt"))) else profile(json.getJSONObject("profile"))
        require(own.userId == session.userId)
        val array = if (version == 1) JSONArray() else json.getJSONArray("people")
        require(array.length() <= PeoplePolicy.MAX_PEOPLE)
        val people = (0 until array.length()).map { index ->
            val person = array.getJSONObject(index)
            val note = person.getString("note"); val group = person.getString("group")
            require(note.length <= 500 && group.length <= 40)
            val observations = person.getJSONArray("history")
            require(observations.length() in 1..PeoplePolicy.MAX_HISTORY)
            val history = (0 until observations.length()).map { profile(observations.getJSONObject(it)) }
            require(history.all { it.userId == history.first().userId })
            require(history.zipWithNext().all { (a, b) -> a.observedAt < b.observedAt })
            val watching = if (version < 3) false else person.get("watching") as? Boolean ?: error("Invalid watch setting")
            val hours = if (version < 3) 1 else person.getInt("intervalHours")
            val nextCheck = if (version < 3) 0L else person.getLong("nextCheckAt")
            require(hours in listOf(1, 6, 24) && nextCheck >= 0)
            val favorite = if (!person.has("favorite")) false else person.get("favorite") as? Boolean ?: error("Invalid favorite setting")
            TrackedPerson(history, note, group, watching, hours, nextCheck, favorite)
        }
        require(people.map { it.latest.userId }.distinct().size == people.size)
        return SavedAccount(session, own, people)
    }
}
