package com.drivemate.core

/**
 * Names for places, from a small built-in table of neighbourhood centres (approximate)
 * around the demo area: southern Seoul, Seongnam, Yongin and Suwon. No network or
 * geocoder needed, and the same answer on every device.
 */
object Neighborhoods {
    private data class Named(val name: String, val center: GeoPoint)

    private val table = listOf(
        Named("분당 정자동", GeoPoint(37.3670, 127.1080)),
        Named("분당 수내동", GeoPoint(37.3780, 127.1150)),
        Named("분당 서현동", GeoPoint(37.3850, 127.1230)),
        Named("분당 이매동", GeoPoint(37.3950, 127.1270)),
        Named("분당 야탑동", GeoPoint(37.4110, 127.1280)),
        Named("분당 구미동", GeoPoint(37.3490, 127.1060)),
        Named("판교 삼평동", GeoPoint(37.4020, 127.1100)),
        Named("판교 백현동", GeoPoint(37.3920, 127.1110)),
        Named("판교 운중동", GeoPoint(37.3930, 127.0800)),
        Named("수지 풍덕천동", GeoPoint(37.3220, 127.0980)),
        Named("수지 죽전동", GeoPoint(37.3240, 127.1070)),
        Named("수지 상현동", GeoPoint(37.2990, 127.0700)),
        Named("광교 이의동", GeoPoint(37.2890, 127.0500)),
        Named("수원 영통동", GeoPoint(37.2510, 127.0710)),
        Named("기흥 구갈동", GeoPoint(37.2750, 127.1150)),
        Named("위례", GeoPoint(37.4790, 127.1450)),
        Named("송파 잠실", GeoPoint(37.5130, 127.1000)),
        Named("강남 역삼동", GeoPoint(37.5000, 127.0360)),
        Named("강남 삼성동", GeoPoint(37.5090, 127.0630)),
        Named("서초 서초동", GeoPoint(37.4900, 127.0150)),
        Named("서초 양재동", GeoPoint(37.4700, 127.0380)),
        Named("과천", GeoPoint(37.4290, 126.9880)),
    )

    private const val MAX_DISTANCE_M = 2_500.0

    fun nameOf(point: GeoPoint): String {
        val nearest = table.minBy { it.center.distanceTo(point) }
        return if (nearest.center.distanceTo(point) <= MAX_DISTANCE_M) nearest.name
        else "%.2f, %.2f 부근".format(point.lat, point.lon)
    }
}
