package dev.mindgraph.ui.graph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.Note
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.ui.work.WorkPanel

@Composable
fun NoteDetailPanel(
    note: Note,
    viewModel: AppViewModel,
    linkSourceId: Long?,
    onStartLink: (Long) -> Unit,
    onCancelLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var document by remember(note.id) { mutableStateOf("") }

    LaunchedEffect(note.id, note.path) {
        document = viewModel.readNoteDocument(note.path)
    }

    val outgoing = viewModel.links.filter { it.sourceNoteId == note.id }
    val incoming = viewModel.links.filter { it.targetNoteId == note.id }
    val notesById = viewModel.notes.associateBy { it.id }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = document,
            onValueChange = { document = it },
            label = { Text("Notes (markdown)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.updateNote(note.id, title, document) }) { Text("Save") }
            OutlinedButton(onClick = { viewModel.deleteNote(note.id) }) { Text("Delete") }
        }

        HorizontalDivider()
        WorkPanel(note = note, viewModel = viewModel)

        HorizontalDivider()
        Text("Links", style = MaterialTheme.typography.titleSmall)
        if (linkSourceId == note.id) {
            Text(
                "Click another note in the graph to link it here.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onCancelLink) { Text("Cancel linking") }
        } else {
            OutlinedButton(onClick = { onStartLink(note.id) }) { Text("Link to another note…") }
        }

        if (outgoing.isNotEmpty()) {
            Text("Outgoing", style = MaterialTheme.typography.labelMedium)
            outgoing.forEach { link ->
                LinkRow(
                    label = notesById[link.targetNoteId]?.title ?: "unknown",
                    relationship = link.relationship,
                    onDelete = { viewModel.deleteLink(link.id) },
                )
            }
        }
        if (incoming.isNotEmpty()) {
            Text("Incoming", style = MaterialTheme.typography.labelMedium)
            incoming.forEach { link ->
                LinkRow(
                    label = notesById[link.sourceNoteId]?.title ?: "unknown",
                    relationship = link.relationship,
                    onDelete = { viewModel.deleteLink(link.id) },
                )
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, relationship: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(relationship, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remove link")
        }
    }
}
