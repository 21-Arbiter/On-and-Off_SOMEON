package com.drivemate.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaTest {

    private val jeongja = Area("분당 정자동", GeoPoint(37.3660, 127.1065).coarse(), 5)

    @Test
    fun `a blurred area moves a few hundred metres, the same way every time`() {
        val once = jeongja.blurred("secret|car-1|분당 정자동")
        val moved = jeongja.center.distanceTo(once.center)
        assertTrue("moved $moved m", moved in 290.0..710.0)
        assertEquals(once, jeongja.blurred("secret|car-1|분당 정자동"))
        assertEquals(jeongja.name, once.name)
    }

    @Test
    fun `another key or secret moves it elsewhere`() {
        val a = jeongja.blurred("secret|car-1|분당 정자동").center
        assertNotEquals(a, jeongja.blurred("secret|car-2|분당 정자동").center)
        assertNotEquals(a, jeongja.blurred("other|car-1|분당 정자동").center)
    }

    @Test
    fun `the circle drawn for a blurred area still holds its whole grid square`() {
        val c = jeongja.center
        val corners = listOf(-1.0 to -1.0, -1.0 to 1.0, 1.0 to -1.0, 1.0 to 1.0).map { (n, e) -> c.moved(north = n * 500.0, east = e * 500.0) }
        for (i in 0 until 200) {
            val shown = jeongja.blurred("car-$i").center
            corners.forEach { assertTrue(shown.distanceTo(it) < Area.SHOWN_RADIUS_M) }
        }
    }
}
