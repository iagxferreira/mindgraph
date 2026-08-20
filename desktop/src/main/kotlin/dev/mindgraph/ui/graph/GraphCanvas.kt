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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.mindgraph.model.Link
import dev.mindgraph.model.Note
import dev.mindgraph.state.GraphLayoutEngine
import dev.mindgraph.state.Vec2
import dev.mindgraph.ui.theme.Accent
import dev.mindgraph.ui.theme.AccentSoft
import dev.mindgraph.ui.theme.Ink
import dev.mindgraph.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Renders notes as nodes and links as edges on a pannable/zoomable canvas. Node radius
 * encodes cumulative tracked time for that note — the visual that turns the graph into a
 * picture of where study time actually went.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GraphCanvas(
    notes: List<Note>,
    links: List<Link>,
    layout: GraphLayoutEngine,
    selectedNoteId: Long?,
    linkSourceId: Long?,
    trackedSecondsForNote: (Long) -> Long,
    onSelectNote: (Long) -> Unit,
    onLinkTarget: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    val textMeasurer = rememberTextMeasurer()
    val noteIds = remember(notes) { notes.map { it.id } }

    LaunchedEffect(noteIds, links) {
        while (true) {
            layout.step()
            delay(16)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    pan += dragAmount
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                zoom = (zoom * (1f - delta * 0.08f)).coerceIn(0.3f, 3f)
            }
            .pointerInput(noteIds, linkSourceId) {
                detectTapGestures { tapOffset ->
                    val centerX = size.width / 2f + pan.x
                    val centerY = size.height / 2f + pan.y
                    val hit = noteIds.minByOrNull { id ->
                        val p = layout.positions[id] ?: return@minByOrNull Float.MAX_VALUE
                        val dx = (centerX + p.x * zoom) - tapOffset.x
                        val dy = (centerY + p.y * zoom) - tapOffset.y
                        dx * dx + dy * dy
                    } ?: return@detectTapGestures
                    val p = layout.positions[hit] ?: return@detectTapGestures
                    val dx = (centerX + p.x * zoom) - tapOffset.x
                    val dy = (centerY + p.y * zoom) - tapOffset.y
                    val radius = nodeRadius(trackedSecondsForNote(hit)) * zoom
                    if (dx * dx + dy * dy <= radius * radius * 4f) {
                        if (linkSourceId != null) onLinkTarget(hit) else onSelectNote(hit)
                    }
                }
            },
    ) {
        val centerX = size.width / 2f + pan.x
        val centerY = size.height / 2f + pan.y

        fun toScreen(p: Vec2): Offset = Offset(centerX + p.x * zoom, centerY + p.y * zoom)

        for (link in links) {
            val from = layout.positions[link.sourceNoteId] ?: continue
            val to = layout.positions[link.targetNoteId] ?: continue
            drawLine(color = AccentSoft, start = toScreen(from), end = toScreen(to), strokeWidth = 1.6f)
        }

        for (note in notes) {
            val pos = layout.positions[note.id] ?: continue
            val screen = toScreen(pos)
            val radius = nodeRadius(trackedSecondsForNote(note.id)) * zoom
            val isSelected = note.id == selectedNoteId
            val isLinkSource = note.id == linkSourceId

            drawCircle(
                color = Accent.copy(alpha = if (isSelected || isLinkSource) 0.4f else 0.18f),
                radius = radius,
                center = screen,
            )
            drawCircle(
                color = if (isSelected || isLinkSource) Accent else AccentSoft,
                radius = radius,
                center = screen,
                style = Stroke(width = if (isSelected || isLinkSource) 3f else 1.4f),
            )

            val label = textMeasurer.measure(
                text = note.title,
                style = TextStyle(color = TextPrimary, fontSize = 12.sp),
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(screen.x - label.size.width / 2f, screen.y + radius + 4f),
            )
        }
    }
}

private fun nodeRadius(trackedSeconds: Long): Float {
    val minutes = trackedSeconds / 60f
    return 18f + min(minutes, 180f) * 0.35f
}
