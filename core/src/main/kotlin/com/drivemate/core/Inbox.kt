package com.drivemate.core

import kotlinx.serialization.Serializable

/**
 * A chat line; [from] is the sender's user id, [at] the server's clock (epoch ms). Besides
 * what people say, the conversation keeps what they did: a 보이스톡 ([text] says how long),
 * a meeting place proposed or accepted ([text] is the place's name), [from] the one who did it,
 * and a place one of them named on a 보이스톡 ([MENTION]: [text] its name, [placeId] its id).
 */
@Serializable
data class Message(val from: String, val text: String, val at: Long, val kind: Kind = Kind.TEXT, val placeId: String? = null) {
    enum class Kind { TEXT, CALL, PROPOSAL, ACCEPTANCE, MENTION }

    val said get() = kind == Kind.TEXT
}

/** Someone both sides showed interest in, and what they've said since [since]. */
@Serializable
data class Conversation(
    val userId: String,
    val profile: Profile,
    val score: Int,
    val since: Long,
    val messages: List<Message> = emptyList(),
    val meetup: Meetup? = null,
)

/** Someone, as others see them. */
@Serializable
data class Person(val userId: String, val profile: Profile)

/**
 * Where a driver stands with others: who they showed interest in ([liked]), who showed
 * interest in them without an answer yet ([likedMe]), and who they matched with.
 */
@Serializable
data class Inbox(
    val liked: List<String> = emptyList(),
    val likedMe: List<Person> = emptyList(),
    val matches: List<Conversation> = emptyList(),
    /** Whether the driver has verified their identity (and is 19 or over); [verificationToken] proves it later. */
    val verified: Boolean = false,
    val verificationToken: String? = null,
    /** The 보이스톡 the driver is in, or is being asked to answer. */
    val call: VoiceCall? = null,
)

/** 보이스톡 between two matches: [from] asked [to], and it is [live] once [to] answers. */
@Serializable
data class VoiceCall(val id: String, val from: String, val to: String, val live: Boolean = false) {
    fun other(me: String) = if (me == from) to else from
}

/** What a sent message carries. */
@Serializable
data class OutgoingMessage(val text: String)

/** Why someone is reported; the app offers set reasons. */
@Serializable
data class ReportRequest(val reason: String)

/** A proposed meeting place. */
@Serializable
data class MeetupProposal(val placeId: String)

/** The meeting places a driver named on a 보이스톡, found by their car in their own words. */
@Serializable
data class CallMentions(val placeIds: List<String>)
