package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.engine.ComposeVectorRenderer.drawStudioCanvas
import com.example.engine.VectorMath
import com.example.model.CanvasGuide
import com.example.model.EditorTool
import com.example.model.GuideOrientation
import com.example.model.PathShape
import com.example.model.VectorElement
import com.example.ui.components.HorizontalRuler
import com.example.ui.components.RulerCornerBadge
import com.example.ui.components.VerticalRuler
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderViolet
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.EditorViewModel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private enum class DragMode {
    NONE,
    PAN,
    MOVE_ELEMENT,
    MOVE_GUIDE,
    RESIZE_HANDLE,
    ROTATE_HANDLE,
    DRAW_SHAPE,
    FREEHAND,
    PEN_HANDLE,
    NODE_ANCHOR,
    NODE_CP1,
    NODE_CP2
}

@Composable
fun EditorCanvasBox(
    state: EditorUiState,
    customFontPaths: Map<String, String>,
    stylusPressureEnabled: Boolean,
    viewModel: EditorViewModel,
    onTextPlaced: () -> Unit,
    modifier: Modifier = Modifier
) {
    val doc = state.document ?: return
    val context = LocalContext.current

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var fittedForDocId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(doc.id, viewportSize) {
        if (viewportSize.width > 0 && viewportSize.height > 0 && fittedForDocId != doc.id) {
            viewModel.fitToViewport(viewportSize.width.toFloat(), viewportSize.height.toFloat())
            fittedForDocId = doc.id
        }
    }

    // Live drag preview for shape creation
    var shapeDragStart by remember { mutableStateOf<Offset?>(null) }
    var shapeDragEnd by remember { mutableStateOf<Offset?>(null) }

    val latestState by rememberUpdatedState(state)
    val latestPressureEnabled by rememberUpdatedState(stylusPressureEnabled)

    Column(modifier = modifier.background(SpiderBlack)) {
        if (state.canvasConfig.showRulers) {
            Row {
                RulerCornerBadge(
                    unit = state.canvasConfig.rulerUnit,
                    onClick = { viewModel.cycleRulerUnit() }
                )
                HorizontalRuler(
                    zoom = state.zoom,
                    panX = state.pan.x,
                    unit = state.canvasConfig.rulerUnit,
                    onTapDocumentCoord = { docX ->
                        viewModel.addGuide(GuideOrientation.VERTICAL, docX)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(modifier = Modifier.weight(1f)) {
            if (state.canvasConfig.showRulers) {
                VerticalRuler(
                    zoom = state.zoom,
                    panY = state.pan.y,
                    unit = state.canvasConfig.rulerUnit,
                    onTapDocumentCoord = { docY ->
                        viewModel.addGuide(GuideOrientation.HORIZONTAL, docY)
                    }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .testTag("editor_canvas")
                    .pointerInput(doc.id) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            val cur = latestState
                            val toDoc: (Offset) -> Offset = { screen ->
                                val z = latestState.zoom.coerceAtLeast(0.05f)
                                Offset(
                                    (screen.x - latestState.pan.x) / z,
                                    (screen.y - latestState.pan.y) / z
                                )
                            }

                            var dragMode = DragMode.NONE
                            var activeHandleIndex = -1
                            var undoRecordedForDrag = false
                            var lastDoc = toDoc(firstDown.position)
                            val startDoc = lastDoc
                            var resizeOriginEl: VectorElement? = null
                            var activeGuide: CanvasGuide? = null
                            val freehandPoints = mutableListOf<Offset>()
                            val pressures = mutableListOf<Float>()
                            var movedDistance = 0f

                            when (cur.activeTool) {
                                EditorTool.HAND -> dragMode = DragMode.PAN
                                EditorTool.RECTANGLE, EditorTool.ELLIPSE, EditorTool.POLYGON, EditorTool.STAR -> {
                                    dragMode = DragMode.DRAW_SHAPE
                                    shapeDragStart = startDoc
                                    shapeDragEnd = startDoc
                                }
                                EditorTool.PENCIL, EditorTool.BRUSH -> {
                                    dragMode = DragMode.FREEHAND
                                    freehandPoints.add(startDoc)
                                    pressures.add(if (latestPressureEnabled) firstDown.pressure else 1f)
                                    viewModel.updateFreehandPreview(freehandPoints.toList())
                                }
                                EditorTool.PEN -> {
                                    viewModel.addPenNode(startDoc)
                                    dragMode = DragMode.PEN_HANDLE
                                }
                                EditorTool.TEXT -> {
                                    viewModel.placeTextAt(startDoc)
                                    onTextPlaced()
                                }
                                EditorTool.NODE -> {
                                    val path = cur.document?.layers
                                        ?.flatMap { it.elements }
                                        ?.firstOrNull { it.id in cur.selectedElementIds } as? PathShape
                                    if (path != null) {
                                        val editable = viewModel.isElementEditable(path.id)
                                        val local = VectorMath.toLocalCoords(path, startDoc.x, startDoc.y)
                                        val tol = 28f / cur.zoom.coerceAtLeast(0.25f)
                                        val activeIdx = cur.activeNodeIndex
                                        val activeContourNodes = if (cur.activeContourIndex == 0) path.nodes
                                        else path.extraContours.getOrNull(cur.activeContourIndex - 1)?.nodes ?: emptyList()
                                        if (editable && activeIdx != null && activeIdx in activeContourNodes.indices) {
                                            val an = activeContourNodes[activeIdx]
                                            when {
                                                hypot(local.x - an.cp1X, local.y - an.cp1Y) <= tol -> dragMode = DragMode.NODE_CP1
                                                hypot(local.x - an.cp2X, local.y - an.cp2Y) <= tol -> dragMode = DragMode.NODE_CP2
                                            }
                                        }
                                        if (dragMode == DragMode.NONE) {
                                            viewModel.selectNodeAt(startDoc)
                                            if (editable) {
                                                if (latestState.activeNodeIndex != null) {
                                                    dragMode = DragMode.NODE_ANCHOR
                                                } else if (viewModel.insertNodeAtPointOnPath(startDoc)) {
                                                    dragMode = DragMode.NODE_ANCHOR
                                                }
                                            }
                                        }
                                    } else {
                                        viewModel.hitTestAndSelect(startDoc)
                                    }
                                }
                                EditorTool.SELECT -> {
                                    val selected = cur.document?.layers
                                        ?.flatMap { it.elements }
                                        ?.filter { it.id in cur.selectedElementIds }
                                        .orEmpty()
                                    if (selected.size == 1 && viewModel.isElementEditable(selected[0].id)) {
                                        val el = selected[0]
                                        val local = VectorMath.toLocalCoords(el, startDoc.x, startDoc.y)
                                        val tol = 32f / cur.zoom.coerceAtLeast(0.25f)
                                        val rotKnob = Offset(el.x + el.width / 2f, el.y - 38f / cur.zoom.coerceAtLeast(0.05f))
                                        if (hypot(local.x - rotKnob.x, local.y - rotKnob.y) <= tol) {
                                            dragMode = DragMode.ROTATE_HANDLE
                                            resizeOriginEl = el
                                        } else {
                                            val handles = listOf(
                                                Offset(el.x, el.y),
                                                Offset(el.x + el.width / 2f, el.y),
                                                Offset(el.x + el.width, el.y),
                                                Offset(el.x + el.width, el.y + el.height / 2f),
                                                Offset(el.x + el.width, el.y + el.height),
                                                Offset(el.x + el.width / 2f, el.y + el.height),
                                                Offset(el.x, el.y + el.height),
                                                Offset(el.x, el.y + el.height / 2f)
                                            )
                                            val hitIdx = handles.indexOfFirst { hypot(local.x - it.x, local.y - it.y) <= tol }
                                            if (hitIdx >= 0) {
                                                dragMode = DragMode.RESIZE_HANDLE
                                                activeHandleIndex = hitIdx
                                                resizeOriginEl = el
                                            }
                                        }
                                    }
                                    if (dragMode == DragMode.NONE) {
                                        val alreadyHit = selected.any {
                                            VectorMath.hitTestElement(it, startDoc.x, startDoc.y, 20f / cur.zoom.coerceAtLeast(0.05f))
                                        }
                                        if (!alreadyHit) {
                                            viewModel.hitTestAndSelect(startDoc)
                                        }
                                        if (latestState.selectedElementIds.any { viewModel.isElementEditable(it) }) {
                                            dragMode = DragMode.MOVE_ELEMENT
                                        } else if (cur.canvasConfig.showGuides && !cur.canvasConfig.guidesLocked) {
                                            val guideTol = 18f / cur.zoom.coerceAtLeast(0.25f)
                                            val hitGuide = cur.document?.guides?.firstOrNull { g ->
                                                when (g.orientation) {
                                                    GuideOrientation.VERTICAL -> abs(startDoc.x - g.positionPx) <= guideTol
                                                    GuideOrientation.HORIZONTAL -> abs(startDoc.y - g.positionPx) <= guideTol
                                                }
                                            }
                                            if (hitGuide != null) {
                                                activeGuide = hitGuide
                                                dragMode = DragMode.MOVE_GUIDE
                                            }
                                        }
                                    }
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2) {
                                    // Multi-touch pinch zoom + pan
                                    val zoomFactor = event.calculateZoom()
                                    val panDelta = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val oldZoom = latestState.zoom.coerceAtLeast(0.05f)
                                    val newZoom = (oldZoom * zoomFactor).coerceIn(0.1f, 16f)
                                    val newPan = centroid - ((centroid - latestState.pan) / oldZoom) * newZoom + panDelta
                                    viewModel.setZoomAndPan(newZoom, newPan)
                                    event.changes.forEach { it.consume() }
                                    dragMode = DragMode.NONE
                                    shapeDragStart = null
                                    shapeDragEnd = null
                                    if (freehandPoints.isNotEmpty()) {
                                        freehandPoints.clear()
                                        pressures.clear()
                                        viewModel.updateFreehandPreview(emptyList())
                                    }
                                    viewModel.clearSnapLines()
                                    continue
                                }

                                val change = pressed.first()
                                change.consume()
                                val pos = change.position
                                val prevPos = change.previousPosition
                                movedDistance += (pos - prevPos).getDistance()
                                val curDoc = toDoc(pos)

                                when (dragMode) {
                                    DragMode.PAN -> {
                                        val delta = pos - prevPos
                                        viewModel.setZoomAndPan(latestState.zoom, latestState.pan + delta)
                                    }
                                    DragMode.MOVE_ELEMENT -> {
                                        if (movedDistance > 4f) {
                                            if (!undoRecordedForDrag) {
                                                viewModel.pushUndoSnapshot()
                                                undoRecordedForDrag = true
                                            }
                                            val deltaDoc = curDoc - lastDoc
                                            viewModel.moveSelectedBy(deltaDoc.x, deltaDoc.y, withSnap = true)
                                        }
                                    }
                                    DragMode.MOVE_GUIDE -> {
                                        val g = activeGuide
                                        if (g != null) {
                                            if (!undoRecordedForDrag) {
                                                viewModel.pushUndoSnapshot()
                                                undoRecordedForDrag = true
                                            }
                                            val nextPos = if (g.orientation == GuideOrientation.VERTICAL) curDoc.x else curDoc.y
                                            viewModel.moveGuide(g.id, nextPos, recordUndo = false)
                                        }
                                    }
                                    DragMode.RESIZE_HANDLE -> {
                                        val orig = resizeOriginEl
                                        if (orig != null) {
                                            if (!undoRecordedForDrag) {
                                                viewModel.pushUndoSnapshot()
                                                undoRecordedForDrag = true
                                            }
                                            val local = VectorMath.toLocalCoords(orig, curDoc.x, curDoc.y)
                                            var nx = orig.x
                                            var ny = orig.y
                                            var nw = orig.width
                                            var nh = orig.height
                                            val right = orig.x + orig.width
                                            val bottom = orig.y + orig.height
                                            when (activeHandleIndex) {
                                                0 -> { nx = min(local.x, right - 16f); ny = min(local.y, bottom - 16f); nw = right - nx; nh = bottom - ny }
                                                1 -> { ny = min(local.y, bottom - 16f); nh = bottom - ny }
                                                2 -> { ny = min(local.y, bottom - 16f); nw = max(16f, local.x - orig.x); nh = bottom - ny }
                                                3 -> { nw = max(16f, local.x - orig.x) }
                                                4 -> { nw = max(16f, local.x - orig.x); nh = max(16f, local.y - orig.y) }
                                                5 -> { nh = max(16f, local.y - orig.y) }
                                                6 -> { nx = min(local.x, right - 16f); nw = right - nx; nh = max(16f, local.y - orig.y) }
                                                7 -> { nx = min(local.x, right - 16f); nw = right - nx }
                                            }
                                            viewModel.resizeSelectedElement(orig.id, nx, ny, nw, nh)
                                        }
                                    }
                                    DragMode.ROTATE_HANDLE -> {
                                        val orig = resizeOriginEl
                                        if (orig != null) {
                                            if (!undoRecordedForDrag) {
                                                viewModel.pushUndoSnapshot()
                                                undoRecordedForDrag = true
                                            }
                                            val cx = orig.x + orig.width / 2f
                                            val cy = orig.y + orig.height / 2f
                                            val deg = Math.toDegrees(atan2((curDoc.y - cy).toDouble(), (curDoc.x - cx).toDouble())).toFloat() + 90f
                                            viewModel.rotateSelectedElement(orig.id, deg)
                                        }
                                    }
                                    DragMode.DRAW_SHAPE -> {
                                        shapeDragEnd = curDoc
                                    }
                                    DragMode.FREEHAND -> {
                                        val stepDist = (curDoc - lastDoc).getDistance()
                                        if (stepDist >= 3f) {
                                            freehandPoints.add(curDoc)
                                            val rawP = change.pressure
                                            val sampleP = if (latestPressureEnabled && abs(rawP - 1f) > 0.01f) {
                                                rawP
                                            } else {
                                                // Velocity-modulated pressure fallback for touch fingers
                                                (1.25f - (stepDist / 45f)).coerceIn(0.45f, 1.35f)
                                            }
                                            pressures.add(sampleP)
                                            viewModel.updateFreehandPreview(freehandPoints.toList())
                                        }
                                    }
                                    DragMode.PEN_HANDLE -> {
                                        viewModel.updateLastPenNodeHandle(curDoc - startDoc)
                                    }
                                    DragMode.NODE_ANCHOR, DragMode.NODE_CP1, DragMode.NODE_CP2 -> {
                                        if (!undoRecordedForDrag) {
                                            viewModel.pushUndoSnapshot()
                                            undoRecordedForDrag = true
                                        }
                                        viewModel.moveNodeOrHandle(
                                            curDoc,
                                            draggingCp1 = dragMode == DragMode.NODE_CP1,
                                            draggingCp2 = dragMode == DragMode.NODE_CP2
                                        )
                                    }
                                    DragMode.NONE -> Unit
                                }
                                lastDoc = curDoc
                            }

                            // Pointer up finalization
                            viewModel.clearSnapLines()
                            if (dragMode == DragMode.DRAW_SHAPE) {
                                val s = shapeDragStart
                                val e = shapeDragEnd
                                shapeDragStart = null
                                shapeDragEnd = null
                                if (s != null && e != null) {
                                    val finalEnd = if ((e - s).getDistance() < 12f) s + Offset(220f, 220f) else e
                                    viewModel.createShapeFromDrag(latestState.activeTool, s, finalEnd)
                                }
                            } else if (dragMode == DragMode.FREEHAND) {
                                val avgP = if (pressures.isNotEmpty()) pressures.average().toFloat() else 1f
                                viewModel.commitFreehandStroke(
                                    points = freehandPoints,
                                    isBrush = latestState.activeTool == EditorTool.BRUSH,
                                    avgPressure = avgP,
                                    pointPressures = pressures.toList()
                                )
                            } else if (dragMode == DragMode.MOVE_ELEMENT && movedDistance <= 4f) {
                                // Pure tap: ensure hit test toggles/selects accurately
                                viewModel.hitTestAndSelect(startDoc)
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawStudioCanvas(
                        context = context,
                        doc = doc,
                        config = state.canvasConfig,
                        zoom = state.zoom,
                        pan = state.pan,
                        selectedIds = state.selectedElementIds,
                        activeTool = state.activeTool,
                        activeNodeIndex = state.activeNodeIndex,
                        activeContourIndex = state.activeContourIndex,
                        penInProgressNodes = state.penInProgressNodes,
                        penPreviewPoint = state.penPreviewPoint,
                        freehandPreviewPoints = state.freehandPreviewPoints,
                        activeBrushSize = state.activeBrushSize,
                        activeBrushPreset = state.activeBrushPreset,
                        activeBrushOpacity = state.activeBrushOpacity,
                        snapLines = state.snapLines,
                        customFontPaths = customFontPaths
                    )
                    // Shape creation rubber-band preview
                    val s = shapeDragStart
                    val e = shapeDragEnd
                    if (s != null && e != null) {
                        val sx = min(s.x, e.x) * state.zoom + state.pan.x
                        val sy = min(s.y, e.y) * state.zoom + state.pan.y
                        val sw = kotlin.math.abs(e.x - s.x) * state.zoom
                        val sh = kotlin.math.abs(e.y - s.y) * state.zoom
                        drawRect(
                            color = SpiderViolet.copy(alpha = 0.22f),
                            topLeft = Offset(sx, sy),
                            size = Size(sw, sh)
                        )
                        drawRect(
                            color = SpiderViolet,
                            topLeft = Offset(sx, sy),
                            size = Size(sw, sh),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}
