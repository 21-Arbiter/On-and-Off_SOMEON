package com.drivemate.app.notice

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeCenterTest {

    private val spoken = mutableListOf<String>()
    private val center = NoticeCenter { spoken += it }

    @Test
    fun `while driving a notice is only spoken, with a reminder to look once stopped`() {
        center.onParkedChanged(false)
        center.post("새 인연 추천", "출근 시간대가 비슷해요", "새 인연 추천이 도착했어요.")
        assertEquals(listOf("새 인연 추천이 도착했어요. 정차한 뒤 화면에서 확인하세요."), spoken)
        assertEquals(1, center.pending.value.size)
    }

    @Test
    fun `stopping recalls what is waiting, once`() {
        center.onParkedChanged(false)
        center.post("새 인연 추천", "", "새 인연 추천이 도착했어요.")
        center.post("매칭 성사", "", "매칭이 성사됐어요.")
        spoken.clear()
        center.onParkedChanged(true)
        center.onParkedChanged(true)
        assertEquals(listOf("정차했어요. 새 소식 2개를 화면에서 확인하세요."), spoken)
    }

    @Test
    fun `stopping with nothing waiting stays quiet`() {
        center.onParkedChanged(false)
        center.onParkedChanged(true)
        assertEquals(emptyList<String>(), spoken)
    }

    @Test
    fun `notices from before the car's state is known are summed up once it is`() {
        center.post("새 인연 추천", "", "새 인연 추천이 도착했어요.")
        assertEquals(emptyList<String>(), spoken)
        center.onParkedChanged(true)
        assertEquals(listOf("새 소식 1개가 있어요. 화면에서 확인하세요."), spoken)
        center.onParkedChanged(false)
        center.onParkedChanged(true)
        assertEquals("정차했어요. 새 소식 1개를 화면에서 확인하세요.", spoken.last())
    }

    @Test
    fun `in P a notice is spoken as it is, and stays until seen`() {
        center.onParkedChanged(true)
        center.post("새 인연 추천", "", "새 인연 추천이 도착했어요.")
        assertEquals(listOf("새 인연 추천이 도착했어요."), spoken)
        center.dismissAll()
        assertEquals(emptyList<Notice>(), center.pending.value)
    }

    @Test
    fun `while driving a match's message goes to the voice reply, and still waits on the screen`() {
        val answered = mutableListOf<String>()
        center.answerByVoice = { _, name, text -> answered += "$name: $text"; true }
        center.onParkedChanged(false)
        center.message("demo-jiyeon", "지연", "퇴근길 어때요?")
        assertEquals(listOf("지연: 퇴근길 어때요?"), answered)
        assertEquals(emptyList<String>(), spoken)
        assertEquals("퇴근길 어때요?", center.pending.value.single().body)
    }

    @Test
    fun `a message the voice reply can't take, or one while parked, is an ordinary notice`() {
        center.answerByVoice = { _, _, _ -> false }
        center.onParkedChanged(false)
        center.message("demo-jiyeon", "지연", "퇴근길 어때요?")
        center.onParkedChanged(true)
        center.message("demo-jiyeon", "지연", "도착했어요")
        assertEquals("지연님이 메시지를 보냈어요. 정차한 뒤 화면에서 확인하세요.", spoken.first())
        assertEquals("지연님이 메시지를 보냈어요.", spoken.last())
    }

    @Test
    fun `while notices are off the voice says nothing, and messages aren't read out`() {
        val answered = mutableListOf<String>()
        center.answerByVoice = { _, name, _ -> answered += name; true }
        center.quiet = { true }
        center.onParkedChanged(false)
        center.post("지연님이 관심을 보냈어요", "", "지연님이 회원님께 관심을 보냈어요.")
        assertEquals(false, center.message("demo-jiyeon", "지연", "퇴근길 어때요?"))
        center.onParkedChanged(true)
        center.post("지연님과 썸이 켜졌어요", "", "지연님과 썸이 켜졌어요.", match = true)
        assertEquals(emptyList<String>(), answered)
        assertEquals(emptyList<String>(), spoken)
        assertEquals("퇴근길 어때요?", center.pending.value[1].body) // the screen still has it all
        assertEquals(true, center.pending.value[2].match)
    }

    @Test
    fun `notices held at start while off aren't summed up later`() {
        var off = true
        center.quiet = { off }
        center.post("새 인연 추천", "", "새 인연 추천이 도착했어요.")
        off = false
        center.onParkedChanged(true)
        assertEquals(emptyList<String>(), spoken)
    }
}
