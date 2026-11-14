package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.CustomFontEntity
import com.example.data.PaletteEntity
import com.example.engine.ComposeVectorRenderer.drawStudioCanvas
import com.example.model.AssetCatalog
import com.example.model.DocumentBackgroundMode
import com.example.model.EditorCanvasConfig
import com.example.model.EditorTool
import com.example.model.SpiderDocument
import com.example.model.VectorLayer
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderSurfaceElevated
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletSoft

@Composable
fun AssetsScreen(
    isWideScreen: Boolean,
    palettes: List<PaletteEntity>,
    customFonts: List<CustomFontEntity>,
    onInsertAsset: (String) -> Unit,
    onCreatePalette: (String, List<Long>) -> Unit,
    onDeletePalette: (String) -> Unit,
    onImportFont: (Uri) -> Unit,
    onDeleteFont: (CustomFontEntity) -> Unit
) {
    val context = LocalContext.current
    val columns = if (isWideScreen) 3 else 2

    var showNewPaletteDialog by remember { mutableStateOf(false) }
    var newPaletteName by remember { mutableStateOf("Mi Paleta Estudio") }

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(onImportFont)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(SpiderBlack)
    ) {
        item(span = { GridItemSpan(columns) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Biblioteca de recursos", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Vectores reutilizables, paletas cromáticas y tipografías importadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpiderTextSecondary
                )
            }
        }

        item(span = { GridItemSpan(columns) }) {
            Text("Gráficos vectoriales", style = MaterialTheme.typography.titleLarge)
        }

        items(AssetCatalog.assets, key = { it.id }) { asset ->
            val previewDoc = remember(asset.id) {
                SpiderDocument(
                    widthPx = 360,
                    heightPx = 360,
                    backgroundMode = DocumentBackgroundMode.SOLID,
                    backgroundColorArgb = 0xFF0E0E15,
                    layers = listOf(VectorLayer(elements = listOf(asset.createElement(180f, 180f))))
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SpiderSurface)
                    .border(1.dp, SpiderBorder, RoundedCornerShape(16.dp))
                    .clickable { onInsertAsset(asset.id) }
                    .padding(10.dp)
                    .testTag("asset_card_${asset.id}"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpiderBlack)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val zoom = minOf(size.width, size.height) / 360f
                        val px = (size.width - 360f * zoom) / 2f
                        val py = (size.height - 360f * zoom) / 2f
                        drawStudioCanvas(
                            context = context,
                            doc = previewDoc,
                            config = EditorCanvasConfig(showGrid = false, showRulers = false, showGuides = false),
                            zoom = zoom,
                            pan = Offset(px, py),
                            selectedIds = emptySet(),
                            activeTool = EditorTool.SELECT,
                            activeNodeIndex = null,
                            penInProgressNodes = emptyList(),
                            freehandPreviewPoints = emptyList(),
                            snapLines = emptyList(),
                            customFontPaths = emptyMap()
                        )
                    }
                }
                Text(asset.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = SpiderVioletSoft)
                Text(asset.title, style = MaterialTheme.typography.titleSmall)
            }
        }

        // Custom Color Palettes
        item(span = { GridItemSpan(columns) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = SpiderViolet)
                    Text("Paletas personalizadas", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedButton(onClick = { showNewPaletteDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nueva")
                }
            }
        }

        item(span = { GridItemSpan(columns) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                palettes.forEach { pal ->
                    val colors = pal.colorsCsv.split(",").mapNotNull { it.trim().toLongOrNull(16) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SpiderSurface)
                            .border(1.dp, SpiderBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pal.name, style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                colors.forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(c))
                                            .border(1.dp, SpiderBorder, CircleShape)
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onDeletePalette(pal.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar paleta")
                        }
                    }
                }
            }
        }

        // Fonts Manager
        item(span = { GridItemSpan(columns) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.FontDownload, contentDescription = null, tint = SpiderViolet)
                    Text("Fuentes (.ttf / .otf)", style = MaterialTheme.typography.titleLarge)
                }
                Button(
                    onClick = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "*/*")) },
                    modifier = Modifier.testTag("import_font_button")
                ) {
                    Text("Importar")
                }
            }
        }

        item(span = { GridItemSpan(columns) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Space Grotesk (Integrada)", "Plus Jakarta Sans (Integrada)", "JetBrains Mono (Integrada)").forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpiderSurfaceElevated)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text("Activa", style = MaterialTheme.typography.labelSmall, color = SpiderVioletSoft)
                    }
                }
                customFonts.forEach { f ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpiderSurface)
                            .border(1.dp, SpiderBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(f.displayName, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { onDeleteFont(f) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar fuente")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showNewPaletteDialog) {
        AlertDialog(
            onDismissRequest = { showNewPaletteDialog = false },
            title = { Text("Nueva paleta personalizada") },
            text = {
                OutlinedTextField(
                    value = newPaletteName,
                    onValueChange = { newPaletteName = it },
                    label = { Text("Nombre de la paleta") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCreatePalette(
                        newPaletteName,
                        listOf(0xFF8B5CF6, 0xFF38BDF8, 0xFFF43F5E, 0xFF22C55E, 0xFFF59E0B, 0xFFF4F4F8)
                    )
                    showNewPaletteDialog = false
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPaletteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
