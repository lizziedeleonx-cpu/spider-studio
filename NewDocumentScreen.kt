package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.PaletteEntity
import com.example.model.DocumentBackgroundMode
import com.example.model.DocumentPresets
import com.example.model.PresetCategory
import com.example.ui.components.ColorPickerModal
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderSurfaceElevated
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderViolet
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDocumentScreen(
    colorHistory: List<Long>,
    palettes: List<PaletteEntity>,
    onBack: () -> Unit,
    onCreateDocument: (
        name: String,
        widthPx: Int,
        heightPx: Int,
        bgMode: DocumentBackgroundMode,
        bgColorArgb: Long
    ) -> Unit
) {
    var name by remember { mutableStateOf("Diseño Nuevo") }
    var widthStr by remember { mutableStateOf("1080") }
    var heightStr by remember { mutableStateOf("1080") }
    var selectedCategory by remember { mutableStateOf(PresetCategory.SOCIAL) }
    var bgMode by remember { mutableStateOf(DocumentBackgroundMode.SOLID) }
    var bgColor by remember { mutableLongStateOf(0xFF14141C) }
    var showColorPicker by remember { mutableStateOf(false) }

    val w = widthStr.toIntOrNull()?.coerceIn(64, 8192) ?: 1080
    val h = heightStr.toIntOrNull()?.coerceIn(64, 8192) ?: 1080
    val isPortrait = h >= w

    Scaffold(
        containerColor = SpiderBlack,
        topBar = {
            TopAppBar(
                title = { Text("Nuevo documento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpiderBlack)
            )
        }
    ) { innerPadding ->
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del proyecto") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_doc_name_input")
                )

                // Dimensiones & Orientación
                Text("Dimensiones y orientación", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = widthStr,
                        onValueChange = { widthStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Ancho (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_doc_width_input")
                    )
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Alto (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_doc_height_input")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = isPortrait,
                        onClick = {
                            val minD = min(w, h)
                            val maxD = max(w, h)
                            widthStr = minD.toString()
                            heightStr = maxD.toString()
                        },
                        leadingIcon = { Icon(Icons.Default.CropPortrait, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Vertical") }
                    )
                    FilterChip(
                        selected = !isPortrait,
                        onClick = {
                            val minD = min(w, h)
                            val maxD = max(w, h)
                            widthStr = maxD.toString()
                            heightStr = minD.toString()
                        },
                        leadingIcon = { Icon(Icons.Default.CropLandscape, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Horizontal") }
                    )
                }

                // Format Presets by Category
                Text("Formatos preestablecidos", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentPresets.all.filter { it.category == selectedCategory }.forEach { preset ->
                        val active = w == preset.widthPx && h == preset.heightPx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) SpiderViolet.copy(alpha = 0.16f) else SpiderSurface)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) SpiderViolet else SpiderBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    widthStr = preset.widthPx.toString()
                                    heightStr = preset.heightPx.toString()
                                    if (name == "Diseño Nuevo") name = preset.title
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(preset.title, style = MaterialTheme.typography.titleSmall)
                                Text(preset.subtitle, style = MaterialTheme.typography.bodySmall, color = SpiderTextSecondary)
                            }
                            if (active) {
                                Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = SpiderViolet)
                            }
                        }
                    }
                }

                // Background Mode & Color
                Text("Fondo del lienzo", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = bgMode == DocumentBackgroundMode.SOLID,
                        onClick = { bgMode = DocumentBackgroundMode.SOLID },
                        label = { Text("Color sólido") }
                    )
                    FilterChip(
                        selected = bgMode == DocumentBackgroundMode.TRANSPARENT,
                        onClick = { bgMode = DocumentBackgroundMode.TRANSPARENT },
                        label = { Text("Transparente") }
                    )
                }

                if (bgMode == DocumentBackgroundMode.SOLID) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DocumentPresets.defaultBackgroundSwatches.forEach { swatch ->
                            val selected = bgColor == swatch
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatch))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) SpiderViolet else SpiderBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { bgColor = swatch }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SpiderSurfaceElevated)
                                .border(1.dp, SpiderBorder, RoundedCornerShape(20.dp))
                                .clickable { showColorPicker = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp), tint = SpiderViolet)
                            Text("Personalizado", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onCreateDocument(name, w, h, bgMode, bgColor) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("create_document_confirm_button")
                ) {
                    Text("Crear y abrir en el Editor", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showColorPicker) {
        ColorPickerModal(
            title = "Color de fondo del documento",
            initialColorArgb = bgColor,
            colorHistory = colorHistory,
            palettes = palettes,
            onDismiss = { showColorPicker = false },
            onColorSelected = { bgColor = it }
        )
    }
}
