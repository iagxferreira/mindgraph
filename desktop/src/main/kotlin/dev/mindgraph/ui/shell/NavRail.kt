package dev.mindgraph.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.BrandPlate
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
            // A light plate rather than the accent. The mark is drawn in the accent violet
            // itself, so on an accent square its own nodes disappear into the background and
            // only the dark connectors survive - and on the bare dark rail the opposite happens,
            // the two unfilled nodes vanish and the M stops being an M. Light is the one ground
            // every part of it reads on.
            .background(BrandPlate)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource("mindgraph-icon.png"),
            // Named for what it is rather than described: at 30dp the mark is an identifier, and
            // reading its shape aloud helps nobody.
            contentDescription = "MindGraph",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
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
