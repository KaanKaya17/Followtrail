package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject

/** Owns the encrypted payload format; v1 accounts migrate without discarding the saved session. */
object AccountCodec {
    private val legacyHourChoices = listOf(1, 6, 24)
    private fun legacyHours(minutes: Int): Int {
        require(minutes in WatchIntervals.minutes)
        return legacyHourChoices.first { it * 60 >= minutes }
    }
    private fun profile(p: Profile, details: Map<ProfileDetails, Int>): JSONObject = JSONObject().put("pk", p.userId).put("username", p.username)
        .put("follower_count", p.followers).put("following_count", p.following).put("observedAt", p.observedAt)
        .apply {
            p.posts?.let { put("media_count", it) }
            if (p.details != ProfileDetails()) put("detailsRef", details.getValue(p.details))
        }
    private fun profile(json: JSONObject, details: List<ProfileDetails> = emptyList()): Profile {
        val time = json.getLong("observedAt")
        require(time > 0)
        val data = if (!json.has("detailsRef")) ProfileDetails() else {
            val ref = json.get("detailsRef")
            require(ref is Int && ref in details.indices)
            details[ref]
        }
        return ProfileParser.user(json, time).copy(details = data)
    }
    private fun text(field: ProfileText, texts: Map<String, Int>) = JSONObject().put("state", field.state.name).apply {
        field.value?.let { put("valueRef", texts.getValue(it)) }
    }
    private fun text(body: JSONObject, texts: List<String>): ProfileText {
        val state = ProfileTextState.valueOf(body.getString("state"))
        val value = if (body.has("valueRef")) {
            val ref = body.get("valueRef"); require(ref is Int && ref in texts.indices); texts[ref]
        } else null
        require(value == null || (value.length <= ProfileDetailsParser.TEXT_BYTES && value.toByteArray(Charsets.UTF_8).size <= ProfileDetailsParser.TEXT_BYTES))
        return ProfileText(state, value)
    }
    private fun encodeDetails(value: ProfileDetails, texts: Map<String, Int>) = JSONObject().put("biography", text(value.biography, texts))
        .put("pictureId", text(value.pictureId, texts)).put("pictureUrl", text(value.pictureUrl, texts)).apply {
            value.anonymousPicture?.let { put("anonymous", it) }
        }
    private fun decodeDetails(body: JSONObject, texts: List<String>) = ProfileDetails(text(body.getJSONObject("biography"), texts),
        text(body.getJSONObject("pictureId"), texts), text(body.getJSONObject("pictureUrl"), texts),
        if (body.has("anonymous")) body.get("anonymous").also { require(it is Boolean) } as Boolean else null)
    fun encode(account: SavedAccount): String {
        val details = (listOf(account.profile) + account.people.flatMap { it.history }).map { it.details }
            .filter { it != ProfileDetails() }.distinct()
        val texts = details.flatMap { listOfNotNull(it.biography.value, it.pictureId.value, it.pictureUrl.value) }.distinct()
        val detailRefs = details.withIndex().associate { it.value to it.index }
        val textRefs = texts.withIndex().associate { it.value to it.index }
        val people = JSONArray()
        for (person in account.people) people.put(JSONObject().put("note", person.note).put("group", person.group)
            .put("favorite", person.favorite)
            .put("watching", person.watching).put("intervalMinutes", person.intervalMinutes)
            // v4 compatibility for older APKs: round up to their supported interval, never speed up a downgrade.
            .put("intervalHours", legacyHours(person.intervalMinutes)).put("nextCheckAt", person.nextCheckAt)
            .put("history", JSONArray().apply { person.history.forEach { put(profile(it, detailRefs)) } }))
        val result = JSONObject().put("version", 4).put("id", account.session.userId)
            .put("cookie", account.session.cookie).put("ua", account.session.userAgent)
            .apply { account.session.mobileBinding?.let { put("mobileBinding", it) } }
            .put("profile", profile(account.profile, detailRefs)).put("people", people)
            .apply { if (account.automation != ListAutomation()) put("listAutomation", JSONObject()
                .put("enabled", account.automation.enabled).put("targets", JSONArray(account.automation.targets.toList()))
                .put("ownNextCheckAt", account.automation.ownNextCheckAt).put("lastWorkWasList", account.automation.lastWorkWasList)) }
            .apply { if (details.isNotEmpty()) put("profileDetails", JSONArray().apply { details.forEach { put(encodeDetails(it, textRefs)) } })
                if (texts.isNotEmpty()) put("profileTexts", JSONArray(texts))
            }.toString()
        // Validate at the owning boundary on both read and write.
        decode(result)
        return result
    }
    fun decode(text: String): SavedAccount {
        val json = JSONObject(text)
        val version = json.getInt("version")
        require(version in 1..4)
        val detailArray = if (json.has("profileDetails")) json.getJSONArray("profileDetails") else JSONArray()
        require(detailArray.length() <= PeoplePolicy.MAX_PEOPLE * PeoplePolicy.MAX_HISTORY + 1)
        val textArray = if (json.has("profileTexts")) json.getJSONArray("profileTexts") else JSONArray()
        require(textArray.length() <= (PeoplePolicy.MAX_PEOPLE * PeoplePolicy.MAX_HISTORY + 1) * 3)
        val texts = (0 until textArray.length()).map { index ->
            (textArray.get(index) as? String ?: error("Invalid profile text")).also {
                require(it.length <= ProfileDetailsParser.TEXT_BYTES && it.toByteArray(Charsets.UTF_8).size <= ProfileDetailsParser.TEXT_BYTES)
            }
        }
        val details = (0 until detailArray.length()).map { decodeDetails(detailArray.getJSONObject(it), texts) }
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
            .put("observedAt", json.get("observedAt"))) else profile(json.getJSONObject("profile"), details)
        require(own.userId == session.userId)
        val array = if (version == 1) JSONArray() else json.getJSONArray("people")
        require(array.length() <= PeoplePolicy.MAX_PEOPLE)
        val people = (0 until array.length()).map { index ->
            val person = array.getJSONObject(index)
            val note = person.getString("note"); val group = person.getString("group")
            require(note.length <= 500 && group.length <= 40)
            val observations = person.getJSONArray("history")
            require(observations.length() in 1..PeoplePolicy.MAX_HISTORY)
            val history = (0 until observations.length()).map { profile(observations.getJSONObject(it), details) }
            require(history.all { it.userId == history.first().userId })
            require(history.zipWithNext().all { (a, b) -> a.observedAt < b.observedAt })
            val watching = if (version < 3) false else person.get("watching") as? Boolean ?: error("Invalid watch setting")
            val hours = if (version < 3) 1 else person.get("intervalHours").also { require(it is Int && it in legacyHourChoices) } as Int
            val minutes = if (version >= 3 && person.has("intervalMinutes")) {
                val value = person.get("intervalMinutes"); require(value is Int && value in WatchIntervals.minutes)
                require(hours == legacyHours(value)); value
            } else hours * 60
            val nextCheck = if (version < 3) 0L else person.getLong("nextCheckAt")
            require(nextCheck >= 0)
            val favorite = if (!person.has("favorite")) false else person.get("favorite") as? Boolean ?: error("Invalid favorite setting")
            TrackedPerson(history, note, group, watching, minutes, nextCheck, favorite)
        }
        require(people.map { it.latest.userId }.distinct().size == people.size)
        val automation = if (!json.has("listAutomation")) ListAutomation() else {
            val config = json.getJSONObject("listAutomation")
            val enabled = config.get("enabled") as? Boolean ?: error("Invalid automation setting")
            val targetArray = config.getJSONArray("targets"); require(targetArray.length() <= PeoplePolicy.MAX_PEOPLE + 1)
            val targets = (0 until targetArray.length()).map { InstagramIdentity.id(targetArray.get(it)) }
            require(targets.distinct().size == targets.size && targets.all { id -> id == session.userId || people.any { it.latest.userId == id } })
            val next = config.getLong("ownNextCheckAt"); require(next >= 0 && (!enabled || targets.isNotEmpty()))
            val lastList = config.get("lastWorkWasList") as? Boolean ?: error("Invalid automation work state")
            ListAutomation(enabled, targets.toSet(), next, lastList)
        }
        return SavedAccount(session, own, people, automation)
    }
}
