package com.drivemate.app.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ShadowColor = Color(0x1F1A2040)

/** A card: white with a soft shadow by day, dark with a hairline border at night. The building block of every screen. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 28.dp,
    spacing: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .shadow(palette.cardShadow, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .background(palette.card, shape)
            .border(1.dp, palette.cardBorder, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun PanelTitle(text: String, icon: ImageVector? = null, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = palette.pink, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Spacer(Modifier.width(18.dp))
            Text(trailing, fontSize = 22.sp, color = palette.textMuted)
        }
    }
}

/** Icon, label, value: one line of a profile. */
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = palette.text,
    size: TextUnit = 22.sp,
    labelWidth: Dp = 150.dp,
    valueBold: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = palette.text, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = size, fontWeight = FontWeight.Bold, modifier = Modifier.width(labelWidth))
        Text(value, fontSize = size, color = valueColor, fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun HairlineDivider(color: Color = palette.divider) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun Muted(text: String, size: TextUnit = 22.sp) {
    Text(text, fontSize = size, color = palette.textMuted, lineHeight = size * 1.4f)
}

@Composable
fun Pill(
    text: String,
    background: Color = palette.pink,
    color: Color = Color.White,
    size: TextUnit = 20.sp,
    icon: ImageVector? = null,
) {
    Row(
        Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(size.value.dp * 1.2f))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = size, fontWeight = FontWeight.Bold, color = color)
    }
}

/** A thin-bordered label, e.g. "이동 패턴 기반 매칭". */
@Composable
fun OutlinedTag(text: String) {
    Text(
        text,
        fontSize = 19.sp,
        color = palette.textMuted,
        modifier = Modifier
            .border(1.dp, palette.cardBorder, RoundedCornerShape(50))
            .background(palette.cardSoft, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

/**
 * The O of SOMEON's "ON": a power sign. [ring] is how much of the ring is drawn, growing up
 * both sides from the bottom to the gap at the top; [stem] is the stem's opacity.
 */
@Composable
fun PowerSign(color: Color, modifier: Modifier = Modifier, weight: Float = 0.14f, ring: Float = 1f, stem: Float = 1f) {
    Canvas(modifier) {
        val w = size.minDimension * weight
        val r = size.minDimension / 2 - w / 2
        val topLeft = Offset(center.x - r, center.y - r)
        val oval = Size(r * 2, r * 2)
        val tube = Stroke(w, cap = StrokeCap.Round)
        if (ring > 0f) {
            // From the bottom (90°) round each side to the gap, which runs from 235° to 305°.
            drawArc(color, 90f, 145f * ring, false, topLeft, oval, style = tube)
            drawArc(color, 90f, -145f * ring, false, topLeft, oval, style = tube)
        }
        if (stem > 0f) drawLine(color, Offset(center.x, center.y - r * 1.06f), Offset(center.x, center.y - r * 0.2f), w, StrokeCap.Round, alpha = stem)
    }
}

/** An on/off switch whose knob carries the power sign; [label] goes before the ON or OFF ("썸 ON"). */
@Composable
fun PowerSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    width: Dp = 116.dp,
    height: Dp = 58.dp,
    fontSize: TextUnit = 19.sp,
) {
    val knobSize = height - 10.dp
    val knob by animateDpAsState(if (on) width - knobSize - 5.dp else 5.dp, label = "knob")
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(50))
            .background(if (on) Brush.horizontalGradient(listOf(Color(0xFFF77BA3), palette.pinkDeep)) else SolidColor(palette.neutralButton))
            .clickable { onChange(!on) },
        contentAlignment = Alignment.CenterStart,
    ) {
        // The word sits in the room the knob leaves.
        Box(Modifier.fillMaxSize().padding(start = if (on) 0.dp else knobSize + 5.dp, end = if (on) knobSize + 5.dp else 0.dp), contentAlignment = Alignment.Center) {
            Text(
                listOfNotNull(label, if (on) "ON" else "OFF").joinToString(" "),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (on) Color.White else palette.textMuted,
            )
        }
        Box(
            Modifier.offset(x = knob).size(knobSize).shadow(3.dp, CircleShape).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PowerSign(if (on) palette.pink else palette.textMuted.copy(alpha = 0.6f), Modifier.size(knobSize * 0.55f))
        }
    }
}

/** Out of focus and colourless: what 썸 off puts aside, still there underneath. */
fun Modifier.hushed(radius: Dp = 16.dp) = graphicsLayer {
    val r = radius.toPx()
    renderEffect = android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })),
        android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL),
    ).asComposeRenderEffect()
}
