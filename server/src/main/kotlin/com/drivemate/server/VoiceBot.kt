package com.drivemate.server

import kotlinx.coroutines.delay
import kotlin.math.sqrt

/**
 * A demo user's side of a 보이스톡: says its first line once the call is up, then each
 * next line after the driver has spoken and paused, as people take turns. Lines are
 * 16 kHz mono 16-bit PCM, sent in 20 ms frames at the pace they play.
 */
class VoiceBot(private val lines: List<ByteArray>, private val now: () -> Long = System::currentTimeMillis) {
    @Volatile private var heardVoice = false
    @Volatile private var lastVoiceAt = 0L

    /** The driver's audio, as it arrives. */
    fun hear(frame: ByteArray) {
        if (rms(frame) > SPEECH_RMS) {
            heardVoice = true
            lastVoiceAt = now()
        }
    }

    /** Says the lines in turn through [send] ([onLine] as each begins); returns when the last is said, or when [send] says the call is over. */
    suspend fun talk(onLine: (Int) -> Unit = {}, send: suspend (ByteArray) -> Boolean) {
        delay(FIRST_LINE_MS)
        lines.forEachIndexed { i, line ->
            if (i > 0) awaitTurn()
            onLine(i)
            for (start in line.indices step FRAME) {
                if (!send(line.copyOfRange(start, minOf(start + FRAME, line.size)))) return
                delay(FRAME_MS)
            }
            heardVoice = false // what it heard while talking was its own echo, at most
        }
        delay(LAST_PAUSE_MS)
    }

    /** Until the driver has said something and paused, or has stayed quiet a long while. */
    private suspend fun awaitTurn() {
        val since = now()
        while (now() - since < WAIT_MS) {
            if (heardVoice && now() - lastVoiceAt > PAUSE_MS) break
            delay(50)
        }
        delay(REPLY_MS)
    }

    companion object {
        const val FRAME = 640 // 20 ms
        const val FRAME_MS = 20L
        const val SPEECH_RMS = 500.0
        const val PAUSE_MS = 700L
        const val WAIT_MS = 15_000L
        const val FIRST_LINE_MS = 2_000L // the car says it's connected first
        const val REPLY_MS = 300L
        const val LAST_PAUSE_MS = 1_500L

        fun rms(frame: ByteArray): Double {
            var sum = 0.0
            var n = 0
            for (i in 0 until frame.size - 1 step 2) {
                val sample = (frame[i].toInt() and 0xFF) or (frame[i + 1].toInt() shl 8)
                sum += sample.toDouble() * sample
                n++
            }
            return if (n == 0) 0.0 else sqrt(sum / n)
        }

        /** What a demo user's lines say (resources /voice/<id>_<n>.txt), as their own car would have heard it. */
        fun wordsOf(user: String): List<String> = linesOf(user).indices.map { i ->
            VoiceBot::class.java.getResource("/voice/${user}_${i + 1}.txt")?.readText()?.trim().orEmpty()
        }

        /** A demo user's recorded lines (resources /voice/<id>_<n>.pcm), in order. */
        fun linesOf(user: String): List<ByteArray> = generateSequence(1) { it + 1 }
            .map { VoiceBot::class.java.getResource("/voice/${user}_$it.pcm")?.readBytes() }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
    }
}
