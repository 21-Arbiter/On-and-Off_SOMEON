package com.drivemate.app.voice

import com.drivemate.core.Conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Talking with matches by voice, parked or driving alike: a match's message is read out
 * and what the driver says next goes back as the reply, and [speak] (the 음성 입력 button)
 * sends what they say whenever they like, to [talkingTo] or to the match they name first.
 * Saying 보이스톡 asks for a call too. Call from the main thread.
 */
class VoiceChat(
    private val announcer: Announcer,
    private val listener: Listener,
    /** Whether the app may use the microphone (asked for once while parked). */
    private val canListen: () -> Boolean,
    private val send: (userId: String, text: String) -> Unit,
    private val call: (userId: String, name: String) -> Unit,
    /** In a 보이스톡, messages wait quietly on the screen. */
    private val inCall: () -> Boolean,
    /** Matches (id to name), the latest conversation first. */
    private val partners: () -> List<Pair<String, String>>,
) {
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _chosen = MutableStateFlow<String?>(null)

    /**
     * Whom the driver last talked with: picked on the driving screen, answered, or named.
     * Null (or someone no longer matched) means the latest conversation (see [talkingTo]).
     */
    val chosen: StateFlow<String?> = _chosen.asStateFlow()

    fun choose(userId: String) {
        _chosen.value = userId
    }

    /** Reads [text] out and listens for the answer; false if it can't (it's then a plain notice). */
    fun answer(from: String, name: String, text: String): Boolean {
        if (inCall()) return true
        if (_listening.value || !listener.available || !canListen()) return false
        _chosen.value = from // the conversation is with whoever just wrote
        val said = text.trimEnd().let { if (it.last() in ".?!~") it else "$it." }
        announcer.say("${name}님 메시지예요.")
        announcer.say(said, voice = Announcer.MESSAGE_VOICE)
        turn("답장하시려면 말씀해 주세요.", from to name)
        return true
    }

    /** The 음성 입력 button: says who it's for, then sends what the driver says. */
    fun speak() {
        if (_listening.value || inCall()) return
        val to = talkingTo(partners(), _chosen.value) ?: return announcer.say("아직 대화할 분이 없어요.")
        if (!listener.available || !canListen()) return announcer.say("음성 입력을 쓸 수 없어요. 마이크 권한을 확인해 주세요.")
        turn("${to.second}님께 뭐라고 보낼까요?", to)
    }

    private fun turn(prompt: String, to: Pair<String, String>, again: Boolean = false) {
        _listening.value = true
        announcer.say(prompt) {
            listener.listen { heard ->
                _listening.value = false
                // Nothing heard: ask once more; then no reply this time.
                if (heard == null) return@listen if (!again) turn("잘 못 들었어요. 다시 말씀해 주세요.", to, again = true) else Unit
                val (target, text) = route(heard, to, partners())
                if (text.isEmpty()) return@listen
                _chosen.value = target.first
                send(target.first, text)
                if (asksForCall(text)) call(target.first, target.second) else announcer.say("보냈어요.")
            }
        }
    }

    companion object {
        /** Who 음성 입력 talks to: the conversation with the latest message (or the newest match) first. */
        fun latestFirst(matches: List<Conversation>) = matches.sortedByDescending { c -> c.messages.lastOrNull()?.at ?: c.since }

        /** Of [partners] (the latest conversation first), the [chosen] one while they're still there, else the latest. */
        fun <T> talkingTo(partners: List<Pair<String, T>>, chosen: String?): Pair<String, T>? =
            partners.firstOrNull { it.first == chosen } ?: partners.firstOrNull()

        /** "보이스톡 할래요?", "통화할까요", ...: the driver wants to talk. */
        fun asksForCall(said: String) = said.replace(" ", "").let { s -> listOf("보이스톡", "통화", "전화").any { it in s } }

        private val Address = Regex("^(님께|님에게|님한테|님|씨|에게|한테|아|야)?[,.!\\s]*")

        /**
         * Who [heard] is for, and what to send: a message that starts with a match's name
         * ("지연님, 저 출발했어요") goes to them without the name; otherwise to [default].
         */
        fun route(heard: String, default: Pair<String, String>, partners: List<Pair<String, String>>): Pair<Pair<String, String>, String> {
            val named = partners.firstOrNull { heard.startsWith(it.second) } ?: return default to heard.trim()
            return named to heard.removePrefix(named.second).replaceFirst(Address, "").trim()
        }
    }
}
