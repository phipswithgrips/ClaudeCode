package de.rezeptkiste.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.pow

/** Farben im Aufbau von Recipe Keeper: dunkle Flächen, eine wählbare Akzentfarbe. */
object RkColors {
    val Background = Color(0xFF202020)
    val Pane = Color(0xFF2B2B2B)
    val Surface = Color(0xFF2B2B2B)
    val SurfaceHigh = Color(0xFF383838)
    val Field = Color(0xFF333333)
    val Line = Color(0xFF3F3F3F)
    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA6A6A6)
    val Link = Color(0xFF5AA9E6)
    val Error = Color(0xFFF2555A)

    /** Wählbare Akzentfarben (Einstellungen > Aussehen). */
    val AccentChoices = listOf(
        0xFFD9622BL, 0xFFF59E1BL, 0xFFE8384FL, 0xFFD9534FL, 0xFFF0628CL,
        0xFFC2185BL, 0xFFB5359AL, 0xFF9C27B0L, 0xFF8E44ADL, 0xFF7E57C2L,
        0xFF5CB85CL, 0xFF2E9E5BL, 0xFF16A085L, 0xFF1EAEDBL, 0xFF4A6CF7L,
        0xFF2E7BCFL, 0xFF7986CBL, 0xFF9FA8DAL, 0xFF9575CDL, 0xFF26A69AL,
        0xFF6D7B70L, 0xFF8D8270L, 0xFF9E9E9EL, 0xFF607080L, 0xFF4A4A4AL,
    )
}

val LocalAccent = staticCompositionLocalOf { Color(0xFFD9622B) }
val LocalAccentFill = staticCompositionLocalOf { Color(0xFFD9622B) }

/** Akzentfarbe für Schrift und Symbole: immer gut lesbar auf dem dunklen Hintergrund. */
val accent: Color
    @Composable @ReadOnlyComposable get() = LocalAccent.current

/** Akzentfarbe für Flächen (Kacheln, Schaltflächen) mit weißer Schrift, genau wie gewählt. */
val accentFill: Color
    @Composable @ReadOnlyComposable get() = LocalAccentFill.current

private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
private fun contrast(a: Color, b: Color): Double {
    val (l1, l2) = luminance(a) to luminance(b)
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}

/** Hellt eine zu dunkle Akzentfarbe so weit auf, dass Text darin lesbar bleibt (Kontrast mindestens 3:1 wie für große Schrift und Bedienelemente). */
fun readableOn(color: Color, background: Color = RkColors.Background, minContrast: Double = 3.0): Color {
    var c = color
    var step = 0
    while (contrast(c, background) < minContrast && step < 20) {
        c = Color(c.red + (1f - c.red) * 0.12f, c.green + (1f - c.green) * 0.12f, c.blue + (1f - c.blue) * 0.12f, 1f)
        step++
    }
    return c
}

private fun typography(scale: Float) = Typography(
    headlineMedium = TextStyle(fontSize = (26 * scale).sp, fontWeight = FontWeight.Light),
    headlineSmall = TextStyle(fontSize = (22 * scale).sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = (20 * scale).sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = (17 * scale).sp, fontWeight = FontWeight.Normal),
    titleSmall = TextStyle(fontSize = (15 * scale).sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = (15 * scale).sp),
    bodyMedium = TextStyle(fontSize = (14 * scale).sp),
    bodySmall = TextStyle(fontSize = (12.5f * scale).sp),
    labelLarge = TextStyle(fontSize = (14 * scale).sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = (12.5f * scale).sp),
    labelSmall = TextStyle(fontSize = (11.5f * scale).sp),
)

@Composable
fun RezeptTheme(accentArgb: Long, textScale: Float = 1f, content: @Composable () -> Unit) {
    val fill = Color(accentArgb.toInt())
    val a = readableOn(fill)
    val scheme = darkColorScheme(
        primary = a, onPrimary = Color.White, primaryContainer = a, onPrimaryContainer = Color.White,
        secondary = a, onSecondary = Color.White, tertiary = a,
        background = RkColors.Background, onBackground = RkColors.Text,
        surface = RkColors.Surface, onSurface = RkColors.Text,
        surfaceVariant = RkColors.Field, onSurfaceVariant = RkColors.TextSecondary,
        surfaceContainer = RkColors.Surface, surfaceContainerLow = RkColors.Surface,
        surfaceContainerHigh = RkColors.SurfaceHigh, surfaceContainerHighest = RkColors.SurfaceHigh,
        outline = RkColors.Line, outlineVariant = RkColors.Line,
        error = RkColors.Error, onError = Color.Black,
    )
    CompositionLocalProvider(LocalAccent provides a, LocalAccentFill provides fill) {
        MaterialTheme(colorScheme = scheme, typography = typography(textScale), content = content)
    }
}
