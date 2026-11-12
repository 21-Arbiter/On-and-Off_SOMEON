package com.drivemate.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PatternFinderTest {

    private val scenarioDir = File(System.getProperty("scenarioDir"))

    @Test
    fun `each commute scenario shows the routines it expects`() {
        val files = scenarioDir.listFiles { f -> f.name.startsWith("commute_") }!!.sorted()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val scenario = CommuteScenario.load(file)
            val routines = PatternFinder.find(scenario.trips).routines.map { it.describe() }
            assertEquals(file.name, scenario.expected, routines)
        }
    }

    @Test
    fun `home and work are the areas, busiest first, and only coarse points leave the trips`() {
        val scenario = CommuteScenario.load(File(scenarioDir, "commute_week_A.csv"))
        val pattern = PatternFinder.find(scenario.trips)
        assertEquals(listOf("분당 정자동", "판교 삼평동", "분당 서현동", "송파 잠실"), pattern.areas.map { it.name })
        val home = scenario.trips.first().from
        assertTrue(pattern.areas.first().center.distanceTo(home) > 50) // snapped to the 1 km grid
        assertEquals(13, pattern.trips)
    }

    @Test
    fun `two trips are not yet a routine`() {
        val scenario = CommuteScenario.load(File(scenarioDir, "commute_week_A.csv"))
        val pattern = PatternFinder.find(scenario.trips.take(4)) // Monday and Tuesday
        assertEquals(emptyList<Routine>(), pattern.routines)
    }

    @Test
    fun `days other than all weekdays are spelled out`() {
        val area = Area("x", GeoPoint(0.0, 0.0), 1)
        assertEquals("월·수", Routine(area, area, listOf(1, 3), 480, 510, 2).dayText())
        assertEquals("주말", Routine(area, area, listOf(6, 7), 600, 620, 3).dayText())
    }
}
