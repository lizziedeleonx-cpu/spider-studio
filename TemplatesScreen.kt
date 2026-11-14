package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.ComposeVectorRenderer.drawStudioCanvas
import com.example.model.EditorCanvasConfig
import com.example.model.EditorTool
import com.example.model.TemplateCatalog
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderVioletSoft
import kotlin.math.min

@Composable
fun TemplatesScreen(
    isWideScreen: Boolean,
    onSelectTemplate: (String) -> Unit
) {
    val context = LocalContext.current
    val columns = if (isWideScreen) 3 else 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(SpiderBlack)
    ) {
        item(span = { GridItemSpan(columns) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Galería de plantillas", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Diseños vectoriales reales con capas y texto totalmente editables.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpiderTextSecondary
                )
            }
        }

        items(TemplateCatalog.templates, key = { it.id }) { template ->
            val previewDoc = remember(template.id) { template.buildDocument() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SpiderSurface)
                    .border(1.dp, SpiderBorder, RoundedCornerShape(18.dp))
                    .clickable { onSelectTemplate(template.id) }
                    .testTag("template_card_${template.id}")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .background(SpiderBlack)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val zx = size.width / previewDoc.widthPx.toFloat()
                        val zy = size.height / previewDoc.heightPx.toFloat()
                        val zoom = min(zx, zy)
                        val px = (size.width - previewDoc.widthPx * zoom) / 2f
                        val py = (size.height - previewDoc.heightPx * zoom) / 2f
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
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(template.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = SpiderVioletSoft)
                        Text("${previewDoc.widthPx}×${previewDoc.heightPx}", style = MaterialTheme.typography.labelSmall, color = SpiderTextSecondary)
                    }
                    Text(template.title, style = MaterialTheme.typography.titleMedium)
                    Text(template.description, style = MaterialTheme.typography.bodySmall, color = SpiderTextSecondary)
                }
            }
        }
    }
}
