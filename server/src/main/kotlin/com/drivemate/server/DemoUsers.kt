package com.drivemate.server

import com.drivemate.core.PlaceMentions
import com.drivemate.core.Profile
import com.drivemate.core.VoiceCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The demo drivers act like people, a little slowly, so a single car can show the
 * whole flow. One who fits a newly arrived car very well shows interest first; one
 * who is liked likes back if the car fits them well enough; they answer messages, and
 * one with recorded lines (resources /voice) answers a 보이스톡 and talks.
 */
class DemoUsers(
    private val users: Users,
    private val scope: CoroutineScope,
    private val likeFirstMs: Long = 45_000,
    private val likeBackMs: Long = 20_000,
    private val replyMs: Long = 8_000,
    private val answerMs: Long = 3_000,
) {
    /** A new car: demo users it suits very well show interest after a while (unless 썸 is off by then). */
    fun onJoined(car: String) {
        profiles.keys.filter { it != car && it in users }.forEach { demo ->
            if (users.fit(demo, car) >= LIKE_FIRST_SCORE) scope.launch {
                delay(likeFirstMs)
                if (!users.paused(car) && !users.passed(car, demo)) users.like(demo, car)
            }
        }
    }

    fun onLiked(by: String, liked: String) {
        if (liked !in profiles || users.matched(by, liked)) return
        scope.launch {
            delay(likeBackMs)
            if (users.fit(liked, by) >= LIKE_BACK_SCORE) users.like(liked, by)
        }
    }

    fun onMessage(from: String, to: String) {
        val replies = profiles[to]?.replies ?: return
        // Asked for a 보이스톡: the call is the answer (one with recorded lines picks up).
        if (users.messages(from, to).lastOrNull { it.said }?.text?.replace(" ", "")?.let { t -> listOf("보이스톡", "통화", "전화").any { it in t } } == true) return
        scope.launch {
            delay(replyMs)
            val sent = users.messages(from, to).count { it.from == to && it.said }
            users.send(to, from, replies[sent % replies.size])
        }
    }

    /** A proposed meeting place: demo users accept it after a moment. */
    fun onProposal(from: String, to: String) {
        if (to !in profiles) return
        scope.launch {
            delay(replyMs)
            users.accept(to, from)
        }
    }

    /**
     * Asked for a 보이스톡: a demo user with recorded lines answers after a moment and says them;
     * a meeting place they name counts as named, as a car would have found it in its driver's words.
     */
    fun onCallAsked(call: VoiceCall, calls: Calls) {
        val lines = VoiceBot.linesOf(call.to)
        if (call.to !in profiles || lines.isEmpty()) return
        val words = VoiceBot.wordsOf(call.to)
        scope.launch {
            delay(answerMs)
            calls.answer(call.id, call.to) ?: return@launch
            val bot = VoiceBot(lines)
            calls.listen(call.id, call.to, bot::hear)
            if (calls.awaitJoined(call.id, call.from, JOIN_MS)) {
                bot.talk(onLine = { i -> calls.mention(call.id, call.to, PlaceMentions.find(words[i], PlaceCatalog.places)) }) { audio ->
                    calls.live(call.id).also { if (it) calls.speak(call.id, call.to, audio) }
                }
            }
            calls.end(call.id)
        }
    }

    class Demo(val scenario: String, val profile: Profile, val replies: List<String>)

    companion object {
        const val LIKE_FIRST_SCORE = 80
        const val LIKE_BACK_SCORE = 70
        const val JOIN_MS = 15_000L

        val profiles = mapOf(
            "demo-jiyeon" to Demo(
                "B",
                Profile("지연", 28, "퇴근길 드라이브랑 맛집 찾아다니는 걸 좋아해요", listOf("드라이브", "맛집", "여행"), "jiyeon", listOf("night", "food", "road"), verified = true),
                listOf("반가워요! 저도 지금 퇴근하려고요. 판교에서 일하세요?", "어 저도 삼평동이에요! 매일 같은 길로 다녔나 봐요", "주말 드라이브 좋아요, 어디로 갈까요?"),
            ),
            "demo-minseo" to Demo(
                "C",
                Profile("민서", 31, "주말엔 캠핑, 평일 아침엔 판교에서 커피 한 잔", listOf("캠핑", "커피", "재즈"), interestPhotos = listOf("road", "cafe"), verified = true),
                listOf("안녕하세요! 저는 백현동으로 출근해요, 반가워요", "커피 좋죠, 퇴근하고 봬요", "주말엔 캠핑 가는데 다음에 같이 가요"),
            ),
            "demo-haeun" to Demo(
                "D",
                Profile("하은", 29, "정자동 탄천 산책 같이 할 분이면 좋겠어요", listOf("전시", "베이킹", "산책"), interestPhotos = listOf("night", "food")),
                listOf("반가워요! 정자동 이웃이네요"),
            ),
            // Fit the demo car (scenario A) less than the three above: they come in when one of those is passed on.
            "demo-seoyun" to Demo(
                "F",
                Profile("서윤", 27, "퇴근하고 탄천 따라 러닝, 주말엔 브런치 맛집 탐방", listOf("러닝", "브런치", "사진"), interestPhotos = listOf("cafe", "night"), verified = true),
                listOf("안녕하세요! 저는 백현동으로 출근해요, 반가워요", "러닝 좋아하시면 탄천에서 같이 뛰어요", "주말 브런치 어때요?"),
            ),
            "demo-yuna" to Demo(
                "G",
                Profile("유나", 30, "출근길에 라디오 들으며 운전하는 게 소소한 행복이에요", listOf("음악", "요가", "드라이브"), interestPhotos = listOf("road", "food")),
                listOf("반가워요! 저는 운중동으로 출근해요", "저도 드라이브 좋아해요"),
            ),
            "demo-junho" to Demo(
                "E",
                Profile("준호", 33, "과천으로 출근해요, 주말엔 등산", listOf("등산", "야구")),
                listOf("안녕하세요!"),
            ),
        )
    }
}
