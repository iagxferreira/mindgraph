package dev.mindgraph.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.mindgraph.model.Edge
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.model.WorkSession
import dev.mindgraph.model.currentUnixTimestamp
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.WikiLinks
import dev.mindgraph.storage.toEdges
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single source of UI state. Reads through [NodeStore], which reads the vault — so the
 * markdown on disk stays authoritative and anything edited outside the app shows up on reload.
 */
class AppViewModel(
    private val store: NodeStore,
    private val sessionLog: SessionLog,
) {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val layout = GraphLayoutEngine()

    var nodes by mutableStateOf<List<Node>>(emptyList())
        private set
    var edges by mutableStateOf<List<Edge>>(emptyList())
        private set
    var sessions by mutableStateOf<List<WorkSession>>(emptyList())
        private set

    var selectedNodeId by mutableStateOf<NodeId?>(null)
        private set
    var statusMessage by mutableStateOf("Ready")
        private set

    /** The node whose timer is running, plus when it started. Null when nothing is tracking. */
    var runningNodeId by mutableStateOf<NodeId?>(null)
        private set
    var runningSinceUnix by mutableStateOf<Long?>(null)
        private set
    var liveNowUnix by mutableStateOf(currentUnixTimestamp())
        private set

    private var tickerJob: Job? = null

    val graph: TaskGraph get() = TaskGraph(nodes)

    init {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        nodes = store.load()
        edges = nodes.toEdges()
        sessions = sessionLog.load()
        layout.sync(nodes.map { it.id.value }, edges)
    }

    fun selectNode(nodeId: NodeId?) {
        selectedNodeId = nodeId
    }

    fun nodeById(nodeId: NodeId?): Node? = nodes.find { it.id == nodeId }

    // ---- authoring ----

    fun createNode(title: String = "Untitled", body: String = "", asTask: Boolean = false) {
        scope.launch { createNodeNow(title, body, asTask) }
    }

    /**
     * The same creation the UI performs, but suspending and returning what it made. Agents
     * calling in over MCP need the id back, and routing them through here — rather than at the
     * store — is what makes a task an agent created appear on the graph without a reload.
     */
    suspend fun createNodeNow(title: String, body: String = "", asTask: Boolean = false): Node {
        val facet = if (asTask) TaskFacet(status = TaskStatus.Todo) else null
        val created = store.create(title.trim().ifBlank { "Untitled" }, body, facet)
        refresh()
        selectedNodeId = created.id
        statusMessage = if (asTask) "Task created" else "Note created"
        return created
    }

    /**
     * Saves title and body only. `[[wikilinks]]` are deliberately *not* copied into frontmatter —
     * they are re-derived from the body on every load, so removing a link from the prose removes
     * the edge with it.
     */
    fun saveNode(nodeId: NodeId, title: String, body: String) {
        scope.launch {
            val node = nodeById(nodeId) ?: return@launch
            store.save(node.copy(title = title.trim().ifBlank { "Untitled" }, body = body))
            refresh()
            statusMessage = "Saved"
        }
    }

    /** Titles linked in this node's body that don't exist yet — offer to create them. */
    fun danglingLinks(nodeId: NodeId): List<String> =
        nodeById(nodeId)?.let { WikiLinks.unresolved(it.body, nodes) }.orEmpty()

    fun deleteNode(nodeId: NodeId) {
        scope.launch {
            if (runningNodeId == nodeId) stopWork(nodeId)
            store.delete(nodeId)
            sessionLog.purge(nodeId)
            if (selectedNodeId == nodeId) selectedNodeId = null
            refresh()
            statusMessage = "Deleted"
        }
    }

    // ---- task facet ----

    fun promoteToTask(nodeId: NodeId) {
        scope.launch {
            val node = nodeById(nodeId) ?: return@launch
            if (node.isTask) return@launch
            store.save(node.copy(task = TaskFacet(status = TaskStatus.Todo)))
            refresh()
            statusMessage = "Now a task"
        }
    }

    fun demoteToNote(nodeId: NodeId) {
        scope.launch {
            val node = nodeById(nodeId) ?: return@launch
            store.save(node.copy(task = null))
            refresh()
            statusMessage = "Back to a note"
        }
    }

    fun setStatus(nodeId: NodeId, status: TaskStatus) {
        scope.launch {
            val node = nodeById(nodeId) ?: return@launch
            val facet = node.task ?: TaskFacet(status = status)
            store.save(
                node.copy(
                    task = facet.copy(
                        status = status,
                        completedAt = if (status == TaskStatus.Done) Instant.now().toString() else null,
                    ),
                ),
            )
            refresh()
            statusMessage = "Marked ${status.name.lowercase()}"
        }
    }

    // ---- edges ----

    fun linkRelates(sourceId: NodeId, targetId: NodeId) {
        scope.launch {
            val source = nodeById(sourceId) ?: return@launch
            if (sourceId == targetId || targetId in source.relatesTo) return@launch
            store.save(source.copy(relatesTo = source.relatesTo + targetId))
            refresh()
            statusMessage = "Linked"
        }
    }

    /** Refuses edges that would close a dependency cycle, and says so rather than failing quietly. */
    fun linkDependsOn(sourceId: NodeId, targetId: NodeId) {
        scope.launch {
            val source = nodeById(sourceId) ?: return@launch
            if (targetId in source.dependsOn) return@launch
            if (graph.wouldCycle(sourceId, targetId)) {
                statusMessage = "That would create a dependency cycle"
                return@launch
            }
            store.save(source.copy(dependsOn = source.dependsOn + targetId))
            refresh()
            statusMessage = "Dependency added"
        }
    }

    fun unlink(sourceId: NodeId, targetId: NodeId) {
        scope.launch {
            val source = nodeById(sourceId) ?: return@launch
            store.save(
                source.copy(
                    dependsOn = source.dependsOn - targetId,
                    relatesTo = source.relatesTo - targetId,
                ),
            )
            refresh()
            statusMessage = "Link removed"
        }
    }

    // ---- time tracking ----

    fun trackedSecondsFor(nodeId: NodeId): Long {
        val logged = sessions.filter { it.nodeId == nodeId }.sumOf { it.seconds }
        val since = runningSinceUnix
        val live = if (runningNodeId == nodeId && since != null) {
            (liveNowUnix - since).coerceAtLeast(0)
        } else {
            0
        }
        return logged + live
    }

    fun isTracking(nodeId: NodeId): Boolean = runningNodeId == nodeId

    fun startWork(nodeId: NodeId) {
        scope.launch {
            runningNodeId?.takeIf { it != nodeId }?.let { stopWorkNow(it) }
            runningNodeId = nodeId
            runningSinceUnix = currentUnixTimestamp()
            liveNowUnix = currentUnixTimestamp()
            startTicker()
            nodeById(nodeId)?.takeIf { it.task?.status == TaskStatus.Todo }?.let {
                setStatus(nodeId, TaskStatus.Doing)
            }
            statusMessage = "Tracking"
        }
    }

    fun stopWork(nodeId: NodeId) {
        scope.launch {
            stopWorkNow(nodeId)
            statusMessage = "Stopped"
        }
    }

    private suspend fun stopWorkNow(nodeId: NodeId) {
        val since = runningSinceUnix
        if (runningNodeId != nodeId || since == null) return
        val now = currentUnixTimestamp()
        val elapsed = (now - since).coerceAtLeast(0)
        runningNodeId = null
        runningSinceUnix = null
        stopTicker()
        if (elapsed > 0) {
            sessionLog.append(WorkSession(nodeId, since, now, elapsed))
            sessions = sessionLog.load()
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                liveNowUnix = currentUnixTimestamp()
                delay(1000)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
