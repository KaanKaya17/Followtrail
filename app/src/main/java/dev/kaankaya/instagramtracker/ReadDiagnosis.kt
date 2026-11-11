package dev.kaankaya.instagramtracker

enum class ReadKind {
    PROFILE, FOLLOWERS, FOLLOWING;
    companion object {
        fun list(direction: FollowDirection) = if (direction == FollowDirection.FOLLOWERS) FOLLOWERS else FOLLOWING
    }
    fun title() = when (this) {
        PROFILE -> R.string.read_health_profile
        FOLLOWERS -> R.string.list_followers
        FOLLOWING -> R.string.list_following
    }
}

enum class ReadReason(val label: Int, val compatibility: Boolean = false) {
    FORMAT(R.string.read_health_format, true), IDENTITY(R.string.read_health_identity, true),
    ROUTE(R.string.read_health_route, true), CURSOR(R.string.read_health_cursor),
    PARTIAL(R.string.read_health_partial), SERVER(R.string.read_health_server),
    CONNECTION(R.string.read_health_connection), TARGET(R.string.read_health_target),
    LIMITED(R.string.read_health_limited), SESSION(R.string.read_health_session),
    VERIFICATION(R.string.read_health_verification), OTHER(R.string.read_health_other);

    companion object {
        fun of(failure: ApiFailure, own: Boolean, continuation: Boolean): ReadReason? {
            val code = failure.http
            return when {
                failure.kind in setOf(ApiFailure.Kind.CANCELLED, ApiFailure.Kind.TOO_SOON, ApiFailure.Kind.BUDGET) -> null
                failure.kind == ApiFailure.Kind.LIMITED -> LIMITED
                failure.kind == ApiFailure.Kind.SESSION_EXPIRED -> SESSION
                failure.kind == ApiFailure.Kind.VERIFICATION -> VERIFICATION
                continuation && (code == 400 || code == 404) -> CURSOR
                code == 405 || own && (code == 404 || code == 410) -> ROUTE
                code == 404 || code == 410 || failure.kind == ApiFailure.Kind.NOT_FOUND -> TARGET
                code != null && code >= 500 -> SERVER
                failure.kind == ApiFailure.Kind.WRONG_ACCOUNT -> IDENTITY
                failure.kind == ApiFailure.Kind.INVALID_RESPONSE -> FORMAT
                failure.kind == ApiFailure.Kind.CONNECTION -> CONNECTION
                else -> OTHER
            }
        }
    }
}

data class ReadDiagnosis(val reason: ReadReason, val count: Int, val http: Int?, val at: Long) {
    val paused get() = count >= 3
    companion object {
        fun next(previous: ReadDiagnosis?, reason: ReadReason, http: Int?, now: Long): ReadDiagnosis {
            if (previous?.paused == true) return previous
            val count = if (!reason.compatibility) 0 else if (previous?.reason == reason) previous.count + 1 else 1
            return ReadDiagnosis(reason, count, http, now)
        }
    }
}

internal fun FollowListSource.readDiagnosed(gate: RequestGate, session: Session, target: String,
    direction: FollowDirection, cursor: String?): FollowPage {
    try {
        val page = read(session, target, direction, cursor)
        if (page.target != target || page.direction != direction || page.inputCursor != cursor)
            throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        return page
    } catch (failure: ApiFailure) {
        ReadReason.of(failure, session.userId == target, cursor != null)?.let {
            gate.forAccount(session.userId).recordRead(ReadKind.list(direction), it, failure.http)
        }
        throw failure
    }
}

internal fun RequestGate.committedRead(direction: FollowDirection, run: FollowRun) {
    val kind = ReadKind.list(direction)
    if (run.comparable()) readSucceeded(kind)
    else if (run.coverage() != FollowCoverage.MORE) recordRead(kind, ReadReason.PARTIAL)
}
