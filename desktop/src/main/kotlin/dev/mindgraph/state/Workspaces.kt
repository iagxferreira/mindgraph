package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.Workspace

/**
 * Turning a saved selection into the nodes it means, and finding the selections a vault already
 * implies.
 */
object Workspaces {

    /**
     * The nodes a workspace selects: the rule gathers, `include` adds, `exclude` removes.
     *
     * Exclusion is applied last and wins over inclusion. A workspace is read as "this, except
     * that", and an exception that could be overruled by the rule it is an exception to would be
     * no exception at all.
     */
    fun resolve(nodes: List<Node>, workspace: Workspace): List<Node> {
        val gathered = nodes.filterTo(LinkedHashSet()) { matches(it, workspace.rule, nodes) }
        workspace.include.forEach { id -> nodes.find { it.id == id }?.let(gathered::add) }
        val excluded = workspace.exclude.toSet()
        return nodes.filter { it in gathered && it.id !in excluded }
    }

    private fun matches(node: Node, rule: Workspace.Rule, nodes: List<Node>): Boolean = when (rule) {
        is Workspace.Rule.OriginUnder -> {
            val origin = node.origin
            // Compared with a separator so `/estudos` cannot swallow `/estudos-antigos`, and
            // the folder itself is not matched as if it were a file inside it.
            origin != null && origin.startsWith(rule.path.trimEnd('/') + "/")
        }

        is Workspace.Rule.InProject -> node.originProject == rule.project
        is Workspace.Rule.OfKind -> node.kind == rule.kind
        is Workspace.Rule.ContextFor -> nodes.any { it.id == node.id && rule.nodeId in it.contextFor }
        Workspace.Rule.Nothing -> false
    }

    /**
     * The folder a node was imported from, one level below [root].
     *
     * The Obsidian import attributed 337 notes to `obsidian-main`, which is the name of a
     * *source* rather than a project — the nine folders inside it are what someone actually
     * maintained. Deriving this from the stored `origin` recovers them without re-importing,
     * which the import would refuse anyway since it skips origins it has seen.
     */
    fun folderUnder(node: Node, root: String): String? {
        val prefix = root.trimEnd('/') + "/"
        val origin = node.origin?.takeIf { it.startsWith(prefix) } ?: return null
        val relative = origin.removePrefix(prefix)
        return relative.substringBefore('/').takeIf { it.isNotEmpty() && it != relative }
    }

    /**
     * Folders under [root] that hold enough nodes to be worth offering as a workspace.
     *
     * Sorted by size: the folder someone filled with 194 notes is a more useful selection than
     * one holding a stray file, and a list of every folder is a list nobody reads.
     */
    fun suggestFolders(nodes: List<Node>, root: String, minimumSize: Int = MINIMUM_FOLDER): List<Pair<String, Int>> =
        nodes.mapNotNull { folderUnder(it, root) }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= minimumSize }
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    /**
     * The import roots present in the vault, derived from where nodes came from.
     *
     * A person should not have to remember and retype the path they imported from, and the vault
     * already knows: it is the common prefix of everything attributed to one project.
     */
    fun importRoots(nodes: List<Node>): Map<String, String> =
        nodes.filter { it.origin != null && it.originProject != null }
            .groupBy { it.originProject!! }
            .mapNotNull { (project, group) ->
                commonDirectory(group.mapNotNull { it.origin })?.let { project to it }
            }
            .toMap()

    private fun commonDirectory(paths: List<String>): String? {
        if (paths.isEmpty()) return null
        val split = paths.map { it.substringBeforeLast('/').split('/') }
        val shortest = split.minOf { it.size }
        val common = mutableListOf<String>()
        for (index in 0 until shortest) {
            val segment = split.first()[index]
            if (split.all { it[index] == segment }) common.add(segment) else break
        }
        return common.joinToString("/").takeIf { it.isNotEmpty() }
    }

    private const val MINIMUM_FOLDER = 3
}
