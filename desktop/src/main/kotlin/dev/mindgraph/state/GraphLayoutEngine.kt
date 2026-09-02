package dev.mindgraph.state

import androidx.compose.runtime.mutableStateMapOf
import dev.mindgraph.model.Edge
import dev.mindgraph.model.EdgeKind
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
}

/**
 * Mind mode is a force-directed layout for associative thinking; Flow mode is a layered
 * left-to-right layout for dependencies. They are separate because springs actively scramble
 * the ordering a dependency graph exists to show.
 *
 * Cluster mode groups by the repository a node was imported from — the axis that makes a
 * cross-project brain legible. Kind was considered for this and argued down: it fights the
 * force-directed layout and the kinds are lopsided.
 */
enum class LayoutMode { Mind, Flow, Cluster }

class GraphLayoutEngine {
    val positions = mutableStateMapOf<String, Vec2>()
    private val velocities = HashMap<String, Vec2>()
    private val pinned = HashSet<String>()
    // Flow and Cluster both place nodes at computed positions and ease toward them; only
    // the placement rule differs, so they share one target map and one stepper.
    private val targets = HashMap<String, Vec2>()
    private val random = Random(42)
    private var nodeIds: List<String> = emptyList()
    private var edges: List<Edge> = emptyList()

    var mode: LayoutMode = LayoutMode.Mind

    fun isPinned(id: String): Boolean = id in pinned

    /** Pinned nodes keep their position through [step] — drag sets this, double-click clears it. */
    fun setPinned(id: String, isPinned: Boolean) {
        if (isPinned) {
            pinned.add(id)
            velocities[id] = Vec2(0f, 0f)
        } else {
            pinned.remove(id)
        }
    }

    fun unpinAll() {
        pinned.clear()
    }

    fun sync(ids: List<String>, edges: List<Edge>) {
        val idSet = ids.toSet()
        positions.keys.retainAll(idSet)
        velocities.keys.retainAll(idSet)
        pinned.retainAll(idSet)
        targets.keys.retainAll(idSet)
        for (id in ids) {
            if (id !in positions) {
                val angle = random.nextDouble(0.0, 2 * Math.PI)
                val radius = 80.0 + random.nextDouble(0.0, 140.0)
                positions[id] = Vec2((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
                velocities[id] = Vec2(0f, 0f)
            }
        }
        nodeIds = ids
        this.edges = edges
    }

    /** Places prerequisites above their dependents, centering each prerequisite over its branch. */
    fun setFlowRanks(ranks: Map<String, Int>) {
        targets.clear()
        if (ranks.isEmpty()) return

        val nodeSpacing = 180f
        val rowHeight = 150f
        val dependencies = edges
            .filter { it.kind == EdgeKind.DependsOn }
            .groupBy({ it.targetId.value }, { it.sourceId.value })
            .mapValues { (_, children) -> children.distinct().sorted() }
        val parentIds = dependencies.values.flatten().toSet()
        val roots = ranks.keys.filter { it !in parentIds }.sorted()
        val xPositions = HashMap<String, Float>()
        var leafCursor = 0

        fun place(id: String): Float {
            xPositions[id]?.let { return it }
            val children = dependencies[id].orEmpty()
            val x = if (children.isEmpty()) {
                leafCursor++ * nodeSpacing
            } else {
                children.map(::place).average().toFloat()
            }
            xPositions[id] = x
            return x
        }

        roots.forEach(::place)
        ranks.keys.filter { it !in xPositions }.sorted().forEach(::place)

        val centerX = ((xPositions.values.minOrNull() ?: 0f) + (xPositions.values.maxOrNull() ?: 0f)) / 2f
        val maxRank = ranks.values.maxOrNull() ?: 0
        val centerY = maxRank * rowHeight / 2f
        xPositions.forEach { (id, x) ->
            targets[id] = Vec2(x - centerX, (ranks[id] ?: 0) * rowHeight - centerY)
        }
    }

    /**
     * Groups nodes into rings, one per project, laid out around the origin.
     *
     * Group centres go on a circle sized by how many groups there are, and members go on a
     * ring inside their group sized by how many members it has — so a project with three
     * notes stays tight and one with thirty does not swallow its neighbours. Groups are
     * ordered by name so the picture does not reshuffle between renders.
     */
    fun setClusters(groups: Map<String, String>) {
        targets.clear()
        if (groups.isEmpty()) return

        val members = groups.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, ids) -> ids.sorted() }
        val names = members.keys.sorted()

        // One group is not a cluster; centring it beats pushing it off to one side.
        val galaxyRadius = if (names.size < 2) 0f else 260f + names.size * 52f

        names.forEachIndexed { index, name ->
            val ids = members.getValue(name)
            val angle = 2 * Math.PI * index / names.size
            val cx = (cos(angle) * galaxyRadius).toFloat()
            val cy = (sin(angle) * galaxyRadius).toFloat()

            if (ids.size == 1) {
                targets[ids.single()] = Vec2(cx, cy)
                return@forEachIndexed
            }
            val ringRadius = 46f + ids.size * 13f
            ids.forEachIndexed { member, id ->
                val theta = 2 * Math.PI * member / ids.size
                targets[id] = Vec2(
                    cx + (cos(theta) * ringRadius).toFloat(),
                    cy + (sin(theta) * ringRadius).toFloat(),
                )
            }
        }
    }

    /** Where each group's label belongs: the centre it was laid out around. */
    fun clusterCentres(groups: Map<String, String>): Map<String, Vec2> {
        if (groups.isEmpty()) return emptyMap()
        val names = groups.values.distinct().sorted()
        val galaxyRadius = if (names.size < 2) 0f else 260f + names.size * 52f
        return names.withIndex().associate { (index, name) ->
            val angle = 2 * Math.PI * index / names.size
            name to Vec2((cos(angle) * galaxyRadius).toFloat(), (sin(angle) * galaxyRadius).toFloat())
        }
    }

    fun step() {
        when (mode) {
            LayoutMode.Mind -> stepForces()
            LayoutMode.Flow -> stepTowardTargets()
            LayoutMode.Cluster -> stepTowardTargets()
        }
    }

    private fun stepTowardTargets() {
        val easing = 0.18f
        for (id in nodeIds) {
            if (id in pinned) continue
            val target = targets[id] ?: continue
            val current = positions[id] ?: continue
            positions[id] = current + (target - current) * easing
        }
    }

    private fun stepForces() {
        val ids = nodeIds
        if (ids.size < 2) return

        val repulsion = 14000f
        val springLength = 170f
        val springStrength = 0.02f
        val damping = 0.82f
        val centerPull = 0.0015f

        val forces = HashMap<String, Vec2>(ids.size)
        for (id in ids) forces[id] = Vec2(0f, 0f)

        for (i in ids.indices) {
            val a = ids[i]
            val pa = positions[a] ?: continue
            for (j in i + 1 until ids.size) {
                val b = ids[j]
                val pb = positions[b] ?: continue
                val dx = pa.x - pb.x
                val dy = pa.y - pb.y
                val distSq = (dx * dx + dy * dy).coerceAtLeast(1f)
                val dist = sqrt(distSq)
                val force = repulsion / distSq
                val fx = dx / dist * force
                val fy = dy / dist * force
                forces[a] = forces[a]!! + Vec2(fx, fy)
                forces[b] = forces[b]!! - Vec2(fx, fy)
            }
        }

        for (edge in edges) {
            val source = edge.sourceId.value
            val target = edge.targetId.value
            val pa = positions[source] ?: continue
            val pb = positions[target] ?: continue
            val dx = pb.x - pa.x
            val dy = pb.y - pa.y
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val force = (dist - springLength) * springStrength
            val fx = dx / dist * force
            val fy = dy / dist * force
            forces[source] = (forces[source] ?: Vec2(0f, 0f)) + Vec2(fx, fy)
            forces[target] = (forces[target] ?: Vec2(0f, 0f)) - Vec2(fx, fy)
        }

        for (id in ids) {
            if (id in pinned) continue
            val pos = positions[id] ?: continue
            val pull = pos * -centerPull
            val vel = ((velocities[id] ?: Vec2(0f, 0f)) + (forces[id] ?: Vec2(0f, 0f)) + pull) * damping
            velocities[id] = vel
            positions[id] = pos + vel
        }
    }
}
