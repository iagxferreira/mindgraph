package dev.mindgraph.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.model.NodeKind
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.shell.KindGlyph
import dev.mindgraph.ui.theme.Blocked
import dev.mindgraph.ui.theme.Border
import dev.mindgraph.ui.theme.Done
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary
import java.time.LocalDate
import dev.mindgraph.ui.theme.Overdue
import dev.mindgraph.ui.theme.SurfaceHigh
import dev.mindgraph.ui.theme.Surface
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.border

private const val EditModeRatio = 1f
private const val SplitModeRatio = 0.5f
private const val PreviewModeRatio = 0f
private val EditorControlShape = RoundedCornerShape(8.dp)
private val EditorControlHeight = 34.dp

/**
 * The writing surface. A node is edited the same way whether or not it's a task — the task
 * controls sit in the header rather than opening a different kind of screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorPanel(
    node: Node,
    viewModel: AppViewModel,
    linkSourceId: NodeId?,
    onStartLink: (NodeId) -> Unit,
    onCancelLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var titleField by remember(node.id) { mutableStateOf(TextFieldValue(node.title)) }
    var bodyField by remember(node.id) { mutableStateOf(TextFieldValue(node.body)) }
    var splitRatio by remember(node.id) { mutableStateOf(PreviewModeRatio) }

    val graph = viewModel.graph
    val blockers = graph.blockers(node.id)
    val isPreview = splitRatio < 0.02f

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = titleField,
                onValueChange = { titleField = it },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = TextPrimary),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.weight(1f),
            )
            EditorModeToggle(splitRatio = splitRatio, onRatioChange = { splitRatio = it })
            TimeTrackingRow(node = node, viewModel = viewModel)
            IconButton(onClick = { viewModel.deleteNode(node.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete note")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 24.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditorControlRow(label = "Node") {
                KindChip(kind = node.kind, onChange = { viewModel.setKind(node.id, it) })
                AssigneeChip(
                    assignee = node.assignee,
                    onSet = { viewModel.setAssignee(node.id, it) },
                )
            }
            EditorControlRow(
                label = "Task",
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (node.isTask) {
                    StatusMenu(
                        selected = node.task?.status,
                        onSelect = { viewModel.setStatus(node.id, it) },
                    )
                    DueChip(
                        due = node.task?.due,
                        isOverdue = node.task?.dueDate?.isBefore(LocalDate.now()) == true &&
                            node.task?.status != TaskStatus.Done,
                        onSet = { viewModel.setDue(node.id, it) },
                    )
                } else {
                    TextButton(onClick = { viewModel.promoteToTask(node.id) }) {
                    Text("Convert to task", style = MaterialTheme.typography.labelMedium, color = Accent)
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.setArchived(node.id, !node.archived) },
                    modifier = Modifier.height(EditorControlHeight),
                    shape = EditorControlShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                ) {
                    Icon(
                        imageVector = if (node.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (node.archived) "Restore" else "Archive",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (node.archived) {
            Text(
                "Archived — kept, but out of the graph and out of ready work.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
        }

        if (blockers.isNotEmpty()) {
            Text(
                "Blocked by ${blockers.joinToString(", ") { it.title }}",
                style = MaterialTheme.typography.labelSmall,
                color = Blocked,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
        }

        HorizontalDivider()

        if (!isPreview) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MarkdownToolbar(value = bodyField, onValueChange = { bodyField = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.saveNode(node.id, titleField.text, bodyField.text) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            }
            HorizontalDivider()
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val showEditor = splitRatio > 0.02f
            val showPreview = splitRatio < 0.98f

            Row(modifier = Modifier.fillMaxSize()) {
                if (showEditor) {
                    OutlinedTextField(
                        value = bodyField,
                        onValueChange = { bodyField = it },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text("Write markdown here… [[link]] to another note.") },
                        modifier = Modifier
                            .weight(if (showPreview) splitRatio else 1f)
                            .fillMaxHeight()
                            .padding(12.dp),
                    )
                }
                if (showEditor && showPreview) {
                    SplitDragHandle(
                        onDrag = { deltaX ->
                            splitRatio = (splitRatio + deltaX / totalWidthPx).coerceIn(0f, 1f)
                        },
                    )
                }
                if (showPreview) {
                    MarkdownPreview(
                        text = bodyField.text,
                        modifier = Modifier
                            .weight(if (showEditor) 1f - splitRatio else 1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
        HorizontalDivider()

        LinksFooter(
            node = node,
            viewModel = viewModel,
            linkSourceId = linkSourceId,
            onStartLink = onStartLink,
            onCancelLink = onCancelLink,
        )
    }
}

@Composable
private fun EditorControlRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.width(44.dp),
        )
        content()
    }
}

/**
 * Who is meant to pick this up. Free text, because the names that matter are whatever you
 * and your agents already call yourselves — the session log takes an agent's own word for
 * its name, and this should agree with it.
 */
@Composable
private fun AssigneeChip(assignee: String?, onSet: (String?) -> Unit) {
    var editing by remember(assignee) { mutableStateOf(false) }
    var draft by remember(assignee) { mutableStateOf(assignee.orEmpty()) }

    fun commit() {
        onSet(draft.trim().takeIf { it.isNotEmpty() })
        editing = false
    }

    if (editing) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .width(128.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(SurfaceHigh)
                .border(1.dp, Border, RoundedCornerShape(7.dp))
                .padding(horizontal = 9.dp, vertical = 6.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> { commit(); true }
                        Key.Escape -> { draft = assignee.orEmpty(); editing = false; true }
                        else -> false
                    }
                },
            decorationBox = { field ->
                if (draft.isEmpty()) {
                    Text(
                        "Who?",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                }
                field()
            },
        )
    } else {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(SurfaceHigh)
                .clickable { editing = true }
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (assignee == null) "+ Assign" else "@$assignee",
                style = MaterialTheme.typography.labelMedium,
                color = if (assignee == null) TextMuted else Accent,
                maxLines = 1,
                softWrap = false,
            )
            if (assignee != null) {
                Text(
                    "×",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.clickable { onSet(null) },
                )
            }
        }
    }
}

/**
 * What this document is — note, RFC, or reference. A menu rather than a row of chips: the
 * header already carries status and a deadline, and kind changes about once in a node's life.
 */
@Composable
private fun KindChip(kind: NodeKind, onChange: (NodeKind) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(SurfaceHigh)
                .clickable { open = true }
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KindGlyph(kind, TextMuted, size = 8.dp)
            Text(
                kind.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NodeKind.entries.forEach { option ->
                DropdownMenuItem(
                    onClick = { onChange(option); open = false },
                    leadingIcon = {
                        KindGlyph(option, if (option == kind) Accent else TextMuted, size = 8.dp)
                    },
                    text = {
                        Text(
                            option.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (option == kind) Accent else TextPrimary,
                        )
                    },
                )
            }
        }
    }
}

/**
 * The deadline, shown and editable in place. A date is typed rather than picked: the vault
 * stores text, an ISO date is what the ranking reads, and a calendar widget would be a lot of
 * surface for a field whose whole job is to be one line of frontmatter.
 *
 * Nothing is committed until the text parses, so a half-typed date cannot clear a real one.
 */
@Composable
private fun DueChip(due: String?, isOverdue: Boolean, onSet: (String?) -> Unit) {
    var editing by remember(due) { mutableStateOf(false) }
    var draft by remember(due) { mutableStateOf(due.orEmpty()) }

    val parsed = runCatching { LocalDate.parse(draft.trim()) }.getOrNull()
    val isDraftUsable = draft.isBlank() || parsed != null

    fun commit() {
        if (!isDraftUsable) return
        onSet(draft.trim().takeIf { it.isNotEmpty() })
        editing = false
    }

    if (editing) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium.copy(
                color = if (isDraftUsable) TextPrimary else Overdue,
            ),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .width(112.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(SurfaceHigh)
                .border(1.dp, if (isDraftUsable) Border else Overdue, RoundedCornerShape(7.dp))
                .padding(horizontal = 9.dp, vertical = 6.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> { commit(); true }
                        Key.Escape -> { draft = due.orEmpty(); editing = false; true }
                        else -> false
                    }
                },
            decorationBox = { field ->
                if (draft.isEmpty()) {
                    Text(
                        "yyyy-mm-dd",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                }
                field()
            },
        )
    } else {
        val tint = if (isOverdue) Overdue else TextMuted
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(SurfaceHigh)
                .clickable { editing = true }
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (due == null) "+ Due" else if (isOverdue) "Overdue $due" else "Due $due",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
            if (due != null) {
                Text(
                    "×",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.clickable { onSet(null) },
                )
            }
        }
    }
}

@Composable
private fun StatusMenu(selected: TaskStatus?, onSelect: (TaskStatus) -> Unit) {
    var open by remember(selected) { mutableStateOf(false) }
    val current = selected ?: TaskStatus.Todo
    val hue = when (current) {
        TaskStatus.Done -> Done
        TaskStatus.Dropped -> TextMuted
        else -> Accent
    }

    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.height(EditorControlHeight),
            shape = EditorControlShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = hue),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(hue),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Status: ${current.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelMedium,
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TaskStatus.entries.forEach { status ->
                DropdownMenuItem(
                    onClick = { onSelect(status); open = false },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when (status) {
                                        TaskStatus.Done -> Done
                                        TaskStatus.Dropped -> TextMuted
                                        else -> Accent
                                    },
                                ),
                        )
                    },
                    text = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

@Composable
private fun EditorModeToggle(splitRatio: Float, onRatioChange: (Float) -> Unit) {
    Row {
        listOf("Edit" to EditModeRatio, "Split" to SplitModeRatio, "Preview" to PreviewModeRatio)
            .forEach { (label, ratio) ->
                val isActive = when (label) {
                    "Edit" -> splitRatio > 0.98f
                    "Preview" -> splitRatio < 0.02f
                    else -> splitRatio in 0.02f..0.98f
                }
                TextButton(onClick = { onRatioChange(ratio) }) {
                    Text(
                        label,
                        color = if (isActive) Accent else TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
    }
}

/** A thin, draggable divider between the editor and preview panes; reports the raw pixel delta. */
@Composable
private fun SplitDragHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .width(6.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            }
            .background(TextMuted.copy(alpha = 0.25f)),
    )
}

@Composable
private fun TimeTrackingRow(node: Node, viewModel: AppViewModel) {
    val tracked = viewModel.trackedSecondsFor(node.id)
    val isRunning = viewModel.isTracking(node.id)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            Icons.Default.Timer,
            contentDescription = null,
            tint = if (isRunning) Accent else TextMuted,
            modifier = Modifier.size(18.dp),
        )
        Text(formatDuration(tracked), style = MaterialTheme.typography.labelLarge)
        if (isRunning) {
            IconButton(onClick = { viewModel.stopWork(node.id) }) {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
            }
        } else {
            IconButton(onClick = { viewModel.startWork(node.id) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start")
            }
        }
    }
}

@Composable
private fun LinksFooter(
    node: Node,
    viewModel: AppViewModel,
    linkSourceId: NodeId?,
    onStartLink: (NodeId) -> Unit,
    onCancelLink: () -> Unit,
) {
    val titleOf = { id: NodeId -> viewModel.nodeById(id)?.title ?: "unknown" }
    val incoming = viewModel.nodes.filter { node.id in it.dependsOn || node.id in it.relatesTo }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (linkSourceId == node.id) {
            AssistChip(onClick = onCancelLink, label = { Text("Click a node in the graph…") })
        } else {
            AssistChip(onClick = { onStartLink(node.id) }, label = { Text("+ Link") })
        }
        node.dependsOn.forEach { id ->
            LinkChip(
                label = "needs ${titleOf(id)}",
                tint = Blocked,
                onDelete = { viewModel.unlink(node.id, id) },
            )
        }
        node.relatesTo.forEach { id ->
            LinkChip(
                label = "→ ${titleOf(id)}",
                tint = TextMuted,
                onDelete = { viewModel.unlink(node.id, id) },
            )
        }
        incoming.forEach { referrer ->
            LinkChip(label = "← ${referrer.title}", tint = TextMuted, onDelete = null)
        }
    }
}

@Composable
private fun LinkChip(label: String, tint: Color, onDelete: (() -> Unit)?) {
    InputChip(
        selected = false,
        onClick = {},
        label = { Text(label, color = tint) },
        border = null,
        trailingIcon = onDelete?.let {
            {
                IconButton(onClick = it, modifier = Modifier.size(18.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove link", modifier = Modifier.size(14.dp))
                }
            }
        },
    )
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
