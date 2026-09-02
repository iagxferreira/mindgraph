package dev.mindgraph.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.mindgraph.model.Edge
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.model.WorkSession
import dev.mindgraph.model.Worker
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
    var runningWorker by mutableStateOf(Worker.Human)
        private set
    var runningAgent by mutableStateOf<String?>(null)
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
    suspend fun createNodeNow(
        title: String,
        body: String = "",
        asTask: Boolean = false,
        due: String? = null,
    ): Node {
        val facet = if (asTask) TaskFacet(status = TaskStatus.Todo, due = due) else null
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
        scope.launch { setStatusNow(nodeId, status) }
    }

    /**
     * Sets a status and hands back the node as saved. A note given a status becomes a task —
     * the facet is the only thing that distinguishes them, so there is nothing else to do.
     */
    suspend fun setStatusNow(
        nodeId: NodeId,
        status: TaskStatus,
        due: String? = null,
        worker: Worker = Worker.Human,
        agent: String? = null,
    ): Node? {
        val node = nodeById(nodeId) ?: return null
        val facet = node.task ?: TaskFacet(status = status)
        val saved = store.save(
            node.copy(
                task = facet.copy(
                    status = status,
                    // A due date is only replaced when one is given: closing a task must not
                    // quietly erase the deadline it was closed against.
                    due = due ?: facet.due,
                    completedAt = if (status == TaskStatus.Done) Instant.now().toString() else null,
                ),
            ),
        )
        refresh()

        // The status *is* the timer. Moving a task to doing starts the clock and closing it
        // stops it, so elapsed time is a consequence of the work being tracked rather than a
        // second thing to remember — which is the only way an agent's time gets recorded at all.
        if (status == TaskStatus.Doing) {
            beginTracking(nodeId, worker, agent)
        } else if (runningNodeId == nodeId) {
            stopWorkNow(nodeId)
        }

        statusMessage = "Marked ${status.name.lowercase()}"
        return saved
    }

    /**
     * Sets or clears a deadline. Null actually clears it — unlike [setStatusNow], where an
     * omitted due means "leave it alone" — so the editor has a way to take a date back off.
     */
    fun setDue(nodeId: NodeId, due: String?) {
        scope.launch {
            val node = nodeById(nodeId) ?: return@launch
            val facet = node.task ?: return@launch
            store.save(node.copy(task = facet.copy(due = due)))
            refresh()
            statusMessage = if (due == null) "Due date cleared" else "Due $due"
        }
    }

    // ---- edges ----

    fun linkRelates(sourceId: NodeId, targetId: NodeId) {
        scope.launch { linkNow(sourceId, targetId, EdgeKind.RelatesTo) }
    }

    /** Refuses edges that would close a dependency cycle, and says so rather than failing quietly. */
    fun linkDependsOn(sourceId: NodeId, targetId: NodeId) {
        scope.launch { linkNow(sourceId, targetId, EdgeKind.DependsOn) }
    }

    /**
     * Adds an edge and reports what happened. The decision lives in [Linking] so an agent
     * calling in over MCP cannot build a graph this app would have refused.
     */
    suspend fun linkNow(sourceId: NodeId, targetId: NodeId, kind: EdgeKind): LinkOutcome {
        val outcome = Linking.evaluate(nodes, sourceId, targetId, kind)
        if (outcome == LinkOutcome.Linked) {
            val source = nodeById(sourceId) ?: return LinkOutcome.UnknownNode
            store.save(Linking.applied(source, targetId, kind))
            refresh()
        }
        statusMessage = when (outcome) {
            LinkOutcome.Linked ->
                if (kind == EdgeKind.DependsOn) "Dependency added" else "Linked"
            LinkOutcome.AlreadyLinked -> "Already linked"
            LinkOutcome.WouldCycle -> "That would create a dependency cycle"
            LinkOutcome.SelfLink -> "A node cannot link to itself"
            LinkOutcome.UnknownNode -> "That node no longer exists"
        }
        return outcome
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

    /** Tracked seconds on a node by one worker, including the stretch running right now. */
    fun trackedSecondsFor(nodeId: NodeId, worker: Worker): Long {
        val logged = sessions.filter { it.nodeId == nodeId && it.worker == worker }.sumOf { it.seconds }
        val since = runningSinceUnix
        val live = if (runningNodeId == nodeId && runningWorker == worker && since != null) {
            (liveNowUnix - since).coerceAtLeast(0)
        } else {
            0
        }
        return logged + live
    }

    fun isTracking(nodeId: NodeId): Boolean = runningNodeId == nodeId

    fun startWork(nodeId: NodeId) {
        scope.launch {
            val wasTodo = nodeById(nodeId)?.task?.status == TaskStatus.Todo
            if (wasTodo) {
                // setStatusNow starts the clock itself, so this is one call, not two.
                setStatusNow(nodeId, TaskStatus.Doing)
            } else {
                beginTracking(nodeId, Worker.Human, agent = null)
            }
            statusMessage = "Tracking"
        }
    }

    /**
     * Puts a node on the clock. Idempotent for the node already running, so a status change
     * that repeats itself doesn't restart the stretch and lose the time before it.
     */
    private suspend fun beginTracking(nodeId: NodeId, worker: Worker, agent: String? = null) {
        if (runningNodeId == nodeId) return
        runningNodeId?.let { stopWorkNow(it) }
        runningNodeId = nodeId
        runningWorker = worker
        runningAgent = agent
        runningSinceUnix = currentUnixTimestamp()
        liveNowUnix = currentUnixTimestamp()
        startTicker()
    }

    fun stopWork(nodeId: NodeId) {
        scope.launch {
            stopWorkNow(nodeId)
            statusMessage = "Stopped"
        }
    }

    /** Closes the running stretch and returns how long it was, or 0 if nothing was running. */
    private suspend fun stopWorkNow(nodeId: NodeId): Long {
        val since = runningSinceUnix
        if (runningNodeId != nodeId || since == null) return 0
        val now = currentUnixTimestamp()
        val elapsed = (now - since).coerceAtLeast(0)
        val worker = runningWorker
        val agent = runningAgent
        runningNodeId = null
        runningAgent = null
        runningSinceUnix = null
        stopTicker()
        if (elapsed > 0) {
            sessionLog.append(WorkSession(nodeId, since, now, elapsed, worker, agent))
            sessions = sessionLog.load()
        }
        return elapsed
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
