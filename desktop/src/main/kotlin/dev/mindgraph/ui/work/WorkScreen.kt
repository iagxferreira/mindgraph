package dev.mindgraph.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Border
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.SurfaceHigh
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary

/**
 * Where the time went: totals across the vault, then a per-node breakdown ranked by tracked
 * time. Clicking a row selects that node so the other destinations follow along.
 */
@Composable
fun WorkScreen(
    viewModel: AppViewModel,
    onOpenNode: (NodeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranked = viewModel.nodes
        .map { it to viewModel.trackedSecondsFor(it.id) }
        .filter { (_, seconds) -> seconds > 0 }
        .sortedByDescending { (_, seconds) -> seconds }
    val totalSeconds = ranked.sumOf { (_, seconds) -> seconds }
    val maxSeconds = ranked.firstOrNull()?.second ?: 0L

    Column(modifier = modifier.fillMaxSize().background(Ink).padding(24.dp)) {
        Text("Work", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("Total tracked", formatDuration(totalSeconds), Modifier.weight(1f))
            StatCard("Nodes worked", ranked.size.toString(), Modifier.weight(1f))
            StatCard("Sessions", viewModel.sessions.size.toString(), Modifier.weight(1f))
        }

        Text(
            "By node",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )

        if (ranked.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No tracked time yet. Start a timer on a note to see it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ranked, key = { (node, _) -> node.id.value }) { (node, seconds) ->
                    WorkRow(
                        node = node,
                        seconds = seconds,
                        fraction = if (maxSeconds > 0) seconds.toFloat() / maxSeconds else 0f,
                        onClick = { onOpenNode(node.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun WorkRow(node: Node, seconds: Long, fraction: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                node.title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(formatDuration(seconds), style = MaterialTheme.typography.labelMedium, color = Accent)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Accent.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Accent),
            )
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
