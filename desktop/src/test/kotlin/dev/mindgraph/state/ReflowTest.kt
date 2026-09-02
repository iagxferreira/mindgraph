package dev.mindgraph.state

import dev.mindgraph.model.Edge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Rebuilding the layout when a filter changes how many nodes there are.
 *
 * A layout is a statement about a count, so the set the engine is driven with has to be the
 * set on screen — otherwise the picture keeps the shape of nodes nobody can see.
 */
class ReflowTest {

    private fun engineOver(vararg ids: String): GraphLayoutEngine =
        GraphLayoutEngine().apply { sync(ids.toList(), emptyList<Edge>()) }

    @Test
    fun reflowMovesUnpinnedNodes() {
        val engine = engineOver("a", "b", "c")
        val before = engine.positions.toMap()

        engine.reflow()

        for (id in listOf("a", "b", "c")) {
            assertNotEquals(before[id], engine.positions[id], "$id should have been reseeded")
        }
    }

    @Test
    fun reflowLeavesPinnedNodesExactlyWhereTheyAre() {
        // A pin is a position a person chose by dragging. Hiding some other node is not a
        // reason to throw that away.
        val engine = engineOver("a", "b")
        engine.setPinned("a", true)
        val pinnedAt = engine.positions["a"]

        engine.reflow()

        assertEquals(pinnedAt, engine.positions["a"])
        assertNotEquals(engine.positions["b"], pinnedAt)
    }

    @Test
    fun everyNodeStillHasAPositionAfterReflow() {
        // Reflow seeds rather than clears, so no node can be dropped from the canvas for a
        // frame regardless of whether sync runs before it or after it.
        val engine = engineOver("a", "b", "c")

        engine.reflow()

        for (id in listOf("a", "b", "c")) {
            assertNotNull(engine.positions[id], "$id lost its position")
        }
    }

    @Test
    fun reflowIsSafeInEitherOrderWithSync() {
        val engine = engineOver("a", "b", "c")

        // The filter drops "c"; sync lands first, then the reflow.
        engine.sync(listOf("a", "b"), emptyList())
        engine.reflow()

        assertEquals(setOf("a", "b"), engine.positions.keys.toSet())

        // And the other way round: reflow first, then sync prunes.
        engine.reflow()
        engine.sync(listOf("a"), emptyList())

        assertEquals(setOf("a"), engine.positions.keys.toSet())
    }

    @Test
    fun aNodeHiddenByAFilterStopsInfluencingTheLayout() {
        // The point of driving the engine with the filtered set: forces are computed over
        // nodeIds, so a hidden node must not still be pushing its neighbours around.
        val engine = engineOver("a", "b", "c")
        engine.sync(listOf("a", "b"), emptyList())

        engine.step()

        assertTrue("c" !in engine.positions, "a filtered-out node should not be laid out")
    }
}
