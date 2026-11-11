package dev.kaankaya.instagramtracker

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/** Values copied by the user from Instagram's account information screen. */
data class ProfileAbout(val joined: String? = null, val country: String? = null, val usernameChanges: Int? = null) {
    init {
        if (joined != null) {
            require(joined.matches(Regex("20[0-9]{2}-(0[1-9]|1[0-2])")) && joined >= "2010-10")
        }
        require(country == null || country.length in 1..80 && country.trim() == country &&
            country.none { it < ' ' || it == '\u007f' })
        require(usernameChanges == null || usernameChanges in 0..1_000_000)
        require(joined != null || country != null || usernameChanges != null)
    }

    fun monthLabel(locale: Locale): String? = joined?.let {
        val utc = TimeZone.getTimeZone("UTC")
        val month = SimpleDateFormat("yyyy-MM", Locale.ROOT).apply { timeZone = utc }.parse(it)!!
        SimpleDateFormat("MMMM yyyy", locale).apply { timeZone = utc }.format(month)
    }

    fun encode() = JSONObject().put("joined", joined).put("country", country).put("usernameChanges", usernameChanges)

    companion object {
        fun decode(value: JSONObject): ProfileAbout {
            require(value.keys().asSequence().all { it in setOf("joined", "country", "usernameChanges") })
            return ProfileAbout(value.opt("joined")?.let { it as String }, value.opt("country")?.let { it as String },
                value.opt("usernameChanges")?.let { it as Int })
        }
    }
}
