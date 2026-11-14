package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChromeStyleEntity
import com.example.data.CustomFontEntity
import com.example.data.PaletteEntity
import com.example.data.StudioPreferences
import com.example.model.BooleanOp
import com.example.model.BrushPreset
import com.example.model.ChromeEffectConfig
import com.example.model.EditorTool
import com.example.model.EllipseShape
import com.example.model.GuideOrientation
import com.example.model.NodeHandleMode
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.StarShape
import com.example.model.TextShape
import com.example.ui.components.ChromeEffectsPanel
import com.example.ui.components.LayersPanel
import com.example.ui.components.PropertiesPanel
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderCharcoal
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderSurfaceElevated
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletSoft
import com.example.viewmodel.EditorViewModel
import kotlin.math.roundToInt

private enum class MobileInspectorSheet {
    NONE,
    CHROME,
    LAYERS,
    PROPERTIES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    isWideScreen: Boolean,
    preferences: StudioPreferences,
    palettes: List<PaletteEntity>,
    customFonts: List<CustomFontEntity>,
    chromeStyles: List<ChromeStyleEntity> = emptyList(),
    pendingInsertAssetId: String?,
    onClearPendingAsset: () -> Unit,
    onSavePaletteColor: (String, List<Long>) -> Unit,
    onSaveChromeStyle: (String, ChromeEffectConfig) -> Unit = { _, _ -> },
    onDeleteChromeStyle: (String) -> Unit = {},
    onBack: () -> Unit,
    onOpenExport: (projectId: String, selectedIds: Set<String>) -> Unit,
    editorViewModel: EditorViewModel
) {
    LaunchedEffect(projectId) {
        editorViewModel.loadProject(projectId)
    }

    val state by editorViewModel.uiState.collectAsStateWithLifecycle()
    val customFontPaths by editorViewModel.customFontPaths.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.document?.id, pendingInsertAssetId) {
        if (state.document != null && pendingInsertAssetId != null) {
            editorViewModel.insertAsset(pendingInsertAssetId)
            onClearPendingAsset()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            editorViewModel.clearMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, editorViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                editorViewModel.flushUnsavedOnExit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        editorViewModel.saveNowAndThen(silent = true) {
            onBack()
        }
    }

    var mobileSheet by remember { mutableStateOf(MobileInspectorSheet.NONE) }
    var tabletTab by remember { mutableIntStateOf(0) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDocInput by remember { mutableStateOf("") }
    var showQuickTextDialog by remember { mutableStateOf(false) }
    var quickTextValue by remember { mutableStateOf("") }
    var importMenuExpanded by remember { mutableStateOf(false) }
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var showGuidesDialog by remember { mutableStateOf(false) }
    var newGuidePosInput by remember { mutableStateOf("540") }
    var newGuideOrientation by remember { mutableStateOf(GuideOrientation.VERTICAL) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { editorViewModel.importImageFromUri(it) }
    }

    val svgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { editorViewModel.importSvgIntoCanvas(it) }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { editorViewModel.importPdfIntoCanvas(it) }
    }

    val doc = state.document

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(SpiderBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = SpiderBlack
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading || doc == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SpiderViolet)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Studio Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(SpiderCharcoal)
                            .border(0.5.dp, SpiderBorder)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            editorViewModel.saveNowAndThen(silent = true) {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a inicio")
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    renameDocInput = doc.name
                                    showRenameDialog = true
                                }
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                doc.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${doc.widthPx}×${doc.heightPx} · ${(state.zoom * 100).roundToInt()}%" +
                                    if (state.hasUnsavedChanges) " · Editado" else " · Guardado",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpiderTextSecondary,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { editorViewModel.undo() },
                            enabled = state.canUndo,
                            modifier = Modifier.testTag("editor_undo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer")
                        }
                        IconButton(
                            onClick = { editorViewModel.redo() },
                            enabled = state.canRedo,
                            modifier = Modifier.testTag("editor_redo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Rehacer")
                        }
                        IconButton(
                            onClick = { editorViewModel.saveNow() },
                            modifier = Modifier.testTag("editor_save_button")
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Guardar",
                                tint = if (state.hasUnsavedChanges) SpiderViolet else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Import Menu (PNG/JPG Photo Picker, SVG, PDF)
                        Box {
                            IconButton(
                                onClick = { importMenuExpanded = true },
                                modifier = Modifier.testTag("editor_import_menu_button")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Importar medios")
                            }
                            DropdownMenu(expanded = importMenuExpanded, onDismissRequest = { importMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Imagen de galería (PNG/JPG)") },
                                    leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                                    onClick = {
                                        importMenuExpanded = false
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Vector SVG editable") },
                                    leadingIcon = { Icon(Icons.Default.Polyline, contentDescription = null) },
                                    onClick = {
                                        importMenuExpanded = false
                                        svgPicker.launch(arrayOf("image/svg+xml", "*/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Documento PDF") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    onClick = {
                                        importMenuExpanded = false
                                        pdfPicker.launch(arrayOf("application/pdf"))
                                    }
                                )
                            }
                        }

                        // View / Grid / Rulers / Snap / Guides Menu
                        Box {
                            IconButton(
                                onClick = { viewMenuExpanded = true },
                                modifier = Modifier.testTag("editor_view_menu_button")
                            ) {
                                Icon(Icons.Default.GridOn, contentDescription = "Cuadrícula, reglas y guías")
                            }
                            DropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (state.canvasConfig.showGrid) "Ocultar cuadrícula" else "Mostrar cuadrícula") },
                                    leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null) },
                                    onClick = {
                                        editorViewModel.updateCanvasConfig { it.copy(showGrid = !it.showGrid) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.canvasConfig.showRulers) "Ocultar reglas" else "Mostrar reglas") },
                                    leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null) },
                                    onClick = {
                                        editorViewModel.updateCanvasConfig { it.copy(showRulers = !it.showRulers) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.canvasConfig.showGuides) "Ocultar guías" else "Mostrar guías") },
                                    onClick = {
                                        editorViewModel.updateCanvasConfig { it.copy(showGuides = !it.showGuides) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.canvasConfig.guidesLocked) "Desbloquear guías" else "Bloquear guías") },
                                    leadingIcon = {
                                        Icon(
                                            if (state.canvasConfig.guidesLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        editorViewModel.updateCanvasConfig { it.copy(guidesLocked = !it.guidesLocked) }
                                    }
                                )
                                HorizontalDivider(color = SpiderBorder)
                                DropdownMenuItem(
                                    text = { Text(if (state.canvasConfig.snapEnabled) "Desactivar ajuste magnético" else "Activar ajuste magnético") },
                                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                                    onClick = {
                                        editorViewModel.updateCanvasConfig { it.copy(snapEnabled = !it.snapEnabled) }
                                    }
                                )
                                if (state.canvasConfig.snapEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("  Snap a cuadrícula") },
                                        trailingIcon = {
                                            Checkbox(
                                                checked = state.canvasConfig.snapToGrid,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            editorViewModel.updateCanvasConfig { it.copy(snapToGrid = !it.snapToGrid) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("  Snap a objetos y espaciado") },
                                        trailingIcon = {
                                            Checkbox(
                                                checked = state.canvasConfig.snapToObjects,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            editorViewModel.updateCanvasConfig { it.copy(snapToObjects = !it.snapToObjects) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("  Snap a guías") },
                                        trailingIcon = {
                                            Checkbox(
                                                checked = state.canvasConfig.snapToGuides,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            editorViewModel.updateCanvasConfig { it.copy(snapToGuides = !it.snapToGuides) }
                                        }
                                    )
                                }
                                HorizontalDivider(color = SpiderBorder)
                                DropdownMenuItem(
                                    text = { Text("Añadir guías centrales") },
                                    onClick = {
                                        viewMenuExpanded = false
                                        editorViewModel.addCenterGuides()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Gestionar guías exactas (${doc.guides.size})") },
                                    onClick = {
                                        viewMenuExpanded = false
                                        showGuidesDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Limpiar guías") },
                                    onClick = {
                                        viewMenuExpanded = false
                                        editorViewModel.clearGuides()
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                editorViewModel.saveNowAndThen(silent = true) {
                                    onOpenExport(doc.id, state.selectedElementIds)
                                }
                            },
                            modifier = Modifier.testTag("editor_export_button")
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = "Exportar proyecto", tint = SpiderVioletSoft)
                        }
                    }

                    // Main Workspace Row: Left Tool Rail + Central Canvas + Optional Tablet Inspector
                    Row(modifier = Modifier.weight(1f)) {
                        // Left Tool Rail
                        ToolRail(
                            activeTool = state.activeTool,
                            multiSelectMode = state.multiSelectMode,
                            onSelectTool = { editorViewModel.setActiveTool(it) },
                            onToggleMultiSelect = { editorViewModel.toggleMultiSelectMode() }
                        )

                        // Central Interactive Canvas + Contextual Floating Bars
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            EditorCanvasBox(
                                state = state,
                                customFontPaths = customFontPaths,
                                stylusPressureEnabled = preferences.stylusPressureEnabled,
                                viewModel = editorViewModel,
                                onTextPlaced = {
                                    val placed = editorViewModel.getSelectedElements().firstOrNull() as? TextShape
                                    if (placed != null) {
                                        quickTextValue = placed.text
                                        showQuickTextDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Contextual Pen / Node / Boolean Action Pill at Top of Canvas
                            val selectedElems = editorViewModel.getSelectedElements()
                            val selectedSingle = selectedElems.singleOrNull()
                            val geometricSelectedCount = selectedElems.count {
                                it is RectShape || it is EllipseShape || it is PolygonShape || it is StarShape || it is PathShape
                            }

                            if (state.activeTool == EditorTool.PEN && state.penInProgressNodes.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 30.dp, start = 12.dp, end = 12.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(SpiderSurface.copy(alpha = 0.94f))
                                        .border(1.dp, SpiderViolet, RoundedCornerShape(24.dp))
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${state.penInProgressNodes.size} nodos", style = MaterialTheme.typography.labelMedium)
                                    TextButton(onClick = { editorViewModel.undoLastPenNode() }) {
                                        Text("Deshacer nodo")
                                    }
                                    TextButton(onClick = { editorViewModel.commitPenPath(closed = false) }) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Finalizar")
                                    }
                                    TextButton(onClick = { editorViewModel.commitPenPath(closed = true) }) {
                                        Text("Cerrar")
                                    }
                                }
                            } else if (state.activeTool == EditorTool.NODE) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 30.dp, start = 12.dp, end = 12.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(SpiderSurface.copy(alpha = 0.94f))
                                        .border(1.dp, SpiderBorder, RoundedCornerShape(24.dp))
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedSingle is PathShape) {
                                        val activeNode = editorViewModel.getActiveNode()
                                        val activeMode = activeNode?.let {
                                            if (it.handleMode == NodeHandleMode.CORNER && it.smooth) NodeHandleMode.SMOOTH else it.handleMode
                                        }
                                        if (activeNode != null) {
                                            FilterChip(
                                                selected = activeMode == NodeHandleMode.CORNER,
                                                onClick = { editorViewModel.setActiveNodeHandleMode(NodeHandleMode.CORNER) },
                                                label = { Text("Esquina") }
                                            )
                                            FilterChip(
                                                selected = activeMode == NodeHandleMode.SMOOTH,
                                                onClick = { editorViewModel.setActiveNodeHandleMode(NodeHandleMode.SMOOTH) },
                                                label = { Text("Suave") }
                                            )
                                            FilterChip(
                                                selected = activeMode == NodeHandleMode.SYMMETRIC,
                                                onClick = { editorViewModel.setActiveNodeHandleMode(NodeHandleMode.SYMMETRIC) },
                                                label = { Text("Simétrico") }
                                            )
                                        } else {
                                            Text(
                                                "Toca un nodo o trazo",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = SpiderTextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )
                                        }
                                        TextButton(onClick = { editorViewModel.addMidpointNodeToPath() }) { Text("+ Nodo") }
                                        TextButton(onClick = { editorViewModel.deleteActiveNode() }) { Text("- Nodo") }
                                        TextButton(onClick = { editorViewModel.toggleSelectedPathClosed() }) {
                                            Text(if (selectedSingle.isClosed) "Abrir" else "Cerrar")
                                        }
                                    } else if (selectedSingle is RectShape || selectedSingle is EllipseShape ||
                                        selectedSingle is PolygonShape || selectedSingle is StarShape
                                    ) {
                                        TextButton(onClick = { editorViewModel.convertSelectedToPath() }) {
                                            Text("Convertir a curvas editables")
                                        }
                                    } else {
                                        Text(
                                            "Selecciona un trazado para editar nodos",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SpiderTextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            } else if (state.activeTool == EditorTool.BRUSH) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 30.dp, start = 12.dp, end = 12.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(SpiderSurface.copy(alpha = 0.95f))
                                        .border(1.dp, SpiderViolet, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BrushPreset.entries.forEach { preset ->
                                            FilterChip(
                                                selected = state.activeBrushPreset == preset,
                                                onClick = { editorViewModel.setBrushPreset(preset) },
                                                label = { Text(preset.label) }
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Tam: ${state.activeBrushSize.roundToInt()} px",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Slider(
                                            value = state.activeBrushSize,
                                            onValueChange = { editorViewModel.setBrushSize(it) },
                                            valueRange = 4f..56f,
                                            modifier = Modifier.width(120.dp)
                                        )
                                        FilterChip(
                                            selected = state.activeBrushTaper,
                                            onClick = { editorViewModel.toggleBrushTaper() },
                                            label = { Text("Afinar") }
                                        )
                                    }
                                }
                            } else if (state.activeTool == EditorTool.SELECT && selectedElems.size >= 2) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 30.dp, start = 12.dp, end = 12.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(SpiderSurface.copy(alpha = 0.95f))
                                        .border(1.dp, SpiderViolet, RoundedCornerShape(24.dp))
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (geometricSelectedCount >= 2) {
                                        Text(
                                            "Booleanas:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SpiderVioletSoft,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                        BooleanOp.entries.forEach { op ->
                                            TextButton(
                                                onClick = { editorViewModel.applyBooleanOperation(op) },
                                                modifier = Modifier.testTag("boolean_op_${op.name.lowercase()}")
                                            ) {
                                                Text(op.label)
                                            }
                                        }
                                    }
                                    TextButton(
                                        onClick = { editorViewModel.createClippingMaskFromSelected() },
                                        modifier = Modifier.testTag("floating_clipping_mask_button")
                                    ) {
                                        Icon(Icons.Default.LayersClear, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Máscara")
                                    }
                                    TextButton(onClick = { editorViewModel.groupSelected() }) {
                                        Text("Agrupar")
                                    }
                                }
                            }

                            // Zoom Reset Chip
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(SpiderSurface.copy(alpha = 0.9f))
                                    .border(1.dp, SpiderBorder, RoundedCornerShape(20.dp))
                                    .clickable {
                                        editorViewModel.setZoomAndPan(1f, androidx.compose.ui.geometry.Offset.Zero)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.FitScreen, contentDescription = "Restablecer zoom", modifier = Modifier.size(16.dp), tint = SpiderVioletSoft)
                                Text("${(state.zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Persistent Inspector on Wide Screens (Tablets / Foldables)
                        if (isWideScreen) {
                            Column(
                                modifier = Modifier
                                    .width(330.dp)
                                    .fillMaxHeight()
                                    .background(SpiderCharcoal)
                                    .border(0.5.dp, SpiderBorder)
                            ) {
                                TabRow(selectedTabIndex = tabletTab, containerColor = SpiderCharcoal) {
                                    Tab(
                                        selected = tabletTab == 0,
                                        onClick = { tabletTab = 0 },
                                        text = { Text("Propiedades", maxLines = 1) }
                                    )
                                    Tab(
                                        selected = tabletTab == 1,
                                        onClick = { tabletTab = 1 },
                                        text = { Text("Chrome", maxLines = 1) },
                                        modifier = Modifier.testTag("tablet_tab_chrome")
                                    )
                                    Tab(
                                        selected = tabletTab == 2,
                                        onClick = { tabletTab = 2 },
                                        text = { Text("Capas", maxLines = 1) }
                                    )
                                }
                                if (tabletTab == 0) {
                                    PropertiesPanel(
                                        selectedElements = editorViewModel.getSelectedElements(),
                                        canPaste = state.canPaste,
                                        colorHistory = preferences.colorHistory,
                                        palettes = palettes,
                                        customFonts = customFonts,
                                        onUpdateSelected = { rec, fn -> editorViewModel.updateSelectedElements(rec, fn) },
                                        onAlign = { editorViewModel.alignSelected(it) },
                                        onZOrder = { editorViewModel.reorderSelectedElement(it) },
                                        onDuplicate = { editorViewModel.duplicateSelected() },
                                        onCopy = { editorViewModel.copySelected() },
                                        onPaste = { editorViewModel.pasteClipboard() },
                                        onDelete = { editorViewModel.deleteSelected() },
                                        onColorUsed = { editorViewModel.recordColorInHistory(it) },
                                        onSavePaletteColor = onSavePaletteColor,
                                        onTogglePathClosed = { editorViewModel.toggleSelectedPathClosed() },
                                        onToggleSmoothNode = { editorViewModel.toggleActiveNodeSmooth() },
                                        onAddMidpointNode = { editorViewModel.addMidpointNodeToPath() },
                                        onDeleteNode = { editorViewModel.deleteActiveNode() },
                                        activeNode = editorViewModel.getActiveNode(),
                                        onSetNodeHandleMode = { editorViewModel.setActiveNodeHandleMode(it) },
                                        onConvertToPath = { editorViewModel.convertSelectedToPath() },
                                        onBooleanOp = { editorViewModel.applyBooleanOperation(it) },
                                        onReversePath = { editorViewModel.reverseSelectedPath() },
                                        onSimplifyPath = { editorViewModel.simplifySelectedPath() },
                                        onBreakApartPath = { editorViewModel.breakApartSelectedPath() },
                                        onUngroup = { editorViewModel.ungroupSelected() },
                                        onOpenChromeStudio = { tabletTab = 1 },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else if (tabletTab == 1) {
                                    ChromeEffectsPanel(
                                        selectedElements = editorViewModel.getSelectedElements(),
                                        customChromeStyles = chromeStyles,
                                        colorHistory = preferences.colorHistory,
                                        palettes = palettes,
                                        customFontPaths = customFontPaths,
                                        onApplyPreset = { editorViewModel.applyChromePreset(it) },
                                        onApplyCustomStyle = { editorViewModel.applyCustomChromeStyle(it) },
                                        onUpdateChrome = { rec, fn -> editorViewModel.updateChromeEffect(rec, fn) },
                                        onDisableChrome = { editorViewModel.disableChromeEffect() },
                                        onSaveChromeStyle = onSaveChromeStyle,
                                        onDeleteChromeStyle = onDeleteChromeStyle,
                                        onColorUsed = { editorViewModel.recordColorInHistory(it) },
                                        onSavePaletteColor = onSavePaletteColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    LayersPanel(
                                        document = doc,
                                        activeLayerId = state.activeLayerId,
                                        selectedElementIds = state.selectedElementIds,
                                        onSelectLayer = { editorViewModel.selectLayer(it) },
                                        onAddLayer = { editorViewModel.addLayer() },
                                        onDuplicateLayer = { editorViewModel.duplicateLayer(it) },
                                        onDeleteLayer = { editorViewModel.deleteLayer(it) },
                                        onRenameLayer = { id, n -> editorViewModel.renameLayer(id, n) },
                                        onToggleVisibility = { editorViewModel.toggleLayerVisibility(it) },
                                        onToggleLock = { editorViewModel.toggleLayerLock(it) },
                                        onMoveLayer = { id, up -> editorViewModel.moveLayerOrder(id, up) },
                                        onOpacityChange = { id, op -> editorViewModel.setLayerOpacity(id, op) },
                                        onSelectElement = { lid, eid -> editorViewModel.selectElementById(lid, eid) },
                                        onGroupSelected = { editorViewModel.groupSelected() },
                                        onUngroupSelected = { editorViewModel.ungroupSelected() },
                                        onCreateClippingMask = { editorViewModel.createClippingMaskFromSelected() },
                                        onToggleGroupMask = { editorViewModel.toggleSelectedGroupMask() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Phone Bottom Quick Dock for Properties, Chrome Effects & Layers
                    if (!isWideScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpiderCharcoal)
                                .border(0.5.dp, SpiderBorder)
                                .navigationBarsPadding()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { mobileSheet = MobileInspectorSheet.PROPERTIES },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mobile_properties_button")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Propiedades", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { mobileSheet = MobileInspectorSheet.CHROME },
                                modifier = Modifier
                                    .weight(0.95f)
                                    .height(48.dp)
                                    .testTag("mobile_chrome_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = SpiderVioletSoft)
                                Spacer(Modifier.width(4.dp))
                                Text("Chrome", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { mobileSheet = MobileInspectorSheet.LAYERS },
                                modifier = Modifier
                                    .weight(0.95f)
                                    .height(48.dp)
                                    .testTag("mobile_layers_button")
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Capas (${doc.layers.size})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
            )
        }
    }

    // Mobile Bottom Sheets
    if (!isWideScreen && mobileSheet != MobileInspectorSheet.NONE && doc != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { mobileSheet = MobileInspectorSheet.NONE },
            sheetState = sheetState,
            containerColor = SpiderSurface
        ) {
            when (mobileSheet) {
                MobileInspectorSheet.PROPERTIES -> {
                    PropertiesPanel(
                        selectedElements = editorViewModel.getSelectedElements(),
                        canPaste = state.canPaste,
                        colorHistory = preferences.colorHistory,
                        palettes = palettes,
                        customFonts = customFonts,
                        onUpdateSelected = { rec, fn -> editorViewModel.updateSelectedElements(rec, fn) },
                        onAlign = { editorViewModel.alignSelected(it) },
                        onZOrder = { editorViewModel.reorderSelectedElement(it) },
                        onDuplicate = { editorViewModel.duplicateSelected() },
                        onCopy = { editorViewModel.copySelected() },
                        onPaste = { editorViewModel.pasteClipboard() },
                        onDelete = { editorViewModel.deleteSelected() },
                        onColorUsed = { editorViewModel.recordColorInHistory(it) },
                        onSavePaletteColor = onSavePaletteColor,
                        onTogglePathClosed = { editorViewModel.toggleSelectedPathClosed() },
                        onToggleSmoothNode = { editorViewModel.toggleActiveNodeSmooth() },
                        onAddMidpointNode = { editorViewModel.addMidpointNodeToPath() },
                        onDeleteNode = { editorViewModel.deleteActiveNode() },
                        activeNode = editorViewModel.getActiveNode(),
                        onSetNodeHandleMode = { editorViewModel.setActiveNodeHandleMode(it) },
                        onConvertToPath = { editorViewModel.convertSelectedToPath() },
                        onBooleanOp = { editorViewModel.applyBooleanOperation(it) },
                        onReversePath = { editorViewModel.reverseSelectedPath() },
                        onSimplifyPath = { editorViewModel.simplifySelectedPath() },
                        onBreakApartPath = { editorViewModel.breakApartSelectedPath() },
                        onUngroup = { editorViewModel.ungroupSelected() },
                        onOpenChromeStudio = { mobileSheet = MobileInspectorSheet.CHROME }
                    )
                }
                MobileInspectorSheet.CHROME -> {
                    ChromeEffectsPanel(
                        selectedElements = editorViewModel.getSelectedElements(),
                        customChromeStyles = chromeStyles,
                        colorHistory = preferences.colorHistory,
                        palettes = palettes,
                        customFontPaths = customFontPaths,
                        onApplyPreset = { editorViewModel.applyChromePreset(it) },
                        onApplyCustomStyle = { editorViewModel.applyCustomChromeStyle(it) },
                        onUpdateChrome = { rec, fn -> editorViewModel.updateChromeEffect(rec, fn) },
                        onDisableChrome = { editorViewModel.disableChromeEffect() },
                        onSaveChromeStyle = onSaveChromeStyle,
                        onDeleteChromeStyle = onDeleteChromeStyle,
                        onColorUsed = { editorViewModel.recordColorInHistory(it) },
                        onSavePaletteColor = onSavePaletteColor
                    )
                }
                MobileInspectorSheet.LAYERS -> {
                    LayersPanel(
                        document = doc,
                        activeLayerId = state.activeLayerId,
                        selectedElementIds = state.selectedElementIds,
                        onSelectLayer = { editorViewModel.selectLayer(it) },
                        onAddLayer = { editorViewModel.addLayer() },
                        onDuplicateLayer = { editorViewModel.duplicateLayer(it) },
                        onDeleteLayer = { editorViewModel.deleteLayer(it) },
                        onRenameLayer = { id, n -> editorViewModel.renameLayer(id, n) },
                        onToggleVisibility = { editorViewModel.toggleLayerVisibility(it) },
                        onToggleLock = { editorViewModel.toggleLayerLock(it) },
                        onMoveLayer = { id, up -> editorViewModel.moveLayerOrder(id, up) },
                        onOpacityChange = { id, op -> editorViewModel.setLayerOpacity(id, op) },
                        onSelectElement = { lid, eid -> editorViewModel.selectElementById(lid, eid) },
                        onGroupSelected = { editorViewModel.groupSelected() },
                        onUngroupSelected = { editorViewModel.ungroupSelected() },
                        onCreateClippingMask = { editorViewModel.createClippingMaskFromSelected() },
                        onToggleGroupMask = { editorViewModel.toggleSelectedGroupMask() }
                    )
                }
                MobileInspectorSheet.NONE -> Unit
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar proyecto") },
            text = {
                OutlinedTextField(
                    value = renameDocInput,
                    onValueChange = { renameDocInput = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    editorViewModel.renameDocument(renameDocInput)
                    showRenameDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showQuickTextDialog) {
        AlertDialog(
            onDismissRequest = { showQuickTextDialog = false },
            title = { Text("Editar texto") },
            text = {
                OutlinedTextField(
                    value = quickTextValue,
                    onValueChange = { quickTextValue = it },
                    label = { Text("Contenido (ñ, á, é, í, ó, ú)") },
                    minLines = 2
                )
            },
            confirmButton = {
                Button(onClick = {
                    editorViewModel.updateSelectedElements(true) { el ->
                        if (el is TextShape) el.copy(text = quickTextValue) else el
                    }
                    showQuickTextDialog = false
                }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickTextDialog = false }) { Text("Cerrar") }
            }
        )
    }

    if (showGuidesDialog && doc != null) {
        AlertDialog(
            onDismissRequest = { showGuidesDialog = false },
            title = { Text("Guías exactas (${doc.guides.size})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Toca las reglas del lienzo o introduce una posición exacta en píxeles:",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpiderTextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = newGuideOrientation == GuideOrientation.VERTICAL,
                            onClick = { newGuideOrientation = GuideOrientation.VERTICAL },
                            label = { Text("Vertical (X)") }
                        )
                        FilterChip(
                            selected = newGuideOrientation == GuideOrientation.HORIZONTAL,
                            onClick = { newGuideOrientation = GuideOrientation.HORIZONTAL },
                            label = { Text("Horizontal (Y)") }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newGuidePosInput,
                            onValueChange = { newGuidePosInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("Posición (px)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val px = newGuidePosInput.toFloatOrNull()
                            if (px != null) {
                                editorViewModel.addGuide(newGuideOrientation, px)
                            }
                        }) {
                            Text("Añadir")
                        }
                    }
                    if (doc.guides.isNotEmpty()) {
                        HorizontalDivider(color = SpiderBorder)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            doc.guides.forEach { g ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${if (g.orientation == GuideOrientation.VERTICAL) "Vertical X" else "Horizontal Y"}: ${g.positionPx.roundToInt()} px",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    IconButton(
                                        onClick = { editorViewModel.deleteGuide(g.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar guía", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuidesDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun ToolRail(
    activeTool: EditorTool,
    multiSelectMode: Boolean,
    onSelectTool: (EditorTool) -> Unit,
    onToggleMultiSelect: () -> Unit
) {
    val tools: List<Pair<EditorTool, ImageVector>> = listOf(
        EditorTool.SELECT to Icons.Default.NearMe,
        EditorTool.NODE to Icons.Default.ViewModule,
        EditorTool.PEN to Icons.Default.Polyline,
        EditorTool.PENCIL to Icons.Default.Create,
        EditorTool.BRUSH to Icons.Default.Brush,
        EditorTool.RECTANGLE to Icons.Default.CropSquare,
        EditorTool.ELLIPSE to Icons.Default.Circle,
        EditorTool.POLYGON to Icons.Default.Hexagon,
        EditorTool.STAR to Icons.Default.Star,
        EditorTool.TEXT to Icons.Default.TextFields,
        EditorTool.HAND to Icons.Default.PanTool
    )
    Column(
        modifier = Modifier
            .width(60.dp)
            .fillMaxHeight()
            .background(SpiderCharcoal)
            .border(0.5.dp, SpiderBorder)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tools.forEach { (tool, icon) ->
            val selected = activeTool == tool
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) SpiderViolet else Color.Transparent)
                    .clickable { onSelectTool(tool) }
                    .testTag("tool_${tool.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = tool.label,
                    tint = if (selected) Color.White else SpiderTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (multiSelectMode) SpiderViolet.copy(alpha = 0.28f) else Color.Transparent)
                .border(
                    width = if (multiSelectMode) 1.dp else 0.dp,
                    color = if (multiSelectMode) SpiderViolet else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onToggleMultiSelect)
                .testTag("tool_multiselect"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SelectAll,
                contentDescription = "Selección múltiple",
                tint = if (multiSelectMode) SpiderVioletSoft else SpiderTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
