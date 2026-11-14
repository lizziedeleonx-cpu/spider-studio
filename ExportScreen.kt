package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ProjectRepository
import com.example.engine.AndroidVectorRenderer
import com.example.engine.ExportManager
import com.example.engine.ExportValidationReport
import com.example.engine.VectorMath
import com.example.model.DocumentBackgroundMode
import com.example.model.ExportFormat
import com.example.model.ExportScope
import com.example.model.SpiderDocument
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    selectedIds: Set<String>,
    defaultScale: Int,
    projectRepository: ProjectRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var document by remember { mutableStateOf<SpiderDocument?>(null) }
    var customFontPaths by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var format by remember { mutableStateOf(ExportFormat.PNG) }
    var exportScope by remember { mutableStateOf(ExportScope.FULL_PAGE) }
    var scale by remember { mutableFloatStateOf(defaultScale.toFloat().coerceIn(0.5f, 4f)) }
    var jpgQuality by remember { mutableIntStateOf(92) }
    var transparentPng by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastValidation by remember { mutableStateOf<ExportValidationReport?>(null) }

    LaunchedEffect(projectId) {
        val loaded = projectRepository.loadDocument(projectId)
        customFontPaths = projectRepository.getCustomFontMap()
        document = loaded
        if (loaded != null) {
            transparentPng = loaded.backgroundMode == DocumentBackgroundMode.TRANSPARENT
        }
    }

    LaunchedEffect(format, exportScope, scale, jpgQuality, transparentPng) {
        lastValidation = null
    }

    LaunchedEffect(document, exportScope, transparentPng, format) {
        val doc = document ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.Default) {
            val cropRect = if (exportScope == ExportScope.SELECTED_AREA && selectedIds.isNotEmpty()) {
                val elems = doc.layers.filter { it.isVisible }.flatMap { it.elements }.filter { it.id in selectedIds }
                VectorMath.computeCombinedBounds(elems)?.let { RectF(it.left, it.top, it.right, it.bottom) }
            } else null
            val bw = cropRect?.width() ?: doc.widthPx.toFloat()
            val bh = cropRect?.height() ?: doc.heightPx.toFloat()
            val ratio = bw / bh.coerceAtLeast(1f)
            val pw = if (ratio >= 1f) 600 else (600 * ratio).toInt().coerceAtLeast(80)
            val ph = if (ratio >= 1f) (600 / ratio).toInt().coerceAtLeast(80) else 600
            AndroidVectorRenderer.renderDocumentToBitmap(
                context = context,
                doc = doc,
                targetWidth = pw,
                targetHeight = ph,
                transparentBackground = format == ExportFormat.PNG && transparentPng,
                customFontPaths = customFontPaths,
                cropBounds = cropRect
            )
        }
        previewBitmap = bmp
    }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(format.mimeType)
    ) { targetUri: Uri? ->
        val doc = document ?: return@rememberLauncherForActivityResult
        if (targetUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            val res = runCatching {
                ExportManager.exportToUri(
                    context = context,
                    targetUri = targetUri,
                    doc = doc,
                    format = format,
                    scope = exportScope,
                    selectedIds = selectedIds,
                    scale = scale,
                    jpgQuality = jpgQuality,
                    transparentPng = transparentPng,
                    customFontPaths = customFontPaths
                )
            }
            isExporting = false
            res.onSuccess { report ->
                lastValidation = report
                snackbarHostState.showSnackbar("Archivo ${format.extension.uppercase()} guardado y verificado correctamente.")
            }.onFailure {
                snackbarHostState.showSnackbar("Error al guardar: ${it.message}")
            }
        }
    }

    Scaffold(
        containerColor = SpiderBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Exportación") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpiderBlack)
            )
        }
    ) { innerPadding ->
        val doc = document
        if (doc == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpiderViolet)
            }
        } else {
            val cropBounds = if (exportScope == ExportScope.SELECTED_AREA && selectedIds.isNotEmpty()) {
                val elems = doc.layers.filter { it.isVisible }.flatMap { it.elements }.filter { it.id in selectedIds }
                VectorMath.computeCombinedBounds(elems)
            } else null
            val baseW = cropBounds?.width ?: doc.widthPx.toFloat()
            val baseH = cropBounds?.height ?: doc.heightPx.toFloat()
            val (outW, outH) = ExportManager.computeProportionalTargetSize(baseW, baseH, scale)
            val totalLayers = doc.layers.size
            val totalElements = doc.layers.sumOf { it.elements.size }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Live Rendered Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.45f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(SpiderSurface)
                            .border(1.dp, SpiderBorder, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        previewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Vista previa de exportación",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(doc.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (format == ExportFormat.SVG) "Vector escalable (viewBox ${baseW.roundToInt()}×${baseH.roundToInt()})"
                            else "$outW × $outH px",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpiderVioletSoft
                        )
                    }

                    // Format Selector
                    Text("Formato de salida", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportFormat.entries.forEach { f ->
                            FilterChip(
                                selected = format == f,
                                onClick = { format = f },
                                label = { Text(f.label) },
                                modifier = Modifier.testTag("export_format_${f.extension}")
                            )
                        }
                    }

                    // Scope Selector
                    Text("Área a exportar", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = exportScope == ExportScope.FULL_PAGE,
                            onClick = { exportScope = ExportScope.FULL_PAGE },
                            label = { Text(ExportScope.FULL_PAGE.label) }
                        )
                        FilterChip(
                            selected = exportScope == ExportScope.SELECTED_AREA,
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { exportScope = ExportScope.SELECTED_AREA },
                            label = {
                                Text(
                                    if (selectedIds.isEmpty()) "Área seleccionada (sin selección)"
                                    else "Área seleccionada (${selectedIds.size})"
                                )
                            }
                        )
                    }

                    // Format-specific settings
                    if (format == ExportFormat.PNG) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Fondo transparente", style = MaterialTheme.typography.titleSmall)
                                Text("Exporta con canal alfa sin color de fondo.", style = MaterialTheme.typography.bodySmall, color = SpiderTextSecondary)
                            }
                            Switch(checked = transparentPng, onCheckedChange = { transparentPng = it })
                        }
                    }
                    if (format == ExportFormat.JPG) {
                        Column {
                            Text("Calidad JPG: $jpgQuality%", style = MaterialTheme.typography.titleSmall)
                            Slider(
                                value = jpgQuality.toFloat(),
                                onValueChange = { jpgQuality = it.roundToInt() },
                                valueRange = 60f..100f
                            )
                        }
                    }
                    if (format == ExportFormat.PNG || format == ExportFormat.JPG || format == ExportFormat.PDF) {
                        Text("Resolución / Escala", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.5f, 1f, 2f, 3f, 4f).forEach { s ->
                                FilterChip(
                                    selected = scale == s,
                                    onClick = { scale = s },
                                    label = { Text("${if (s == 0.5f) "0.5" else s.toInt().toString()}x") }
                                )
                            }
                        }
                    }

                    // Preservation & Verification Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SpiderSurface)
                            .border(1.dp, SpiderBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Integridad y compatibilidad", style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isExporting = true
                                        val res = runCatching {
                                            ExportManager.exportToCacheAndGetShareUri(
                                                context = context,
                                                doc = doc,
                                                format = format,
                                                scope = exportScope,
                                                selectedIds = selectedIds,
                                                scale = scale,
                                                jpgQuality = jpgQuality,
                                                transparentPng = transparentPng,
                                                customFontPaths = customFontPaths
                                            )
                                        }
                                        isExporting = false
                                        res.onSuccess { exported ->
                                            lastValidation = exported.validation
                                            if (!exported.validation.isValid) {
                                                snackbarHostState.showSnackbar(
                                                    exported.validation.errorMessage ?: "Advertencia en la validación."
                                                )
                                            }
                                        }.onFailure {
                                            snackbarHostState.showSnackbar("Error al verificar: ${it.message}")
                                        }
                                    }
                                },
                                enabled = !isExporting,
                                modifier = Modifier.testTag("export_verify_button")
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = "Verificar archivo", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verificar")
                            }
                        }

                        val preservationDesc = when (format) {
                            ExportFormat.SVG -> "Conserva $totalLayers capas (Inkscape/SVG), $totalElements vectores editables, gradientes, colores y viewBox ${baseW.roundToInt()}×${baseH.roundToInt()}."
                            ExportFormat.PNG -> "Raster sin pérdida a $outW×$outH px${if (transparentPng) " con transparencia alfa" else " con fondo sólido"}."
                            ExportFormat.JPG -> "JPEG RGB a $outW×$outH px (calidad $jpgQuality%) con composición opaca fiel."
                            ExportFormat.PDF -> "Documento PDF estándar (%PDF-1.4, $outW×$outH pt) apto para lectores e impresión."
                            ExportFormat.SPIDER -> "Proyecto editable nativo con $totalLayers capas, curvas Bézier y efectos."
                        }
                        Text(preservationDesc, style = MaterialTheme.typography.bodySmall, color = SpiderTextSecondary)

                        lastValidation?.let { report ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (report.isValid) Color(0x1E22C55E) else Color(0x1EEF4444)
                                    )
                                    .padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (report.isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = if (report.isValid) "Archivo verificado" else "Error de validación",
                                    tint = if (report.isValid) Color(0xFF4ADE80) else Color(0xFFF87171),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        if (report.isValid) "Verificado • ${formatBytes(report.byteSize)}"
                                        else "Error de verificación",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (report.isValid) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                                    )
                                    Text(
                                        report.compatibilityNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SpiderTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Primary Actions Row: Open External + Share + Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    val res = runCatching {
                                        ExportManager.exportToCacheAndGetShareUri(
                                            context = context,
                                            doc = doc,
                                            format = format,
                                            scope = exportScope,
                                            selectedIds = selectedIds,
                                            scale = scale,
                                            jpgQuality = jpgQuality,
                                            transparentPng = transparentPng,
                                            customFontPaths = customFontPaths
                                        )
                                    }
                                    isExporting = false
                                    res.onSuccess { exported ->
                                        lastValidation = exported.validation
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(exported.shareUri, format.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(
                                                Intent.createChooser(viewIntent, "Abrir ${exported.fileName} con...")
                                            )
                                        } catch (_: ActivityNotFoundException) {
                                            snackbarHostState.showSnackbar(
                                                "No hay ninguna aplicación instalada para abrir archivos ${format.extension.uppercase()}."
                                            )
                                        }
                                    }.onFailure {
                                        snackbarHostState.showSnackbar("Error al abrir: ${it.message}")
                                    }
                                }
                            },
                            enabled = !isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("export_open_external_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Abrir en app externa",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Abrir")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    val res = runCatching {
                                        ExportManager.exportToCacheAndGetShareUri(
                                            context = context,
                                            doc = doc,
                                            format = format,
                                            scope = exportScope,
                                            selectedIds = selectedIds,
                                            scale = scale,
                                            jpgQuality = jpgQuality,
                                            transparentPng = transparentPng,
                                            customFontPaths = customFontPaths
                                        )
                                    }
                                    isExporting = false
                                    res.onSuccess { exported ->
                                        lastValidation = exported.validation
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = format.mimeType
                                            putExtra(Intent.EXTRA_STREAM, exported.shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartir ${exported.fileName}"))
                                    }.onFailure {
                                        snackbarHostState.showSnackbar("Error al compartir: ${it.message}")
                                    }
                                }
                            },
                            enabled = !isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("export_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir archivo", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Compartir")
                        }

                        Button(
                            onClick = {
                                val safeName = doc.name.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_-]"), "_")
                                    .ifBlank { "spider_design" }
                                saveDocumentLauncher.launch("$safeName.${format.extension}")
                            },
                            enabled = !isExporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("export_save_file_button")
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "Guardar archivo", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Guardar")
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
    }
}
