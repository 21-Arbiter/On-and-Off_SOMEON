package com.drivemate.app.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.drivemate.app.match.MatchClient
import com.drivemate.core.Place
import com.drivemate.core.PlaceMentions
import com.drivemate.core.VoiceCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * 보이스톡 with a match, hands-free. The driver asks (or answers); once the other side is
 * in, the microphone goes to the server and their voice comes back, 16 kHz mono 16-bit
 * PCM over a WebSocket, 20 ms at a time. Spoken lines say what happens, so it works while
 * driving; hanging up is one big button. The driver's own words go, an utterance at a time,
 * through the car's recogniser for the meeting places they name; only which places reach the
 * server, for the conversation (nothing said is kept). Call from the main thread.
 */
class VoiceTalk(
    private val scope: CoroutineScope,
    private val client: MatchClient,
    private val myId: () -> String,
    private val announcer: Announcer,
    private val listener: Listener,
    private val canRecord: () -> Boolean,
    private val driving: () -> Boolean,
    /** A match's profile photo name (see ui/Photos), for the call screens. */
    private val photoOf: (userId: String) -> String?,
    /** Someone else is in the car and the driver wants quiet then: a request rings without the name and waits for the screen. */
    private val discreet: () -> Boolean = { false },
    /** The places to listen for on a call with [other]: theirs and the driver's to meet at. */
    private val placesOf: suspend (other: String) -> List<Place> = { emptyList() },
) {
    /** A call being asked for, ringing for the driver, or live since [since] (elapsed realtime). */
    data class State(val call: VoiceCall, val name: String, val photo: String?, val since: Long? = null, val incoming: Boolean = false) {
        val live get() = since != null
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /** How loud the call is right now, 0..1 (either side), for the equalizer on the call screens. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    @Volatile private var loudest = 0f

    private val http = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    private var audio: Job? = null
    private var speaker: AudioTrack? = null

    /** On the live call: the places worth listening for, and the driver's utterance being spoken (on the microphone's thread). */
    @Volatile private var places = emptyList<Place>()
    private val utterance = ByteArrayOutputStream()
    private val preRoll = ArrayDeque<ByteArray>()
    private var speaking = false
    private var quietFrames = 0
    /** Utterances waiting for the recogniser, which takes one at a time (main thread). */
    private val unheard = ArrayDeque<ByteArray>()
    private var recognizing = false

    /** Recordings to say instead of the microphone, frame by frame (scripted demos on the emulator). */
    private val standIn = ConcurrentLinkedQueue<ByteArray>()

    fun sayInstead(recording: ByteArray) {
        for (start in recording.indices step FRAME) standIn += recording.copyOfRange(start, minOf(start + FRAME, recording.size))
    }

    /** Asks [other] for a 보이스톡. */
    fun ask(other: String, name: String) {
        if (_state.value != null) return
        scope.launch {
            val call = try {
                client.askCall(myId(), other)
            } catch (e: IOException) {
                Log.w(TAG, "Call not asked", e)
                announcer.say("지금은 보이스톡을 걸 수 없어요.")
                return@launch
            }
            _state.value = State(call, name, photoOf(other))
            announcer.say("${name}님께 보이스톡을 걸었어요.")
            connect(call)
        }
    }

    /** Answers the call ringing for the driver. */
    fun answer() {
        val ringing = _state.value?.takeIf { it.incoming && !it.live } ?: return
        scope.launch {
            try {
                client.answerCall(myId(), ringing.call.id)
                connect(ringing.call)
            } catch (e: IOException) {
                Log.w(TAG, "Call not answered", e)
                finish(null)
            }
        }
    }

    fun hangUp() {
        val current = _state.value ?: return
        scope.launch { runCatching { client.endCall(myId(), current.call.id) } }
        finish("보이스톡을 끊었어요.")
    }

    /** The inbox's word on calls: one asked of the driver rings; one the server no longer has is over. */
    fun onInbox(call: VoiceCall?, nameOf: (String) -> String) {
        val current = _state.value
        val me = myId()
        when {
            current != null && call?.id != current.call.id && socket == null -> finish(null)
            current == null && call != null && call.to == me && !call.live -> ring(call, nameOf(call.from))
        }
    }

    private fun ring(call: VoiceCall, name: String) {
        _state.value = State(call, name, photoOf(call.from), incoming = true)
        // Answered on the speakers, everyone would hear it: the driver decides once stopped.
        if (discreet()) return announcer.say(if (driving()) "보이스톡 요청이 왔어요. 정차한 뒤 화면에서 확인하세요." else "보이스톡 요청이 왔어요.")
        if (!driving()) return announcer.say("${name}님이 보이스톡을 요청했어요.")
        if (!listener.available || !canRecord()) return announcer.say("${name}님이 보이스톡을 요청했어요. 정차한 뒤 받을 수 있어요.")
        announcer.say("${name}님이 보이스톡을 요청했어요. 받으시겠어요?") {
            listener.listen { heard ->
                if (heard != null && listOf("받", "응", "네", "좋아", "그래").any { it in heard }) answer() else hangUp()
            }
        }
    }

    private fun connect(call: VoiceCall) {
        socket = http.newWebSocket(Request.Builder().url(client.callAudioUrl(myId(), call.id)).build(), events)
    }

    private val events = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch {
                when (text) {
                    "live" -> live()
                    "ended" -> finish("보이스톡이 끝났어요.")
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val audio = bytes.toByteArray()
            hear(audio)
            speaker?.write(audio, 0, audio.size)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Call connection lost", t)
            scope.launch { if (socket === webSocket) finish("보이스톡 연결이 끊겼어요.") }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scope.launch { if (socket === webSocket) finish("보이스톡이 끝났어요.") }
        }
    }

    /** Both are in: the speaker opens at once (they may already be talking), the microphone once that's been said. */
    private fun live() {
        val current = _state.value ?: return
        if (current.live || !_state.compareAndSet(current, current.copy(since = SystemClock.elapsedRealtime()))) return
        openSpeaker()
        startMeter()
        scope.launch { places = runCatching { placesOf(current.call.other(myId())) }.getOrDefault(emptyList()) }
        announcer.say("${current.name}님과 연결됐어요.") { openMic() }
    }

    private fun openSpeaker() {
        speaker = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(FRAME * 25)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .apply { play() }
    }

    @SuppressLint("MissingPermission") // canRecord() checks it
    private fun openMic() {
        if (_state.value?.live != true || audio != null) return
        utterance.reset()
        preRoll.clear()
        speaking = false
        audio = scope.launch(Dispatchers.IO) {
            val mic = if (canRecord()) {
                runCatching {
                    AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, FRAME * 10).apply { startRecording() }
                }.getOrNull()
            } else {
                null
            }
            val frame = ByteArray(FRAME)
            try {
                while (isActive) {
                    // The microphone sets the pace; without one, a clock does.
                    if (mic != null) mic.read(frame, 0, FRAME) else delay(20)
                    val out = standIn.poll() ?: frame.takeIf { mic != null } ?: ByteArray(FRAME)
                    hear(out)
                    own(out)
                    socket?.send(out.toByteString())
                }
            } finally {
                mic?.run { stop(); release() }
            }
        }
    }

    /** Keeps the loudest frame since the last tick of [meter]. */
    private fun hear(frame: ByteArray) {
        loudest = maxOf(loudest, (rms(frame) / 6000f).coerceAtMost(1f))
    }

    private fun rms(frame: ByteArray): Float {
        var sum = 0.0
        for (i in 0 until frame.size - 1 step 2) {
            val sample = (frame[i].toInt() and 0xFF) or (frame[i + 1].toInt() shl 8)
            sum += sample.toDouble() * sample
        }
        return kotlin.math.sqrt(sum / (frame.size / 2).coerceAtLeast(1)).toFloat()
    }

    /** The driver's side, frame by frame: from the first loud frame (and a little before) to a pause, one utterance. */
    private fun own(frame: ByteArray) {
        if (places.isEmpty()) return
        val loud = rms(frame) > SPEECH_RMS
        if (!speaking) {
            preRoll.addLast(frame)
            if (preRoll.size > PRE_ROLL) preRoll.removeFirst()
            if (!loud) return
            speaking = true
            quietFrames = 0
            preRoll.forEach(utterance::write)
            preRoll.clear()
            return
        }
        utterance.write(frame)
        quietFrames = if (loud) 0 else quietFrames + 1
        if (quietFrames < PAUSE_FRAMES && utterance.size() < MAX_UTTERANCE) return
        val said = utterance.toByteArray()
        utterance.reset()
        speaking = false
        scope.launch { recognize(said) }
    }

    /** One utterance through the recogniser (the next waits), and on to the server if it names one of [places]. */
    private fun recognize(said: ByteArray?) {
        val current = _state.value?.takeIf { it.live } ?: return
        if (said != null) {
            if (unheard.size < 2) unheard.addLast(said)
            if (recognizing) return
        }
        val next = unheard.removeFirstOrNull() ?: return
        recognizing = listener.recognize(next) { text ->
            recognizing = false
            val named = text?.let { PlaceMentions.find(it, places) }.orEmpty()
            if (named.isNotEmpty()) {
                Log.i(TAG, "Named on the call: ${named.joinToString { it.name }}")
                scope.launch { runCatching { client.mentionPlaces(myId(), current.call.id, named.map { it.id }) } }
            }
            recognize(null)
        }
        if (!recognizing) unheard.clear() // the recogniser is busy with something else: these go unheard
    }

    /** About 15 times a second: the level jumps up with the voice and falls back gently. */
    private var meter: Job? = null

    private fun startMeter() {
        meter?.cancel()
        meter = scope.launch {
            while (isActive) {
                delay(66)
                _level.value = maxOf(loudest, _level.value * 0.6f)
                loudest = 0f
            }
        }
    }

    private fun finish(line: String?) {
        if (_state.value == null) return
        _state.value = null
        meter?.cancel()
        meter = null
        _level.value = 0f
        socket?.let { val s = it; socket = null; s.close(1000, "bye") }
        audio?.cancel()
        audio = null
        speaker?.run { stop(); release() }
        speaker = null
        standIn.clear()
        places = emptyList()
        unheard.clear()
        if (recognizing) listener.cancel()
        recognizing = false
        line?.let(announcer::say)
    }

    private companion object {
        const val TAG = "VoiceTalk"
        const val RATE = 16_000
        const val FRAME = 640 // 20 ms
        const val SPEECH_RMS = 500f
        const val PRE_ROLL = 15 // 300 ms before the first loud frame
        const val PAUSE_FRAMES = 35 // 700 ms of quiet ends an utterance
        const val MAX_UTTERANCE = 16_000 * 2 * 12 // 12 s
    }
}
