package com.drivemate.app.notice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Something to tell the user, e.g. a new recommendation; [spoken] is one short sentence
 * for the voice. [about] is the user id of the person it concerns, if any; [aboutPlace]
 * says it's about where to meet them, [match] that the two have just matched.
 */
data class Notice(
    val id: Long,
    val title: String,
    val body: String,
    val spoken: String,
    val about: String? = null,
    val aboutPlace: Boolean = false,
    val match: Boolean = false,
)

/**
 * Notices reach the driver by voice only while driving, with a reminder to look once
 * stopped; in P they are spoken as they are. Either way they wait in [pending], shown
 * on the parked screen, until the user has seen them. Before the car's state is known
 * (app start) they are held and summed up once it is. While notices are off ([quiet]:
 * switched off, or by themselves with someone else in the car) the voice says nothing of
 * them and they only wait on the screen. Call from the main thread.
 */
class NoticeCenter(private val say: (String) -> Unit) {

    private val _pending = MutableStateFlow<List<Notice>>(emptyList())
    val pending: StateFlow<List<Notice>> = _pending.asStateFlow()

    private var parked: Boolean? = null
    private var unspoken = 0
    private var nextId = 1L

    /**
     * While driving, a match's message goes here (sender id, name, text) instead of the
     * usual line, to be read out and answered by voice; true if it took it (it may keep it quiet).
     */
    var answerByVoice: ((from: String, name: String, text: String) -> Boolean)? = null

    /** Whether the car keeps notices to the screen. */
    var quiet: () -> Boolean = { false }

    val driving get() = parked == false

    fun post(title: String, body: String, spoken: String, about: String? = null, aboutPlace: Boolean = false, match: Boolean = false) {
        _pending.update { it + Notice(nextId++, title, body, spoken, about, aboutPlace, match) }
        if (quiet()) return
        when (parked) {
            null -> unspoken++
            true -> say(spoken)
            false -> say("$spoken 정차한 뒤 화면에서 확인하세요.")
        }
    }

    /**
     * A match's message: on the screen like any notice, and while driving read out to answer
     * by voice if possible (not while [quiet]); true if it was read out.
     */
    fun message(from: String, name: String, text: String): Boolean {
        if (parked == false && !quiet() && answerByVoice?.invoke(from, name, text) == true) {
            _pending.update { it + Notice(nextId++, "${name}님의 메시지", text, "", about = from) }
            return true
        }
        post("${name}님의 메시지", text, "${name}님이 메시지를 보냈어요.", about = from)
        return false
    }

    /** [parked] is null while unknown. On stopping, a reminder of what is waiting. */
    fun onParkedChanged(parked: Boolean?) {
        val before = this.parked
        this.parked = parked
        val waiting = _pending.value.size
        if (!quiet()) when {
            parked == true && before == false && waiting > 0 -> say("정차했어요. 새 소식 ${waiting}개를 화면에서 확인하세요.")
            parked == true && unspoken > 0 -> say("새 소식 ${waiting}개가 있어요. 화면에서 확인하세요.")
            parked == false && unspoken > 0 -> say("새 소식 ${unspoken}개가 있어요. 정차한 뒤 화면에서 확인하세요.")
        }
        if (parked != null) unspoken = 0
    }

    /** The user has seen them all. */
    fun dismissAll() {
        _pending.value = emptyList()
    }
}
