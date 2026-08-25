package dev.mindgraph.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.NodeId
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Blocked
import dev.mindgraph.ui.theme.Border
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.SurfaceHigh
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary

/**
 * The graph as the full canvas, with every control floating above it. Mind mode is for
 * thinking, Flow mode for planning; they share the same nodes and never the same layout.
 */
@Composable
fun GraphScreen(
    viewModel: AppViewModel,
    linkSourceId: NodeId?,
    onStartLink: (NodeId) -> Unit,
    onCancelLink: () -> Unit,
    onOpenNode: (NodeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingTarget by remember { mutableStateOf<Pair<NodeId, NodeId>?>(null) }
    var viewResetKey by remember { mutableStateOf(0) }

    val graph = viewModel.graph

    Box(modifier = modifier.fillMaxSize().background(Ink)) {
        GraphCanvas(
            nodes = viewModel.nodes,
            edges = viewModel.edges,
            graph = graph,
            layout = viewModel.layout,
            selectedNodeId = viewModel.selectedNodeId,
            linkSourceId = linkSourceId,
            viewResetKey = viewResetKey,
            trackedSecondsFor = viewModel::trackedSecondsFor,
            onSelectNode = { viewModel.selectNode(it) },
            onLinkTarget = { targetId ->
                val sourceId = linkSourceId
                if (sourceId != null && sourceId != targetId) {
                    pendingTarget = sourceId to targetId
                }
                onCancelLink()
            },
        )

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CountsPill(
                nodeCount = viewModel.nodes.size,
                edgeCount = viewModel.edges.size,
                readyCount = graph.readyTasks().size,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingAction(Icons.Outlined.Add, "New note") { viewModel.createNode() }
            FloatingAction(Icons.Outlined.Refresh, "Release pinned nodes") { viewModel.layout.unpinAll() }
            FloatingAction(Icons.Outlined.CenterFocusStrong, "Recenter view") { viewResetKey++ }
        }

        val selected = viewModel.nodeById(viewModel.selectedNodeId)
        if (selected != null) {
            val blockers = graph.blockers(selected.id)
            SelectedNodeCard(
                title = selected.title,
                trackedSeconds = viewModel.trackedSecondsFor(selected.id),
                statusLabel = when {
                    !selected.isTask -> "note"
                    blockers.isNotEmpty() -> "blocked by ${blockers.size}"
                    graph.isReady(selected) -> "ready"
                    else -> selected.task!!.status.name.lowercase()
                },
                isBlocked = blockers.isNotEmpty(),
                isLinking = linkSourceId == selected.id,
                onOpen = { onOpenNode(selected.id) },
                onStartLink = { onStartLink(selected.id) },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            )
        }

        Text(
            text = if (linkSourceId != null) {
                "Click another node to link it"
            } else {
                "Click: select · Drag: move · Double-click: unpin · Scroll: zoom"
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        )
    }

    val target = pendingTarget
    if (target != null) {
        val (sourceId, targetId) = target
        LinkKindDialog(
            sourceTitle = viewModel.nodeById(sourceId)?.title.orEmpty(),
            targetTitle = viewModel.nodeById(targetId)?.title.orEmpty(),
            wouldCycle = graph.wouldCycle(sourceId, targetId),
            onDismiss = { pendingTarget = null },
            onPick = { kind ->
                when (kind) {
                    EdgeKind.RelatesTo -> viewModel.linkRelates(sourceId, targetId)
                    EdgeKind.DependsOn -> viewModel.linkDependsOn(sourceId, targetId)
                }
                pendingTarget = null
            },
        )
    }
}

@Composable
private fun LinkKindDialog(
    sourceTitle: String,
    targetTitle: String,
    wouldCycle: Boolean,
    onDismiss: () -> Unit,
    onPick: (EdgeKind) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How does \"$sourceTitle\" connect to \"$targetTitle\"?")
                if (wouldCycle) {
                    Text(
                        "A dependency here would create a cycle, so only an association is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Blocked,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(EdgeKind.DependsOn) }, enabled = !wouldCycle) {
                Text("Depends on it", color = if (wouldCycle) TextMuted else Accent)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                TextButton(onClick = { onPick(EdgeKind.RelatesTo) }) { Text("Relates to it", color = Accent) }
            }
        },
    )
}

@Composable
private fun CountsPill(nodeCount: Int, edgeCount: Int, readyCount: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$nodeCount nodes · $edgeCount links", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        if (readyCount > 0) {
            Text("$readyCount ready", style = MaterialTheme.typography.labelSmall, color = Accent)
        }
    }
}

@Composable
private fun FloatingAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SelectedNodeCard(
    title: String,
    trackedSeconds: Long,
    statusLabel: String,
    isBlocked: Boolean,
    isLinking: Boolean,
    onOpen: () -> Unit,
    onStartLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 220.dp, max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(
            "${formatDuration(trackedSeconds)} tracked · $statusLabel",
            style = MaterialTheme.typography.labelSmall,
            color = if (isBlocked) Blocked else TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpen) { Text("Open", color = Accent) }
            TextButton(onClick = onStartLink, enabled = !isLinking) {
                Text(if (isLinking) "Pick a target…" else "Link", color = if (isLinking) TextMuted else Accent)
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
