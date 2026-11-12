package com.drivemate.app.match

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.drivemate.app.BuildConfig
import com.drivemate.app.notice.NoticeCenter
import com.drivemate.core.Inbox
import com.drivemate.core.LifePattern
import com.drivemate.core.PlaceSuggestions
import com.drivemate.core.Profile
import com.drivemate.core.Recommendation
import com.drivemate.core.UserUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * The driver's place among others on the matching server. Whenever the life pattern
 * changes it goes to the server (with the profile, never trips) and the server's picks
 * come back. Every [POLL_MS] the inbox is fetched: interest shown, matches and messages.
 * Anything new becomes a notice, which is only spoken while driving. With 썸 switched off
 * ([paused]) the driver isn't recommended to anyone and new recommendations and interest
 * aren't told (until it's back on); matches and conversations go on. Retries while the
 * server can't be reached; sends the pattern again if the server has forgotten the car.
 */
class Matchmaker(
    context: Context,
    private val scope: CoroutineScope,
    pattern: StateFlow<LifePattern>,
    recording: StateFlow<Boolean>,
    private val notices: NoticeCenter,
) {
    enum class Status { WAITING_FOR_PATTERN, LOADING, READY, OFFLINE }

    private val prefs = context.getSharedPreferences("drive_mate", Context.MODE_PRIVATE)
    private val client = MatchClient(BuildConfig.SERVER_URL)

    /** This car on the server; random, made once (and anew when the account is deleted). */
    var userId: String = prefs.getString(KEY_USER_ID, null) ?: newUserId()
        private set

    /** The phone page for verifying the driver's identity; the car shows it as a QR code. */
    val verifyUrl get() = "${BuildConfig.SERVER_URL.trimEnd('/')}/verify/$userId"

    private fun newUserId() = "car-${UUID.randomUUID()}".also { prefs.edit { putString(KEY_USER_ID, it) } }

    private fun update(p: LifePattern) = UserUpdate(PROFILE, p, verification = prefs.getString(KEY_VERIFICATION, null), paused = _paused.value)

    private val _paused = MutableStateFlow(prefs.getBoolean(KEY_PAUSED, false))

    /** 썸 is switched off (the parked screen's switch). */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun setPaused(paused: Boolean) {
        _paused.value = paused
        prefs.edit { putBoolean(KEY_PAUSED, paused) }
    }

    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations.asStateFlow()

    /** Those the driver passed on ("다음에"); kept here too, so they stay gone if the server restarts and forgets. */
    private var passed: Set<String> = prefs.getStringSet(KEY_PASSED, emptySet())!!

    private fun shown(recs: List<Recommendation>) = recs.filterNot { it.userId in passed }

    private val _status = MutableStateFlow(Status.WAITING_FOR_PATTERN)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _inbox = MutableStateFlow(Inbox())
    val inbox: StateFlow<Inbox> = _inbox.asStateFlow()

    /** The driver as others see them. */
    val profile get() = PROFILE

    private val _read = MutableStateFlow(
        prefs.getStringSet(KEY_READ, emptySet())!!.associate { it.substringBefore('\t') to it.substringAfter('\t').toLong() },
    )

    /** How far the driver has seen each conversation (the server's clock); none yet for a new match. */
    val read: StateFlow<Map<String, Long>> = _read.asStateFlow()

    private fun forgetRead() {
        _read.value = emptyMap()
        prefs.edit { remove(KEY_READ) }
    }

    /** The driver has seen the conversation with [other] up to [upTo] (open on the screen, or read out). */
    fun markRead(other: String, upTo: Long) {
        if ((_read.value[other] ?: Long.MIN_VALUE) >= upTo) return
        _read.update { it + (other to upTo) }
        prefs.edit { putStringSet(KEY_READ, _read.value.map { (id, at) -> "$id\t$at" }.toSet()) }
    }

    /** The pattern the server has, once it has one. */
    private var sent: LifePattern? = null

    init {
        scope.launch {
            // Nothing to match on until the driver records and has a routine; the server hears of 썸 off and on too.
            combine(pattern, recording, _paused) { p, on, paused -> p.takeIf { on && it.routines.isNotEmpty() } to paused }
                .distinctUntilChanged()
                .collectLatest { (p, _) ->
                    if (p == null) {
                        // Records deleted (or recording stopped): the server forgets this car too.
                        if (sent != null) runCatching { client.forget(userId) }
                        sent = null
                        _status.value = Status.WAITING_FOR_PATTERN
                        _recommendations.value = emptyList()
                        _inbox.value = Inbox()
                        forgetRead()
                    } else {
                        sync(p)
                    }
                }
        }
        scope.launch {
            while (true) {
                delay(POLL_MS)
                refreshInbox()
                refreshRecommendations()
            }
        }
    }

    /**
     * "다음에": [other] leaves the recommendations at once and isn't recommended again; the server
     * recommends someone new in their place after a while, and the poll picks them up.
     */
    fun pass(other: String) {
        passed = passed + other
        prefs.edit { putStringSet(KEY_PASSED, passed) }
        _recommendations.update(::shown)
        act("Pass not sent") { client.pass(userId, other) }
    }

    /** Shows interest in [other]; a match if they already showed theirs. */
    fun like(other: String) {
        _inbox.update { it.copy(liked = it.liked + other) } // at once; the refresh brings the truth
        scope.launch {
            try {
                client.like(userId, other)
            } catch (e: IOException) {
                Log.w(TAG, "Like not sent", e)
            }
            refreshInbox()
        }
    }

    /** Places on both drives home, for a match; null when the server can't be reached. */
    suspend fun places(other: String): PlaceSuggestions? = try {
        client.places(userId, other)
    } catch (e: IOException) {
        Log.w(TAG, "Places not loaded", e)
        null
    }

    /** Proposes a meeting place to a match; replaces an earlier proposal. */
    fun propose(other: String, placeId: String) = act("Proposal not sent") { client.propose(userId, other, placeId) }

    /** Accepts the match's proposed meeting place. */
    fun accept(other: String) = act("Acceptance not sent") { client.accept(userId, other) }

    private fun act(failure: String, call: suspend () -> Unit) {
        scope.launch {
            try {
                call()
            } catch (e: IOException) {
                Log.w(TAG, failure, e)
            }
            refreshInbox()
        }
    }

    fun send(other: String, text: String) {
        scope.launch {
            try {
                client.message(userId, other, text)
            } catch (e: IOException) {
                Log.w(TAG, "Message not sent", e)
            }
            refreshInbox()
        }
    }

    private suspend fun sync(p: LifePattern) {
        _status.value = Status.LOADING
        while (true) {
            try {
                client.send(userId, update(p))
                sent = p
                val recs = shown(client.recommendations(userId))
                if (!_paused.value) announceRecommendations(recs)
                _recommendations.value = recs
                _status.value = Status.READY
                refreshInbox()
                return
            } catch (e: IOException) {
                Log.w(TAG, "Matching server unreachable", e)
                _status.value = Status.OFFLINE
                delay(RETRY_MS)
            }
        }
    }

    /** Blocks someone: they and everything with them disappear for good. */
    fun block(other: String) = part(other) { client.block(userId, other) }

    /** Reports someone with a set reason; that blocks them too. */
    fun report(other: String, reason: String) = part(other) { client.report(userId, other, reason) }

    /** Takes [other] off the screen at once, then has the server part the two and recommend again. */
    private fun part(other: String, call: suspend () -> Unit) {
        _recommendations.update { recs -> recs.filterNot { it.userId == other } }
        _inbox.update { inbox -> inbox.copy(liked = inbox.liked - other, likedMe = inbox.likedMe.filterNot { it.userId == other }, matches = inbox.matches.filterNot { it.userId == other }) }
        act("Block not sent") {
            call()
            _recommendations.value = shown(client.recommendations(userId))
        }
    }

    /** Fetches the inbox now, e.g. while the driver verifies on the phone. */
    suspend fun refresh() = refreshInbox()

    /** Deletes the driver's account on the server and starts over under a new id. */
    suspend fun deleteAccount() {
        runCatching { client.forget(userId) }
        prefs.edit { clear() }
        userId = newUserId()
        passed = emptySet()
        sent = null
        _paused.value = false
        _recommendations.value = emptyList()
        _inbox.value = Inbox()
        _read.value = emptyMap()
        _status.value = Status.WAITING_FOR_PATTERN
    }

    private suspend fun refreshInbox() {
        val p = sent ?: return
        try {
            val inbox = client.inbox(userId)
            announceInbox(inbox)
            _inbox.value = inbox
        } catch (e: MatchClient.NotRegistered) {
            Log.i(TAG, "The server forgot this car; sending the pattern again")
            runCatching { client.send(userId, update(p)) }
        } catch (e: IOException) {
            Log.w(TAG, "Inbox not refreshed", e)
        }
    }

    /** New faces come over time (someone in a passed-on place, a car that just joined): told like the first ones. */
    private suspend fun refreshRecommendations() {
        if (sent == null) return
        try {
            val recs = shown(client.recommendations(userId))
            if (!_paused.value) announceRecommendations(recs)
            _recommendations.value = recs
        } catch (e: IOException) {
            // forgotten by the server: the inbox refresh sends the pattern again
            if (e !is MatchClient.NotRegistered) Log.w(TAG, "Recommendations not refreshed", e)
        }
    }

    private fun announceRecommendations(recs: List<Recommendation>) {
        val fresh = recs.filter { it.userId !in seen(KEY_SEEN_RECOMMENDED) }
        if (fresh.isEmpty()) return
        markSeen(KEY_SEEN_RECOMMENDED, fresh.map { it.userId })
        val first = fresh.first()
        if (fresh.size == 1) {
            notices.post("새 인연 추천", "${first.profile.name}님과 이동 패턴이 ${first.score}% 맞아요", "새 인연 추천이 도착했어요.")
        } else {
            notices.post("새 인연 추천 ${fresh.size}명", fresh.joinToString(", ") { "${it.profile.name} ${it.score}%" }, "새 인연 추천이 ${fresh.size}명 도착했어요.")
        }
    }

    /** Interest shown in the driver, new matches and their messages, each told once (even across restarts). */
    private fun announceInbox(inbox: Inbox) {
        inbox.verificationToken?.takeIf { it != prefs.getString(KEY_VERIFICATION, null) }?.let {
            prefs.edit { putString(KEY_VERIFICATION, it) }
            notices.post("본인 인증 완료", "이제 관심 보내기, 대화, 만남 제안을 할 수 있어요", "본인 인증이 완료됐어요.")
        }
        // With 썸 off interest waits, and is told once it's back on.
        if (!_paused.value) inbox.likedMe.filter { it.userId !in seen(KEY_SEEN_ADMIRERS) }.forEach {
            markSeen(KEY_SEEN_ADMIRERS, listOf(it.userId))
            val name = it.profile.name
            notices.post("${name}님이 관심을 보냈어요", "${name}님께 관심을 보내면 바로 매칭돼요", "${name}님이 회원님께 관심을 보냈어요.")
        }
        inbox.matches.filter { it.userId !in seen(KEY_SEEN_MATCHES) }.forEach {
            markSeen(KEY_SEEN_MATCHES, listOf(it.userId))
            val name = it.profile.name
            notices.post("${name}님과 썸이 켜졌어요", "매칭/대화에서 먼저 인사해 보세요", "${name}님과 썸이 켜졌어요.", about = it.userId, match = true)
        }
        inbox.matches.forEach { c ->
            val meetup = c.meetup ?: return@forEach
            val key = "${c.userId}:${meetup.place.id}:${meetup.accepted}"
            if (key in seen(KEY_SEEN_MEETUPS)) return@forEach
            markSeen(KEY_SEEN_MEETUPS, listOf(key))
            val name = c.profile.name
            val place = meetup.place.name
            when {
                meetup.accepted -> notices.post("${place}에서 만나기로 했어요", "${name}님과 약속 장소가 정해졌어요. 만남 장소에서 길안내를 시작할 수 있어요", "${name}님과 ${place}에서 만나기로 했어요.", c.userId, aboutPlace = true)
                meetup.proposedBy == c.userId -> notices.post("${name}님의 만남 장소 제안", place, "${name}님이 만남 장소를 제안했어요.", c.userId, aboutPlace = true)
            }
        }
        val heardUpTo = prefs.getLong(KEY_HEARD_UP_TO, 0)
        val incoming = inbox.matches.flatMap { c -> c.messages.filter { it.from == c.userId && it.said && it.at > heardUpTo }.map { c.profile.name to it } }
        if (incoming.isEmpty()) return
        prefs.edit { putLong(KEY_HEARD_UP_TO, incoming.maxOf { it.second.at }) }
        incoming.forEach { (name, message) ->
            if (notices.message(message.from, name, message.text)) markRead(message.from, message.at)
        }
    }

    private fun seen(key: String): Set<String> = prefs.getStringSet(key, emptySet())!!

    private fun markSeen(key: String, ids: List<String>) = prefs.edit { putStringSet(key, seen(key) + ids) }

    private companion object {
        const val TAG = "Matchmaker"
        const val KEY_USER_ID = "userId"
        const val KEY_SEEN_RECOMMENDED = "recommendedUserIds"
        const val KEY_SEEN_ADMIRERS = "admirerUserIds"
        const val KEY_SEEN_MATCHES = "matchedUserIds"
        const val KEY_HEARD_UP_TO = "messagesHeardUpTo"
        const val KEY_SEEN_MEETUPS = "meetupsSeen"
        const val KEY_VERIFICATION = "verificationToken"
        const val KEY_READ = "conversationsReadUpTo"
        const val KEY_PAUSED = "paused"
        const val KEY_PASSED = "passedUserIds"
        const val RETRY_MS = 30_000L
        const val POLL_MS = 10_000L

        /** The demo driver (scenario A) until profiles can be edited. */
        val PROFILE = Profile("민준", 31, "출퇴근길이 조금 덜 심심했으면 해요", listOf("드라이브", "커피", "러닝"))
    }
}
