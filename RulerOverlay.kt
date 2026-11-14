package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.model.RulerUnit
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderCharcoal
import com.example.ui.theme.SpiderTextMuted
import com.example.ui.theme.SpiderViolet
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun HorizontalRuler(
    zoom: Float,
    panX: Float,
    unit: RulerUnit,
    onTapDocumentCoord: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestZoom by rememberUpdatedState(zoom)
    val latestPanX by rememberUpdatedState(panX)
    val paint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#8A8AA3")
            textSize = 22f
            isAntiAlias = true
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(SpiderCharcoal)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val docX = (offset.x - latestPanX) / latestZoom.coerceAtLeast(0.1f)
                    onTapDocumentCoord(docX)
                }
            }
    ) {
        drawLine(SpiderBorder, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        val stepDoc = chooseStep(zoom)
        val startDoc = floor((-panX) / zoom / stepDoc) * stepDoc
        val endDoc = ceil((size.width - panX) / zoom / stepDoc) * stepDoc
        var xDoc = startDoc
        while (xDoc <= endDoc) {
            val sx = panX + xDoc * zoom
            if (sx in 0f..size.width) {
                drawLine(SpiderTextMuted, Offset(sx, size.height * 0.45f), Offset(sx, size.height), 1f)
                val valUnit = (xDoc * unit.factorFromPx).roundToInt()
                drawContext.canvas.nativeCanvas.drawText("$valUnit", sx + 4f, size.height * 0.55f, paint)
            }
            xDoc += stepDoc
        }
    }
}

@Composable
fun VerticalRuler(
    zoom: Float,
    panY: Float,
    unit: RulerUnit,
    onTapDocumentCoord: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestZoom by rememberUpdatedState(zoom)
    val latestPanY by rememberUpdatedState(panY)
    val paint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#8A8AA3")
            textSize = 20f
            isAntiAlias = true
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .background(SpiderCharcoal)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val docY = (offset.y - latestPanY) / latestZoom.coerceAtLeast(0.1f)
                    onTapDocumentCoord(docY)
                }
            }
    ) {
        drawLine(SpiderBorder, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
        val stepDoc = chooseStep(zoom)
        val startDoc = floor((-panY) / zoom / stepDoc) * stepDoc
        val endDoc = ceil((size.height - panY) / zoom / stepDoc) * stepDoc
        var yDoc = startDoc
        while (yDoc <= endDoc) {
            val sy = panY + yDoc * zoom
            if (sy in 0f..size.height) {
                drawLine(SpiderTextMuted, Offset(size.width * 0.45f, sy), Offset(size.width, sy), 1f)
                val valUnit = (yDoc * unit.factorFromPx).roundToInt()
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.rotate(-90f, size.width * 0.65f, sy - 4f)
                drawContext.canvas.nativeCanvas.drawText("$valUnit", size.width * 0.65f, sy - 4f, paint)
                drawContext.canvas.nativeCanvas.restore()
            }
            yDoc += stepDoc
        }
    }
}

@Composable
fun RulerCornerBadge(
    unit: RulerUnit,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(SpiderCharcoal)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = unit.suffix,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = SpiderViolet
        )
    }
}

private fun chooseStep(zoom: Float): Float {
    val raw = 90f / zoom.coerceAtLeast(0.1f)
    return when {
        raw <= 25f -> 25f
        raw <= 50f -> 50f
        raw <= 100f -> 100f
        raw <= 250f -> 250f
        raw <= 500f -> 500f
        else -> 1000f
    }
}
