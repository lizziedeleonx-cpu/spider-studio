package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.example.data.PaletteEntity
import com.example.model.ColorInputMode
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderSurfaceElevated
import com.example.ui.theme.SpiderViolet
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerModal(
    title: String,
    initialColorArgb: Long,
    colorHistory: List<Long>,
    palettes: List<PaletteEntity>,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit,
    onSavePaletteColor: ((String, List<Long>) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpiderSurface
    ) {
        ColorPickerContent(
            title = title,
            initialColorArgb = initialColorArgb,
            colorHistory = colorHistory,
            palettes = palettes,
            onColorSelected = {
                onColorSelected(it)
                onDismiss()
            },
            onCancel = onDismiss,
            onSavePaletteColor = onSavePaletteColor
        )
    }
}

@Composable
fun ColorPickerContent(
    title: String,
    initialColorArgb: Long,
    colorHistory: List<Long>,
    palettes: List<PaletteEntity>,
    onColorSelected: (Long) -> Unit,
    onCancel: () -> Unit,
    onSavePaletteColor: ((String, List<Long>) -> Unit)? = null
) {
    val initInt = initialColorArgb.toInt()
    val initHsl = FloatArray(3).also { ColorUtils.colorToHSL(initInt, it) }

    var mode by remember { mutableStateOf(ColorInputMode.HSL) }
    var hue by remember { mutableFloatStateOf(initHsl[0]) }
    var sat by remember { mutableFloatStateOf(initHsl[1]) }
    var light by remember { mutableFloatStateOf(initHsl[2]) }
    var alpha by remember { mutableFloatStateOf(((initInt ushr 24) and 0xFF) / 255f) }

    fun currentColorInt(): Int {
        val rgb = ColorUtils.HSLToColor(floatArrayOf(hue, sat, light))
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (rgb and 0x00FFFFFF)
    }

    fun setFromArgbInt(argb: Int) {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hue = hsl[0]
        sat = hsl[1]
        light = hsl[2]
        alpha = ((argb ushr 24) and 0xFF) / 255f
    }

    val currentInt = currentColorInt()
    val currentLong = currentInt.toLong() and 0xFFFFFFFFL
    val hexFormatted = String.format("#%06X", currentInt and 0x00FFFFFF)
    var hexText by remember(currentInt) { mutableStateOf(hexFormatted) }

    val r = (currentInt ushr 16) and 0xFF
    val g = (currentInt ushr 8) and 0xFF
    val b = currentInt and 0xFF

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(hexFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(currentInt))
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }

        // Interactive Saturation/Lightness 2D Spectrum Pad
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, SpiderBorder, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        sat = (offset.x / size.width).coerceIn(0f, 1f)
                        light = (1f - offset.y / size.height).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        light = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val pureHue = Color(ColorUtils.HSLToColor(floatArrayOf(hue, 1f, 0.5f)))
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF808080), pureHue))
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xCCFFFFFF), Color.Transparent, Color(0xEE000000))
                    )
                )
                val cx = sat * size.width
                val cy = (1f - light) * size.height
                drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(cx, cy))
                drawCircle(Color.Black, radius = 7.dp.toPx(), center = Offset(cx, cy))
            }
        }

        // Hue Strip
        Column {
            Text("Matiz (${hue.roundToInt()}°)", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = hue,
                onValueChange = { hue = it },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = SpiderViolet)
            )
        }

        // Mode Tabs: HEX / RGB / HSL
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorInputMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m },
                    label = { Text(m.label) },
                    modifier = Modifier.testTag("color_mode_${m.name.lowercase()}")
                )
            }
        }

        when (mode) {
            ColorInputMode.HEX -> {
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { raw ->
                        hexText = raw.uppercase()
                        val cleaned = raw.trim().removePrefix("#")
                        if (cleaned.length == 6) {
                            cleaned.toLongOrNull(16)?.let { rgb ->
                                val full = ((alpha * 255).toInt() shl 24) or rgb.toInt()
                                setFromArgbInt(full)
                            }
                        }
                    },
                    label = { Text("Código HEX (#RRGGBB)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("color_hex_input")
                )
            }
            ColorInputMode.RGB -> {
                RgbSlider("Rojo (R)", r) { nr ->
                    setFromArgbInt((currentInt and 0xFF00FFFF.toInt()) or ((nr and 0xFF) shl 16))
                }
                RgbSlider("Verde (G)", g) { ng ->
                    setFromArgbInt((currentInt and 0xFFFF00FF.toInt()) or ((ng and 0xFF) shl 8))
                }
                RgbSlider("Azul (B)", b) { nb ->
                    setFromArgbInt((currentInt and 0xFFFFFF00.toInt()) or (nb and 0xFF))
                }
            }
            ColorInputMode.HSL -> {
                Text("Saturación (${(sat * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                Text("Luminosidad (${(light * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
                Slider(value = light, onValueChange = { light = it }, valueRange = 0f..1f)
            }
        }

        // Alpha
        Text("Opacidad (${(alpha * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
        Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0f..1f)

        // Color History
        if (colorHistory.isNotEmpty()) {
            Text("Historial de colores", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colorHistory.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(1.dp, SpiderBorder, CircleShape)
                            .clickable { setFromArgbInt(c.toInt()) }
                    )
                }
            }
        }

        // Custom Palettes
        if (palettes.isNotEmpty()) {
            Text("Paletas personalizadas", style = MaterialTheme.typography.titleSmall)
            palettes.take(3).forEach { pal ->
                val colors = pal.colorsCsv.split(",").mapNotNull { it.trim().toLongOrNull(16) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SpiderSurfaceElevated)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(pal.name, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(c))
                                    .border(1.dp, SpiderBorder, RoundedCornerShape(8.dp))
                                    .clickable { setFromArgbInt(c.toInt()) }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onSavePaletteColor != null) {
                OutlinedButton(
                    onClick = { onSavePaletteColor("Mi Paleta", listOf(currentLong)) },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("+ Paleta")
                }
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Cancelar")
            }
            Button(
                onClick = { onColorSelected(currentLong) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("apply_color_button")
            ) {
                Text("Aplicar")
            }
        }
    }
}

@Composable
private fun RgbSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f
        )
    }
}
