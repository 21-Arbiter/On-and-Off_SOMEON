package com.drivemate.server

import com.drivemate.core.CallMentions
import com.drivemate.core.MeetupProposal
import com.drivemate.core.OutgoingMessage
import com.drivemate.core.ReportRequest
import com.drivemate.core.UserUpdate
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

/**
 * Matching server. Cars send their driver's profile and life pattern (never trips), ask
 * who fits them, show interest, and message the people they matched with. Listens on
 * 127.0.0.1 by default, which the emulator reaches as 10.0.2.2; Render sets HOST and PORT.
 */
fun main() {
    val host = System.getenv("HOST") ?: "127.0.0.1"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, host = host, port = port) { module() }.start(wait = true)
}

private val json = Json { ignoreUnknownKeys = true }
private val startedAt = java.time.Instant.now().toString()
private val ID = Regex("[A-Za-z0-9-]{8,64}")
private const val MAX_BODY = 64 * 1024
private const val MAX_MESSAGE = 200
private val LOOPBACK = setOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost")
private val log = org.slf4j.LoggerFactory.getLogger("com.drivemate.server")

fun Application.module(users: Users = Users.withDemoUsers(), demo: DemoUsers = DemoUsers(users, this), calls: Calls = Calls(users, this)) {
    install(WebSockets) {
        pingPeriod = 15.seconds
        maxFrameSize = 64 * 1024L
    }
    routing {
        get("/health") { call.respondText("ok") }
        get("/version") {
            // Render sets RENDER_GIT_COMMIT; tells which commit is live after a push.
            val commit = System.getenv("RENDER_GIT_COMMIT")?.take(7) ?: "local"
            call.respondText("""{"commit":"$commit","startedAt":"$startedAt"}""", ContentType.Application.Json)
        }
        put("/users/{id}") {
            val id = call.id("id") ?: return@put
            if ((call.request.contentLength() ?: 0) > MAX_BODY) return@put call.respond(HttpStatusCode.PayloadTooLarge)
            val user = runCatching { json.decodeFromString<UserUpdate>(call.receiveText()) }.getOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest)
            val joined = users.put(id, user)
            users.touch(id)
            if (joined) demo.onJoined(id)
            call.respond(HttpStatusCode.NoContent)
        }
        // The driver deleted their records: nothing of theirs stays here.
        delete("/users/{id}") {
            val id = call.id("id") ?: return@delete
            calls.endFor(id)
            users.remove(id)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/users/{id}/recommendations") {
            val id = call.known("id", users) ?: return@get
            call.respondText(json.encodeToString(users.recommend(id)), ContentType.Application.Json)
        }
        get("/users/{id}/inbox") {
            val id = call.known("id", users) ?: return@get
            call.respondText(json.encodeToString(users.inbox(id).copy(call = calls.of(id))), ContentType.Application.Json)
        }
        // "다음에": not recommended again; someone new comes in a while
        put("/users/{id}/passes/{other}") {
            val id = call.known("id", users) ?: return@put
            val other = call.known("other", users) ?: return@put
            if (id == other) return@put call.respond(HttpStatusCode.BadRequest)
            users.pass(id, other)
            call.respond(HttpStatusCode.NoContent)
        }
        put("/users/{id}/likes/{other}") {
            val id = call.known("id", users) ?: return@put
            val other = call.known("other", users) ?: return@put
            if (id == other) return@put call.respond(HttpStatusCode.BadRequest)
            if (!call.verifiedOrRefuse(id, users) || users.blocked(id, other)) return@put
            val matched = users.like(id, other)
            demo.onLiked(id, other)
            call.respondText("""{"matched":$matched}""", ContentType.Application.Json)
        }
        post("/users/{id}/chats/{other}") {
            val id = call.known("id", users) ?: return@post
            val other = call.known("other", users) ?: return@post
            val text = runCatching { json.decodeFromString<OutgoingMessage>(call.receiveText()).text.trim() }.getOrNull()
            if (text.isNullOrEmpty() || text.length > MAX_MESSAGE) return@post call.respond(HttpStatusCode.BadRequest)
            if (!call.verifiedOrRefuse(id, users)) return@post
            if (!users.send(id, other, text)) return@post call.respond(HttpStatusCode.Forbidden) // not matched
            demo.onMessage(id, other)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/users/{id}/chats/{other}/places") {
            val id = call.known("id", users) ?: return@get
            val other = call.known("other", users) ?: return@get
            val places = users.places(id, other, PlaceCatalog.places) ?: return@get call.respond(HttpStatusCode.Forbidden)
            call.respondText(json.encodeToString(places), ContentType.Application.Json)
        }
        put("/users/{id}/chats/{other}/meetup") {
            val id = call.known("id", users) ?: return@put
            val other = call.known("other", users) ?: return@put
            val place = runCatching { json.decodeFromString<MeetupProposal>(call.receiveText()) }.getOrNull()?.let { PlaceCatalog.byId(it.placeId) }
                ?: return@put call.respond(HttpStatusCode.BadRequest)
            if (!call.verifiedOrRefuse(id, users)) return@put
            if (!users.propose(id, other, place)) return@put call.respond(HttpStatusCode.Forbidden)
            demo.onProposal(id, other)
            call.respond(HttpStatusCode.NoContent)
        }
        put("/users/{id}/blocks/{other}") {
            val id = call.known("id", users) ?: return@put
            val other = call.known("other", users) ?: return@put
            calls.endFor(id)
            users.block(id, other)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/users/{id}/reports/{other}") {
            val id = call.known("id", users) ?: return@post
            val other = call.known("other", users) ?: return@post
            val reason = runCatching { json.decodeFromString<ReportRequest>(call.receiveText()).reason.trim() }.getOrNull()
            if (reason.isNullOrEmpty() || reason.length > MAX_MESSAGE) return@post call.respond(HttpStatusCode.BadRequest)
            calls.endFor(id)
            users.report(id, other, reason)
            log.info("report: {} about {}: {}", id, other, reason)
            call.respond(HttpStatusCode.NoContent)
        }
        // 보이스톡: ask a match, answer, hang up; the voice itself goes over the socket below.
        post("/users/{id}/calls/{other}") {
            val id = call.known("id", users) ?: return@post
            val other = call.known("other", users) ?: return@post
            if (!call.verifiedOrRefuse(id, users)) return@post
            val voiceCall = calls.ask(id, other) ?: return@post call.respond(HttpStatusCode.Conflict) // not matched, or busy
            demo.onCallAsked(voiceCall, calls)
            call.respondText(json.encodeToString(voiceCall), ContentType.Application.Json, HttpStatusCode.Created)
        }
        post("/users/{id}/calls/{call}/answer") {
            val id = call.known("id", users) ?: return@post
            val callId = call.id("call") ?: return@post
            val answered = calls.answer(callId, id) ?: return@post call.respond(HttpStatusCode.Conflict)
            call.respondText(json.encodeToString(answered), ContentType.Application.Json)
        }
        post("/users/{id}/calls/{call}/mentions") {
            val id = call.known("id", users) ?: return@post
            val callId = call.id("call") ?: return@post
            val places = runCatching { json.decodeFromString<CallMentions>(call.receiveText()).placeIds }.getOrNull()?.mapNotNull(PlaceCatalog::byId)
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            call.respond(if (calls.mention(callId, id, places)) HttpStatusCode.NoContent else HttpStatusCode.Conflict)
        }
        delete("/users/{id}/calls/{call}") {
            val id = call.known("id", users) ?: return@delete
            val callId = call.id("call") ?: return@delete
            if (calls.inCall(callId, id)) calls.end(callId)
            call.respond(HttpStatusCode.NoContent)
        }
        webSocket("/users/{id}/calls/{call}/audio") {
            val id = call.parameters["id"].orEmpty()
            val callId = call.parameters["call"].orEmpty()
            if (!calls.inCall(callId, id)) return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "not in this call"))
            calls.join(callId, id, this)
        }
        // Reports wait here for the operators; only from this machine.
        get("/admin/reports") {
            if (call.request.origin.remoteHost !in LOOPBACK) return@get call.respond(HttpStatusCode.NotFound)
            call.respondText(users.reports().joinToString("\n") { "${it.at} ${it.from} -> ${it.about}: ${it.reason}" })
        }
        // The phone side of verification: the car shows this page's address as a QR code.
        get("/verify/{id}") {
            val id = call.id("id") ?: return@get
            if (id !in users) return@get call.respondText(VerifyPage.unknown(), ContentType.Text.Html, HttpStatusCode.NotFound)
            call.respondText(VerifyPage.form(id, users.nameOf(id), users.isVerified(id)), ContentType.Text.Html)
        }
        post("/verify/{id}") {
            val id = call.id("id") ?: return@post
            if (id !in users) return@post call.respondText(VerifyPage.unknown(), ContentType.Text.Html, HttpStatusCode.NotFound)
            val born = runCatching { LocalDate.parse(call.receiveParameters()["birth"]) }.getOrNull()
                ?: return@post call.respondText(VerifyPage.result(false, "생년월일을 확인해 주세요."), ContentType.Text.Html, HttpStatusCode.BadRequest)
            if (!Verification.oldEnough(born)) {
                return@post call.respondText(VerifyPage.result(false, "SOMEON은 만 ${Verification.MIN_AGE}세 이상만 이용할 수 있어요."), ContentType.Text.Html)
            }
            users.verify(id)
            call.respondText(VerifyPage.result(true, "본인 인증이 완료됐어요. 차 화면으로 돌아가 주세요."), ContentType.Text.Html)
        }
        post("/users/{id}/chats/{other}/meetup/accept") {
            val id = call.known("id", users) ?: return@post
            val other = call.known("other", users) ?: return@post
            call.respond(if (users.accept(id, other)) HttpStatusCode.NoContent else HttpStatusCode.Conflict)
        }
    }
}

/** The path's id, or null after answering 400 for one that isn't a plain id. */
private suspend fun ApplicationCall.id(name: String): String? {
    val id = parameters[name].orEmpty()
    if (ID.matches(id)) return id
    respond(HttpStatusCode.BadRequest)
    return null
}

/** Showing interest, messaging and proposing need a verified identity: 403 "verify" otherwise. */
private suspend fun ApplicationCall.verifiedOrRefuse(id: String, users: Users): Boolean {
    if (users.isVerified(id)) return true
    respondText("verify", status = HttpStatusCode.Forbidden)
    return false
}

/** Like [id], and the user must be known here (404 otherwise: the car should send its pattern again). */
private suspend fun ApplicationCall.known(name: String, users: Users): String? {
    val id = id(name) ?: return null
    if (id !in users) {
        respond(HttpStatusCode.NotFound)
        return null
    }
    if (name == "id") users.touch(id) // the caller
    return id
}
