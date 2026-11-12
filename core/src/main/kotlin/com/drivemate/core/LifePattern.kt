package com.drivemate.core

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** A place the car often starts or ends at; [center] is coarse (about 1 km), see [GeoPoint.coarse]. */
@Serializable
data class Area(val name: String, val center: GeoPoint, val visits: Int) {
    /**
     * This area as someone else sees it: its centre moved 300–700 m in a direction [key] (with a
     * server secret in it) always gives, so asking again and again can't average it back out.
     * Drawn as a circle of [SHOWN_RADIUS_M] it still holds the whole grid square, but no longer
     * says which square it is.
     */
    fun blurred(key: String): Area {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        fun unit(i: Int) = ((hash[i].toInt() and 0xff) shl 8 or (hash[i + 1].toInt() and 0xff)) / 65535.0
        val angle = 2 * Math.PI * unit(0)
        val metres = BLUR_MIN_M + (BLUR_MAX_M - BLUR_MIN_M) * unit(2)
        return copy(center = center.moved(north = metres * Math.cos(angle), east = metres * Math.sin(angle)))
    }

    companion object {
        /** How big to draw an area (metres): a circle, never a pin, so no home or workplace shows. */
        const val SHOWN_RADIUS_M = 1_500.0
        private const val BLUR_MIN_M = 300.0
        private const val BLUR_MAX_M = 700.0
    }
}

/**
 * A drive the car makes again and again: from one area to another, on [days]
 * (ISO numbers, 1 = Monday), leaving between [departFromMin] and [departToMin]
 * (minutes after midnight, rounded out to ten minutes).
 */
@Serializable
data class Routine(
    val from: Area,
    val to: Area,
    val days: List<Int>,
    val departFromMin: Int,
    val departToMin: Int,
    val trips: Int,
) {
    /** E.g. "평일 08시대 · 판교 삼평동": when and where to, in a few words. */
    fun summary() = "${dayText()} ${departFromMin / 60}시대 · ${to.name}"

    /** E.g. "평일 08:00~08:30 분당 정자동 → 판교 삼평동". */
    fun describe() = "${dayText()} ${clock(departFromMin)}~${clock(departToMin)} ${from.name} → ${to.name}"

    fun dayText(): String = when {
        days.all { it <= 5 } && days.size >= 3 -> "평일"
        days.all { it >= 6 } -> "주말"
        else -> days.joinToString("·") { DAY_NAMES[it - 1] }
    }
}

/** What the car's trips say about its driver's everyday life; the only trip data that leaves the car. */
@Serializable
data class LifePattern(val areas: List<Area> = emptyList(), val routines: List<Routine> = emptyList(), val trips: Int = 0)

/** Days of the week, Monday first. */
val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

fun clock(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * Finds the areas and the regular drives in a car's trips. Trip ends within
 * [SAME_PLACE_M] of each other are the same place; drives between the same two
 * places that leave within [SAME_TIME_MIN] of the one before are the same routine,
 * which needs [MIN_TRIPS] trips on as many different days.
 */
object PatternFinder {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private const val SAME_PLACE_M = 600.0
    private const val SAME_TIME_MIN = 45
    private const val MIN_TRIPS = 3

    private class Place(first: GeoPoint) {
        var lat = first.lat
        var lon = first.lon
        var visits = 1
        val center get() = GeoPoint(lat, lon)

        fun add(p: GeoPoint) {
            visits++
            lat += (p.lat - lat) / visits
            lon += (p.lon - lon) / visits
        }
    }

    private class Leg(val from: Place, val to: Place, val start: ZonedDateTime) {
        val minute get() = start.hour * 60 + start.minute
    }

    fun find(trips: List<Trip>, zone: ZoneId = SEOUL): LifePattern {
        val places = mutableListOf<Place>()
        fun placeOf(p: GeoPoint) =
            places.firstOrNull { it.center.distanceTo(p) <= SAME_PLACE_M }?.also { it.add(p) } ?: Place(p).also { places += it }

        val legs = trips.sortedBy { it.startMs }.map { Leg(placeOf(it.from), placeOf(it.to), Instant.ofEpochMilli(it.startMs).atZone(zone)) }
        val areas = places.associateWith { Area(Neighborhoods.nameOf(it.center), it.center.coarse(), it.visits) }

        val routines = legs.filter { it.from !== it.to }
            .groupBy { it.from to it.to }
            .flatMap { (_, group) -> byDepartureTime(group) }
            .filter { run -> run.size >= MIN_TRIPS && run.map { it.start.toLocalDate() }.distinct().size >= MIN_TRIPS }
            .map { run ->
                Routine(
                    from = areas.getValue(run.first().from),
                    to = areas.getValue(run.first().to),
                    days = run.map { it.start.dayOfWeek.value }.distinct().sorted(),
                    departFromMin = run.minOf { it.minute } / 10 * 10,
                    departToMin = (run.maxOf { it.minute } + 9) / 10 * 10,
                    trips = run.size,
                )
            }
            .sortedBy { it.departFromMin }

        return LifePattern(
            areas = places.filter { it.visits >= 2 }.sortedByDescending { it.visits }.map { areas.getValue(it) },
            routines = routines,
            trips = trips.size,
        )
    }

    /** Splits drives into runs whose departure times (time of day) follow each other closely. */
    private fun byDepartureTime(legs: List<Leg>): List<List<Leg>> {
        val runs = mutableListOf<MutableList<Leg>>()
        for (leg in legs.sortedBy { it.minute }) {
            val last = runs.lastOrNull()
            if (last != null && leg.minute - last.last().minute <= SAME_TIME_MIN) last += leg else runs += mutableListOf(leg)
        }
        return runs
    }
}
