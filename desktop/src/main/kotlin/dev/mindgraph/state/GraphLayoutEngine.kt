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
 */
enum class LayoutMode { Mind, Flow }

class GraphLayoutEngine {
    val positions = mutableStateMapOf<String, Vec2>()
    private val velocities = HashMap<String, Vec2>()
    private val pinned = HashSet<String>()
    private val flowTargets = HashMap<String, Vec2>()
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
        flowTargets.keys.retainAll(idSet)
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
        flowTargets.clear()
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
            flowTargets[id] = Vec2(x - centerX, (ranks[id] ?: 0) * rowHeight - centerY)
        }
    }

    fun step() {
        when (mode) {
            LayoutMode.Mind -> stepForces()
            LayoutMode.Flow -> stepTowardFlowTargets()
        }
    }

    private fun stepTowardFlowTargets() {
        val easing = 0.18f
        for (id in nodeIds) {
            if (id in pinned) continue
            val target = flowTargets[id] ?: continue
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
