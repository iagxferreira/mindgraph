package dev.mindgraph.model

/**
 * The single entity in the graph. A node is always a markdown document; it is *also* a task
 * when [task] is non-null. That optionality is the whole model: a stray thought and a piece
 * of tracked work are the same kind of thing, so nothing has to be kept in sync between them.
 */
data class Node(
    val id: NodeId,
    val title: String,
    val body: String,
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

/** A ULID. Opaque and stable — the filename may change freely, this may not. */
@JvmInline
value class NodeId(val value: String) {
    override fun toString(): String = value
}

data class TaskFacet(
    val status: TaskStatus,
    val due: String? = null,
    val completedAt: String? = null,
)

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
