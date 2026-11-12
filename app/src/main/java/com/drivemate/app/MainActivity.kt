package com.drivemate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import com.drivemate.app.match.Matchmaker
import com.drivemate.app.trip.RecordingService
import com.drivemate.app.ui.Alerts
import com.drivemate.app.ui.DriveMateTheme
import com.drivemate.app.ui.DrivingPartner
import com.drivemate.app.ui.DrivingScreen
import com.drivemate.app.ui.ParkedScreen
import com.drivemate.app.ui.ParkedState
import com.drivemate.app.ui.Social
import com.drivemate.app.ui.Tab
import com.drivemate.app.ui.unread
import com.drivemate.app.voice.VoiceChat
import kotlinx.coroutines.launch
import com.drivemate.core.tripsPerWeekday

class MainActivity : ComponentActivity() {

    private val drive get() = (application as DriveMateApp).drive

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startRecording() }

    /** What waits for the microphone permission: run it if granted, the other if not. */
    private var afterMic: Pair<() -> Unit, () -> Unit>? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        afterMic?.let { (go, refused) -> if (granted) go() else refused() }
        afterMic = null
    }

    /** Voice messages and 보이스톡 need the microphone; asked for the first time while parked. */
    private fun withMic(refused: () -> Unit = {}, go: () -> Unit) {
        if (drive.canRecord()) return go()
        afterMic = go to refused
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startRecording()
        setContent {
            val parked by drive.parked.collectAsState()
            val gear by drive.vehicle.gear.collectAsState()
            val notices by drive.notices.pending.collectAsState()
            val recording by drive.trips.recording.collectAsState()
            val pattern by drive.trips.pattern.collectAsState()
            val trips by drive.trips.store.trips.collectAsState()
            val weekdayTrips = remember(trips) { tripsPerWeekday(trips) }
            val candidates by drive.matches.recommendations.collectAsState()
            val matchStatus by drive.matches.status.collectAsState()
            val inbox by drive.matches.inbox.collectAsState()
            val voiceCall by drive.talk.state.collectAsState()
            val callLevel by drive.talk.level.collectAsState()
            val voiceListening by drive.voice.listening.collectAsState()
            val chosen by drive.voice.chosen.collectAsState()
            val read by drive.matches.read.collectAsState()
            val paused by drive.matches.paused.collectAsState()
            val passenger by drive.vehicle.passenger.collectAsState()
            val alertsOn by drive.alerts.on.collectAsState()
            val offWithPassenger by drive.alerts.offWithPassenger.collectAsState()
            val discreet by drive.discreet.collectAsState()
            val quiet by drive.quiet.collectAsState()
            val alerts = Alerts(alertsOn, offWithPassenger, discreet, quiet, drive.alerts::setOn, drive.alerts::setOffWithPassenger)
            val parkedState = rememberSaveable(saver = ParkedState.Saver) { ParkedState() }
            LaunchedEffect(discreet) { if (!discreet) parkedState.revealed = false } // alone again: shown on asking next time too
            val showDebugLine by drive.showDebugLine.collectAsState()
            val debugLine = if (BuildConfig.DEBUG && showDebugLine) "DEBUG 기어 $gear · 정차 $parked · 대기 소식 ${notices.size} · 기록 $recording · 동승 $passenger · 알림 ${if (quiet) "꺼짐" else "켜짐"} · 썸 ${if (paused) "OFF" else "ON"}" else null
            DriveMateTheme {
                // Only a settled P counts as stopped; any other gear (or an unknown one) gets the driving screen.
                if (parked) {
                    ParkedScreen(
                        parkedState, pattern, weekdayTrips, recording, ::agreeToRecording, drive.trips.store::clear, ::deleteAccount,
                        Social(
                            drive.matches.userId, candidates, emptyMessage(matchStatus), inbox,
                            drive.matches::like, drive.matches::send, drive.matches::places, drive.matches::propose, drive.matches::accept,
                            drive.navigator::navigateTo, drive.matches::block, drive.matches::report,
                            askReport = { id, name -> parkedState.reporting = id to name },
                            askVerification = { parkedState.tab = Tab.SETTINGS },
                            refresh = drive.matches::refresh,
                            verifyUrl = drive.matches.verifyUrl,
                            listen = { partial, done -> withMic({ done(null) }) { drive.listener.listen(partial, done) } },
                            call = { id, name -> withMic { drive.talk.ask(id, name) } },
                            voiceCall = voiceCall,
                            callLevel = callLevel,
                            answerCall = { withMic(drive.talk::hangUp, drive.talk::answer) },
                            hangUp = drive.talk::hangUp,
                            me = drive.matches.profile,
                            read = read,
                            markRead = drive.matches::markRead,
                            paused = paused,
                            setPaused = drive.matches::setPaused,
                            covered = discreet && !parkedState.revealed,
                            reveal = { parkedState.revealed = true },
                            pass = drive.matches::pass,
                        ),
                        alerts, notices, drive.notices::dismissAll, debugLine,
                    )
                } else {
                    // The latest few conversations and whoever is talked with, in the order they matched (the cards stay put).
                    val latest = VoiceChat.latestFirst(inbox.matches)
                    val talkingTo = VoiceChat.talkingTo(latest.map { it.userId to it }, chosen)?.second
                    val partners = (latest.take(3) + listOfNotNull(talkingTo)).distinctBy { it.userId }.sortedBy { it.since }
                        .map { DrivingPartner(it.userId, it.profile, unread(it, read)) }
                    DrivingScreen(
                        notices.size, recording, debugLine, voiceCall, callLevel, drive.talk::hangUp,
                        alerts = alerts,
                        paused = paused,
                        partners = partners,
                        talkingTo = partners.find { it.userId == talkingTo?.userId },
                        onChoose = drive.voice::choose,
                        onVoiceInput = drive.voice::speak,
                        onCall = { p -> withMic { drive.talk.ask(p.userId, p.profile.name) } },
                        listening = voiceListening,
                    )
                }
            }
        }
    }

    private fun emptyMessage(status: Matchmaker.Status) = when (status) {
        Matchmaker.Status.WAITING_FOR_PATTERN -> "자주 다니는 길이 생기면 이동 패턴이 비슷한 분을 추천해 드려요."
        Matchmaker.Status.LOADING -> "이동 패턴이 맞는 분을 찾고 있어요…"
        Matchmaker.Status.OFFLINE -> "추천 서버에 연결하지 못했어요. 잠시 후 다시 시도할게요."
        Matchmaker.Status.READY -> "아직 이동 패턴이 맞는 분이 없어요. 새로운 분이 생기면 알려 드릴게요."
    }

    /** Everything goes: trips, consent, the server account; recording stops until the driver agrees again. */
    private fun deleteAccount() {
        lifecycleScope.launch {
            drive.deleteAccount()
            stopService(Intent(this@MainActivity, RecordingService::class.java))
        }
    }

    /** From the parked screen only: the permission dialog is blocked while driving. */
    private fun agreeToRecording() {
        drive.trips.consent()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    /** Needs consent and permission; the service has to be started while the app is in front. */
    private fun startRecording() {
        drive.trips.start()
        if (drive.trips.recording.value) RecordingService.start(this)
    }
}
