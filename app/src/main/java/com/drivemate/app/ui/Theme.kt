package com.drivemate.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivemate.app.R

/**
 * The app's colours for one lighting. Pleos draws its own screens light by day and
 * black in night mode (the car switches with the light), and the app follows it.
 */
@Immutable
data class Palette(
    val background: Color,
    val rail: Color,
    val card: Color,
    val cardBorder: Color,
    /** Sections inside a card, chips. */
    val cardSoft: Color,
    val cardShadow: Dp,
    val text: Color,
    val textMuted: Color,
    val divider: Color,
    val pink: Color,
    val pinkDeep: Color,
    /** Selected menu item, own chat bubble tint. */
    val pinkSoft: Color,
    val onPinkSoft: Color,
    /** Data values and the driver's own side. */
    val blue: Color,
    val green: Color,
    val neutralButton: Color,
    val profileTop: Color,
    val profileBottom: Color,
    val bubbleTheirs: Color,
    val mapBackground: Color,
    /** How the OpenStreetMap tiles are tinted for this lighting. */
    val mapFilter: ColorFilter,
    /** The unlit "SOME" of the wordmark. */
    val wordOff: Color,
    val heroLeft: Color,
    val heroRight: Color,
    @param:DrawableRes val hero: Int,
)

val DayPalette = Palette(
    background = Color(0xFFF2F3F7),
    rail = Color(0xFFF7F8FB),
    card = Color.White,
    cardBorder = Color(0xFFE6E8EF),
    cardSoft = Color(0xFFF4F5F9),
    cardShadow = 6.dp,
    text = Color(0xFF151928),
    textMuted = Color(0xFF6E7280),
    divider = Color(0xFFE9EBF1),
    pink = Color(0xFFF04D80),
    pinkDeep = Color(0xFFE23D72),
    pinkSoft = Color(0xFFFBDCE6),
    onPinkSoft = Color(0xFFCA2F58),
    blue = Color(0xFF256FD8),
    green = Color(0xFF1FA971),
    neutralButton = Color(0xFFEDEFF4),
    profileTop = Color.White,
    profileBottom = Color(0xFFFBF5F9),
    bubbleTheirs = Color(0xFFEEF0F5),
    mapBackground = Color(0xFFEDF2FA),
    mapFilter = DayMapFilter,
    wordOff = Color(0xFF665E92),
    heroLeft = Color(0xFFF6F2FA),
    heroRight = Color(0xFFEFE2F2),
    hero = R.drawable.hero_day,
)

val NightPalette = Palette(
    background = Color(0xFF0B0D12),
    rail = Color(0xFF08090C),
    card = Color(0xFF161920),
    cardBorder = Color(0x1FFFFFFF),
    cardSoft = Color(0xFF1F232C),
    cardShadow = 0.dp,
    text = Color.White,
    textMuted = Color(0xFFA3A8B5),
    divider = Color(0x1FFFFFFF),
    pink = Color(0xFFFF5A8C),
    pinkDeep = Color(0xFFE84A7C),
    pinkSoft = Color(0x40FF5A8C),
    onPinkSoft = Color(0xFFFF9DBA),
    blue = Color(0xFF7C9DFF),
    green = Color(0xFF22B07D),
    neutralButton = Color(0xFF242B3B),
    profileTop = Color(0xFF1D2131),
    profileBottom = Color(0xFF252338),
    bubbleTheirs = Color(0xFF262E40),
    mapBackground = Color(0xFF1C2436),
    mapFilter = NightMapFilter,
    wordOff = Color(0xFF8E88B8),
    heroLeft = Color(0xFF11182B),
    heroRight = Color(0xFF3A2C55),
    hero = R.drawable.hero_city,
)

private val LocalPalette = staticCompositionLocalOf { DayPalette }

/** The current lighting's colours. */
val palette: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

// Car displays are read at arm's length and at a glance: keep text large.
private val CarTypography = Typography(
    displayMedium = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 24.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
)

/** Day or night as the car says (Android's night mode, which Pleos switches). */
@Composable
fun DriveMateTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (night) NightPalette else DayPalette
    val scheme = if (night) {
        darkColorScheme(primary = p.pink, onPrimary = Color.White, background = p.background, onBackground = p.text, surface = p.card, onSurface = p.text, onSurfaceVariant = p.textMuted)
    } else {
        lightColorScheme(primary = p.pink, onPrimary = Color.White, background = p.background, onBackground = p.text, surface = p.card, onSurface = p.text, onSurfaceVariant = p.textMuted)
    }
    MaterialTheme(colorScheme = scheme, typography = CarTypography) {
        CompositionLocalProvider(LocalPalette provides p, LocalContentColor provides p.text, content = content)
    }
}
