package dev.mindgraph.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.NodeKind
import dev.mindgraph.storage.FolderImport
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.Border
import dev.mindgraph.ui.theme.SurfaceHigh
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary
import java.nio.file.Path

/**
 * Confirms what a chosen folder is before importing it.
 *
 * The kind and the project are guessed from the path and shown rather than applied silently. A
 * folder called `adr` almost certainly holds RFCs and `~/workspace/tally/docs/adr` is almost
 * certainly tally's - but "almost certainly" is exactly the kind of guess that should be visible
 * and correctable before it is written onto every node in the folder.
 */
@Composable
fun FolderImportDialog(
    folder: Path,
    fileCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (NodeKind, String) -> Unit,
) {
    var kind by remember(folder) { mutableStateOf(FolderImport.suggestedKind(folder)) }
    var project by remember(folder) { mutableStateOf(FolderImport.projectNameFor(folder)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import ${folder.fileName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    folder.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (fileCount == 1) "1 markdown file" else "$fileCount markdown files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Text(
                    "The files are copied into your vault and the folder is never written to. " +
                        "Later edits there will not reach the copies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )

                Text("Import as", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NodeKind.entries.forEach { candidate ->
                        KindChoice(
                            kind = candidate,
                            isActive = candidate == kind,
                            onClick = { kind = candidate },
                        )
                    }
                }

                OutlinedTextField(
                    value = project,
                    onValueChange = { project = it },
                    label = { Text("Project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(kind, project.trim()) },
                // A blank project would put the whole folder in the group named after nothing,
                // which is where everything written in this vault already lives.
                enabled = fileCount > 0 && project.isNotBlank(),
            ) {
                Text("Import", color = if (fileCount > 0 && project.isNotBlank()) Accent else TextMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
    )
}

@Composable
private fun KindChoice(kind: NodeKind, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Accent.copy(alpha = 0.18f) else SurfaceHigh)
            .border(1.dp, if (isActive) Accent else Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            kind.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) Accent else TextMuted,
        )
    }
}
