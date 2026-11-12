package com.drivemate.server

import com.drivemate.core.CommuteScenario
import com.drivemate.core.Inbox
import com.drivemate.core.Message
import com.drivemate.core.PatternFinder
import com.drivemate.core.Profile
import com.drivemate.core.UserUpdate
import com.drivemate.core.VoiceCall
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.delete
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsTest {

    private val pattern = PatternFinder.find(CommuteScenario.parse(javaClass.getResource("/scenarios/commute_week_A.csv")!!.readText().lines()).trips)
    private val minjun = "car-minjun-01"
    private val seoyeon = "car-seoyeon-01"

    private fun ApplicationTestBuilder.server() {
        application {
            val users = Users.withDemoUsers()
            module(users, DemoUsers(users, this, likeFirstMs = 60_000, likeBackMs = 0, replyMs = 0, answerMs = 0))
        }
    }

    /** A verified car with the commute of scenario A. */
    private suspend fun HttpClient.join(id: String, name: String) {
        put("/users/$id") { setBody(Json.encodeToString(UserUpdate(Profile(name, 30), pattern))) }
        post("/verify/$id") { setBody(FormDataContent(Parameters.build { append("birth", "1995-03-14") })) }
    }

    private suspend fun HttpClient.match(a: String, b: String) {
        put("/users/$a/likes/$b")
        put("/users/$b/likes/$a")
    }

    private suspend fun HttpClient.inbox(id: String) = Json.decodeFromString<Inbox>(get("/users/$id/inbox").bodyAsText())

    private suspend fun HttpClient.ask(from: String, to: String) = post("/users/$from/calls/$to")

    @Test
    fun `a matched pair calls, one asks and the other answers, and each hears the other`() = testApplication {
        server()
        client.join(minjun, "민준")
        client.join(seoyeon, "서연")
        client.match(minjun, seoyeon)

        val asked = client.ask(minjun, seoyeon)
        assertEquals(HttpStatusCode.Created, asked.status)
        val call = Json.decodeFromString<VoiceCall>(asked.bodyAsText())
        assertEquals(call.copy(live = false), client.inbox(seoyeon).call)

        val ws = createClient { install(WebSockets) }
        val caller = ws.webSocketSession("/users/$minjun/calls/${call.id}/audio")
        assertEquals(HttpStatusCode.OK, client.post("/users/$seoyeon/calls/${call.id}/answer").status)
        assertEquals("live", withTimeout(5_000) { (caller.incoming.receive() as Frame.Text).readText() })
        assertTrue(client.inbox(minjun).call!!.live)

        val callee = ws.webSocketSession("/users/$seoyeon/calls/${call.id}/audio")
        assertEquals("live", withTimeout(5_000) { (callee.incoming.receive() as Frame.Text).readText() })
        val hello = ByteArray(640) { it.toByte() }
        caller.send(Frame.Binary(true, hello))
        assertArrayEquals(hello, withTimeout(5_000) { (callee.incoming.receive() as Frame.Binary).readBytes() })

        client.delete("/users/$seoyeon/calls/${call.id}")
        assertEquals("ended", withTimeout(5_000) { (caller.incoming.receive() as Frame.Text).readText() })
        assertEquals(null, client.inbox(minjun).call)
        // The conversation keeps that they talked, and how long.
        val record = client.inbox(seoyeon).matches.single().messages.single()
        assertEquals(Message.Kind.CALL to minjun, record.kind to record.from)
        assertTrue(record.text, Regex("보이스톡 \\d+초").matches(record.text))
    }

    @Test
    fun `a place named on the call stays in the conversation after it, as which place and who named it`() = testApplication {
        server()
        client.join(minjun, "민준")
        client.join(seoyeon, "서연")
        client.match(minjun, seoyeon)
        val call = Json.decodeFromString<VoiceCall>(client.ask(minjun, seoyeon).bodyAsText())
        val named = """{"placeIds":["drive-brew-jeongja","nowhere"]}"""
        assertEquals(HttpStatusCode.Conflict, client.post("/users/$minjun/calls/${call.id}/mentions") { setBody(named) }.status) // not live yet
        client.post("/users/$seoyeon/calls/${call.id}/answer")
        assertEquals(HttpStatusCode.NoContent, client.post("/users/$minjun/calls/${call.id}/mentions") { setBody(named) }.status)
        client.post("/users/$seoyeon/calls/${call.id}/mentions") { setBody(named) } // named again: once is enough
        client.delete("/users/$seoyeon/calls/${call.id}")
        val (record, mention) = client.inbox(seoyeon).matches.single().messages
        assertEquals(Message.Kind.CALL, record.kind)
        assertEquals(listOf(Message.Kind.MENTION, minjun, "드라이브 브루 정자", "drive-brew-jeongja"), listOf(mention.kind, mention.from, mention.text, mention.placeId))
    }

    @Test
    fun `the demo driver's lines name the café she suggests`() {
        val words = VoiceBot.wordsOf("demo-jiyeon")
        assertEquals(VoiceBot.linesOf("demo-jiyeon").size, words.size)
        assertEquals(listOf("drive-brew-jeongja"), words.flatMap { com.drivemate.core.PlaceMentions.find(it, PlaceCatalog.places) }.map { it.id })
    }

    @Test
    fun `a call nobody answers is kept as one that didn't connect`() = testApplication {
        server()
        client.join(minjun, "민준")
        client.join(seoyeon, "서연")
        client.match(minjun, seoyeon)
        val call = Json.decodeFromString<VoiceCall>(client.ask(minjun, seoyeon).bodyAsText())
        client.delete("/users/$minjun/calls/${call.id}")
        assertEquals("보이스톡 · 연결되지 않음", client.inbox(minjun).matches.single().messages.single().text)
    }

    @Test
    fun `only matches can call, one call at a time, and only the one asked answers`() = testApplication {
        server()
        client.join(minjun, "민준")
        client.join(seoyeon, "서연")
        assertEquals(HttpStatusCode.Conflict, client.ask(minjun, seoyeon).status) // not matched
        client.match(minjun, seoyeon)
        val call = Json.decodeFromString<VoiceCall>(client.ask(minjun, seoyeon).bodyAsText())
        assertEquals(HttpStatusCode.Conflict, client.ask(seoyeon, minjun).status) // already in one
        assertEquals(HttpStatusCode.Conflict, client.post("/users/$minjun/calls/${call.id}/answer").status)
    }

    @Test
    fun `a block ends the call`() = testApplication {
        server()
        client.join(minjun, "민준")
        client.join(seoyeon, "서연")
        client.match(minjun, seoyeon)
        client.ask(minjun, seoyeon)
        client.put("/users/$seoyeon/blocks/$minjun")
        assertEquals(null, client.inbox(minjun).call)
    }

    @Test
    fun `a demo driver with recorded lines answers and talks`() = testApplication {
        server()
        client.join(minjun, "민준")
        client.put("/users/$minjun/likes/demo-jiyeon") // she likes back at once
        repeat(50) { if (client.inbox(minjun).matches.isEmpty()) kotlinx.coroutines.delay(20) }
        val call = Json.decodeFromString<VoiceCall>(client.ask(minjun, "demo-jiyeon").bodyAsText())
        val ws = createClient { install(WebSockets) }
        val caller = ws.webSocketSession("/users/$minjun/calls/${call.id}/audio")
        assertEquals("live", withTimeout(5_000) { (caller.incoming.receive() as Frame.Text).readText() })
        val voice = withTimeout(5_000) { caller.incoming.receive() } as Frame.Binary
        assertEquals(VoiceBot.FRAME, voice.readBytes().size)
    }
}
