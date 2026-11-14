package com.example.viewmodel

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesRepository
import com.example.data.ProjectRepository
import com.example.data.SpiderDatabase
import com.example.engine.PdfImporter
import com.example.engine.SvgImporter
import com.example.engine.VectorBooleanEngine
import com.example.engine.VectorMath
import com.example.model.AlignAction
import com.example.model.AssetCatalog
import com.example.model.BezierNode
import com.example.model.BooleanOp
import com.example.model.BrushPreset
import com.example.model.CanvasGuide
import com.example.model.ChromeEffectConfig
import com.example.model.ChromeStyleType
import com.example.model.EditorCanvasConfig
import com.example.model.EditorTool
import com.example.model.EffectStyle
import com.example.model.EllipseShape
import com.example.model.GroupShape
import com.example.model.GuideOrientation
import com.example.model.ImageShape
import com.example.model.NodeHandleMode
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathContour
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.RulerUnit
import com.example.model.SnapLine
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.StrokeStyle
import com.example.model.TextAlignMode
import com.example.model.TextShape
import com.example.model.VectorElement
import com.example.model.VectorLayer
import com.example.model.ZOrderAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class EditorUiState(
    val document: SpiderDocument? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val lastSavedTimestamp: Long? = null,
    val activeTool: EditorTool = EditorTool.SELECT,
    val activeLayerId: String? = null,
    val selectedElementIds: Set<String> = emptySet(),
    val activeNodeIndex: Int? = null,
    val activeContourIndex: Int = 0,
    val penInProgressNodes: List<BezierNode> = emptyList(),
    val penPreviewPoint: Offset? = null,
    val freehandPreviewPoints: List<Offset> = emptyList(),
    val activeBrushPreset: BrushPreset = BrushPreset.ROUND_CALLIGRAPHIC,
    val activeBrushSize: Float = 16f,
    val activeBrushOpacity: Float = 1f,
    val activeBrushTaper: Boolean = true,
    val snapLines: List<SnapLine> = emptyList(),
    val canvasConfig: EditorCanvasConfig = EditorCanvasConfig(),
    val zoom: Float = 1f,
    val pan: Offset = Offset.Zero,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val canPaste: Boolean = false,
    val multiSelectMode: Boolean = false,
    val message: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SpiderDatabase.getInstance(application)
    private val projectRepository = ProjectRepository(application, database)
    private val preferencesRepository = PreferencesRepository(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _customFontPaths = MutableStateFlow<Map<String, String>>(emptyMap())
    val customFontPaths: StateFlow<Map<String, String>> = _customFontPaths.asStateFlow()

    private val undoStack = ArrayDeque<SpiderDocument>()
    private val redoStack = ArrayDeque<SpiderDocument>()
    private var clipboard: List<VectorElement> = emptyList()
    private var autoSaveJob: Job? = null
    private val ioExitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentActiveColor: Long = 0xFF8B5CF6
    private var currentStrokeColor: Long = 0xFFF4F4F8

    fun loadProject(projectId: String) {
        val current = _uiState.value
        if (current.document?.id == projectId && !current.isLoading) {
            viewModelScope.launch {
                _customFontPaths.value = projectRepository.getCustomFontMap()
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val prefs = preferencesRepository.preferencesFlow.first()
            val doc = projectRepository.loadDocument(projectId)
            val fonts = projectRepository.getCustomFontMap()
            _customFontPaths.value = fonts
            if (doc != null) {
                preferencesRepository.setLastOpenedProjectId(doc.id)
                undoStack.clear()
                redoStack.clear()
                _uiState.value = EditorUiState(
                    document = doc,
                    isLoading = false,
                    activeLayerId = doc.layers.lastOrNull()?.id,
                    canvasConfig = EditorCanvasConfig(
                        showGrid = prefs.defaultShowGrid,
                        gridSizePx = prefs.defaultGridSizePx,
                        showRulers = prefs.defaultShowRulers,
                        showGuides = true,
                        snapEnabled = prefs.defaultSnapEnabled,
                        rulerUnit = prefs.defaultRulerUnit
                    ),
                    lastSavedTimestamp = doc.updatedAt
                )
                startAutoSaveLoop(prefs.autoSaveEnabled, prefs.autoSaveIntervalSec)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "No se pudo abrir el proyecto."
                )
            }
        }
    }

    private fun startAutoSaveLoop(enabled: Boolean, intervalSec: Int) {
        autoSaveJob?.cancel()
        if (!enabled) return
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(intervalSec.coerceAtLeast(5) * 1000L)
                if (_uiState.value.hasUnsavedChanges) {
                    saveNow(silent = true)
                }
            }
        }
    }

    fun saveNow(silent: Boolean = false) {
        saveNowAndThen(silent = silent, onSaved = {})
    }

    fun saveNowAndThen(silent: Boolean = true, onSaved: () -> Unit) {
        val doc = _uiState.value.document ?: run {
            onSaved()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val saved = withContext(NonCancellable) {
                projectRepository.saveDocument(doc)
            }
            val latestDoc = _uiState.value.document
            val noNewEditsDuringSave = latestDoc == doc
            _uiState.value = _uiState.value.copy(
                document = if (noNewEditsDuringSave) saved else latestDoc,
                isSaving = false,
                hasUnsavedChanges = if (noNewEditsDuringSave) false else _uiState.value.hasUnsavedChanges,
                lastSavedTimestamp = saved.updatedAt,
                message = if (silent) _uiState.value.message else "Proyecto guardado."
            )
            onSaved()
        }
    }

    fun flushUnsavedOnExit() {
        val state = _uiState.value
        val doc = state.document ?: return
        if (!state.hasUnsavedChanges) return
        ioExitScope.launch {
            runCatching {
                projectRepository.saveDocument(doc)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun setZoomAndPan(zoom: Float, pan: Offset) {
        _uiState.value = _uiState.value.copy(
            zoom = zoom.coerceIn(0.1f, 16f),
            pan = pan
        )
    }

    fun fitToViewport(viewportW: Float, viewportH: Float) {
        val doc = _uiState.value.document ?: return
        if (viewportW <= 10f || viewportH <= 10f) return
        val pad = 72f
        val zx = (viewportW - pad) / doc.widthPx.toFloat()
        val zy = (viewportH - pad) / doc.heightPx.toFloat()
        val zoom = min(zx, zy).coerceIn(0.15f, 4f)
        val panX = (viewportW - doc.widthPx * zoom) / 2f
        val panY = (viewportH - doc.heightPx * zoom) / 2f
        _uiState.value = _uiState.value.copy(zoom = zoom, pan = Offset(panX, panY))
    }

    fun setActiveTool(tool: EditorTool) {
        if (_uiState.value.penInProgressNodes.size >= 2 && tool != EditorTool.PEN) {
            commitPenPath(closed = false)
        } else if (tool != EditorTool.PEN) {
            _uiState.value = _uiState.value.copy(penInProgressNodes = emptyList(), penPreviewPoint = null)
        }
        _uiState.value = _uiState.value.copy(
            activeTool = tool,
            activeNodeIndex = null,
            activeContourIndex = 0,
            penPreviewPoint = null
        )
    }

    fun toggleMultiSelectMode() {
        _uiState.value = _uiState.value.copy(multiSelectMode = !_uiState.value.multiSelectMode)
    }

    fun updateCanvasConfig(transform: (EditorCanvasConfig) -> EditorCanvasConfig) {
        _uiState.value = _uiState.value.copy(canvasConfig = transform(_uiState.value.canvasConfig))
    }

    fun setBrushPreset(preset: BrushPreset) {
        _uiState.value = _uiState.value.copy(activeBrushPreset = preset)
    }

    fun setBrushSize(size: Float) {
        _uiState.value = _uiState.value.copy(activeBrushSize = size.coerceIn(4f, 64f))
    }

    fun setBrushOpacity(opacity: Float) {
        _uiState.value = _uiState.value.copy(activeBrushOpacity = opacity.coerceIn(0.1f, 1f))
    }

    fun toggleBrushTaper() {
        _uiState.value = _uiState.value.copy(activeBrushTaper = !_uiState.value.activeBrushTaper)
    }

    fun cycleRulerUnit() {
        val next = when (_uiState.value.canvasConfig.rulerUnit) {
            RulerUnit.PX -> RulerUnit.DP
            RulerUnit.DP -> RulerUnit.MM
            RulerUnit.MM -> RulerUnit.PX
        }
        updateCanvasConfig { it.copy(rulerUnit = next) }
    }

    fun pushUndoSnapshot() {
        val doc = _uiState.value.document ?: return
        if (undoStack.lastOrNull() == doc) return
        undoStack.addLast(doc)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        syncHistoryFlags()
    }

    private fun applyDocumentChange(recordUndo: Boolean = true, transform: (SpiderDocument) -> SpiderDocument) {
        val doc = _uiState.value.document ?: return
        if (recordUndo) {
            undoStack.addLast(doc)
            if (undoStack.size > 50) undoStack.removeFirst()
            redoStack.clear()
        }
        val next = transform(doc)
        _uiState.value = _uiState.value.copy(
            document = next,
            hasUnsavedChanges = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    private fun syncHistoryFlags() {
        _uiState.value = _uiState.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            canPaste = clipboard.isNotEmpty()
        )
    }

    fun undo() {
        val current = _uiState.value.document ?: return
        if (undoStack.isEmpty()) return
        redoStack.addLast(current)
        val prev = undoStack.removeLast()
        _uiState.value = _uiState.value.copy(
            document = prev,
            hasUnsavedChanges = true,
            selectedElementIds = emptySet(),
            activeNodeIndex = null,
            activeContourIndex = 0,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun redo() {
        val current = _uiState.value.document ?: return
        if (redoStack.isEmpty()) return
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        _uiState.value = _uiState.value.copy(
            document = next,
            hasUnsavedChanges = true,
            selectedElementIds = emptySet(),
            activeNodeIndex = null,
            activeContourIndex = 0,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun renameDocument(newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        applyDocumentChange { it.copy(name = clean) }
    }

    fun hitTestAndSelect(docPoint: Offset) {
        val doc = _uiState.value.document ?: return
        val tol = 20f / _uiState.value.zoom.coerceAtLeast(0.25f)
        val hit = doc.layers
            .asReversed()
            .filter { it.isVisible && !it.isLocked }
            .firstNotNullOfOrNull { layer ->
                layer.elements.asReversed().firstOrNull { el ->
                    !el.isLocked && VectorMath.hitTestElement(el, docPoint.x, docPoint.y, tol)
                }?.let { layer.id to it }
            }

        if (hit == null) {
            if (!_uiState.value.multiSelectMode) {
                _uiState.value = _uiState.value.copy(
                    selectedElementIds = emptySet(),
                    activeNodeIndex = null,
                    activeContourIndex = 0
                )
            }
            return
        }

        val (layerId, el) = hit
        val nextSel = if (_uiState.value.multiSelectMode) {
            if (el.id in _uiState.value.selectedElementIds) _uiState.value.selectedElementIds - el.id
            else _uiState.value.selectedElementIds + el.id
        } else {
            setOf(el.id)
        }
        _uiState.value = _uiState.value.copy(
            activeLayerId = layerId,
            selectedElementIds = nextSel,
            activeNodeIndex = null,
            activeContourIndex = 0
        )
    }

    fun selectElementById(layerId: String, elementId: String) {
        _uiState.value = _uiState.value.copy(
            activeLayerId = layerId,
            selectedElementIds = setOf(elementId),
            activeNodeIndex = null,
            activeContourIndex = 0
        )
    }

    fun getSelectedElements(): List<VectorElement> {
        val doc = _uiState.value.document ?: return emptyList()
        val ids = _uiState.value.selectedElementIds
        return doc.layers.flatMap { it.elements }.filter { it.id in ids }
    }

    fun isElementEditable(elementId: String): Boolean {
        val doc = _uiState.value.document ?: return false
        val layer = doc.layers.firstOrNull { l -> l.elements.any { it.id == elementId } } ?: return false
        if (layer.isLocked) return false
        val el = layer.elements.firstOrNull { it.id == elementId } ?: return false
        return !el.isLocked
    }

    private fun resolveUnlockedActiveLayer(): VectorLayer? {
        val doc = _uiState.value.document ?: return null
        val targetLayerId = _uiState.value.activeLayerId ?: doc.layers.lastOrNull()?.id ?: return null
        val layer = doc.layers.firstOrNull { it.id == targetLayerId } ?: return null
        if (layer.isLocked) {
            _uiState.value = _uiState.value.copy(
                message = "La capa '${layer.name}' está bloqueada. Desbloquéala para editar."
            )
            return null
        }
        return layer
    }

    fun addElementToActiveLayer(element: VectorElement): Boolean {
        val targetLayer = resolveUnlockedActiveLayer() ?: return false
        applyDocumentChange { d ->
            val layers = d.layers.map { l ->
                if (l.id == targetLayer.id) l.copy(elements = l.elements + element) else l
            }
            d.copy(layers = layers)
        }
        _uiState.value = _uiState.value.copy(
            selectedElementIds = setOf(element.id),
            activeLayerId = targetLayer.id
        )
        return true
    }

    fun createShapeFromDrag(tool: EditorTool, start: Offset, end: Offset) {
        val x = min(start.x, end.x)
        val y = min(start.y, end.y)
        val w = max(48f, kotlin.math.abs(end.x - start.x))
        val h = max(48f, kotlin.math.abs(end.y - start.y))
        val fill = PaintStyle(type = PaintType.SOLID, colorArgb = currentActiveColor)
        val stroke = StrokeStyle(enabled = false, colorArgb = currentStrokeColor, width = 4f)
        val element: VectorElement = when (tool) {
            EditorTool.RECTANGLE -> RectShape(x = x, y = y, width = w, height = h, cornerRadius = 16f, fill = fill, stroke = stroke)
            EditorTool.ELLIPSE -> EllipseShape(x = x, y = y, width = w, height = h, fill = fill, stroke = stroke)
            EditorTool.POLYGON -> PolygonShape(x = x, y = y, width = w, height = h, sides = 6, fill = fill, stroke = stroke)
            EditorTool.STAR -> StarShape(x = x, y = y, width = w, height = h, points = 5, innerRadiusRatio = 0.45f, fill = fill, stroke = stroke)
            else -> return
        }
        addElementToActiveLayer(element)
        _uiState.value = _uiState.value.copy(activeTool = EditorTool.SELECT)
    }

    fun placeTextAt(pos: Offset, initialText: String = "Nuevo texto") {
        val textEl = TextShape(
            x = pos.x,
            y = pos.y,
            width = 440f,
            height = 96f,
            text = initialText,
            fontFamilyKey = "space_grotesk",
            fontSize = 56f,
            isBold = true,
            alignment = TextAlignMode.LEFT,
            fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFFF4F4F8)
        )
        addElementToActiveLayer(textEl)
        _uiState.value = _uiState.value.copy(activeTool = EditorTool.SELECT)
    }

    fun insertAsset(assetId: String) {
        val doc = _uiState.value.document ?: return
        val asset = AssetCatalog.assets.firstOrNull { it.id == assetId } ?: return
        val el = asset.createElement(doc.widthPx / 2f, doc.heightPx / 2f)
        addElementToActiveLayer(el)
        _uiState.value = _uiState.value.copy(activeTool = EditorTool.SELECT, message = "Recurso '${asset.title}' añadido.")
    }

    // --- Pen & Freehand ---
    fun addPenNode(pos: Offset, handleDelta: Offset = Offset.Zero) {
        val current = _uiState.value.penInProgressNodes
        if (current.size >= 3) {
            val first = current.first()
            val dist = hypot(pos.x - first.x, pos.y - first.y)
            if (dist <= 28f / _uiState.value.zoom.coerceAtLeast(0.25f)) {
                commitPenPath(closed = true)
                return
            }
        }
        val smooth = handleDelta.getDistance() > 6f
        val node = BezierNode(
            x = pos.x,
            y = pos.y,
            cp1X = pos.x - handleDelta.x,
            cp1Y = pos.y - handleDelta.y,
            cp2X = pos.x + handleDelta.x,
            cp2Y = pos.y + handleDelta.y,
            smooth = smooth,
            handleMode = if (smooth) NodeHandleMode.SYMMETRIC else NodeHandleMode.CORNER
        )
        _uiState.value = _uiState.value.copy(
            penInProgressNodes = current + node,
            penPreviewPoint = pos
        )
    }

    fun updateLastPenNodeHandle(handleDelta: Offset) {
        val current = _uiState.value.penInProgressNodes
        if (current.isEmpty()) return
        val last = current.last()
        val isSmooth = handleDelta.getDistance() > 4f
        val updated = last.copy(
            cp1X = last.x - handleDelta.x,
            cp1Y = last.y - handleDelta.y,
            cp2X = last.x + handleDelta.x,
            cp2Y = last.y + handleDelta.y,
            smooth = isSmooth,
            handleMode = if (isSmooth) NodeHandleMode.SYMMETRIC else NodeHandleMode.CORNER
        )
        _uiState.value = _uiState.value.copy(
            penInProgressNodes = current.dropLast(1) + updated,
            penPreviewPoint = Offset(last.x + handleDelta.x, last.y + handleDelta.y)
        )
    }

    fun undoLastPenNode() {
        val current = _uiState.value.penInProgressNodes
        if (current.isEmpty()) return
        val next = current.dropLast(1)
        _uiState.value = _uiState.value.copy(
            penInProgressNodes = next,
            penPreviewPoint = next.lastOrNull()?.let { Offset(it.x, it.y) }
        )
    }

    fun updatePenPreviewPoint(pos: Offset?) {
        _uiState.value = _uiState.value.copy(penPreviewPoint = pos)
    }

    fun commitPenPath(closed: Boolean) {
        val nodes = _uiState.value.penInProgressNodes
        if (nodes.size < 2) {
            _uiState.value = _uiState.value.copy(penInProgressNodes = emptyList(), penPreviewPoint = null)
            return
        }
        val path = VectorBooleanEngine.recalculateCompoundBounds(
            PathShape(
                name = if (closed) "Forma Bézier" else "Trazado Pluma",
                x = 0f, y = 0f, width = 10f, height = 10f,
                nodes = nodes,
                isClosed = closed,
                fill = if (closed) PaintStyle(type = PaintType.SOLID, colorArgb = currentActiveColor)
                else PaintStyle(type = PaintType.NONE),
                stroke = StrokeStyle(enabled = true, colorArgb = currentStrokeColor, width = 6f)
            )
        )
        _uiState.value = _uiState.value.copy(penInProgressNodes = emptyList(), penPreviewPoint = null)
        addElementToActiveLayer(path)
    }

    fun updateFreehandPreview(points: List<Offset>) {
        _uiState.value = _uiState.value.copy(freehandPreviewPoints = points)
    }

    fun commitFreehandStroke(
        points: List<Offset>,
        isBrush: Boolean,
        avgPressure: Float = 1f,
        pointPressures: List<Float> = emptyList()
    ) {
        _uiState.value = _uiState.value.copy(freehandPreviewPoints = emptyList())
        if (points.size < 2) return
        val nodes = VectorMath.pointsToSmoothPathNodes(points, epsilon = if (isBrush) 7f else 5f)
        if (nodes.size < 2) return
        val state = _uiState.value
        val strokeW = if (isBrush) state.activeBrushSize else 5f
        val strokeOpacity = if (isBrush) state.activeBrushOpacity else 1f
        val nodePressures: List<Float> = if (isBrush) {
            if (pointPressures.size >= 2) {
                List(nodes.size) { idx ->
                    val t = if (nodes.size > 1) idx.toFloat() / (nodes.size - 1) else 0f
                    val srcPos = t * (pointPressures.size - 1)
                    val lo = srcPos.toInt().coerceIn(0, pointPressures.lastIndex)
                    val hi = (lo + 1).coerceIn(0, pointPressures.lastIndex)
                    val frac = srcPos - lo
                    val p = pointPressures[lo] + (pointPressures[hi] - pointPressures[lo]) * frac
                    p.coerceIn(0.25f, 1.8f)
                }
            } else {
                List(nodes.size) { avgPressure.coerceIn(0.4f, 1.8f) }
            }
        } else emptyList()

        val preset = state.activeBrushPreset
        val path = VectorMath.recalculatePathBounds(
            PathShape(
                name = if (isBrush) "Pincel ${preset.label}" else "Dibujo Lápiz",
                x = 0f, y = 0f, width = 10f, height = 10f,
                nodes = nodes,
                isClosed = false,
                isBrushStroke = isBrush,
                pressureWidths = nodePressures,
                brushPreset = preset,
                brushTaperEnds = if (isBrush) state.activeBrushTaper else false,
                fill = PaintStyle(type = PaintType.NONE),
                stroke = StrokeStyle(
                    enabled = true,
                    colorArgb = currentActiveColor,
                    width = strokeW,
                    opacity = strokeOpacity,
                    dashed = isBrush && preset == BrushPreset.ARTISTIC_DASH
                )
            )
        )
        addElementToActiveLayer(path)
    }

    // --- Interactive Transform / Dragging ---
    fun moveSelectedBy(dx: Float, dy: Float, withSnap: Boolean) {
        val doc = _uiState.value.document ?: return
        val selected = getSelectedElements()
        if (selected.isEmpty()) return

        var adjDx = dx
        var adjDy = dy
        var snapLines = emptyList<SnapLine>()
        val cfg = _uiState.value.canvasConfig

        if (withSnap && selected.size == 1 && cfg.snapEnabled) {
            val el = selected[0]
            val snap = VectorMath.computeSnap(
                targetX = el.x + dx,
                targetY = el.y + dy,
                targetW = el.width,
                targetH = el.height,
                doc = doc,
                excludeIds = _uiState.value.selectedElementIds,
                gridSizePx = cfg.gridSizePx,
                snapEnabled = true,
                threshold = 12f / _uiState.value.zoom.coerceAtLeast(0.25f),
                snapToGrid = cfg.snapToGrid,
                snapToObjects = cfg.snapToObjects,
                snapToGuides = cfg.snapToGuides
            )
            adjDx = snap.x - el.x
            adjDy = snap.y - el.y
            snapLines = snap.lines
        }

        applyDocumentChange(recordUndo = false) { d ->
            d.copy(layers = d.layers.map { layer ->
                if (layer.isLocked) layer
                else layer.copy(elements = layer.elements.map { el ->
                    if (el.id in _uiState.value.selectedElementIds && !el.isLocked) {
                        VectorMath.moveElementBy(el, adjDx, adjDy)
                    } else el
                })
            })
        }
        _uiState.value = _uiState.value.copy(snapLines = snapLines)
    }

    fun clearSnapLines() {
        if (_uiState.value.snapLines.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(snapLines = emptyList())
        }
    }

    fun resizeSelectedElement(id: String, newX: Float, newY: Float, newW: Float, newH: Float) {
        if (!isElementEditable(id)) return
        applyDocumentChange(recordUndo = false) { d ->
            d.copy(layers = d.layers.map { layer ->
                if (layer.isLocked) layer
                else layer.copy(elements = layer.elements.map { el ->
                    if (el.id == id && !el.isLocked) VectorMath.scaleElementToBounds(el, newX, newY, newW, newH)
                    else el
                })
            })
        }
    }

    fun rotateSelectedElement(id: String, newRotationDeg: Float) {
        if (!isElementEditable(id)) return
        applyDocumentChange(recordUndo = false) { d ->
            d.copy(layers = d.layers.map { layer ->
                if (layer.isLocked) layer
                else layer.copy(elements = layer.elements.map { el ->
                    if (el.id == id && !el.isLocked) VectorMath.updateElementTransform(el, rotationDeg = newRotationDeg)
                    else el
                })
            })
        }
    }

    // --- Node Editing & Advanced Vector Engine ---
    fun getActiveNode(): BezierNode? {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return null
        val idx = _uiState.value.activeNodeIndex ?: return null
        return getContourNodes(path, _uiState.value.activeContourIndex).getOrNull(idx)
    }

    private fun getContourNodes(path: PathShape, contourIdx: Int): List<BezierNode> {
        return if (contourIdx == 0) path.nodes
        else path.extraContours.getOrNull(contourIdx - 1)?.nodes ?: emptyList()
    }

    private fun getContourClosed(path: PathShape, contourIdx: Int): Boolean {
        return if (contourIdx == 0) path.isClosed
        else path.extraContours.getOrNull(contourIdx - 1)?.isClosed ?: true
    }

    private fun replaceContour(
        path: PathShape,
        contourIdx: Int,
        nodes: List<BezierNode>,
        isClosed: Boolean = getContourClosed(path, contourIdx)
    ): PathShape {
        val next = if (contourIdx == 0) {
            path.copy(nodes = nodes, isClosed = isClosed)
        } else {
            val extraIdx = contourIdx - 1
            if (extraIdx !in path.extraContours.indices) path
            else {
                val updatedExtras = path.extraContours.toMutableList()
                updatedExtras[extraIdx] = updatedExtras[extraIdx].copy(nodes = nodes, isClosed = isClosed)
                path.copy(extraContours = updatedExtras)
            }
        }
        return VectorBooleanEngine.recalculateCompoundBounds(next)
    }

    fun selectNodeAt(docPoint: Offset) {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        val local = VectorMath.toLocalCoords(path, docPoint.x, docPoint.y)
        val tol = 28f / _uiState.value.zoom.coerceAtLeast(0.25f)
        val allContours = listOf(path.nodes) + path.extraContours.map { it.nodes }
        for (cIdx in allContours.indices) {
            val nIdx = allContours[cIdx].indexOfFirst { n ->
                hypot(local.x - n.x, local.y - n.y) <= tol
            }
            if (nIdx >= 0) {
                _uiState.value = _uiState.value.copy(activeContourIndex = cIdx, activeNodeIndex = nIdx)
                return
            }
        }
        _uiState.value = _uiState.value.copy(activeNodeIndex = null)
    }

    fun insertNodeAtPointOnPath(docPoint: Offset): Boolean {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return false
        if (!isElementEditable(path.id)) {
            _uiState.value = _uiState.value.copy(message = "El trazado o su capa están bloqueados.")
            return false
        }
        val local = VectorMath.toLocalCoords(path, docPoint.x, docPoint.y)
        val tol = 24f / _uiState.value.zoom.coerceAtLeast(0.25f)

        val primaryHit = VectorBooleanEngine.findClosestSegmentOnPrimary(path, local)
        if (primaryHit != null && primaryHit.distance <= tol) {
            pushUndoSnapshot()
            val (updatedPath, insertedIdx) = VectorBooleanEngine.insertNodeAtSegment(path, primaryHit.segmentIndex, primaryHit.t)
            replaceElement(updatedPath, recordUndo = false)
            _uiState.value = _uiState.value.copy(activeContourIndex = 0, activeNodeIndex = insertedIdx)
            return true
        }

        // Check extraContours too
        path.extraContours.forEachIndexed { eIdx, contour ->
            val temp = path.copy(nodes = contour.nodes, isClosed = contour.isClosed, extraContours = emptyList())
            val hit = VectorBooleanEngine.findClosestSegmentOnPrimary(temp, local)
            if (hit != null && hit.distance <= tol) {
                pushUndoSnapshot()
                val (splitTemp, insertedIdx) = VectorBooleanEngine.insertNodeAtSegment(temp, hit.segmentIndex, hit.t)
                val updatedPath = replaceContour(path, eIdx + 1, splitTemp.nodes, splitTemp.isClosed)
                replaceElement(updatedPath, recordUndo = false)
                _uiState.value = _uiState.value.copy(activeContourIndex = eIdx + 1, activeNodeIndex = insertedIdx)
                return true
            }
        }
        return false
    }

    fun moveNodeOrHandle(docPoint: Offset, draggingCp1: Boolean = false, draggingCp2: Boolean = false) {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        if (!isElementEditable(path.id)) return
        val contourIdx = _uiState.value.activeContourIndex
        val contourNodes = getContourNodes(path, contourIdx)
        val idx = _uiState.value.activeNodeIndex ?: return
        if (idx !in contourNodes.indices) return
        val local = VectorMath.toLocalCoords(path, docPoint.x, docPoint.y)
        val n = contourNodes[idx]
        val mode = if (n.handleMode == NodeHandleMode.CORNER && n.smooth) NodeHandleMode.SMOOTH else n.handleMode

        val updatedNode = when {
            draggingCp1 -> {
                val dx = local.x - n.x
                val dy = local.y - n.y
                val dragDist = hypot(dx, dy).coerceAtLeast(0.001f)
                when (mode) {
                    NodeHandleMode.CORNER -> n.copy(cp1X = local.x, cp1Y = local.y, smooth = false)
                    NodeHandleMode.SYMMETRIC -> n.copy(
                        cp1X = local.x, cp1Y = local.y,
                        cp2X = n.x - dx, cp2Y = n.y - dy,
                        smooth = true
                    )
                    NodeHandleMode.SMOOTH -> {
                        val oppDist = hypot(n.cp2X - n.x, n.cp2Y - n.y).let { if (it < 1f) dragDist else it }
                        val ux = -dx / dragDist
                        val uy = -dy / dragDist
                        n.copy(
                            cp1X = local.x, cp1Y = local.y,
                            cp2X = n.x + ux * oppDist, cp2Y = n.y + uy * oppDist,
                            smooth = true
                        )
                    }
                }
            }
            draggingCp2 -> {
                val dx = local.x - n.x
                val dy = local.y - n.y
                val dragDist = hypot(dx, dy).coerceAtLeast(0.001f)
                when (mode) {
                    NodeHandleMode.CORNER -> n.copy(cp2X = local.x, cp2Y = local.y, smooth = false)
                    NodeHandleMode.SYMMETRIC -> n.copy(
                        cp2X = local.x, cp2Y = local.y,
                        cp1X = n.x - dx, cp1Y = n.y - dy,
                        smooth = true
                    )
                    NodeHandleMode.SMOOTH -> {
                        val oppDist = hypot(n.cp1X - n.x, n.cp1Y - n.y).let { if (it < 1f) dragDist else it }
                        val ux = -dx / dragDist
                        val uy = -dy / dragDist
                        n.copy(
                            cp2X = local.x, cp2Y = local.y,
                            cp1X = n.x + ux * oppDist, cp1Y = n.y + uy * oppDist,
                            smooth = true
                        )
                    }
                }
            }
            else -> {
                val dx = local.x - n.x
                val dy = local.y - n.y
                n.copy(
                    x = local.x, y = local.y,
                    cp1X = n.cp1X + dx, cp1Y = n.cp1Y + dy,
                    cp2X = n.cp2X + dx, cp2Y = n.cp2Y + dy
                )
            }
        }
        val nextList = contourNodes.toMutableList().apply { this[idx] = updatedNode }
        replaceElement(replaceContour(path, contourIdx, nextList), recordUndo = false)
    }

    fun setActiveNodeHandleMode(mode: NodeHandleMode) {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        if (!isElementEditable(path.id)) {
            _uiState.value = _uiState.value.copy(message = "El trazado o su capa están bloqueados.")
            return
        }
        val contourIdx = _uiState.value.activeContourIndex
        val contourNodes = getContourNodes(path, contourIdx)
        val idx = _uiState.value.activeNodeIndex ?: return
        val n = contourNodes.getOrNull(idx) ?: return

        val prev = contourNodes.getOrNull((idx - 1 + contourNodes.size) % contourNodes.size)
        val next = contourNodes.getOrNull((idx + 1) % contourNodes.size)
        var tx = (next?.x ?: (n.x + 40f)) - (prev?.x ?: (n.x - 40f))
        var ty = (next?.y ?: n.y) - (prev?.y ?: n.y)
        val tLen = hypot(tx, ty).coerceAtLeast(1f)
        tx /= tLen
        ty /= tLen

        val d1 = hypot(n.cp1X - n.x, n.cp1Y - n.y)
        val d2 = hypot(n.cp2X - n.x, n.cp2Y - n.y)
        val defaultLen = max(32f, min(60f, max(d1, d2).let { if (it < 4f) 42f else it }))

        val nextNode = when (mode) {
            NodeHandleMode.CORNER -> n.copy(
                cp1X = n.x, cp1Y = n.y, cp2X = n.x, cp2Y = n.y,
                smooth = false, handleMode = NodeHandleMode.CORNER
            )
            NodeHandleMode.SMOOTH -> {
                val len1 = if (d1 >= 4f) d1 else defaultLen
                val len2 = if (d2 >= 4f) d2 else defaultLen
                n.copy(
                    cp1X = n.x - tx * len1, cp1Y = n.y - ty * len1,
                    cp2X = n.x + tx * len2, cp2Y = n.y + ty * len2,
                    smooth = true, handleMode = NodeHandleMode.SMOOTH
                )
            }
            NodeHandleMode.SYMMETRIC -> {
                val symLen = if (d1 >= 4f || d2 >= 4f) max(4f, (d1 + d2) * 0.5f) else defaultLen
                n.copy(
                    cp1X = n.x - tx * symLen, cp1Y = n.y - ty * symLen,
                    cp2X = n.x + tx * symLen, cp2Y = n.y + ty * symLen,
                    smooth = true, handleMode = NodeHandleMode.SYMMETRIC
                )
            }
        }
        val nextList = contourNodes.toMutableList().apply { this[idx] = nextNode }
        replaceElement(replaceContour(path, contourIdx, nextList))
    }

    fun toggleActiveNodeSmooth() {
        val active = getActiveNode() ?: return
        val nextMode = when (active.handleMode) {
            NodeHandleMode.CORNER -> if (active.smooth) NodeHandleMode.SYMMETRIC else NodeHandleMode.SMOOTH
            NodeHandleMode.SMOOTH -> NodeHandleMode.SYMMETRIC
            NodeHandleMode.SYMMETRIC -> NodeHandleMode.CORNER
        }
        setActiveNodeHandleMode(nextMode)
    }

    fun addMidpointNodeToPath() {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        if (!isElementEditable(path.id)) {
            _uiState.value = _uiState.value.copy(message = "El trazado o su capa están bloqueados.")
            return
        }
        val contourIdx = _uiState.value.activeContourIndex
        val contourNodes = getContourNodes(path, contourIdx)
        if (contourNodes.size < 2) return
        val idx = (_uiState.value.activeNodeIndex ?: 0).coerceIn(0, contourNodes.lastIndex)
        val isClosed = getContourClosed(path, contourIdx)
        val segIdx = if (!isClosed && idx == contourNodes.lastIndex) idx - 1 else idx
        val nextIdx = (segIdx + 1) % contourNodes.size
        val split = VectorBooleanEngine.splitCubicAt(contourNodes[segIdx], contourNodes[nextIdx], 0.5f)
        val nextList = contourNodes.toMutableList().apply {
            this[segIdx] = split.leftStart
            this[nextIdx] = split.rightEnd
            add(segIdx + 1, split.midNode)
        }
        replaceElement(replaceContour(path, contourIdx, nextList, isClosed))
        _uiState.value = _uiState.value.copy(activeNodeIndex = segIdx + 1)
    }

    fun deleteActiveNode() {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        if (!isElementEditable(path.id)) {
            _uiState.value = _uiState.value.copy(message = "El trazado o su capa están bloqueados.")
            return
        }
        val contourIdx = _uiState.value.activeContourIndex
        val contourNodes = getContourNodes(path, contourIdx)
        val idx = _uiState.value.activeNodeIndex ?: return
        if (contourNodes.size <= 2) return
        val nextList = contourNodes.toMutableList().apply { removeAt(idx) }
        replaceElement(replaceContour(path, contourIdx, nextList))
        _uiState.value = _uiState.value.copy(activeNodeIndex = (idx - 1).coerceAtLeast(0))
    }

    fun toggleSelectedPathClosed() {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        if (!isElementEditable(path.id)) {
            _uiState.value = _uiState.value.copy(message = "El trazado o su capa están bloqueados.")
            return
        }
        val contourIdx = _uiState.value.activeContourIndex
        val closed = getContourClosed(path, contourIdx)
        replaceElement(replaceContour(path, contourIdx, getContourNodes(path, contourIdx), !closed))
    }

    fun convertSelectedToPath() {
        val selected = getSelectedElements().filter { isElementEditable(it.id) }
        if (selected.isEmpty()) {
            if (_uiState.value.selectedElementIds.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(message = "Desbloquea la capa u objeto para convertir a curvas.")
            }
            return
        }
        val editableIds = selected.map { it.id }.toSet()
        val convertedIds = mutableSetOf<String>()
        var changed = false
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else l.copy(elements = l.elements.map { el ->
                    if (el.id in editableIds && el !is PathShape) {
                        val path = VectorBooleanEngine.elementToPath(el)
                        if (path != null) {
                            changed = true
                            convertedIds.add(path.id)
                            path
                        } else el
                    } else el
                })
            })
        }
        if (changed) {
            _uiState.value = _uiState.value.copy(
                activeTool = if (convertedIds.size == 1) EditorTool.NODE else _uiState.value.activeTool,
                activeNodeIndex = if (convertedIds.size == 1) 0 else null,
                activeContourIndex = 0,
                message = "Forma convertida a curvas Bézier editables."
            )
        }
    }

    fun applyBooleanOperation(op: BooleanOp) {
        val doc = _uiState.value.document ?: return
        val selected = getSelectedElements().filter {
            isElementEditable(it.id) &&
                (it is RectShape || it is EllipseShape || it is PolygonShape || it is StarShape || it is PathShape)
        }
        if (selected.size < 2) {
            _uiState.value = _uiState.value.copy(
                message = "Selecciona al menos 2 formas vectoriales desbloqueadas para combinar."
            )
            return
        }
        val result = VectorBooleanEngine.applyBooleanOperation(selected, op)
        if (result == null) {
            _uiState.value = _uiState.value.copy(message = "La operación '${op.label}' no produjo un trazado válido.")
            return
        }
        val selectedIds = selected.map { it.id }.toSet()
        val targetLayerId = doc.layers.lastOrNull { l -> !l.isLocked && l.elements.any { it.id in selectedIds } }?.id
            ?: _uiState.value.activeLayerId
            ?: doc.layers.last().id

        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else {
                    val remaining = l.elements.filterNot { it.id in selectedIds }
                    if (l.id == targetLayerId) l.copy(elements = remaining + result)
                    else l.copy(elements = remaining)
                }
            })
        }
        _uiState.value = _uiState.value.copy(
            activeLayerId = targetLayerId,
            selectedElementIds = setOf(result.id),
            activeNodeIndex = null,
            activeContourIndex = 0,
            message = "${op.label} aplicada correctamente."
        )
    }

    fun reverseSelectedPath() {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        if (!isElementEditable(path.id)) {
            _uiState.value = _uiState.value.copy(message = "El trazado o su capa están bloqueados.")
            return
        }
        replaceElement(VectorBooleanEngine.reversePath(path))
        _uiState.value = _uiState.value.copy(message = "Dirección del trazado invertida.")
    }

    fun simplifySelectedPath() {
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        if (!isElementEditable(path.id)) {
            _uiState.value = _uiState.value.copy(message = "El trazado o su capa están bloqueados.")
            return
        }
        val simplified = VectorBooleanEngine.simplifyPath(path)
        replaceElement(simplified)
        _uiState.value = _uiState.value.copy(
            activeNodeIndex = null,
            activeContourIndex = 0,
            message = "Trazado simplificado (${simplified.nodes.size} nodos)."
        )
    }

    fun breakApartSelectedPath() {
        val doc = _uiState.value.document ?: return
        val path = getSelectedElements().singleOrNull() as? PathShape ?: return
        if (!isElementEditable(path.id)) {
            _uiState.value = _uiState.value.copy(message = "El trazado o su capa están bloqueados.")
            return
        }
        if (path.extraContours.isEmpty()) return
        val parts = VectorBooleanEngine.breakApartContours(path)
        val targetLayerId = doc.layers.firstOrNull { l -> !l.isLocked && l.elements.any { it.id == path.id } }?.id ?: return
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.id != targetLayerId) l
                else {
                    val nextElems = l.elements.flatMap { el ->
                        if (el.id == path.id) parts else listOf(el)
                    }
                    l.copy(elements = nextElems)
                }
            })
        }
        _uiState.value = _uiState.value.copy(
            selectedElementIds = parts.map { it.id }.toSet(),
            activeNodeIndex = null,
            activeContourIndex = 0,
            message = "Contorno separado en ${parts.size} trazados independientes."
        )
    }

    // --- Element Editing, Clipboard, Grouping, Alignment ---
    fun replaceElement(updated: VectorElement, recordUndo: Boolean = true) {
        if (!isElementEditable(updated.id)) return
        applyDocumentChange(recordUndo = recordUndo) { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else l.copy(elements = l.elements.map { el -> if (el.id == updated.id && !el.isLocked) updated else el })
            })
        }
    }

    fun updateSelectedElements(recordUndo: Boolean = true, updater: (VectorElement) -> VectorElement) {
        val ids = _uiState.value.selectedElementIds
        if (ids.isEmpty()) return
        val editableIds = ids.filter { isElementEditable(it) }.toSet()
        if (editableIds.isEmpty()) {
            if (recordUndo) {
                _uiState.value = _uiState.value.copy(message = "El objeto o su capa están bloqueados.")
            }
            return
        }
        applyDocumentChange(recordUndo = recordUndo) { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else l.copy(elements = l.elements.map { el ->
                    if (el.id in editableIds) {
                        val next = updater(el)
                        if (next.fill.type != PaintType.NONE) currentActiveColor = next.fill.colorArgb
                        if (next.stroke.enabled) currentStrokeColor = next.stroke.colorArgb
                        next
                    } else el
                })
            })
        }
    }

    fun recordColorInHistory(colorArgb: Long) {
        currentActiveColor = colorArgb
        viewModelScope.launch {
            preferencesRepository.pushColorToHistory(colorArgb)
        }
    }

    fun applyChromePreset(styleType: ChromeStyleType) {
        val preset = styleType.defaultConfig()
        updateSelectedElements(recordUndo = true) { el ->
            withUpdatedEffects(el, el.effects.copy(chrome = preset))
        }
    }

    fun applyCustomChromeStyle(config: ChromeEffectConfig) {
        val enabledConfig = config.copy(enabled = true)
        updateSelectedElements(recordUndo = true) { el ->
            withUpdatedEffects(el, el.effects.copy(chrome = enabledConfig))
        }
    }

    fun updateChromeEffect(recordUndo: Boolean = false, updater: (ChromeEffectConfig) -> ChromeEffectConfig) {
        updateSelectedElements(recordUndo = recordUndo) { el ->
            val currentChrome = el.effects.chrome
            val nextChrome = updater(currentChrome)
            withUpdatedEffects(el, el.effects.copy(chrome = nextChrome))
        }
    }

    fun disableChromeEffect() {
        updateSelectedElements(recordUndo = true) { el ->
            withUpdatedEffects(el, el.effects.copy(chrome = el.effects.chrome.copy(enabled = false)))
        }
    }

    private fun withUpdatedEffects(el: VectorElement, effects: EffectStyle): VectorElement = when (el) {
        is RectShape -> el.copy(effects = effects)
        is EllipseShape -> el.copy(effects = effects)
        is PolygonShape -> el.copy(effects = effects)
        is StarShape -> el.copy(effects = effects)
        is PathShape -> el.copy(effects = effects)
        is TextShape -> el.copy(effects = effects)
        is ImageShape -> el.copy(effects = effects)
        is GroupShape -> el.copy(effects = effects)
    }

    fun duplicateSelected() {
        val targetLayer = resolveUnlockedActiveLayer() ?: return
        val selected = getSelectedElements()
        if (selected.isEmpty()) return
        val clones = selected.map { cloneWithNewIds(VectorMath.moveElementBy(it, 32f, 32f)) }
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.id == targetLayer.id) l.copy(elements = l.elements + clones) else l
            })
        }
        _uiState.value = _uiState.value.copy(selectedElementIds = clones.map { it.id }.toSet())
    }

    fun copySelected() {
        val selected = getSelectedElements()
        if (selected.isNotEmpty()) {
            clipboard = selected
            syncHistoryFlags()
            _uiState.value = _uiState.value.copy(message = "${selected.size} objeto(s) copiado(s).")
        }
    }

    fun pasteClipboard() {
        if (clipboard.isEmpty()) return
        val targetLayer = resolveUnlockedActiveLayer() ?: return
        val clones = clipboard.map { cloneWithNewIds(VectorMath.moveElementBy(it, 36f, 36f)) }
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.id == targetLayer.id) l.copy(elements = l.elements + clones) else l
            })
        }
        _uiState.value = _uiState.value.copy(selectedElementIds = clones.map { it.id }.toSet())
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedElementIds
        if (ids.isEmpty()) return
        val deletableIds = ids.filter { isElementEditable(it) }.toSet()
        if (deletableIds.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "No se puede eliminar: capa u objeto bloqueado.")
            return
        }
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else l.copy(elements = l.elements.filterNot { it.id in deletableIds })
            })
        }
        val remainingIds = ids - deletableIds
        _uiState.value = _uiState.value.copy(
            selectedElementIds = remainingIds,
            activeNodeIndex = null,
            activeContourIndex = 0,
            message = if (remainingIds.isNotEmpty()) "Algunos objetos bloqueados no se eliminaron." else _uiState.value.message
        )
    }

    fun groupSelected() {
        val doc = _uiState.value.document ?: return
        val ids = _uiState.value.selectedElementIds
        if (ids.size < 2) return
        val editableChildren = doc.layers.filter { !it.isLocked }
            .flatMap { it.elements }
            .filter { it.id in ids && !it.isLocked }
        if (editableChildren.size < 2) {
            _uiState.value = _uiState.value.copy(message = "Desbloquea la capa y al menos 2 objetos para agrupar.")
            return
        }
        val editableIds = editableChildren.map { it.id }.toSet()
        val targetLayer = doc.layers.firstOrNull { !it.isLocked && it.elements.any { el -> el.id in editableIds } } ?: return
        val bounds = VectorMath.computeCombinedBounds(editableChildren) ?: return
        val group = GroupShape(
            name = "Grupo (${editableChildren.size})",
            x = bounds.left,
            y = bounds.top,
            width = max(12f, bounds.width),
            height = max(12f, bounds.height),
            children = editableChildren
        )
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else {
                    val filtered = l.elements.filterNot { it.id in editableIds }
                    if (l.id == targetLayer.id) l.copy(elements = filtered + group)
                    else l.copy(elements = filtered)
                }
            })
        }
        _uiState.value = _uiState.value.copy(selectedElementIds = setOf(group.id))
    }

    fun ungroupSelected() {
        val groups = getSelectedElements().filterIsInstance<GroupShape>().filter { isElementEditable(it.id) }
        if (groups.isEmpty()) {
            if (getSelectedElements().any { it is GroupShape }) {
                _uiState.value = _uiState.value.copy(message = "El grupo o su capa están bloqueados.")
            }
            return
        }
        val groupIds = groups.map { it.id }.toSet()
        val ungroupedIds = mutableSetOf<String>()
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else {
                    val nextElems = l.elements.flatMap { el ->
                        if (el is GroupShape && el.id in groupIds) {
                            el.children.forEach { ungroupedIds.add(it.id) }
                            el.children
                        } else listOf(el)
                    }
                    l.copy(elements = nextElems)
                }
            })
        }
        _uiState.value = _uiState.value.copy(selectedElementIds = ungroupedIds)
    }

    fun createClippingMaskFromSelected() {
        val doc = _uiState.value.document ?: return
        val ids = _uiState.value.selectedElementIds
        if (ids.size < 2) {
            _uiState.value = _uiState.value.copy(message = "Selecciona al menos 2 objetos: el primero actuará como máscara.")
            return
        }
        val editableChildren = doc.layers.filter { !it.isLocked }
            .flatMap { it.elements }
            .filter { it.id in ids && !it.isLocked }
        if (editableChildren.size < 2) {
            _uiState.value = _uiState.value.copy(message = "Desbloquea la capa y al menos 2 objetos para crear una máscara.")
            return
        }
        val editableIds = editableChildren.map { it.id }.toSet()
        val targetLayer = doc.layers.firstOrNull { !it.isLocked && it.elements.any { el -> el.id in editableIds } } ?: return
        val bounds = VectorMath.computeCombinedBounds(editableChildren) ?: return
        val maskGroup = GroupShape(
            name = "Máscara (${editableChildren.first().name})",
            x = bounds.left,
            y = bounds.top,
            width = max(12f, bounds.width),
            height = max(12f, bounds.height),
            children = editableChildren,
            hasClippingMask = true,
            showMaskOutline = false
        )
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else {
                    val filtered = l.elements.filterNot { it.id in editableIds }
                    if (l.id == targetLayer.id) l.copy(elements = filtered + maskGroup)
                    else l.copy(elements = filtered)
                }
            })
        }
        _uiState.value = _uiState.value.copy(
            selectedElementIds = setOf(maskGroup.id),
            message = "Máscara de recorte aplicada."
        )
    }

    fun toggleSelectedGroupMask() {
        val group = getSelectedElements().singleOrNull() as? GroupShape ?: return
        if (!isElementEditable(group.id)) {
            _uiState.value = _uiState.value.copy(message = "El grupo o su capa están bloqueados.")
            return
        }
        if (group.children.size < 2) {
            _uiState.value = _uiState.value.copy(message = "El grupo necesita al menos 2 objetos para crear una máscara.")
            return
        }
        val nextMasked = !group.hasClippingMask
        replaceElement(group.copy(hasClippingMask = nextMasked))
        _uiState.value = _uiState.value.copy(
            message = if (nextMasked) "Máscara de recorte activada." else "Máscara de recorte liberada."
        )
    }

    fun alignSelected(action: AlignAction) {
        val doc = _uiState.value.document ?: return
        val selected = getSelectedElements().filter { isElementEditable(it.id) }
        if (selected.isEmpty()) {
            if (_uiState.value.selectedElementIds.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(message = "No se puede alinear: capa u objeto bloqueado.")
            }
            return
        }
        val refRect = if (selected.size > 1) {
            VectorMath.computeCombinedBounds(selected) ?: return
        } else {
            androidx.compose.ui.geometry.Rect(0f, 0f, doc.widthPx.toFloat(), doc.heightPx.toFloat())
        }

        val updates = mutableMapOf<String, Pair<Float, Float>>()
        when (action) {
            AlignAction.LEFT -> selected.forEach { updates[it.id] = refRect.left - it.x to 0f }
            AlignAction.CENTER_H -> selected.forEach {
                updates[it.id] = (refRect.left + refRect.width / 2f - it.width / 2f) - it.x to 0f
            }
            AlignAction.RIGHT -> selected.forEach { updates[it.id] = (refRect.right - it.width) - it.x to 0f }
            AlignAction.TOP -> selected.forEach { updates[it.id] = 0f to refRect.top - it.y }
            AlignAction.CENTER_V -> selected.forEach {
                updates[it.id] = 0f to (refRect.top + refRect.height / 2f - it.height / 2f) - it.y
            }
            AlignAction.BOTTOM -> selected.forEach { updates[it.id] = 0f to (refRect.bottom - it.height) - it.y }
            AlignAction.DISTRIBUTE_H -> {
                if (selected.size < 3) return
                val sorted = selected.sortedBy { it.x }
                val totalW = sorted.sumOf { it.width.toDouble() }.toFloat()
                val span = (sorted.last().x + sorted.last().width) - sorted.first().x
                val gap = (span - totalW) / (sorted.size - 1)
                var curX = sorted.first().x
                sorted.forEach { el ->
                    updates[el.id] = (curX - el.x) to 0f
                    curX += el.width + gap
                }
            }
            AlignAction.DISTRIBUTE_V -> {
                if (selected.size < 3) return
                val sorted = selected.sortedBy { it.y }
                val totalH = sorted.sumOf { it.height.toDouble() }.toFloat()
                val span = (sorted.last().y + sorted.last().height) - sorted.first().y
                val gap = (span - totalH) / (sorted.size - 1)
                var curY = sorted.first().y
                sorted.forEach { el ->
                    updates[el.id] = 0f to (curY - el.y)
                    curY += el.height + gap
                }
            }
        }

        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else l.copy(elements = l.elements.map { el ->
                    val delta = updates[el.id]
                    if (delta != null) VectorMath.moveElementBy(el, delta.first, delta.second) else el
                })
            })
        }
    }

    fun reorderSelectedElement(action: ZOrderAction) {
        val targetId = _uiState.value.selectedElementIds.firstOrNull() ?: return
        if (!isElementEditable(targetId)) {
            _uiState.value = _uiState.value.copy(message = "La capa u objeto está bloqueado.")
            return
        }
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { l ->
                if (l.isLocked) l
                else {
                    val idx = l.elements.indexOfFirst { it.id == targetId }
                    if (idx < 0) l
                    else {
                        val list = l.elements.toMutableList()
                        val item = list.removeAt(idx)
                        val nextIdx = when (action) {
                            ZOrderAction.BRING_FORWARD -> (idx + 1).coerceAtMost(list.size)
                            ZOrderAction.SEND_BACKWARD -> (idx - 1).coerceAtLeast(0)
                            ZOrderAction.BRING_TO_FRONT -> list.size
                            ZOrderAction.SEND_TO_BACK -> 0
                        }
                        list.add(nextIdx, item)
                        l.copy(elements = list)
                    }
                }
            })
        }
    }

    // --- Layer Operations ---
    fun selectLayer(layerId: String) {
        _uiState.value = _uiState.value.copy(activeLayerId = layerId)
    }

    fun addLayer() {
        val count = (_uiState.value.document?.layers?.size ?: 0) + 1
        val newLayer = VectorLayer(name = "Capa $count")
        applyDocumentChange { d -> d.copy(layers = d.layers + newLayer) }
        _uiState.value = _uiState.value.copy(activeLayerId = newLayer.id)
    }

    fun duplicateLayer(layerId: String) {
        val doc = _uiState.value.document ?: return
        val src = doc.layers.firstOrNull { it.id == layerId } ?: return
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            name = "${src.name} (Copia)",
            elements = src.elements.map { cloneWithNewIds(it) }
        )
        applyDocumentChange { d ->
            val idx = d.layers.indexOfFirst { it.id == layerId }
            val next = d.layers.toMutableList().apply { add((idx + 1).coerceAtMost(size), copy) }
            d.copy(layers = next)
        }
        _uiState.value = _uiState.value.copy(activeLayerId = copy.id)
    }

    fun deleteLayer(layerId: String) {
        val doc = _uiState.value.document ?: return
        if (doc.layers.size <= 1) return
        applyDocumentChange { d -> d.copy(layers = d.layers.filterNot { it.id == layerId }) }
        val nextActive = _uiState.value.document?.layers?.lastOrNull()?.id
        _uiState.value = _uiState.value.copy(activeLayerId = nextActive, selectedElementIds = emptySet())
    }

    fun renameLayer(layerId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { if (it.id == layerId) it.copy(name = clean) else it })
        }
    }

    fun toggleLayerVisibility(layerId: String) {
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it })
        }
    }

    fun toggleLayerLock(layerId: String) {
        applyDocumentChange { d ->
            d.copy(layers = d.layers.map { if (it.id == layerId) it.copy(isLocked = !it.isLocked) else it })
        }
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        applyDocumentChange(recordUndo = false) { d ->
            d.copy(layers = d.layers.map {
                if (it.id == layerId) it.copy(opacity = opacity.coerceIn(0f, 1f)) else it
            })
        }
    }

    fun moveLayerOrder(layerId: String, moveUp: Boolean) {
        applyDocumentChange { d ->
            val idx = d.layers.indexOfFirst { it.id == layerId }
            if (idx < 0) d
            else {
                val target = if (moveUp) idx + 1 else idx - 1
                if (target !in d.layers.indices) d
                else {
                    val list = d.layers.toMutableList()
                    val item = list.removeAt(idx)
                    list.add(target, item)
                    d.copy(layers = list)
                }
            }
        }
    }

    // --- Guides ---
    fun addCenterGuides() {
        val doc = _uiState.value.document ?: return
        applyDocumentChange { d ->
            d.copy(
                guides = d.guides + listOf(
                    CanvasGuide(orientation = GuideOrientation.VERTICAL, positionPx = doc.widthPx / 2f),
                    CanvasGuide(orientation = GuideOrientation.HORIZONTAL, positionPx = doc.heightPx / 2f)
                )
            )
        }
    }

    fun addGuide(orientation: GuideOrientation, posPx: Float) {
        val doc = _uiState.value.document ?: return
        val clamped = when (orientation) {
            GuideOrientation.VERTICAL -> posPx.coerceIn(0f, doc.widthPx.toFloat())
            GuideOrientation.HORIZONTAL -> posPx.coerceIn(0f, doc.heightPx.toFloat())
        }
        applyDocumentChange { d ->
            d.copy(guides = d.guides + CanvasGuide(orientation = orientation, positionPx = clamped))
        }
        _uiState.value = _uiState.value.copy(
            canvasConfig = _uiState.value.canvasConfig.copy(showGuides = true),
            message = "Guía añadida (${clamped.toInt()} px)."
        )
    }

    fun moveGuide(guideId: String, newPosPx: Float, recordUndo: Boolean = false) {
        if (_uiState.value.canvasConfig.guidesLocked) return
        val doc = _uiState.value.document ?: return
        applyDocumentChange(recordUndo = recordUndo) { d ->
            d.copy(guides = d.guides.map { g ->
                if (g.id != guideId) g
                else {
                    val clamped = when (g.orientation) {
                        GuideOrientation.VERTICAL -> newPosPx.coerceIn(0f, doc.widthPx.toFloat())
                        GuideOrientation.HORIZONTAL -> newPosPx.coerceIn(0f, doc.heightPx.toFloat())
                    }
                    g.copy(positionPx = clamped)
                }
            })
        }
    }

    fun deleteGuide(guideId: String) {
        applyDocumentChange { d ->
            d.copy(guides = d.guides.filterNot { it.id == guideId })
        }
    }

    fun clearGuides() {
        applyDocumentChange { d -> d.copy(guides = emptyList()) }
    }

    // --- Imports into Active Canvas ---
    fun importImageFromUri(uri: Uri) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val mime = app.contentResolver.getType(uri).orEmpty().lowercase()
                    val ext = if (mime.contains("jpeg") || mime.contains("jpg")) "jpg" else "png"
                    val dir = File(app.filesDir, "imported_media").apply { mkdirs() }
                    val outFile = File(dir, "img_${UUID.randomUUID()}.$ext")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    } ?: error("No se pudo leer la imagen.")

                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(outFile.absolutePath, opts)
                    if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                        outFile.delete()
                        error("La imagen no es válida o está dañada.")
                    }
                    val rawW = opts.outWidth.toFloat()
                    val rawH = opts.outHeight.toFloat()
                    val maxDim = min(doc.widthPx, doc.heightPx) * 0.65f
                    val scale = min(1f, maxDim / max(rawW, rawH))
                    val w = rawW * scale
                    val h = rawH * scale
                    ImageShape(
                        name = "Imagen importada",
                        x = (doc.widthPx - w) / 2f,
                        y = (doc.heightPx - h) / 2f,
                        width = w,
                        height = h,
                        imagePath = outFile.absolutePath
                    )
                }
            }
            result.onSuccess { imgEl ->
                addElementToActiveLayer(imgEl)
                _uiState.value = _uiState.value.copy(message = "Imagen insertada en el lienzo.")
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(message = err.message ?: "Error al importar la imagen.")
            }
        }
    }

    fun importSvgIntoCanvas(uri: Uri) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val svgText = app.contentResolver
                        .openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo leer el SVG.")
                    SvgImporter.parseSvg(svgText, app)
                }
            }
            res.onSuccess { parsed ->
                val nonEmptyLayers = parsed.layers.filter { it.elements.isNotEmpty() }
                val elems = parsed.elements
                if (elems.isEmpty()) {
                    _uiState.value = _uiState.value.copy(message = "El SVG no contiene elementos compatibles.")
                } else if (nonEmptyLayers.size > 1) {
                    applyDocumentChange { d ->
                        d.copy(layers = d.layers + nonEmptyLayers)
                    }
                    _uiState.value = _uiState.value.copy(
                        activeLayerId = nonEmptyLayers.last().id,
                        selectedElementIds = elems.map { it.id }.toSet(),
                        message = "${nonEmptyLayers.size} capas SVG importadas (${elems.size} objetos)."
                    )
                } else {
                    val targetLayer = resolveUnlockedActiveLayer() ?: return@onSuccess
                    applyDocumentChange { d ->
                        d.copy(layers = d.layers.map { l ->
                            if (l.id == targetLayer.id) l.copy(elements = l.elements + elems) else l
                        })
                    }
                    _uiState.value = _uiState.value.copy(
                        selectedElementIds = elems.map { it.id }.toSet(),
                        message = "${elems.size} objeto(s) SVG importado(s)."
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(message = "Error al analizar el archivo SVG.")
            }
        }
    }

    fun importPdfIntoCanvas(uri: Uri) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { PdfImporter.importFirstPagesFromPdf(getApplication(), uri) }
            }
            res.onSuccess { pages ->
                if (pages.isEmpty()) {
                    _uiState.value = _uiState.value.copy(message = "No se encontraron páginas en el PDF.")
                } else {
                    val targetLayer = resolveUnlockedActiveLayer() ?: return@onSuccess
                    applyDocumentChange { d ->
                        d.copy(layers = d.layers.map { l ->
                            if (l.id == targetLayer.id) l.copy(elements = l.elements + pages) else l
                        })
                    }
                    _uiState.value = _uiState.value.copy(
                        selectedElementIds = setOf(pages.first().id),
                        message = "${pages.size} página(s) PDF importada(s)."
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(message = "Error al importar el archivo PDF.")
            }
        }
    }

    private fun cloneWithNewIds(el: VectorElement): VectorElement {
        val newId = UUID.randomUUID().toString()
        return when (el) {
            is RectShape -> el.copy(id = newId)
            is EllipseShape -> el.copy(id = newId)
            is PolygonShape -> el.copy(id = newId)
            is StarShape -> el.copy(id = newId)
            is PathShape -> el.copy(
                id = newId,
                nodes = el.nodes.map { it.copy(id = UUID.randomUUID().toString()) },
                extraContours = el.extraContours.map { c ->
                    c.copy(
                        id = UUID.randomUUID().toString(),
                        nodes = c.nodes.map { it.copy(id = UUID.randomUUID().toString()) }
                    )
                }
            )
            is TextShape -> el.copy(id = newId)
            is ImageShape -> el.copy(id = newId)
            is GroupShape -> el.copy(id = newId, children = el.children.map { cloneWithNewIds(it) })
        }
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        flushUnsavedOnExit()
        super.onCleared()
    }
}
