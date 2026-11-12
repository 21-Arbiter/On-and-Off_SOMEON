package com.drivemate.core

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A week or so of one driver's trips, for tests and the debug app's seeded history.
 * CSV rows are `date,depart,arrive,from_lat,from_lon,to_lat,to_lon,label` in Korean
 * time; `# expect:` lines give the routines the trips should show
 * ([Routine.describe]), and other `#` lines are notes.
 */
class CommuteScenario(val trips: List<Trip>, val expected: List<String>) {
    companion object {
        fun load(file: File) = parse(file.readLines())

        fun parse(lines: List<String>, zone: ZoneId = PatternFinder.SEOUL): CommuteScenario {
            val expected = lines.filter { it.startsWith("# expect:") }.map { it.removePrefix("# expect:").trim() }
            val trips = lines
                .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("date,") }
                .map { line ->
                    val f = line.split(",").map { it.trim() }
                    val date = LocalDate.parse(f[0])
                    Trip(
                        startMs = date.atTime(LocalTime.parse(f[1])).atZone(zone).toInstant().toEpochMilli(),
                        endMs = date.atTime(LocalTime.parse(f[2])).atZone(zone).toInstant().toEpochMilli(),
                        from = GeoPoint(f[3].toDouble(), f[4].toDouble()),
                        to = GeoPoint(f[5].toDouble(), f[6].toDouble()),
                    )
                }
            return CommuteScenario(trips, expected)
        }
    }
}
