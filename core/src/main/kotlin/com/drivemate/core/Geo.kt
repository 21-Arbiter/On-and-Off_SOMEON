package com.drivemate.core

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class GeoPoint(val lat: Double, val lon: Double) {

    fun distanceTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /** This point moved [north] and [east] metres (a few kilometres at most). */
    fun moved(north: Double, east: Double) = GeoPoint(
        lat + Math.toDegrees(north / EARTH_RADIUS_M),
        lon + Math.toDegrees(east / (EARTH_RADIUS_M * cos(Math.toRadians(lat)))),
    )

    /** The centre of this point's grid square, about 1 km a side in Korea: precise enough for a neighbourhood, not for a home. */
    fun coarse() = GeoPoint(
        (floor(lat / GRID_LAT) + 0.5) * GRID_LAT,
        (floor(lon / GRID_LON) + 0.5) * GRID_LON,
    )

    private companion object {
        const val EARTH_RADIUS_M = 6_371_000.0
        const val GRID_LAT = 0.009 // ≈ 1 km
        const val GRID_LON = 0.0113 // ≈ 1 km at 37.5°N
    }
}
