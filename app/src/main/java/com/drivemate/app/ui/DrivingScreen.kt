package com.drivemate.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.R
import com.drivemate.app.voice.VoiceTalk
import com.drivemate.core.Profile

/** A match the driver can talk to while driving, and how much they've said that's unseen. */
class DrivingPartner(val userId: String, val profile: Profile, val unread: Int)

/**
 * The app while driving: one line to read, whom the driver is talking with (a tap picks
 * another), and two things to press: 음성 입력 and 보이스톡 (in a 보이스톡, hanging up instead).
 * Notices and messages are spoken; what waits shows only as a count. While notices or 썸
 * are off the wordmark's ON is out; with someone else in the car it shows no one's face or name.
 */
@Composable
fun DrivingScreen(
    waiting: Int,
    recording: Boolean,
    debugLine: String?,
    call: VoiceTalk.State? = null,
    callLevel: Float = 0f,
    onHangUp: () -> Unit = {},
    alerts: Alerts = Alerts(),
    /** 썸 is switched off (see [Social.paused]). */
    paused: Boolean = false,
    /** The matches to pick from, at most a few, in a steady order. */
    partners: List<DrivingPartner> = emptyList(),
    /** Whom 음성 입력 and 보이스톡 go to (VoiceChat.talkingTo); null when there's nobody yet. */
    talkingTo: DrivingPartner? = null,
    onChoose: (String) -> Unit = {},
    onVoiceInput: () -> Unit = {},
    onCall: (DrivingPartner) -> Unit = {},
    listening: Boolean = false,
) {
    if (call != null) {
        Column(
            Modifier.fillMaxSize().background(palette.background).padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            DrivingCall(call, callLevel, onHangUp)
            if (debugLine != null) Text(debugLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.background, palette.heroRight)))) {
        Column(Modifier.align(Alignment.Center).offset(y = (-30).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark(44.dp, lit = !alerts.quiet && !paused)
            Spacer(Modifier.height(22.dp))
            Text(
                when {
                    alerts.discreet -> "동승자가 있어 알림을 잠시 껐어요"
                    paused -> "썸을 잠시 꺼 뒀어요 · 대화는 그대로예요"
                    alerts.quiet -> "알림을 꺼 두었어요 · 소식은 정차하면 보여 드려요"
                    else -> "운전 중에는 목소리로 주고받아요"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            if (talkingTo == null) {
                Spacer(Modifier.height(48.dp))
                Halo(false, 360.dp) { Image(painterResource(R.drawable.logo_icon), null, modifier = Modifier.size(150.dp)) }
                Spacer(Modifier.height(8.dp))
                Text("매칭된 분이 생기면 여기서 목소리로 대화해요", fontSize = 26.sp, color = palette.textMuted)
                return@Column
            }
            Spacer(Modifier.height(44.dp))
            // Faces and names only with no one else in the car.
            if (!alerts.discreet) PartnerPicker(partners, talkingTo.userId, onChoose)
            Spacer(Modifier.height(if (alerts.discreet) 24.dp else 36.dp))
            Row(verticalAlignment = Alignment.Top) {
                Spacer(Modifier.width(ACTION_GAP + CALL_SIZE)) // keeps 음성 입력 on the centre line
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Halo(listening, 236.dp) {
                        ActionButton(Icons.Filled.Mic, "음성 입력", if (listening) palette.pinkDeep else palette.pink, 150.dp, enabled = !listening, onVoiceInput)
                    }
                    Text(
                        when {
                            listening -> "듣고 있어요"
                            alerts.discreet -> "음성 입력"
                            else -> "${talkingTo.profile.name}님께 말하기"
                        },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (listening) palette.pink else palette.text,
                    )
                }
                Spacer(Modifier.width(ACTION_GAP))
                Column(Modifier.padding(top = (236.dp - CALL_SIZE) / 2), horizontalAlignment = Alignment.CenterHorizontally) {
                    ActionButton(Icons.Filled.Call, "보이스톡", palette.green, CALL_SIZE, enabled = !listening) { onCall(talkingTo) }
                    Spacer(Modifier.height(14.dp))
                    Text("보이스톡", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.textMuted)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (waiting > 0) StatusChip("새 소식 ${waiting}개", Icons.Outlined.Notifications, highlight = true)
            if (recording) StatusChip("이동 기록 중", Icons.Outlined.Route)
        }
        if (debugLine != null) {
            Text(debugLine, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
}

private val CALL_SIZE = 112.dp
private val ACTION_GAP = 56.dp

/** The matches side by side, big enough to tap at a glance; the one talked with is lit. */
@Composable
private fun PartnerPicker(partners: List<DrivingPartner>, current: String, onChoose: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        partners.forEach { p ->
            val on = p.userId == current
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .width(200.dp)
                    .shadow(if (on) 8.dp else 0.dp, shape, ambientColor = palette.pink, spotColor = palette.pink)
                    .clip(shape)
                    .background(if (on) palette.card else palette.card.copy(alpha = 0.55f))
                    .border(if (on) 3.dp else 1.dp, if (on) palette.pink else palette.cardBorder, shape)
                    .clickable { onChoose(p.userId) }
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    PersonPhoto(p.profile, 92.dp)
                    if (p.unread > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(34.dp).background(palette.pink, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (p.unread > 9) "9+" else "${p.unread}", fontSize = 17.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(p.profile.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (on) palette.text else palette.textMuted)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (on) "대화 중" else "눌러서 바꾸기",
                    fontSize = 18.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) palette.pink else palette.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, color: Color, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .shadow(12.dp, CircleShape, ambientColor = color, spotColor = color)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.48f))
    }
}

/** Soft rings round the button, so the screen isn't bare; they breathe while the car listens. */
@Composable
private fun Halo(listening: Boolean, size: Dp, content: @Composable () -> Unit) {
    val breath by rememberInfiniteTransition(label = "listen").animateFloat(1f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "breath")
    val pink = palette.pink
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().scale(if (listening) breath else 1f)) {
            val r = this.size.minDimension / 2
            drawCircle(pink.copy(alpha = 0.06f), r)
            drawCircle(pink.copy(alpha = 0.08f), r * 0.82f)
            drawCircle(pink.copy(alpha = 0.11f), r * 0.66f)
        }
        content()
    }
}

@Composable
private fun StatusChip(text: String, icon: ImageVector, highlight: Boolean = false) {
    val color = if (highlight) Color.White else palette.textMuted
    Row(
        Modifier
            .background(if (highlight) palette.pink else palette.card.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 26.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
