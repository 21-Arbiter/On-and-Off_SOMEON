package com.drivemate.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.voice.VoiceChat
import com.drivemate.core.Conversation
import com.drivemate.core.Meetup
import com.drivemate.core.Message
import com.drivemate.core.Profile
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Sentences to send with a tap, beside saying it. */
private val QuickReplies = listOf(
    "안녕하세요! 출퇴근길이 겹쳐서 반가워요",
    "퇴근하고 커피 한잔 어때요?",
    "이번 주말에 드라이브 어때요?",
)

/** Matches on the left; on the right the open conversation, answered by voice or with a set phrase. */
@Composable
fun MatchesTab(social: Social, chatWith: String?, onChatWith: (String) -> Unit, onPlaces: () -> Unit) {
    val matches = social.inbox.matches
    val open = matches.firstOrNull { it.userId == chatWith } ?: matches.firstOrNull()
    // With someone else in the car the conversations stay underneath, out of focus.
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(28.dp).then(if (social.covered) Modifier.hushed(DiscreetBlur) else Modifier), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Panel(Modifier.width(470.dp).fillMaxHeight(), spacing = 14.dp) {
                PanelTitle("매칭/대화", Icons.Outlined.Forum)
                if (matches.isEmpty()) Muted("서로 \"관심\"을 누르면 매칭되고, 정차 중에 대화를 나눌 수 있어요.")
                matches.forEach { ConversationRow(it, selected = it.userId == open?.userId, myId = social.myId, unread = social.unread(it)) { onChatWith(it.userId) } }
                val waiting = social.inbox.liked.mapNotNull { id -> social.candidates.find { it.userId == id }?.profile?.name }
                if (waiting.isNotEmpty() || social.inbox.likedMe.isNotEmpty()) HairlineDivider()
                if (waiting.isNotEmpty()) Muted("응답을 기다리는 중: ${waiting.joinToString(", ")}", 20.sp)
                if (social.inbox.likedMe.isNotEmpty()) Muted("회원님께 관심을 보낸 분: ${social.inbox.likedMe.joinToString(", ") { it.profile.name }}", 20.sp)
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                if (open == null) {
                    PanelTitle("대화")
                    Muted("매칭된 분과 음성으로 대화할 수 있어요. 주행 중에도 음성 입력으로 보내고, 온 메시지는 읽어 드려요.")
                    return@Panel
                }
                ChatHeader(open, onPlaces, onCall = { social.call(open.userId, open.profile.name) }, calling = social.voiceCall != null) { social.askReport(open.userId, open.profile.name) }
                open.meetup?.let { MeetupLine(it, open.profile.name, social.myId, onPlaces) }
                HairlineDivider()
                val scroll = rememberScrollState()
                LaunchedEffect(open.userId, open.messages.size, social.covered) {
                    if (!social.covered) social.seen(open) // covered, it hasn't been seen
                    scroll.animateScrollTo(scroll.maxValue)
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (open.messages.isEmpty()) Muted("${open.profile.name}님께 먼저 인사해 보세요.")
                    open.messages.forEach {
                        if (it.said) {
                            Bubble(it, mine = it.from == social.myId)
                        } else {
                            Record(it, mine = it.from == social.myId, open.profile.name, open.meetup) { placeId -> social.propose(open.userId, placeId) }
                        }
                    }
                }
                VoiceComposer(social, open)
            }
        }
        if (social.covered) DiscreetCover(social.reveal, Modifier.matchParentSize().padding(28.dp).clip(RoundedCornerShape(28.dp)))
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, selected: Boolean, myId: String, unread: Int, onClick: () -> Unit) {
    val last = conversation.messages.lastOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) palette.pinkSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonPhoto(conversation.profile, 68.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("${conversation.profile.name} · ${conversation.profile.age}세", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    last == null -> "새로 매칭됐어요"
                    !last.said -> describe(last, last.from == myId, conversation.profile.name)
                    last.from == myId -> "나: ${last.text}"
                    else -> last.text
                },
                fontSize = 19.sp,
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (unread > 0) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(34.dp).background(palette.pink, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (unread > 9) "9+" else "$unread", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** 음성 입력: say it and it's sent (a 보이스톡 asked for if that's what was said), or tap a set phrase. */
@Composable
private fun VoiceComposer(social: Social, open: Conversation) {
    var listening by remember(open.userId) { mutableStateOf(false) }
    var heard by remember(open.userId) { mutableStateOf("") }
    var missed by remember(open.userId) { mutableStateOf(false) }
    if (listening) Bubble(Message(social.myId, heard.ifEmpty { "듣고 있어요…" }, System.currentTimeMillis()), mine = true, draft = true)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (listening) palette.pinkDeep else palette.pink)
                .clickable(enabled = !listening) {
                    listening = true
                    heard = ""
                    missed = false
                    social.listen({ heard = it }) { said ->
                        listening = false
                        if (said == null) {
                            missed = true
                        } else {
                            social.send(open.userId, said)
                            if (VoiceChat.asksForCall(said)) social.call(open.userId, open.profile.name)
                        }
                    }
                }
                .padding(horizontal = 30.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Mic, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (listening) "듣고 있어요" else "음성 입력", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(18.dp))
        Muted(if (missed) "잘 듣지 못했어요. 다시 말씀해 주세요." else "말하면 바로 보내져요 · 주행 중에도 돼요 · 문장을 골라도 돼요", 19.sp)
    }
    QuickReplyChips { social.send(open.userId, it) }
}

@Composable
private fun ChatHeader(conversation: Conversation, onPlaces: () -> Unit, onCall: () -> Unit, calling: Boolean, onReport: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PersonPhoto(conversation.profile, 84.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${conversation.profile.name} · ${conversation.profile.age}세", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("이동 패턴 ${conversation.score}% 일치")
                Pill("매칭 성사", background = palette.green)
            }
        }
        // The two actions side by side, reporting quietly under them.
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.5.dp, palette.pink, RoundedCornerShape(50))
                        .clickable(enabled = !calling, onClick = onCall)
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Call, null, tint = palette.pink, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("보이스톡", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.pink)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.neutralButton)
                        .clickable(onClick = onPlaces)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("만남 장소 정하기", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(26.dp))
                }
            }
            Text("신고·차단", fontSize = 18.sp, color = palette.textMuted, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onReport).padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

/** Where the two stand on meeting, one line; tapping it opens the places tab. */
@Composable
private fun MeetupLine(meetup: Meetup, name: String, myId: String, onPlaces: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardSoft)
            .clickable(onClick = onPlaces)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocationOn, null, tint = palette.pink, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            when {
                meetup.accepted -> "약속 장소: ${meetup.place.name}"
                meetup.proposedBy == myId -> "${meetup.place.name} 제안함 · 답을 기다리는 중"
                else -> "${name}님이 ${meetup.place.name}에서 만나자고 했어요"
            },
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text("보기", fontSize = 20.sp, color = palette.pink, fontWeight = FontWeight.Bold)
    }
}

private val TimeOfDay = SimpleDateFormat("a h:mm", Locale.KOREA).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }

@Composable
private fun Bubble(message: Message, mine: Boolean, draft: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .alpha(if (draft) 0.55f else 1f)
                .background(if (mine) palette.pink else palette.bubbleTheirs, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Text(message.text, fontSize = 23.sp, color = if (mine) Color.White else palette.text)
            if (!draft) Text(TimeOfDay.format(message.at), fontSize = 15.sp, color = if (mine) Color(0xDDFFFFFF) else palette.textMuted)
        }
    }
}

/** What [record] says happened, from the driver's side ([mine]: the driver did it). */
private fun describe(record: Message, mine: Boolean, name: String) = when (record.kind) {
    Message.Kind.PROPOSAL -> if (mine) "만남 장소를 제안했어요 · ${record.text}" else "${name}님이 만남 장소를 제안했어요 · ${record.text}"
    Message.Kind.ACCEPTANCE -> "${record.text}에서 만나기로 했어요"
    Message.Kind.MENTION -> "통화에서 ‘${record.text}’ 얘기가 나왔어요"
    else -> record.text
}

/**
 * Something the two did, between the messages: a 보이스톡, a meeting place proposed or agreed on,
 * or one named on the call, which can be proposed from here (unless it's already the meetup's).
 */
@Composable
private fun Record(record: Message, mine: Boolean, name: String, meetup: Meetup?, onPropose: (placeId: String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier
                .background(palette.cardSoft, RoundedCornerShape(50))
                .border(1.dp, palette.cardBorder, RoundedCornerShape(50))
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = when (record.kind) {
                Message.Kind.CALL -> Icons.Outlined.Call
                Message.Kind.ACCEPTANCE -> Icons.Outlined.EventAvailable
                else -> Icons.Outlined.LocationOn
            }
            Icon(icon, null, tint = palette.pink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(describe(record, mine, name), fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Text(TimeOfDay.format(record.at), fontSize = 15.sp, color = palette.textMuted)
            val placeId = record.placeId
            if (record.kind == Message.Kind.MENTION && placeId != null) {
                Spacer(Modifier.width(14.dp))
                if (meetup?.place?.id == placeId) {
                    Text(if (meetup.accepted) "약속 장소예요" else "제안했어요", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.green)
                } else {
                    Text(
                        "만남 장소로 제안",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(palette.pink)
                            .clickable { onPropose(placeId) }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickReplyChips(onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        QuickReplies.forEach { text ->
            Text(
                text,
                fontSize = 21.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.cardSoft)
                    .border(1.dp, palette.pink.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .clickable { onPick(text) }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            )
        }
    }
}

/** A profile photo in a circle, or the name's first letter when there is none. */
@Composable
fun PersonPhoto(profile: Profile, size: Dp) {
    val res = photoRes(profile.photo)
    if (res != null) {
        Image(painterResource(res), profile.name, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(
            Modifier.size(size).background(Brush.verticalGradient(listOf(Color(0xFFFFB3C9), Color(0xFFB9A6F0))), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(profile.name.take(1), fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
