package dev.mindgraph.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF10131A)
val Surface = Color(0xFF171B24)
val SurfaceHigh = Color(0xFF1F2430)
val Accent = Color(0xFF7C9CFF)
val AccentSoft = Color(0xFF3A4A73)
val TextPrimary = Color(0xFFE7EAF2)
val TextMuted = Color(0xFF9AA3B8)

private val MindGraphColorScheme = darkColorScheme(
    background = Ink,
    surface = Surface,
    surfaceVariant = SurfaceHigh,
    primary = Accent,
    secondary = AccentSoft,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = Ink,
)

@Composable
fun MindGraphTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MindGraphColorScheme, content = content)
}
