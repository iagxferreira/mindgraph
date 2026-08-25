package dev.mindgraph.state

import androidx.compose.runtime.mutableStateMapOf
import dev.mindgraph.model.Edge
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
 * A small force-directed layout: nodes repel each other, edges act as springs pulling linked
 * nodes together, and everything is pulled gently toward the center. Positions live in a
 * Compose snapshot map so the canvas redraws as they move.
 */
class GraphLayoutEngine {
    val positions = mutableStateMapOf<String, Vec2>()
    private val velocities = HashMap<String, Vec2>()
    private val pinned = HashSet<String>()
    private val random = Random(42)
    private var nodeIds: List<String> = emptyList()
    private var edges: List<Edge> = emptyList()

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

    fun step() {
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
