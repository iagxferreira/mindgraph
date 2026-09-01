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
    val dependsOn: List<NodeId> = emptyList(),
    val relatesTo: List<NodeId> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    /** Where this node's markdown lives. Derived from the title; never an identity. */
    val slug: String,
) {
    val isTask: Boolean get() = task != null
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

/** One tracked stretch of work against a node. Appended to a log; never edited in place. */
data class WorkSession(
    val nodeId: NodeId,
    val startedAtUnix: Long,
    val stoppedAtUnix: Long,
    val seconds: Long,
)

fun currentUnixTimestamp(): Long = System.currentTimeMillis() / 1000
