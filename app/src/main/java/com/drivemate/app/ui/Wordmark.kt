package com.drivemate.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

private val BrandPink = Color(0xFFF74C93)
private val BrandPinkLight = Color(0xFFFF84AD)

// Letters are strokes in units of the cap height (y down: 0 is the top, 1 the baseline).
private const val STROKE = 0.15f
private const val GAP = 0.2f
private val Widths = floatArrayOf(0.62f, 1f, 0.84f, 0.52f, 1f, 0.74f)
private const val LIT_FROM = 4 // "ON"
private val WIDTH = Widths.sum() + GAP * (Widths.size - 1) + STROKE

/**
 * SOMEON in neon tubes: "SOME" unlit, "ON" lit pink and glowing, its O a power symbol —
 * the app turns something on in your day. Not [lit] (notices off), the ON goes out like the
 * rest. tools/brand/make_brand.py draws the same strokes for the documents; keep the two in step.
 */
@Composable
fun Wordmark(capHeight: Dp, modifier: Modifier = Modifier, lit: Boolean = true) {
    val off = palette.wordOff
    val glow by animateFloatAsState(if (lit) 1f else 0f, tween(600), label = "on")
    Box(modifier.size(capHeight * WIDTH, capHeight * (1 + STROKE))) {
        if (glow > 0f) {
            Canvas(Modifier.matchParentSize().blur(capHeight * 0.16f, BlurredEdgeTreatment.Unbounded)) {
                letters(lit = true) { path, _ -> drawPath(path, BrandPink.copy(alpha = 0.75f * glow), style = tube(1.1f)) }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            letters(lit = false) { path, _ -> drawPath(path, off, style = tube(1f)) }
            letters(lit = true) { path, span ->
                if (glow < 1f) drawPath(path, off, style = tube(1f))
                drawPath(path, Brush.horizontalGradient(listOf(BrandPink, BrandPinkLight), span.start, span.endInclusive), style = tube(1f), alpha = glow)
                drawPath(path, Color(0xFFFFECF4).copy(alpha = 0.4f * glow), style = tube(0.2f))
            }
        }
    }
}

private fun DrawScope.tube(weight: Float) = Stroke(size.height / (1 + STROKE) * STROKE * weight, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Each lit or unlit letter's path in pixels, with the x span of the lit ones (for their gradient). */
private fun DrawScope.letters(lit: Boolean, draw: (Path, ClosedFloatingPointRange<Float>) -> Unit) {
    val u = size.height / (1 + STROKE)
    val inset = u * STROKE / 2
    val litStart = inset + u * (Widths.take(LIT_FROM).sum() + GAP * LIT_FROM)
    val span = litStart..size.width - inset
    var x = inset
    Widths.forEachIndexed { i, width ->
        if ((i >= LIT_FROM) == lit) draw(letter(i, u, x, inset), span)
        x += (width + GAP) * u
    }
}

private fun letter(i: Int, u: Float, ox: Float, oy: Float) = Path().apply {
    fun p(x: Float, y: Float) = ox + x * u to oy + y * u
    fun move(x: Float, y: Float) = p(x, y).let { (a, b) -> moveTo(a, b) }
    fun line(vararg pts: Float) = pts.toList().chunked(2).forEach { (x, y) -> p(x, y).let { (a, b) -> lineTo(a, b) } }
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = Rect(ox + (cx - rx) * u, oy + (cy - ry) * u, ox + (cx + rx) * u, oy + (cy + ry) * u)
    when (i) {
        0 -> { // S: two bowls
            arcTo(oval(0.31f, 0.235f, 0.29f, 0.235f), -20f, -250f, true)
            arcTo(oval(0.31f, 0.735f, 0.31f, 0.265f), -90f, 250f, false)
        }
        1 -> addOval(oval(0.5f, 0.5f, 0.5f, 0.5f))
        2 -> { move(0f, 1f); line(0f, 0f, 0.42f, 0.62f, 0.84f, 0f, 0.84f, 1f) }
        3 -> { move(0.52f, 0f); line(0f, 0f, 0f, 1f, 0.52f, 1f); move(0f, 0.5f); line(0.44f, 0.5f) }
        4 -> { // O as a power symbol
            arcTo(oval(0.5f, 0.5f, 0.5f, 0.5f), -55f, 290f, true)
            move(0.5f, -0.06f); line(0.5f, 0.4f)
        }
        else -> { move(0f, 1f); line(0f, 0f, 0.74f, 1f, 0.74f, 0f) }
    }
}
