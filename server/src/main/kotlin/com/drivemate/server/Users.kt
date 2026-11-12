package com.drivemate.server

import com.drivemate.core.CommuteScenario
import com.drivemate.core.Conversation
import com.drivemate.core.Inbox
import com.drivemate.core.Matcher
import com.drivemate.core.MeetingPlaces
import com.drivemate.core.Meetup
import com.drivemate.core.Message
import com.drivemate.core.Place
import com.drivemate.core.PlaceSuggestions
import com.drivemate.core.PatternFinder
import com.drivemate.core.Person
import com.drivemate.core.Profile
import com.drivemate.core.Recommendation
import com.drivemate.core.UserUpdate
import java.util.concurrent.ConcurrentHashMap

/**
 * Everyone the server knows: cars that sent their profile and life pattern, and the
 * demo users; who likes whom, and what matched pairs said. Memory only; cars send their
 * pattern again whenever it changes or the app starts. Only cars heard from within
 * [ACTIVE_MS] (they poll while the app runs) and haven't switched 썸 off are recommended;
 * demo users always are.
 * Showing interest, messaging and proposing need a verified identity; a block (either
 * way) parts two people for good. A car sees [SHOWN] recommendations at a time; one it
 * passes on ("다음에") isn't recommended to it again, and someone new takes the place only
 * after [REFILL_MS].
 */
class Users(
    seed: Map<String, UserUpdate> = emptyMap(),
    private val now: () -> Long = System::currentTimeMillis,
    private val verification: Verification = Verification(),
    /** Keys how others' areas are blurred; set AREA_SECRET on the server before a release. */
    private val areaSecret: String = System.getenv("AREA_SECRET") ?: "drive-mate-areas",
) {
    private val users = ConcurrentHashMap(seed)
    private val permanent = seed.keys.toSet()
    private val lastSeen = ConcurrentHashMap<String, Long>()
    private val verified: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { addAll(seed.filterValues { it.profile.verified }.keys) }
    private val blocks = ConcurrentHashMap<String, MutableSet<String>>()
    private val reports = java.util.concurrent.CopyOnWriteArrayList<Report>()

    /** Someone reported [about]: kept for the operators (who would review it), and [about] is blocked. */
    data class Report(val from: String, val about: String, val reason: String, val at: Long)
    private val likes = ConcurrentHashMap<String, MutableSet<String>>()
    private val matchedAt = ConcurrentHashMap<String, Long>()
    private val chats = ConcurrentHashMap<String, MutableList<Message>>()
    private val meetups = ConcurrentHashMap<String, Meetup>()
    /** Whom each car passed on, and when. */
    private val passes = ConcurrentHashMap<String, MutableMap<String, Long>>()

    /** Stores [user]; true if [id] is new here. A valid verification token marks them verified. */
    fun put(id: String, user: UserUpdate): Boolean {
        if (verification.valid(id, user.verification)) verified += id
        return users.put(id, user) == null
    }

    fun isVerified(id: String) = id in verified

    fun nameOf(id: String) = users[id]?.profile?.name.orEmpty()

    /** Marks [id] verified (the phone page passed); returns the token the car keeps. */
    fun verify(id: String): String {
        verified += id
        return verification.token(id)
    }

    /** How others see [id]: with the server's word on verification, not the car's. */
    private fun profileOf(id: String) = users.getValue(id).profile.copy(verified = id in verified)

    fun blocked(a: String, b: String) = blocks[a]?.contains(b) == true || blocks[b]?.contains(a) == true

    /** [me] blocks [other]: interest either way, the match, its messages and meeting place all go. */
    @Synchronized
    fun block(me: String, other: String) {
        blocks.getOrPut(me) { ConcurrentHashMap.newKeySet() }.add(other)
        likes[me]?.remove(other)
        likes[other]?.remove(me)
        val key = pair(me, other)
        matchedAt.remove(key)
        chats.remove(key)
        meetups.remove(key)
    }

    fun report(from: String, about: String, reason: String) {
        reports += Report(from, about, reason, now())
        block(from, about)
    }

    fun reports(): List<Report> = reports.toList()

    /** Those [me] could be recommended: active, 썸 on, and not blocked either way. */
    fun candidatesFor(me: String): Map<String, UserUpdate> = active().filter { (id, user) -> id == me || (!user.paused && !blocked(me, id)) }

    /** [id] switched 썸 off for now. */
    fun paused(id: String) = users[id]?.paused == true

    operator fun contains(id: String) = users.containsKey(id)

    /** Forgets [id] entirely: profile, pattern, interest either way and conversations. */
    @Synchronized
    fun remove(id: String) {
        users.remove(id)
        lastSeen.remove(id)
        verified.remove(id)
        blocks.remove(id)
        blocks.values.forEach { it.remove(id) }
        likes.remove(id)
        likes.values.forEach { it.remove(id) }
        passes.remove(id)
        passes.values.forEach { it.remove(id) }
        matchedAt.keys.removeIf { id in it.split('|') }
        chats.keys.removeIf { id in it.split('|') }
        meetups.keys.removeIf { id in it.split('|') }
    }

    fun all(): Map<String, UserUpdate> = users.toMap()

    /** [id] was just heard from. */
    fun touch(id: String) {
        lastSeen[id] = now()
    }

    /** Those to recommend from: demo users and cars heard from lately. */
    fun active(): Map<String, UserUpdate> {
        val since = now() - ACTIVE_MS
        return users.filterKeys { it in permanent || (lastSeen[it] ?: 0) >= since }
    }

    /** How well [other] fits [me]'s days (see [Matcher.match]). */
    fun fit(me: String, other: String): Int = Matcher.match(users.getValue(me).pattern, users.getValue(other).pattern).score

    /** [me] shows interest in [other]; true if that makes a match. Callers check [isVerified] and [blocked]. */
    @Synchronized
    fun like(me: String, other: String): Boolean {
        if (blocked(me, other)) return false
        likes.getOrPut(me) { ConcurrentHashMap.newKeySet() }.add(other)
        val matched = matched(me, other)
        if (matched) matchedAt.putIfAbsent(pair(me, other), now())
        return matched
    }

    fun matched(a: String, b: String) = likes[a]?.contains(b) == true && likes[b]?.contains(a) == true

    /** [me] passes on [other] ("다음에"): not recommended to [me] again, and their interest isn't shown. */
    fun pass(me: String, other: String) {
        passes.getOrPut(me) { ConcurrentHashMap() }[other] = now()
    }

    fun passed(me: String, other: String) = passes[me]?.containsKey(other) == true

    /** Adds a message between a matched pair (or a record of what [from] did); false if they haven't matched. */
    fun send(from: String, to: String, text: String, kind: Message.Kind = Message.Kind.TEXT, placeId: String? = null): Boolean {
        if (!matched(from, to)) return false
        chats.getOrPut(pair(from, to)) { java.util.concurrent.CopyOnWriteArrayList() } += Message(from, text, now(), kind, placeId)
        return true
    }

    fun messages(a: String, b: String): List<Message> = chats[pair(a, b)].orEmpty().toList()

    /** Places on both drives home, for a matched pair (null if they haven't matched). */
    fun places(me: String, other: String, catalog: List<Place>): PlaceSuggestions? {
        if (!matched(me, other)) return null
        return MeetingPlaces.suggest(users.getValue(me).pattern, users.getValue(other).pattern, catalog)
    }

    /** [from] proposes [place]; replaces an earlier proposal. False if they haven't matched. */
    fun propose(from: String, to: String, place: Place): Boolean {
        if (!matched(from, to)) return false
        meetups[pair(from, to)] = Meetup(place, proposedBy = from)
        send(from, to, place.name, Message.Kind.PROPOSAL)
        return true
    }

    /** [by] accepts the other side's proposal; false if there is none to accept. */
    @Synchronized
    fun accept(by: String, other: String): Boolean {
        val meetup = meetups[pair(by, other)] ?: return false
        if (meetup.proposedBy == by) return false
        meetups[pair(by, other)] = meetup.copy(accepted = true)
        send(by, other, meetup.place.name, Message.Kind.ACCEPTANCE)
        return true
    }

    fun meetup(a: String, b: String): Meetup? = meetups[pair(a, b)]

    fun inbox(me: String): Inbox {
        val mine = likes[me].orEmpty().filter { it in this && !blocked(me, it) }
        val admirers = likes.filterValues { me in it }.keys.filter { it in this && it !in mine && !blocked(me, it) && !passed(me, it) }
        return Inbox(
            liked = mine.filterNot { matched(me, it) },
            likedMe = admirers.map { Person(it, profileOf(it)) },
            matches = mine.filter { matched(me, it) }.map { other ->
                Conversation(other, profileOf(other), fit(me, other), matchedAt[pair(me, other)] ?: now(), messages(me, other), meetups[pair(me, other)])
            }.sortedBy { it.since },
            verified = me in verified,
            verificationToken = if (me in verified) verification.token(me) else null,
        )
    }

    /**
     * Recommendations for [me], with each person's verification as the server knows it: up to [SHOWN],
     * less one for each pass made in the last [REFILL_MS] (the place stays empty a while).
     */
    fun recommend(me: String): List<Recommendation> {
        val mine = passes[me].orEmpty()
        val refilling = mine.values.count { now() - it < REFILL_MS }
        val candidates = candidatesFor(me).filterKeys { it !in mine }
        return Matcher.recommend(me, candidates, limit = (SHOWN - refilling).coerceAtLeast(0), secret = areaSecret).map { it.copy(profile = profileOf(it.userId)) }
    }

    private fun pair(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"

    companion object {
        const val ACTIVE_MS = 30 * 60_000L
        const val SHOWN = 3
        /** How long a passed-on place stays empty before someone new is recommended (short, for the demo). */
        const val REFILL_MS = 60_000L

        /** The demo drivers around Pangyo, each living one of the commute scenarios (tools/scenarios). */
        fun withDemoUsers(now: () -> Long = System::currentTimeMillis) = Users(
            DemoUsers.profiles.mapValues { (_, demo) ->
                val lines = Users::class.java.getResource("/scenarios/commute_week_${demo.scenario}.csv")!!.readText().lines()
                UserUpdate(demo.profile, PatternFinder.find(CommuteScenario.parse(lines).trips))
            },
            now,
        )
    }
}
