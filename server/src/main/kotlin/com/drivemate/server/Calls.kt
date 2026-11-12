package com.drivemate.server

import com.drivemate.core.Message
import com.drivemate.core.Place
import com.drivemate.core.VoiceCall
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 보이스톡 between matches. One asks, the other answers, then each joins over a WebSocket:
 * binary frames are their voice (16 kHz mono 16-bit PCM), passed on to the other as they
 * come; text frames say what happened ("live", "ended"). Nothing is recorded. Someone is
 * in one call at a time, and a call nobody answers gives up after [ringMs]. Demo users
 * have no socket: the server talks for them ([listen], [speak]). The conversation keeps
 * that they talked and for how long, or that the call didn't connect, and the meeting
 * places either named ([mention]: each car finds them in its own driver's words; only
 * which places, never what was said, comes here).
 */
class Calls(
    private val users: Users,
    private val scope: CoroutineScope,
    private val ringMs: Long = 30_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val calls = ConcurrentHashMap<String, VoiceCall>()
    private val answeredAt = ConcurrentHashMap<String, Long>()
    private val sockets = ConcurrentHashMap<String, WebSocketSession>()
    private val listeners = ConcurrentHashMap<String, (ByteArray) -> Unit>()
    /** Per call, the places named so far and by whom, first mention first. */
    private val mentions = ConcurrentHashMap<String, MutableMap<String, String>>()

    private fun key(id: String, user: String) = "$id $user"

    /** The call [user] is in or is asked to answer. */
    fun of(user: String): VoiceCall? = calls.values.firstOrNull { user == it.from || user == it.to }

    /** [from] calls [to]; null if they haven't matched or either is already in a call. */
    @Synchronized
    fun ask(from: String, to: String): VoiceCall? {
        if (from == to || !users.matched(from, to) || of(from) != null || of(to) != null) return null
        val call = VoiceCall("call-${UUID.randomUUID()}", from, to)
        calls[call.id] = call
        scope.launch {
            delay(ringMs)
            if (calls[call.id]?.live == false) end(call.id)
        }
        return call
    }

    /** [by] answers; only the one asked can, once. */
    fun answer(id: String, by: String): VoiceCall? {
        val live = calls.computeIfPresent(id) { _, call -> if (call.to == by && !call.live) call.copy(live = true) else call } ?: return null
        if (!live.live || live.to != by) return null
        answeredAt.putIfAbsent(id, now())
        scope.launch { sockets[key(id, live.from)]?.send(Frame.Text("live")) }
        return live
    }

    /** [user] named [places] on the live call [id]; false if they aren't in it. */
    fun mention(id: String, user: String, places: List<Place>): Boolean {
        if (!inCall(id, user) || !live(id)) return false
        val named = mentions.computeIfAbsent(id) { java.util.Collections.synchronizedMap(LinkedHashMap()) }
        places.forEach { named.putIfAbsent(it.id, user) }
        return true
    }

    /** Either side hangs up (or blocks, or leaves): the call ends for both. */
    fun end(id: String) {
        val call = calls.remove(id) ?: return
        val answered = answeredAt.remove(id)
        users.send(call.from, call.to, if (answered == null) "보이스톡 · 연결되지 않음" else "보이스톡 ${length(now() - answered)}", Message.Kind.CALL)
        mentions.remove(id)?.toMap()?.forEach { (placeId, by) ->
            val place = PlaceCatalog.byId(placeId) ?: return@forEach
            users.send(by, call.other(by), place.name, Message.Kind.MENTION, place.id)
        }
        for (user in listOf(call.from, call.to)) {
            listeners.remove(key(id, user))
            val socket = sockets.remove(key(id, user)) ?: continue
            scope.launch {
                runCatching {
                    socket.send(Frame.Text("ended"))
                    socket.close(CloseReason(CloseReason.Codes.NORMAL, "ended"))
                }
            }
        }
    }

    /** Ends whatever call [user] is in. */
    fun endFor(user: String) {
        of(user)?.let { end(it.id) }
    }

    fun inCall(id: String, user: String) = calls[id]?.let { user == it.from || user == it.to } == true

    /** [user]'s side of call [id], until their socket closes; then the call ends. */
    suspend fun join(id: String, user: String, socket: WebSocketSession) {
        val call = calls[id] ?: return socket.close(CloseReason(CloseReason.Codes.NORMAL, "no call"))
        sockets.put(key(id, user), socket)?.close(CloseReason(CloseReason.Codes.NORMAL, "replaced"))
        if (call.live) socket.send(Frame.Text("live"))
        try {
            for (frame in socket.incoming) {
                if (frame is Frame.Binary && calls[id]?.live == true) relay(id, user, frame.readBytes())
            }
        } finally {
            if (sockets.remove(key(id, user), socket)) end(id)
        }
    }

    private suspend fun relay(id: String, from: String, audio: ByteArray) {
        val to = calls[id]?.other(from) ?: return
        listeners[key(id, to)]?.invoke(audio)
        sockets[key(id, to)]?.let { runCatching { it.send(Frame.Binary(true, audio)) } }
    }

    /** What the other side says reaches [onAudio], for a user the server talks for. */
    fun listen(id: String, user: String, onAudio: (ByteArray) -> Unit) {
        listeners[key(id, user)] = onAudio
    }

    /** [user] (one the server talks for) says [audio] to the other side. */
    suspend fun speak(id: String, user: String, audio: ByteArray) = relay(id, user, audio)

    /** Waits up to [timeoutMs] for [user] to join call [id]; false if they don't. */
    suspend fun awaitJoined(id: String, user: String, timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) {
        while (sockets[key(id, user)] == null) {
            if (calls[id] == null) return@withTimeoutOrNull false
            delay(50)
        }
        true
    } ?: false

    fun live(id: String) = calls[id]?.live == true

    /** E.g. "1분 20초", "45초". */
    private fun length(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(1)
        return if (s < 60) "${s}초" else "${s / 60}분 ${s % 60}초"
    }
}
