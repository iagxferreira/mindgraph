package dev.mindgraph.storage

import dev.mindgraph.model.NodeId
import dev.mindgraph.model.WorkSession
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tracked time as an append-only JSONL log, one line per completed stretch of work.
 *
 * Kept out of node frontmatter on purpose: time is high-churn, append-shaped data, and folding
 * it into the markdown would rewrite a note every time a timer stopped. Totals are summed on
 * load, so the log stays the only place a duration is recorded.
 */
class SessionLog(private val vault: Vault) {

    private val file get() = vault.internalDir.resolve("sessions.jsonl")

    suspend fun load(): List<WorkSession> = withContext(Dispatchers.IO) {
        vault.prepare()
        if (!Files.exists(file)) return@withContext emptyList()
        Files.readAllLines(file).mapNotNull(::parseLine)
    }

    suspend fun append(session: WorkSession) = withContext(Dispatchers.IO) {
        vault.prepare()
        Files.writeString(
            file,
            render(session) + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
        Unit
    }

    /** Rewrites the log without the given node's entries; used when a node is deleted. */
    suspend fun purge(nodeId: NodeId) = withContext(Dispatchers.IO) {
        if (!Files.exists(file)) return@withContext
        val kept = Files.readAllLines(file)
            .mapNotNull(::parseLine)
            .filter { it.nodeId != nodeId }
        Files.writeString(file, kept.joinToString("") { render(it) + "\n" })
        Unit
    }

    private fun render(session: WorkSession): String = buildString {
        append("{")
        append("\"node_id\":\"").append(session.nodeId.value).append("\",")
        append("\"started_at\":").append(session.startedAtUnix).append(",")
        append("\"stopped_at\":").append(session.stoppedAtUnix).append(",")
        append("\"seconds\":").append(session.seconds)
        append("}")
    }

    /**
     * A hand-rolled reader for a shape this module also writes. Malformed lines are skipped
     * rather than fatal — a truncated final line from a hard kill shouldn't cost you the log.
     */
    private fun parseLine(line: String): WorkSession? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        val nodeId = stringField(trimmed, "node_id") ?: return null
        return WorkSession(
            nodeId = NodeId(nodeId),
            startedAtUnix = longField(trimmed, "started_at") ?: return null,
            stoppedAtUnix = longField(trimmed, "stopped_at") ?: return null,
            seconds = longField(trimmed, "seconds") ?: return null,
        )
    }

    private fun stringField(line: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(line)?.groupValues?.get(1)

    private fun longField(line: String, key: String): Long? =
        Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
}
