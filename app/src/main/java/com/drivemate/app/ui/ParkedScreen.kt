package com.drivemate.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.R
import com.drivemate.app.notice.Notice
import com.drivemate.core.LifePattern

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Outlined.Home),
    RECOMMEND("오늘의 추천", Icons.Outlined.StarOutline),
    MATCHES("매칭/대화", Icons.Outlined.Forum),
    PLACES("만남 장소", Icons.Outlined.LocationOn),
    PROFILE("내 프로필", Icons.Outlined.Person),
    SETTINGS("설정", Icons.Outlined.Settings),
}

/**
 * Where the parked screen is: tab, recommendation shown, open conversation. The
 * activity keeps it, so after a drive (the driving screen replaces this one) the
 * driver comes back to the same place; [Saver] keeps it when the activity is made
 * again, as on a day/night switch (dusk, a tunnel).
 */
class ParkedState {
    var tab by mutableStateOf(Tab.HOME)
    var shown by mutableIntStateOf(0)
    var chatWith by mutableStateOf<String?>(null)

    /** Someone being reported or blocked (user id to name), while the sheet is open. */
    var reporting by mutableStateOf<Pair<String, String>?>(null)

    /** The driver chose to show the screen with someone else in the car (until they're alone again). */
    var revealed by mutableStateOf(false)

    /** The match notices whose moment (MatchMoment) the driver has seen. */
    var celebrated by mutableStateOf(emptySet<Long>())

    fun openChat(userId: String) {
        chatWith = userId
        tab = Tab.MATCHES
    }

    companion object {
        val Saver = listSaver<ParkedState, Any?>(
            save = { listOf(it.tab.name, it.shown, it.chatWith, it.celebrated.toLongArray()) },
            restore = { (tab, shown, chatWith, celebrated) ->
                ParkedState().apply {
                    this.tab = Tab.valueOf(tab as String)
                    this.shown = shown as Int
                    this.chatWith = chatWith as String?
                    this.celebrated = (celebrated as LongArray).toSet()
                }
            },
        )
    }
}

/** The app while stopped in P: a menu on the left and the chosen tab beside it. */
@Composable
fun ParkedScreen(
    state: ParkedState,
    pattern: LifePattern,
    weekdayTrips: List<Int>,
    recording: Boolean,
    onStartRecording: () -> Unit,
    onClearTrips: () -> Unit,
    onDeleteAccount: () -> Unit,
    social: Social,
    alerts: Alerts,
    notices: List<Notice>,
    onNoticesSeen: () -> Unit,
    debugLine: String?,
) {
    var tab by state::tab
    val candidates = social.candidates
    val index = if (candidates.isEmpty()) 0 else state.shown.coerceIn(0, candidates.lastIndex)
    val openChat = state::openChat
    val openNotice: (Notice) -> Unit = { notice ->
        onNoticesSeen()
        notice.about?.let(openChat)
        if (notice.aboutPlace) tab = Tab.PLACES
    }

    Row(Modifier.fillMaxSize().background(palette.background)) {
        NavRail(tab, onSelect = { tab = it }, unread = social.inbox.matches.sumOf(social::unread), lit = !alerts.quiet && !social.paused, social.paused, social.setPaused)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (tab) {
                Tab.HOME -> HomeTab(
                    pattern, weekdayTrips, recording, onStartRecording, notices, openNotice,
                    social, alerts, index, { state.shown = it }, openChat, onTab = { tab = it },
                )
                Tab.RECOMMEND -> Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    RecommendationPanel(social, index, { state.shown = it }, openChat, Modifier.weight(1f).fillMaxHeight())
                    LifeRoutePanel(pattern, candidates.getOrNull(index), social.paused || social.covered, Modifier.weight(1f).fillMaxHeight(), covered = social.covered)
                }
                Tab.MATCHES -> MatchesTab(social, state.chatWith, { state.chatWith = it }, onPlaces = { tab = Tab.PLACES })
                Tab.PLACES -> PlacesTab(social, state.chatWith, { state.chatWith = it })
                Tab.PROFILE -> ProfileTab(pattern, weekdayTrips, recording, onStartRecording, social)
                Tab.SETTINGS -> SettingsTab(recording, onClearTrips, social, alerts, onDeleteAccount)
            }
            if (debugLine != null) {
                Text(debugLine, fontSize = 16.sp, color = palette.textMuted, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 4.dp))
            }
            social.voiceCall?.let { CallIsland(it, social.callLevel, social.answerCall, social.hangUp, Modifier.align(Alignment.TopCenter).padding(top = 3.dp)) } // in the margin above every tab's content
        }
    }
    state.reporting?.let { (userId, name) ->
        ReportSheet(userId, name, social) {
            state.reporting = null
            if (state.chatWith == userId) state.chatWith = null
        }
    }
    // A new match, once per match notice; not while it would show a face to someone else in the car.
    if (!alerts.discreet) {
        notices.firstOrNull { it.match && it.id !in state.celebrated }?.let { notice ->
            social.inbox.matches.find { it.userId == notice.about }?.let { match ->
                val done = { state.celebrated += notice.id }
                MatchMoment(social.me, match.profile, onChat = { done(); openChat(match.userId) }, onLater = done)
            }
        }
    }
}

@Composable
private fun NavRail(selected: Tab, onSelect: (Tab) -> Unit, unread: Int, lit: Boolean, paused: Boolean, setPaused: (Boolean) -> Unit) {
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(palette.rail)
            .padding(horizontal = 16.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_icon), null, modifier = Modifier.size(104.dp))
            Spacer(Modifier.height(18.dp))
            Wordmark(24.dp, lit = lit)
            Spacer(Modifier.height(26.dp))
            // 썸 itself: off, the driver steps out of recommendations for a while.
            PowerSwitch(!paused, { setPaused(!it) }, label = "썸", width = 164.dp, height = 54.dp, fontSize = 21.sp)
        }
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isSelected) Modifier.background(palette.pinkSoft) else Modifier,
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (isSelected) palette.onPinkSoft else palette.text
                // A count sits on the icon's corner, ringed in the row's colour, like an app badge.
                Box {
                    Icon(tab.icon, null, tint = tint, modifier = Modifier.size(34.dp))
                    if (tab == Tab.MATCHES && unread > 0) {
                        val ring = if (isSelected) palette.pinkSoft.compositeOver(palette.rail) else palette.rail
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 12.dp, y = (-10).dp)
                                .size(26.dp)
                                .background(ring, CircleShape)
                                .padding(2.5.dp)
                                .background(palette.pink, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (unread > 9) "9+" else "$unread", fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.width(18.dp))
                Text(tab.label, fontSize = 24.sp, color = tint, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
