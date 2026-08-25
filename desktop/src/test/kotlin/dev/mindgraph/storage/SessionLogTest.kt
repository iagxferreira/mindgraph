package dev.mindgraph.storage

import dev.mindgraph.model.NodeId
import dev.mindgraph.model.WorkSession
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionLogTest {
    @Test
    fun appendedSessionsSurviveAReload() = runTest {
        val log = SessionLog(Vault(Files.createTempDirectory("mindgraph-sessions")))
        log.append(WorkSession(NodeId("A"), 100, 160, 60))
        log.append(WorkSession(NodeId("B"), 200, 290, 90))

        val loaded = log.load()
        assertEquals(2, loaded.size)
        assertEquals(60, loaded.first { it.nodeId == NodeId("A") }.seconds)
        assertEquals(90, loaded.first { it.nodeId == NodeId("B") }.seconds)
    }

    @Test
    fun purgeRemovesOnlyTheGivenNode() = runTest {
        val log = SessionLog(Vault(Files.createTempDirectory("mindgraph-purge")))
        log.append(WorkSession(NodeId("keep"), 1, 2, 1))
        log.append(WorkSession(NodeId("drop"), 3, 4, 1))

        log.purge(NodeId("drop"))

        assertEquals(listOf(NodeId("keep")), log.load().map { it.nodeId })
    }

    @Test
    fun aTruncatedFinalLineDoesNotCostTheWholeLog() = runTest {
        val vault = Vault(Files.createTempDirectory("mindgraph-truncated"))
        val log = SessionLog(vault)
        log.append(WorkSession(NodeId("good"), 1, 61, 60))

        val file = vault.internalDir.resolve("sessions.jsonl")
        Files.writeString(file, Files.readString(file) + "{\"node_id\":\"trunc")

        assertEquals(listOf(NodeId("good")), log.load().map { it.nodeId })
    }
}
