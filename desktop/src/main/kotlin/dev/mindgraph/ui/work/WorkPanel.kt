package dev.mindgraph.ui.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mindgraph.model.Note
import dev.mindgraph.model.RunState
import dev.mindgraph.state.AppViewModel

/** Start/pause/stop time tracking for the currently selected note. */
@Composable
fun WorkPanel(note: Note, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val workItem = viewModel.workItemForNote(note.id)
    val tracked = viewModel.trackedSecondsForNote(note.id)
    val isRunning = viewModel.runningWorkItemId != null && workItem?.id == viewModel.runningWorkItemId

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Time tracked", style = MaterialTheme.typography.titleSmall)
        Text(formatDuration(tracked), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                isRunning -> {
                    Button(onClick = { viewModel.pauseWork(workItem!!.id) }) { Text("Pause") }
                    OutlinedButton(onClick = { viewModel.stopWork(workItem!!.id) }) { Text("Stop") }
                }
                workItem != null && workItem.runState == RunState.Paused -> {
                    Button(onClick = { viewModel.startWork(note.id) }) { Text("Resume") }
                    OutlinedButton(onClick = { viewModel.stopWork(workItem.id) }) { Text("Stop") }
                }
                else -> Button(onClick = { viewModel.startWork(note.id) }) { Text("Start") }
            }
        }
    }
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
