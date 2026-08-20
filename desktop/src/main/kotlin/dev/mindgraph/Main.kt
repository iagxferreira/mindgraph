package dev.mindgraph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.storage.Database
import dev.mindgraph.ui.graph.GraphScreen
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.MindGraphTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MindGraph",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        MindGraphTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                var viewModel by remember { mutableStateOf<AppViewModel?>(null) }

                LaunchedEffect(Unit) {
                    val database = withContext(Dispatchers.IO) { Database.openDefault() }
                    viewModel = AppViewModel(database)
                }

                val model = viewModel
                if (model == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    GraphScreen(model)
                }
            }
        }
    }
}
