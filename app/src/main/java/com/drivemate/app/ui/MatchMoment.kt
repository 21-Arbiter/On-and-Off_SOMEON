package com.drivemate.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.core.Profile
import kotlinx.coroutines.delay

/**
 * Both have shown interest: the power sign fills up both sides at once, one for each of them,
 * and SOMEON's ON is on for the two. Over the parked screen until the driver goes to the
 * conversation or leaves it for later.
 */
@Composable
fun MatchMoment(me: Profile?, them: Profile, onChat: () -> Unit, onLater: () -> Unit) {
    val ring = remember { Animatable(0f) }
    val stem = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(250)
        ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        stem.animateTo(1f, tween(450))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x8C0E1020))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(1040.dp)
                .shadow(24.dp, RoundedCornerShape(40.dp))
                .background(palette.card, RoundedCornerShape(40.dp))
                .padding(horizontal = 56.dp, vertical = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                Face(me, "나")
                Box(Modifier.size(260.dp).padding(14.dp), contentAlignment = Alignment.Center) {
                    PowerSign(palette.neutralButton, Modifier.fillMaxSize(), weight = 0.1f)
                    PowerSign(
                        palette.pink.copy(alpha = 0.8f),
                        Modifier.fillMaxSize().blur(16.dp, BlurredEdgeTreatment.Unbounded),
                        weight = 0.12f,
                        ring = ring.value,
                        stem = stem.value,
                    )
                    PowerSign(palette.pink, Modifier.fillMaxSize(), weight = 0.1f, ring = ring.value, stem = stem.value)
                }
                Face(them, them.name)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                buildAnnotatedString {
                    append("${them.name}님과 ")
                    withStyle(SpanStyle(color = palette.pink)) { append("썸이 켜졌어요") }
                },
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Text("두 분 모두 관심을 보냈어요. 먼저 인사하고 만날 곳을 정해 보세요.", fontSize = 24.sp, color = palette.textMuted)
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    "나중에",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.neutralButton)
                        .clickable(onClick = onLater)
                        .padding(horizontal = 56.dp, vertical = 20.dp),
                )
                Text(
                    "대화하기",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFF77BA3), palette.pinkDeep)))
                        .clickable(onClick = onChat)
                        .padding(horizontal = 80.dp, vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun Face(profile: Profile?, label: String) {
    Column(Modifier.padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(132.dp).shadow(6.dp, CircleShape).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            if (profile != null) PersonPhoto(profile, 122.dp)
        }
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = palette.textMuted)
    }
}
