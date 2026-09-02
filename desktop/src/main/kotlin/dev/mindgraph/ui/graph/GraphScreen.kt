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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.width
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.state.FlowForest
import dev.mindgraph.state.GraphFilter
import dev.mindgraph.state.Clustering
import dev.mindgraph.state.LayoutMode
import dev.mindgraph.ui.shell.KindFilterBar
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
    var mode by remember { mutableStateOf(LayoutMode.Mind) }
    var kindFilter by remember { mutableStateOf<NodeKind?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var hideDone by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(true) }

    val graph = viewModel.graph

    // Filtering decides how many nodes there are, and the layout is built from that count, so
    // the engine is driven with the visible set rather than the vault. Hiding a node therefore
    // rebuilds the picture around what is left instead of leaving a hole where it used to sit.
    val visible = GraphFilter.apply(
        viewModel.nodes,
        viewModel.edges,
        kindFilter,
        showArchived,
        includeDone = !hideDone,
    )
    val kindCounts = viewModel.nodes.groupingBy { it.kind }.eachCount()

    // Grouping is derived once and shared: the canvas captions and the layout positions must
    // agree about what a group is, and they only do if they come from the same map.
    val clusterGroups = remember(visible.nodes) { Clustering.groups(visible.nodes) }

    // Flow does not draw the visible set at all. Its subject is the tasks that participate in
    // a dependency, and the forest works out their positions as one decision, so it replaces
    // both the node list and the layout rather than filtering what the other modes show.
    val forest = remember(viewModel.nodes, hideDone, showArchived) {
        FlowForest.build(viewModel.nodes, includeDone = !hideDone, includeArchived = showArchived)
    }
    val drawn = if (mode == LayoutMode.Flow) {
        // Edges are re-derived over the forest's own nodes so a chain does not trail a line off
        // to a task the mode deliberately does not draw.
        GraphFilter.apply(forest.nodes, viewModel.edges, kind = null, includeArchived = true)
    } else {
        visible
    }

    LaunchedEffect(mode, drawn.nodes, drawn.edges) {
        viewModel.layout.mode = mode
        when (mode) {
            LayoutMode.Cluster -> viewModel.layout.setClusters(clusterGroups)
            LayoutMode.Flow -> viewModel.layout.setTargets(forest.positions)
            LayoutMode.Mind -> Unit
        }
    }

    // Keyed on the filters themselves rather than on the resulting node list: a filter toggle
    // is a request to rebuild the picture, whereas an agent writing a node over MCP is not, and
    // scrambling the canvas under someone every time the vault changes would be its own bug.
    LaunchedEffect(kindFilter, showArchived, hideDone) {
        viewModel.layout.reflow()
    }

    Box(modifier = modifier.fillMaxSize().background(Ink)) {
        GraphCanvas(
            nodes = drawn.nodes,
            edges = drawn.edges,
            graph = graph,
            layout = viewModel.layout,
            selectedNodeId = viewModel.selectedNodeId,
            linkSourceId = linkSourceId,
            viewResetKey = viewResetKey,
            trackedSecondsFor = viewModel::trackedSecondsFor,
            clusterLabels = if (mode == LayoutMode.Cluster) {
                viewModel.layout.clusterCentres(clusterGroups)
            } else {
                emptyMap()
            },
            showLabels = showLabels,
            ghostIds = if (mode == LayoutMode.Flow) forest.ghosts else emptySet(),
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
            ModeToggle(mode = mode, onChange = { mode = it })
            if (mode == LayoutMode.Flow) {
                // No kind filter here. Flow's subject is a dependency chain, and narrowing it
                // to one kind would cut trees in half - which is the failure this mode was
                // rewritten to stop making.
                FlowCountsPill(
                    taskCount = forest.nodes.size,
                    looseCount = forest.looseTaskCount,
                    ghostCount = forest.ghosts.size,
                )
            } else {
                KindFilterBar(
                    selected = kindFilter,
                    counts = kindCounts,
                    onSelect = { kindFilter = it },
                )
                CountsPill(
                    nodeCount = visible.nodes.size,
                    edgeCount = visible.edges.size,
                    readyCount = graph.readyTasks().size,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .width(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceHigh.copy(alpha = 0.94f))
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // The rail is wider than the 38dp buttons it holds, and a Column defaults to Start
            // — so without this every button sits hard against the left edge with the slack
            // pooled on the right, which the full-width dividers make impossible to miss.
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FloatingAction(Icons.Outlined.Add, "New note") { viewModel.createNode() }
            ActionDivider()
            FloatingAction(
                icon = Icons.Outlined.Inventory2,
                description = if (showArchived) "Hide archived nodes" else "Show archived nodes",
                isActive = showArchived,
            ) { showArchived = !showArchived }
            FloatingAction(
                icon = if (hideDone) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                description = if (hideDone) "Show done tasks" else "Hide done tasks",
                isActive = hideDone,
            ) { hideDone = !hideDone }
            FloatingAction(
                icon = Icons.Outlined.TextFields,
                description = if (showLabels) "Hide node labels" else "Show node labels",
                // Active means "this control is changing the picture", which for the other
                // toggles is their true state and here is the off state.
                isActive = !showLabels,
            ) { showLabels = !showLabels }
            ActionDivider()
            FloatingAction(Icons.Outlined.Refresh, "Release pinned nodes") { viewModel.layout.unpinAll() }
            FloatingAction(Icons.Outlined.CenterFocusStrong, "Recenter view") { viewResetKey++ }
        }

        if (mode == LayoutMode.Flow && forest.isEmpty) {
            FlowEmptyState(
                looseCount = forest.looseTaskCount,
                modifier = Modifier.align(Alignment.Center),
            )
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
private fun ActionDivider() {
    androidx.compose.material3.HorizontalDivider(color = Border.copy(alpha = 0.7f))
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
private fun ModeToggle(mode: LayoutMode, onChange: (LayoutMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LayoutMode.entries.forEach { candidate ->
            val isActive = candidate == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isActive) Accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onChange(candidate) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    candidate.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) Accent else TextMuted,
                )
            }
        }
    }
}

/**
 * What Flow is showing and, as importantly, what it is leaving out.
 *
 * The mode draws a deliberate minority of the vault, so the count of what it dropped belongs on
 * screen. Silently showing 15 nodes out of 104 is how the old layout hid its own problem.
 */
@Composable
private fun FlowCountsPill(taskCount: Int, looseCount: Int, ghostCount: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$taskCount task${if (taskCount == 1) "" else "s"} in chains",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
        )
        if (ghostCount > 0) {
            Text("$ghostCount done", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        if (looseCount > 0) {
            Text(
                "$looseCount unlinked hidden",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

/**
 * A blank canvas reads as a broken tab. If nothing depends on anything there is nothing for
 * Flow to draw, and saying so is more useful than leaving the user to guess.
 */
@Composable
private fun FlowEmptyState(looseCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Nothing depends on anything yet", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(
            if (looseCount > 0) {
                "Flow draws the trees formed by dependencies between tasks. " +
                    "$looseCount task${if (looseCount == 1) " has" else "s have"} no dependency " +
                    "either way. Select a task and use Link · Depends on it to start a chain."
            } else {
                "Flow draws the trees formed by dependencies between tasks. " +
                    "Create a task, then link it to what it is waiting on."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
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
@OptIn(ExperimentalMaterial3Api::class)
private fun FloatingAction(
    icon: ImageVector,
    description: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                // A toggle has to look held down, or you cannot tell an emptier graph from a
                // filtered one.
                .background(if (isActive) Accent.copy(alpha = 0.18f) else Color.Transparent)
                .border(1.dp, if (isActive) Accent else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (isActive) Accent else TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
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
