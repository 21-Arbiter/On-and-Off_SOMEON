package com.drivemate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Reasons offered when reporting; set phrases, no typing in the car. */
private val ReportReasons = listOf(
    "불쾌하거나 무례한 메시지를 보냈어요",
    "프로필이 실제와 달라요",
    "만남을 강요하거나 위협했어요",
    "그 밖의 안전 문제가 있어요",
)

/** Report (which also blocks) or just block someone; over the whole parked screen. */
@Composable
fun ReportSheet(userId: String, name: String, social: Social, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Panel(Modifier.width(720.dp).clickable(interactionSource = MutableInteractionSource(), indication = null) {}, spacing = 16.dp) {
            PanelTitle("${name}님 신고·차단", Icons.Outlined.Flag)
            Muted("신고하면 바로 차단돼요. 서로의 추천·대화·약속에서 사라지고 다시 연락할 수 없어요. 신고 내용은 운영팀이 확인해요.", 20.sp)
            ReportReasons.forEach { reason ->
                Text(
                    reason,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.cardSoft)
                        .clickable {
                            social.report(userId, reason)
                            onClose()
                        }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SheetButton("신고 없이 차단만", palette.neutralButton, palette.text, Modifier.weight(1f)) {
                    social.block(userId)
                    onClose()
                }
                SheetButton("취소", palette.card, palette.textMuted, Modifier.weight(1f), onClose)
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, background: Color, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
    )
}

/**
 * Identity check, done on the phone: the car shows a QR code to the check page and
 * picks up the result by itself. Verified drivers see that they are.
 */
@Composable
fun VerificationPanel(social: Social, modifier: Modifier = Modifier) {
    Panel(modifier) {
        PanelTitle("본인 인증", Icons.Outlined.VerifiedUser)
        if (social.verified) {
            Pill("본인 인증 완료 · 만 19세 이상 확인", background = palette.green)
            Muted("인증한 분만 관심 보내기, 대화, 만남 제안을 할 수 있어요. 추천 카드의 파란 인증 표시가 그 뜻이에요.", 20.sp)
            return@Panel
        }
        // While this is on screen, look for the phone's result often.
        LaunchedEffect(social.verifyUrl) {
            while (true) {
                delay(2_000)
                social.refresh()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            QrCode(social.verifyUrl, Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("휴대폰 카메라로 QR을 찍어\n본인 인증을 해 주세요", fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                Muted("만 19세 이상만 이용할 수 있어요. 인증하면 관심 보내기, 대화, 만남 제안을 할 수 있어요.", 20.sp)
                Muted("차에서 글자를 입력하지 않도록 휴대폰에서 인증해요. 인증을 마치면 이 화면이 저절로 바뀌어요.", 19.sp)
                Muted("공모전 시연용 모의 인증이에요 (실서비스는 통신사 본인확인)", 17.sp)
            }
        }
    }
}

/** A line on meeting safely, for the places screens. */
@Composable
fun SafetyTip() {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Shield, null, tint = palette.green, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Muted("첫 만남은 사람이 많은 공개된 장소에서 하세요. 실시간 위치는 서로 공유되지 않아요. 불편하면 언제든 신고·차단할 수 있어요.", 18.sp)
    }
}
