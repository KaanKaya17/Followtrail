package dev.kaankaya.instagramtracker

import org.json.JSONObject

enum class ProfileTextState { MISSING, NULL, VALUE, UNAVAILABLE }

/** VALUE("") is an observed empty biography; absence/null is never deletion evidence. */
data class ProfileText(val state: ProfileTextState = ProfileTextState.MISSING, val value: String? = null) {
    init { require((state == ProfileTextState.VALUE) == (value != null)) }
    companion object { fun known(value: String) = ProfileText(ProfileTextState.VALUE, value) }
}

data class ProfileDetails(val biography: ProfileText = ProfileText(), val pictureId: ProfileText = ProfileText(),
    val pictureUrl: ProfileText = ProfileText(), val anonymousPicture: Boolean? = null, val links: List<ProfileSite> = emptyList(),
    val privateAccount: Boolean? = null)

object ProfileDetailsParser {
    // Capture budgets, not Instagram field limits. Oversize optional data cannot break count reads.
    const val TEXT_BYTES = 8192
    private fun text(body: JSONObject, key: String): ProfileText = when {
        !body.has(key) -> ProfileText()
        body.isNull(key) -> ProfileText(ProfileTextState.NULL)
        else -> when (val value = body.opt(key)) {
            is String -> if (value.length <= TEXT_BYTES && value.toByteArray(Charsets.UTF_8).size <= TEXT_BYTES)
                ProfileText.known(value) else ProfileText(ProfileTextState.UNAVAILABLE)
            else -> ProfileText(ProfileTextState.UNAVAILABLE)
        }
    }
    fun parse(body: JSONObject) = ProfileDetails(text(body, "biography"), text(body, "profile_pic_id"),
        text(body, "profile_pic_url"), body.opt("has_anonymous_profile_picture") as? Boolean, ProfileLinks.parse(body),
        body.opt("is_private") as? Boolean)
}

enum class ProfileChangeField { USERNAME, BIOGRAPHY, PICTURE }
data class ProfileEdit(val profileId: String, val username: String, val field: ProfileChangeField,
    val before: String, val after: String, val previousAt: Long, val observedAt: Long) {
    val key get() = "profile:$profileId:${field.name}:$previousAt:$observedAt"
}

/** The single comparison owner for history, activity feed and notifications. No network operations. */
object ProfileChanges {
    private fun value(profile: Profile, field: ProfileChangeField): String? = when (field) {
        ProfileChangeField.USERNAME -> profile.username
        ProfileChangeField.BIOGRAPHY -> profile.details.biography.value
        ProfileChangeField.PICTURE -> when {
            profile.details.anonymousPicture == true -> "anonymous"
            !profile.details.pictureId.value.isNullOrEmpty() -> "id:${profile.details.pictureId.value}"
            else -> null
        }
    }
    fun pictureComparable(profile: Profile) = value(profile, ProfileChangeField.PICTURE) != null
    fun history(history: List<Profile>): List<ProfileEdit> {
        if (history.isEmpty()) return emptyList()
        require(history.all { it.userId == history.first().userId })
        require(history.zipWithNext().all { (a, b) -> a.observedAt < b.observedAt })
        val previous = mutableMapOf<ProfileChangeField, Pair<String, Long>>()
        val result = mutableListOf<ProfileEdit>()
        for (profile in history) for (field in ProfileChangeField.entries) {
            val current = value(profile, field) ?: continue
            val before = previous[field]
            if (before != null && before.first != current) result += ProfileEdit(profile.userId, profile.username,
                field, before.first, current, before.second, profile.observedAt)
            previous[field] = current to profile.observedAt
        }
        return result
    }
    fun latest(history: List<Profile>, profile: Profile): List<ProfileEdit> =
        history(history + profile).filter { it.observedAt == profile.observedAt }
}
