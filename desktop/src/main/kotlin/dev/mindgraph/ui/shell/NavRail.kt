package dev.mindgraph.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.Surface
import dev.mindgraph.ui.theme.TextMuted

/** Top-level destinations, each backed by real data rather than a placeholder screen. */
enum class Destination(val label: String, val icon: ImageVector) {
    Graph("Graph", Icons.Outlined.Hub),
    Notes("Notes", Icons.Outlined.Description),
    Tasks("Tasks", Icons.Outlined.CheckCircleOutline),
    Work("Work", Icons.Outlined.Timer),
}

private val RailWidth = 60.dp

/**
 * A narrow, icon-only rail. Deliberately chrome-light: the destination content —
 * above all the graph — is what should hold the eye, not the navigation around it.
 */
@Composable
fun NavRail(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(RailWidth).fillMaxHeight().background(Surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BrandMark()
        Destination.entries.forEach { destination ->
            RailItem(
                destination = destination,
                isActive = destination == current,
                onClick = { onSelect(destination) },
            )
        }
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .padding(vertical = 14.dp)
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Accent),
        contentAlignment = Alignment.Center,
    ) {
        Text("M", style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun RailItem(destination: Destination, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            destination.icon,
            contentDescription = destination.label,
            tint = if (isActive) Accent else TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
