package dev.mindgraph.ui.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.state.NodeWork
import dev.mindgraph.state.WorkSummary
import dev.mindgraph.state.WorkerSplit
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Blocked
import dev.mindgraph.ui.theme.Border
import dev.mindgraph.ui.theme.Done
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.Machine
import dev.mindgraph.ui.theme.Overdue
import dev.mindgraph.ui.theme.SurfaceHigh
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary
import kotlin.math.roundToInt

private val AgentColors = listOf(Accent, Machine, Done, Blocked, Overdue)

/**
 * Where the time went, and whose it was.
 *
 * The split between your hours and a machine's is the question this screen exists to answer:
 * a total that mixes them tells you a task was expensive without telling you who paid.
 */
@Composable
fun WorkScreen(
    viewModel: AppViewModel,
    onOpenNode: (NodeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions = viewModel.sessions + listOfNotNull(viewModel.liveSession)
    val ranked = WorkSummary.byNode(viewModel.nodes, sessions)
    val agents = WorkSummary.byAgent(sessions)
    val total = WorkSummary.splitOf(sessions)
    val busiest = ranked.firstOrNull()?.split?.total ?: 0L
    val agentColors = agents
        .map { it.name ?: "unnamed agent" }
        .distinct()
        .sorted()
        .mapIndexed { index, name -> name to AgentColors[index % AgentColors.size] }
        .toMap()

    Column(modifier = modifier.fillMaxSize().background(Ink).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Work", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Legend()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = "Total tracked",
                value = formatDuration(total.total),
                detail = if (total.total > 0) {
                    "${formatDuration(total.human)} Yours  ·  ${formatDuration(total.agent)} Machine"
                } else {
                    null
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard("Yours", formatDuration(total.human), null, Modifier.weight(1f).fillMaxHeight(), Accent)
            StatCard(
                label = "Machine",
                value = formatDuration(total.agent),
                detail = if (total.total > 0) {
                    "${(total.agentShare * 100).roundToInt()}% of all tracked time"
                } else {
                    null
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                tint = Machine,
            )
            StatCard("Nodes worked", ranked.size.toString(), null, Modifier.weight(1f).fillMaxHeight())
        }

        if (agents.isNotEmpty()) {
            SectionTitle("Contributors", "Time grouped by agent")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                agents.forEach { agent ->
                    val agentName = agent.name ?: "unnamed agent"
                    val agentColor = agentColors[agentName] ?: Accent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceHigh)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Swatch(agentColor)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            // An agent that never introduced itself still did the work.
                            agentName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatDuration(agent.seconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = agentColor,
                        )
                    }
                }
            }
        }

        SectionTitle("Activity by node", "Most tracked time first")

        if (ranked.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No tracked time yet. Start a timer on a note, or let an agent move a task " +
                        "to doing over MCP.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ranked, key = { it.node.id.value }) { row ->
                    WorkRow(
                        node = row.node,
                        split = row.split,
                        fraction = if (busiest > 0) row.split.total.toFloat() / busiest else 0f,
                        agentColors = agentColors,
                        onClick = { onOpenNode(row.node.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Spacer(Modifier.width(10.dp))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem(Accent, "Yours")
        LegendItem(Machine, "Machine")
    }
}

@Composable
private fun LegendItem(tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Swatch(tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun Swatch(tint: Color) {
    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(tint))
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        detail?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun WorkRow(
    node: Node,
    split: WorkerSplit,
    fraction: Float,
    agentColors: Map<String, Color>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
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
            Text(
                formatDuration(split.total),
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
            )
        }

        // Length is this node against the busiest one; segments show who did the work.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Accent.copy(alpha = 0.12f)),
        ) {
            Row(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()) {
                if (split.human > 0) {
                    Box(
                        modifier = Modifier
                            .weight(split.human.toFloat() / split.total.toFloat())
                            .fillMaxHeight()
                            .background(Accent),
                    )
                }
                split.agents.entries
                    .sortedBy { it.key ?: "" }
                    .forEach { (name, seconds) ->
                        Box(
                            modifier = Modifier
                                .weight(seconds.toFloat() / split.total.toFloat())
                                .fillMaxHeight()
                                .background(agentColors[name ?: "unnamed agent"] ?: Machine),
                        )
                    }
                if (split.agent > 0 && split.agents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(split.agent.toFloat() / split.total.toFloat())
                            .fillMaxHeight()
                            .background(Machine),
                    )
                }
            }
        }

        Text(
            buildString {
                if (split.human > 0) append("${formatDuration(split.human)} yours")
                if (split.human > 0 && split.agent > 0) append(" · ")
                if (split.agent > 0) append("${formatDuration(split.agent)} machine")
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

/** Keep all meaningful units visible so short and long sessions remain precise. */
private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (seconds > 0 || isEmpty()) add("${seconds}s")
    }.joinToString(" ")
}
