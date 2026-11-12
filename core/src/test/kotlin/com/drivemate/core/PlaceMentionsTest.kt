package com.drivemate.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceMentionsTest {

    private fun place(id: String, name: String) = Place(id, name, "카페", "동네", GeoPoint(37.4, 127.1), "주소")

    private val places = listOf(
        place("drive-brew", "드라이브 브루 정자"),
        place("bookcafe", "백현 북카페"),
        place("terrace", "탄천 테라스"),
        place("forest", "과천 숲 카페"),
    )

    private fun named(said: String) = PlaceMentions.find(said, places).map { it.id }

    @Test
    fun `a place is named without its neighbourhood, however it's spaced`() {
        assertEquals(listOf("drive-brew"), named("정자동에 드라이브 브루라고, 제가 자주 가는 데가 있어요"))
        assertEquals(listOf("drive-brew"), named("드라이브브루 정자에서 봬요"))
    }

    @Test
    fun `a short name has to be said whole`() {
        assertEquals(listOf("bookcafe", "terrace"), named("백현 북카페도 좋고 탄천테라스도 좋아요"))
        assertEquals(emptyList<String>(), named("백현동이나 탄천 쪽에서 볼까요"))
        assertEquals(emptyList<String>(), named("과천 숲길 좋더라고요"))
    }

    @Test
    fun `nothing named, nothing found`() {
        assertEquals(emptyList<String>(), named("혹시 커피 좋아하세요?"))
    }
}
