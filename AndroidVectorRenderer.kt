package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.BlendModeStyle
import com.example.model.BrushPreset
import com.example.model.ChromeEffectConfig
import com.example.model.DocumentBackgroundMode
import com.example.model.EllipseShape
import com.example.model.GroupShape
import com.example.model.ImageShape
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.StrokeCapStyle
import com.example.model.StrokeJoinStyle
import com.example.model.StrokeStyle
import com.example.model.TextAlignMode
import com.example.model.TextShape
import com.example.model.VectorElement
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object AndroidVectorRenderer {

    fun renderDocumentToBitmap(
        context: Context,
        doc: SpiderDocument,
        targetWidth: Int,
        targetHeight: Int,
        transparentBackground: Boolean,
        customFontPaths: Map<String, String> = emptyMap(),
        cropBounds: RectF? = null
    ): Bitmap {
        val w = targetWidth.coerceIn(16, 4096)
        val h = targetHeight.coerceIn(16, 4096)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderToCanvas(
            context = context,
            canvas = canvas,
            doc = doc,
            targetWidth = w.toFloat(),
            targetHeight = h.toFloat(),
            transparentBackground = transparentBackground,
            customFontPaths = customFontPaths,
            cropBounds = cropBounds
        )
        return bitmap
    }

    fun renderToCanvas(
        context: Context,
        canvas: Canvas,
        doc: SpiderDocument,
        targetWidth: Float,
        targetHeight: Float,
        transparentBackground: Boolean,
        customFontPaths: Map<String, String> = emptyMap(),
        cropBounds: RectF? = null
    ) {
        if (!transparentBackground && doc.backgroundMode == DocumentBackgroundMode.SOLID) {
            canvas.drawColor(doc.backgroundColorArgb.toInt())
        }

        val srcW = cropBounds?.width()?.coerceAtLeast(1f) ?: doc.widthPx.toFloat().coerceAtLeast(1f)
        val srcH = cropBounds?.height()?.coerceAtLeast(1f) ?: doc.heightPx.toFloat().coerceAtLeast(1f)
        val scaleX = targetWidth / srcW
        val scaleY = targetHeight / srcH

        canvas.save()
        canvas.scale(scaleX, scaleY)
        if (cropBounds != null) {
            canvas.translate(-cropBounds.left, -cropBounds.top)
        }

        val renderScale = max(scaleX, scaleY).coerceAtLeast(0.1f)
        doc.layers.filter { it.isVisible }.forEach { layer ->
            layer.elements.filter { it.isVisible }.forEach { element ->
                drawElement(context, canvas, element, layer.opacity, customFontPaths, renderScale)
            }
        }
        canvas.restore()
    }

    private fun drawElement(
        context: Context,
        canvas: Canvas,
        element: VectorElement,
        parentOpacity: Float,
        customFontPaths: Map<String, String>,
        renderScale: Float = 1f,
        inheritedChrome: ChromeEffectConfig? = null
    ) {
        val effectiveAlpha = (element.opacity * parentOpacity).coerceIn(0f, 1f)
        if (effectiveAlpha <= 0f) return
        val activeChrome = ChromeEffectRenderer.effectiveChrome(element, inheritedChrome)

        val needsBlendLayer = element.effects.blendMode != BlendModeStyle.NORMAL
        if (needsBlendLayer) {
            val layerPaint = Paint().apply {
                xfermode = PorterDuffXfermode(mapBlendToPorterDuff(element.effects.blendMode))
            }
            val pad = max(element.effects.shadowBlur, element.effects.glowRadius) + 40f
            val bounds = RectF(
                element.x - pad,
                element.y - pad,
                element.x + element.width + pad,
                element.y + element.height + pad
            )
            canvas.saveLayer(bounds, layerPaint)
        } else {
            canvas.save()
        }

        if (element.rotationDeg != 0f) {
            canvas.rotate(
                element.rotationDeg,
                element.x + element.width / 2f,
                element.y + element.height / 2f
            )
        }

        when (element) {
            is GroupShape -> {
                if (element.hasClippingMask && element.children.size >= 2) {
                    val maskEl = element.children.first()
                    val maskPath = buildElementPath(maskEl)
                    canvas.save()
                    if (maskEl.rotationDeg != 0f) {
                        val m = android.graphics.Matrix().apply {
                            setRotate(maskEl.rotationDeg, maskEl.x + maskEl.width / 2f, maskEl.y + maskEl.height / 2f)
                        }
                        maskPath.transform(m)
                    }
                    canvas.clipPath(maskPath)
                    element.children.drop(1).filter { it.isVisible }.forEach { child ->
                        drawElement(context, canvas, child, effectiveAlpha, customFontPaths, renderScale, activeChrome)
                    }
                    canvas.restore()
                    if (element.showMaskOutline && maskEl.stroke.enabled) {
                        val sp = createStrokePaint(maskEl.stroke, 0f, effectiveAlpha)
                        canvas.drawPath(maskPath, sp)
                    }
                } else {
                    element.children.filter { it.isVisible }.forEach { child ->
                        drawElement(context, canvas, child, effectiveAlpha, customFontPaths, renderScale, activeChrome)
                    }
                }
            }
            is ImageShape -> {
                val file = File(element.imagePath)
                if (file.exists()) {
                    val reqW = (element.width * renderScale).toInt().coerceIn(64, 4096)
                    val reqH = (element.height * renderScale).toInt().coerceIn(64, 4096)
                    val bmp = decodeSampledBitmap(file.absolutePath, reqW, reqH)
                    if (bmp != null) {
                        if (element.effects.shadowEnabled) {
                            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = applyAlpha(element.effects.shadowColorArgb, element.effects.shadowOpacity * effectiveAlpha)
                                if (element.effects.shadowBlur > 0.5f) {
                                    maskFilter = BlurMaskFilter(element.effects.shadowBlur, BlurMaskFilter.Blur.NORMAL)
                                }
                            }
                            val sDst = RectF(
                                element.x + element.effects.shadowOffsetX,
                                element.y + element.effects.shadowOffsetY,
                                element.x + element.width + element.effects.shadowOffsetX,
                                element.y + element.height + element.effects.shadowOffsetY
                            )
                            canvas.drawRect(sDst, sp)
                        }
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                            alpha = (effectiveAlpha * 255).toInt()
                        }
                        val dst = RectF(element.x, element.y, element.x + element.width, element.y + element.height)
                        canvas.drawBitmap(bmp, null, dst, paint)
                        bmp.recycle()
                        if (activeChrome != null && activeChrome.overallIntensity > 0.02f) {
                            val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.FILL
                                shader = ChromeEffectRenderer.buildLinearChromeShader(
                                    activeChrome, element.x, element.y, element.width, element.height,
                                    effectiveAlpha * activeChrome.overallIntensity * 0.45f
                                )
                            }
                            canvas.drawRect(dst, sheen)
                        }
                    }
                }
            }
            is TextShape -> drawTextElement(context, canvas, element, effectiveAlpha, customFontPaths, activeChrome)
            else -> {
                if (element is PathShape && element.isBrushStroke && element.pressureWidths.size >= 2 &&
                    element.brushPreset != BrushPreset.MARKER_FLAT && element.brushPreset != BrushPreset.ARTISTIC_DASH
                ) {
                    val ribbonPts = VectorMath.buildBrushRibbonOutline(element)
                    if (ribbonPts.size >= 3) {
                        val ribbonPath = Path().apply {
                            moveTo(ribbonPts[0].x, ribbonPts[0].y)
                            for (i in 1 until ribbonPts.size) lineTo(ribbonPts[i].x, ribbonPts[i].y)
                            close()
                        }
                        drawPathShadowAndGlow(canvas, ribbonPath, element, effectiveAlpha)
                        if (activeChrome != null) {
                            ChromeEffectRenderer.drawChromeForPath(canvas, ribbonPath, element, activeChrome, effectiveAlpha)
                        } else {
                            val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.FILL
                                color = applyAlpha(element.stroke.colorArgb, element.stroke.opacity * effectiveAlpha)
                                val blur = when {
                                    element.effects.blurRadius > 0.5f -> element.effects.blurRadius
                                    element.brushPreset == BrushPreset.SOFT_AIRBRUSH -> max(6f, element.stroke.width * 0.45f)
                                    else -> 0f
                                }
                                if (blur > 0.5f) {
                                    maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                                }
                            }
                            canvas.drawPath(ribbonPath, brushPaint)
                        }
                        canvas.restore()
                        return
                    }
                }

                val path = buildElementPath(element)
                drawPathShadowAndGlow(canvas, path, element, effectiveAlpha)
                if (activeChrome != null) {
                    ChromeEffectRenderer.drawChromeForPath(canvas, path, element, activeChrome, effectiveAlpha)
                } else {
                    if (element.fill.type != PaintType.NONE) {
                        val fillPaint = createFillPaint(element, effectiveAlpha)
                        canvas.drawPath(path, fillPaint)
                    }
                    if (element.stroke.enabled && element.stroke.width > 0f) {
                        val effectiveStroke = if (element is PathShape && element.isBrushStroke) {
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
                        val extraBlur = if (element is PathShape && element.isBrushStroke && element.brushPreset == BrushPreset.SOFT_AIRBRUSH) {
                            max(element.effects.blurRadius, element.stroke.width * 0.45f)
                        } else element.effects.blurRadius
                        val strokePaint = createStrokePaint(effectiveStroke, extraBlur, effectiveAlpha)
                        canvas.drawPath(path, strokePaint)
                    }
                }
            }
        }

        canvas.restore()
    }

    private fun drawPathShadowAndGlow(
        canvas: Canvas,
        path: Path,
        element: VectorElement,
        effectiveAlpha: Float
    ) {
        if (element.effects.glowEnabled) {
            val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = element.effects.glowRadius.coerceAtLeast(2f) * 0.5f
                color = applyAlpha(element.effects.glowColorArgb, effectiveAlpha * 0.85f)
                maskFilter = BlurMaskFilter(element.effects.glowRadius.coerceAtLeast(2f), BlurMaskFilter.Blur.OUTER)
            }
            canvas.drawPath(path, gp)
        }
        if (element.effects.shadowEnabled) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = if (element.fill.type != PaintType.NONE) Paint.Style.FILL else Paint.Style.STROKE
                strokeWidth = element.stroke.width
                color = applyAlpha(element.effects.shadowColorArgb, element.effects.shadowOpacity * effectiveAlpha)
                if (element.effects.shadowBlur > 0.5f) {
                    maskFilter = BlurMaskFilter(element.effects.shadowBlur, BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.save()
            canvas.translate(element.effects.shadowOffsetX, element.effects.shadowOffsetY)
            canvas.drawPath(path, sp)
            canvas.restore()
        }
    }

    private fun drawTextElement(
        context: Context,
        canvas: Canvas,
        element: TextShape,
        effectiveAlpha: Float,
        customFontPaths: Map<String, String>,
        activeChrome: ChromeEffectConfig? = null
    ) {
        val tf = resolveTypeface(context, element.fontFamilyKey, element.isBold, element.isItalic, customFontPaths)
        val displayText = VectorMath.applyTextTransform(element.text, element.textTransform)

        if (element.backgroundBadgeEnabled) {
            val pad = element.backgroundPaddingPx.coerceAtLeast(0f)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = applyAlpha(element.backgroundBadgeColorArgb, effectiveAlpha)
            }
            val bgRect = RectF(
                element.x - pad,
                element.y - pad * 0.6f,
                element.x + element.width + pad,
                element.y + element.height + pad * 0.6f
            )
            canvas.drawRoundRect(bgRect, min(24f, pad + 8f), min(24f, pad + 8f), bgPaint)
        }

        if (activeChrome != null) {
            val arcPath = if (abs(element.curveRadius) >= 40f) buildTextArcPath(element) else null
            ChromeEffectRenderer.drawChromeText(
                canvas = canvas,
                element = element,
                tf = tf,
                displayText = displayText,
                chrome = activeChrome,
                alpha = effectiveAlpha,
                arcPath = arcPath
            )
            return
        }

        if (abs(element.curveRadius) >= 40f) {
            val arcPath = buildTextArcPath(element)
            val align = when (element.alignment) {
                TextAlignMode.LEFT -> Paint.Align.LEFT
                TextAlignMode.CENTER -> Paint.Align.CENTER
                TextAlignMode.RIGHT -> Paint.Align.RIGHT
            }
            if (element.fill.type != PaintType.NONE) {
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = element.fontSize
                    letterSpacing = element.letterSpacing
                    textAlign = align
                    isUnderlineText = element.isUnderline
                    isStrikeThruText = element.isStrikethrough
                    applyPaintShader(this, element.fill, element.x, element.y, element.width, element.height, effectiveAlpha)
                    if (element.effects.shadowEnabled) {
                        setShadowLayer(
                            element.effects.shadowBlur.coerceAtLeast(1f),
                            element.effects.shadowOffsetX,
                            element.effects.shadowOffsetY,
                            applyAlpha(element.effects.shadowColorArgb, element.effects.shadowOpacity * effectiveAlpha)
                        )
                    }
                }
                canvas.drawTextOnPath(displayText.replace("\n", " "), arcPath, 0f, 0f, tp)
            }
            if (element.stroke.enabled && element.stroke.width > 0f) {
                val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = element.stroke.width
                    color = applyAlpha(element.stroke.colorArgb, element.stroke.opacity * effectiveAlpha)
                    typeface = tf
                    textSize = element.fontSize
                    letterSpacing = element.letterSpacing
                    textAlign = align
                }
                canvas.drawTextOnPath(displayText.replace("\n", " "), arcPath, 0f, 0f, sp)
            }
            return
        }

        val align = when (element.alignment) {
            TextAlignMode.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignMode.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignMode.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val textWidth = element.width.toInt().coerceAtLeast(24)

        if (element.fill.type != PaintType.NONE) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = element.fontSize
                letterSpacing = element.letterSpacing
                isUnderlineText = element.isUnderline
                isStrikeThruText = element.isStrikethrough
                applyPaintShader(this, element.fill, element.x, element.y, element.width, element.height, effectiveAlpha)
                if (element.effects.shadowEnabled) {
                    setShadowLayer(
                        element.effects.shadowBlur.coerceAtLeast(1f),
                        element.effects.shadowOffsetX,
                        element.effects.shadowOffsetY,
                        applyAlpha(element.effects.shadowColorArgb, element.effects.shadowOpacity * effectiveAlpha)
                    )
                }
                if (element.effects.blurRadius > 0.5f) {
                    maskFilter = BlurMaskFilter(element.effects.blurRadius, BlurMaskFilter.Blur.NORMAL)
                }
            }
            val layout = StaticLayout.Builder
                .obtain(displayText, 0, displayText.length, tp, textWidth)
                .setAlignment(align)
                .setLineSpacing(0f, element.lineHeightMultiplier)
                .build()
            canvas.save()
            canvas.translate(element.x, element.y)
            layout.draw(canvas)
            canvas.restore()
        }

        if (element.stroke.enabled && element.stroke.width > 0f) {
            val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = element.stroke.width
                color = applyAlpha(element.stroke.colorArgb, element.stroke.opacity * effectiveAlpha)
                typeface = tf
                textSize = element.fontSize
                letterSpacing = element.letterSpacing
                isUnderlineText = element.isUnderline
                isStrikeThruText = element.isStrikethrough
            }
            val layout = StaticLayout.Builder
                .obtain(displayText, 0, displayText.length, sp, textWidth)
                .setAlignment(align)
                .setLineSpacing(0f, element.lineHeightMultiplier)
                .build()
            canvas.save()
            canvas.translate(element.x, element.y)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    fun buildTextArcPath(element: TextShape): Path {
        val r = abs(element.curveRadius).coerceAtLeast(40f)
        val cx = element.x + element.width / 2f
        val baselineY = element.y + element.fontSize
        return Path().apply {
            if (element.curveRadius > 0f) {
                val oval = RectF(cx - r, baselineY, cx + r, baselineY + 2f * r)
                addArc(oval, -155f, 130f)
            } else {
                val oval = RectF(cx - r, baselineY - 2f * r, cx + r, baselineY)
                addArc(oval, 155f, -130f)
            }
        }
    }

    fun resolveTypeface(
        context: Context,
        fontFamilyKey: String,
        isBold: Boolean,
        isItalic: Boolean,
        customFontPaths: Map<String, String> = emptyMap()
    ): Typeface {
        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val customPath = customFontPaths[fontFamilyKey]
        if (customPath != null && File(customPath).exists()) {
            runCatching {
                return Typeface.create(Typeface.createFromFile(customPath), style)
            }
        }
        val base = when (fontFamilyKey) {
            "space_grotesk" -> ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.SANS_SERIF
            "plus_jakarta_sans" -> ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.SANS_SERIF
            "jetbrains_mono" -> ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        return Typeface.create(base, style)
    }

    fun buildElementPath(element: VectorElement): Path {
        val path = Path()
        when (element) {
            is RectShape -> {
                val rect = RectF(element.x, element.y, element.x + element.width, element.y + element.height)
                val r = min(element.cornerRadius, min(element.width, element.height) / 2f).coerceAtLeast(0f)
                path.addRoundRect(rect, r, r, Path.Direction.CW)
            }
            is EllipseShape -> {
                val rect = RectF(element.x, element.y, element.x + element.width, element.y + element.height)
                path.addOval(rect, Path.Direction.CW)
            }
            is PolygonShape -> {
                val pts = VectorMath.buildPolygonPoints(element.x, element.y, element.width, element.height, element.sides)
                if (pts.isNotEmpty()) {
                    path.moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                    path.close()
                }
            }
            is StarShape -> {
                val pts = VectorMath.buildStarPoints(
                    element.x, element.y, element.width, element.height, element.points, element.innerRadiusRatio
                )
                if (pts.isNotEmpty()) {
                    path.moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                    path.close()
                }
            }
            is PathShape -> {
                path.fillType = if (element.fillEvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                fun appendContour(nodes: List<com.example.model.BezierNode>, closed: Boolean) {
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
                element.extraContours.forEach { contour ->
                    appendContour(contour.nodes, contour.isClosed)
                }
            }
            else -> Unit
        }
        return path
    }

    private fun createFillPaint(element: VectorElement, effectiveAlpha: Float): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            applyPaintShader(this, element.fill, element.x, element.y, element.width, element.height, effectiveAlpha)
            if (element.effects.blurRadius > 0.5f) {
                maskFilter = BlurMaskFilter(element.effects.blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        }
    }

    fun applyPaintShader(
        paint: Paint,
        style: PaintStyle,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        effectiveAlpha: Float
    ) {
        val alpha = (style.opacity * effectiveAlpha).coerceIn(0f, 1f)
        val sortedStops = if (style.stops.size >= 2) {
            style.stops.sortedBy { it.offset }
        } else {
            listOf(
                com.example.model.GradientStop(0f, style.colorArgb),
                com.example.model.GradientStop(1f, style.secondaryColorArgb)
            )
        }
        val colors = sortedStops.map { applyAlpha(it.colorArgb, alpha) }.toIntArray()
        val positions = sortedStops.map { it.offset.coerceIn(0f, 1f) }.toFloatArray()

        when (style.type) {
            PaintType.NONE -> {
                paint.shader = null
                paint.color = 0x00000000
            }
            PaintType.SOLID -> {
                paint.shader = null
                paint.color = applyAlpha(style.colorArgb, alpha)
            }
            PaintType.LINEAR_GRADIENT -> {
                val cx = x + w / 2f
                val cy = y + h / 2f
                val rad = Math.toRadians(style.gradientAngleDeg.toDouble())
                val dx = (cos(rad) * max(1f, w) / 2f).toFloat()
                val dy = (sin(rad) * max(1f, h) / 2f).toFloat()
                paint.shader = LinearGradient(
                    cx - dx, cy - dy, cx + dx, cy + dy,
                    colors, positions, Shader.TileMode.CLAMP
                )
            }
            PaintType.RADIAL_GRADIENT -> {
                val cx = x + w * style.gradientCenterX.coerceIn(0f, 1f)
                val cy = y + h * style.gradientCenterY.coerceIn(0f, 1f)
                val radius = max(12f, max(w, h) * style.radialRadiusFactor.coerceIn(0.15f, 1.5f))
                paint.shader = RadialGradient(cx, cy, radius, colors, positions, Shader.TileMode.CLAMP)
            }
            PaintType.SWEEP_GRADIENT -> {
                val cx = x + w * style.gradientCenterX.coerceIn(0f, 1f)
                val cy = y + h * style.gradientCenterY.coerceIn(0f, 1f)
                paint.shader = SweepGradient(cx, cy, colors, positions)
            }
        }
    }

    private fun createStrokePaint(stroke: StrokeStyle, blurRadius: Float, effectiveAlpha: Float): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = applyAlpha(stroke.colorArgb, stroke.opacity * effectiveAlpha)
            strokeWidth = stroke.width
            strokeCap = when (stroke.cap) {
                StrokeCapStyle.BUTT -> Paint.Cap.BUTT
                StrokeCapStyle.ROUND -> Paint.Cap.ROUND
                StrokeCapStyle.SQUARE -> Paint.Cap.SQUARE
            }
            strokeJoin = when (stroke.join) {
                StrokeJoinStyle.MITER -> Paint.Join.MITER
                StrokeJoinStyle.ROUND -> Paint.Join.ROUND
                StrokeJoinStyle.BEVEL -> Paint.Join.BEVEL
            }
            if (stroke.dashed) {
                pathEffect = DashPathEffect(
                    floatArrayOf(stroke.dashLength.coerceAtLeast(2f), stroke.gapLength.coerceAtLeast(2f)),
                    0f
                )
            }
            if (blurRadius > 0.5f) {
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        }
    }

    private fun mapBlendToPorterDuff(mode: BlendModeStyle): PorterDuff.Mode = when (mode) {
        BlendModeStyle.NORMAL -> PorterDuff.Mode.SRC_OVER
        BlendModeStyle.MULTIPLY -> PorterDuff.Mode.MULTIPLY
        BlendModeStyle.SCREEN -> PorterDuff.Mode.SCREEN
        BlendModeStyle.OVERLAY -> PorterDuff.Mode.OVERLAY
        BlendModeStyle.DARKEN -> PorterDuff.Mode.DARKEN
        BlendModeStyle.LIGHTEN -> PorterDuff.Mode.LIGHTEN
    }

    fun applyAlpha(argb: Long, opacity: Float): Int {
        val base = argb.toInt()
        val origA = (base ushr 24) and 0xFF
        val newA = ((origA * opacity).toInt()).coerceIn(0, 255)
        return (newA shl 24) or (base and 0x00FFFFFF)
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        var inSampleSize = 1
        val halfW = boundsOpts.outWidth / 2
        val halfH = boundsOpts.outHeight / 2
        while (halfW / inSampleSize >= reqWidth && halfH / inSampleSize >= reqHeight) {
            inSampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return BitmapFactory.decodeFile(path, decodeOpts)
    }
}
