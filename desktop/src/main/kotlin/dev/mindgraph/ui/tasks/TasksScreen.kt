package dev.mindgraph.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.state.TaskGraph
import java.time.LocalDate
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Blocked
import dev.mindgraph.ui.theme.Overdue
import dev.mindgraph.ui.theme.Done
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.SurfaceHigh
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary

/**
 * Tasks grouped by what the graph says you can do, not just by what you typed. Ready comes
 * first and is ranked by how much finishing each one unblocks — the closest thing the app has
 * to an opinion about what to work on.
 */
@Composable
fun TasksScreen(
    viewModel: AppViewModel,
    onOpenNode: (NodeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = viewModel.graph
    // Archived work is put away, not finished — it belongs in neither column.
    val tasks = viewModel.nodes.filter { it.isTask && !it.archived }

    val doing = tasks.filter { it.task?.status == TaskStatus.Doing }
    val ready = graph.rankedReadyTasks()
    val blocked = tasks.filter { it.isLiveWork && graph.isBlocked(it.id) }
    val finished = tasks.filter { it.task?.status == TaskStatus.Done }

    Column(modifier = modifier.fillMaxSize().background(Ink).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tasks",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.createNode(asTask = true) }) {
                Text("New task", color = Accent)
            }
        }

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No tasks yet. Open any note and choose \"Make this a task\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            group("In progress", doing, graph, viewModel, onOpenNode)
            group("Ready", ready, graph, viewModel, onOpenNode)
            group("Blocked", blocked, graph, viewModel, onOpenNode)
            group("Done", finished, graph, viewModel, onOpenNode)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    label: String,
    nodes: List<Node>,
    graph: TaskGraph,
    viewModel: AppViewModel,
    onOpenNode: (NodeId) -> Unit,
) {
    if (nodes.isEmpty()) return
    item(key = "header-$label") {
        Text(
            "$label · ${nodes.size}",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        )
    }
    items(nodes.size, key = { "$label-${nodes[it].id.value}" }) { index ->
        val node = nodes[index]
        TaskRow(
            node = node,
            graph = graph,
            trackedSeconds = viewModel.trackedSecondsFor(node.id),
            isTracking = viewModel.isTracking(node.id),
            onOpen = { onOpenNode(node.id) },
            onToggleDone = {
                val next = if (node.task?.status == TaskStatus.Done) TaskStatus.Todo else TaskStatus.Done
                viewModel.setStatus(node.id, next)
            },
        )
    }
}

@Composable
private fun TaskRow(
    node: Node,
    graph: TaskGraph,
    trackedSeconds: Long,
    isTracking: Boolean,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val blockers = graph.blockers(node.id)
    val isDone = node.task?.status == TaskStatus.Done
    val unblocks = graph.unblockedCount(node.id)
    val due = node.task?.dueDate
    val isOverdue = due != null && !isDone && due.isBefore(LocalDate.now())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(if (isDone) Done.copy(alpha = 0.22f) else Color.Transparent)
                .clickable(onClick = onToggleDone)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                if (isDone) "✓" else "○",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDone) Done else TextMuted,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                node.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDone) TextMuted else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(formatDuration(trackedSeconds))
                    if (isTracking) append(" · tracking")
                    node.assignee?.let { append(" · @$it") }
                    // The list is ordered by this, so it has to be legible in the list.
                    if (due != null && !isDone) {
                        append(if (isOverdue) " · overdue $due" else " · due $due")
                    }
                    if (blockers.isNotEmpty()) {
                        append(" · needs ")
                        append(blockers.joinToString(", ") { it.title })
                    } else if (unblocks > 0 && !isDone) {
                        append(" · unblocks $unblocks")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isOverdue -> Overdue
                    blockers.isNotEmpty() -> Blocked
                    else -> TextMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 520.dp),
            )
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
