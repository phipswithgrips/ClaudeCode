package de.rezeptkiste.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Farbschema laut Spezifikation: nur dunkel, Orange als einziger Akzent. */
object RkColors {
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceHigh = Color(0xFF2A2A2A)
    val Line = Color(0xFF3A3A3A)
    val Text = Color(0xFFEDEDED)
    val TextSecondary = Color(0xFFA0A0A0)
    val Accent = Color(0xFFF26B2A)
    val AccentFilled = Color(0xFFD24400)
    val Error = Color(0xFFF2555A)
}

private val scheme = darkColorScheme(
    primary = RkColors.AccentFilled,
    onPrimary = Color.White,
    primaryContainer = RkColors.AccentFilled,
    onPrimaryContainer = Color.White,
    secondary = RkColors.Accent,
    onSecondary = Color.Black,
    tertiary = RkColors.Accent,
    background = RkColors.Background,
    onBackground = RkColors.Text,
    surface = RkColors.Surface,
    onSurface = RkColors.Text,
    surfaceVariant = RkColors.SurfaceHigh,
    onSurfaceVariant = RkColors.TextSecondary,
    surfaceContainer = RkColors.Surface,
    surfaceContainerLow = RkColors.Surface,
    surfaceContainerHigh = RkColors.SurfaceHigh,
    surfaceContainerHighest = RkColors.SurfaceHigh,
    outline = RkColors.Line,
    outlineVariant = RkColors.Line,
    error = RkColors.Error,
    onError = Color.Black,
)

@Composable
fun RezeptTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
