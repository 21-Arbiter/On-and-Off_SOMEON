package com.drivemate.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.R
import com.drivemate.core.Area
import com.drivemate.core.DAY_NAMES
import com.drivemate.core.LifePattern
import com.drivemate.core.Profile
import com.drivemate.core.Routine
import com.drivemate.core.clock

@Composable
private fun TabPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
}

/**
 * The driver's profile as others see it on a recommendation, beside their life pattern
 * in full; or the consent to start recording one.
 */
@Composable
fun ProfileTab(pattern: LifePattern, weekdayTrips: List<Int>, recording: Boolean, onStartRecording: () -> Unit, social: Social) = TabPage {
    if (!recording) {
        Panel(Modifier.fillMaxWidth()) {
            PanelTitle("이동 패턴 기록", Icons.Outlined.Person)
            Text("출퇴근처럼 자주 다니는 길과 시간대를 찾아, 생활 패턴이 비슷한 분을 추천해 드려요.", fontSize = 22.sp)
            Muted("· 운행을 시작하고 마친 위치와 시각만 기록해요")
            Muted("· 정확한 위치는 이 차에만 저장돼요")
            Muted("· 추천에는 동네 이름과 시간대만 쓰여요")
            CoralButton("동의하고 기록 시작", onStartRecording)
        }
        return@TabPage
    }
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        social.me?.let { AsOthersSeeMe(it, pattern, social.verified, Modifier.weight(1f).fillMaxHeight()) }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            PanelTitle("내 생활 패턴", Icons.Outlined.Route, trailing = "기록된 이동 ${pattern.trips}회")
            if (pattern.areas.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pattern.areas.take(4).forEach { Pill(it.name, background = palette.cardSoft, color = palette.text, size = 21.sp) }
                }
            }
            Text("자주 다니는 길", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (pattern.routines.isEmpty()) Muted("같은 길을 같은 시간대에 세 번 이상 다니면 여기에 나와요.")
            pattern.routines.forEach { RoutineRow(it) }
            HairlineDivider()
            Text("요일별 이동", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            WeekChart(weekdayTrips, Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 520.dp))
            Spacer(Modifier.weight(1f))
            Muted("정확한 위치는 이 차에만 저장되고, 추천에는 동네 이름과 시간대만 쓰여요.", 19.sp)
        }
    }
}

/** The recommendation card others get, as it is: no photo until both show interest, areas as neighbourhood names. */
@Composable
private fun AsOthersSeeMe(me: Profile, pattern: LifePattern, verified: Boolean, modifier: Modifier) {
    val first = pattern.routines.firstOrNull()
    Panel(modifier) {
        PanelTitle("상대에게 이렇게 보여요", Icons.Outlined.Person)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonPhoto(me, 150.dp)
            Spacer(Modifier.width(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(me.name, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("${me.age}세", fontSize = 26.sp)
                }
                if (verified) {
                    Pill("본인 인증 완료", background = palette.green, icon = Icons.Outlined.VerifiedUser)
                } else {
                    Pill("본인 인증 전", background = palette.neutralButton, color = palette.text, icon = Icons.Outlined.VerifiedUser)
                }
            }
        }
        if (me.intro.isNotEmpty()) Text("“${me.intro}”", fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow(Icons.Outlined.WorkOutline, "출퇴근", first?.summary() ?: "찾는 중", size = 21.sp, labelWidth = 130.dp)
            HairlineDivider()
            InfoRow(Icons.Outlined.LocationOn, "주요 생활권", first?.let { "${it.from.name} ↔ ${it.to.name}" } ?: "찾는 중", size = 21.sp, labelWidth = 130.dp)
            HairlineDivider()
            InfoRow(Icons.Outlined.FavoriteBorder, "관심사", me.interests.joinToString(", ").ifEmpty { "-" }, size = 21.sp, labelWidth = 130.dp)
        }
        val home = pattern.areas.firstOrNull()
        if (home != null) AreaMap(home, Modifier.weight(1f).fillMaxWidth()) else Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Sharing("상대에게 보여요", Icons.Outlined.Visibility, palette.blue, listOf("이름 · 나이 · 소개 · 관심사", "출퇴근 시간대와 동네 이름", "지도에는 핀 없이 반경 1.5km 원"), Modifier.weight(1f))
            Sharing("보이지 않아요", Icons.Outlined.VisibilityOff, palette.pink, listOf("정확한 위치와 실시간 위치", "운행 기록", "전화번호"), Modifier.weight(1f))
        }
    }
}

/**
 * The driver's neighbourhood as a match's map shows it: a circle about 3 km across, no pin.
 * (Theirs is drawn a few hundred metres off the real centre; this one is where it really is.)
 */
@Composable
private fun AreaMap(area: Area, modifier: Modifier) {
    val p = palette
    val text = rememberTextMeasurer()
    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.mapBackground)) {
            val density = LocalDensity.current
            val view = remember(area, constraints.maxWidth, constraints.maxHeight) {
                val r = Area.SHOWN_RADIUS_M * 1.5
                MapView.fit(listOf(area.center.moved(r, r), area.center.moved(-r, -r)), constraints.maxWidth, constraints.maxHeight, 0f, 0f, with(density) { 300.dp.toPx() })
            }
            val tiles = rememberMapTiles(view)
            Canvas(Modifier.fillMaxSize()) {
                view.tiles.forEach { tile ->
                    val image = tiles[tile] ?: return@forEach
                    val (offset, tileSize) = view.place(tile)
                    drawImage(image, dstOffset = offset, dstSize = tileSize, colorFilter = p.mapFilter, filterQuality = FilterQuality.Medium)
                }
                val o = view.at(area.center)
                val r = (view.at(area.center.moved(Area.SHOWN_RADIUS_M, 0.0)) - o).getDistance()
                drawCircle(p.blue.copy(alpha = 0.16f), r, o)
                drawCircle(p.blue.copy(alpha = 0.7f), r, o, style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))))
                val label = text.measure("나 · ${area.name.substringAfterLast(' ')} 부근", TextStyle(fontSize = 17.sp, color = p.blue, fontWeight = FontWeight.Bold))
                val chip = Size(label.size.width + 22.dp.toPx(), label.size.height + 10.dp.toPx())
                val topLeft = o - Offset(chip.width / 2, chip.height / 2)
                drawRoundRect(p.card, topLeft, chip, CornerRadius(chip.height / 2))
                drawRoundRect(p.blue.copy(alpha = 0.35f), topLeft, chip, CornerRadius(chip.height / 2), style = Stroke(1.5.dp.toPx()))
                drawText(label, topLeft = topLeft + Offset(11.dp.toPx(), 5.dp.toPx()))
            }
        }
        // OpenStreetMap's licence: credit it with the map.
        Text("© OpenStreetMap", fontSize = 13.sp, color = p.textMuted, modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 8.dp))
    }
}

@Composable
private fun Sharing(title: String, icon: ImageVector, tint: Color, items: List<String>, modifier: Modifier) {
    Column(
        modifier.background(palette.cardSoft, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = tint)
        }
        items.forEach { Text("· $it", fontSize = 19.sp, color = palette.textMuted) }
    }
}

/** Trips per day of the week, Monday first: a bar for each day, its count on top. */
@Composable
private fun WeekChart(counts: List<Int>, modifier: Modifier) {
    val most = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val blue = palette.blue
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        counts.forEachIndexed { i, n ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val bar = (maxHeight - 30.dp) * (0.06f + 0.94f * n / most)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${n}회", fontSize = 18.sp, color = if (n == 0) palette.textMuted else palette.text)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(34.dp).height(bar).background(blue.copy(alpha = if (n == 0) 0.25f else 1f), RoundedCornerShape(17.dp)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(DAY_NAMES[i], fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (i >= 5) palette.pink else palette.text)
            }
        }
    }
}

@Composable
private fun RoutineRow(routine: Routine) {
    Column {
        Text("${routine.dayText()} ${clock(routine.departFromMin)}~${clock(routine.departToMin)} 출발", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.pink)
        Text("${routine.from.name} → ${routine.to.name} · ${routine.trips}회", fontSize = 22.sp)
    }
}

@Composable
fun SettingsTab(recording: Boolean, onClearTrips: () -> Unit, social: Social, alerts: Alerts, onDeleteAccount: () -> Unit) = TabPage {
    VerificationPanel(social, Modifier.fillMaxWidth())
    AlertsPanel(alerts, Modifier.fillMaxWidth())
    // side by side at one height, their buttons on one line
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            PanelTitle("이동 기록", Icons.Outlined.Settings)
            Text("이동 기록 ${if (recording) "켜짐" else "꺼짐"}", fontSize = 22.sp)
            Muted("기록을 지우면 매칭 서버에서도 내 정보와 관심, 대화가 모두 지워지고, 생활 패턴과 추천은 처음부터 다시 만들어져요.", 20.sp)
            Spacer(Modifier.weight(1f))
            CoralButton("이동 기록 모두 지우기", onClearTrips)
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            PanelTitle("계정 삭제")
            Muted("이 차의 이동 기록과 동의, 서버의 프로필·관심·대화·약속을 모두 지우고 처음 상태로 돌아가요.", 20.sp)
            Spacer(Modifier.weight(1f))
            var confirming by remember { mutableStateOf(false) }
            CoralButton(if (confirming) "한 번 더 누르면 모두 지워져요" else "계정 삭제") {
                if (confirming) onDeleteAccount() else confirming = true
            }
        }
    }
}

/** Whether the car tells of news by voice, and whether it goes quiet by itself with someone else in the car. */
@Composable
private fun AlertsPanel(alerts: Alerts, modifier: Modifier) {
    Panel(modifier) {
        PanelTitle(
            "썸 알림",
            Icons.Outlined.NotificationsActive,
            trailing = "지금은 동승자가 있어 꺼져 있어요".takeIf { alerts.on && alerts.discreet },
        )
        SettingRow(
            "음성 알림",
            "새 인연, 관심, 매칭, 메시지, 만남 약속을 차가 말로 알려 드려요. 끄면 소식은 화면에만 조용히 쌓여요. 보이스톡 요청은 꺼 둬도 알려 드려요.",
            alerts.on,
            alerts.setOn,
        )
        HairlineDivider()
        SettingRow(
            "동승자가 타면 자동으로 끄기",
            "옆자리나 뒷자리에 누가 앉으면 알림을 끄고, 화면의 이름과 내용도 가려요. 혼자가 되면 다시 켜져요.",
            alerts.offWithPassenger,
            alerts.setOffWithPassenger,
        )
    }
}

@Composable
private fun SettingRow(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Muted(detail, 20.sp)
        }
        Spacer(Modifier.width(32.dp))
        PowerSwitch(on, onChange)
    }
}

@Composable
private fun CoralButton(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(palette.pink)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
    )
}
