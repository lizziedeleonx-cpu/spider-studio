package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChromeStyleEntity
import com.example.data.PaletteEntity
import com.example.data.SpiderJsonCodec
import com.example.engine.ChromeEffectRenderer
import com.example.engine.ComposeVectorRenderer.drawStudioCanvas
import com.example.model.ChromeEffectConfig
import com.example.model.ChromeStyleType
import com.example.model.DocumentBackgroundMode
import com.example.model.EditorCanvasConfig
import com.example.model.EditorTool
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.TextAlignMode
import com.example.model.TextShape
import com.example.model.VectorElement
import com.example.model.VectorLayer
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderCharcoal
import com.example.ui.theme.SpiderError
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderSurfaceElevated
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletSoft
import kotlin.math.min
import kotlin.math.roundToInt

private data class MetallicAlloyChip(val label: String, val argb: Long)

private val QuickMetallicAlloys = listOf(
    MetallicAlloyChip("Plata", 0xFFE2E8F0),
    MetallicAlloyChip("Titanio", 0xFF94A3B8),
    MetallicAlloyChip("Violeta", 0xFFC4B5FD),
    MetallicAlloyChip("Oro", 0xFFFDE047),
    MetallicAlloyChip("Oro Rosa", 0xFFFBCFE8),
    MetallicAlloyChip("Holo Cian", 0xFF67E8F9)
)

@Composable
fun ChromeEffectsPanel(
    selectedElements: List<VectorElement>,
    customChromeStyles: List<ChromeStyleEntity>,
    colorHistory: List<Long>,
    palettes: List<PaletteEntity>,
    customFontPaths: Map<String, String>,
    onApplyPreset: (ChromeStyleType) -> Unit,
    onApplyCustomStyle: (ChromeEffectConfig) -> Unit,
    onUpdateChrome: (Boolean, (ChromeEffectConfig) -> ChromeEffectConfig) -> Unit,
    onDisableChrome: () -> Unit,
    onSaveChromeStyle: (String, ChromeEffectConfig) -> Unit,
    onDeleteChromeStyle: (String) -> Unit,
    onColorUsed: (Long) -> Unit,
    onSavePaletteColor: (String, List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primary = selectedElements.firstOrNull()
    val hasSelection = primary != null

    // When no element is selected yet, let users inspect presets in real time on the demo badge
    var previewFallbackChrome by remember {
        mutableStateOf(ChromeStyleType.CLASSIC_CHROME.defaultConfig())
    }
    val activeChrome = primary?.effects?.chrome ?: previewFallbackChrome

    var showSaveDialog by remember { mutableStateOf(false) }
    var styleNameInput by remember { mutableStateOf("") }
    var showMetallicColorPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("chrome_effects_panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SpiderViolet.copy(alpha = 0.22f))
                        .border(1.dp, SpiderVioletSoft.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = SpiderVioletSoft,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        "CHROME EFFECTS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (primary != null) "${primary.name} · No destructivo" else "Previsualización estudio",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpiderTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = activeChrome.enabled,
                onCheckedChange = { enabled ->
                    if (hasSelection) {
                        onUpdateChrome(true) { current ->
                            if (enabled && !current.enabled) {
                                current.styleType.defaultConfig().copy(
                                    reflectionIntensity = current.reflectionIntensity,
                                    brightness = current.brightness,
                                    contrast = current.contrast,
                                    depth = current.depth,
                                    bevelRelief = current.bevelRelief,
                                    lightAngleDeg = current.lightAngleDeg,
                                    metallicColorArgb = current.metallicColorArgb,
                                    reflectiveGradientBias = current.reflectiveGradientBias,
                                    shadowIntensity = current.shadowIntensity,
                                    overallIntensity = current.overallIntensity
                                )
                            } else {
                                current.copy(enabled = enabled)
                            }
                        }
                    } else {
                        previewFallbackChrome = previewFallbackChrome.copy(enabled = enabled)
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpiderViolet),
                modifier = Modifier.testTag("chrome_enable_switch")
            )
        }

        // Live Real-Time Shader Preview Stage (Zero static images)
        ChromeLivePreviewStage(
            context = context,
            selectedElement = primary,
            previewConfig = activeChrome,
            customFontPaths = customFontPaths
        )

        if (!hasSelection) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpiderSurfaceElevated)
                    .border(1.dp, SpiderViolet.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    "Selecciona un texto, logotipo, forma vectorial o trazo en el lienzo para aplicar y editar efectos cromados en tiempo real sin destruir el objeto original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpiderTextSecondary
                )
            }
        }

        // Action bar: Save Style + Remove Effect
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    styleNameInput = "${activeChrome.styleType.label} Pro"
                    showSaveDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpiderViolet, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("chrome_save_style_button")
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar estilo", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasSelection && activeChrome.enabled) {
                OutlinedButton(
                    onClick = onDisableChrome,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("chrome_disable_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Quitar")
                }
            }
        }

        // 6 Chrome Styles Selector
        HorizontalDivider(color = SpiderBorder)
        Text("Acabados metálicos (6 estilos)", style = MaterialTheme.typography.titleSmall)

        val styles = ChromeStyleType.entries
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            styles.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { style ->
                        val isSelected = activeChrome.enabled && activeChrome.styleType == style
                        ChromePresetCard(
                            styleType = style,
                            isSelected = isSelected,
                            onClick = {
                                if (hasSelection) {
                                    onApplyPreset(style)
                                } else {
                                    previewFallbackChrome = style.defaultConfig()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chrome_preset_${style.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Saved Custom Styles Section
        if (customChromeStyles.isNotEmpty()) {
            HorizontalDivider(color = SpiderBorder)
            Text("Estilos personalizados (${customChromeStyles.size})", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                customChromeStyles.forEach { saved ->
                    val decoded = remember(saved.configJson) {
                        SpiderJsonCodec.decodeChromeConfigJson(saved.configJson)
                    }
                    val ramp = remember(decoded) {
                        ChromeEffectRenderer.computeChromeRamp(decoded.copy(enabled = true), alpha = 1f)
                    }
                    val previewColors = remember(ramp) {
                        ramp.colors.map { Color(it) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpiderSurfaceElevated)
                            .border(1.dp, SpiderBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                if (hasSelection) {
                                    onApplyCustomStyle(decoded)
                                } else {
                                    previewFallbackChrome = decoded.copy(enabled = true)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("chrome_custom_style_${saved.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors = previewColors))
                                .border(1.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                saved.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                decoded.styleType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = SpiderTextSecondary,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { onDeleteChromeStyle(saved.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar estilo ${saved.name}",
                                tint = SpiderError
                            )
                        }
                    }
                }
            }
        }

        // All 10 Chrome Controls
        HorizontalDivider(color = SpiderBorder)
        Text("Controles ópticos y metálicos", style = MaterialTheme.typography.titleSmall)

        // 1. Intensidad del reflejo
        ChromeSliderRow(
            label = "Intensidad del reflejo",
            valueLabel = "${(activeChrome.reflectionIntensity * 100).roundToInt()}%",
            value = activeChrome.reflectionIntensity,
            range = 0f..1f,
            testTag = "chrome_slider_reflection",
            onValueChange = { v ->
                if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, reflectionIntensity = v) }
                else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, reflectionIntensity = v)
            },
            onValueChangeFinished = {
                if (hasSelection) onUpdateChrome(true) { it }
            }
        )

        // 2. Brillo
        ChromeSliderRow(
            label = "Brillo",
            valueLabel = "${(activeChrome.brightness * 100).roundToInt()}%",
            value = activeChrome.brightness,
            range = -0.5f..0.5f,
            testTag = "chrome_slider_brightness",
            onValueChange = { v ->
                if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, brightness = v) }
                else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, brightness = v)
            },
            onValueChangeFinished = {
                if (hasSelection) onUpdateChrome(true) { it }
            }
        )

        // 3. Contraste
        ChromeSliderRow(
            label = "Contraste",
            valueLabel = "${(activeChrome.contrast * 100).roundToInt()}%",
            value = activeChrome.contrast,
            range = 0.6f..2.2f,
            testTag = "chrome_slider_contrast",
            onValueChange = { v ->
                if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, contrast = v) }
                else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, contrast = v)
            },
            onValueChangeFinished = {
                if (hasSelection) onUpdateChrome(true) { it }
            }
        )

        // 4. Profundidad (volumen 3D)
        ChromeSliderRow(
            label = "Profundidad",
            valueLabel = "${(activeChrome.depth * 100).roundToInt()}%",
            value = activeChrome.depth,
            range = 0f..1f,
            testTag = "chrome_slider_depth",
            onValueChange = { v ->
                if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, depth = v) }
                else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, depth = v)
            },
            onValueChangeFinished = {
                if (hasSelection) onUpdateChrome(true) { it }
            }
        )

        // 5. Bisel y relieve
        ChromeSliderRow(
            label = "Bisel y relieve",
            valueLabel = "${(activeChrome.bevelRelief * 100).roundToInt()}%",
            value = activeChrome.bevelRelief,
            range = 0f..1f,
            testTag = "chrome_slider_bevel",
            onValueChange = { v ->
                if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, bevelRelief = v) }
                else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, bevelRelief = v)
            },
            onValueChangeFinished = {
                if (hasSelection) onUpdateChrome(true) { it }
            }
        )

        // 6. Dirección de iluminación
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.LightMode, contentDescription = null, tint = SpiderVioletSoft, modifier = Modifier.size(16.dp))
                Text("Dirección de iluminación", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${activeChrome.lightAngleDeg.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = SpiderVioletSoft)
            }
            Slider(
                value = activeChrome.lightAngleDeg.coerceIn(0f, 360f),
                onValueChange = { deg ->
                    if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, lightAngleDeg = deg) }
                    else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, lightAngleDeg = deg)
                },
                onValueChangeFinished = {
                    if (hasSelection) onUpdateChrome(true) { it }
                },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(thumbColor = SpiderVioletSoft, activeTrackColor = SpiderViolet),
                modifier = Modifier.testTag("chrome_slider_light_angle")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(315f to "Sup-Izq", 45f to "Sup-Der", 90f to "Cenital", 225f to "Inf-Izq", 0f to "Lateral").forEach { (deg, label) ->
                    val closeMatch = kotlin.math.abs(activeChrome.lightAngleDeg - deg) < 8f
                    FilterChip(
                        selected = closeMatch,
                        onClick = {
                            if (hasSelection) onUpdateChrome(true) { it.copy(enabled = true, lightAngleDeg = deg) }
                            else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, lightAngleDeg = deg)
                        },
                        label = { Text("$label ${deg.roundToInt()}°", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpiderViolet.copy(alpha = 0.28f),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // 7. Color metálico
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpiderSurfaceElevated)
                    .border(1.dp, SpiderBorder, RoundedCornerShape(12.dp))
                    .clickable { showMetallicColorPicker = true }
                    .padding(horizontal = 12.dp)
                    .testTag("chrome_metallic_color_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = SpiderVioletSoft, modifier = Modifier.size(18.dp))
                    Text("Color metálico", style = MaterialTheme.typography.bodyMedium)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(activeChrome.metallicColorArgb))
                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickMetallicAlloys.forEach { alloy ->
                    val isCurrent = (activeChrome.metallicColorArgb and 0x00FFFFFFL) == (alloy.argb and 0x00FFFFFFL)
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isCurrent) SpiderViolet.copy(alpha = 0.25f) else SpiderCharcoal)
                            .border(
                                width = 1.dp,
                                color = if (isCurrent) SpiderViolet else SpiderBorder,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                onColorUsed(alloy.argb)
                                if (hasSelection) {
                                    onUpdateChrome(true) { it.copy(enabled = true, metallicColorArgb = alloy.argb) }
                                } else {
                                    previewFallbackChrome = previewFallbackChrome.copy(enabled = true, metallicColorArgb = alloy.argb)
                                }
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(alloy.argb))
                                .border(0.8.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        )
                        Text(alloy.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 8. Degradados reflectantes
        ChromeSliderRow(
            label = "Degradados reflectantes",
            valueLabel = "${(activeChrome.reflectiveGradientBias * 100).roundToInt()}%",
            value = activeChrome.reflectiveGradientBias,
            range = 0f..1f,
            testTag = "chrome_slider_gradient_bias",
            onValueChange = { v ->
                if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, reflectiveGradientBias = v) }
                else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, reflectiveGradientBias = v)
            },
            onValueChangeFinished = {
                if (hasSelection) onUpdateChrome(true) { it }
            }
        )

        // 9. Sombras
        ChromeSliderRow(
            label = "Sombras",
            valueLabel = "${(activeChrome.shadowIntensity * 100).roundToInt()}%",
            value = activeChrome.shadowIntensity,
            range = 0f..1f,
            testTag = "chrome_slider_shadow",
            onValueChange = { v ->
                if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, shadowIntensity = v) }
                else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, shadowIntensity = v)
            },
            onValueChangeFinished = {
                if (hasSelection) onUpdateChrome(true) { it }
            }
        )

        // 10. Ajuste de intensidad
        ChromeSliderRow(
            label = "Ajuste de intensidad",
            valueLabel = "${(activeChrome.overallIntensity * 100).roundToInt()}%",
            value = activeChrome.overallIntensity,
            range = 0.05f..1f,
            testTag = "chrome_slider_intensity",
            onValueChange = { v ->
                if (hasSelection) onUpdateChrome(false) { it.copy(enabled = true, overallIntensity = v) }
                else previewFallbackChrome = previewFallbackChrome.copy(enabled = true, overallIntensity = v)
            },
            onValueChangeFinished = {
                if (hasSelection) onUpdateChrome(true) { it }
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = SpiderSurface,
            title = { Text("Guardar estilo Chrome") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Guarda esta combinación de brillo, bisel, reflejo e iluminación para reutilizarla en cualquier proyecto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpiderTextSecondary
                    )
                    OutlinedTextField(
                        value = styleNameInput,
                        onValueChange = { styleNameInput = it },
                        label = { Text("Nombre del estilo") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chrome_save_style_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = styleNameInput.trim().ifEmpty { activeChrome.styleType.label }
                        onSaveChromeStyle(trimmed, activeChrome.copy(enabled = true))
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("chrome_confirm_save_style_button")
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showMetallicColorPicker) {
        ColorPickerModal(
            title = "Color metálico",
            initialColorArgb = activeChrome.metallicColorArgb,
            colorHistory = colorHistory,
            palettes = palettes,
            onDismiss = { showMetallicColorPicker = false },
            onColorSelected = { chosen ->
                onColorUsed(chosen)
                if (hasSelection) {
                    onUpdateChrome(true) { it.copy(enabled = true, metallicColorArgb = chosen) }
                } else {
                    previewFallbackChrome = previewFallbackChrome.copy(enabled = true, metallicColorArgb = chosen)
                }
            },
            onSavePaletteColor = onSavePaletteColor
        )
    }
}

@Composable
private fun ChromeLivePreviewStage(
    context: android.content.Context,
    selectedElement: VectorElement?,
    previewConfig: ChromeEffectConfig,
    customFontPaths: Map<String, String>
) {
    val demoBadge = remember(selectedElement, previewConfig) {
        if (selectedElement != null) {
            listOf(selectedElement)
        } else {
            val baseEffects = com.example.model.EffectStyle(chrome = previewConfig)
            listOf(
                StarShape(
                    id = "chrome_preview_star",
                    name = "Estrella",
                    x = 32f,
                    y = 26f,
                    width = 112f,
                    height = 112f,
                    points = 5,
                    innerRadiusRatio = 0.45f,
                    fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFFE2E8F0),
                    effects = baseEffects
                ),
                TextShape(
                    id = "chrome_preview_text",
                    name = "CHROME",
                    x = 158f,
                    y = 42f,
                    width = 330f,
                    height = 88f,
                    text = "CHROME",
                    fontSize = 60f,
                    isBold = true,
                    alignment = TextAlignMode.LEFT,
                    fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFFF8FAFC),
                    effects = baseEffects
                )
            )
        }
    }

    val previewDoc = remember(demoBadge) {
        SpiderDocument(
            id = "chrome_preview_doc",
            name = "Vista Previa",
            widthPx = 540,
            heightPx = 176,
            backgroundMode = DocumentBackgroundMode.SOLID,
            backgroundColorArgb = 0xFF0A0A10,
            layers = listOf(
                VectorLayer(
                    id = "chrome_preview_layer",
                    name = "Preview",
                    elements = demoBadge
                )
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SpiderBlack)
            .border(1.dp, SpiderViolet.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clipToBounds()
            .testTag("chrome_live_preview")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bounds = if (selectedElement != null) {
                val minX = selectedElement.x
                val minY = selectedElement.y
                val w = selectedElement.width.coerceAtLeast(32f)
                val h = selectedElement.height.coerceAtLeast(32f)
                listOf(minX, minY, w, h)
            } else {
                listOf(12f, 10f, 500f, 150f)
            }

            val targetW = bounds[2]
            val targetH = bounds[3]
            val padFactor = 0.78f
            val scaleX = (size.width * padFactor) / targetW
            val scaleY = (size.height * padFactor) / targetH
            val fitZoom = min(scaleX, scaleY).coerceIn(0.15f, 3.0f)

            val centerX = bounds[0] + targetW / 2f
            val centerY = bounds[1] + targetH / 2f
            val panX = size.width / 2f - centerX * fitZoom
            val panY = size.height / 2f - centerY * fitZoom

            drawStudioCanvas(
                context = context,
                doc = previewDoc,
                config = EditorCanvasConfig(
                    showGrid = false,
                    showRulers = false,
                    showGuides = false,
                    snapEnabled = false
                ),
                zoom = fitZoom,
                pan = Offset(panX, panY),
                selectedIds = emptySet(),
                activeTool = EditorTool.SELECT,
                activeNodeIndex = null,
                penInProgressNodes = emptyList(),
                freehandPreviewPoints = emptyList(),
                snapLines = emptyList(),
                customFontPaths = customFontPaths
            )
        }

        // Badge indicating live shader preview
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SpiderCharcoal.copy(alpha = 0.88f))
                .border(0.8.dp, SpiderBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (previewConfig.enabled) SpiderVioletSoft else SpiderTextSecondary)
            )
            Text(
                if (previewConfig.enabled) previewConfig.styleType.label else "Original",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ChromePresetCard(
    styleType: ChromeStyleType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ramp = remember(styleType) {
        ChromeEffectRenderer.computeChromeRamp(styleType.defaultConfig(), alpha = 1f)
    }
    val swatchColors = remember(ramp) {
        ramp.colors.map { Color(it) }
    }

    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SpiderViolet.copy(alpha = 0.22f) else SpiderSurfaceElevated)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) SpiderViolet else SpiderBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors = swatchColors))
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                styleType.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                styleType.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SpiderTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChromeSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    testTag: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = SpiderVioletSoft)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = SpiderVioletSoft,
                activeTrackColor = SpiderViolet
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
