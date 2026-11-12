package com.drivemate.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/** One drive, from leaving P to stopping in P again. Precise points: these stay on the car. */
@Serializable
data class Trip(val startMs: Long, val endMs: Long, val from: GeoPoint, val to: GeoPoint)

/**
 * Turns the car's parked/driving changes into trips. Moves too short to be a trip
 * (re-parking, a car wash queue) are dropped.
 */
class TripTracker(private val minMs: Long = 60_000, private val minMeters: Double = 300.0) {
    private var wasParked = false
    private var start: Pair<Long, GeoPoint>? = null

    /** Call on every change; [at] is the latest location, if any. Returns a trip when one ends. */
    fun onParkedChanged(parked: Boolean, nowMs: Long, at: GeoPoint?): Trip? {
        if (!parked) {
            // A drive starts only on leaving P, so starting the app mid-drive records nothing.
            if (wasParked) start = at?.let { nowMs to it }
            wasParked = false
            return null
        }
        wasParked = true
        val (startMs, from) = start ?: return null
        start = null
        if (at == null || nowMs - startMs < minMs || from.distanceTo(at) < minMeters) return null
        return Trip(startMs, nowMs, from, at)
    }
}

/** How many [trips] started on each day of the week, Monday first. */
fun tripsPerWeekday(trips: List<Trip>, zone: ZoneId = PatternFinder.SEOUL): List<Int> =
    (1..7).map { day -> trips.count { Instant.ofEpochMilli(it.startMs).atZone(zone).dayOfWeek.value == day } }
