package dev.mindgraph.storage

import dev.mindgraph.model.Edge
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes the vault. Markdown files are the source of truth: every node is one
 * `.md` file that fully describes itself, and the in-memory graph is rebuilt by scanning them.
 * Nothing here caches across calls — at vault sizes a second brain actually reaches, a full
 * scan is cheaper than any invalidation scheme worth debugging.
 */
class NodeStore(private val vault: Vault) {

    suspend fun load(): List<Node> = withContext(Dispatchers.IO) {
        vault.prepare()
        Files.list(vault.nodesDir).use { stream ->
            stream.filter { it.toString().endsWith(".md") && Files.isRegularFile(it) }
                .toList()
        }
            .mapNotNull { readNode(it) }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun create(title: String, body: String = "", task: TaskFacet? = null): Node =
        withContext(Dispatchers.IO) {
            vault.prepare()
            val now = timestamp()
            val node = Node(
                id = NodeId(Ulid.generate()),
                title = title,
                body = body,
                task = task,
                createdAt = now,
                updatedAt = now,
                slug = allocateSlug(slugify(title), null),
            )
            writeNode(node)
            node
        }

    suspend fun save(node: Node): Node = withContext(Dispatchers.IO) {
        val existingPath = findPathById(node.id)
        val desiredSlug = slugify(node.title)
        val slug = if (existingPath != null && slugOf(existingPath) == desiredSlug) {
            desiredSlug
        } else {
            allocateSlug(desiredSlug, node.id)
        }

        val updated = node.copy(slug = slug, updatedAt = timestamp())
        writeNode(updated)
        if (existingPath != null && slugOf(existingPath) != slug) {
            Files.deleteIfExists(existingPath)
        }
        updated
    }

    suspend fun delete(nodeId: NodeId) = withContext(Dispatchers.IO) {
        findPathById(nodeId)?.let { Files.deleteIfExists(it) }
        // Drop dangling references so a deleted node can't leave edges pointing at nothing.
        load().filter { nodeId in it.dependsOn || nodeId in it.relatesTo }.forEach { referrer ->
            writeNode(
                referrer.copy(
                    dependsOn = referrer.dependsOn - nodeId,
                    relatesTo = referrer.relatesTo - nodeId,
                    updatedAt = timestamp(),
                ),
            )
        }
        Unit
    }

    // ---- reading ----

    private fun readNode(path: Path): Node? {
        val raw = runCatching { Files.readString(path) }.getOrNull() ?: return null
        val (frontmatter, body) = Frontmatter.split(raw)

        val id = frontmatter.string(KEY_ID)?.takeIf { Ulid.looksValid(it) } ?: return null
        val slug = slugOf(path)
        val status = TaskStatus.parse(frontmatter.string(KEY_STATUS))

        return Node(
            id = NodeId(id),
            title = frontmatter.string(KEY_TITLE) ?: slug.replace('-', ' '),
            body = body,
            task = status?.let {
                TaskFacet(
                    status = it,
                    due = frontmatter.string(KEY_DUE),
                    completedAt = frontmatter.string(KEY_COMPLETED),
                )
            },
            dependsOn = frontmatter.list(KEY_DEPENDS_ON).filter(Ulid::looksValid).map(::NodeId),
            relatesTo = frontmatter.list(KEY_RELATES_TO).filter(Ulid::looksValid).map(::NodeId),
            createdAt = frontmatter.string(KEY_CREATED) ?: timestamp(),
            updatedAt = frontmatter.string(KEY_UPDATED) ?: timestamp(),
            slug = slug,
        )
    }

    // ---- writing ----

    private fun writeNode(node: Node) {
        val path = vault.nodesDir.resolve("${node.slug}.md")
        val preserved = existingExtras(path)

        val lines = buildList {
            add("---")
            add("$KEY_ID: ${node.id.value}")
            add("$KEY_TITLE: ${Frontmatter.quote(node.title)}")
            node.task?.let { facet ->
                add("$KEY_STATUS: ${facet.status.name.lowercase()}")
                facet.due?.let { add("$KEY_DUE: ${Frontmatter.quote(it)}") }
                facet.completedAt?.let { add("$KEY_COMPLETED: ${Frontmatter.quote(it)}") }
            }
            if (node.dependsOn.isNotEmpty()) {
                add("$KEY_DEPENDS_ON: [${node.dependsOn.joinToString(", ") { it.value }}]")
            }
            if (node.relatesTo.isNotEmpty()) {
                add("$KEY_RELATES_TO: [${node.relatesTo.joinToString(", ") { it.value }}]")
            }
            add("$KEY_CREATED: ${node.createdAt}")
            add("$KEY_UPDATED: ${node.updatedAt}")
            preserved.forEach { (key, value) -> add("$key: ${Frontmatter.renderValue(value)}") }
            add("---")
        }

        val document = lines.joinToString("\n") + "\n\n" + node.body.trimStart('\n')
        writeAtomically(path, document)
    }

    private fun existingExtras(path: Path): Map<String, Frontmatter.Value> {
        if (!Files.exists(path)) return emptyMap()
        val raw = runCatching { Files.readString(path) }.getOrNull() ?: return emptyMap()
        return Frontmatter.split(raw).first.extras(KNOWN_KEYS)
    }

    /** Write to a sibling temp file then move, so a crash mid-write can't truncate a note. */
    private fun writeAtomically(path: Path, content: String) {
        path.parent?.let { Files.createDirectories(it) }
        val temp = Files.createTempFile(path.parent, ".${path.fileName}", ".tmp")
        Files.writeString(temp, content)
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
    }

    // ---- slugs ----

    private fun findPathById(nodeId: NodeId): Path? =
        Files.list(vault.nodesDir).use { stream ->
            stream.filter { it.toString().endsWith(".md") }.toList()
        }.firstOrNull { path ->
            val raw = runCatching { Files.readString(path) }.getOrNull() ?: return@firstOrNull false
            Frontmatter.split(raw).first.string(KEY_ID) == nodeId.value
        }

    /**
     * Slugs are cosmetic, so collisions get a numeric suffix rather than an error — the id in
     * frontmatter is what actually distinguishes two notes that share a title.
     */
    private fun allocateSlug(base: String, owner: NodeId?): String {
        val candidate = base.ifBlank { "untitled" }
        var attempt = candidate
        var counter = 2
        while (isSlugTaken(attempt, owner)) {
            attempt = "$candidate-$counter"
            counter++
        }
        return attempt
    }

    private fun isSlugTaken(slug: String, owner: NodeId?): Boolean {
        val path = vault.nodesDir.resolve("$slug.md")
        if (!Files.exists(path)) return false
        if (owner == null) return true
        val raw = runCatching { Files.readString(path) }.getOrNull() ?: return true
        return Frontmatter.split(raw).first.string(KEY_ID) != owner.value
    }

    private fun slugOf(path: Path): String = path.fileName.toString().removeSuffix(".md")

    private fun slugify(title: String): String =
        title.trim().lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .trim('-')
            .take(60)

    private fun timestamp(): String = Instant.now().toString()

    companion object {
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_STATUS = "status"
        const val KEY_DUE = "due"
        const val KEY_COMPLETED = "completed"
        const val KEY_DEPENDS_ON = "depends_on"
        const val KEY_RELATES_TO = "relates_to"
        const val KEY_CREATED = "created"
        const val KEY_UPDATED = "updated"

        val KNOWN_KEYS = setOf(
            KEY_ID, KEY_TITLE, KEY_STATUS, KEY_DUE, KEY_COMPLETED,
            KEY_DEPENDS_ON, KEY_RELATES_TO, KEY_CREATED, KEY_UPDATED,
        )
    }
}

/**
 * Projects edges from frontmatter *and* from `[[wikilinks]]` in the body.
 *
 * Wikilink edges are derived on every load rather than written into frontmatter: the body is
 * already their source of truth, and copying them into metadata would strand an edge the moment
 * the link was deleted from the prose.
 */
fun List<Node>.toEdges(): List<Edge> {
    val known = mapTo(HashSet()) { it.id }
    val nodes = this
    return flatMap { node ->
        val explicitDependencies = node.dependsOn.filter { it in known }
        val explicitRelations = node.relatesTo.filter { it in known }
        val inlineRelations = WikiLinks.resolve(node.body, nodes)
            .filter { it != node.id && it !in explicitRelations && it !in explicitDependencies }

        explicitDependencies.map { Edge(node.id, it, EdgeKind.DependsOn) } +
            (explicitRelations + inlineRelations).map { Edge(node.id, it, EdgeKind.RelatesTo) }
    }
}
