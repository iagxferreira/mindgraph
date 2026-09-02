package dev.mindgraph.state

import dev.mindgraph.model.Node

/**
 * Which group each node belongs to in [LayoutMode.Cluster].
 *
 * Pulled out of the composable so the rule can be tested and so the label on screen and the
 * position on the canvas cannot disagree about what a group is.
 */
object Clustering {

    /**
     * Nodes written here have no origin, and there are far more of them than of anything
     * imported. Calling that out as its own group is the honest reading — the split that
     * matters is your own work against context carried in from elsewhere — rather than
     * leaving the majority of the vault in a group named after nothing.
     */
    const val LOCAL = "This vault"

    /** Node id to group name, for every node on the canvas. */
    fun groups(nodes: List<Node>): Map<String, String> =
        nodes.associate { node -> node.id.value to label(node.originProject) }

    /**
     * `-home-iago-workspace-geo-resolution-rag` reads as `geo-resolution-rag`.
     *
     * The stored value is an absolute path with its separators replaced by `-`, which is kept
     * whole because shortening it is guesswork — but a label is exactly where that guess is
     * cheap, since being wrong costs a nicer name rather than a wrong edge.
     */
    fun label(originProject: String?): String {
        val raw = originProject?.trim().orEmpty()
        if (raw.isEmpty()) return LOCAL
        return raw.substringAfterLast("-workspace-", raw).trim('-').ifEmpty { raw }
    }
}
