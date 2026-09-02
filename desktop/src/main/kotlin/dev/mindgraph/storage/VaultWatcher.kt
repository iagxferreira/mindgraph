package dev.mindgraph.storage

import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Watches the vault's `nodes/` directory and emits once per settled burst of changes.
 *
 * The MCP server made MindGraph a two-writer system: an agent in another process writes the
 * same markdown the window is drawing. Without this, the app shows whatever the vault held
 * when it last loaded, and the graph quietly drifts away from the disk that owns it.
 *
 * Emissions carry no payload on purpose. Markdown is the source of truth and nothing caches
 * across calls, so the only honest response to "something changed" is to read the vault again
 * — a diff computed here would be a second, staler answer to a question [NodeStore] already
 * answers correctly.
 */
class VaultWatcher(
    private val vault: Vault,
    /**
     * How long the directory must be quiet before a burst counts as finished. One save is
     * several filesystem events, and a rename is a delete plus a create — without a settle
     * window a single edit would reload the vault three or four times.
     */
    private val settleMillis: Long = DEFAULT_SETTLE_MILLIS,
) {

    fun changes(): Flow<Unit> = flow {
        vault.prepare()

        val service = FileSystems.getDefault().newWatchService()
        try {
            vault.nodesDir.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)

            while (currentCoroutineContext().isActive) {
                // Polled rather than blocked on take(), so cancelling the scope that collects
                // this flow actually stops the loop instead of stranding a thread in the JVM.
                val first = service.poll(POLL_MILLIS, TimeUnit.MILLISECONDS) ?: continue

                var relevant = drain(first)
                while (true) {
                    val next = service.poll(settleMillis, TimeUnit.MILLISECONDS) ?: break
                    relevant = drain(next) || relevant
                }

                // A `.mindgraph/` log write or an editor's swap file is not a node changing.
                if (relevant) emit(Unit)
            }
        } catch (_: ClosedWatchServiceException) {
            // The service was closed under us; there is nothing left to watch and nothing wrong.
        } finally {
            service.close()
        }
    }.flowOn(Dispatchers.IO)

    /** Consumes a key's events and reports whether any of them touched a node file. */
    private fun drain(key: WatchKey): Boolean {
        val touchedNode = key.pollEvents().any { event ->
            // Overflow means the queue dropped events, so what changed is unknown — reload
            // rather than assume nothing did.
            event.kind() == OVERFLOW || event.context()?.toString()?.endsWith(".md") == true
        }
        key.reset()
        return touchedNode
    }

    private companion object {
        const val DEFAULT_SETTLE_MILLIS = 150L
        const val POLL_MILLIS = 250L
    }
}
