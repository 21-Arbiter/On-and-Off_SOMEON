package com.drivemate.core

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Who a driver is to others. [photo] and [interestPhotos] name pictures the app has. */
@Serializable
data class Profile(
    val name: String,
    val age: Int,
    val intro: String = "",
    val interests: List<String> = emptyList(),
    val photo: String? = null,
    val interestPhotos: List<String> = emptyList(),
    val verified: Boolean = false,
)

/**
 * What a car sends the matching server: the driver's profile and life pattern, never
 * trips. [verification] is the token the server gave when the driver verified their
 * identity, so it holds across server restarts; [Profile.verified] from a car is ignored.
 * [paused]: the driver switched 썸 off, so for now they aren't recommended to anyone.
 */
@Serializable
data class UserUpdate(val profile: Profile, val pattern: LifePattern, val verification: String? = null, val paused: Boolean = false)

/** Why two people were put together; [kind] picks the icon. */
@Serializable
data class Reason(val kind: Kind, val text: String) {
    @Serializable
    enum class Kind { WORK, TIME, PLACE }
}

/** Someone for the driver to meet, with how well their everyday drives fit and why. */
@Serializable
data class Recommendation(
    val userId: String,
    val profile: Profile,
    val score: Int,
    /** Their first routine in a few words, e.g. "평일 08시대 · 판교 삼평동". */
    val commute: String?,
    /** E.g. "수지 풍덕천동 ↔ 판교 삼평동". */
    val route: String?,
    /** Where they are most often (coarse). */
    val home: Area?,
    /** A neighbourhood both drives reach, for meeting. */
    val meet: Area?,
    val reasons: List<Reason>,
)

/** How well [theirs] fits someone, 0–100, with the reasons in words. */
data class Match(val score: Int, val reasons: List<Reason>, val meet: Area?)

/**
 * Scores how much two drivers' everyday drives overlap. For each of my routines the
 * best of theirs counts: going to the same area (most), leaving at the same time of
 * day (as much), from nearby (a little), on the same days. Sharing my two busiest
 * areas adds to it. Not symmetric: it answers "how well do they fit my days".
 */
object Matcher {
    const val MIN_SCORE = 50
    private const val CLOSE_M = 1_500.0
    private const val NEAR_M = 6_000.0
    private const val MORNING_END = 12 * 60
    private const val EVENING_START = 16 * 60

    private class Pair(val mine: Routine, val theirs: Routine, val to: Double, val from: Double, val gap: Int, val fit: Double)

    fun match(mine: LifePattern, theirs: LifePattern): Match {
        val best = mine.routines.mapNotNull { m -> theirs.routines.map { pair(m, it) }.maxByOrNull { it.fit } }
        val routineFit = if (mine.routines.isEmpty()) 0.0 else best.sumOf { it.fit } / mine.routines.size
        val myPlaces = mine.areas.take(2)
        val areaFit = if (myPlaces.isEmpty()) 0.0 else myPlaces.sumOf { a -> theirs.areas.maxOfOrNull { nearness(a, it) } ?: 0.0 } / myPlaces.size
        val score = (100 * (0.8 * routineFit + 0.2 * areaFit)).roundToInt()
        return Match(score, reasons(best, mine, theirs), meetArea(best, mine))
    }

    /**
     * The best fits for [me] among [users], best first; only those scoring [MIN_SCORE] or more.
     * Their areas come [Area.blurred] with [secret], so where they live or work never shows.
     */
    fun recommend(me: String, users: Map<String, UserUpdate>, limit: Int = 5, secret: String = ""): List<Recommendation> {
        val mine = users[me]?.pattern ?: return emptyList()
        return users.filterKeys { it != me }
            .map { (id, user) -> Triple(id, user, match(mine, user.pattern)) }
            .filter { (_, _, match) -> match.score >= MIN_SCORE }
            .sortedByDescending { (_, _, match) -> match.score }
            .take(limit)
            .map { (id, user, match) ->
                val first = user.pattern.routines.firstOrNull()
                Recommendation(
                    userId = id,
                    profile = user.profile,
                    score = match.score,
                    commute = first?.summary(),
                    route = first?.let { "${it.from.name} ↔ ${it.to.name}" },
                    home = user.pattern.areas.firstOrNull()?.let { it.blurred("$secret|$id|${it.name}") },
                    meet = match.meet?.let { it.blurred("$secret|${listOf(me, id).sorted()}|${it.name}") },
                    reasons = match.reasons,
                )
            }
    }

    private fun pair(m: Routine, t: Routine): Pair {
        val to = nearness(m.to, t.to)
        val from = nearness(m.from, t.from)
        val gap = maxOf(0, maxOf(m.departFromMin, t.departFromMin) - minOf(m.departToMin, t.departToMin))
        val days = m.days.count { it in t.days }.toDouble() / m.days.size
        val fit = (0.45 * to + 0.1 * from + 0.45 * timeFit(gap)) * (0.5 + 0.5 * days)
        return Pair(m, t, to, from, gap, fit)
    }

    private fun nearness(a: Area, b: Area): Double {
        val d = a.center.distanceTo(b.center)
        return when {
            d <= CLOSE_M -> 1.0
            d <= NEAR_M -> 0.5
            else -> 0.0
        }
    }

    private fun timeFit(gap: Int) = when {
        gap == 0 -> 1.0
        gap <= 30 -> 0.6
        gap <= 60 -> 0.3
        else -> 0.0
    }

    private fun leg(r: Routine) = when {
        r.departFromMin < MORNING_END -> "출근"
        r.departFromMin >= EVENING_START -> "퇴근"
        else -> "이동"
    }

    /** The strongest few of: same destination, same departure time, same home area. */
    private fun reasons(best: List<Pair>, mine: LifePattern, theirs: LifePattern): List<Reason> {
        val found = mutableListOf<kotlin.Pair<Double, Reason>>()
        outbound(best).maxByOrNull { it.to + if (it.mine.departFromMin < MORNING_END) 0.1 else 0.0 }?.takeIf { it.to > 0 }?.let { p ->
            val place = if (leg(p.mine) == "출근") "출근하는 동네" else "자주 가는 동네"
            found += if (p.to == 1.0) 3.0 to Reason(Reason.Kind.WORK, "$place(${p.theirs.to.name})가 같아요")
            else 1.5 to Reason(Reason.Kind.WORK, "가는 방향이 비슷해요 (${p.theirs.to.name})")
        }
        best.filter { it.gap == 0 }.sortedBy { it.mine.departFromMin }.forEachIndexed { i, p ->
            val t = p.theirs
            found += if (i == 0) 2.5 to Reason(Reason.Kind.TIME, "${t.dayText()} ${leg(p.mine)} 시간대가 비슷해요 (${clock(t.departFromMin)}~${clock(t.departToMin)})")
            else 2.0 to Reason(Reason.Kind.TIME, "${leg(p.mine)} 시간대도 겹쳐요")
        }
        best.filter { it.gap in 1..30 }.minByOrNull { it.gap }?.let { p ->
            found += 1.0 to Reason(Reason.Kind.TIME, "${leg(p.mine)} 시간이 ${p.gap}분 차이예요")
        }
        val myHome = mine.areas.firstOrNull()
        val theirHome = theirs.areas.firstOrNull()
        if (myHome != null && theirHome != null) {
            when (nearness(myHome, theirHome)) {
                1.0 -> found += 2.8 to Reason(Reason.Kind.PLACE, "같은 동네(${theirHome.name})에서 출발해요")
                0.5 -> found += 1.2 to Reason(Reason.Kind.PLACE, "생활권이 가까워요 (${theirHome.name})")
            }
        }
        return found.sortedByDescending { it.first }.take(3).map { it.second }
    }

    /** Drives out rather than home in the evening: where they go says more than home does. */
    private fun outbound(best: List<Pair>) = best.filter { it.mine.departFromMin < EVENING_START }.ifEmpty { best }

    /** Where both drives go (my side of the best destination match), else my busiest area. */
    private fun meetArea(best: List<Pair>, mine: LifePattern): Area? =
        outbound(best).filter { it.to > 0 }.maxByOrNull { it.to }?.mine?.to ?: mine.areas.firstOrNull()
}
