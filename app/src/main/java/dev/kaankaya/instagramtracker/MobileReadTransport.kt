package dev.kaankaya.instagramtracker

import okhttp3.Call
import org.json.JSONObject

/** Mobile protocol adapter; exchange ownership and response policy are shared with web reads. */
class MobileReadTransport(gate: RequestGate, sessions: MobileSessions,
    client: Call.Factory = client()) {
    companion object { fun client() = PrivateReadTransport.client() }
    private val transport = PrivateReadTransport(gate, sessions, client, setOf("i.instagram.com"),
        { state: MobileSession, headers, _ -> state.updated(headers) })
    fun cancel() = transport.cancel()
    fun <T> read(reference: Session, connectionTest: Boolean = false, resumeAutomation: Boolean = true,
        request: (MobileSession, Long) -> okhttp3.Request, parse: (JSONObject, Long) -> T): T =
        transport.read(reference, connectionTest, resumeAutomation, request, parse)
}
