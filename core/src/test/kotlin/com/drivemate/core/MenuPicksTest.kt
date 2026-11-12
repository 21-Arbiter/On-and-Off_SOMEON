package com.drivemate.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuPicksTest {

    private val coldBrew = MenuItem("드라이브 콜드브루", 5_800, signature = true, mentions = 420, suits = listOf("커피", "드라이브"))
    private val sesameLatte = MenuItem("흑임자 라떼", 6_300, mentions = 260, suits = listOf("커피", "맛집"))
    private val cheesecake = MenuItem("바스크 치즈케이크", 7_000, mentions = 190, suits = listOf("베이킹", "맛집"))
    private val granola = MenuItem("그릭요거트 그래놀라", 6_800, mentions = 70, suits = listOf("러닝", "요가", "브런치"))
    private val menu = listOf(coldBrew, sesameLatte, cheesecake, granola)

    @Test
    fun `the signature, and what reviews pick most besides it`() {
        assertEquals(coldBrew, MenuPicks.signature(menu))
        assertEquals(sesameLatte, MenuPicks.mostMentioned(menu))
    }

    @Test
    fun `someone gets the most reviewed thing that goes with their first interest that the menu has`() {
        assertEquals(MenuPicks.Pick(cheesecake, "베이킹"), MenuPicks.forInterests(menu, listOf("전시", "베이킹", "산책")))
        assertEquals(MenuPicks.Pick(coldBrew, null), MenuPicks.forInterests(menu, listOf("등산", "야구"))) // nothing fits: the signature
    }

    @Test
    fun `the two get different things where they can`() {
        val (forJiyeon, forMinjun) = MenuPicks.forTwo(menu, theirs = listOf("드라이브", "맛집", "여행"), mine = listOf("드라이브", "커피", "러닝"))
        assertEquals(MenuPicks.Pick(coldBrew, "드라이브"), forJiyeon)
        assertEquals(MenuPicks.Pick(sesameLatte, "커피"), forMinjun)
    }

    @Test
    fun `prices read in won`() {
        assertEquals("5,800원", won(5_800))
    }
}
