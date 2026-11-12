package com.drivemate.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MeetingPlacesTest {

    private val scenarioDir = File(System.getProperty("scenarioDir"))
    private fun pattern(name: String) = PatternFinder.find(CommuteScenario.load(File(scenarioDir, "commute_week_$name.csv")).trips)

    private fun place(id: String, lat: Double, lon: Double) = Place(id, id, "카페", "동네", GeoPoint(lat, lon), "주소")

    private val places = listOf(
        place("판교", 37.4010, 127.1085),   // at both workplaces
        place("정자", 37.3660, 127.1065),   // A's home, on B's way south
        place("미금", 37.3505, 127.1090),   // past A's home, on B's way
        place("과천", 37.4300, 126.9900),   // nobody's way
        place("광교", 37.2880, 127.0510),   // past B's home
    )

    @Test
    fun `places on both drives home come first, far ones not at all`() {
        val suggestions = MeetingPlaces.suggest(pattern("A"), pattern("B"), places)
        assertEquals(setOf("판교", "정자", "미금"), suggestions.places.map { it.place.id }.toSet())
        assertTrue(suggestions.places.first().let { it.myDetourM < 1_000 && it.theirDetourM < 1_000 })
        assertEquals("미금", suggestions.places.last().place.id) // past A's home: costs A more
    }

    @Test
    fun `the hint says when both drives home overlap`() {
        assertEquals("두 분 모두 평일 18:40~19:00쯤 퇴근길에 올라요", MeetingPlaces.suggest(pattern("A"), pattern("B"), places).hint)
        assertEquals(null, MeetingPlaces.suggest(pattern("A"), pattern("C"), places).hint) // C leaves at 19:30
    }

    @Test
    fun `distances read in kilometres`() {
        assertEquals("0.8km", km(840))
        assertEquals("0.0km", km(20))
        assertEquals("2.5km", km(2_450))
    }
}
