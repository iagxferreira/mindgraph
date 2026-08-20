package dev.mindgraph.storage

import dev.mindgraph.model.Vault
import dev.mindgraph.model.Workspace
import dev.mindgraph.model.currentUnixTimestamp
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class StorageNotFoundException(entity: String, id: Long) : Exception("$entity #$id not found")

/**
 * File-backed persistence rooted at the same directory the Rust app uses
 * (`$MINDGRAPH_HOME`, else `$HOME/.config/mindgraph`, else `./.mindgraph`), reading and
 * writing the exact same `data.json` / `config.json` shape so either app can be used
 * against the same store.
 */
class Database private constructor(val rootDir: Path) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    companion object {
        suspend fun openDefault(): Database = openAt(defaultRootDir())

        suspend fun openAt(rootDir: Path): Database {
            val database = Database(rootDir)
            database.initialize()
            return database
        }

        fun defaultRootDir(): Path {
            System.getenv("MINDGRAPH_HOME")?.let { return Paths.get(it) }
            System.getenv("HOME")?.let { return Paths.get(it, ".config", "mindgraph") }
            return Paths.get(".mindgraph")
        }
    }

    private fun configPath(): Path = rootDir.resolve("config.json")
    private fun dataPath(): Path = rootDir.resolve("data.json")

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        Files.createDirectories(rootDir)
        var config = loadConfigRaw()
        if (config.workspaceRoot.isBlank()) {
            config = StorageConfig(rootDir.resolve("workspaces").toString())
        }
        val workspaceRoot = Paths.get(config.workspaceRoot)
        Files.createDirectories(workspaceRoot)

        mutex.withLock {
            val data = loadDataRaw()
            if (data.workspaces.isEmpty()) {
                val defaultWorkspacePath = workspaceRoot.resolve("default")
                Files.createDirectories(defaultWorkspacePath)
                val id = data.allocateWorkspaceId()
                data.workspaces.add(Workspace(id, "default", defaultWorkspacePath.toString()))
            }
            if (data.vaults.isEmpty()) {
                val defaultVaultPath = rootDir.resolve("vaults").resolve("default")
                Files.createDirectories(defaultVaultPath)
                val id = data.allocateVaultId()
                data.vaults.add(Vault(id, "default", defaultVaultPath.toString(), currentUnixTimestamp()))
            }
            data.normalizeCounters()
            saveDataRaw(data)
        }
        saveConfigRaw(config)
    }

    /** Loads the store, lets [block] mutate it in place, then persists the result. */
    suspend fun <T> withData(block: (StorageData) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val data = loadDataRaw()
            val result = block(data)
            saveDataRaw(data)
            result
        }
    }

    suspend fun readData(): StorageData = withContext(Dispatchers.IO) {
        mutex.withLock { loadDataRaw() }
    }

    private fun loadConfigRaw(): StorageConfig {
        val path = configPath()
        if (!Files.exists(path)) return StorageConfig(rootDir.resolve("workspaces").toString())
        return json.decodeFromString(StorageConfig.serializer(), Files.readString(path))
    }

    private fun saveConfigRaw(config: StorageConfig) {
        Files.writeString(configPath(), json.encodeToString(StorageConfig.serializer(), config))
    }

    private fun loadDataRaw(): StorageData {
        val path = dataPath()
        if (!Files.exists(path)) return StorageData()
        val element = json.parseToJsonElement(Files.readString(path))
        val normalized = normalizeWorkItems(element.jsonObject)
        val data = json.decodeFromJsonElement(StorageData.serializer(), normalized)
        data.normalizeCounters()
        return data
    }

    private fun saveDataRaw(data: StorageData) {
        Files.writeString(dataPath(), json.encodeToString(StorageData.serializer(), data))
    }

    /**
     * The Rust model accepts `pomodoro_session_ids` as either a JSON array or a single
     * number, and also a legacy `pomodoro_session_id` alias. Normalize both into a plain
     * array under `pomodoro_session_ids` before decoding, since kotlinx.serialization's
     * per-property serializers can't see alternate keys on their own.
     */
    private fun normalizeWorkItems(root: JsonObject): JsonObject {
        val workItems = root["work_items"] as? JsonArray ?: return root
        val normalized = JsonArray(
            workItems.map { item ->
                val obj = item.jsonObject
                val raw = obj["pomodoro_session_ids"] ?: obj["pomodoro_session_id"]
                val ids: JsonElement = when (raw) {
                    null -> JsonArray(emptyList())
                    is JsonArray -> raw
                    else -> JsonArray(listOf(raw))
                }
                buildJsonObject {
                    obj.forEach { (key, value) ->
                        if (key != "pomodoro_session_ids" && key != "pomodoro_session_id") put(key, value)
                    }
                    put("pomodoro_session_ids", ids)
                }
            },
        )
        return buildJsonObject {
            root.forEach { (key, value) -> if (key != "work_items") put(key, value) }
            put("work_items", normalized)
        }
    }
}
