package com.drivemate.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripTrackerTest {

    private val home = GeoPoint(37.3672, 127.1079)
    private val work = GeoPoint(37.4021, 127.1102)
    private val tracker = TripTracker()

    @Test
    fun `a trip runs from leaving P to the next P`() {
        tracker.onParkedChanged(true, 0, home)
        assertNull(tracker.onParkedChanged(false, 1_000, home))
        assertEquals(Trip(1_000, 1_201_000, home, work), tracker.onParkedChanged(true, 1_201_000, work))
    }

    @Test
    fun `re-parking a few metres away is not a trip`() {
        tracker.onParkedChanged(true, 0, home)
        tracker.onParkedChanged(false, 1_000, home)
        assertNull(tracker.onParkedChanged(true, 121_000, GeoPoint(37.3674, 127.1080)))
    }

    @Test
    fun `starting the app while driving records nothing until the next drive`() {
        tracker.onParkedChanged(false, 0, home)
        assertNull(tracker.onParkedChanged(true, 1_200_000, work))
        tracker.onParkedChanged(false, 1_300_000, work)
        assertEquals(Trip(1_300_000, 2_500_000, work, home), tracker.onParkedChanged(true, 2_500_000, home))
    }
}
