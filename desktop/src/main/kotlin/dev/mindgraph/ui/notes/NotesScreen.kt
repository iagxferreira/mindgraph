package dev.mindgraph.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.ui.shell.KindFilterBar
import dev.mindgraph.ui.shell.KindGlyph
import dev.mindgraph.ui.shell.label
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Blocked
import dev.mindgraph.ui.theme.Done
import dev.mindgraph.ui.theme.Surface
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary

private val ListWidth = 260.dp

/** The writing destination: every node in a list, the focused editor filling the rest. */
@Composable
fun NotesScreen(
    viewModel: AppViewModel,
    linkSourceId: NodeId?,
    onStartLink: (NodeId) -> Unit,
    onCancelLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        NodeList(
            viewModel = viewModel,
            modifier = Modifier.width(ListWidth).fillMaxHeight(),
        )
        VerticalDivider()

        val selected = viewModel.nodeById(viewModel.selectedNodeId)
        if (selected != null) {
            NoteEditorPanel(
                node = selected,
                viewModel = viewModel,
                linkSourceId = linkSourceId,
                onStartLink = onStartLink,
                onCancelLink = onCancelLink,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        } else {
            EmptyEditorState(
                onCreateNote = { viewModel.createNode() },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeList(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val graph = viewModel.graph
    var kindFilter by remember { mutableStateOf<NodeKind?>(null) }

    val counts = viewModel.nodes.groupingBy { it.kind }.eachCount()
    // Grouped rather than filtered-by-default: the whole vault stays visible, but a run of
    // notes no longer hides the four RFCs sitting among them.
    val sections = NodeKind.entries
        .filter { kindFilter == null || it == kindFilter }
        .mapNotNull { kind ->
            viewModel.nodes.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { kind to it }
        }

    Column(modifier = modifier.background(Surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "All nodes",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.createNode() }) { Text("New", color = Accent) }
        }
        KindFilterBar(
            selected = kindFilter,
            counts = counts,
            onSelect = { kindFilter = it },
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            compact = true,
        )
        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            sections.forEach { (kind, nodes) ->
                stickyHeader(key = "header-${kind.name}") {
                    SectionHeader(kind = kind, count = nodes.size)
                }
                items(nodes, key = { it.id.value }) { node ->
                    NodeRow(
                        node = node,
                        isSelected = node.id == viewModel.selectedNodeId,
                        isBlocked = node.isTask && graph.isBlocked(node.id),
                        trackedSeconds = viewModel.trackedSecondsFor(node.id),
                        onClick = { viewModel.selectNode(node.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(kind: NodeKind, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        KindGlyph(kind, TextMuted, size = 7.dp)
        Text(
            kind.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun NodeRow(
    node: Node,
    isSelected: Boolean,
    isBlocked: Boolean,
    trackedSeconds: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(node = node, isBlocked = isBlocked)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                node.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) Accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(formatDuration(trackedSeconds))
                    if (isBlocked) append(" · blocked")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isBlocked) Blocked else TextMuted,
            )
        }
    }
}

/** A task's state at a glance; a plain note gets a hollow dot rather than no mark at all. */
@Composable
private fun StatusDot(node: Node, isBlocked: Boolean) {
    val color = when {
        !node.isTask -> TextMuted.copy(alpha = 0.45f)
        isBlocked -> Blocked
        node.task?.status == TaskStatus.Done -> Done
        node.task?.status == TaskStatus.Dropped -> TextMuted.copy(alpha = 0.5f)
        else -> Accent
    }
    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
}

@Composable
private fun EmptyEditorState(onCreateNote: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Pick a node, or start a new one.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
            )
            Button(onClick = onCreateNote, modifier = Modifier.padding(top = 12.dp)) { Text("New note") }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
