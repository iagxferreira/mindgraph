package dev.mindgraph

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mindgraph.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MindGraph",
        // The packaged icon is set by jpackage and only applies to an installed build; this is
        // the one the window manager reads at runtime, so `./gradlew run` and an installed copy
        // show the same thing in the switcher.
        icon = painterResource("mindgraph-icon.png"),
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App()
    }
}
