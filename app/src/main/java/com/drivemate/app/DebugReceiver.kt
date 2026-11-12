package com.drivemate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drivemate.app.voice.Announcer
import com.drivemate.core.CommuteScenario

/**
 * Demo commands, from adb only (`adb shell am broadcast -n com.drivemate.app/.DebugReceiver ...`; the
 * receiver needs android.permission.DUMP, which only the shell has). In release builds too, so judges
 * can run the whole story on an emulator, which has no microphone and no week of driving behind it:
 * - `--es seed commute_week_A.csv`: adds that scenario's week of trips (tools/scenarios)
 * - `--ez clear true`: forgets all trips
 * - `--ez debugLine true`: shows a line of internals (gear, notices, 썸) at the screen's foot; `false` hides it again
 * - `--es say "..."`: speaks a line (to record voices for scripted demos); `--es voice seoyoon` in another voice
 * - `--es hear clip.pcm`: the next time the app listens it hears that recording (16 kHz mono
 *   16-bit PCM in the app's files, or one of the APK's: minjun_message, minjun_answer, minjun_reply,
 *   minjun_call_1, minjun_call_2) instead of the microphone; with `--ez now true` it listens at once.
 *   `--es says "..."` gives its words, so a misheard take is heard again rather than sent;
 *   `--ez keep true` keeps it for the following listens until it is heard right
 * - `--es talk clip.pcm`: in a 보이스톡, says that recording instead of the microphone
 * - `--ez speak true`: presses 음성 입력 (scripted demos: the button moves with what's above it)
 * - `--ez proposeNamed true`: presses 만남 장소로 제안 on the latest place named on a 보이스톡 (it moves with the chat too)
 * - otherwise posts a notice (extras: title, body, spoken), to try what the driver hears and sees
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val drive = (context.applicationContext as DriveMateApp).drive
        val seed = intent.getStringExtra("seed")
        val hear = intent.getStringExtra("hear")
        // a recording: the app's files first (debug builds, `run-as`), else one that comes in the APK
        fun recording(name: String) = java.io.File(context.filesDir, name).takeIf { it.exists() }?.readBytes()
            ?: context.assets.open(name).use { it.readBytes() }
        when {
            intent.hasExtra("say") -> drive.announcer.say(intent.getStringExtra("say").orEmpty(), intent.getStringExtra("voice") ?: Announcer.VOICE)
            hear != null -> {
                drive.listener.standIn = recording(hear)
                drive.listener.standInSays = intent.getStringExtra("says")
                drive.listener.standInKeep = intent.getBooleanExtra("keep", false)
                if (intent.getBooleanExtra("now", false)) drive.listener.listen { android.util.Log.i("DebugReceiver", "heard: $it") }
            }
            intent.getBooleanExtra("speak", false) -> drive.voice.speak()
            intent.getBooleanExtra("proposeNamed", false) -> drive.matches.inbox.value.matches
                .flatMap { c -> c.messages.filter { it.kind == com.drivemate.core.Message.Kind.MENTION }.map { c.userId to it } }
                .maxByOrNull { it.second.at }
                ?.let { (other, named) -> named.placeId?.let { drive.matches.propose(other, it) } }
            intent.hasExtra("talk") -> drive.talk.sayInstead(recording(intent.getStringExtra("talk")!!))
            intent.hasExtra("debugLine") -> drive.showDebugLine.value = intent.getBooleanExtra("debugLine", true)
            intent.getBooleanExtra("clear", false) -> drive.trips.store.clear()
            seed != null -> drive.trips.add(CommuteScenario.parse(context.assets.open(seed).bufferedReader().readLines()).trips)
            else -> drive.notices.post(
                title = intent.getStringExtra("title") ?: "새 인연 추천",
                body = intent.getStringExtra("body") ?: "평일 아침 출근 시간대와 생활권이 비슷한 분이 있어요.",
                spoken = intent.getStringExtra("spoken") ?: "새 인연 추천이 도착했어요.",
            )
        }
    }
}
