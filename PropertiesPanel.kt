package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.CustomFontEntity
import com.example.data.PaletteEntity
import com.example.engine.VectorMath
import com.example.model.AlignAction
import com.example.model.BezierNode
import com.example.model.BlendModeStyle
import com.example.model.BooleanOp
import com.example.model.BrushPreset
import com.example.model.EffectStyle
import com.example.model.EllipseShape
import com.example.model.GradientStop
import com.example.model.GroupShape
import com.example.model.ImageShape
import com.example.model.NodeHandleMode
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.StarShape
import com.example.model.StrokeStyle
import com.example.model.TextAlignMode
import com.example.model.TextCaseMode
import com.example.model.TextShape
import com.example.model.VectorElement
import com.example.model.ZOrderAction
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderError
import com.example.ui.theme.SpiderSurfaceElevated
import com.example.ui.theme.SpiderViolet
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface ColorTarget {
    data object FillPrimary : ColorTarget
    data object FillSecondary : ColorTarget
    data class StopIndex(val index: Int) : ColorTarget
    data object Stroke : ColorTarget
    data object Shadow : ColorTarget
    data object Glow : ColorTarget
    data object TextBadge : ColorTarget
}

@Composable
fun PropertiesPanel(
    selectedElements: List<VectorElement>,
    canPaste: Boolean,
    colorHistory: List<Long>,
    palettes: List<PaletteEntity>,
    customFonts: List<CustomFontEntity>,
    onUpdateSelected: (Boolean, (VectorElement) -> VectorElement) -> Unit,
    onAlign: (AlignAction) -> Unit,
    onZOrder: (ZOrderAction) -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDelete: () -> Unit,
    onColorUsed: (Long) -> Unit,
    onSavePaletteColor: (String, List<Long>) -> Unit,
    onTogglePathClosed: () -> Unit,
    onToggleSmoothNode: () -> Unit,
    onAddMidpointNode: () -> Unit,
    onDeleteNode: () -> Unit,
    activeNode: BezierNode? = null,
    onSetNodeHandleMode: (NodeHandleMode) -> Unit = {},
    onConvertToPath: () -> Unit = {},
    onBooleanOp: (BooleanOp) -> Unit = {},
    onReversePath: () -> Unit = {},
    onSimplifyPath: () -> Unit = {},
    onBreakApartPath: () -> Unit = {},
    onUngroup: () -> Unit = {},
    onOpenChromeStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeColorTarget by remember { mutableStateOf<ColorTarget?>(null) }

    val primary = selectedElements.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = SpiderViolet)
                Text(
                    if (primary != null) primary.name else "Propiedades",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (canPaste) {
                OutlinedButton(onClick = onPaste, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pegar")
                }
            }
        }

        if (primary == null) {
            Text(
                "Selecciona un objeto en el lienzo o en el panel de capas para editar geometría, rellenos, degradados, sombras o tipografía.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        // Quick Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(onClick = onCopy, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(16.dp))
            }
            OutlinedButton(onClick = onDuplicate, modifier = Modifier.height(48.dp)) {
                Text("Duplicar", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            OutlinedButton(onClick = { onZOrder(ZOrderAction.BRING_TO_FRONT) }, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.FlipToFront, contentDescription = "Traer al frente", modifier = Modifier.size(16.dp))
            }
            OutlinedButton(onClick = { onZOrder(ZOrderAction.SEND_TO_BACK) }, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.FlipToBack, contentDescription = "Enviar atrás", modifier = Modifier.size(16.dp))
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = SpiderError.copy(alpha = 0.2f), contentColor = SpiderError),
                modifier = Modifier.height(48.dp).testTag("delete_element_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(18.dp))
            }
        }

        // Alignment
        Text("Alineación y distribución", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Izq" to AlignAction.LEFT,
                "Centro H" to AlignAction.CENTER_H,
                "Der" to AlignAction.RIGHT,
                "Arr" to AlignAction.TOP,
                "Centro V" to AlignAction.CENTER_V,
                "Abajo" to AlignAction.BOTTOM,
                "Dist H" to AlignAction.DISTRIBUTE_H,
                "Dist V" to AlignAction.DISTRIBUTE_V
            ).forEach { (label, act) ->
                FilterChip(
                    selected = false,
                    onClick = { onAlign(act) },
                    label = { Text(label) }
                )
            }
        }

        // Boolean Operations when 2+ geometric/path shapes are selected
        val geometricCount = selectedElements.count {
            it is RectShape || it is EllipseShape || it is PolygonShape || it is StarShape || it is PathShape
        }
        if (geometricCount >= 2) {
            HorizontalDivider(color = SpiderBorder)
            Text("Operaciones booleanas ($geometricCount formas)", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BooleanOp.entries.forEach { op ->
                    OutlinedButton(
                        onClick = { onBooleanOp(op) },
                        modifier = Modifier.height(40.dp).testTag("prop_boolean_${op.name.lowercase()}")
                    ) {
                        Text(op.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        HorizontalDivider(color = SpiderBorder)

        // Transform & Geometry
        Text("Transformación", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("X: ${primary.x.roundToInt()}  Y: ${primary.y.roundToInt()}", style = MaterialTheme.typography.labelSmall)
            Text("Ancho: ${primary.width.roundToInt()}  Alto: ${primary.height.roundToInt()}", style = MaterialTheme.typography.labelSmall)
        }
        SliderRow("Rotación", "${primary.rotationDeg.roundToInt()}°", primary.rotationDeg, -180f..180f) { rot ->
            onUpdateSelected(false) { VectorMath.updateElementTransform(it, rotationDeg = rot) }
        }
        SliderRow("Opacidad", "${(primary.opacity * 100).roundToInt()}%", primary.opacity, 0.05f..1f) { op ->
            onUpdateSelected(false) { VectorMath.updateElementTransform(it, opacity = op) }
        }

        when (primary) {
            is RectShape -> {
                SliderRow("Radio esquina", "${primary.cornerRadius.roundToInt()} px", primary.cornerRadius, 0f..200f) { r ->
                    onUpdateSelected(false) { if (it is RectShape) it.copy(cornerRadius = r) else it }
                }
                OutlinedButton(
                    onClick = onConvertToPath,
                    modifier = Modifier.fillMaxWidth().testTag("convert_to_path_button")
                ) {
                    Text("Convertir a trazado editable (Curvas)")
                }
            }
            is EllipseShape -> {
                OutlinedButton(
                    onClick = onConvertToPath,
                    modifier = Modifier.fillMaxWidth().testTag("convert_to_path_button")
                ) {
                    Text("Convertir a trazado editable (Curvas)")
                }
            }
            is PolygonShape -> {
                SliderRow("Lados", "${primary.sides}", primary.sides.toFloat(), 3f..12f) { s ->
                    onUpdateSelected(false) { if (it is PolygonShape) it.copy(sides = s.roundToInt()) else it }
                }
                OutlinedButton(
                    onClick = onConvertToPath,
                    modifier = Modifier.fillMaxWidth().testTag("convert_to_path_button")
                ) {
                    Text("Convertir a trazado editable (Curvas)")
                }
            }
            is StarShape -> {
                SliderRow("Puntas", "${primary.points}", primary.points.toFloat(), 3f..16f) { p ->
                    onUpdateSelected(false) { if (it is StarShape) it.copy(points = p.roundToInt()) else it }
                }
                SliderRow("Radio interior", "${(primary.innerRadiusRatio * 100).roundToInt()}%", primary.innerRadiusRatio, 0.15f..0.85f) { r ->
                    onUpdateSelected(false) { if (it is StarShape) it.copy(innerRadiusRatio = r) else it }
                }
                OutlinedButton(
                    onClick = onConvertToPath,
                    modifier = Modifier.fillMaxWidth().testTag("convert_to_path_button")
                ) {
                    Text("Convertir a trazado editable (Curvas)")
                }
            }
            is PathShape -> {
                val totalContours = 1 + primary.extraContours.size
                Text(
                    "Edición de Trazado (${primary.nodes.size} nodos" +
                        if (totalContours > 1) " · $totalContours contornos)" else ")",
                    style = MaterialTheme.typography.titleSmall
                )
                if (activeNode != null) {
                    val currentMode = if (activeNode.handleMode == NodeHandleMode.CORNER && activeNode.smooth) {
                        NodeHandleMode.SMOOTH
                    } else activeNode.handleMode
                    Text("Tipo de nodo seleccionado", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Esquina" to NodeHandleMode.CORNER,
                            "Suave" to NodeHandleMode.SMOOTH,
                            "Simétrico" to NodeHandleMode.SYMMETRIC
                        ).forEach { (label, mode) ->
                            FilterChip(
                                selected = currentMode == mode,
                                onClick = { onSetNodeHandleMode(mode) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onTogglePathClosed, modifier = Modifier.weight(1f)) {
                        Text(if (primary.isClosed) "Abrir trazo" else "Cerrar trazo")
                    }
                    OutlinedButton(onClick = onToggleSmoothNode, modifier = Modifier.weight(1f)) {
                        Text("Modo nodo")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onAddMidpointNode, modifier = Modifier.weight(1f)) {
                        Text("+ Nodo")
                    }
                    OutlinedButton(onClick = onDeleteNode, modifier = Modifier.weight(1f)) {
                        Text("- Nodo")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onReversePath, modifier = Modifier.weight(1f)) {
                        Text("Invertir")
                    }
                    OutlinedButton(onClick = onSimplifyPath, modifier = Modifier.weight(1f)) {
                        Text("Simplificar")
                    }
                }
                if (primary.extraContours.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onBreakApartPath,
                        modifier = Modifier.fillMaxWidth().testTag("break_apart_path_button")
                    ) {
                        Text("Separar contornos ($totalContours)")
                    }
                }
                if (primary.isBrushStroke) {
                    HorizontalDivider(color = SpiderBorder)
                    Text("Ajustes de pincel", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BrushPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = primary.brushPreset == preset,
                                onClick = {
                                    onUpdateSelected(true) { el ->
                                        if (el is PathShape) {
                                            el.copy(
                                                brushPreset = preset,
                                                stroke = el.stroke.copy(dashed = preset == BrushPreset.ARTISTIC_DASH)
                                            )
                                        } else el
                                    }
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Afinar extremos (Taper)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = primary.brushTaperEnds,
                            onCheckedChange = { taper ->
                                onUpdateSelected(true) { el ->
                                    if (el is PathShape) el.copy(brushTaperEnds = taper) else el
                                }
                            }
                        )
                    }
                }
            }
            is GroupShape -> {
                HorizontalDivider(color = SpiderBorder)
                Text("Grupo y máscara de recorte", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${primary.children.size} objeto(s) dentro del grupo. El primer objeto define el área visible cuando la máscara está activa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Activar máscara de recorte", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = primary.hasClippingMask,
                        enabled = primary.children.size >= 2,
                        onCheckedChange = { masked ->
                            onUpdateSelected(true) { el ->
                                if (el is GroupShape) el.copy(hasClippingMask = masked) else el
                            }
                        }
                    )
                }
                if (primary.hasClippingMask) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mostrar contorno de máscara", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = primary.showMaskOutline,
                            onCheckedChange = { show ->
                                onUpdateSelected(true) { el ->
                                    if (el is GroupShape) el.copy(showMaskOutline = show) else el
                                }
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = onUngroup,
                    modifier = Modifier.fillMaxWidth().testTag("prop_ungroup_button")
                ) {
                    Text("Desagrupar / Liberar elementos")
                }
            }
            else -> Unit
        }

        // Typography Section
        if (primary is TextShape) {
            HorizontalDivider(color = SpiderBorder)
            Text("Tipografía avanzada", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = primary.text,
                onValueChange = { txt ->
                    onUpdateSelected(true) { if (it is TextShape) it.copy(text = txt) else it }
                },
                label = { Text("Texto editable (admite acentos y ñ)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("text_content_input")
            )
            val builtInFonts = listOf(
                "space_grotesk" to "Space Grotesk",
                "plus_jakarta_sans" to "Jakarta Sans",
                "jetbrains_mono" to "JetBrains Mono",
                "sans" to "Sistema Sans",
                "serif" to "Sistema Serif"
            ) + customFonts.map { it.id to it.displayName }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                builtInFonts.forEach { (key, label) ->
                    FilterChip(
                        selected = primary.fontFamilyKey == key,
                        onClick = {
                            onUpdateSelected(true) { if (it is TextShape) it.copy(fontFamilyKey = key) else it }
                        },
                        label = { Text(label) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = {
                    onUpdateSelected(true) { if (it is TextShape) it.copy(isBold = !it.isBold) else it }
                }) {
                    Icon(
                        Icons.Default.FormatBold,
                        contentDescription = "Negrita",
                        tint = if (primary.isBold) SpiderViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    onUpdateSelected(true) { if (it is TextShape) it.copy(isItalic = !it.isItalic) else it }
                }) {
                    Icon(
                        Icons.Default.FormatItalic,
                        contentDescription = "Cursiva",
                        tint = if (primary.isItalic) SpiderViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    onUpdateSelected(true) { if (it is TextShape) it.copy(isUnderline = !it.isUnderline) else it }
                }) {
                    Icon(
                        Icons.Default.FormatUnderlined,
                        contentDescription = "Subrayado",
                        tint = if (primary.isUnderline) SpiderViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    onUpdateSelected(true) { if (it is TextShape) it.copy(isStrikethrough = !it.isStrikethrough) else it }
                }) {
                    Icon(
                        Icons.Default.FormatStrikethrough,
                        contentDescription = "Tachado",
                        tint = if (primary.isStrikethrough) SpiderViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = {
                    onUpdateSelected(true) { if (it is TextShape) it.copy(alignment = TextAlignMode.LEFT) else it }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatAlignLeft,
                        contentDescription = "Alinear izquierda",
                        tint = if (primary.alignment == TextAlignMode.LEFT) SpiderViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    onUpdateSelected(true) { if (it is TextShape) it.copy(alignment = TextAlignMode.CENTER) else it }
                }) {
                    Icon(
                        Icons.Default.FormatAlignCenter,
                        contentDescription = "Centrar texto",
                        tint = if (primary.alignment == TextAlignMode.CENTER) SpiderViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    onUpdateSelected(true) { if (it is TextShape) it.copy(alignment = TextAlignMode.RIGHT) else it }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatAlignRight,
                        contentDescription = "Alinear derecha",
                        tint = if (primary.alignment == TextAlignMode.RIGHT) SpiderViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text("Transformación de mayúsculas", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextCaseMode.entries.forEach { mode ->
                    FilterChip(
                        selected = primary.textTransform == mode,
                        onClick = {
                            onUpdateSelected(true) { if (it is TextShape) it.copy(textTransform = mode) else it }
                        },
                        label = { Text(mode.label) }
                    )
                }
            }
            SliderRow("Tamaño fuente", "${primary.fontSize.roundToInt()} px", primary.fontSize, 14f..220f) { fs ->
                onUpdateSelected(false) { if (it is TextShape) it.copy(fontSize = fs) else it }
            }
            SliderRow("Espaciado letras", String.format(Locale.US, "%.2f", primary.letterSpacing), primary.letterSpacing, -0.05f..0.4f) { ls ->
                onUpdateSelected(false) { if (it is TextShape) it.copy(letterSpacing = ls) else it }
            }
            SliderRow("Interlineado", String.format(Locale.US, "%.2fx", primary.lineHeightMultiplier), primary.lineHeightMultiplier, 0.8f..2.5f) { lh ->
                onUpdateSelected(false) { if (it is TextShape) it.copy(lineHeightMultiplier = lh) else it }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (kotlin.math.abs(primary.curveRadius) < 10f) "Texto curvo: Recto"
                    else "Texto curvo: ${primary.curveRadius.roundToInt()} px",
                    style = MaterialTheme.typography.labelMedium
                )
                if (kotlin.math.abs(primary.curveRadius) >= 10f) {
                    TextButton(onClick = {
                        onUpdateSelected(true) { if (it is TextShape) it.copy(curveRadius = 0f) else it }
                    }) { Text("Enderezar") }
                }
            }
            Slider(
                value = primary.curveRadius.coerceIn(-600f, 600f),
                onValueChange = { cr ->
                    val snapped = if (kotlin.math.abs(cr) < 28f) 0f else cr
                    onUpdateSelected(false) { if (it is TextShape) it.copy(curveRadius = snapped) else it }
                },
                valueRange = -600f..600f
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fondo tipo etiqueta", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = primary.backgroundBadgeEnabled,
                    onCheckedChange = { en ->
                        onUpdateSelected(true) { if (it is TextShape) it.copy(backgroundBadgeEnabled = en) else it }
                    }
                )
            }
            if (primary.backgroundBadgeEnabled) {
                ColorSwatchButton(
                    label = "Color de etiqueta",
                    colorArgb = primary.backgroundBadgeColorArgb,
                    onClick = { activeColorTarget = ColorTarget.TextBadge },
                    modifier = Modifier.fillMaxWidth()
                )
                SliderRow("Relleno etiqueta", "${primary.backgroundPaddingPx.roundToInt()} px", primary.backgroundPaddingPx, 4f..48f) { pad ->
                    onUpdateSelected(false) { if (it is TextShape) it.copy(backgroundPaddingPx = pad) else it }
                }
            }
        }

        // Fill & Stroke Section (non-image/group elements)
        if (primary !is ImageShape && primary !is GroupShape) {
            HorizontalDivider(color = SpiderBorder)
            Text("Relleno y degradados", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Sin relleno" to PaintType.NONE,
                    "Sólido" to PaintType.SOLID,
                    "Lineal" to PaintType.LINEAR_GRADIENT,
                    "Radial" to PaintType.RADIAL_GRADIENT,
                    "Cónico" to PaintType.SWEEP_GRADIENT
                ).forEach { (label, pt) ->
                    FilterChip(
                        selected = primary.fill.type == pt,
                        onClick = {
                            onUpdateSelected(true) { el ->
                                val nextStops = if (pt != PaintType.NONE && pt != PaintType.SOLID && el.fill.stops.size < 2) {
                                    listOf(
                                        GradientStop(0f, el.fill.colorArgb),
                                        GradientStop(1f, el.fill.secondaryColorArgb)
                                    )
                                } else el.fill.stops
                                updateElementFill(el, el.fill.copy(type = pt, stops = nextStops))
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
            if (primary.fill.type != PaintType.NONE) {
                val isGradient = primary.fill.type == PaintType.LINEAR_GRADIENT ||
                    primary.fill.type == PaintType.RADIAL_GRADIENT ||
                    primary.fill.type == PaintType.SWEEP_GRADIENT

                if (!isGradient) {
                    ColorSwatchButton(
                        label = "Color relleno",
                        colorArgb = primary.fill.colorArgb,
                        onClick = { activeColorTarget = ColorTarget.FillPrimary },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val effectiveStops = effectiveGradientStops(primary.fill)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Paradas de color (${effectiveStops.size})", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(
                            onClick = {
                                onUpdateSelected(true) { el ->
                                    val stops = effectiveGradientStops(el.fill).toMutableList()
                                    val midColor = if (stops.size % 2 == 0) 0xFFEC4899 else 0xFF22D3EE
                                    stops.add(GradientStop(0.5f, midColor))
                                    val sorted = stops.sortedBy { it.offset }
                                    updateElementFill(
                                        el,
                                        el.fill.copy(
                                            stops = sorted,
                                            colorArgb = sorted.first().colorArgb,
                                            secondaryColorArgb = sorted.last().colorArgb
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("+ Parada", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    effectiveStops.forEachIndexed { idx, stop ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(stop.colorArgb))
                                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                    .clickable { activeColorTarget = ColorTarget.StopIndex(idx) }
                            )
                            Text(
                                "${(stop.offset * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(34.dp)
                            )
                            Slider(
                                value = stop.offset.coerceIn(0f, 1f),
                                onValueChange = { nextOffset ->
                                    onUpdateSelected(false) { el ->
                                        val mutable = effectiveGradientStops(el.fill).toMutableList()
                                        if (idx in mutable.indices) {
                                            mutable[idx] = mutable[idx].copy(offset = nextOffset)
                                        }
                                        val sorted = mutable.sortedBy { it.offset }
                                        updateElementFill(
                                            el,
                                            el.fill.copy(
                                                stops = sorted,
                                                colorArgb = sorted.first().colorArgb,
                                                secondaryColorArgb = sorted.last().colorArgb
                                            )
                                        )
                                    }
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            if (effectiveStops.size > 2) {
                                IconButton(
                                    onClick = {
                                        onUpdateSelected(true) { el ->
                                            val mutable = effectiveGradientStops(el.fill).toMutableList()
                                            if (idx in mutable.indices && mutable.size > 2) {
                                                mutable.removeAt(idx)
                                            }
                                            updateElementFill(
                                                el,
                                                el.fill.copy(
                                                    stops = mutable,
                                                    colorArgb = mutable.first().colorArgb,
                                                    secondaryColorArgb = mutable.last().colorArgb
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar parada", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    if (primary.fill.type == PaintType.LINEAR_GRADIENT) {
                        SliderRow("Ángulo degradado", "${primary.fill.gradientAngleDeg.roundToInt()}°", primary.fill.gradientAngleDeg, 0f..360f) { ang ->
                            onUpdateSelected(false) { el -> updateElementFill(el, el.fill.copy(gradientAngleDeg = ang)) }
                        }
                    } else {
                        SliderRow("Centro focal X", "${(primary.fill.gradientCenterX * 100).roundToInt()}%", primary.fill.gradientCenterX, 0f..1f) { cx ->
                            onUpdateSelected(false) { el -> updateElementFill(el, el.fill.copy(gradientCenterX = cx)) }
                        }
                        SliderRow("Centro focal Y", "${(primary.fill.gradientCenterY * 100).roundToInt()}%", primary.fill.gradientCenterY, 0f..1f) { cy ->
                            onUpdateSelected(false) { el -> updateElementFill(el, el.fill.copy(gradientCenterY = cy)) }
                        }
                        if (primary.fill.type == PaintType.RADIAL_GRADIENT) {
                            SliderRow("Radio radial", "${(primary.fill.radialRadiusFactor * 100).roundToInt()}%", primary.fill.radialRadiusFactor, 0.15f..1.5f) { rf ->
                                onUpdateSelected(false) { el -> updateElementFill(el, el.fill.copy(radialRadiusFactor = rf)) }
                            }
                        }
                    }
                }
                SliderRow("Opacidad relleno", "${(primary.fill.opacity * 100).roundToInt()}%", primary.fill.opacity, 0f..1f) { fo ->
                    onUpdateSelected(false) { el -> updateElementFill(el, el.fill.copy(opacity = fo)) }
                }
            }

            // Stroke Section
            HorizontalDivider(color = SpiderBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Contorno / Trazo", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = primary.stroke.enabled,
                    onCheckedChange = { enabled ->
                        onUpdateSelected(true) { el -> updateElementStroke(el, el.stroke.copy(enabled = enabled)) }
                    }
                )
            }
            if (primary.stroke.enabled) {
                ColorSwatchButton(
                    label = "Color de contorno",
                    colorArgb = primary.stroke.colorArgb,
                    onClick = { activeColorTarget = ColorTarget.Stroke },
                    modifier = Modifier.fillMaxWidth()
                )
                SliderRow("Grosor", "${primary.stroke.width.roundToInt()} px", primary.stroke.width, 1f..64f) { w ->
                    onUpdateSelected(false) { el -> updateElementStroke(el, el.stroke.copy(width = w)) }
                }
                SliderRow("Opacidad contorno", "${(primary.stroke.opacity * 100).roundToInt()}%", primary.stroke.opacity, 0.05f..1f) { so ->
                    onUpdateSelected(false) { el -> updateElementStroke(el, el.stroke.copy(opacity = so)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Línea discontinua", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = primary.stroke.dashed,
                        onCheckedChange = { d ->
                            onUpdateSelected(true) { el -> updateElementStroke(el, el.stroke.copy(dashed = d)) }
                        }
                    )
                }
            }
        }

        // CHROME EFFECTS Quick Shortcut Card
        HorizontalDivider(color = SpiderBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (primary.effects.chrome.enabled) SpiderViolet.copy(alpha = 0.18f) else SpiderSurfaceElevated
                )
                .border(
                    width = 1.dp,
                    color = if (primary.effects.chrome.enabled) SpiderViolet else SpiderBorder,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onOpenChromeStudio)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("properties_open_chrome_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SpiderViolet)
                Column {
                    Text("CHROME EFFECTS", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (primary.effects.chrome.enabled) {
                            "${primary.effects.chrome.styleType.label} · Reflejo ${(primary.effects.chrome.reflectionIntensity * 100).roundToInt()}%"
                        } else {
                            "Classic, Liquid, 3D, Holographic, Gold y Rose Gold"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onOpenChromeStudio) {
                Text(if (primary.effects.chrome.enabled) "Editar" else "Abrir")
            }
        }

        // Universal Effects, Blend Modes, Shadows & Glow Section (all elements)
        HorizontalDivider(color = SpiderBorder)
        Text("Fusión, sombras y efectos", style = MaterialTheme.typography.titleSmall)
        Text("Modo de fusión", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BlendModeStyle.entries.forEach { bm ->
                FilterChip(
                    selected = primary.effects.blendMode == bm,
                    onClick = {
                        onUpdateSelected(true) { el -> updateElementEffects(el, el.effects.copy(blendMode = bm)) }
                    },
                    label = { Text(bm.label) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sombra paralela", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = primary.effects.shadowEnabled,
                onCheckedChange = { en ->
                    onUpdateSelected(true) { el -> updateElementEffects(el, el.effects.copy(shadowEnabled = en)) }
                }
            )
        }
        if (primary.effects.shadowEnabled) {
            ColorSwatchButton(
                label = "Color de sombra",
                colorArgb = primary.effects.shadowColorArgb,
                onClick = { activeColorTarget = ColorTarget.Shadow },
                modifier = Modifier.fillMaxWidth()
            )
            SliderRow("Opacidad sombra", "${(primary.effects.shadowOpacity * 100).roundToInt()}%", primary.effects.shadowOpacity, 0.1f..1f) { so ->
                onUpdateSelected(false) { el -> updateElementEffects(el, el.effects.copy(shadowOpacity = so)) }
            }
            SliderRow("Desenfoque sombra", "${primary.effects.shadowBlur.roundToInt()} px", primary.effects.shadowBlur, 1f..48f) { b ->
                onUpdateSelected(false) { el -> updateElementEffects(el, el.effects.copy(shadowBlur = b)) }
            }
            SliderRow("Desplazamiento X", "${primary.effects.shadowOffsetX.roundToInt()} px", primary.effects.shadowOffsetX, -40f..40f) { dx ->
                onUpdateSelected(false) { el -> updateElementEffects(el, el.effects.copy(shadowOffsetX = dx)) }
            }
            SliderRow("Desplazamiento Y", "${primary.effects.shadowOffsetY.roundToInt()} px", primary.effects.shadowOffsetY, -40f..40f) { dy ->
                onUpdateSelected(false) { el -> updateElementEffects(el, el.effects.copy(shadowOffsetY = dy)) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Resplandor exterior (Glow)", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = primary.effects.glowEnabled,
                onCheckedChange = { en ->
                    onUpdateSelected(true) { el -> updateElementEffects(el, el.effects.copy(glowEnabled = en)) }
                }
            )
        }
        if (primary.effects.glowEnabled) {
            ColorSwatchButton(
                label = "Color de resplandor",
                colorArgb = primary.effects.glowColorArgb,
                onClick = { activeColorTarget = ColorTarget.Glow },
                modifier = Modifier.fillMaxWidth()
            )
            SliderRow("Radio resplandor", "${primary.effects.glowRadius.roundToInt()} px", primary.effects.glowRadius, 2f..48f) { gr ->
                onUpdateSelected(false) { el -> updateElementEffects(el, el.effects.copy(glowRadius = gr)) }
            }
        }

        SliderRow("Desenfoque gaussiano", "${primary.effects.blurRadius.roundToInt()} px", primary.effects.blurRadius, 0f..32f) { b ->
            onUpdateSelected(false) { el -> updateElementEffects(el, el.effects.copy(blurRadius = b)) }
        }
    }

    val target = activeColorTarget
    if (target != null && primary != null) {
        val initColor = when (target) {
            ColorTarget.FillPrimary -> primary.fill.colorArgb
            ColorTarget.FillSecondary -> primary.fill.secondaryColorArgb
            is ColorTarget.StopIndex -> {
                effectiveGradientStops(primary.fill).getOrNull(target.index)?.colorArgb ?: primary.fill.colorArgb
            }
            ColorTarget.Stroke -> primary.stroke.colorArgb
            ColorTarget.Shadow -> primary.effects.shadowColorArgb
            ColorTarget.Glow -> primary.effects.glowColorArgb
            ColorTarget.TextBadge -> (primary as? TextShape)?.backgroundBadgeColorArgb ?: 0xCC1E1E2A
        }
        ColorPickerModal(
            title = when (target) {
                ColorTarget.FillPrimary -> "Color de relleno"
                ColorTarget.FillSecondary -> "Color secundario"
                is ColorTarget.StopIndex -> "Color de parada #${target.index + 1}"
                ColorTarget.Stroke -> "Color de contorno"
                ColorTarget.Shadow -> "Color de sombra"
                ColorTarget.Glow -> "Color de resplandor"
                ColorTarget.TextBadge -> "Color de etiqueta"
            },
            initialColorArgb = initColor,
            colorHistory = colorHistory,
            palettes = palettes,
            onDismiss = { activeColorTarget = null },
            onColorSelected = { chosen ->
                onColorUsed(chosen)
                onUpdateSelected(true) { el ->
                    when (target) {
                        ColorTarget.FillPrimary -> updateElementFill(el, el.fill.copy(colorArgb = chosen))
                        ColorTarget.FillSecondary -> updateElementFill(el, el.fill.copy(secondaryColorArgb = chosen))
                        is ColorTarget.StopIndex -> {
                            val stops = effectiveGradientStops(el.fill).toMutableList()
                            if (target.index in stops.indices) {
                                stops[target.index] = stops[target.index].copy(colorArgb = chosen)
                            }
                            updateElementFill(
                                el,
                                el.fill.copy(
                                    stops = stops,
                                    colorArgb = stops.first().colorArgb,
                                    secondaryColorArgb = stops.last().colorArgb
                                )
                            )
                        }
                        ColorTarget.Stroke -> updateElementStroke(el, el.stroke.copy(colorArgb = chosen))
                        ColorTarget.Shadow -> updateElementEffects(el, el.effects.copy(shadowColorArgb = chosen))
                        ColorTarget.Glow -> updateElementEffects(el, el.effects.copy(glowColorArgb = chosen))
                        ColorTarget.TextBadge -> if (el is TextShape) el.copy(backgroundBadgeColorArgb = chosen) else el
                    }
                }
            },
            onSavePaletteColor = onSavePaletteColor
        )
    }
}

private fun effectiveGradientStops(fill: PaintStyle): List<GradientStop> {
    return if (fill.stops.size >= 2) fill.stops.sortedBy { it.offset }
    else listOf(
        GradientStop(0f, fill.colorArgb),
        GradientStop(1f, fill.secondaryColorArgb)
    )
}

@Composable
private fun SliderRow(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ColorSwatchButton(label: String, colorArgb: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SpiderSurfaceElevated)
            .border(1.dp, SpiderBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(colorArgb))
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun updateElementFill(el: VectorElement, fill: PaintStyle): VectorElement = when (el) {
    is RectShape -> el.copy(fill = fill)
    is EllipseShape -> el.copy(fill = fill)
    is PolygonShape -> el.copy(fill = fill)
    is StarShape -> el.copy(fill = fill)
    is PathShape -> el.copy(fill = fill)
    is TextShape -> el.copy(fill = fill)
    is ImageShape -> el.copy(fill = fill)
    is GroupShape -> el.copy(fill = fill)
}

private fun updateElementStroke(el: VectorElement, stroke: StrokeStyle): VectorElement = when (el) {
    is RectShape -> el.copy(stroke = stroke)
    is EllipseShape -> el.copy(stroke = stroke)
    is PolygonShape -> el.copy(stroke = stroke)
    is StarShape -> el.copy(stroke = stroke)
    is PathShape -> el.copy(stroke = stroke)
    is TextShape -> el.copy(stroke = stroke)
    is ImageShape -> el.copy(stroke = stroke)
    is GroupShape -> el.copy(stroke = stroke)
}

private fun updateElementEffects(el: VectorElement, effects: EffectStyle): VectorElement = when (el) {
    is RectShape -> el.copy(effects = effects)
    is EllipseShape -> el.copy(effects = effects)
    is PolygonShape -> el.copy(effects = effects)
    is StarShape -> el.copy(effects = effects)
    is PathShape -> el.copy(effects = effects)
    is TextShape -> el.copy(effects = effects)
    is ImageShape -> el.copy(effects = effects)
    is GroupShape -> el.copy(effects = effects)
}
