package com.drivemate.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.voice.VoiceTalk
import com.drivemate.core.Profile
import kotlinx.coroutines.delay

private val HangUpRed = Color(0xFFE5484D)
private val AnswerGreen = Color(0xFF1FA971)

/** How long the call has been live, m:ss; null until it is. */
@Composable
private fun callTime(call: VoiceTalk.State): String? {
    val since = call.since ?: return null
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val seconds = ((now - since) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/**
 * The parked screen's call, floating over the tab like the iPhone's call pill: photo,
 * an equalizer that moves with the voices, the time, and hanging up (or answering).
 */
@Composable
fun CallIsland(call: VoiceTalk.State, level: Float, onAnswer: () -> Unit, onHangUp: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(18.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(IslandBlack)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonPhoto(Profile(call.name, 0, photo = call.photo), 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(call.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.width(14.dp))
        when {
            call.live -> {
                Equalizer(level, bars = 5, height = 24.dp)
                Spacer(Modifier.width(12.dp))
                Text(callTime(call).orEmpty(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = FontFamily.Monospace)
            }
            call.incoming -> Text("보이스톡", fontSize = 18.sp, color = IslandMuted)
            else -> Text("연결 중…", fontSize = 18.sp, color = IslandMuted)
        }
        Spacer(Modifier.width(16.dp))
        if (call.incoming && !call.live) {
            IslandButton(Icons.Filled.Call, "받기", AnswerGreen, onAnswer)
            Spacer(Modifier.width(8.dp))
        }
        IslandButton(Icons.Filled.CallEnd, if (call.incoming && !call.live) "거절" else "끊기", HangUpRed, onHangUp)
    }
}

private val IslandBlack = Color(0xFF15131C)
private val IslandMuted = Color(0xFFB9B4C8)

@Composable
private fun IslandButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Bars that rise with the call's [level] (0..1), each on its own beat so it looks alive; flat dots in silence. */
@Composable
fun Equalizer(level: Float, bars: Int, height: Dp, modifier: Modifier = Modifier) {
    val beat by rememberInfiniteTransition(label = "eq").animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(900)), label = "beat")
    val shown by animateFloatAsState(level, tween(90), label = "level")
    val barWidth = height * 0.2f
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(barWidth * 0.8f), verticalAlignment = Alignment.CenterVertically) {
        repeat(bars) { i ->
            val wave = 0.55f + 0.45f * kotlin.math.sin(beat * (1f + i * 0.37f) + i * 1.3f)
            val fraction = (0.16f + 0.84f * (shown * wave).coerceIn(0f, 1f))
            Box(
                Modifier
                    .width(barWidth)
                    .height(height * fraction)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(BrandPinkLight, BrandPink))),
            )
        }
    }
}

private val BrandPink = Color(0xFFF74C93)
private val BrandPinkLight = Color(0xFFFF84AD)

/** While driving: who, for how long, in large type, and one big button to hang up; nothing else to read. */
@Composable
fun DrivingCall(call: VoiceTalk.State, level: Float, onHangUp: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PersonPhoto(Profile(call.name, 0, photo = call.photo), 168.dp)
        Spacer(Modifier.height(26.dp))
        Text("${call.name}님과 보이스톡", fontSize = 46.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        if (call.live) Equalizer(level, bars = 9, height = 56.dp) else LiveDot(false)
        Spacer(Modifier.height(10.dp))
        Text(callTime(call) ?: if (call.incoming) "요청이 왔어요" else "연결하는 중…", fontSize = 32.sp, color = if (call.live) palette.text else palette.textMuted, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace.takeIf { call.live })
        Spacer(Modifier.height(10.dp))
        Text("운전 중에는 핸즈프리로만 통화해요", fontSize = 22.sp, color = palette.textMuted)
        Spacer(Modifier.height(36.dp))
        RoundButton(Icons.Filled.CallEnd, "끊기", HangUpRed, 120.dp, onHangUp, labelSize = 22)
    }
}

@Composable
private fun LiveDot(live: Boolean) {
    val pulse by rememberInfiniteTransition(label = "call").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.size(18.dp).alpha(if (live) 1f else pulse).background(if (live) palette.green else palette.pink, CircleShape))
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, size: Dp, onClick: () -> Unit, labelSize: Int = 16) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = labelSize.sp, color = palette.textMuted)
    }
}
