package dev.kaankaya.instagramtracker

/** A missing username cannot be replaced by a display name or a guessed account. */
object ProfileLinks {
    fun url(username: String?): String? = username?.takeIf(ProfileInput::validUsername)?.let { "https://www.instagram.com/$it/" }
}
