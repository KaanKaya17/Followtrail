package dev.kaankaya.instagramtracker

import org.json.JSONObject

/** An exact recorded observation. Missing fields stay unknown. */
internal data class ProfileComparisonRecord(
    val at: Long,
    val username: String? = null,
    val name: String? = null,
    val bio: String? = null,
    val links: List<ProfileSite>? = null,
    val followers: Long? = null,
    val following: Long? = null,
    val posts: Long? = null,
    val picture: String? = null,
    val image: String? = null,
)

internal object ProfileComparison {
    fun records(profiles: List<Profile>, archive: List<JSONObject>, checks: List<CheckEvent>): List<ProfileComparisonRecord> {
        val byTime = mutableMapOf<Long, ProfileComparisonRecord>()
        val photos = mutableMapOf<String, String>()
        for (entry in archive) {
            val picture = entry.optString("picture").ifEmpty { null }
            val image = entry.optString("image").ifEmpty { null }
            if (picture != null && image != null) photos[picture] = image
            for (at in listOf(entry.getLong("at"), entry.getLong("lastSeen"))) {
                byTime[at] = ProfileComparisonRecord(at, bio = entry.opt("bio") as? String, picture = picture, image = image)
            }
        }
        for (check in checks) {
            val at = check.observedAt ?: continue
            if (check.kind != CheckKind.PROFILE) continue
            val existing = byTime[at] ?: ProfileComparisonRecord(at)
            byTime[at] = existing.copy(username = check.username, followers = check.afterFollowers,
                following = check.afterFollowing, posts = check.afterPosts)
        }
        for (profile in profiles) {
            val picture = ProfileChanges.pictureIdentity(profile)
            val at = profile.observedAt
            byTime[at] = ProfileComparisonRecord(at, profile.username, profile.name, profile.details.biography.value,
                profile.details.links.takeIf { it.isNotEmpty() }, profile.followers, profile.following, profile.posts,
                picture, if (picture != null) photos[picture] else byTime[at]?.image)
        }
        return byTime.values.filter { it.at > 0 }.sortedBy { it.at }
    }
}
