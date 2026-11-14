package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.model.BezierNode
import com.example.model.BlendModeStyle
import com.example.model.BrushPreset
import com.example.model.ChromeEffectConfig
import com.example.model.DocumentBackgroundMode
import com.example.model.EditorCanvasConfig
import com.example.model.EditorTool
import com.example.model.EllipseShape
import com.example.model.GradientStop
import com.example.model.GroupShape
import com.example.model.GuideOrientation
import com.example.model.ImageShape
import com.example.model.NodeHandleMode
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.SnapLine
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.StrokeCapStyle
import com.example.model.StrokeJoinStyle
import com.example.model.TextAlignMode
import com.example.model.TextShape
import com.example.model.VectorElement
import com.example.ui.theme.SpiderGuideAccent
import com.example.ui.theme.SpiderSnapAccent
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletSoft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object ComposeVectorRenderer {

    private const val MAX_CACHED_IMAGES = 24
    private const val MAX_DISPLAY_IMAGE_DIM = 1600

    private val imageCache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(MAX_CACHED_IMAGES + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > MAX_CACHED_IMAGES
            }
        }
    )
    private val pendingImageKeys = ConcurrentHashMap.newKeySet<String>()
    private val decodeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var imageCacheRevision by mutableIntStateOf(0)

    private val snapLabelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private val snapPillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#EC4899")
    }
    private val snapPillRect = android.graphics.RectF()

    private fun loadImageCached(path: String): ImageBitmap? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val key = "${file.absolutePath}:${file.lastModified()}"
        synchronized(imageCache) {
            imageCache[key]?.let { return it }
        }
        if (pendingImageKeys.add(key)) {
            decodeScope.launch {
                val decoded = runCatching { decodeDownsampledImageBitmap(file) }.getOrNull()
                if (decoded != null) {
                    synchronized(imageCache) {
                        imageCache[key] = decoded
                    }
                    runCatching {
                        withContext(Dispatchers.Main.immediate) {
                            imageCacheRevision++
                        }
                    }.onFailure {
                        // Fallback in non-Android-Main environments
                        imageCacheRevision++
                    }
                }
                pendingImageKeys.remove(key)
            }
        }
        return null
    }

    private fun decodeDownsampledImageBitmap(file: File): ImageBitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        var inSampleSize = 1
        while (boundsOpts.outWidth / inSampleSize > MAX_DISPLAY_IMAGE_DIM ||
            boundsOpts.outHeight / inSampleSize > MAX_DISPLAY_IMAGE_DIM
        ) {
            inSampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val bmp: Bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        return bmp.asImageBitmap()
    }

    fun DrawScope.drawStudioCanvas(
        context: Context,
        doc: SpiderDocument,
        config: EditorCanvasConfig,
        zoom: Float,
        pan: Offset,
        selectedIds: Set<String>,
        activeTool: EditorTool,
        activeNodeIndex: Int?,
        activeContourIndex: Int = 0,
        penInProgressNodes: List<BezierNode>,
        penPreviewPoint: Offset? = null,
        freehandPreviewPoints: List<Offset>,
        activeBrushSize: Float = 14f,
        activeBrushPreset: BrushPreset = BrushPreset.ROUND_CALLIGRAPHIC,
        activeBrushOpacity: Float = 1f,
        snapLines: List<SnapLine>,
        customFontPaths: Map<String, String>
    ) {
        // Observe async image decode completion so Canvas invalidates automatically
        @Suppress("UNUSED_VARIABLE")
        val rev = imageCacheRevision

        val safeZoom = zoom.coerceAtLeast(0.05f)
        val docW = doc.widthPx.toFloat()
        val docH = doc.heightPx.toFloat()
        val visLeft = ((-pan.x) / safeZoom).coerceIn(0f, docW)
        val visTop = ((-pan.y) / safeZoom).coerceIn(0f, docH)
        val visRight = ((size.width - pan.x) / safeZoom).coerceIn(0f, docW)
        val visBottom = ((size.height - pan.y) / safeZoom).coerceIn(0f, docH)

        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.translate(pan.x, pan.y)
            canvas.scale(safeZoom, safeZoom)

            // Document drop shadow & background
            drawRect(
                color = Color(0x66000000),
                topLeft = Offset(10f / safeZoom, 12f / safeZoom),
                size = Size(docW, docH)
            )
            if (doc.backgroundMode == DocumentBackgroundMode.TRANSPARENT) {
                drawCheckerboard(
                    w = docW,
                    h = docH,
                    tile = (24f / safeZoom).coerceIn(8f, 48f),
                    visLeft = visLeft,
                    visTop = visTop,
                    visRight = visRight,
                    visBottom = visBottom
                )
            } else {
                drawRect(
                    color = Color(doc.backgroundColorArgb),
                    topLeft = Offset.Zero,
                    size = Size(docW, docH)
                )
            }

            // Clip document contents cleanly to artboard
            canvas.save()
            canvas.clipRect(Rect(0f, 0f, docW, docH))

            // Render vector layers
            doc.layers.filter { it.isVisible }.forEach { layer ->
                layer.elements.filter { it.isVisible }.forEach { element ->
                    drawVectorElement(context, element, layer.opacity, customFontPaths)
                }
            }

            // Grid overlay inside artboard (adaptive step + viewport culling)
            if (config.showGrid && config.gridSizePx >= 8 && visRight > visLeft && visBottom > visTop) {
                var step = config.gridSizePx.toFloat()
                while (step * safeZoom < 14f && step < max(docW, docH)) {
                    step *= 2f
                }
                val gridColor = Color(0x22FFFFFF)
                val lineW = (1f / safeZoom).coerceAtLeast(0.5f)
                val startX = (floor((visLeft / step).toDouble()).toFloat() * step).coerceAtLeast(step)
                val endX = min(docW, visRight + step)
                var gx = startX
                while (gx < endX && gx < docW) {
                    drawLine(gridColor, Offset(gx, visTop), Offset(gx, visBottom), strokeWidth = lineW)
                    gx += step
                }
                val startY = (floor((visTop / step).toDouble()).toFloat() * step).coerceAtLeast(step)
                val endY = min(docH, visBottom + step)
                var gy = startY
                while (gy < endY && gy < docH) {
                    drawLine(gridColor, Offset(visLeft, gy), Offset(visRight, gy), strokeWidth = lineW)
                    gy += step
                }
            }

            // Custom guides
            if (config.showGuides) {
                val guideStroke = (1.6f / zoom).coerceAtLeast(0.9f)
                val guideColor = if (config.guidesLocked) SpiderGuideAccent.copy(alpha = 0.55f) else SpiderGuideAccent.copy(alpha = 0.85f)
                doc.guides.forEach { g ->
                    if (g.orientation == GuideOrientation.VERTICAL) {
                        drawLine(
                            color = guideColor,
                            start = Offset(g.positionPx, 0f),
                            end = Offset(g.positionPx, docH),
                            strokeWidth = guideStroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / zoom, 6f / zoom))
                        )
                    } else {
                        drawLine(
                            color = guideColor,
                            start = Offset(0f, g.positionPx),
                            end = Offset(docW, g.positionPx),
                            strokeWidth = guideStroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / zoom, 6f / zoom))
                        )
                    }
                }
            }

            // Pen in-progress preview
            if (penInProgressNodes.isNotEmpty()) {
                drawPenInProgress(penInProgressNodes, penPreviewPoint, zoom)
            }

            // Freehand Pencil / Brush preview
            if (freehandPreviewPoints.size > 1) {
                val p = Path().apply {
                    moveTo(freehandPreviewPoints[0].x, freehandPreviewPoints[0].y)
                    for (i in 1 until freehandPreviewPoints.size) {
                        lineTo(freehandPreviewPoints[i].x, freehandPreviewPoints[i].y)
                    }
                }
                val isBrush = activeTool == EditorTool.BRUSH
                drawPath(
                    path = p,
                    color = SpiderVioletSoft.copy(alpha = if (isBrush) activeBrushOpacity else 0.9f),
                    style = Stroke(
                        width = if (isBrush) activeBrushSize else 4f,
                        cap = if (isBrush && activeBrushPreset == BrushPreset.MARKER_FLAT) StrokeCap.Square else StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Smart Alignment & Spacing Snap Lines
            drawSnapLinesOverlay(snapLines, docW, docH, zoom)

            canvas.restore()

            // Artboard crisp border
            drawRect(
                color = Color(0xFF3E3E54),
                topLeft = Offset.Zero,
                size = Size(docW, docH),
                style = Stroke(width = (1.5f / zoom).coerceAtLeast(1f))
            )

            // Selection / Node overlays (drawn unclipped so handles are always visible)
            val allVisible = doc.layers.filter { it.isVisible }.flatMap { it.elements }
            val selected = allVisible.filter { it.id in selectedIds }
            if (selected.isNotEmpty()) {
                if (activeTool == EditorTool.NODE && selected.size == 1 && selected[0] is PathShape) {
                    drawNodeHandles(selected[0] as PathShape, activeNodeIndex, activeContourIndex, zoom)
                } else if (selected.size == 1) {
                    drawSelectionBox(selected[0], zoom)
                } else {
                    VectorMath.computeCombinedBounds(selected)?.let { box ->
                        drawRect(
                            color = SpiderViolet,
                            topLeft = box.topLeft,
                            size = box.size,
                            style = Stroke(width = 2f / zoom)
                        )
                    }
                }
            }

            canvas.restore()
        }
    }

    private fun DrawScope.drawSnapLinesOverlay(
        snapLines: List<SnapLine>,
        docW: Float,
        docH: Float,
        zoom: Float
    ) {
        if (snapLines.isEmpty()) return
        val sw = (1.6f / zoom).coerceAtLeast(1f)
        snapLabelPaint.textSize = (24f / zoom).coerceIn(10f, 36f)

        snapLines.forEach { snap ->
            val lineColor = if (snap.isSpacingGuide) Color(0xFFEC4899) else SpiderSnapAccent
            if (snap.isVertical) {
                val y0 = snap.spanStart ?: 0f
                val y1 = snap.spanEnd ?: docH
                drawLine(
                    color = lineColor,
                    start = Offset(snap.positionDoc, y0),
                    end = Offset(snap.positionDoc, y1),
                    strokeWidth = sw
                )
            } else {
                val x0 = snap.spanStart ?: 0f
                val x1 = snap.spanEnd ?: docW
                drawLine(
                    color = lineColor,
                    start = Offset(x0, snap.positionDoc),
                    end = Offset(x1, snap.positionDoc),
                    strokeWidth = sw
                )
                if (snap.isSpacingGuide && snap.distanceLabel != null) {
                    val midX = (x0 + x1) * 0.5f
                    val padX = 12f / zoom
                    val padY = 14f / zoom
                    val tw = snapLabelPaint.measureText(snap.distanceLabel)
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        snapPillRect.set(
                            midX - tw / 2f - padX,
                            snap.positionDoc - padY * 1.4f,
                            midX + tw / 2f + padX,
                            snap.positionDoc + padY * 0.4f
                        )
                        nc.drawRoundRect(snapPillRect, 8f / zoom, 8f / zoom, snapPillPaint)
                        nc.drawText(snap.distanceLabel, midX, snap.positionDoc - 4f / zoom, snapLabelPaint)
                    }
                }
            }
        }
    }

    private fun DrawScope.drawCheckerboard(
        w: Float,
        h: Float,
        tile: Float,
        visLeft: Float = 0f,
        visTop: Float = 0f,
        visRight: Float = w,
        visBottom: Float = h
    ) {
        drawRect(color = Color(0xFF1A1A22), topLeft = Offset.Zero, size = Size(w, h))
        if (visRight <= visLeft || visBottom <= visTop) return
        val light = Color(0xFF242430)
        val firstRow = floor((visTop / tile).toDouble()).toInt().coerceAtLeast(0)
        val lastRow = floor((visBottom / tile).toDouble()).toInt().coerceAtLeast(firstRow)
        val firstCol = floor((visLeft / tile).toDouble()).toInt().coerceAtLeast(0)
        val lastCol = floor((visRight / tile).toDouble()).toInt().coerceAtLeast(firstCol)
        for (row in firstRow..lastRow) {
            val y = row * tile
            if (y >= h) break
            val tileH = min(tile, h - y)
            val startCol = if ((firstCol + row) % 2 == 1) firstCol else firstCol + 1
            var col = startCol
            while (col <= lastCol) {
                val x = col * tile
                if (x >= w) break
                val tileW = min(tile, w - x)
                drawRect(color = light, topLeft = Offset(x, y), size = Size(tileW, tileH))
                col += 2
            }
        }
    }

    private fun DrawScope.drawVectorElement(
        context: Context,
        element: VectorElement,
        parentOpacity: Float,
        customFontPaths: Map<String, String>,
        inheritedChrome: ChromeEffectConfig? = null
    ) {
        val alpha = (element.opacity * parentOpacity).coerceIn(0f, 1f)
        if (alpha <= 0f) return
        val activeChrome = ChromeEffectRenderer.effectiveChrome(element, inheritedChrome)

        val needsBlend = element.effects.blendMode != BlendModeStyle.NORMAL
        drawIntoCanvas { canvas ->
            if (needsBlend) {
                val pad = max(element.effects.shadowBlur, element.effects.glowRadius) + 40f
                val bounds = Rect(
                    element.x - pad,
                    element.y - pad,
                    element.x + element.width + pad,
                    element.y + element.height + pad
                )
                val layerPaint = Paint().apply {
                    blendMode = mapBlendMode(element.effects.blendMode)
                }
                canvas.saveLayer(bounds, layerPaint)
            }

            val pivot = Offset(element.x + element.width / 2f, element.y + element.height / 2f)
            rotate(degrees = element.rotationDeg, pivot = pivot) {
                when (element) {
                    is GroupShape -> {
                        if (element.hasClippingMask && element.children.size >= 2) {
                            val maskEl = element.children.first()
                            val maskComposePath = buildComposeMaskPath(maskEl)
                            canvas.save()
                            canvas.clipPath(maskComposePath)
                            element.children.drop(1).filter { it.isVisible }.forEach { child ->
                                drawVectorElement(context, child, alpha, customFontPaths, activeChrome)
                            }
                            canvas.restore()
                            if (element.showMaskOutline && maskEl.stroke.enabled) {
                                drawPath(
                                    path = maskComposePath,
                                    color = Color(applyAlphaArgb(maskEl.stroke.colorArgb, maskEl.stroke.opacity * alpha)),
                                    style = buildComposeStroke(maskEl)
                                )
                            }
                        } else {
                            element.children.filter { it.isVisible }.forEach { child ->
                                drawVectorElement(context, child, alpha, customFontPaths, activeChrome)
                            }
                        }
                    }
                    is ImageShape -> {
                        val img = loadImageCached(element.imagePath)
                        if (element.effects.shadowEnabled) {
                            drawRect(
                                color = Color(applyAlphaArgb(element.effects.shadowColorArgb, element.effects.shadowOpacity * alpha)),
                                topLeft = Offset(element.x + element.effects.shadowOffsetX, element.y + element.effects.shadowOffsetY),
                                size = Size(element.width, element.height)
                            )
                        }
                        if (img != null) {
                            drawImage(
                                image = img,
                                dstOffset = IntOffset(element.x.toInt(), element.y.toInt()),
                                dstSize = IntSize(element.width.toInt().coerceAtLeast(1), element.height.toInt().coerceAtLeast(1)),
                                alpha = alpha
                            )
                        } else {
                            drawRect(
                                color = Color(0x338B5CF6),
                                topLeft = Offset(element.x, element.y),
                                size = Size(element.width, element.height)
                            )
                        }
                        if (activeChrome != null && activeChrome.overallIntensity > 0.02f) {
                            val rimPath = android.graphics.Path().apply {
                                addRect(element.x, element.y, element.x + element.width, element.y + element.height, android.graphics.Path.Direction.CW)
                            }
                            drawIntoCanvas { c ->
                                val tintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    style = android.graphics.Paint.Style.FILL
                                    shader = ChromeEffectRenderer.buildLinearChromeShader(
                                        activeChrome, element.x, element.y, element.width, element.height,
                                        alpha * activeChrome.overallIntensity * 0.45f
                                    )
                                }
                                c.nativeCanvas.drawPath(rimPath, tintPaint)
                            }
                        }
                    }
                    is TextShape -> drawComposeTextElement(context, element, alpha, customFontPaths, activeChrome)
                    is RectShape -> {
                        val cr = min(element.cornerRadius, min(element.width, element.height) / 2f).coerceAtLeast(0f)
                        drawNativeShadowAndGlow(element, alpha)
                        if (activeChrome != null) {
                            val nativePath = AndroidVectorRenderer.buildElementPath(element)
                            drawIntoCanvas { c ->
                                ChromeEffectRenderer.drawChromeForPath(c.nativeCanvas, nativePath, element, activeChrome, alpha)
                            }
                        } else {
                            if (element.fill.type != PaintType.NONE) {
                                if (element.effects.blurRadius > 0.5f) {
                                    drawNativeBlurredFill(element, alpha)
                                } else {
                                    val brush = buildComposeBrush(element.fill, element.x, element.y, element.width, element.height, alpha)
                                    drawRoundRect(
                                        brush = brush,
                                        topLeft = Offset(element.x, element.y),
                                        size = Size(element.width, element.height),
                                        cornerRadius = CornerRadius(cr, cr)
                                    )
                                }
                            }
                            if (element.stroke.enabled && element.stroke.width > 0f) {
                                drawRoundRect(
                                    color = Color(applyAlphaArgb(element.stroke.colorArgb, element.stroke.opacity * alpha)),
                                    topLeft = Offset(element.x, element.y),
                                    size = Size(element.width, element.height),
                                    cornerRadius = CornerRadius(cr, cr),
                                    style = buildComposeStroke(element)
                                )
                            }
                        }
                    }
                    is EllipseShape -> {
                        drawNativeShadowAndGlow(element, alpha)
                        if (activeChrome != null) {
                            val nativePath = AndroidVectorRenderer.buildElementPath(element)
                            drawIntoCanvas { c ->
                                ChromeEffectRenderer.drawChromeForPath(c.nativeCanvas, nativePath, element, activeChrome, alpha)
                            }
                        } else {
                            if (element.fill.type != PaintType.NONE) {
                                if (element.effects.blurRadius > 0.5f) {
                                    drawNativeBlurredFill(element, alpha)
                                } else {
                                    val brush = buildComposeBrush(element.fill, element.x, element.y, element.width, element.height, alpha)
                                    drawOval(
                                        brush = brush,
                                        topLeft = Offset(element.x, element.y),
                                        size = Size(element.width, element.height)
                                    )
                                }
                            }
                            if (element.stroke.enabled && element.stroke.width > 0f) {
                                drawOval(
                                    color = Color(applyAlphaArgb(element.stroke.colorArgb, element.stroke.opacity * alpha)),
                                    topLeft = Offset(element.x, element.y),
                                    size = Size(element.width, element.height),
                                    style = buildComposeStroke(element)
                                )
                            }
                        }
                    }
                    is PolygonShape -> {
                        drawNativeShadowAndGlow(element, alpha)
                        if (activeChrome != null) {
                            val nativePath = AndroidVectorRenderer.buildElementPath(element)
                            drawIntoCanvas { c ->
                                ChromeEffectRenderer.drawChromeForPath(c.nativeCanvas, nativePath, element, activeChrome, alpha)
                            }
                        } else {
                            val pts = VectorMath.buildPolygonPoints(element.x, element.y, element.width, element.height, element.sides)
                            drawPointsShape(pts, closed = true, element = element, alpha = alpha)
                        }
                    }
                    is StarShape -> {
                        drawNativeShadowAndGlow(element, alpha)
                        if (activeChrome != null) {
                            val nativePath = AndroidVectorRenderer.buildElementPath(element)
                            drawIntoCanvas { c ->
                                ChromeEffectRenderer.drawChromeForPath(c.nativeCanvas, nativePath, element, activeChrome, alpha)
                            }
                        } else {
                            val pts = VectorMath.buildStarPoints(
                                element.x, element.y, element.width, element.height, element.points, element.innerRadiusRatio
                            )
                            drawPointsShape(pts, closed = true, element = element, alpha = alpha)
                        }
                    }
                    is PathShape -> {
                        if (element.isBrushStroke && element.pressureWidths.size >= 2 &&
                            element.brushPreset != BrushPreset.MARKER_FLAT && element.brushPreset != BrushPreset.ARTISTIC_DASH
                        ) {
                            val ribbonPts = VectorMath.buildBrushRibbonOutline(element)
                            if (ribbonPts.size >= 3) {
                                val ribbonPath = android.graphics.Path().apply {
                                    moveTo(ribbonPts[0].x, ribbonPts[0].y)
                                    for (i in 1 until ribbonPts.size) lineTo(ribbonPts[i].x, ribbonPts[i].y)
                                    close()
                                }
                                drawNativeShadowAndGlowForNativePath(ribbonPath, element, alpha)
                                if (activeChrome != null) {
                                    drawIntoCanvas { c ->
                                        ChromeEffectRenderer.drawChromeForPath(c.nativeCanvas, ribbonPath, element, activeChrome, alpha)
                                    }
                                } else {
                                    drawIntoCanvas { c ->
                                        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                            style = android.graphics.Paint.Style.FILL
                                            color = applyAlphaArgb(element.stroke.colorArgb, element.stroke.opacity * alpha)
                                            val blur = when {
                                                element.effects.blurRadius > 0.5f -> element.effects.blurRadius
                                                element.brushPreset == BrushPreset.SOFT_AIRBRUSH -> max(6f, element.stroke.width * 0.45f)
                                                else -> 0f
                                            }
                                            if (blur > 0.5f) {
                                                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                                            }
                                        }
                                        c.nativeCanvas.drawPath(ribbonPath, p)
                                    }
                                }
                                return@rotate
                            }
                        }

                        drawNativeShadowAndGlow(element, alpha)
                        if (activeChrome != null) {
                            val nativePath = AndroidVectorRenderer.buildElementPath(element)
                            drawIntoCanvas { c ->
                                ChromeEffectRenderer.drawChromeForPath(c.nativeCanvas, nativePath, element, activeChrome, alpha)
                            }
                        } else {
                            val path = buildComposePath(element)
                            if (element.fill.type != PaintType.NONE && element.nodes.size >= 2) {
                                if (element.effects.blurRadius > 0.5f) {
                                    drawNativeBlurredFill(element, alpha)
                                } else {
                                    val brush = buildComposeBrush(element.fill, element.x, element.y, element.width, element.height, alpha)
                                    drawPath(path = path, brush = brush)
                                }
                            }
                            if (element.stroke.enabled && element.stroke.width > 0f) {
                                val effectiveStroke = if (element.isBrushStroke) {
                                    when (element.brushPreset) {
                                        BrushPreset.MARKER_FLAT -> element.stroke.copy(
                                            cap = StrokeCapStyle.SQUARE,
                                            opacity = element.stroke.opacity * 0.72f
                                        )
                                        BrushPreset.ARTISTIC_DASH -> element.stroke.copy(
                                            dashed = true,
                                            dashLength = max(8f, element.stroke.width * 1.6f),
                                            gapLength = max(6f, element.stroke.width * 0.8f)
                                        )
                                        else -> element.stroke
                                    }
                                } else element.stroke
                                if (element.isBrushStroke && element.brushPreset == BrushPreset.SOFT_AIRBRUSH) {
                                    drawIntoCanvas { c ->
                                        val sp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                            style = android.graphics.Paint.Style.STROKE
                                            strokeWidth = effectiveStroke.width
                                            strokeCap = android.graphics.Paint.Cap.ROUND
                                            strokeJoin = android.graphics.Paint.Join.ROUND
                                            color = applyAlphaArgb(effectiveStroke.colorArgb, effectiveStroke.opacity * alpha)
                                            maskFilter = BlurMaskFilter(max(6f, effectiveStroke.width * 0.45f), BlurMaskFilter.Blur.NORMAL)
                                        }
                                        c.nativeCanvas.drawPath(AndroidVectorRenderer.buildElementPath(element), sp)
                                    }
                                } else {
                                    drawPath(
                                        path = path,
                                        color = Color(applyAlphaArgb(effectiveStroke.colorArgb, effectiveStroke.opacity * alpha)),
                                        style = buildComposeStrokeFromStyle(effectiveStroke)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (needsBlend) {
                canvas.restore()
            }
        }
    }

    private fun DrawScope.drawNativeShadowAndGlow(element: VectorElement, alpha: Float) {
        if (!element.effects.shadowEnabled && !element.effects.glowEnabled) return
        val nativePath = AndroidVectorRenderer.buildElementPath(element)
        drawNativeShadowAndGlowForNativePath(nativePath, element, alpha)
    }

    private fun DrawScope.drawNativeShadowAndGlowForNativePath(
        nativePath: android.graphics.Path,
        element: VectorElement,
        alpha: Float
    ) {
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            if (element.effects.glowEnabled) {
                val gp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.FILL_AND_STROKE
                    strokeWidth = element.effects.glowRadius.coerceAtLeast(2f) * 0.5f
                    color = applyAlphaArgb(element.effects.glowColorArgb, alpha * 0.85f)
                    maskFilter = BlurMaskFilter(element.effects.glowRadius.coerceAtLeast(2f), BlurMaskFilter.Blur.OUTER)
                }
                nc.drawPath(nativePath, gp)
            }
            if (element.effects.shadowEnabled) {
                val sp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = if (element.fill.type != PaintType.NONE) android.graphics.Paint.Style.FILL
                    else android.graphics.Paint.Style.STROKE
                    strokeWidth = element.stroke.width
                    color = applyAlphaArgb(element.effects.shadowColorArgb, element.effects.shadowOpacity * alpha)
                    if (element.effects.shadowBlur > 0.5f) {
                        maskFilter = BlurMaskFilter(element.effects.shadowBlur, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                nc.save()
                nc.translate(element.effects.shadowOffsetX, element.effects.shadowOffsetY)
                nc.drawPath(nativePath, sp)
                nc.restore()
            }
        }
    }

    private fun DrawScope.drawNativeBlurredFill(element: VectorElement, alpha: Float) {
        val nativePath = AndroidVectorRenderer.buildElementPath(element)
        drawIntoCanvas { c ->
            val fp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                AndroidVectorRenderer.applyPaintShader(this, element.fill, element.x, element.y, element.width, element.height, alpha)
                maskFilter = BlurMaskFilter(element.effects.blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
            c.nativeCanvas.drawPath(nativePath, fp)
        }
    }

    private fun DrawScope.drawComposeTextElement(
        context: Context,
        element: TextShape,
        alpha: Float,
        customFontPaths: Map<String, String>,
        activeChrome: ChromeEffectConfig? = null
    ) {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val tf = AndroidVectorRenderer.resolveTypeface(
                context, element.fontFamilyKey, element.isBold, element.isItalic, customFontPaths
            )
            val displayText = VectorMath.applyTextTransform(element.text, element.textTransform)

            if (element.backgroundBadgeEnabled) {
                val pad = element.backgroundPaddingPx.coerceAtLeast(0f)
                val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.FILL
                    color = applyAlphaArgb(element.backgroundBadgeColorArgb, alpha)
                }
                val bgRect = android.graphics.RectF(
                    element.x - pad,
                    element.y - pad * 0.6f,
                    element.x + element.width + pad,
                    element.y + element.height + pad * 0.6f
                )
                nc.drawRoundRect(bgRect, min(24f, pad + 8f), min(24f, pad + 8f), bgPaint)
            }

            if (activeChrome != null) {
                val arcPath = if (abs(element.curveRadius) >= 40f) AndroidVectorRenderer.buildTextArcPath(element) else null
                ChromeEffectRenderer.drawChromeText(
                    canvas = nc,
                    element = element,
                    tf = tf,
                    displayText = displayText,
                    chrome = activeChrome,
                    alpha = alpha,
                    arcPath = arcPath
                )
                return@drawIntoCanvas
            }

            if (abs(element.curveRadius) >= 40f) {
                val arcPath = AndroidVectorRenderer.buildTextArcPath(element)
                val align = when (element.alignment) {
                    TextAlignMode.LEFT -> android.graphics.Paint.Align.LEFT
                    TextAlignMode.CENTER -> android.graphics.Paint.Align.CENTER
                    TextAlignMode.RIGHT -> android.graphics.Paint.Align.RIGHT
                }
                if (element.fill.type != PaintType.NONE) {
                    val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = tf
                        textSize = element.fontSize
                        letterSpacing = element.letterSpacing
                        textAlign = align
                        isUnderlineText = element.isUnderline
                        isStrikeThruText = element.isStrikethrough
                        AndroidVectorRenderer.applyPaintShader(this, element.fill, element.x, element.y, element.width, element.height, alpha)
                        if (element.effects.shadowEnabled) {
                            setShadowLayer(
                                element.effects.shadowBlur.coerceAtLeast(1f),
                                element.effects.shadowOffsetX,
                                element.effects.shadowOffsetY,
                                applyAlphaArgb(element.effects.shadowColorArgb, element.effects.shadowOpacity * alpha)
                            )
                        }
                    }
                    nc.drawTextOnPath(displayText.replace("\n", " "), arcPath, 0f, 0f, tp)
                }
                if (element.stroke.enabled && element.stroke.width > 0f) {
                    val sp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = element.stroke.width
                        color = applyAlphaArgb(element.stroke.colorArgb, element.stroke.opacity * alpha)
                        typeface = tf
                        textSize = element.fontSize
                        letterSpacing = element.letterSpacing
                        textAlign = align
                    }
                    nc.drawTextOnPath(displayText.replace("\n", " "), arcPath, 0f, 0f, sp)
                }
                return@drawIntoCanvas
            }

            val align = when (element.alignment) {
                TextAlignMode.LEFT -> android.text.Layout.Alignment.ALIGN_NORMAL
                TextAlignMode.CENTER -> android.text.Layout.Alignment.ALIGN_CENTER
                TextAlignMode.RIGHT -> android.text.Layout.Alignment.ALIGN_OPPOSITE
            }
            val tw = element.width.toInt().coerceAtLeast(24)
            if (element.fill.type != PaintType.NONE) {
                val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = element.fontSize
                    letterSpacing = element.letterSpacing
                    isUnderlineText = element.isUnderline
                    isStrikeThruText = element.isStrikethrough
                    AndroidVectorRenderer.applyPaintShader(this, element.fill, element.x, element.y, element.width, element.height, alpha)
                    if (element.effects.shadowEnabled) {
                        setShadowLayer(
                            element.effects.shadowBlur.coerceAtLeast(1f),
                            element.effects.shadowOffsetX,
                            element.effects.shadowOffsetY,
                            applyAlphaArgb(element.effects.shadowColorArgb, element.effects.shadowOpacity * alpha)
                        )
                    }
                    if (element.effects.blurRadius > 0.5f) {
                        maskFilter = BlurMaskFilter(element.effects.blurRadius, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                val layout = android.text.StaticLayout.Builder
                    .obtain(displayText, 0, displayText.length, tp, tw)
                    .setAlignment(align)
                    .setLineSpacing(0f, element.lineHeightMultiplier)
                    .build()
                nc.save()
                nc.translate(element.x, element.y)
                layout.draw(nc)
                nc.restore()
            }
            if (element.stroke.enabled && element.stroke.width > 0f) {
                val sp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = element.stroke.width
                    color = applyAlphaArgb(element.stroke.colorArgb, element.stroke.opacity * alpha)
                    typeface = tf
                    textSize = element.fontSize
                    letterSpacing = element.letterSpacing
                    isUnderlineText = element.isUnderline
                    isStrikeThruText = element.isStrikethrough
                }
                val layout = android.text.StaticLayout.Builder
                    .obtain(displayText, 0, displayText.length, sp, tw)
                    .setAlignment(align)
                    .setLineSpacing(0f, element.lineHeightMultiplier)
                    .build()
                nc.save()
                nc.translate(element.x, element.y)
                layout.draw(nc)
                nc.restore()
            }
        }
    }

    private fun DrawScope.drawPointsShape(
        pts: List<Offset>,
        closed: Boolean,
        element: VectorElement,
        alpha: Float
    ) {
        if (pts.isEmpty()) return
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            if (closed) close()
        }
        if (element.fill.type != PaintType.NONE) {
            if (element.effects.blurRadius > 0.5f) {
                drawNativeBlurredFill(element, alpha)
            } else {
                val brush = buildComposeBrush(element.fill, element.x, element.y, element.width, element.height, alpha)
                drawPath(path = path, brush = brush)
            }
        }
        if (element.stroke.enabled && element.stroke.width > 0f) {
            drawPath(
                path = path,
                color = Color(applyAlphaArgb(element.stroke.colorArgb, element.stroke.opacity * alpha)),
                style = buildComposeStroke(element)
            )
        }
    }

    private fun buildComposeMaskPath(maskEl: VectorElement): Path {
        return when (maskEl) {
            is PathShape -> buildComposePath(maskEl)
            is RectShape -> Path().apply {
                val cr = min(maskEl.cornerRadius, min(maskEl.width, maskEl.height) / 2f).coerceAtLeast(0f)
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = maskEl.x, top = maskEl.y,
                        right = maskEl.x + maskEl.width, bottom = maskEl.y + maskEl.height,
                        cornerRadius = CornerRadius(cr, cr)
                    )
                )
            }
            is EllipseShape -> Path().apply {
                addOval(Rect(maskEl.x, maskEl.y, maskEl.x + maskEl.width, maskEl.y + maskEl.height))
            }
            is PolygonShape -> {
                val pts = VectorMath.buildPolygonPoints(maskEl.x, maskEl.y, maskEl.width, maskEl.height, maskEl.sides)
                Path().apply {
                    if (pts.isNotEmpty()) {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        close()
                    }
                }
            }
            is StarShape -> {
                val pts = VectorMath.buildStarPoints(maskEl.x, maskEl.y, maskEl.width, maskEl.height, maskEl.points, maskEl.innerRadiusRatio)
                Path().apply {
                    if (pts.isNotEmpty()) {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        close()
                    }
                }
            }
            else -> Path().apply {
                addRect(Rect(maskEl.x, maskEl.y, maskEl.x + maskEl.width, maskEl.y + maskEl.height))
            }
        }
    }

    private fun buildComposePath(element: PathShape): Path {
        val path = Path().apply {
            fillType = if (element.fillEvenOdd) PathFillType.EvenOdd else PathFillType.NonZero
        }
        fun appendContour(nodes: List<BezierNode>, closed: Boolean) {
            if (nodes.isEmpty()) return
            path.moveTo(nodes[0].x, nodes[0].y)
            val segs = if (closed) nodes.size else nodes.size - 1
            for (i in 0 until segs) {
                val a = nodes[i]
                val b = nodes[(i + 1) % nodes.size]
                path.cubicTo(a.cp2X, a.cp2Y, b.cp1X, b.cp1Y, b.x, b.y)
            }
            if (closed) path.close()
        }
        appendContour(element.nodes, element.isClosed)
        element.extraContours.forEach { c ->
            appendContour(c.nodes, c.isClosed)
        }
        return path
    }

    private fun buildComposeBrush(
        style: PaintStyle,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        alpha: Float
    ): Brush {
        val sortedStops = if (style.stops.size >= 2) {
            style.stops.sortedBy { it.offset }
        } else {
            listOf(
                GradientStop(0f, style.colorArgb),
                GradientStop(1f, style.secondaryColorArgb)
            )
        }
        val colorStops = sortedStops.map { st ->
            st.offset.coerceIn(0f, 1f) to Color(applyAlphaArgb(st.colorArgb, style.opacity * alpha))
        }.toTypedArray()

        return when (style.type) {
            PaintType.NONE -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            PaintType.SOLID -> {
                val c1 = Color(applyAlphaArgb(style.colorArgb, style.opacity * alpha))
                Brush.linearGradient(listOf(c1, c1))
            }
            PaintType.LINEAR_GRADIENT -> {
                val cx = x + w / 2f
                val cy = y + h / 2f
                val rad = Math.toRadians(style.gradientAngleDeg.toDouble())
                val dx = (cos(rad) * max(1f, w) / 2f).toFloat()
                val dy = (sin(rad) * max(1f, h) / 2f).toFloat()
                Brush.linearGradient(
                    colorStops = colorStops,
                    start = Offset(cx - dx, cy - dy),
                    end = Offset(cx + dx, cy + dy)
                )
            }
            PaintType.RADIAL_GRADIENT -> {
                val cx = x + w * style.gradientCenterX.coerceIn(0f, 1f)
                val cy = y + h * style.gradientCenterY.coerceIn(0f, 1f)
                Brush.radialGradient(
                    colorStops = colorStops,
                    center = Offset(cx, cy),
                    radius = max(12f, max(w, h) * style.radialRadiusFactor.coerceIn(0.15f, 1.5f))
                )
            }
            PaintType.SWEEP_GRADIENT -> {
                val cx = x + w * style.gradientCenterX.coerceIn(0f, 1f)
                val cy = y + h * style.gradientCenterY.coerceIn(0f, 1f)
                Brush.sweepGradient(
                    colorStops = colorStops,
                    center = Offset(cx, cy)
                )
            }
        }
    }

    private fun buildComposeStroke(element: VectorElement): Stroke =
        buildComposeStrokeFromStyle(element.stroke)

    private fun buildComposeStrokeFromStyle(s: com.example.model.StrokeStyle): Stroke {
        val cap = when (s.cap) {
            StrokeCapStyle.BUTT -> StrokeCap.Butt
            StrokeCapStyle.ROUND -> StrokeCap.Round
            StrokeCapStyle.SQUARE -> StrokeCap.Square
        }
        val join = when (s.join) {
            StrokeJoinStyle.MITER -> StrokeJoin.Miter
            StrokeJoinStyle.ROUND -> StrokeJoin.Round
            StrokeJoinStyle.BEVEL -> StrokeJoin.Bevel
        }
        val effect = if (s.dashed) {
            PathEffect.dashPathEffect(floatArrayOf(s.dashLength.coerceAtLeast(2f), s.gapLength.coerceAtLeast(2f)))
        } else null
        return Stroke(width = s.width, cap = cap, join = join, pathEffect = effect)
    }

    private fun mapBlendMode(mode: BlendModeStyle): BlendMode = when (mode) {
        BlendModeStyle.NORMAL -> BlendMode.SrcOver
        BlendModeStyle.MULTIPLY -> BlendMode.Multiply
        BlendModeStyle.SCREEN -> BlendMode.Screen
        BlendModeStyle.OVERLAY -> BlendMode.Overlay
        BlendModeStyle.DARKEN -> BlendMode.Darken
        BlendModeStyle.LIGHTEN -> BlendMode.Lighten
    }

    private fun DrawScope.drawSelectionBox(element: VectorElement, zoom: Float) {
        val pivot = Offset(element.x + element.width / 2f, element.y + element.height / 2f)
        rotate(degrees = element.rotationDeg, pivot = pivot) {
            val strokeW = 2f / zoom
            drawRect(
                color = SpiderViolet,
                topLeft = Offset(element.x, element.y),
                size = Size(element.width, element.height),
                style = Stroke(width = strokeW)
            )
            // Rotation handle stem + knob
            val topCenter = Offset(element.x + element.width / 2f, element.y)
            val rotKnob = Offset(topCenter.x, element.y - 38f / zoom)
            drawLine(SpiderViolet, topCenter, rotKnob, strokeWidth = strokeW)
            drawCircle(Color(0xFF09090D), radius = 9f / zoom, center = rotKnob)
            drawCircle(SpiderViolet, radius = 7f / zoom, center = rotKnob, style = Stroke(width = strokeW))

            // 8 resize handles
            val hs = 12f / zoom
            val handlePoints = listOf(
                Offset(element.x, element.y),
                Offset(element.x + element.width / 2f, element.y),
                Offset(element.x + element.width, element.y),
                Offset(element.x + element.width, element.y + element.height / 2f),
                Offset(element.x + element.width, element.y + element.height),
                Offset(element.x + element.width / 2f, element.y + element.height),
                Offset(element.x, element.y + element.height),
                Offset(element.x, element.y + element.height / 2f)
            )
            handlePoints.forEach { pt ->
                drawRect(
                    color = Color.White,
                    topLeft = Offset(pt.x - hs / 2f, pt.y - hs / 2f),
                    size = Size(hs, hs)
                )
                drawRect(
                    color = SpiderViolet,
                    topLeft = Offset(pt.x - hs / 2f, pt.y - hs / 2f),
                    size = Size(hs, hs),
                    style = Stroke(width = 1.8f / zoom)
                )
            }
        }
    }

    private fun DrawScope.drawNodeHandles(
        path: PathShape,
        activeIndex: Int?,
        activeContourIndex: Int,
        zoom: Float
    ) {
        val pivot = Offset(path.x + path.width / 2f, path.y + path.height / 2f)
        rotate(degrees = path.rotationDeg, pivot = pivot) {
            drawPath(
                path = buildComposePath(path),
                color = SpiderVioletSoft.copy(alpha = 0.65f),
                style = Stroke(width = (1.4f / zoom).coerceAtLeast(0.75f))
            )

            val lineW = 1.6f / zoom
            val sq = 12f / zoom
            val cr = 6.5f / zoom
            val contours = listOf(path.nodes) + path.extraContours.map { it.nodes }

            contours.forEachIndexed { cIdx, contourNodes ->
                val isCurrentContour = cIdx == activeContourIndex
                contourNodes.forEachIndexed { idx, n ->
                    val isSelected = isCurrentContour && idx == activeIndex
                    val showHandles = isSelected || hypot(n.cp1X - n.x, n.cp1Y - n.y) > 1f ||
                        hypot(n.cp2X - n.x, n.cp2Y - n.y) > 1f

                    if (showHandles) {
                        if (n.cp1X != n.x || n.cp1Y != n.y) {
                            drawLine(
                                color = if (isSelected) SpiderGuideAccent else SpiderVioletSoft.copy(alpha = 0.7f),
                                start = Offset(n.x, n.y),
                                end = Offset(n.cp1X, n.cp1Y),
                                strokeWidth = lineW
                            )
                            drawCircle(color = Color(0xFF09090D), radius = cr, center = Offset(n.cp1X, n.cp1Y))
                            drawCircle(
                                color = if (isSelected) SpiderGuideAccent else SpiderVioletSoft,
                                radius = cr,
                                center = Offset(n.cp1X, n.cp1Y),
                                style = Stroke(width = lineW)
                            )
                        }
                        if (n.cp2X != n.x || n.cp2Y != n.y) {
                            drawLine(
                                color = if (isSelected) SpiderGuideAccent else SpiderVioletSoft.copy(alpha = 0.7f),
                                start = Offset(n.x, n.y),
                                end = Offset(n.cp2X, n.cp2Y),
                                strokeWidth = lineW
                            )
                            drawCircle(color = Color(0xFF09090D), radius = cr, center = Offset(n.cp2X, n.cp2Y))
                            drawCircle(
                                color = if (isSelected) SpiderGuideAccent else SpiderVioletSoft,
                                radius = cr,
                                center = Offset(n.cp2X, n.cp2Y),
                                style = Stroke(width = lineW)
                            )
                        }
                    }

                    val mode = if (n.handleMode == NodeHandleMode.CORNER && n.smooth) NodeHandleMode.SMOOTH else n.handleMode
                    when (mode) {
                        NodeHandleMode.CORNER -> {
                            drawRect(
                                color = if (isSelected) SpiderViolet else Color.White,
                                topLeft = Offset(n.x - sq / 2f, n.y - sq / 2f),
                                size = Size(sq, sq)
                            )
                            drawRect(
                                color = if (isSelected) Color.White else SpiderViolet,
                                topLeft = Offset(n.x - sq / 2f, n.y - sq / 2f),
                                size = Size(sq, sq),
                                style = Stroke(width = lineW)
                            )
                        }
                        NodeHandleMode.SMOOTH -> {
                            drawCircle(
                                color = if (isSelected) SpiderViolet else Color.White,
                                radius = sq * 0.56f,
                                center = Offset(n.x, n.y)
                            )
                            drawCircle(
                                color = if (isSelected) Color.White else SpiderViolet,
                                radius = sq * 0.56f,
                                center = Offset(n.x, n.y),
                                style = Stroke(width = lineW)
                            )
                        }
                        NodeHandleMode.SYMMETRIC -> {
                            drawCircle(
                                color = if (isSelected) SpiderGuideAccent else SpiderVioletSoft,
                                radius = sq * 0.62f,
                                center = Offset(n.x, n.y)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = sq * 0.62f,
                                center = Offset(n.x, n.y),
                                style = Stroke(width = lineW)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun DrawScope.drawPenInProgress(
        nodes: List<BezierNode>,
        previewPoint: Offset?,
        zoom: Float
    ) {
        val path = Path().apply {
            moveTo(nodes[0].x, nodes[0].y)
            for (i in 0 until nodes.lastIndex) {
                val a = nodes[i]
                val b = nodes[i + 1]
                cubicTo(a.cp2X, a.cp2Y, b.cp1X, b.cp1Y, b.x, b.y)
            }
        }
        drawPath(
            path = path,
            color = SpiderVioletSoft,
            style = Stroke(width = 2.5f / zoom)
        )
        if (previewPoint != null) {
            val last = nodes.last()
            val rubber = Path().apply {
                moveTo(last.x, last.y)
                cubicTo(last.cp2X, last.cp2Y, previewPoint.x, previewPoint.y, previewPoint.x, previewPoint.y)
            }
            drawPath(
                path = rubber,
                color = SpiderGuideAccent.copy(alpha = 0.7f),
                style = Stroke(
                    width = 2f / zoom,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / zoom, 8f / zoom))
                )
            )
        }
        val sq = 12f / zoom
        nodes.forEachIndexed { i, n ->
            if (n.cp1X != n.x || n.cp1Y != n.y) {
                drawLine(SpiderGuideAccent.copy(alpha = 0.6f), Offset(n.x, n.y), Offset(n.cp1X, n.cp1Y), 1.4f / zoom)
                drawCircle(SpiderGuideAccent, 4.5f / zoom, Offset(n.cp1X, n.cp1Y))
            }
            if (n.cp2X != n.x || n.cp2Y != n.y) {
                drawLine(SpiderGuideAccent, Offset(n.x, n.y), Offset(n.cp2X, n.cp2Y), 1.5f / zoom)
                drawCircle(SpiderGuideAccent, 5f / zoom, Offset(n.cp2X, n.cp2Y))
            }
            drawRect(
                color = if (i == 0) Color(0xFF22C55E) else Color.White,
                topLeft = Offset(n.x - sq / 2f, n.y - sq / 2f),
                size = Size(sq, sq)
            )
            drawRect(
                color = SpiderViolet,
                topLeft = Offset(n.x - sq / 2f, n.y - sq / 2f),
                size = Size(sq, sq),
                style = Stroke(width = 1.5f / zoom)
            )
        }
    }

    private fun applyAlphaArgb(argb: Long, opacity: Float): Int {
        val base = argb.toInt()
        val origA = (base ushr 24) and 0xFF
        val newA = ((origA * opacity).toInt()).coerceIn(0, 255)
        return (newA shl 24) or (base and 0x00FFFFFF)
    }
}
