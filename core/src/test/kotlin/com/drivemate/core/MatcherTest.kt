package com.drivemate.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MatcherTest {

    private val scenarioDir = File(System.getProperty("scenarioDir"))
    private fun pattern(name: String) = PatternFinder.find(CommuteScenario.load(File(scenarioDir, "commute_week_$name.csv")).trips)
    private fun user(name: String, scenario: String) = UserUpdate(Profile(name, 30), pattern(scenario))

    private val users = mapOf(
        "a" to user("A", "A"),
        "jiyeon" to user("지연", "B"),
        "minseo" to user("민서", "C"),
        "haeun" to user("하은", "D"),
        "junho" to user("준호", "E"),
    )

    @Test
    fun `A's best fits are the Pangyo commuters, closest timing first, and not the Gwacheon one`() {
        val recs = Matcher.recommend("a", users)
        assertEquals(listOf("지연", "민서", "하은"), recs.map { it.profile.name })
        assertTrue(Matcher.match(users.getValue("a").pattern, users.getValue("junho").pattern).score < Matcher.MIN_SCORE)
    }

    @Test
    fun `the reasons say where and when the drives meet`() {
        val jiyeon = Matcher.recommend("a", users).first()
        assertEquals(
            listOf(
                "출근하는 동네(판교 삼평동)가 같아요",
                "평일 출근 시간대가 비슷해요 (07:50~08:20)",
                "퇴근 시간대도 겹쳐요",
            ),
            jiyeon.reasons.map { it.text },
        )
        assertEquals("판교 삼평동", jiyeon.meet?.name)
        assertEquals("평일 7시대 · 판교 삼평동", jiyeon.commute)
        assertEquals("수지 풍덕천동 ↔ 판교 삼평동", jiyeon.route)
    }

    @Test
    fun `others' areas come blurred, never their grid square's centre`() {
        val jiyeon = Matcher.recommend("a", users, secret = "s").first()
        val home = users.getValue("jiyeon").pattern.areas.first()
        assertEquals(home.name, jiyeon.home?.name)
        assertTrue(home.center.distanceTo(jiyeon.home!!.center) in 290.0..710.0)
        val meet = Matcher.match(users.getValue("a").pattern, users.getValue("jiyeon").pattern).meet!!
        assertTrue(meet.center.distanceTo(jiyeon.meet!!.center) in 290.0..710.0)
    }

    @Test
    fun `someone from the same neighbourhood is told so`() {
        val haeun = Matcher.recommend("a", users).single { it.profile.name == "하은" }
        assertTrue(haeun.reasons.map { it.text }.toString(), haeun.reasons.any { it.text == "같은 동네(분당 정자동)에서 출발해요" })
    }

    @Test
    fun `a driver without routines yet gets no recommendations`() {
        val fresh = users + ("new" to UserUpdate(Profile("새 운전자", 30), LifePattern()))
        assertEquals(emptyList<Recommendation>(), Matcher.recommend("new", fresh))
    }
}
