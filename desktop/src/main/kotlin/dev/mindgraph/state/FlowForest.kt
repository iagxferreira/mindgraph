package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskStatus

/**
 * What Flow mode draws: the dependency trees among tasks, and nothing else.
 *
 * Flow used to lay out every visible node by dependency depth. In a real vault almost nothing
 * depends on anything — 11 dependency edges against 104 nodes — so all but a handful landed at
 * depth 0, each claiming its own column, and the mode rendered as one row thousands of pixels
 * wide with the actual structure lost inside it.
 *
 * The fix is to stop laying out nodes that have no place in a dependency tree. Flow answers
 * "what has an order", so its subject is exactly the tasks that participate in a dependency.
 * Everything else is still on Mind, on Cluster, and in the node list.
 */
object FlowForest {

    /** One dependency tree: the ids in it, positioned, with the deepest chain it contains. */
    data class Tree(val positions: Map<String, Vec2>, val depth: Int)

    data class Layout(
        /** The nodes Flow should draw, in place of the ordinary visible set. */
        val nodes: List<Node>,
        /** Where each one goes. Keyed by raw id, matching [GraphLayoutEngine]. */
        val positions: Map<String, Vec2>,
        /**
         * Chain members kept only to hold a tree together: drawn faded, not as live work.
         *
         * Hiding done tasks severed the one tree worth looking at — seven of its nine nodes
         * were finished — leaving the live tasks as orphans with no visible reason to be
         * where they are. A finished prerequisite is why a task sits where it sits, so it
         * stays on the canvas as evidence rather than vanishing.
         */
        val ghosts: Set<NodeId>,
        /** Tasks with no dependency either way, deliberately not drawn. Reported, not hidden. */
        val looseTaskCount: Int,
    ) {
        val isEmpty: Boolean get() = nodes.isEmpty()
    }

    // A tree is read top-down: a prerequisite sits above everything that waits on it.
    private const val ROW_HEIGHT = 150f
    private const val COLUMN_SPACING = 180f
    /** Gap between two trees packed side by side, and between packed rows of them. */
    private const val TREE_GUTTER = 120f
    /**
     * Trees are packed into rows rather than one line. The width is a windowful at zoom 1 —
     * the point is that the picture wraps at all, not the exact figure.
     */
    private const val WRAP_WIDTH = 1400f

    /**
     * @param includeDone when false, finished tasks are kept as ghosts rather than dropped,
     *   so a tree does not lose its interior.
     * @param includeArchived archived work is left out by default. Archiving stops a task
     *   blocking its dependents, so an archived node is not part of an order of work — but the
     *   toggle stays live here rather than sitting inert on a tab where it does nothing.
     */
    fun build(
        nodes: List<Node>,
        includeDone: Boolean = true,
        includeArchived: Boolean = false,
    ): Layout {
        val tasks = nodes.filter { it.isTask && (includeArchived || !it.archived) }
        val byId = tasks.associateBy { it.id }

        // Only task-to-task dependencies shape Flow. A task depending on a plain note would
        // put a non-task in a view about the order of work, so those edges are left to Mind.
        val prerequisites: Map<NodeId, List<NodeId>> = tasks.associate { task ->
            task.id to task.dependsOn.filter { it in byId && it != task.id }
        }
        val dependents = HashMap<NodeId, MutableList<NodeId>>()
        for ((id, deps) in prerequisites) {
            for (dep in deps) dependents.getOrPut(dep) { mutableListOf() }.add(id)
        }

        val components = components(tasks.map { it.id }, prerequisites, dependents)
        // A task on its own is not a tree. Dropping single-node components is what keeps the
        // 4 real chains from being lost among 55 unconnected dots.
        val chains = components.filter { it.size > 1 }
        val looseCount = components.size - chains.size

        val drawnIds = chains.flatten().toSet()
        val ghosts = if (includeDone) {
            emptySet()
        } else {
            drawnIds.filterTo(HashSet()) { byId.getValue(it).task?.status == TaskStatus.Done }
        }

        val trees = chains.map { layOutTree(it, prerequisites, dependents) }
        val positions = pack(trees)

        // Ordered by the tree they belong to, so the canvas draws a component together.
        val drawn = chains.flatten().mapNotNull { byId[it] }
        return Layout(
            nodes = drawn,
            positions = positions,
            ghosts = ghosts,
            looseTaskCount = looseCount,
        )
    }

    /** Connected components over dependency edges, direction ignored. */
    private fun components(
        ids: List<NodeId>,
        prerequisites: Map<NodeId, List<NodeId>>,
        dependents: Map<NodeId, List<NodeId>>,
    ): List<List<NodeId>> {
        val seen = HashSet<NodeId>()
        val out = mutableListOf<List<NodeId>>()
        for (start in ids) {
            if (!seen.add(start)) continue
            val component = mutableListOf(start)
            val stack = ArrayDeque(listOf(start))
            while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                val neighbours = prerequisites[current].orEmpty() + dependents[current].orEmpty()
                for (next in neighbours) {
                    if (seen.add(next)) {
                        component.add(next)
                        stack.addLast(next)
                    }
                }
            }
            out.add(component)
        }
        // Biggest tree first: it is the one worth the best position on the canvas.
        return out.sortedWith(compareByDescending<List<NodeId>> { it.size }.thenBy { it.first().value })
    }

    /**
     * One component, laid out top-down: depth is the longest chain of prerequisites behind a
     * node, and a prerequisite is centred over the work waiting on it.
     *
     * Not a tree in the strict sense — a task can have two prerequisites, so a node can be
     * reached twice. Memoising the x of a node it is shared by makes the shared node sit once
     * and both parents centre over the same point, which is the honest picture of a diamond.
     */
    private fun layOutTree(
        component: List<NodeId>,
        prerequisites: Map<NodeId, List<NodeId>>,
        dependents: Map<NodeId, List<NodeId>>,
    ): Tree {
        val inComponent = component.toSet()
        val depths = HashMap<NodeId, Int>()

        fun depthOf(id: NodeId, seen: Set<NodeId> = emptySet()): Int {
            depths[id]?.let { return it }
            if (id in seen) return 0 // A hand-edited file could still cycle.
            val depth = prerequisites[id].orEmpty()
                .filter { it in inComponent }
                .maxOfOrNull { depthOf(it, seen + id) + 1 } ?: 0
            depths[id] = depth
            return depth
        }
        component.forEach { depthOf(it) }

        val xs = HashMap<NodeId, Float>()
        var cursor = 0
        // Ordered so the picture does not reshuffle between renders.
        val roots = component.filter { depths.getValue(it) == 0 }.sortedBy { it.value }
        val placing = HashSet<NodeId>()

        fun place(id: NodeId): Float {
            xs[id]?.let { return it }
            // Centring a node over its dependents recurses downwards, and a cycle in a
            // hand-edited file would make that recursion unbounded. Break it with a column of
            // its own: a cycle has no meaningful centre to sit over anyway.
            if (!placing.add(id)) {
                val fallback = cursor++ * COLUMN_SPACING
                xs[id] = fallback
                return fallback
            }
            val below = dependents[id].orEmpty().filter { it in inComponent }.sortedBy { it.value }
            val x = if (below.isEmpty()) cursor++ * COLUMN_SPACING else below.map(::place).average().toFloat()
            placing.remove(id)
            xs[id] = x
            return x
        }
        roots.forEach { place(it) }
        // A cycle would leave nodes unreached by any root; give them a column rather than drop them.
        component.filter { it !in xs }.sortedBy { it.value }.forEach { place(it) }

        return Tree(
            positions = component.associate { it.value to Vec2(xs.getValue(it), depths.getValue(it) * ROW_HEIGHT) },
            depth = depths.values.maxOrNull() ?: 0,
        )
    }

    /**
     * Packs trees into rows that wrap, then centres the whole arrangement on the origin.
     *
     * The old layout put every node on one line because it never wrapped; a vault with a dozen
     * small chains has to read as a dozen small pictures, not one endless strip.
     */
    private fun pack(trees: List<Tree>): Map<String, Vec2> {
        if (trees.isEmpty()) return emptyMap()

        val placed = HashMap<String, Vec2>()
        var rowX = 0f
        var rowY = 0f
        var rowHeight = 0f

        for (tree in trees) {
            val xsInTree = tree.positions.values.map { it.x }
            val minX = xsInTree.min()
            val width = xsInTree.max() - minX
            val height = tree.depth * ROW_HEIGHT

            if (rowX > 0f && rowX + width > WRAP_WIDTH) {
                rowX = 0f
                rowY += rowHeight + TREE_GUTTER
                rowHeight = 0f
            }
            for ((id, position) in tree.positions) {
                placed[id] = Vec2(rowX + (position.x - minX), rowY + position.y)
            }
            rowX += width + TREE_GUTTER
            rowHeight = maxOf(rowHeight, height)
        }

        val allX = placed.values.map { it.x }
        val allY = placed.values.map { it.y }
        val centreX = (allX.min() + allX.max()) / 2f
        val centreY = (allY.min() + allY.max()) / 2f
        return placed.mapValues { (_, position) -> Vec2(position.x - centreX, position.y - centreY) }
    }
}
