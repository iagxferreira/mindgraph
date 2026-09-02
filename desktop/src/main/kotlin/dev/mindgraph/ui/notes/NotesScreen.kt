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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
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
import dev.mindgraph.ui.shell.DoneFilterToggle
import dev.mindgraph.ui.shell.KindGlyph
import dev.mindgraph.ui.shell.label
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Blocked
import dev.mindgraph.ui.theme.Done
import dev.mindgraph.ui.theme.Surface
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary

private val ListWidth = 320.dp

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
    var hideDone by remember { mutableStateOf(true) }
    var showArchived by remember { mutableStateOf(false) }

    val filteredNodes = viewModel.nodes.filter {
        it.archived == showArchived &&
            (showArchived || !hideDone || it.task?.status != TaskStatus.Done)
    }
    val counts = filteredNodes.groupingBy { it.kind }.eachCount()
    // Grouped rather than filtered-by-default: the whole vault stays visible, but a run of
    // notes no longer hides the four RFCs sitting among them.
    val sections = NodeKind.entries
        .filter { kindFilter == null || it == kindFilter }
        .mapNotNull { kind ->
            filteredNodes.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { kind to it }
        }

    Column(modifier = modifier.background(Surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "All nodes",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                // This column is narrow and the controls beside it are fixed width, so without
                // a line limit the title wraps a word at a time rather than the row giving way.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The count that stood here said exactly what the All pill below it says — counts
            // is grouped from the same filtered list — and it was spending width to do it.
            TextButton(onClick = { viewModel.createNode() }) { Text("New", color = Accent) }
            ImportMemoryButton(onImport = { viewModel.importClaudeMemory() })
            DoneFilterToggle(hideDone = hideDone, onToggle = { hideDone = !hideDone })
            ArchiveFilterMenu(showArchived = showArchived, onChange = { showArchived = it })
        }
        KindFilterBar(
            selected = kindFilter,
            counts = counts,
            onSelect = { kindFilter = it },
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
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
@OptIn(ExperimentalMaterial3Api::class)
private fun ArchiveFilterMenu(showArchived: Boolean, onChange: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = if (showArchived) "Show active nodes" else "Show archived nodes"

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Box {
            IconButton(onClick = { open = true }) {
                Icon(
                    imageVector = if (showArchived) Icons.Outlined.Unarchive else Icons.Outlined.Inventory2,
                    contentDescription = description,
                    tint = if (showArchived) Accent else TextMuted,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Active nodes") },
                    leadingIcon = { Icon(Icons.Outlined.Unarchive, contentDescription = null) },
                    onClick = { onChange(false); open = false },
                )
                DropdownMenuItem(
                    text = { Text("Archived nodes") },
                    leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                    onClick = { onChange(true); open = false },
                )
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

/**
 * Pulls in Claude Code's memory notes. Safe to press twice: files already imported are skipped,
 * which matters because Claude keeps writing new ones and this is how they arrive.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ImportMemoryButton(onImport: () -> Unit) {
    val description = "Import Claude memory notes"
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onImport) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = description,
                tint = TextMuted,
            )
        }
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(node = node, isBlocked = isBlocked)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                node.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) Accent else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(formatDuration(trackedSeconds))
                    node.assignee?.let { append(" · @$it") }
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
