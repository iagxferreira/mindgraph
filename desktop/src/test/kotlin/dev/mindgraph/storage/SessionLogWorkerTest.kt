package dev.mindgraph.storage

import dev.mindgraph.model.NodeId
import dev.mindgraph.model.WorkSession
import dev.mindgraph.model.Worker
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Telling your work from a machine's, in a log that predates the distinction. */
class SessionLogWorkerTest {

    private fun newVault(): Vault = Vault(Files.createTempDirectory("mindgraph-worker"))

    private val node = NodeId("01M0V4BQMAJ000RTB5PNFK2P5N")

    @Test
    fun anAgentsNameSurvivesTheRoundTrip() = runTest {
        val log = SessionLog(newVault())
        log.append(WorkSession(node, 100, 400, 300, Worker.Agent, "claude-code"))

        val loaded = log.load().single()
        assertEquals(Worker.Agent, loaded.worker)
        assertEquals("claude-code", loaded.agent)
    }

    @Test
    fun yourOwnWorkCarriesNoAgentName() = runTest {
        val log = SessionLog(newVault())
        log.append(WorkSession(node, 100, 400, 300))

        val loaded = log.load().single()
        assertEquals(Worker.Human, loaded.worker)
        assertNull(loaded.agent)
    }

    @Test
    fun linesWrittenBeforeWorkersExistedReadAsYourWork() = runTest {
        val vault = newVault()
        vault.prepare()
        Files.writeString(
            vault.internalDir.resolve("sessions.jsonl"),
            """{"node_id":"${node.value}","started_at":100,"stopped_at":400,"seconds":300}""" + "\n",
        )

        val loaded = SessionLog(vault).load().single()
        assertEquals(Worker.Human, loaded.worker)
        assertEquals(300, loaded.seconds)
    }

    @Test
    fun anAgentNameWithQuotesCannotBreakTheLine() = runTest {
        val log = SessionLog(newVault())
        // The name is whatever an agent calls itself, so it is arbitrary text.
        log.append(WorkSession(node, 100, 400, 300, Worker.Agent, """we"ird\name"""))

        val loaded = log.load()
        assertEquals(1, loaded.size)
        assertEquals(300, loaded.single().seconds)
        assertEquals(Worker.Agent, loaded.single().worker)
    }

    @Test
    fun bothWorkersAccumulateAgainstTheSameNode() = runTest {
        val log = SessionLog(newVault())
        log.append(WorkSession(node, 0, 600, 600, Worker.Human))
        log.append(WorkSession(node, 600, 3000, 2400, Worker.Agent, "claude-code"))

        val byWorker = log.load().groupBy { it.worker }.mapValues { (_, v) -> v.sumOf { it.seconds } }
        assertEquals(600, byWorker[Worker.Human])
        assertEquals(2400, byWorker[Worker.Agent])
    }
}
