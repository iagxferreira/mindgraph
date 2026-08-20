package dev.mindgraph.state

import androidx.compose.runtime.mutableStateMapOf
import dev.mindgraph.model.Link
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
 * A small force-directed layout: nodes repel each other, edges act as springs pulling
 * linked notes together, and everything is pulled gently toward the center. Run [step]
 * on a timer while the graph is visible; positions live in a Compose snapshot map so
 * the canvas redraws automatically as they move.
 */
class GraphLayoutEngine {
    val positions = mutableStateMapOf<Long, Vec2>()
    private val velocities = HashMap<Long, Vec2>()
    private val random = Random(42)
    private var nodeIds: List<Long> = emptyList()
    private var links: List<Link> = emptyList()

    fun sync(noteIds: List<Long>, links: List<Link>) {
        val idSet = noteIds.toSet()
        positions.keys.retainAll(idSet)
        velocities.keys.retainAll(idSet)
        for (id in noteIds) {
            if (id !in positions) {
                val angle = random.nextDouble(0.0, 2 * Math.PI)
                val radius = 80.0 + random.nextDouble(0.0, 140.0)
                positions[id] = Vec2((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
                velocities[id] = Vec2(0f, 0f)
            }
        }
        nodeIds = noteIds
        this.links = links
    }

    fun step() {
        val ids = nodeIds
        if (ids.size < 2) return

        val repulsion = 14000f
        val springLength = 170f
        val springStrength = 0.02f
        val damping = 0.82f
        val centerPull = 0.0015f

        val forces = HashMap<Long, Vec2>(ids.size)
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

        for (link in links) {
            val pa = positions[link.sourceNoteId] ?: continue
            val pb = positions[link.targetNoteId] ?: continue
            val dx = pb.x - pa.x
            val dy = pb.y - pa.y
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val force = (dist - springLength) * springStrength
            val fx = dx / dist * force
            val fy = dy / dist * force
            forces[link.sourceNoteId] = (forces[link.sourceNoteId] ?: Vec2(0f, 0f)) + Vec2(fx, fy)
            forces[link.targetNoteId] = (forces[link.targetNoteId] ?: Vec2(0f, 0f)) - Vec2(fx, fy)
        }

        for (id in ids) {
            val pos = positions[id] ?: continue
            val pull = pos * -centerPull
            val vel = ((velocities[id] ?: Vec2(0f, 0f)) + (forces[id] ?: Vec2(0f, 0f)) + pull) * damping
            velocities[id] = vel
            positions[id] = pos + vel
        }
    }
}
