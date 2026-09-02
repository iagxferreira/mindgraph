package dev.mindgraph.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import dev.mindgraph.model.Edge
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.state.GraphLayoutEngine
import dev.mindgraph.state.TaskGraph
import dev.mindgraph.state.Vec2
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.AccentSoft
import dev.mindgraph.ui.theme.Blocked
import dev.mindgraph.ui.theme.Done
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.Context
import dev.mindgraph.ui.theme.TextMuted
import dev.mindgraph.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Notes and tasks as one graph. Radius encodes tracked time; color encodes task state, with
 * blocked nodes dashed — so "where did time go" and "what can I start" read off the same picture.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GraphCanvas(
    nodes: List<Node>,
    edges: List<Edge>,
    graph: TaskGraph,
    layout: GraphLayoutEngine,
    selectedNodeId: NodeId?,
    linkSourceId: NodeId?,
    viewResetKey: Int,
    trackedSecondsFor: (NodeId) -> Long,
    /** Group name to the centre it was laid out around; empty in every mode but Cluster. */
    clusterLabels: Map<String, Vec2> = emptyMap(),
    /**
     * Whether to draw each node's title under it. Off is for reading the shape of the graph
     * rather than its contents: at vault scale the titles overlap into noise, and the selected
     * node's card still names whatever you click, so nothing becomes unidentifiable.
     */
    showLabels: Boolean = true,
    /**
     * Nodes drawn as evidence rather than as work: finished tasks held in a Flow tree so it
     * keeps its shape. Faded and shrunk, so they read as the past of the chain rather than as
     * something waiting to be picked up.
     */
    ghostIds: Set<NodeId> = emptySet(),
    onSelectNode: (NodeId) -> Unit,
    onLinkTarget: (NodeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    val textMeasurer = rememberTextMeasurer()
    val nodeIds = remember(nodes) { nodes.map { it.id.value } }

    LaunchedEffect(viewResetKey) {
        pan = Offset.Zero
        zoom = 1f
    }

    LaunchedEffect(nodeIds, edges, layout.mode) {
        // Keep hidden archive nodes out of the layout too; invisible nodes must not reserve space.
        layout.sync(nodeIds, edges)
        while (true) {
            layout.step()
            delay(16)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(nodeIds) {
                var draggedId: String? = null
                detectDragGestures(
                    onDragStart = { startOffset ->
                        draggedId = hitTest(nodeIds, layout, trackedSecondsFor, pan, zoom, startOffset, size)
                        draggedId?.let { layout.setPinned(it, true) }
                    },
                    onDragEnd = { draggedId = null },
                    onDragCancel = { draggedId = null },
                ) { change, dragAmount ->
                    change.consume()
                    val id = draggedId
                    val current = id?.let { layout.positions[it] }
                    if (id != null && current != null) {
                        layout.positions[id] = current + Vec2(dragAmount.x / zoom, dragAmount.y / zoom)
                    } else {
                        pan += dragAmount
                    }
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                zoom = (zoom * (1f - delta * 0.08f)).coerceIn(0.3f, 3f)
            }
            .pointerInput(nodeIds, linkSourceId) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val hit = hitTest(nodeIds, layout, trackedSecondsFor, pan, zoom, tapOffset, size)
                            ?: return@detectTapGestures
                        if (linkSourceId != null) onLinkTarget(NodeId(hit)) else onSelectNode(NodeId(hit))
                    },
                    onDoubleTap = { tapOffset ->
                        val hit = hitTest(nodeIds, layout, trackedSecondsFor, pan, zoom, tapOffset, size)
                            ?: return@detectTapGestures
                        layout.setPinned(hit, false)
                    },
                )
            },
    ) {
        val centerX = size.width / 2f + pan.x
        val centerY = size.height / 2f + pan.y

        fun toScreen(p: Vec2): Offset = Offset(centerX + p.x * zoom, centerY + p.y * zoom)

        for (edge in edges) {
            val from = layout.positions[edge.sourceId.value] ?: continue
            val to = layout.positions[edge.targetId.value] ?: continue
            // Three relationships, three readings: an unbroken line orders work, a dash is a
            // loose association, and a dotted line in its own hue is what a project would load.
            drawLine(
                color = when (edge.kind) {
                    EdgeKind.DependsOn -> Accent.copy(alpha = 0.55f)
                    EdgeKind.ContextFor -> Context.copy(alpha = 0.6f)
                    EdgeKind.RelatesTo -> AccentSoft
                },
                start = toScreen(from),
                end = toScreen(to),
                strokeWidth = when (edge.kind) {
                    EdgeKind.DependsOn -> 2.2f
                    EdgeKind.ContextFor -> 1.8f
                    EdgeKind.RelatesTo -> 1.6f
                },
                pathEffect = when (edge.kind) {
                    EdgeKind.DependsOn -> null
                    EdgeKind.ContextFor -> PathEffect.dashPathEffect(floatArrayOf(1.5f, 4f))
                    EdgeKind.RelatesTo -> PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                },
            )
        }

        // Drawn between the edges and the nodes, in the hole a ring leaves at its own centre —
        // a cluster nobody can name is just a blob that moved.
        for ((name, centre) in clusterLabels) {
            val caption = textMeasurer.measure(
                text = name,
                style = TextStyle(color = TextMuted, fontSize = 13.sp),
            )
            val at = toScreen(centre)
            drawText(
                textLayoutResult = caption,
                topLeft = Offset(at.x - caption.size.width / 2f, at.y - caption.size.height / 2f),
            )
        }

        for (node in nodes) {
            val pos = layout.positions[node.id.value] ?: continue
            val screen = toScreen(pos)
            val radius = nodeRadius(trackedSecondsFor(node.id)) * zoom
            val isSelected = node.id == selectedNodeId
            val isLinkSource = node.id == linkSourceId
            val emphasized = isSelected || isLinkSource
            val blocked = node.isTask && graph.isBlocked(node.id)
            val hue = nodeHue(node, blocked)
            val isGhost = node.id in ghostIds && !emphasized

            drawNodeShape(
                kind = node.kind,
                center = screen,
                // Smaller as well as fainter. Tracked time sets the radius, and finished work
                // has the most of it, so fading alone would leave the biggest circles on the
                // canvas belonging to the nodes that matter least.
                radius = if (isGhost) radius * GHOST_SCALE else radius,
                fill = when {
                    isGhost -> hue.copy(alpha = 0.06f)
                    emphasized -> hue.copy(alpha = 0.42f)
                    else -> hue.copy(alpha = 0.16f)
                },
                stroke = when {
                    isGhost -> hue.copy(alpha = 0.28f)
                    emphasized -> hue
                    else -> hue.copy(alpha = 0.7f)
                },
                strokeStyle = Stroke(
                    width = if (emphasized) 3f else 1.6f,
                    pathEffect = when {
                        blocked -> PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        layout.isPinned(node.id.value) -> PathEffect.dashPathEffect(floatArrayOf(2f, 3f))
                        else -> null
                    },
                ),
            )

            // Measuring is the expensive half, so the check wraps it rather than just the draw:
            // with labels off there is no reason to lay out text for every node every frame.
            if (showLabels) {
                val labelText = if (layout.mode == dev.mindgraph.state.LayoutMode.Flow) {
                    node.title.take(24).let { if (node.title.length > 24) "$it..." else it }
                } else {
                    node.title
                }
                val label = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = if (isGhost) TextMuted.copy(alpha = 0.5f) else TextPrimary,
                        fontSize = 12.sp,
                    ),
                )
                if (layout.mode != dev.mindgraph.state.LayoutMode.Flow || zoom >= 0.7f || emphasized) {
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(screen.x - label.size.width / 2f, screen.y + radius + 4f),
                    )
                }
            }
        }
    }
}

/**
 * Shape says what a node *is*; colour still says what state its work is in, and radius still
 * says how much time went into it. Three meanings, three channels, none of them borrowed.
 */
private fun DrawScope.drawNodeShape(
    kind: NodeKind,
    center: Offset,
    radius: Float,
    fill: Color,
    stroke: Color,
    strokeStyle: Stroke,
) {
    when (kind) {
        NodeKind.Note -> {
            drawCircle(color = fill, radius = radius, center = center)
            drawCircle(color = stroke, radius = radius, center = center, style = strokeStyle)
        }

        NodeKind.Rfc -> {
            // Slightly larger, because a diamond of equal radius reads smaller than a circle.
            val path = diamondPath(center, radius * 1.18f)
            drawPath(path, color = fill)
            drawPath(path, color = stroke, style = strokeStyle)
        }

        NodeKind.Reference -> {
            val side = radius * 1.62f
            val topLeft = Offset(center.x - side / 2f, center.y - side / 2f)
            drawRect(color = fill, topLeft = topLeft, size = Size(side, side))
            drawRect(color = stroke, topLeft = topLeft, size = Size(side, side), style = strokeStyle)
        }
    }
}

private fun diamondPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius, center.y)
    lineTo(center.x, center.y + radius)
    lineTo(center.x - radius, center.y)
    close()
}

private fun nodeHue(node: Node, blocked: Boolean): Color = when {
    blocked -> Blocked
    node.task?.status == TaskStatus.Done -> Done
    else -> Accent
}

/** How much of its normal size a ghost keeps. Small enough to recede, large enough to trace. */
private const val GHOST_SCALE = 0.6f

private fun nodeRadius(trackedSeconds: Long): Float {
    val minutes = trackedSeconds / 60f
    return 18f + min(minutes, 180f) * 0.35f
}

/** Finds the node whose circle contains [point], in screen space. */
private fun hitTest(
    nodeIds: List<String>,
    layout: GraphLayoutEngine,
    trackedSecondsFor: (NodeId) -> Long,
    pan: Offset,
    zoom: Float,
    point: Offset,
    canvasSize: IntSize,
): String? {
    val centerX = canvasSize.width / 2f + pan.x
    val centerY = canvasSize.height / 2f + pan.y
    val hit = nodeIds.minByOrNull { id ->
        val p = layout.positions[id] ?: return@minByOrNull Float.MAX_VALUE
        val dx = (centerX + p.x * zoom) - point.x
        val dy = (centerY + p.y * zoom) - point.y
        dx * dx + dy * dy
    } ?: return null
    val p = layout.positions[hit] ?: return null
    val dx = (centerX + p.x * zoom) - point.x
    val dy = (centerY + p.y * zoom) - point.y
    val radius = nodeRadius(trackedSecondsFor(NodeId(hit))) * zoom
    return if (dx * dx + dy * dy <= radius * radius * 4f) hit else null
}
