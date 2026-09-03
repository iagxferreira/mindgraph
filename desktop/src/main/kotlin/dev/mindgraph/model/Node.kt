package dev.mindgraph.model

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * The single entity in the graph. A node is always a markdown document; it is *also* a task
 * when [task] is non-null. That optionality is the whole model: a stray thought and a piece
 * of tracked work are the same kind of thing, so nothing has to be kept in sync between them.
 */
data class Node(
    val id: NodeId,
    val title: String,
    val body: String,
    val kind: NodeKind = NodeKind.Note,
    val task: TaskFacet? = null,
    /**
     * Who is meant to pick this up — a person or a named agent. Deliberately not the same as
     * [WorkSession.worker], which records who actually spent time: one is a plan, the other is
     * a fact, and a node can easily be assigned to one and worked by another.
     *
     * Free text, matching the names agents already give themselves in the session log. Absent
     * rather than empty when nobody owns it, because most nodes have no owner.
     */
    val assignee: String? = null,
    /**
     * Other names this node answers to in `[[links]]`. A node already has two — its title and
     * its slug — and this is the third: a name given from outside that neither derives from.
     *
     * Imported notes need it. Claude's memory files link each other by a `name:` slug while
     * their titles are full sentences, so without a name that survives the import, twenty-five
     * links in the vault resolve to nothing.
     */
    val aliases: List<String> = emptyList(),
    /**
     * The repository a node was imported from, when it came from one. Absent for everything
     * written here, which is the distinction the cluster layout draws: work you did in this
     * vault, versus context carried in from somewhere else.
     */
    val originProject: String? = null,
    /**
     * The absolute path a node was imported from, when it was imported.
     *
     * Modelled rather than left as preserved frontmatter because resolution needs it: a vault
     * that links by path — `[[estudos/elixir/roadmap]]` — is naming a file, and the only record
     * of which file that is lives here. Absent for everything written in the app.
     */
    val origin: String? = null,
    /**
     * Present when this node is also a saved selection over the vault.
     *
     * A workspace is a node rather than a settings file: it versions with the vault, travels
     * with it, can be linked and described in its own body, and needs no storage layer that
     * does not already exist. Not a fourth [NodeKind] — kind says what a *document* is and is
     * drawn as shape on the canvas, and a workspace is not a document.
     */
    val workspace: Workspace? = null,
    /**
     * Put away, but kept. Archiving is about visibility, not outcome — which is why it is not
     * a fifth [TaskStatus]: archiving a finished task must not overwrite the fact that it was
     * finished rather than abandoned.
     */
    val archived: Boolean = false,
    val dependsOn: List<NodeId> = emptyList(),
    val relatesTo: List<NodeId> = emptyList(),
    /**
     * Nodes this one is context for — the briefing you would hand an agent starting on them.
     *
     * Deliberately not [relatesTo]: "these two ideas are related" is not "load this before
     * working on that". Reusing association would put every incidental link into the bundle,
     * which is exactly what makes a context window fill with things nobody chose.
     *
     * Held on the node that *is* the context rather than on the thing it serves, like every
     * other edge here, so a note copied out of the vault still says what it was for — and so
     * one note can brief several projects without being duplicated.
     */
    val contextFor: List<NodeId> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    /** Where this node's markdown lives. Derived from the title; never an identity. */
    val slug: String,
) {
    val isTask: Boolean get() = task != null

    /** The nodes this one points at with [kind]. */
    fun links(kind: EdgeKind): List<NodeId> = when (kind) {
        EdgeKind.DependsOn -> dependsOn
        EdgeKind.RelatesTo -> relatesTo
        EdgeKind.ContextFor -> contextFor
    }

    /**
     * This node with one more edge of [kind].
     *
     * Which field a kind lives in is the model's business, not the linking rules'. Keeping the
     * mapping here means adding a fourth kind is one branch in one place rather than a hunt
     * through every caller that happened to know the shape.
     */
    fun withLink(kind: EdgeKind, targetId: NodeId): Node = when (kind) {
        EdgeKind.DependsOn -> copy(dependsOn = dependsOn + targetId)
        EdgeKind.RelatesTo -> copy(relatesTo = relatesTo + targetId)
        EdgeKind.ContextFor -> copy(contextFor = contextFor + targetId)
    }

    /**
     * Work that is still live: open, and not put away. Archived work stops blocking whatever
     * depended on it — otherwise archiving one task would strand every task behind it.
     */
    val isLiveWork: Boolean get() = !archived && task?.status?.isOpen == true
}

/**
 * What a document *is*. Deliberately not a list that includes "task": whether a node is work
 * is [task], and keeping the two axes separate is what lets an RFC also be a task you are
 * tracking, rather than forcing a choice between describing it and scheduling it.
 */
enum class NodeKind {
    Note, Rfc, Reference;

    /** How the kind appears in frontmatter. */
    val slug: String get() = name.lowercase()

    companion object {
        fun parse(raw: String?): NodeKind? =
            entries.find { it.slug.equals(raw?.trim(), ignoreCase = true) }
    }
}

/** A ULID. Opaque and stable — the filename may change freely, this may not. */
@JvmInline
value class NodeId(val value: String) {
    override fun toString(): String = value
}

data class TaskFacet(
    val status: TaskStatus,
    val due: String? = null,
    val completedAt: String? = null,
) {
    /**
     * The deadline as a date, or null if there isn't one that parses. It is stored as text
     * because frontmatter is text and a person may type anything there; a date nobody can read
     * is treated as no deadline rather than as an error, so a typo costs ordering, not the task.
     *
     * Accepts a plain `2026-09-04` and the timestamps the app writes elsewhere.
     */
    val dueDate: LocalDate?
        get() {
            val raw = due?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return runCatching { LocalDate.parse(raw) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(raw).toLocalDate() }.getOrNull()
                ?: runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .getOrNull()
        }
}

enum class TaskStatus {
    Todo, Doing, Done, Dropped;

    val isOpen: Boolean get() = this == Todo || this == Doing

    companion object {
        fun parse(raw: String?): TaskStatus? =
            entries.find { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class EdgeKind { RelatesTo, DependsOn, ContextFor }

/**
 * Edges are not stored. They are projected from the frontmatter of each node so that a file
 * remains the complete description of itself — copy one out of the vault and it still carries
 * its own links.
 */
data class Edge(
    val sourceId: NodeId,
    val targetId: NodeId,
    val kind: EdgeKind,
)

/**
 * Who spent the time. Recorded per stretch rather than per node, because the same task is
 * usually worked by both — and a total that cannot separate them answers neither "how long did
 * this take me" nor "how much of this did the machine do".
 */
enum class Worker {
    Human, Agent;

    val slug: String get() = name.lowercase()

    companion object {
        /** Anything unrecognised, including a log written before this existed, is human work. */
        fun parse(raw: String?): Worker =
            entries.find { it.slug.equals(raw?.trim(), ignoreCase = true) } ?: Human
    }
}

/** One tracked stretch of work against a node. Appended to a log; never edited in place. */
data class WorkSession(
    val nodeId: NodeId,
    val startedAtUnix: Long,
    val stoppedAtUnix: Long,
    val seconds: Long,
    val worker: Worker = Worker.Human,
    /** Which agent, when [worker] is [Worker.Agent]. Null for your own work. */
    val agent: String? = null,
)

fun currentUnixTimestamp(): Long = System.currentTimeMillis() / 1000
