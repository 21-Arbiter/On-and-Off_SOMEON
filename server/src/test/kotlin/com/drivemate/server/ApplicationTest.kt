package com.drivemate.server

import com.drivemate.core.CommuteScenario
import com.drivemate.core.Inbox
import com.drivemate.core.MeetupProposal
import com.drivemate.core.Message
import com.drivemate.core.OutgoingMessage
import com.drivemate.core.PlaceSuggestions
import com.drivemate.core.PatternFinder
import com.drivemate.core.Profile
import com.drivemate.core.Recommendation
import com.drivemate.core.ReportRequest
import com.drivemate.core.UserUpdate
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationTest {

    private fun scenario(name: String) =
        PatternFinder.find(CommuteScenario.parse(javaClass.getResource("/scenarios/commute_week_$name.csv")!!.readText().lines()).trips)

    private val driverA = UserUpdate(Profile("민준", 31), scenario("A"))
    private val car = "car-a-0001"

    private var clock = 1_000_000L

    /** The server with demo users that answer at once. */
    private fun ApplicationTestBuilder.server() {
        application {
            val users = Users.withDemoUsers { clock }
            module(users, DemoUsers(users, this, likeFirstMs = 0, likeBackMs = 0, replyMs = 0))
        }
    }

    /** The phone page's form, as the driver would send it. */
    private suspend fun HttpClient.verify(id: String = car, birth: String = "1995-03-14") =
        post("/verify/$id") { setBody(FormDataContent(Parameters.build { append("birth", birth) })) }

    /** A car that sent its pattern and verified its driver. */
    private suspend fun HttpClient.join(id: String = car) {
        put("/users/$id") { setBody(Json.encodeToString(driverA)) }
        verify(id)
    }

    private suspend fun HttpClient.inbox(id: String = car) = Json.decodeFromString<Inbox>(get("/users/$id/inbox").bodyAsText())

    /** Polls the inbox until [done]; demo users answer from a coroutine. */
    private suspend fun HttpClient.awaitInbox(done: (Inbox) -> Boolean): Inbox {
        repeat(50) {
            val inbox = inbox()
            if (done(inbox)) return inbox
            delay(20)
        }
        return inbox()
    }

    @Test
    fun `a car sends its pattern and gets the demo drivers who fit it`() = testApplication {
        server()
        assertEquals(HttpStatusCode.NoContent, client.put("/users/$car") { setBody(Json.encodeToString(driverA)) }.status)
        val recs = Json.decodeFromString<List<Recommendation>>(client.get("/users/$car/recommendations").bodyAsText())
        assertEquals(listOf("지연", "민서", "하은"), recs.map { it.profile.name })
        assertEquals("jiyeon", recs.first().profile.photo)
    }

    @Test
    fun `one passed on leaves the recommendations, and someone new takes the place a while later`() = testApplication {
        server()
        client.put("/users/$car") { setBody(Json.encodeToString(driverA)) }
        suspend fun names() = Json.decodeFromString<List<Recommendation>>(client.get("/users/$car/recommendations").bodyAsText()).map { it.profile.name }
        assertEquals(HttpStatusCode.NoContent, client.put("/users/$car/passes/demo-jiyeon").status)
        assertEquals(listOf("민서", "하은"), names())
        assertEquals(emptyList<String>(), client.inbox().likedMe.map { it.profile.name }.filter { it == "지연" })
        clock += Users.REFILL_MS
        assertEquals(listOf("민서", "하은", "서윤"), names())
    }

    @Test
    fun `the demo driver who fits best shows interest first, and liking back is a match`() = testApplication {
        server()
        client.join()
        assertEquals(listOf("지연"), client.awaitInbox { it.likedMe.isNotEmpty() }.likedMe.map { it.profile.name })
        assertEquals("""{"matched":true}""", client.put("/users/$car/likes/demo-jiyeon").bodyAsText())
        val inbox = client.inbox()
        assertEquals(listOf("지연"), inbox.matches.map { it.profile.name })
        assertEquals(0, inbox.likedMe.size)
    }

    @Test
    fun `demo drivers like back only when the car fits their days well enough`() = testApplication {
        server()
        client.join()
        client.put("/users/$car/likes/demo-minseo")
        client.put("/users/$car/likes/demo-haeun")
        val inbox = client.awaitInbox { it.matches.isNotEmpty() }
        assertEquals(listOf("민서"), inbox.matches.map { it.profile.name })
        assertEquals(listOf("demo-haeun"), inbox.liked) // still waiting
    }

    @Test
    fun `only matched pairs can message, and demo drivers answer`() = testApplication {
        server()
        client.join()
        val hello = Json.encodeToString(OutgoingMessage("안녕하세요!"))
        assertEquals(HttpStatusCode.Forbidden, client.post("/users/$car/chats/demo-minseo") { setBody(hello) }.status)
        client.put("/users/$car/likes/demo-minseo")
        client.awaitInbox { it.matches.isNotEmpty() }
        assertEquals(HttpStatusCode.NoContent, client.post("/users/$car/chats/demo-minseo") { setBody(hello) }.status)
        val chat = client.awaitInbox { it.matches.single().messages.size == 2 }.matches.single().messages
        assertEquals(listOf(car to "안녕하세요!", "demo-minseo" to "안녕하세요! 저는 백현동으로 출근해요, 반가워요"), chat.map { it.from to it.text })
    }

    @Test
    fun `a matched pair gets places on both drives home, and a demo driver accepts one`() = testApplication {
        server()
        client.join()
        assertEquals(HttpStatusCode.Forbidden, client.get("/users/$car/chats/demo-jiyeon/places").status)
        client.awaitInbox { it.likedMe.isNotEmpty() }
        client.put("/users/$car/likes/demo-jiyeon")
        val places = Json.decodeFromString<PlaceSuggestions>(client.get("/users/$car/chats/demo-jiyeon/places").bodyAsText())
        assertEquals(3, places.places.size)
        assertEquals(true, places.places.none { it.place.area == "과천" || it.place.area == "강남 역삼동" })
        // Each comes with its menu; one without parking of its own, with a public car park and its fee.
        assertEquals(true, places.places.all { it.place.menu.any { item -> item.signature } })
        val terrace = places.places.single { it.place.id == "tancheon-terrace" }
        assertEquals("30분 600원 · 이후 10분 200원", terrace.place.carPark?.fee)
        assertEquals(true, "공영주차장 걸어서 4분" in terrace.reasons)
        val pick = places.places.first().place.id
        val proposal = Json.encodeToString(MeetupProposal(pick))
        assertEquals(HttpStatusCode.NoContent, client.put("/users/$car/chats/demo-jiyeon/meetup") { setBody(proposal) }.status)
        val meetup = client.awaitInbox { it.matches.single().meetup?.accepted == true }.matches.single().meetup!!
        assertEquals(pick to car, meetup.place.id to meetup.proposedBy)
        assertEquals(true, meetup.accepted)
        // The one who proposed can't accept their own proposal.
        assertEquals(HttpStatusCode.Conflict, client.post("/users/$car/chats/demo-jiyeon/meetup/accept").status)
        // The conversation keeps both, and the demo driver's next answer doesn't count them as talk.
        val name = places.places.first().place.name
        val records = client.inbox().matches.single().messages
        assertEquals(listOf(Triple(car, name, Message.Kind.PROPOSAL), Triple("demo-jiyeon", name, Message.Kind.ACCEPTANCE)), records.map { Triple(it.from, it.text, it.kind) })
        client.post("/users/$car/chats/demo-jiyeon") { setBody(Json.encodeToString(OutgoingMessage("안녕하세요"))) }
        val reply = client.awaitInbox { it.matches.single().messages.count { m -> m.said } == 2 }.matches.single().messages.last()
        assertEquals("반가워요! 저도 지금 퇴근하려고요. 판교에서 일하세요?", reply.text)
    }

    @Test
    fun `a deleted car is gone for everyone, conversations too`() = testApplication {
        server()
        client.join()
        val other = "car-b-0002"
        client.join(other)
        client.put("/users/$car/likes/$other")
        client.put("/users/$other/likes/$car")
        assertEquals(1, client.inbox(other).matches.size)
        assertEquals(HttpStatusCode.NoContent, client.delete("/users/$car").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/users/$car/inbox").status)
        val inbox = client.inbox(other)
        assertEquals(0, inbox.matches.size)
        assertEquals(false, inbox.likedMe.any { it.userId == car })
        val recs = Json.decodeFromString<List<Recommendation>>(client.get("/users/$other/recommendations").bodyAsText())
        assertEquals(false, recs.any { it.userId == car })
    }

    @Test
    fun `a car that hasn't been heard from for half an hour isn't recommended`() = testApplication {
        server()
        val other = "car-b-0002"
        client.put("/users/$car") { setBody(Json.encodeToString(driverA)) }
        client.put("/users/$other") { setBody(Json.encodeToString(driverA)) }
        suspend fun recommended() = Json.decodeFromString<List<Recommendation>>(client.get("/users/$car/recommendations").bodyAsText()).map { it.userId }
        assertEquals(true, other in recommended())
        clock += Users.ACTIVE_MS + 1
        assertEquals(false, other in recommended())
        assertEquals(true, "demo-jiyeon" in recommended())
    }

    @Test
    fun `a car with 썸 switched off isn't recommended until it's back on`() = testApplication {
        server()
        val other = "car-b-0002"
        client.put("/users/$car") { setBody(Json.encodeToString(driverA)) }
        client.put("/users/$other") { setBody(Json.encodeToString(driverA.copy(paused = true))) }
        suspend fun recommended() = Json.decodeFromString<List<Recommendation>>(client.get("/users/$car/recommendations").bodyAsText()).map { it.userId }
        assertEquals(false, other in recommended())
        assertEquals(true, "demo-jiyeon" in recommended())
        client.put("/users/$other") { setBody(Json.encodeToString(driverA)) }
        assertEquals(true, other in recommended())
    }

    @Test
    fun `interest, messages and proposals need a verified driver of 19 or over`() = testApplication {
        server()
        client.put("/users/$car") { setBody(Json.encodeToString(driverA)) }
        assertEquals(HttpStatusCode.Forbidden, client.put("/users/$car/likes/demo-jiyeon").status)
        assertEquals(false, client.inbox().verified)
        assertEquals(true, client.verify(birth = "2012-05-01").bodyAsText().contains("만 19세 이상"))
        assertEquals(false, client.inbox().verified)
        assertEquals(true, client.verify().bodyAsText().contains("완료"))
        assertEquals(true, client.inbox().verified)
        assertEquals(HttpStatusCode.OK, client.put("/users/$car/likes/demo-jiyeon").status)
    }

    @Test
    fun `the verification token keeps a car verified after a server restart`() {
        var token: String? = null
        testApplication {
            server()
            client.join()
            token = client.inbox().verificationToken
        }
        testApplication {
            server()
            client.put("/users/$car") { setBody(Json.encodeToString(driverA.copy(verification = token))) }
            assertEquals(true, client.inbox().verified)
            client.put("/users/car-x-0003") { setBody(Json.encodeToString(driverA.copy(verification = token))) } // someone else's token
            assertEquals(false, client.inbox("car-x-0003").verified)
        }
    }

    @Test
    fun `a block parts two people with no match, messages or recommendation`() = testApplication {
        server()
        client.join()
        client.awaitInbox { it.likedMe.isNotEmpty() }
        client.put("/users/$car/likes/demo-jiyeon")
        assertEquals(1, client.inbox().matches.size)
        assertEquals(HttpStatusCode.NoContent, client.put("/users/$car/blocks/demo-jiyeon").status)
        assertEquals(Inbox(verified = true, verificationToken = client.inbox().verificationToken), client.inbox())
        val recs = Json.decodeFromString<List<Recommendation>>(client.get("/users/$car/recommendations").bodyAsText())
        assertEquals(false, recs.any { it.userId == "demo-jiyeon" })
        assertEquals(HttpStatusCode.Forbidden, client.post("/users/$car/chats/demo-jiyeon") { setBody(Json.encodeToString(OutgoingMessage("안녕"))) }.status)
    }

    @Test
    fun `a report blocks too`() = testApplication {
        server()
        client.join()
        val report = Json.encodeToString(ReportRequest("프로필이 실제와 달라요"))
        assertEquals(HttpStatusCode.NoContent, client.post("/users/$car/reports/demo-minseo") { setBody(report) }.status)
        val recs = Json.decodeFromString<List<Recommendation>>(client.get("/users/$car/recommendations").bodyAsText())
        // the one reported is gone for good, and the next who fits takes the place at once
        assertEquals(listOf("지연", "하은", "서윤"), recs.map { it.profile.name })
    }

    @Test
    fun `unknown users and odd ids are refused`() = testApplication {
        server()
        assertEquals(HttpStatusCode.NotFound, client.get("/users/nobody-0001/recommendations").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/users/nobody-0001/inbox").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/users/x/recommendations").status)
        assertEquals(HttpStatusCode.BadRequest, client.put("/users/car-a-0001") { setBody("not json") }.status)
    }
}
