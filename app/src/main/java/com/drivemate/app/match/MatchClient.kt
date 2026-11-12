package com.drivemate.app.match

import com.drivemate.core.CallMentions
import com.drivemate.core.Inbox
import com.drivemate.core.MeetupProposal
import com.drivemate.core.OutgoingMessage
import com.drivemate.core.PlaceSuggestions
import com.drivemate.core.ReportRequest
import com.drivemate.core.Recommendation
import com.drivemate.core.UserUpdate
import com.drivemate.core.VoiceCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The matching server's HTTP API (server/.../Application.kt). Calls throw [IOException]
 * when it can't be reached, and [NotRegistered] when it doesn't know this car (it keeps
 * users in memory, so after a restart the car has to send its pattern again).
 */
class MatchClient(private val baseUrl: String) {

    class NotRegistered : IOException("unknown to the server")

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun send(userId: String, user: UserUpdate) {
        request("PUT", "/users/$userId", json.encodeToString(user))
    }

    suspend fun recommendations(userId: String): List<Recommendation> =
        json.decodeFromString(request("GET", "/users/$userId/recommendations", null))

    suspend fun forget(userId: String) {
        request("DELETE", "/users/$userId", null)
    }

    suspend fun inbox(userId: String): Inbox = json.decodeFromString(request("GET", "/users/$userId/inbox", null))

    suspend fun like(userId: String, other: String) {
        request("PUT", "/users/$userId/likes/$other", "")
    }

    /** "다음에": [other] isn't recommended again. */
    suspend fun pass(userId: String, other: String) {
        request("PUT", "/users/$userId/passes/$other", "")
    }

    suspend fun places(userId: String, other: String): PlaceSuggestions =
        json.decodeFromString(request("GET", "/users/$userId/chats/$other/places", null))

    suspend fun propose(userId: String, other: String, placeId: String) {
        request("PUT", "/users/$userId/chats/$other/meetup", json.encodeToString(MeetupProposal(placeId)))
    }

    suspend fun accept(userId: String, other: String) {
        request("POST", "/users/$userId/chats/$other/meetup/accept", "")
    }

    suspend fun block(userId: String, other: String) {
        request("PUT", "/users/$userId/blocks/$other", "")
    }

    suspend fun report(userId: String, other: String, reason: String) {
        request("POST", "/users/$userId/reports/$other", json.encodeToString(ReportRequest(reason)))
    }

    suspend fun message(userId: String, other: String, text: String) {
        request("POST", "/users/$userId/chats/$other", json.encodeToString(OutgoingMessage(text)))
    }

    /** Asks a match for a 보이스톡. */
    suspend fun askCall(userId: String, other: String): VoiceCall =
        json.decodeFromString(request("POST", "/users/$userId/calls/$other", ""))

    suspend fun answerCall(userId: String, callId: String): VoiceCall =
        json.decodeFromString(request("POST", "/users/$userId/calls/$callId/answer", ""))

    suspend fun endCall(userId: String, callId: String) {
        request("DELETE", "/users/$userId/calls/$callId", null)
    }

    /** The meeting places the driver named on the call (only which ones, never their words). */
    suspend fun mentionPlaces(userId: String, callId: String, placeIds: List<String>) {
        request("POST", "/users/$userId/calls/$callId/mentions", json.encodeToString(CallMentions(placeIds)))
    }

    /** Where the call's voice goes both ways (a WebSocket). */
    fun callAudioUrl(userId: String, callId: String) =
        baseUrl.trimEnd('/').replaceFirst("http", "ws") + "/users/$userId/calls/$callId/audio"

    private suspend fun request(method: String, path: String, body: String?): String = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 70_000 // a sleeping free-tier server takes up to a minute to wake
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code == 404) throw NotRegistered()
            if (code !in 200..299) throw IOException("$method $path: HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
