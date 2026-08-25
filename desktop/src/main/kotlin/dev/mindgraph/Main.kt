package dev.mindgraph

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mindgraph.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MindGraph",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App()
    }
}
