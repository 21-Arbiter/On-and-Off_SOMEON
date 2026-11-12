package com.drivemate.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceChatTest {

    private val jiyeon = "demo-jiyeon" to "지연"
    private val minseo = "demo-minseo" to "민서"

    @Test
    fun `a reply that asks to talk asks for a call, however the words are spaced`() {
        assertTrue(VoiceChat.asksForCall("좋아요 퇴근길에 보이스톡할래요"))
        assertTrue(VoiceChat.asksForCall("보이스 톡 해요"))
        assertTrue(VoiceChat.asksForCall("이따 통화할까요"))
    }

    @Test
    fun `an ordinary reply doesn't`() {
        assertFalse(VoiceChat.asksForCall("안녕하세요 저도 판교로 출근해요"))
    }

    @Test
    fun `what the driver says goes to the latest conversation`() {
        assertEquals(jiyeon to "저 지금 출발했어요", VoiceChat.route("저 지금 출발했어요", jiyeon, listOf(jiyeon, minseo)))
    }

    @Test
    fun `the one picked on the driving screen is talked to while they're still matched`() {
        assertEquals(minseo, VoiceChat.talkingTo(listOf(jiyeon, minseo), "demo-minseo"))
        assertEquals(jiyeon, VoiceChat.talkingTo(listOf(jiyeon, minseo), "demo-gone"))
        assertEquals(jiyeon, VoiceChat.talkingTo(listOf(jiyeon, minseo), null))
        assertEquals(null, VoiceChat.talkingTo(emptyList<Pair<String, String>>(), "demo-minseo"))
    }

    @Test
    fun `starting with a match's name sends it to them, without the name`() {
        assertEquals(minseo to "이따 커피 어때요", VoiceChat.route("민서님 이따 커피 어때요", jiyeon, listOf(jiyeon, minseo)))
        assertEquals(minseo to "주말에 캠핑 가요", VoiceChat.route("민서님께, 주말에 캠핑 가요", jiyeon, listOf(jiyeon, minseo)))
    }
}
