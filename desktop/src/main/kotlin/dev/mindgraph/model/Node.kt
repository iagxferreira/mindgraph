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
     * Put away, but kept. Archiving is about visibility, not outcome — which is why it is not
     * a fifth [TaskStatus]: archiving a finished task must not overwrite the fact that it was
     * finished rather than abandoned.
     */
    val archived: Boolean = false,
    val dependsOn: List<NodeId> = emptyList(),
    val relatesTo: List<NodeId> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    /** Where this node's markdown lives. Derived from the title; never an identity. */
    val slug: String,
) {
    val isTask: Boolean get() = task != null

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

enum class EdgeKind { RelatesTo, DependsOn }

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
