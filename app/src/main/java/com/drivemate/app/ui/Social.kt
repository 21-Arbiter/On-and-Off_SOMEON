package com.drivemate.app.ui

import com.drivemate.core.Conversation
import com.drivemate.core.Inbox
import com.drivemate.core.Message
import com.drivemate.core.Place
import com.drivemate.core.PlaceSuggestions
import com.drivemate.app.voice.VoiceTalk
import com.drivemate.core.Profile
import com.drivemate.core.Recommendation

/** What the parked screens know and can do about other people. */
class Social(
    val myId: String,
    val candidates: List<Recommendation>,
    /** Shown instead of a recommendation when there is none, saying why. */
    val emptyMessage: String,
    val inbox: Inbox,
    val like: (userId: String) -> Unit,
    val send: (userId: String, text: String) -> Unit,
    val places: suspend (userId: String) -> PlaceSuggestions?,
    val propose: (userId: String, placeId: String) -> Unit,
    val accept: (userId: String) -> Unit,
    /** Sets the destination in the car's navigation; false if it isn't reachable. */
    val navigate: (Place) -> Boolean,
    val block: (userId: String) -> Unit,
    val report: (userId: String, reason: String) -> Unit,
    /** Opens the report/block sheet for someone. */
    val askReport: (userId: String, name: String) -> Unit,
    /** Takes the driver to verifying their identity. */
    val askVerification: () -> Unit,
    val refresh: suspend () -> Unit,
    val verifyUrl: String,
    /** Listens for one utterance (asking for the microphone first if need be); null if nothing was understood. */
    val listen: (onPartial: (String) -> Unit, onResult: (String?) -> Unit) -> Unit = { _, done -> done(null) },
    /** Asks a match for a 보이스톡. */
    val call: (userId: String, name: String) -> Unit = { _, _ -> },
    val voiceCall: VoiceTalk.State? = null,
    /** The call's loudness now, 0..1, for the equalizer. */
    val callLevel: Float = 0f,
    val answerCall: () -> Unit = {},
    val hangUp: () -> Unit = {},
    /** The driver as others see them. */
    val me: Profile? = null,
    /** How far the driver has seen each conversation (see [unread]). */
    val read: Map<String, Long> = emptyMap(),
    val markRead: (userId: String, upTo: Long) -> Unit = { _, _ -> },
    /** 썸 is switched off: the driver isn't recommended to anyone, and today's matches are put aside. */
    val paused: Boolean = false,
    val setPaused: (Boolean) -> Unit = {},
    /** Someone else is in the car (Alerts.discreet): people's cards and conversations are covered until [reveal]. */
    val covered: Boolean = false,
    val reveal: () -> Unit = {},
    /** "다음에": someone leaves the recommendations, and someone new comes in a while. */
    val pass: (userId: String) -> Unit = {},
) {
    val verified get() = inbox.verified

    /**
     * What's new in [c] for the driver: what the other side said or proposed since the
     * conversation was last seen, or the match itself until it is first opened.
     */
    fun unread(c: Conversation): Int = unread(c, read)

    /** The conversation with [c] is on the screen. */
    fun seen(c: Conversation) = markRead(c.userId, c.messages.maxOfOrNull { it.at } ?: c.since)

    enum class Standing { NONE, LIKED_ME, LIKED, MATCHED }

    fun standing(userId: String) = when {
        inbox.matches.any { it.userId == userId } -> Standing.MATCHED
        userId in inbox.liked -> Standing.LIKED
        inbox.likedMe.any { it.userId == userId } -> Standing.LIKED_ME
        else -> Standing.NONE
    }
}

/** What's new in [c] given how far the driver has [read] each conversation (see [Social.unread]). */
fun unread(c: Conversation, read: Map<String, Long>): Int {
    fun theirs(m: Message) = m.from == c.userId && m.kind != Message.Kind.CALL && m.kind != Message.Kind.MENTION
    val seen = read[c.userId] ?: return c.messages.count(::theirs).coerceAtLeast(1)
    return c.messages.count { theirs(it) && it.at > seen }
}

/** The notice settings (notice/AlertSettings), and what they come to now (DriveSession.discreet and quiet). */
class Alerts(
    val on: Boolean = true,
    val offWithPassenger: Boolean = true,
    /** Someone else is in the car and the driver wants quiet then: notices show without names or contents. */
    val discreet: Boolean = false,
    /** Notices are kept to the screen; the wordmarks' ON is out. */
    val quiet: Boolean = false,
    val setOn: (Boolean) -> Unit = {},
    val setOffWithPassenger: (Boolean) -> Unit = {},
)
