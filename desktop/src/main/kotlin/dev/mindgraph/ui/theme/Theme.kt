package dev.mindgraph.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// A restrained palette: one accent color, everything else tonal grays derived from it.
val Ink = Color(0xFF0E0F12)
val Surface = Color(0xFF17191D)
val SurfaceHigh = Color(0xFF1F2227)
val Border = Color(0xFF2A2D33)
val Accent = Color(0xFF8C93FF)
val TextPrimary = Color(0xFFE8E9EC)
val TextMuted = Color(0xFF8B909B)

// Semantic state, kept separate from [Accent] so "selected" and "blocked" never read as the
// same thing. Two hues only — status should be legible, not a rainbow.
val Blocked = Color(0xFFE0A45E)
val Overdue = Color(0xFFE0705E)

/** Machine labour, against [Accent] for your own. */
val Machine = Color(0xFF48BFD6)
val Done = Color(0xFF77BE97)

/** A quieter variant of [Accent] for de-emphasized strokes/fills, kept as one hue not two. */
val AccentSoft: Color get() = Accent.copy(alpha = 0.35f)

private val MindGraphColorScheme = darkColorScheme(
    background = Ink,
    surface = Surface,
    surfaceVariant = SurfaceHigh,
    outline = Border,
    outlineVariant = Border,
    primary = Accent,
    secondary = Accent,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    onPrimary = Ink,
)

private val MindGraphShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
)

@Composable
fun MindGraphTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MindGraphColorScheme, shapes = MindGraphShapes, content = content)
}
