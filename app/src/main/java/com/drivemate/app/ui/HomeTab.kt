package com.drivemate.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.R
import com.drivemate.app.notice.Notice
import com.drivemate.core.Area
import com.drivemate.core.GeoPoint
import com.drivemate.core.LifePattern
import com.drivemate.core.Reason
import com.drivemate.core.Recommendation
import kotlin.math.abs

/** Home: who the driver is on the road, today's recommendation and how your days meet. */
@Composable
fun HomeTab(
    pattern: LifePattern,
    weekdayTrips: List<Int>,
    recording: Boolean,
    onStartRecording: () -> Unit,
    notices: List<Notice>,
    onNotice: (Notice) -> Unit,
    social: Social,
    alerts: Alerts,
    index: Int,
    onIndex: (Int) -> Unit,
    onChat: (String) -> Unit,
    onTab: (Tab) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Hero(pattern, weekdayTrips, recording, onStartRecording, notices, onNotice, alerts, social.paused, Modifier.fillMaxWidth().height(310.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RecommendationPanel(social, index, onIndex, onChat, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LifeRoutePanel(pattern, social.candidates.getOrNull(index), social.paused || social.covered, Modifier.weight(1f).fillMaxWidth(), covered = social.covered)
                PlacesButton({ onTab(Tab.PLACES) }, Modifier.fillMaxWidth().height(132.dp))
            }
        }
    }
}

@Composable
private fun Hero(
    pattern: LifePattern,
    weekdayTrips: List<Int>,
    recording: Boolean,
    onStartRecording: () -> Unit,
    notices: List<Notice>,
    onNotice: (Notice) -> Unit,
    alerts: Alerts,
    paused: Boolean,
    modifier: Modifier,
) {
    val p = palette
    BoxWithConstraints(
        modifier
            .shadow(p.cardShadow, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(p.heroLeft, p.heroLeft, p.heroRight))),
    ) {
        // The drive photo sits between the title and the profile card, its edges fading into the banner.
        val photoWidth = maxHeight * 2f
        val photoEnd = maxWidth - 440.dp
        val photoStart = photoEnd - photoWidth
        Image(
            painterResource(p.hero),
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.offset(x = photoStart).width(photoWidth).fillMaxHeight(),
        )
        val start = photoStart / maxWidth
        val end = photoEnd / maxWidth
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to p.heroLeft,
                    start to p.heroLeft,
                    start + 0.14f to Color.Transparent,
                    end - 0.06f to Color.Transparent,
                    end to p.heroRight,
                    1f to p.heroRight,
                ),
            ),
        )
        Column(Modifier.padding(start = 44.dp, top = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Wordmark(44.dp, Modifier.padding(top = 8.dp, bottom = 6.dp), lit = !alerts.quiet && !paused)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bar = if (paused) listOf(Color(0xFFB9B6CC), Color(0xFF9C98B8)) else listOf(p.pink, Color(0xFFB45CF0))
                Box(Modifier.width(5.dp).height(64.dp).background(Brush.verticalGradient(bar), RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(16.dp))
                Text(
                    buildAnnotatedString {
                        append("당신의 일상에\n")
                        withStyle(SpanStyle(color = if (paused) p.wordOff else p.pink, fontWeight = FontWeight.Bold)) {
                            append(if (paused) "썸을 잠시 꺼 뒀어요." else "썸을 켜다.")
                        }
                    },
                    fontSize = 25.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (paused) {
                PausedChip()
            } else {
                notices.lastOrNull()?.let { NoticeChip(it, notices.size, alerts.discreet, onNotice) }
            }
        }
        DrivingProfileCard(
            pattern,
            weekdayTrips,
            recording,
            onStartRecording,
            Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).width(470.dp),
        )
    }
}

/**
 * The latest notice; tapping marks them all seen and opens the latest one's conversation, if it has one.
 * With someone else in the car ([discreet]) only how many there are shows, until the driver taps.
 */
@Composable
private fun NoticeChip(notice: Notice, count: Int, discreet: Boolean, onClick: (Notice) -> Unit) {
    Row(
        Modifier
            .background(palette.card.copy(alpha = 0.85f), RoundedCornerShape(50))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(50))
            .clickable { onClick(notice) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Notifications, null, tint = palette.pink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                discreet -> "새 소식 ${count}개 · 동승자가 있어 내용은 숨겼어요"
                count > 1 -> "새 소식 $count · ${notice.title}"
                else -> "새 소식 · ${notice.title}"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(16.dp))
        Text(if (notice.about != null) "보기" else "확인", fontSize = 20.sp, color = palette.pink, fontWeight = FontWeight.Bold)
    }
}

/** In the notice's place while 썸 is off: what rests and what doesn't. */
@Composable
private fun PausedChip() {
    Row(
        Modifier
            .background(palette.card.copy(alpha = 0.85f), RoundedCornerShape(50))
            .border(1.dp, palette.cardBorder, RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.NotificationsOff, null, tint = palette.textMuted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text("꺼 둔 동안 새 추천·알림은 쉬어요 · 대화는 그대로예요", fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

/** The driver's own pattern at a glance, or the way to start recording one. */
@Composable
private fun DrivingProfileCard(pattern: LifePattern, weekdayTrips: List<Int>, recording: Boolean, onStartRecording: () -> Unit, modifier: Modifier) {
    Panel(modifier, padding = 24.dp, spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.GraphicEq, null, tint = palette.pink, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text("내 드라이빙 프로필", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (!recording) {
            Muted("이동 기록을 켜면 자주 다니는 길과 시간대로 프로필이 만들어져요.", 19.sp)
            Text(
                "동의하고 기록 시작",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.pink)
                    .clickable(onClick = onStartRecording)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
            return@Panel
        }
        HairlineDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("최근 이동 ${pattern.trips}회", fontSize = 19.sp, modifier = Modifier.weight(1f))
            WeekBars(weekdayTrips, Modifier.width(130.dp).height(30.dp))
        }
        HairlineDivider()
        val first = pattern.routines.firstOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WorkOutline, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(first?.let { "주 ${it.days.size}회 ${it.to.name} 출근" } ?: "자주 다니는 길을 찾는 중", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        HairlineDivider()
        ProfileLine(Icons.Outlined.LocationOn, "주요 생활권", pattern.areas.firstOrNull()?.name ?: "찾는 중")
        HairlineDivider()
        ProfileLine(Icons.Outlined.Schedule, "주행 시간대", drivingTimes(pattern) ?: "찾는 중")
    }
}

@Composable
private fun ProfileLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 19.sp, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = palette.blue)
    }
}

/** Trips per day of the week, Monday first, as small bars. */
@Composable
private fun WeekBars(counts: List<Int>, modifier: Modifier) {
    val blue = palette.blue
    val most = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        val slot = size.width / counts.size
        val bar = slot * 0.5f
        counts.forEachIndexed { i, n ->
            val h = size.height * (0.15f + 0.85f * n / most)
            drawRoundRect(
                blue.copy(alpha = if (n == 0) 0.25f else 1f),
                topLeft = Offset(i * slot + (slot - bar) / 2, size.height - h),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2),
            )
        }
    }
}

/** E.g. "평일 아침·저녁", from when the routines leave. */
private fun drivingTimes(pattern: LifePattern): String? {
    val routines = pattern.routines.ifEmpty { return null }
    val parts = routines.map {
        when (it.departFromMin / 60) {
            in 5..10 -> "아침"
            in 11..16 -> "낮"
            in 17..22 -> "저녁"
            else -> "밤"
        }
    }.distinct()
    return "${routines.first().dayText()} ${parts.joinToString("·")}"
}

@Composable
fun RecommendationPanel(
    social: Social,
    index: Int,
    onIndex: (Int) -> Unit,
    onChat: (String) -> Unit,
    modifier: Modifier,
) {
    val candidates = social.candidates
    val candidate = candidates.getOrNull(index)
    Panel(modifier) {
        if (social.paused || social.covered) {
            // The card stays underneath, out of focus, with why over it (and the way back).
            PanelTitle("오늘의 추천 인연")
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (candidate != null) {
                    Column(Modifier.fillMaxSize().hushed(if (social.covered) DiscreetBlur else 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Recommended(social, candidates, index, onIndex, onChat)
                    }
                }
                if (social.paused) {
                    PausedCover({ social.setPaused(false) }, Modifier.matchParentSize())
                } else {
                    DiscreetCover(social.reveal, Modifier.matchParentSize())
                }
            }
            return@Panel
        }
        if (candidate == null) {
            PanelTitle("오늘의 추천 인연")
            Muted(social.emptyMessage)
            return@Panel
        }
        PanelTitle("오늘의 추천 인연", trailing = "${index + 1} / ${candidates.size}")
        Recommended(social, candidates, index, onIndex, onChat)
    }
}

/** The card for [candidates] at [index], with the arrows round it and the buttons under it. */
@Composable
private fun ColumnScope.Recommended(social: Social, candidates: List<Recommendation>, index: Int, onIndex: (Int) -> Unit, onChat: (String) -> Unit) {
    val candidate = candidates[index]
    val standing = social.standing(candidate.userId)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        ProfileCard(candidate, standing, onReport = { social.askReport(candidate.userId, candidate.profile.name) }, modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp))
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) {
            onIndex((index - 1).mod(candidates.size))
        }
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) {
            onIndex((index + 1).mod(candidates.size))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // Passed on, they're gone (the next one slides into the place); matched, there's nothing to pass on.
        if (standing != Social.Standing.MATCHED) {
            BigButton("다음에", Icons.Filled.Close, solid(palette.neutralButton), palette.text, Modifier.weight(1f)) {
                social.pass(candidate.userId)
            }
        }
        val pink = Brush.horizontalGradient(listOf(Color(0xFFF77BA3), palette.pinkDeep))
        when (standing) {
            Social.Standing.MATCHED -> BigButton("대화하기", Icons.Outlined.Forum, pink, Color.White, Modifier.weight(1f)) { onChat(candidate.userId) }
            Social.Standing.LIKED -> BigButton("관심 보냈어요", Icons.Filled.Favorite, pink, Color.White, Modifier.weight(1f)) {}
            else -> if (social.verified) {
                BigButton("관심", Icons.Outlined.FavoriteBorder, pink, Color.White, Modifier.weight(1f)) { social.like(candidate.userId) }
            } else {
                BigButton("인증하고 관심 보내기", Icons.Outlined.VerifiedUser, pink, Color.White, Modifier.weight(1f), social.askVerification)
            }
        }
    }
}

private fun solid(color: Color) = Brush.linearGradient(listOf(color, color))

/** With someone else in the car, people's cards and conversations are blurred this much, under an all but opaque [DiscreetCover]. */
val DiscreetBlur = 40.dp
private const val DISCREET_COVER = 0.96f

/** Over people's cards and conversations while someone else is in the car: they show again once the driver is alone, or on asking. */
@Composable
fun DiscreetCover(onReveal: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .background(palette.card.copy(alpha = DISCREET_COVER))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, // nothing underneath can be pressed
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(132.dp).background(palette.neutralButton, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.VisibilityOff, null, tint = palette.textMuted.copy(alpha = 0.7f), modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(30.dp))
        Text("동승자가 있어 잠시 가려 뒀어요", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            "혼자가 되면 다시 보여요.\n함께 봐도 괜찮다면 지금 볼 수 있어요.",
            fontSize = 22.sp,
            lineHeight = 34.sp,
            color = palette.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(34.dp))
        Row(
            Modifier
                .height(64.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.neutralButton)
                .clickable(onClick = onReveal)
                .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Visibility, null, tint = palette.text, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("지금 보기", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Over today's matches while 썸 is off: why they're out of focus, and the way back on. */
@Composable
private fun PausedCover(onOn: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .background(palette.card.copy(alpha = 0.8f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, // nothing underneath can be pressed
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(132.dp).background(palette.neutralButton, CircleShape), contentAlignment = Alignment.Center) {
            PowerSign(palette.textMuted.copy(alpha = 0.5f), Modifier.size(76.dp), weight = 0.13f)
        }
        Spacer(Modifier.height(30.dp))
        Text("썸이 꺼져 있어요", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            "지금은 다른 분의 추천에도 내가 보이지 않아요.\n켜면 오늘의 추천 인연이 다시 보여요.",
            fontSize = 22.sp,
            lineHeight = 34.sp,
            color = palette.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(34.dp))
        Row(
            Modifier
                .height(72.dp)
                .shadow(10.dp, RoundedCornerShape(50), ambientColor = palette.pink, spotColor = palette.pink)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Color(0xFFF77BA3), palette.pinkDeep)))
                .clickable(onClick = onOn)
                .padding(horizontal = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PowerSign(Color.White, Modifier.size(30.dp), weight = 0.15f)
            Spacer(Modifier.width(12.dp))
            Text("썸 켜기", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun ProfileCard(candidate: Recommendation, standing: Social.Standing, onReport: () -> Unit, modifier: Modifier) {
    val p = palette
    Column(
        modifier
            .shadow(p.cardShadow, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(p.profileTop, p.profileBottom)))
            .border(1.dp, p.cardBorder, RoundedCornerShape(26.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.height(262.dp)) {
            Photo(candidate, standing, modifier = Modifier.fillMaxHeight().aspectRatio(0.8f))
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(candidate.profile.name, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("${candidate.profile.age}세", fontSize = 26.sp)
                    Spacer(Modifier.weight(1f))
                    Text("신고·차단", fontSize = 17.sp, color = palette.textMuted, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onReport).padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Pill("이동 패턴 ${candidate.score}% 일치", icon = Icons.Outlined.DirectionsCar)
                when (standing) {
                    Social.Standing.MATCHED -> Pill("매칭 성사", background = p.green)
                    Social.Standing.LIKED_ME -> Pill("회원님께 관심을 보냈어요", background = p.blue)
                    else -> Unit
                }
                if (candidate.profile.intro.isNotEmpty()) {
                    Text("“${candidate.profile.intro}”", fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow(Icons.Outlined.WorkOutline, "출퇴근", candidate.commute ?: "-", size = 20.sp, labelWidth = 120.dp)
            HairlineDivider()
            InfoRow(Icons.Outlined.LocationOn, "주요 생활권", candidate.route ?: candidate.home?.name ?: "-", size = 20.sp, labelWidth = 120.dp)
            HairlineDivider()
            InfoRow(Icons.Outlined.FavoriteBorder, "관심사", candidate.profile.interests.joinToString(", ").ifEmpty { "-" }, size = 20.sp, labelWidth = 120.dp)
        }
        val pictures = candidate.profile.interestPhotos.mapNotNull(::photoRes).take(3)
        // Fills what's left, but no taller than a photo strip should be (the recommend tab has room to spare).
        Row(Modifier.weight(1f, fill = false).heightIn(max = 170.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            pictures.forEach {
                Image(
                    painterResource(it),
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)),
                )
            }
            repeat(3 - pictures.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** The photo, or a placeholder for someone without a public one. */
@Composable
private fun Photo(candidate: Recommendation, standing: Social.Standing, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(22.dp))) {
        val photo = photoRes(candidate.profile.photo)
        if (photo != null) {
            Image(painterResource(photo), candidate.profile.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFB3C9), Color(0xFFB9A6F0)))),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Person, null, tint = Color.White, modifier = Modifier.size(110.dp))
                Text(
                    when (standing) {
                        Social.Standing.MATCHED -> "아직 사진을\n올리지 않았어요"
                        Social.Standing.LIKED -> "상대도 관심을 보이면\n사진이 열려요"
                        else -> "서로 관심을 보이면\n사진이 열려요"
                    },
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (candidate.profile.verified) {
            Icon(
                Icons.Filled.Verified,
                "본인 인증",
                tint = Color(0xFF3D7BFF),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(38.dp).background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(60.dp)
            .shadow(palette.cardShadow, CircleShape)
            .background(palette.card, CircleShape)
            .border(1.dp, palette.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun BigButton(text: String, icon: ImageVector, background: Brush, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(42.dp))
            .background(background)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

/** How the driver's and the candidate's everyday drives meet: a small map, how alike they are, and why. */
@Composable
fun LifeRoutePanel(pattern: LifePattern, candidate: Recommendation?, paused: Boolean, modifier: Modifier, covered: Boolean = false) {
    val myArea = pattern.areas.firstOrNull()
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("라이프 루트 매칭", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedTag("이동 패턴 기반 매칭")
        }
        if (candidate == null) {
            Muted("추천이 생기면 두 분의 이동이 어디서 어떻게 겹치는지 보여 드려요.")
            return@Panel
        }
        val theirs = candidate.home
        val meet = candidate.meet
        val map: (@Composable (Modifier) -> Unit)? =
            if (myArea != null && theirs != null && meet != null) { m -> MiniMap(myArea, theirs, meet, candidate.profile.name, m) } else null
        Box(Modifier.weight(1f).fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxSize().then(if (paused) Modifier.hushed(if (covered) DiscreetBlur else 14.dp) else Modifier)) {
                // A tall panel (the recommendation tab) puts the reasons on top and gives the map the rest.
                if (maxHeight > maxWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
                        LifeRouteReasons(candidate)
                        map?.invoke(Modifier.weight(1f).fillMaxWidth())
                    }
                } else {
                    Row {
                        map?.let {
                            it(Modifier.weight(0.42f).fillMaxHeight())
                            Spacer(Modifier.width(26.dp))
                        }
                        LifeRouteReasons(candidate, Modifier.weight(0.58f))
                    }
                }
            }
            if (paused) Box(Modifier.matchParentSize().background(palette.card.copy(alpha = if (covered) DISCREET_COVER else 0.72f)))
        }
    }
}

/** Why the two fit: how much, the reasons (each with its kind's icon), and where and when their days meet. */
@Composable
private fun LifeRouteReasons(candidate: Recommendation, modifier: Modifier = Modifier) {
    val p = palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("당신과 ${candidate.profile.name}님의\n이동 패턴이 ${alike(candidate.score)}", fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이동 패턴 일치도", fontSize = 19.sp, color = p.textMuted, modifier = Modifier.weight(1f))
                Text("${candidate.score}%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.pink)
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(p.cardSoft)) {
                Box(Modifier.fillMaxWidth(candidate.score / 100f).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(p.pink, Color(0xFFB45CF0)))))
            }
        }
        candidate.reasons.forEach { reason ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (reason.kind) {
                        Reason.Kind.WORK -> Icons.Outlined.WorkOutline
                        Reason.Kind.TIME -> Icons.Outlined.Schedule
                        Reason.Kind.PLACE -> Icons.Outlined.LocationOn
                    },
                    null,
                    tint = p.blue,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(reason.text, fontSize = 20.sp, lineHeight = 28.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            candidate.meet?.let { RouteFact("함께 가는 동네", it.name, Modifier.weight(1f)) }
            candidate.commute?.let { RouteFact("${candidate.profile.name}님 출퇴근", it, Modifier.weight(1f)) }
        }
    }
}

/** One fact of the two drives, small label over a value. */
@Composable
private fun RouteFact(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(palette.cardSoft, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 16.sp, color = palette.textMuted)
        Text(value, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun alike(score: Int) = when {
    score >= 80 -> "매우 비슷해요."
    score >= 65 -> "비슷해요."
    else -> "겹치는 부분이 있어요."
}

/**
 * Both homes and the neighbourhood both drives head to, on an OpenStreetMap map (north up,
 * true distances), each a circle about 3 km across, never a pin: the other's areas come
 * blurred from the server ([Area.blurred]), so no home or workplace can be read off it.
 * Until its tiles arrive, or with no network, a plain street grid stands in.
 */
@Composable
private fun MiniMap(mine: Area, theirs: Area, meetArea: Area, theirName: String, modifier: Modifier) {
    val text = rememberTextMeasurer()
    val p = palette
    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val view = with(LocalDensity.current) {
                val r = Area.SHOWN_RADIUS_M
                val edges = listOf(mine, theirs, meetArea).flatMap { a ->
                    listOf(a.center.moved(r, 0.0), a.center.moved(-r, 0.0), a.center.moved(0.0, r), a.center.moved(0.0, -r))
                }
                MapView.fit(edges, constraints.maxWidth, constraints.maxHeight, 24.dp.toPx(), 40.dp.toPx(), 300.dp.toPx())
            }
            val tiles = rememberMapTiles(view)
            Canvas(Modifier.fillMaxSize()) {
                MapLayer(view, tiles, mine, theirs, meetArea, theirName, text, p)
            }
        }
        // OpenStreetMap's licence: credit it with the map.
        Text("© OpenStreetMap", fontSize = 13.sp, color = p.textMuted, modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 8.dp))
    }
}

private fun DrawScope.MapLayer(view: MapView, tiles: Map<Tile, ImageBitmap>, mine: Area, theirs: Area, meetArea: Area, theirName: String, text: TextMeasurer, p: Palette) {
    val corner = CornerRadius(24.dp.toPx())
    drawRoundRect(p.mapBackground, cornerRadius = corner)
    clipPath(Path().apply { addRoundRect(RoundRect(size.toRect(), corner)) }) {
        val shown = view.tiles.mapNotNull { tile -> tiles[tile]?.let { tile to it } }
        if (shown.isEmpty()) {
            val step = 36.dp.toPx()
            val street = p.card.copy(alpha = 0.55f)
            for (i in 1..(size.width / step).toInt()) drawLine(street, Offset(i * step, 0f), Offset(i * step, size.height), 2.dp.toPx())
            for (i in 1..(size.height / step).toInt()) drawLine(street, Offset(0f, i * step), Offset(size.width, i * step), 2.dp.toPx())
        }
        shown.forEach { (tile, image) ->
            val (offset, tileSize) = view.place(tile)
            drawImage(image, dstOffset = offset, dstSize = tileSize, colorFilter = p.mapFilter, filterQuality = FilterQuality.Medium)
        }
    }

    val me = view.at(mine.center)
    val them = view.at(theirs.center)
    val goal = view.at(meetArea.center)
    // The circles' radius in pixels (Web Mercator stretches north-south as much as east-west).
    fun radius(a: Area) = (view.at(a.center.moved(Area.SHOWN_RADIUS_M, 0.0)) - view.at(a.center)).getDistance()
    val rMe = radius(mine)
    val rThem = radius(theirs)
    val rGoal = radius(meetArea)

    // Each drive runs from its circle's edge to the meeting circle's, bowing away from the other
    // home so the two never lie on top of each other.
    fun road(from: Offset, rFrom: Float, other: Offset): Path? {
        val d = goal - from
        val length = d.getDistance()
        if (length < rFrom + rGoal + 12.dp.toPx()) return null
        val dir = d / length
        val start = from + dir * rFrom
        val end = goal - dir * rGoal
        val span = end - start
        var normal = Offset(-dir.y, dir.x)
        val toOther = other - (from + goal) / 2f
        if (toOther.x * normal.x + toOther.y * normal.y > 0) normal = -normal
        val bow = normal * minOf(span.getDistance() * 0.2f, 36.dp.toPx())
        val c1 = start + span * 0.3f + bow
        val c2 = start + span * 0.8f + bow * 0.7f
        return Path().apply { moveTo(start.x, start.y); cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y) }
    }

    val areas = listOf(Triple(me, rMe, p.blue), Triple(them, rThem, p.pink), Triple(goal, rGoal, p.text))
    areas.forEach { (o, r, color) -> drawCircle(color.copy(alpha = if (color == p.text) 0.10f else 0.16f), r, o) }
    val roads = listOf(road(me, rMe, them) to p.blue, road(them, rThem, me) to p.pink)
    // A pale edge keeps the two drives apart from the map's own roads.
    roads.forEach { (path, color) -> path?.let { drawPath(it, color.copy(alpha = 0.22f), style = Stroke(16.dp.toPx(), cap = StrokeCap.Round)) } }
    roads.forEach { (path, _) -> path?.let { drawPath(it, p.card, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)) } }
    roads.forEach { (path, color) -> path?.let { drawPath(it, color, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)) } }
    // A dashed rim: "somewhere around here".
    val dashes = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
    areas.forEach { (o, r, color) -> drawCircle(color.copy(alpha = 0.7f), r, o, style = Stroke(2.5.dp.toPx(), pathEffect = dashes)) }

    // Each circle's name sits in its middle, over the roads, kept inside the map.
    fun label(label: String, o: Offset, content: Color, background: Color, border: Color) {
        val measured = text.measure(label, TextStyle(fontSize = 17.sp, color = content, fontWeight = FontWeight.Bold))
        val chip = Size(measured.size.width + 22.dp.toPx(), measured.size.height + 10.dp.toPx())
        val margin = 8.dp.toPx()
        val topLeft = Offset(
            (o.x - chip.width / 2).coerceIn(margin, size.width - chip.width - margin),
            (o.y - chip.height / 2).coerceIn(margin, size.height - chip.height - margin),
        )
        val shape = CornerRadius(chip.height / 2)
        drawRoundRect(background, topLeft, chip, shape)
        drawRoundRect(border, topLeft, chip, shape, style = Stroke(1.5.dp.toPx()))
        drawText(measured, topLeft = topLeft + Offset(11.dp.toPx(), 5.dp.toPx()))
    }
    fun dong(area: Area) = area.name.substringAfterLast(' ')
    label("나 · ${dong(mine)} 부근", me, p.blue, p.card, p.blue.copy(alpha = 0.35f))
    label("$theirName · ${dong(theirs)} 부근", them, p.pink, p.card, p.pink.copy(alpha = 0.35f))
    label(dong(meetArea), goal, p.card, p.text, p.text)
}

@Composable
private fun PlacesButton(onClick: () -> Unit, modifier: Modifier) {
    val p = palette
    Box(
        modifier
            .shadow(p.cardShadow, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(p.card)
            .border(1.dp, p.cardBorder, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
    ) {
        Image(
            painterResource(R.drawable.place_cafe),
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(260.dp),
        )
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to p.card, 0.62f to p.card, 0.8f to Color.Transparent)))
        Row(Modifier.align(Alignment.CenterStart).padding(start = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("함께 가기 좋은 만남 장소 보기", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(32.dp))
                }
                Muted("두 사람의 활동 지역과 이동 패턴을 고려해\n만남 장소를 추천해 드려요.", 18.sp)
            }
        }
    }
}
