package com.drivemate.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale

/**
 * One utterance at a time through the Pleos on-device speech recognition: the driver
 * speaks, the text comes back. Call from the main thread; callbacks arrive on it.
 */
class Listener(context: Context) {

    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var turn: Turn? = null
    @Volatile private var feeding: ByteArray? = null

    /** A recording to hear instead of the microphone, once (scripted demos on the emulator). */
    var standIn: ByteArray? = null

    /**
     * What [standIn] says. The emulator's engine often mishears a recording; with this set it
     * hears it again (a few times at most) until it gets it, like retaking a shot.
     */
    var standInSays: String? = null

    /** Keep hearing [standIn] (when the driver is asked again, say) until it is heard right. */
    var standInKeep = false

    /**
     * One utterance. The engine starts its text afresh after a pause, so what came before
     * a gap of [SEGMENT_GAP_MS] between updates is kept in [said].
     */
    private class Turn(val onPartial: (String) -> Unit, val onResult: (String?) -> Unit, val recording: ByteArray?, val says: String?, val attempt: Int = 1) {
        var said = ""
        var text = ""
        var updatedAt = 0L
        val all get() = listOf(said, text).filter { it.isNotBlank() }.joinToString(" ")
    }

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            main.post {
                val current = turn ?: return@post
                val now = SystemClock.elapsedRealtime()
                if (current.text.isNotBlank() && now - current.updatedAt > SEGMENT_GAP_MS) current.said = current.all
                current.text = text
                current.updatedAt = now
                if (isFinal) finish(current.all) else current.onPartial(current.all)
            }
        }
        override fun onUpdatedEpdData(onMs: Long, offMs: Long) = Unit
        override fun onStartedRecognition() {
            Log.d(TAG, "listening")
        }
        override fun onEndedRecognition() {
            main.post {
                endFeed() // the engine has heard the end; audio after it can crash the engine
                turn?.let { finish(it.all) }
            }
        }
        override fun onError(error: ErrorType) {
            Log.w(TAG, "STT error: $error")
            // The engine can fail at the very end of an utterance: what it heard by then still counts.
            main.post { turn?.let { finish(it.all) } }
        }
        override fun onReady() {
            // Like a microphone: a moment of quiet before the words, while the engine warms up.
            main.post {
                turn?.recording?.let { recording ->
                    // Whole 100 ms blocks only: the engine buffers in blocks and a short last one upsets it.
                    // No pause after: if the engine itself detects the end while audio still comes, it fails.
                    feeding = roomNoise(LEAD_IN) + recording + roomNoise(TAIL + (CHUNK - recording.size % CHUNK) % CHUNK)
                    Log.i(TAG, "stand-in speaks in ${LEAD_IN / 32} ms")
                    feed(feeding!!, 0)
                }
            }
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        // Like the voice, recognition is a bonus: without it the chat still has its sentences.
        try {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        stt.setLocale(Locale.KOREA)
                        ready = true
                        Log.i(TAG, "ready")
                    }
                }

                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "STT unavailable: $error")
                }
            })
            stt.addListener(results)
        } catch (e: SecurityException) {
            Log.w(TAG, "STT not allowed", e)
        }
    }

    val available get() = ready

    /** Listens for one utterance; [onResult] gets its text, or null if nothing was understood. */
    fun listen(onPartial: (String) -> Unit = {}, onResult: (String?) -> Unit) {
        if (!ready) return onResult(null)
        turn?.let { stt.stop() }
        start(Turn(onPartial, onResult, standIn, standInSays))
        if (!standInKeep) {
            standIn = null
            standInSays = null
        }
    }

    /**
     * What [recording] (16 kHz mono 16-bit PCM, one utterance) says, as if it were heard, e.g.
     * the driver's own side of a 보이스톡; false (and no answer) while the recogniser is busy.
     */
    fun recognize(recording: ByteArray, onResult: (String?) -> Unit): Boolean {
        if (!ready || turn != null) return false
        start(Turn({}, onResult, recording, null))
        return true
    }

    private fun start(current: Turn) {
        turn = current
        stt.request()
        // If the engine never answers (it can restart under us), the turn ends empty.
        main.postDelayed({ if (turn === current) finish(current.all) }, TURN_LIMIT_MS + (current.recording?.size ?: 0) / 32L)
    }

    /** Plays a stand-in recording to the recogniser like a microphone would, at the pace it was spoken. */
    private fun feed(recording: ByteArray, from: Int) {
        if (feeding !== recording) return
        if (from >= recording.size) return endFeed()
        stt.sendAudio(recording.copyOfRange(from, minOf(from + CHUNK, recording.size)))
        main.postDelayed({ feed(recording, from + CHUNK) }, CHUNK_MS)
    }

    /** A microphone's faint hiss rather than digital silence, which the engine handles worse. */
    private fun roomNoise(bytes: Int) = ByteArray(bytes).also { noise ->
        val random = java.util.Random(7)
        for (i in 0 until bytes - 1 step 2) {
            val sample = (random.nextGaussian() * 60).toInt()
            noise[i] = sample.toByte()
            noise[i + 1] = (sample shr 8).toByte()
        }
    }

    /** Every fed session is closed exactly once, or the engine's next one goes wrong. */
    private fun endFeed() {
        if (feeding == null) return
        feeding = null
        stt.completeAudioSend()
        // The final result normally follows at once; if the engine went down instead, keep what it heard.
        val current = turn
        main.postDelayed({ if (turn === current) current?.let { finish(it.all) } }, SETTLE_MS)
    }

    fun cancel() {
        if (turn == null) return
        turn = null
        endFeed()
        stt.stop()
    }

    private fun finish(text: String?) {
        val done = turn ?: return
        turn = null
        endFeed()
        val heard = text?.trim()?.takeIf { it.isNotEmpty() }
        val says = done.says?.filter { it.isLetterOrDigit() }
        if (done.recording != null && says != null && heard?.filter { it.isLetterOrDigit() } != says && done.attempt < STAND_IN_ATTEMPTS) {
            Log.i(TAG, "stand-in misheard (${heard ?: "nothing"}), again")
            main.postDelayed({ start(Turn(done.onPartial, done.onResult, done.recording, done.says, done.attempt + 1)) }, 1_500)
            return
        }
        // Still misheard: better nothing than the wrong words (the driver is asked again).
        val result = if (says != null && heard?.filter { it.isLetterOrDigit() } != says) null else heard
        if (result != null && done.recording != null && standIn === done.recording) {
            standIn = null
            standInSays = null
            standInKeep = false
        }
        Log.i(TAG, "heard: ${result ?: "(nothing)"}")
        done.onResult(result)
    }

    fun release() {
        stt.removeListener(results)
        stt.release()
        ready = false
    }

    private companion object {
        const val TAG = "Listener"
        const val CHUNK = 3_200 // 100 ms of 16 kHz mono 16-bit audio, the engine's block
        const val CHUNK_MS = 100L
        const val LEAD_IN = CHUNK * 6
        const val TAIL = CHUNK * 8
        const val SETTLE_MS = 2_500L
        const val STAND_IN_ATTEMPTS = 5
        const val TURN_LIMIT_MS = 12_000L
        const val SEGMENT_GAP_MS = 1_000L
    }
}
