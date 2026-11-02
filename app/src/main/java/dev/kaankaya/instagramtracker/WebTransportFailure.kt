package dev.kaankaya.instagramtracker

import java.io.IOException

/** Finite transport facts only; SDK messages may contain credentials or request URLs. */
internal class WebTransportFailure(val reason: Reason, val networkCode: Int? = null) : IOException("Web transport failed") {
    enum class Reason { START, NETWORK, TIMEOUT }
}
