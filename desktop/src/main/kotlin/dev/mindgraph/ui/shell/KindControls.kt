package dev.mindgraph.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import dev.mindgraph.model.NodeKind
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Border
import dev.mindgraph.ui.theme.SurfaceHigh
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary

/** What a kind is called in the interface, where a plural reads better than an enum name. */
val NodeKind.label: String
    get() = when (this) {
        NodeKind.Note -> "Notes"
        NodeKind.Rfc -> "RFCs"
        NodeKind.Reference -> "References"
    }

/**
 * The same shape the graph draws for this kind, small enough to sit in a label.
 *
 * Shape rather than colour because colour on the canvas is already spent on task state, and a
 * second meaning on one channel is how a legend stops being readable.
 */
@Composable
fun KindGlyph(kind: NodeKind, tint: Color, size: Dp = 9.dp) {
    val shape = when (kind) {
        NodeKind.Note -> CircleShape
        NodeKind.Rfc -> RectangleShape
        NodeKind.Reference -> RoundedCornerShape(1.dp)
    }
    Box(
        modifier = Modifier
            .size(size)
            // A diamond is a square that knows where it's going.
            .rotate(if (kind == NodeKind.Rfc) 45f else 0f)
            .background(tint, shape),
    )
}

/**
 * Narrows a view to one kind. Null is everything, which is the default and the way back.
 *
 * A filter rather than a cluster: showing only the RFCs answers the same question as gathering
 * them into a corner, and leaves the edges between what remains meaning what they meant.
 */
@Composable
fun KindFilterBar(
    selected: NodeKind?,
    counts: Map<NodeKind, Int>,
    onSelect: (NodeKind?) -> Unit,
    modifier: Modifier = Modifier,
    /** Drops the labels, leaving shape and count. For columns too narrow to spell "References". */
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(SurfaceHigh)
            .border(1.dp, Border, RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterPill(
            kind = null,
            count = counts.values.sum(),
            isActive = selected == null,
            compact = compact,
            onClick = { onSelect(null) },
        )
        NodeKind.entries.forEach { kind ->
            FilterPill(
                kind = kind,
                count = counts[kind] ?: 0,
                isActive = selected == kind,
                compact = compact,
                onClick = { onSelect(if (selected == kind) null else kind) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DoneFilterToggle(
    hideDone: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = if (hideDone) "Show done tasks" else "Hide done tasks"
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onToggle, modifier = modifier) {
            Icon(
                imageVector = if (hideDone) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = description,
                tint = if (hideDone) Accent else TextMuted,
            )
        }
    }
}

@Composable
private fun FilterPill(
    kind: NodeKind?,
    count: Int,
    isActive: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    // A kind with nothing in it stays visible so the row doesn't reshuffle as a vault fills up.
    val isEmpty = count == 0 && kind != null
    val tint = when {
        isActive -> Accent
        isEmpty -> TextMuted.copy(alpha = 0.45f)
        else -> TextMuted
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (isActive) Accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = !isEmpty, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        kind?.let { KindGlyph(it, tint, size = 8.dp) }
        // The label is what overflows a narrow column; the shape and the count still identify it.
        if (!compact || kind == null) {
            Text(
                text = kind?.label ?: "All",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) TextPrimary else tint,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}
