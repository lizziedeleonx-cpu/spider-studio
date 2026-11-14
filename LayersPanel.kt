package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.GroupShape
import com.example.model.PaintType
import com.example.model.SpiderDocument
import com.example.model.VectorElement
import com.example.model.VectorLayer
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderSurfaceElevated
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletTint
import kotlin.math.roundToInt

@Composable
fun LayersPanel(
    document: SpiderDocument,
    activeLayerId: String?,
    selectedElementIds: Set<String>,
    onSelectLayer: (String) -> Unit,
    onAddLayer: () -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onRenameLayer: (String, String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onMoveLayer: (String, Boolean) -> Unit,
    onOpacityChange: (String, Float) -> Unit,
    onSelectElement: (String, String) -> Unit,
    onGroupSelected: () -> Unit,
    onUngroupSelected: () -> Unit,
    onCreateClippingMask: () -> Unit = {},
    onToggleGroupMask: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var renamingLayer by remember { mutableStateOf<VectorLayer?>(null) }
    var renameText by remember { mutableStateOf("") }

    val selectedCount = selectedElementIds.size
    val selectedGroups = document.layers.flatMap { it.elements }
        .filter { it.id in selectedElementIds && it is GroupShape }
        .filterIsInstance<GroupShape>()
    val hasGroupSelected = selectedGroups.isNotEmpty()
    val isSingleMaskGroup = selectedGroups.size == 1 && selectedGroups.first().hasClippingMask

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = SpiderViolet)
                Text("Capas (${document.layers.size})", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onAddLayer,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("add_layer_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nueva capa", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Capa")
            }
        }

        if (selectedCount >= 2 || hasGroupSelected) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedCount >= 2) {
                        OutlinedButton(
                            onClick = onGroupSelected,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("group_button")
                        ) {
                            Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Agrupar")
                        }
                        OutlinedButton(
                            onClick = onCreateClippingMask,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("clipping_mask_button")
                        ) {
                            Icon(Icons.Default.LayersClear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Máscara")
                        }
                    }
                }
                if (hasGroupSelected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onUngroupSelected,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("ungroup_button")
                        ) {
                            Icon(Icons.Outlined.Workspaces, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Desagrupar")
                        }
                        if (selectedGroups.size == 1 && selectedGroups.first().children.size >= 2) {
                            OutlinedButton(
                                onClick = onToggleGroupMask,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("toggle_mask_button")
                            ) {
                                Text(if (isSingleMaskGroup) "Soltar máscara" else "Usar máscara")
                            }
                        }
                    }
                }
            }
        }

        val reversedLayers = document.layers.asReversed()
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(reversedLayers, key = { _, l -> l.id }) { idx, layer ->
                val isActive = layer.id == activeLayerId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) SpiderVioletTint else SpiderSurfaceElevated)
                        .border(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) SpiderViolet else SpiderBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectLayer(layer.id) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LayerMiniThumbnail(layer = layer, docW = document.widthPx, docH = document.heightPx)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                layer.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${layer.elements.size} objeto(s) · ${(layer.opacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onToggleVisibility(layer.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Visibilidad de capa",
                                tint = if (layer.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onToggleLock(layer.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Bloquear capa",
                                tint = if (layer.isLocked) SpiderViolet else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { onMoveLayer(layer.id, true) },
                                enabled = idx > 0,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Subir capa")
                            }
                            IconButton(
                                onClick = { onMoveLayer(layer.id, false) },
                                enabled = idx < reversedLayers.lastIndex,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar capa")
                            }
                            IconButton(
                                onClick = {
                                    renamingLayer = layer
                                    renameText = layer.name
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Renombrar capa")
                            }
                            IconButton(
                                onClick = { onDuplicateLayer(layer.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicar capa")
                            }
                            IconButton(
                                onClick = { onDeleteLayer(layer.id) },
                                enabled = document.layers.size > 1,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar capa")
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Opacidad", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(68.dp))
                            Slider(
                                value = layer.opacity,
                                onValueChange = { onOpacityChange(layer.id, it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (layer.elements.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                layer.elements.asReversed().forEach { el ->
                                    val elSelected = el.id in selectedElementIds
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (elSelected) SpiderViolet.copy(alpha = 0.25f) else SpiderSurface)
                                            .clickable { onSelectElement(layer.id, el.id) }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            el.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (el is GroupShape && el.hasClippingMask) {
                                            Text(
                                                "MÁSCARA",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SpiderViolet,
                                                modifier = Modifier
                                                    .padding(end = 6.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(SpiderViolet.copy(alpha = 0.18f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            "${el.width.roundToInt()}×${el.height.roundToInt()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renamingLayer != null) {
        AlertDialog(
            onDismissRequest = { renamingLayer = null },
            title = { Text("Renombrar capa") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nombre de la capa") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    renamingLayer?.let { onRenameLayer(it.id, renameText) }
                    renamingLayer = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renamingLayer = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun LayerMiniThumbnail(layer: VectorLayer, docW: Int, docH: Int) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D0D14))
            .border(1.dp, SpiderBorder, RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize().padding(3.dp)) {
            val sx = size.width / docW.coerceAtLeast(1)
            val sy = size.height / docH.coerceAtLeast(1)
            layer.elements.take(12).forEach { el: VectorElement ->
                val color = when {
                    el.fill.type != PaintType.NONE -> Color(el.fill.colorArgb)
                    el.stroke.enabled -> Color(el.stroke.colorArgb)
                    else -> SpiderViolet
                }
                drawRect(
                    color = color.copy(alpha = 0.85f),
                    topLeft = Offset(el.x * sx, el.y * sy),
                    size = Size((el.width * sx).coerceAtLeast(2f), (el.height * sy).coerceAtLeast(2f))
                )
            }
        }
    }
}
