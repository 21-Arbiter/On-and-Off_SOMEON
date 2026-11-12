package com.drivemate.app.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Short spoken lines through the Pleos on-device TTS, which needs no network or app
 * registration. Lines asked for before the engine is ready are spoken once it is.
 * A line can be in another of the engine's voices (a match's message, apart from the app's own
 * lines); the engine has one voice at a time, so such a line waits for the ones before it.
 * Call from the main thread.
 */
class Announcer(context: Context) {

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private val waiting = mutableListOf<Line>()
    /** Lines not yet handed to the engine: in another voice, they wait for those before them. */
    private val held = ArrayDeque<Line>()
    /** Lines handed to the engine, in order. */
    private val speaking = ArrayDeque<Line>()
    /** The voice the engine speaks in now. */
    private var voice = VOICE

    private class Line(val text: String, val voice: String, val then: (() -> Unit)?)

    private val events = object : EventListener {
        override fun onStart() {
            Log.d(TAG, "speaking")
        }
        override fun onReady() = Unit
        override fun onDone() {
            Log.d(TAG, "done")
            main.post(::next)
        }
        override fun onError(error: ErrorType) {
            Log.w(TAG, "TTS error: $error")
            main.post(::next)
        }
        override fun onStop() {
            main.post(::next)
        }
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        // Voice is a bonus: without it (e.g. the TTS service is missing) the app still works.
        try {
            initialize()
        } catch (e: SecurityException) {
            Log.w(TAG, "TTS not allowed", e)
        }
    }

    private fun initialize() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                main.post {
                    tts.setLocale(Locale.KOREA)
                    // the engine keeps an app's voice from before (it may have been changed then)
                    tts.setSpeakerType(VOICE)
                    voice = VOICE
                    ready = true
                    held += waiting
                    waiting.clear()
                    feed()
                }
            }

            override fun onFailure(error: ErrorType) {
                Log.w(TAG, "TTS unavailable: $error")
            }
        })
        tts.addEventListener(events)
    }

    /** Speaks [line] in [voice] (one of the engine's: eunwoo, sanghyeok, haeun, seoyoon); [then] runs once it has been said (or couldn't be). */
    fun say(line: String, voice: String = VOICE, then: (() -> Unit)? = null) {
        Log.i(TAG, "say: $line")
        if (!ready) {
            waiting += Line(line, voice, null)
            then?.let { main.post(it) }
            return
        }
        held += Line(line, voice, then)
        feed()
    }

    /** Hands the held lines to the engine; one in another voice only once the engine has said those before it. */
    private fun feed() {
        while (held.isNotEmpty()) {
            val line = held.first()
            if (line.voice != voice) {
                if (speaking.isNotEmpty()) return
                tts.setSpeakerType(line.voice)
                voice = line.voice
            }
            held.removeFirst()
            speaking.addLast(line)
            tts.speak(line.text)
            // If the engine never reports back (it can restart under us), go on anyway.
            main.postDelayed({ if (speaking.remove(line)) done(line) }, line.text.length * 200L + 4_000)
        }
    }

    private fun next() {
        speaking.removeFirstOrNull()?.let(::done)
    }

    private fun done(line: Line) {
        line.then?.invoke()
        feed()
    }

    fun release() {
        tts.removeEventListener(events)
        tts.release()
        ready = false
        held.clear()
        speaking.clear()
    }

    companion object {
        private const val TAG = "Announcer"
        /** The app's own voice. */
        const val VOICE = "haeun"
        /** A match's message read out: another voice than the app's, so the driver hears whose words they are. */
        const val MESSAGE_VOICE = "seoyoon"
    }
}
