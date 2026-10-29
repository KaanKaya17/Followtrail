package dev.kaankaya.instagramtracker

fun ApiFailure.userMessage(context: android.content.Context): String = context.getString(when (kind) {
        ApiFailure.Kind.MOBILE_REQUIRED -> R.string.ui_lists_require_a_mobile_connection_go_to
        ApiFailure.Kind.CAPACITY -> R.string.ui_the_device_storage_limit_for_this_read
        ApiFailure.Kind.SESSION_EXPIRED -> R.string.ui_your_connection_has_expired_reconnect_your_account
        ApiFailure.Kind.VERIFICATION -> R.string.ui_instagram_requires_additional_verification_reconnect_to_complete
        ApiFailure.Kind.LIMITED -> R.string.ui_instagram_is_currently_limiting_refreshes_try_again
        ApiFailure.Kind.TOO_SOON -> R.string.ui_wait_a_few_seconds_before_another_request
        ApiFailure.Kind.CONNECTION -> R.string.ui_could_not_connect_check_your_internet_connection
        ApiFailure.Kind.CANCELLED -> R.string.ui_the_action_was_cancelled_no_automatic_retry
        ApiFailure.Kind.INVALID_RESPONSE -> R.string.ui_instagram_did_not_return_counts_in_the
        ApiFailure.Kind.WRONG_ACCOUNT -> R.string.ui_account_identity_did_not_match_remove_the
        ApiFailure.Kind.NOT_FOUND -> R.string.ui_the_profile_was_not_found_or_is
        ApiFailure.Kind.STORAGE -> R.string.ui_could_not_open_secure_storage_on_this
    })

/** Translate known app-owned diagnostic labels; retain any other recorded signal verbatim. */
fun RequestGate.limitReasonMessage(context: android.content.Context): String {
    val source = limitReason()
    val resource = when (source) {
        InstagramResponsePolicy.Restriction.WAIT.label -> R.string.limit_wait_signal
        InstagramResponsePolicy.Restriction.FEEDBACK.label -> R.string.limit_feedback_signal
        "HTTP 429 / prototip sayfası" -> R.string.limit_prototype_signal
        "HTTP 429 / Instagram sayfa kaynağı" -> R.string.web_compare_page_limit_signal
        "Önceki sürümde kaydedilen sınırlama" -> R.string.limit_legacy_signal
        else -> return source
    }
    return context.getString(resource)
}
