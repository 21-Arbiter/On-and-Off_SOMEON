package com.drivemate.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** A public place to meet, e.g. a partner café; [address] is street level at most. */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val category: String,
    val area: String,
    val point: GeoPoint,
    val address: String,
    val photo: String? = null,
    val parking: Boolean = false,
    /** A partner's offer for people who meet there. */
    val offer: String? = null,
    /** Where to park instead, when the place has no parking of its own. */
    val carPark: CarPark? = null,
    val menu: List<MenuItem> = emptyList(),
    /** Its reviews: the average out of 5, and how many. */
    val rating: Double? = null,
    val reviews: Int = 0,
)

/** A public car park near a place without parking: how far a walk, and what it costs ([fee] as posted). */
@Serializable
data class CarPark(val name: String, val point: GeoPoint, val walkMin: Int, val fee: String)

/**
 * Something on a place's menu (won): the house's own [signature], how many reviews pick it ([mentions]),
 * and the interests (as in [Profile.interests]) it goes with.
 */
@Serializable
data class MenuItem(val name: String, val price: Int, val signature: Boolean = false, val mentions: Int = 0, val suits: List<String> = emptyList())

/** What to have at a place. */
object MenuPicks {
    /** An item for someone, and the interest of theirs it goes with (null: none does, so the house's signature). */
    data class Pick(val item: MenuItem, val interest: String?)

    fun signature(menu: List<MenuItem>): MenuItem? = menu.firstOrNull { it.signature }

    /** What reviews pick most, the signature aside. */
    fun mostMentioned(menu: List<MenuItem>): MenuItem? = menu.filterNot { it.signature }.maxByOrNull { it.mentions }

    /** For [interests] (first the strongest): the most reviewed item that goes with one of them; else the signature. */
    fun forInterests(menu: List<MenuItem>, interests: List<String>, besides: MenuItem? = null): Pick? {
        val choices = menu.filter { it != besides }
        interests.forEach { interest ->
            choices.filter { interest in it.suits }.maxByOrNull { it.mentions }?.let { return Pick(it, interest) }
        }
        return (signature(choices) ?: choices.maxByOrNull { it.mentions })?.let { Pick(it, null) }
    }

    /** One for the match from their interests, and one for the driver from theirs, two different things where the menu allows. */
    fun forTwo(menu: List<MenuItem>, theirs: List<String>, mine: List<String>): Pair<Pick?, Pick?> {
        val forThem = forInterests(menu, theirs)
        return forThem to (forInterests(menu, mine, besides = forThem?.item) ?: forInterests(menu, mine))
    }
}

/** E.g. 6,500원. */
fun won(price: Int) = String.format(java.util.Locale.KOREA, "%,d원", price)

/** A place for two people, with how far it takes each off their drive home (metres). */
@Serializable
data class PlaceSuggestion(val place: Place, val myDetourM: Int, val theirDetourM: Int, val reasons: List<String>)

/** Places for a pair, best first; [hint] says when both are on the road, if they are at the same time. */
@Serializable
data class PlaceSuggestions(val hint: String? = null, val places: List<PlaceSuggestion> = emptyList())

/** A meeting place one side proposed; settled once the other accepts. */
@Serializable
data class Meetup(val place: Place, val proposedBy: String, val accepted: Boolean = false)

/**
 * Picks places on both people's way home: for each, how much farther each would drive
 * from where their evening drive starts to where it ends. Fair beats short: a place
 * that costs one of them much more than the other ranks lower.
 */
object MeetingPlaces {
    private const val MAX_DETOUR_M = 5_000.0
    private const val EVENING_START = 16 * 60

    fun suggest(mine: LifePattern, theirs: LifePattern, places: List<Place>, limit: Int = 3): PlaceSuggestions {
        val myWay = homeward(mine)
        val theirWay = homeward(theirs)
        val picks = places
            .map { Triple(it, detour(myWay, mine.areas.firstOrNull(), it.point), detour(theirWay, theirs.areas.firstOrNull(), it.point)) }
            .filter { (_, a, b) -> max(a, b) <= MAX_DETOUR_M }
            .sortedBy { (_, a, b) -> a + b + abs(a - b) / 2 }
            .take(limit)
            .map { (place, a, b) -> PlaceSuggestion(place, a.roundToInt(), b.roundToInt(), reasons(place)) }
        return PlaceSuggestions(hint(myWay, theirWay), picks)
    }

    /** The first evening routine: the drive home. */
    private fun homeward(p: LifePattern): Routine? = p.routines.filter { it.departFromMin >= EVENING_START }.minByOrNull { it.departFromMin }

    /** Extra distance to call at [p] on the way home; without one, there and back from the busiest area. */
    private fun detour(way: Routine?, area: Area?, p: GeoPoint): Double = when {
        way != null -> max(0.0, way.from.center.distanceTo(p) + p.distanceTo(way.to.center) - way.from.center.distanceTo(way.to.center))
        area != null -> 2 * area.center.distanceTo(p)
        else -> Double.MAX_VALUE
    }

    private fun reasons(place: Place) = buildList {
        add("${place.area}에 있어요")
        if (place.parking) add("주차할 수 있어요") else place.carPark?.let { add("공영주차장 걸어서 ${it.walkMin}분") }
        place.offer?.let { add("제휴 매장 · $it") }
        MenuPicks.signature(place.menu)?.let { add("시그니처 · ${it.name}") }
    }

    /** E.g. "두 분 모두 평일 18:40~19:00쯤 퇴근길에 올라요", when the drives home overlap. */
    private fun hint(a: Routine?, b: Routine?): String? {
        if (a == null || b == null) return null
        val from = max(a.departFromMin, b.departFromMin)
        val to = minOf(a.departToMin, b.departToMin)
        if (from >= to) return null
        val days = a.days.filter { it in b.days }
        val dayText = Routine(a.from, a.to, days, from, to, 0).dayText()
        return "두 분 모두 $dayText ${clock(from)}~${clock(to)}쯤 퇴근길에 올라요"
    }
}

/**
 * Which of [places] someone named in what they said on a 보이스톡, e.g. "정자동에 드라이브 브루라고…"
 * names 드라이브 브루 정자. Spaces don't matter, and a name may come without its last word (often
 * the neighbourhood) when what's left still tells it apart (four letters or more).
 */
object PlaceMentions {
    fun find(said: String, places: List<Place>): List<Place> {
        val text = plain(said)
        return places.filter { place ->
            val words = place.name.split(" ")
            val short = plain(words.dropLast(1).joinToString(""))
            plain(place.name) in text || (words.size > 1 && short.length >= 4 && short in text)
        }
    }

    private fun plain(s: String) = s.filter { it.isLetterOrDigit() }
}

/** Kilometres with one decimal, e.g. 840 → "0.8km". */
fun km(metres: Int) = "${(metres + 50) / 100 / 10.0}km"
