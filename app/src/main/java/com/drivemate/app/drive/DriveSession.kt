package com.drivemate.app.drive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.drivemate.app.BuildConfig
import com.drivemate.app.match.MatchClient
import com.drivemate.app.match.Matchmaker
import com.drivemate.app.navi.Navigator
import com.drivemate.app.notice.AlertSettings
import com.drivemate.app.notice.NoticeCenter
import com.drivemate.app.trip.TripRecorder
import com.drivemate.app.vehicle.Gear
import com.drivemate.app.vehicle.PleosVehicleSource
import com.drivemate.app.voice.Announcer
import com.drivemate.app.voice.Listener
import com.drivemate.app.voice.VoiceChat
import com.drivemate.app.voice.VoiceTalk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Driving state, voice, notices, trip recording, recommendations and navigation. Held by the application rather than
 * the activity, so they keep working while navigation is on screen.
 */
class DriveSession(context: Context) {
    private val scope = MainScope()

    val vehicle = PleosVehicleSource(context.applicationContext)
    val announcer = Announcer(context)
    val listener = Listener(context)
    val notices = NoticeCenter(announcer::say)
    val alerts = AlertSettings(context.applicationContext)

    /** Someone else is in the car and the driver wants quiet then: no notices by voice, and no names or contents on the screen. */
    val discreet: StateFlow<Boolean> = combine(vehicle.passenger, alerts.offWithPassenger) { passenger, off -> passenger && off }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** The car keeps notices to the screen: switched off, or [discreet]. */
    val quiet: StateFlow<Boolean> = combine(alerts.on, discreet) { on, discreet -> !on || discreet }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val trips = TripRecorder(context.applicationContext, scope, notices)
    val matches = Matchmaker(context.applicationContext, scope, trips.pattern, trips.recording, notices)
    private val app = context.applicationContext

    /** The microphone (for voice messages and 보이스톡) is allowed once asked for while parked. */
    fun canRecord() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val talk = VoiceTalk(
        scope, MatchClient(BuildConfig.SERVER_URL), { matches.userId }, announcer, listener, ::canRecord, { notices.driving },
        photoOf = { id -> matches.inbox.value.matches.find { it.userId == id }?.profile?.photo },
        discreet = { discreet.value },
        placesOf = { other -> matches.places(other)?.places?.map { it.place }.orEmpty() },
    )
    val voice = VoiceChat(
        announcer, listener, ::canRecord, matches::send, talk::ask,
        inCall = { talk.state.value != null },
        partners = { VoiceChat.latestFirst(matches.inbox.value.matches).map { it.userId to it.profile.name } },
    )
    val navigator = Navigator(context)

    /** A line of internals at the screen's foot: off, unless a developer turns it on from adb (DebugReceiver). */
    val showDebugLine = MutableStateFlow(false)

    /**
     * True once the car has stayed in P for [PARK_SETTLE_MS], false as soon as it leaves P,
     * null until then (app start). The Vehicle SDK reports a stale P for a moment right
     * after subscribing, even while driving.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val parkedOrUnknown: StateFlow<Boolean?> = vehicle.gear
        .map { if (it == Gear.UNKNOWN) null else it == Gear.PARK }
        .distinctUntilChanged()
        .mapLatest { inPark ->
            if (inPark == true) delay(PARK_SETTLE_MS)
            inPark
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Stopped for sure; touch features and permission prompts wait for this. */
    val parked: StateFlow<Boolean> = parkedOrUnknown.map { it == true }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        notices.answerByVoice = voice::answer
        notices.quiet = { quiet.value }
        // Said once as someone sits down, so the driver knows why the car has gone quiet.
        scope.launch { discreet.collect { if (it && alerts.on.value) announcer.say("동승자가 있어 알림을 잠시 꺼 둘게요.") } }
        scope.launch {
            matches.inbox.collect { inbox -> talk.onInbox(inbox.call) { id -> inbox.matches.find { it.userId == id }?.profile?.name ?: "상대" } }
        }
        scope.launch { parkedOrUnknown.collect(notices::onParkedChanged) }
        scope.launch { parked.collect(trips::onParkedChanged) }
    }

    /** Deletes everything: trips on the car, consent, and the account on the server. */
    suspend fun deleteAccount() {
        trips.store.clear()
        trips.revoke()
        matches.deleteAccount()
        notices.dismissAll()
    }

    fun start() {
        announcer.start()
        listener.start()
        vehicle.start()
        trips.start()
        navigator.start()
    }

    private companion object {
        const val PARK_SETTLE_MS = 1_500L
    }
}
