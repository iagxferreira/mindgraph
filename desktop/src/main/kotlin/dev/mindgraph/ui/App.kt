package dev.mindgraph.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.mindgraph.mcp.McpDispatcher
import dev.mindgraph.mcp.McpHttpServer
import dev.mindgraph.mcp.VaultAccess
import dev.mindgraph.mcp.mindGraphTools
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.model.Worker
import dev.mindgraph.state.AppViewModel
import dev.mindgraph.state.LinkOutcome
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import dev.mindgraph.storage.VaultWatcher
import dev.mindgraph.ui.graph.GraphScreen
import dev.mindgraph.ui.notes.NotesScreen
import dev.mindgraph.ui.shell.Destination
import dev.mindgraph.ui.shell.NavRail
import dev.mindgraph.ui.tasks.TasksScreen
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.MindGraphTheme
import dev.mindgraph.ui.work.WorkScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App content: theme, vault bootstrap, and destination selection. Kept separate from Main.kt
 * so the window (chrome, sizing, close behavior) doesn't own app state.
 */
@Composable
fun App() {
    MindGraphTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            var viewModel by remember { mutableStateOf<AppViewModel?>(null) }

            LaunchedEffect(Unit) {
                val vault = withContext(Dispatchers.IO) {
                    Vault.default().also { it.prepare() }
                }
                viewModel = AppViewModel(NodeStore(vault), SessionLog(vault), VaultWatcher(vault))
            }

            val model = viewModel

            // The MCP server lives exactly as long as the window does, and it is handed the same
            // view model the UI draws — so a task an agent creates lands on the graph on screen,
            // not just on disk.
            if (model != null) {
                DisposableEffect(model) {
                    val vault = object : VaultAccess {
                        override suspend fun createTask(
                            title: String,
                            body: String,
                            due: String?,
                            assignee: String?,
                        ): Node = model.createNodeNow(
                            title,
                            body,
                            asTask = true,
                            due = due,
                            assignee = assignee,
                        )

                        override suspend fun createNote(
                            title: String,
                            body: String,
                            kind: NodeKind,
                            assignee: String?,
                        ): Node = model.createNodeNow(
                            title,
                            body,
                            asTask = false,
                            assignee = assignee,
                            kind = kind,
                        )

                        override suspend fun appendToBody(nodeId: NodeId, content: String): Node? =
                            model.appendToBodyNow(nodeId, content)

                        override suspend fun nodes(): List<Node> = model.nodes

                        override suspend fun link(
                            sourceId: NodeId,
                            targetId: NodeId,
                            kind: EdgeKind,
                        ): LinkOutcome = model.linkNow(sourceId, targetId, kind)

                        override suspend fun setStatus(
                            nodeId: NodeId,
                            status: TaskStatus,
                            due: String?,
                            agent: String?,
                            assignee: String?,
                        ): Node? = model.setStatusNow(
                            nodeId,
                            status,
                            due,
                            // Reaching the app over MCP is what makes it machine labour; the
                            // same change made in the window is yours.
                            worker = Worker.Agent,
                            agent = agent,
                            assignee = assignee,
                        )

                        override suspend fun trackedSeconds(nodeId: NodeId): Long =
                            model.trackedSecondsFor(nodeId)
                    }
                    val server = McpHttpServer(McpDispatcher(mindGraphTools(vault)))
                    if (server.start()) {
                        println("MindGraph: MCP server listening on ${server.endpoint}")
                    }
                    onDispose { server.stop() }
                }
            }

            if (model == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                AppShell(model)
            }
        }
    }
}

/**
 * The nav rail plus the active destination. Link-in-progress state lives here because linking
 * spans two destinations: you start it on a node and finish it on the graph.
 */
@Composable
private fun AppShell(viewModel: AppViewModel) {
    var destination by remember { mutableStateOf(Destination.Graph) }
    var linkSourceId by remember { mutableStateOf<NodeId?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.statusMessage) {
        snackbarHostState.showSnackbar(viewModel.statusMessage)
    }

    val openNode: (NodeId) -> Unit = { nodeId ->
        viewModel.selectNode(nodeId)
        destination = Destination.Notes
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavRail(current = destination, onSelect = { destination = it })
            VerticalDivider()

            when (destination) {
                Destination.Graph -> GraphScreen(
                    viewModel = viewModel,
                    linkSourceId = linkSourceId,
                    onStartLink = { linkSourceId = it },
                    onCancelLink = { linkSourceId = null },
                    onOpenNode = openNode,
                    modifier = Modifier.weight(1f),
                )

                Destination.Notes -> NotesScreen(
                    viewModel = viewModel,
                    linkSourceId = linkSourceId,
                    onStartLink = { nodeId ->
                        linkSourceId = nodeId
                        destination = Destination.Graph
                    },
                    onCancelLink = { linkSourceId = null },
                    modifier = Modifier.weight(1f),
                )

                Destination.Tasks -> TasksScreen(
                    viewModel = viewModel,
                    onOpenNode = openNode,
                    modifier = Modifier.weight(1f),
                )

                Destination.Work -> WorkScreen(
                    viewModel = viewModel,
                    onOpenNode = openNode,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
