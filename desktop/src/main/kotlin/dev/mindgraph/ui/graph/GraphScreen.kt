package dev.mindgraph.ui.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mindgraph.state.AppViewModel

@Composable
fun GraphScreen(viewModel: AppViewModel) {
    var linkSourceId by remember { mutableStateOf<Long?>(null) }
    var pendingLinkTarget by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var relationshipText by remember { mutableStateOf("relates to") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.statusMessage) {
        snackbarHostState.showSnackbar(viewModel.statusMessage)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { viewModel.createNote("Untitled", "") }) {
                Text("New note")
            }
        },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                GraphCanvas(
                    notes = viewModel.notes,
                    links = viewModel.links,
                    layout = viewModel.layout,
                    selectedNoteId = viewModel.selectedNoteId,
                    linkSourceId = linkSourceId,
                    trackedSecondsForNote = viewModel::trackedSecondsForNote,
                    onSelectNote = { viewModel.selectNote(it) },
                    onLinkTarget = { targetId ->
                        val sourceId = linkSourceId
                        if (sourceId != null && sourceId != targetId) {
                            pendingLinkTarget = sourceId to targetId
                            relationshipText = "relates to"
                        }
                        linkSourceId = null
                    },
                )
                if (viewModel.notes.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    ) {
                        Text(
                            "No notes yet — start with \"New note\".",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            val selectedNote = viewModel.notes.find { it.id == viewModel.selectedNoteId }
            if (selectedNote != null) {
                NoteDetailPanel(
                    note = selectedNote,
                    viewModel = viewModel,
                    linkSourceId = linkSourceId,
                    onStartLink = { linkSourceId = it },
                    onCancelLink = { linkSourceId = null },
                )
            }
        }
    }

    val target = pendingLinkTarget
    if (target != null) {
        val (sourceId, targetId) = target
        AlertDialog(
            onDismissRequest = { pendingLinkTarget = null },
            title = { Text("Link notes") },
            text = {
                OutlinedTextField(
                    value = relationshipText,
                    onValueChange = { relationshipText = it },
                    label = { Text("Relationship") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createLink(sourceId, targetId, relationshipText)
                    pendingLinkTarget = null
                }) { Text("Create link") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLinkTarget = null }) { Text("Cancel") }
            },
        )
    }
}
