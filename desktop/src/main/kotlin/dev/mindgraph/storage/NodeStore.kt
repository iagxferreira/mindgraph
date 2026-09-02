package dev.mindgraph.storage

import dev.mindgraph.model.Edge
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
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

    suspend fun create(
        title: String,
        body: String = "",
        task: TaskFacet? = null,
        kind: NodeKind = NodeKind.Note,
        assignee: String? = null,
        /**
         * Frontmatter keys MindGraph does not model, written on the way in. Once on disk they
         * are carried by [existingExtras] like any hand-added key, so an importer can record
         * where a node came from without the domain growing a field for every source.
         */
        extras: Map<String, String> = emptyMap(),
    ): Node =
        withContext(Dispatchers.IO) {
            vault.prepare()
            val now = timestamp()
            val node = Node(
                id = NodeId(Ulid.generate()),
                title = title,
                body = body,
                kind = kind,
                task = task,
                assignee = assignee,
                createdAt = now,
                updatedAt = now,
                slug = allocateSlug(slugify(title), null),
            )
            writeNode(node, extras.mapValues { (_, text) -> Frontmatter.Value.Scalar(text) })
            node
        }

    /**
     * Every value a frontmatter key takes across the vault.
     *
     * Reads the files rather than [load], because the keys worth asking about this way are the
     * ones [Node] deliberately does not model — an importer asking "which of these have I
     * already brought in?" is the case this exists for.
     */
    suspend fun frontmatterValues(key: String): Set<String> = withContext(Dispatchers.IO) {
        vault.prepare()
        Files.list(vault.nodesDir).use { stream ->
            stream.filter { it.toString().endsWith(".md") && Files.isRegularFile(it) }.toList()
        }
            .mapNotNull { path ->
                runCatching { Files.readString(path) }.getOrNull()
                    ?.let { Frontmatter.split(it).first.string(key) }
            }
            .toSet()
    }

    /**
     * Nodes whose frontmatter has [key] set to [value].
     *
     * The companion to [frontmatterValues], for the same reason: the keys worth asking about
     * are the ones [Node] does not model, so the question has to be put to the files.
     */
    suspend fun nodesWith(key: String, value: String): List<Node> = withContext(Dispatchers.IO) {
        vault.prepare()
        Files.list(vault.nodesDir).use { stream ->
            stream.filter { it.toString().endsWith(".md") && Files.isRegularFile(it) }.toList()
        }
            .filter { path ->
                runCatching { Files.readString(path) }.getOrNull()
                    ?.let { Frontmatter.split(it).first.string(key) } == value
            }
            .mapNotNull { readNode(it) }
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
            // An unrecognised kind reads as a note rather than dropping the file: a typo in a
            // hand-edited vault should cost you a label, not the document.
            kind = NodeKind.parse(frontmatter.string(KEY_KIND)) ?: NodeKind.Note,
            archived = frontmatter.string(KEY_ARCHIVED)?.trim().equals("true", ignoreCase = true),
            assignee = frontmatter.string(KEY_ASSIGNEE)?.trim()?.takeIf { it.isNotEmpty() },
            aliases = readAliases(frontmatter),
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

    private fun writeNode(node: Node, added: Map<String, Frontmatter.Value> = emptyMap()) {
        val path = vault.nodesDir.resolve("${node.slug}.md")
        // What is already on disk wins nothing and loses nothing: added keys are only supplied
        // on create, where there is no file to preserve.
        val preserved = existingExtras(path) + added

        val lines = buildList {
            add("---")
            add("$KEY_ID: ${node.id.value}")
            add("$KEY_TITLE: ${Frontmatter.quote(node.title)}")
            add("$KEY_KIND: ${node.kind.slug}")
            // Written only when true: `archived: false` on every file is noise on a flag that
            // is false almost always.
            if (node.archived) add("$KEY_ARCHIVED: true")
            node.assignee?.let { add("$KEY_ASSIGNEE: ${Frontmatter.quote(it)}") }
            if (node.aliases.isNotEmpty()) {
                add("$KEY_ALIASES: [${node.aliases.joinToString(", ") { Frontmatter.quote(it) }}]")
            }
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

    /**
     * Aliases come from [KEY_ALIASES], plus [MemoryImport.KEY_MEMORY_NAME] when it is there.
     *
     * The importer wrote only the memory name before aliases existed, and those notes are
     * already in people's vaults — re-importing would not reach them, because the import skips
     * anything it has seen. Reading both is what makes those links resolve without a migration.
     */
    private fun readAliases(frontmatter: Frontmatter): List<String> =
        (frontmatter.list(KEY_ALIASES) + listOfNotNull(frontmatter.string(MemoryImport.KEY_MEMORY_NAME)))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

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
        const val KEY_KIND = "kind"
        const val KEY_ARCHIVED = "archived"
        const val KEY_ASSIGNEE = "assignee"
        const val KEY_ALIASES = "aliases"
        const val KEY_STATUS = "status"
        const val KEY_DUE = "due"
        const val KEY_COMPLETED = "completed"
        const val KEY_DEPENDS_ON = "depends_on"
        const val KEY_RELATES_TO = "relates_to"
        const val KEY_CREATED = "created"
        const val KEY_UPDATED = "updated"

        val KNOWN_KEYS = setOf(
            KEY_ID, KEY_TITLE, KEY_KIND, KEY_ARCHIVED, KEY_ASSIGNEE, KEY_ALIASES, KEY_STATUS, KEY_DUE, KEY_COMPLETED,
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
