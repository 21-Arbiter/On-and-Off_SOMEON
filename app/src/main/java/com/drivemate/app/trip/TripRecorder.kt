package com.drivemate.app.trip

import android.content.Context
import androidx.core.content.edit
import com.drivemate.app.location.LocationSource
import com.drivemate.app.notice.NoticeCenter
import com.drivemate.core.LifePattern
import com.drivemate.core.PatternFinder
import com.drivemate.core.Trip
import com.drivemate.core.TripTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Records trips once the driver has agreed (and granted location), and keeps the life
 * pattern found in them up to date. A routine seen for the first time becomes a notice.
 */
class TripRecorder(context: Context, scope: CoroutineScope, private val notices: NoticeCenter) {

    private val prefs = context.getSharedPreferences("drive_mate", Context.MODE_PRIVATE)
    private val location = LocationSource(context)
    private val tracker = TripTracker()
    val store = TripStore(context)

    private val _consented = MutableStateFlow(prefs.getBoolean(KEY_CONSENT, false))
    val consented: StateFlow<Boolean> = _consented.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    val pattern: StateFlow<LifePattern> = store.trips.map { PatternFinder.find(it) }
        .stateIn(scope, SharingStarted.Eagerly, PatternFinder.find(store.trips.value))

    fun consent() {
        prefs.edit { putBoolean(KEY_CONSENT, true) }
        _consented.value = true
    }

    /** The driver withdrew (account deleted): no more recording until they agree again. */
    fun revoke() {
        prefs.edit { remove(KEY_CONSENT) }
        _consented.value = false
        _recording.value = false
    }

    /** Starts once there is consent and location permission; safe to call again. */
    fun start() {
        if (!_consented.value) return
        location.startIfPermitted()
        _recording.value = location.started
    }

    fun onParkedChanged(parked: Boolean) {
        if (!_recording.value) return
        tracker.onParkedChanged(parked, System.currentTimeMillis(), location.latest.value)?.let { add(listOf(it)) }
    }

    /** New trips (also the debug app's seeded week); announces routines they reveal. */
    fun add(trips: List<Trip>) {
        val known = pattern.value.routines.map { it.describe() }.toSet()
        store.add(trips)
        val found = PatternFinder.find(store.trips.value).routines.filter { it.describe() !in known }
        if (found.isEmpty()) return
        notices.post(
            title = "자주 다니는 길",
            body = found.joinToString("\n") { it.describe() },
            spoken = if (found.size == 1) "자주 다니는 길을 찾았어요." else "자주 다니는 길을 ${found.size}개 찾았어요.",
        )
    }

    private companion object {
        const val KEY_CONSENT = "locationConsent"
    }
}
