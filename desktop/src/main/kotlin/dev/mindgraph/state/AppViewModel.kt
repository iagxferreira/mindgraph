package dev.mindgraph.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.mindgraph.model.Edge
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.model.WorkSession
import dev.mindgraph.model.Worker
import dev.mindgraph.model.currentUnixTimestamp
import dev.mindgraph.storage.MemoryImport
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.PlanImport
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.VaultWatcher
import dev.mindgraph.storage.WikiLinks
import dev.mindgraph.storage.toEdges
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The single source of UI state. Reads through [NodeStore], which reads the vault — so the
 * markdown on disk stays authoritative and anything edited outside the app shows up on reload.
 */
class AppViewModel(
    private val store: NodeStore,
    private val sessionLog: SessionLog,
    /**
     * Optional: when present, changes written to the vault by anything other than this window
     * — an agent over MCP, an editor, a git checkout — reload the graph on their own. Absent in
     * tests that drive the store directly and have nothing to watch for.
     */
    private val watcher: VaultWatcher? = null,
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

        // The vault, not this class, is the source of truth — so an outside write is not a
        // special case to merge, it is just the same load the app already does on startup.
        watcher?.let { source ->
            scope.launch { source.changes().collect { refresh() } }
        }
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
        assignee: String? = null,
        kind: NodeKind = NodeKind.Note,
    ): Node {
        val facet = if (asTask) TaskFacet(status = TaskStatus.Todo, due = due) else null
        val created = store.create(
            title.trim().ifBlank { "Untitled" },
            body,
            facet,
            kind = kind,
            assignee = assignee?.trim()?.takeIf { it.isNotEmpty() },
        )
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

    /**
     * Adds to the end of a node's body and changes nothing else.
     *
     * Agents may not rewrite a node — that rule exists so a human's words cannot be silently
     * replaced — but a running record of one piece of work belongs on that work, not scattered
     * across new nodes. Appending is the narrow carve-out: it cannot destroy what is already
     * there, so it is safe in a way that saving a whole body is not.
     *
     * Entries are separated by a blank line rather than a `---` rule, which markdown would
     * read as a heading underline for the line above it.
     */
    suspend fun appendToBodyNow(nodeId: NodeId, content: String): Node? {
        val addition = content.trim()
        if (addition.isEmpty()) return null
        val node = nodeById(nodeId) ?: return null

        val existing = node.body.trimEnd()
        val saved = store.save(
            // A data-class copy of body alone: title, kind, task, due, assignee and archived
            // are carried over by construction rather than by remembering to preserve them.
            node.copy(body = if (existing.isEmpty()) addition else "$existing\n\n$addition"),
        )
        refresh()
        statusMessage = "Appended"
        return saved
    }

    /**
     * Copies Claude Code's per-project memory notes into the vault, skipping any file already
     * imported. Read-only upstream: `~/.claude` is never written back to.
     *
     * Re-runnable on purpose. Claude keeps writing memory files, so this is not the one-shot it
     * first looked like; a second run brings in what is new and leaves everything else alone.
     */
    suspend fun importClaudeMemoryNow(projectsRoot: Path = defaultClaudeProjectsRoot()): MemoryImportResult {
        val files = MemoryImport.scan(projectsRoot)
        // Origin is the source path, so a file that moved imports again rather than never.
        // That is the right trade: a duplicate is visible and fixable, a silent gap is not.
        val alreadyImported = store.frontmatterValues(MemoryImport.KEY_ORIGIN)

        var imported = 0
        var skipped = 0
        var unreadable = 0
        for (file in files) {
            if (file.toAbsolutePath().toString() in alreadyImported) {
                skipped++
                continue
            }
            val note = MemoryImport.read(file)
            if (note == null) {
                unreadable++
                continue
            }
            // The memory name is written as an extra and read back as an alias, so
            // `[[that-name]]` resolves here with no second write to make it so.
            store.create(
                title = note.title,
                body = note.body,
                task = null,
                kind = note.kind,
                extras = MemoryImport.extrasFor(note),
            )
            imported++
        }

        refresh()
        val result = MemoryImportResult(imported, skipped, unreadable, files.size)
        statusMessage = result.summary()
        return result
    }

    /**
     * Copies Claude Code's plan documents in as RFC nodes, skipping any file already imported.
     * Read-only upstream, like the memory import.
     *
     * A plan whose title names exactly one project the vault already knows is linked to that
     * project's notes; the rest land unlinked rather than guessed at. See [PlanImport.subjectOf].
     */
    suspend fun importClaudePlansNow(plansRoot: Path = defaultClaudePlansRoot()): PlanImportResult {
        val files = PlanImport.scan(plansRoot)
        val alreadyImported = store.frontmatterValues(PlanImport.KEY_ORIGIN)
        // Only projects already in the vault can be linked to, which is why the memory import
        // runs first: it is what makes a project known at all.
        val knownProjects = store.frontmatterValues(MemoryImport.KEY_ORIGIN_PROJECT)

        var imported = 0
        var skipped = 0
        val toLink = mutableListOf<Pair<Node, String>>()
        for (file in files) {
            if (file.toAbsolutePath().toString() in alreadyImported) {
                skipped++
                continue
            }
            val plan = PlanImport.read(file, knownProjects) ?: continue
            val created = store.create(
                title = plan.title,
                body = plan.body,
                task = null,
                kind = NodeKind.Rfc,
                extras = PlanImport.extrasFor(plan),
            )
            imported++
            plan.subject?.let { toLink += created to it }
        }

        // Linking is a second pass because linkNow evaluates against the in-memory snapshot,
        // and a node created a moment ago is not in it yet — one reload, then every edge.
        refresh()
        var linked = 0
        for ((plan, subject) in toLink) {
            if (linkToProject(plan, subject)) linked++
        }

        refresh()
        val result = PlanImportResult(imported, skipped, linked, files.size)
        statusMessage = result.summary()
        return result
    }

    /**
     * Links a plan to what the vault already holds about its project. Relates-to rather than
     * depends-on: a design document and the notes about its project inform each other, and
     * neither blocks the other.
     */
    private suspend fun linkToProject(plan: Node, originProject: String): Boolean {
        val about = store.nodesWith(MemoryImport.KEY_ORIGIN_PROJECT, originProject)
            .filter { it.id != plan.id }
        if (about.isEmpty()) return false
        about.forEach { target -> linkNow(plan.id, target.id, EdgeKind.RelatesTo) }
        return true
    }

    /** Runs both imports, memory first — plans can only link to projects memory has introduced. */
    fun importClaudeContext() {
        scope.launch {
            val memory = importClaudeMemoryNow()
            val plans = importClaudePlansNow()
            statusMessage = listOf(memory.summary(), plans.summary()).joinToString(" · ")
        }
    }

    fun importClaudeMemory() {
        scope.launch { importClaudeMemoryNow() }
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
        assignee: String? = null,
    ): Node? {
        val node = nodeById(nodeId) ?: return null
        val facet = node.task ?: TaskFacet(status = status)
        val saved = store.save(
            node.copy(
                // Like the due date: only replaced when one is given, so closing a task does
                // not quietly unassign whoever it belonged to.
                assignee = assignee?.trim()?.takeIf { it.isNotEmpty() } ?: node.assignee,
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
     * Puts a node away, or brings it back. Nothing is deleted: the file keeps its id, its
     * links and its tracked time, and stops appearing in work that is still live.
     */
    fun setArchived(nodeId: NodeId, archived: Boolean) {
        scope.launch {
            val node = nodeById(nodeId) ?: return@launch
            if (node.archived == archived) return@launch
            if (archived && runningNodeId == nodeId) stopWorkNow(nodeId)
            store.save(node.copy(archived = archived))
            refresh()
            statusMessage = if (archived) "Archived" else "Restored"
        }
    }

    /**
     * Hands a node to someone, or takes it back. Assignment is a plan, not a record of work —
     * it never changes whether a task is ready, only whose it is.
     */
    fun setAssignee(nodeId: NodeId, assignee: String?) {
        scope.launch {
            val node = nodeById(nodeId) ?: return@launch
            val cleaned = assignee?.trim()?.takeIf { it.isNotEmpty() }
            if (node.assignee == cleaned) return@launch
            store.save(node.copy(assignee = cleaned))
            refresh()
            statusMessage = cleaned?.let { "Assigned to $it" } ?: "Unassigned"
        }
    }

    /** Changes what a document is. Task-ness is separate, so this never touches the facet. */
    fun setKind(nodeId: NodeId, kind: NodeKind) {
        scope.launch {
            val node = nodeById(nodeId) ?: return@launch
            if (node.kind == kind) return@launch
            store.save(node.copy(kind = kind))
            refresh()
            statusMessage = "Now a ${kind.name.lowercase()}"
        }
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

    /**
     * The stretch currently on the clock, as a session that has not been written yet — so a
     * running timer shows up in totals without the log having to be touched every second.
     */
    val liveSession: WorkSession?
        get() {
            val nodeId = runningNodeId ?: return null
            val since = runningSinceUnix ?: return null
            val elapsed = (liveNowUnix - since).coerceAtLeast(0)
            if (elapsed <= 0) return null
            return WorkSession(nodeId, since, liveNowUnix, elapsed, runningWorker, runningAgent)
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

/** What one run of the memory import did, in the terms a person would ask about. */
data class MemoryImportResult(
    val imported: Int,
    val skipped: Int,
    val unreadable: Int,
    val filesFound: Int,
) {
    fun summary(): String = when {
        filesFound == 0 -> "No Claude memory files found"
        imported == 0 && skipped > 0 -> "Already imported ($skipped notes)"
        imported == 0 -> "Nothing to import"
        skipped > 0 -> "Imported $imported note(s), $skipped already there"
        else -> "Imported $imported note(s)"
    }
}

/** `~/.claude/projects`, where Claude Code keeps a memory directory per project. */
fun defaultClaudeProjectsRoot(): Path =
    Paths.get(System.getProperty("user.home") ?: ".", ".claude", "projects")

/** What one run of the plan import did. */
data class PlanImportResult(
    val imported: Int,
    val skipped: Int,
    val linked: Int,
    val filesFound: Int,
) {
    fun summary(): String = when {
        filesFound == 0 -> "No Claude plans found"
        imported == 0 && skipped > 0 -> "Plans already imported ($skipped)"
        imported == 0 -> "No plans to import"
        // Saying how many linked matters: a plan whose title names no known project lands
        // unlinked on purpose, and that should be visible rather than look like a failure.
        else -> "Imported $imported plan(s) as RFCs, $linked linked to a project"
    }
}

/** `~/.claude/plans`, where Claude Code keeps its design documents. */
fun defaultClaudePlansRoot(): Path =
    Paths.get(System.getProperty("user.home") ?: ".", ".claude", "plans")
