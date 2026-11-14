package com.example.engine

import android.content.Context
import android.util.Base64
import android.util.Xml
import com.example.model.BezierNode
import com.example.model.BlendModeStyle
import com.example.model.DocumentBackgroundMode
import com.example.model.EffectStyle
import com.example.model.EllipseShape
import com.example.model.GradientStop
import com.example.model.GroupShape
import com.example.model.ImageShape
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathContour
import com.example.model.PathShape
import com.example.model.RectShape
import com.example.model.SpiderDocument
import com.example.model.StrokeCapStyle
import com.example.model.StrokeJoinStyle
import com.example.model.StrokeStyle
import com.example.model.TextAlignMode
import com.example.model.TextShape
import com.example.model.VectorElement
import com.example.model.VectorLayer
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

object SvgImporter {

    data class SvgImportResult(
        val widthPx: Int,
        val heightPx: Int,
        val layers: List<VectorLayer>,
        val backgroundMode: DocumentBackgroundMode = DocumentBackgroundMode.TRANSPARENT,
        val backgroundColorArgb: Long = 0xFF14141C
    ) {
        val elements: List<VectorElement>
            get() = layers.flatMap { it.elements }
    }

    private data class ParsedGradient(
        val type: PaintType,
        val angleDeg: Float,
        val centerX: Float,
        val centerY: Float,
        val radiusFactor: Float,
        val stops: List<GradientStop>
    )

    private data class GroupFrame(
        val name: String,
        val opacity: Float,
        val isVisible: Boolean,
        val offsetX: Float,
        val offsetY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val rotationDeg: Float,
        val clipId: String?,
        val inheritedStyle: Map<String, String>,
        val collected: MutableList<VectorElement> = mutableListOf()
    )

    fun importToDocument(
        svgContent: String,
        name: String,
        context: Context? = null
    ): SpiderDocument {
        val res = parseSvg(svgContent, context)
        val layers = if (res.layers.isNotEmpty()) {
            res.layers
        } else {
            listOf(VectorLayer(name = "Capa SVG"))
        }
        return SpiderDocument(
            name = name.ifBlank { "Diseño SVG" },
            widthPx = res.widthPx,
            heightPx = res.heightPx,
            backgroundMode = res.backgroundMode,
            backgroundColorArgb = res.backgroundColorArgb,
            layers = layers
        )
    }

    fun parseSvg(svgContent: String, context: Context? = null): SvgImportResult {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(svgContent))
        }
        var docW = 1080
        var docH = 1080
        var vbOffsetX = 0f
        var vbOffsetY = 0f
        var vbScaleX = 1f
        var vbScaleY = 1f

        val topLayers = mutableListOf<VectorLayer>()
        val rootElements = mutableListOf<VectorElement>()
        val groupStack = ArrayDeque<GroupFrame>()

        val gradients = mutableMapOf<String, ParsedGradient>()
        val clipPaths = mutableMapOf<String, MutableList<VectorElement>>()
        val pathDefs = mutableMapOf<String, String>()

        var defsDepth = 0
        var currentClipId: String? = null
        var currentGradId: String? = null
        var currentGradType: PaintType = PaintType.LINEAR_GRADIENT
        var currentGradAngle = 45f
        var currentGradCx = 0.5f
        var currentGradCy = 0.5f
        var currentGradRadius = 0.55f
        var currentGradStops = mutableListOf<GradientStop>()

        var bgMode = DocumentBackgroundMode.TRANSPARENT
        var bgColorArgb = 0xFF14141C

        fun currentInherited(): Map<String, String> =
            groupStack.lastOrNull()?.inheritedStyle ?: emptyMap()

        fun currentTransform(): QuadTransform {
            val top = groupStack.lastOrNull()
            return if (top != null) {
                QuadTransform(top.offsetX, top.offsetY, top.scaleX, top.scaleY)
            } else {
                QuadTransform(-vbOffsetX * vbScaleX, -vbOffsetY * vbScaleY, vbScaleX, vbScaleY)
            }
        }

        fun addParsedElement(el: VectorElement) {
            if (currentClipId != null) {
                clipPaths.getOrPut(currentClipId!!) { mutableListOf() }.add(el)
                return
            }
            if (defsDepth > 0) return

            val topGroup = groupStack.lastOrNull()
            if (topGroup != null) {
                topGroup.collected.add(el)
            } else {
                // Detect full-canvas background rect exported by Spider Studio or other tools
                if (el is RectShape &&
                    el.id != "" &&
                    el.fill.type == PaintType.SOLID &&
                    !el.stroke.enabled &&
                    kotlin.math.abs(el.x) <= 1f &&
                    kotlin.math.abs(el.y) <= 1f &&
                    kotlin.math.abs(el.width - docW) <= 2f &&
                    kotlin.math.abs(el.height - docH) <= 2f &&
                    rootElements.isEmpty() &&
                    topLayers.isEmpty()
                ) {
                    bgMode = DocumentBackgroundMode.SOLID
                    val a = ((el.fill.opacity * 255f).toLong().coerceIn(0L, 255L)) shl 24
                    bgColorArgb = (el.fill.colorArgb and 0x00FFFFFFL) or a
                } else {
                    rootElements.add(el)
                }
            }
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name?.lowercase()?.substringAfter(':') ?: ""
                    val tr = parseTransform(parser.getAttributeValue(null, "transform"))
                    val curTr = currentTransform()
                    val ox = curTr.ox + tr.tx * curTr.sx
                    val oy = curTr.oy + tr.ty * curTr.sy
                    val sx = curTr.sx * tr.sx
                    val sy = curTr.sy * tr.sy

                    when (tag) {
                        "svg" -> {
                            val vb = parser.getAttributeValue(null, "viewBox")
                            val rawW = parseDim(parser.getAttributeValue(null, "width"))
                            val rawH = parseDim(parser.getAttributeValue(null, "height"))
                            if (!vb.isNullOrBlank()) {
                                val parts = vb.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
                                if (parts.size == 4) {
                                    vbOffsetX = parts[0]
                                    vbOffsetY = parts[1]
                                    val vbW = parts[2].coerceAtLeast(1f)
                                    val vbH = parts[3].coerceAtLeast(1f)
                                    if (vbW < 64f && (rawW ?: 0f) >= 64f && (rawH ?: 0f) >= 64f) {
                                        docW = rawW!!.toInt().coerceIn(32, 8192)
                                        docH = rawH!!.toInt().coerceIn(32, 8192)
                                        vbScaleX = docW / vbW
                                        vbScaleY = docH / vbH
                                    } else {
                                        docW = vbW.toInt().coerceIn(32, 8192)
                                        docH = vbH.toInt().coerceIn(32, 8192)
                                        vbScaleX = 1f
                                        vbScaleY = 1f
                                    }
                                }
                            } else {
                                if (rawW != null) docW = rawW.toInt().coerceIn(32, 8192)
                                if (rawH != null) docH = rawH.toInt().coerceIn(32, 8192)
                            }
                        }
                        "defs", "mask", "symbol", "pattern" -> {
                            defsDepth++
                        }
                        "clippath" -> {
                            defsDepth++
                            currentClipId = parser.getAttributeValue(null, "id")
                        }
                        "lineargradient" -> {
                            defsDepth++
                            currentGradId = parser.getAttributeValue(null, "id")
                            currentGradType = PaintType.LINEAR_GRADIENT
                            val x1 = parsePercentOrFraction(parser.getAttributeValue(null, "x1"), 0f)
                            val y1 = parsePercentOrFraction(parser.getAttributeValue(null, "y1"), 0f)
                            val x2 = parsePercentOrFraction(parser.getAttributeValue(null, "x2"), 1f)
                            val y2 = parsePercentOrFraction(parser.getAttributeValue(null, "y2"), 0f)
                            currentGradAngle = Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
                            currentGradStops = mutableListOf()
                        }
                        "radialgradient" -> {
                            defsDepth++
                            currentGradId = parser.getAttributeValue(null, "id")
                            currentGradType = PaintType.RADIAL_GRADIENT
                            currentGradCx = parsePercentOrFraction(parser.getAttributeValue(null, "cx"), 0.5f)
                            currentGradCy = parsePercentOrFraction(parser.getAttributeValue(null, "cy"), 0.5f)
                            currentGradRadius = parsePercentOrFraction(parser.getAttributeValue(null, "r"), 0.5f)
                            currentGradStops = mutableListOf()
                        }
                        "stop" -> {
                            val styleAttr = parser.getAttributeValue(null, "style")
                            val stopColorRaw = parser.getAttributeValue(null, "stop-color")
                                ?: extractStyleProp(styleAttr, "stop-color")
                            val stopOpacityRaw = (parser.getAttributeValue(null, "stop-opacity")
                                ?: extractStyleProp(styleAttr, "stop-opacity"))?.toFloatOrNull() ?: 1f
                            val offsetRaw = parsePercentOrFraction(
                                parser.getAttributeValue(null, "offset"),
                                if (currentGradStops.isEmpty()) 0f else 1f
                            ).coerceIn(0f, 1f)
                            val baseColor = stopColorRaw?.let { parseColor(it) } ?: 0xFF8B5CF6
                            val alpha = (((((baseColor ushr 24) and 0xFF) / 255f) * stopOpacityRaw) * 255f)
                                .toInt().coerceIn(0, 255)
                            val finalColor = (baseColor and 0x00FFFFFFL) or (alpha.toLong() shl 24)
                            currentGradStops.add(GradientStop(offsetRaw, finalColor))
                        }
                        "g" -> {
                            val inherited = mergeInheritedStyles(currentInherited(), parser)
                            val label = parser.getAttributeValue(null, "inkscape:label")
                                ?: parser.getAttributeValue(null, "data-name")
                                ?: parser.getAttributeValue(null, "id")
                                ?: "Capa ${topLayers.size + 1}"
                            val op = (parser.getAttributeValue(null, "opacity")
                                ?: extractStyleProp(parser.getAttributeValue(null, "style"), "opacity"))
                                ?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                            val disp = parser.getAttributeValue(null, "display")
                                ?: extractStyleProp(parser.getAttributeValue(null, "style"), "display")
                            val vis = parser.getAttributeValue(null, "visibility")
                                ?: extractStyleProp(parser.getAttributeValue(null, "style"), "visibility")
                            val isVisible = disp != "none" && vis != "hidden"
                            val clipAttr = parser.getAttributeValue(null, "clip-path")
                            val clipRef = clipAttr?.let { extractUrlRef(it) }

                            groupStack.addLast(
                                GroupFrame(
                                    name = label,
                                    opacity = op,
                                    isVisible = isVisible,
                                    offsetX = ox,
                                    offsetY = oy,
                                    scaleX = sx,
                                    scaleY = sy,
                                    rotationDeg = tr.rotDeg,
                                    clipId = clipRef,
                                    inheritedStyle = inherited
                                )
                            )
                        }
                        "rect" -> {
                            val x = (parseDim(parser.getAttributeValue(null, "x")) ?: 0f) * sx + ox
                            val y = (parseDim(parser.getAttributeValue(null, "y")) ?: 0f) * sy + oy
                            val w = (parseDim(parser.getAttributeValue(null, "width")) ?: 100f) * sx
                            val h = (parseDim(parser.getAttributeValue(null, "height")) ?: 100f) * sy
                            val rx = (parseDim(parser.getAttributeValue(null, "rx")) ?: 0f) * sx
                            val (fill, stroke, opacity, effects, visible) = parseStyles(parser, gradients, currentInherited())
                            val elName = resolveElementName(parser, "Rectángulo")
                            addParsedElement(
                                RectShape(
                                    name = elName,
                                    x = x, y = y, width = max(2f, w), height = max(2f, h),
                                    rotationDeg = tr.rotDeg,
                                    opacity = opacity,
                                    isVisible = visible,
                                    cornerRadius = rx,
                                    fill = fill,
                                    stroke = stroke,
                                    effects = effects
                                )
                            )
                        }
                        "circle" -> {
                            val cx = (parseDim(parser.getAttributeValue(null, "cx")) ?: 0f) * sx + ox
                            val cy = (parseDim(parser.getAttributeValue(null, "cy")) ?: 0f) * sy + oy
                            val r = (parseDim(parser.getAttributeValue(null, "r")) ?: 50f) * ((sx + sy) * 0.5f)
                            val (fill, stroke, opacity, effects, visible) = parseStyles(parser, gradients, currentInherited())
                            addParsedElement(
                                EllipseShape(
                                    name = resolveElementName(parser, "Círculo"),
                                    x = cx - r, y = cy - r, width = max(2f, r * 2f), height = max(2f, r * 2f),
                                    rotationDeg = tr.rotDeg,
                                    opacity = opacity,
                                    isVisible = visible,
                                    fill = fill,
                                    stroke = stroke,
                                    effects = effects
                                )
                            )
                        }
                        "ellipse" -> {
                            val cx = (parseDim(parser.getAttributeValue(null, "cx")) ?: 0f) * sx + ox
                            val cy = (parseDim(parser.getAttributeValue(null, "cy")) ?: 0f) * sy + oy
                            val rx = (parseDim(parser.getAttributeValue(null, "rx")) ?: 50f) * sx
                            val ry = (parseDim(parser.getAttributeValue(null, "ry")) ?: 50f) * sy
                            val (fill, stroke, opacity, effects, visible) = parseStyles(parser, gradients, currentInherited())
                            addParsedElement(
                                EllipseShape(
                                    name = resolveElementName(parser, "Elipse"),
                                    x = cx - rx, y = cy - ry, width = max(2f, rx * 2f), height = max(2f, ry * 2f),
                                    rotationDeg = tr.rotDeg,
                                    opacity = opacity,
                                    isVisible = visible,
                                    fill = fill,
                                    stroke = stroke,
                                    effects = effects
                                )
                            )
                        }
                        "line" -> {
                            val x1 = (parseDim(parser.getAttributeValue(null, "x1")) ?: 0f) * sx + ox
                            val y1 = (parseDim(parser.getAttributeValue(null, "y1")) ?: 0f) * sy + oy
                            val x2 = (parseDim(parser.getAttributeValue(null, "x2")) ?: 100f) * sx + ox
                            val y2 = (parseDim(parser.getAttributeValue(null, "y2")) ?: 100f) * sy + oy
                            val (_, stroke, opacity, effects, visible) = parseStyles(
                                parser, gradients, currentInherited(), defaultStrokeEnabled = true
                            )
                            val raw = VectorMath.recalculatePathBounds(
                                PathShape(
                                    name = resolveElementName(parser, "Línea"),
                                    x = min(x1, x2), y = min(y1, y2),
                                    width = max(2f, kotlin.math.abs(x2 - x1)),
                                    height = max(2f, kotlin.math.abs(y2 - y1)),
                                    rotationDeg = tr.rotDeg,
                                    opacity = opacity,
                                    isVisible = visible,
                                    fill = PaintStyle(type = PaintType.NONE),
                                    stroke = stroke,
                                    effects = effects,
                                    nodes = listOf(BezierNode(x = x1, y = y1), BezierNode(x = x2, y = y2)),
                                    isClosed = false
                                )
                            )
                            addParsedElement(raw)
                        }
                        "polygon", "polyline" -> {
                            val ptsAttr = parser.getAttributeValue(null, "points") ?: ""
                            val nums = ptsAttr.trim().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
                            if (nums.size >= 4) {
                                val nodes = mutableListOf<BezierNode>()
                                var i = 0
                                while (i + 1 < nums.size) {
                                    nodes.add(BezierNode(x = nums[i] * sx + ox, y = nums[i + 1] * sy + oy))
                                    i += 2
                                }
                                val (fill, stroke, opacity, effects, visible) = parseStyles(parser, gradients, currentInherited())
                                val path = VectorBooleanEngine.recalculateCompoundBounds(
                                    PathShape(
                                        name = resolveElementName(parser, if (tag == "polygon") "Polígono" else "Polilínea"),
                                        x = 0f, y = 0f, width = 10f, height = 10f,
                                        rotationDeg = tr.rotDeg,
                                        opacity = opacity,
                                        isVisible = visible,
                                        fill = fill,
                                        stroke = stroke,
                                        effects = effects,
                                        nodes = nodes,
                                        isClosed = tag == "polygon"
                                    )
                                )
                                addParsedElement(path)
                            }
                        }
                        "path" -> {
                            val d = parser.getAttributeValue(null, "d") ?: ""
                            val pathId = parser.getAttributeValue(null, "id")
                            if (defsDepth > 0 && currentClipId == null && pathId != null && d.isNotBlank()) {
                                pathDefs[pathId] = d
                            }
                            val contours = parsePathContours(d, ox, oy, sx, sy)
                            if (contours.isNotEmpty() && contours.first().nodes.isNotEmpty()) {
                                val (fill, stroke, opacity, effects, visible) = parseStyles(parser, gradients, currentInherited())
                                val fillRule = parser.getAttributeValue(null, "fill-rule")
                                    ?: extractStyleProp(parser.getAttributeValue(null, "style"), "fill-rule")
                                val path = VectorBooleanEngine.recalculateCompoundBounds(
                                    PathShape(
                                        name = resolveElementName(parser, "Trazado"),
                                        x = 0f, y = 0f, width = 10f, height = 10f,
                                        rotationDeg = tr.rotDeg,
                                        opacity = opacity,
                                        isVisible = visible,
                                        fill = fill,
                                        stroke = stroke,
                                        effects = effects,
                                        nodes = contours.first().nodes,
                                        isClosed = contours.first().isClosed,
                                        extraContours = contours.drop(1),
                                        fillEvenOdd = fillRule == "evenodd"
                                    )
                                )
                                addParsedElement(path)
                            }
                        }
                        "text" -> {
                            val x = (parseDim(parser.getAttributeValue(null, "x")) ?: 0f) * sx + ox
                            val y = (parseDim(parser.getAttributeValue(null, "y")) ?: 60f) * sy + oy
                            val styleAttr = parser.getAttributeValue(null, "style")
                            val fontSize = ((parseDim(
                                parser.getAttributeValue(null, "font-size")
                                    ?: extractStyleProp(styleAttr, "font-size")
                                    ?: currentInherited()["font-size"]
                            ) ?: 42f) * sy).coerceIn(8f, 400f)
                            val fontWeight = parser.getAttributeValue(null, "font-weight")
                                ?: extractStyleProp(styleAttr, "font-weight") ?: "normal"
                            val fontStyle = parser.getAttributeValue(null, "font-style")
                                ?: extractStyleProp(styleAttr, "font-style") ?: "normal"
                            val textAnchor = parser.getAttributeValue(null, "text-anchor")
                                ?: extractStyleProp(styleAttr, "text-anchor") ?: "start"
                            val textDec = parser.getAttributeValue(null, "text-decoration")
                                ?: extractStyleProp(styleAttr, "text-decoration") ?: ""
                            val fontFamilyRaw = (parser.getAttributeValue(null, "font-family")
                                ?: extractStyleProp(styleAttr, "font-family")
                                ?: currentInherited()["font-family"]
                                ?: "").lowercase()
                            val fontKey = when {
                                fontFamilyRaw.contains("space grotesk") -> "space_grotesk"
                                fontFamilyRaw.contains("jakarta") -> "plus_jakarta_sans"
                                fontFamilyRaw.contains("jetbrains") || fontFamilyRaw.contains("mono") -> "jetbrains_mono"
                                fontFamilyRaw.contains("serif") && !fontFamilyRaw.contains("sans") -> "serif"
                                else -> "space_grotesk"
                            }
                            val alignment = when (textAnchor) {
                                "middle" -> TextAlignMode.CENTER
                                "end" -> TextAlignMode.RIGHT
                                else -> TextAlignMode.LEFT
                            }
                            val (fill, stroke, opacity, effects, visible) = parseStyles(parser, gradients, currentInherited())
                            val elName = resolveElementName(parser, "Texto")
                            val content = readElementText(parser).trim()
                            if (content.isNotEmpty()) {
                                val lineCount = content.count { it == '\n' } + 1
                                val approxW = max(120f, content.lines().maxOf { it.length } * fontSize * 0.62f)
                                val adjustedX = when (alignment) {
                                    TextAlignMode.LEFT -> x
                                    TextAlignMode.CENTER -> (x - approxW / 2f).coerceAtLeast(0f)
                                    TextAlignMode.RIGHT -> (x - approxW).coerceAtLeast(0f)
                                }
                                addParsedElement(
                                    TextShape(
                                        name = elName,
                                        x = adjustedX,
                                        y = (y - fontSize).coerceAtLeast(0f),
                                        width = approxW,
                                        height = fontSize * 1.3f * lineCount,
                                        rotationDeg = tr.rotDeg,
                                        opacity = opacity,
                                        isVisible = visible,
                                        text = content,
                                        fontFamilyKey = fontKey,
                                        fontSize = fontSize,
                                        isBold = fontWeight == "bold" || (fontWeight.toIntOrNull() ?: 400) >= 600,
                                        isItalic = fontStyle == "italic",
                                        alignment = alignment,
                                        isUnderline = textDec.contains("underline"),
                                        isStrikethrough = textDec.contains("line-through"),
                                        fill = if (fill.type == PaintType.NONE) PaintStyle(colorArgb = 0xFFF4F4F8) else fill,
                                        stroke = stroke,
                                        effects = effects
                                    )
                                )
                            }
                        }
                        "image" -> {
                            val href = parser.getAttributeValue(null, "href")
                                ?: parser.getAttributeValue(null, "xlink:href")
                            if (context != null && href != null && href.startsWith("data:image/")) {
                                val x = (parseDim(parser.getAttributeValue(null, "x")) ?: 0f) * sx + ox
                                val y = (parseDim(parser.getAttributeValue(null, "y")) ?: 0f) * sy + oy
                                val w = (parseDim(parser.getAttributeValue(null, "width")) ?: 200f) * sx
                                val h = (parseDim(parser.getAttributeValue(null, "height")) ?: 200f) * sy
                                val b64 = href.substringAfter("base64,", "")
                                if (b64.isNotEmpty()) {
                                    runCatching {
                                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                                        val dir = File(context.filesDir, "imported_media").apply { mkdirs() }
                                        val ext = if (href.contains("jpeg") || href.contains("jpg")) "jpg" else "png"
                                        val outFile = File(dir, "svg_img_${UUID.randomUUID()}.$ext")
                                        FileOutputStream(outFile).use { it.write(bytes) }
                                        val op = parser.getAttributeValue(null, "opacity")?.toFloatOrNull() ?: 1f
                                        addParsedElement(
                                            ImageShape(
                                                name = resolveElementName(parser, "Imagen SVG"),
                                                x = x, y = y, width = max(4f, w), height = max(4f, h),
                                                rotationDeg = tr.rotDeg,
                                                opacity = op,
                                                imagePath = outFile.absolutePath
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.lowercase()?.substringAfter(':') ?: ""
                    when (tag) {
                        "defs", "mask", "symbol", "pattern" -> {
                            defsDepth = (defsDepth - 1).coerceAtLeast(0)
                        }
                        "clippath" -> {
                            defsDepth = (defsDepth - 1).coerceAtLeast(0)
                            currentClipId = null
                        }
                        "lineargradient", "radialgradient" -> {
                            defsDepth = (defsDepth - 1).coerceAtLeast(0)
                            val id = currentGradId
                            if (id != null && currentGradStops.isNotEmpty()) {
                                val sorted = currentGradStops.sortedBy { it.offset }
                                gradients[id] = ParsedGradient(
                                    type = currentGradType,
                                    angleDeg = currentGradAngle,
                                    centerX = currentGradCx,
                                    centerY = currentGradCy,
                                    radiusFactor = currentGradRadius,
                                    stops = sorted
                                )
                            }
                            currentGradId = null
                        }
                        "g" -> {
                            val finished = if (groupStack.isNotEmpty()) groupStack.removeLast() else null
                            if (finished != null && defsDepth == 0) {
                                val clipChildren = finished.clipId?.let { clipPaths[it] }.orEmpty()
                                val isClipGroup = clipChildren.isNotEmpty() && finished.collected.isNotEmpty()
                                if (groupStack.isEmpty()) {
                                    // Top-level <g> becomes a VectorLayer!
                                    val layerElements = if (isClipGroup) {
                                        val all = listOf(clipChildren.first()) + finished.collected
                                        val bounds = VectorMath.computeCombinedBounds(all)
                                        listOf(
                                            GroupShape(
                                                name = finished.name,
                                                x = bounds?.left ?: 0f,
                                                y = bounds?.top ?: 0f,
                                                width = max(10f, bounds?.width ?: 100f),
                                                height = max(10f, bounds?.height ?: 100f),
                                                rotationDeg = finished.rotationDeg,
                                                opacity = 1f,
                                                children = all,
                                                hasClippingMask = true
                                            )
                                        )
                                    } else {
                                        finished.collected.toList()
                                    }
                                    topLayers.add(
                                        VectorLayer(
                                            name = finished.name,
                                            isVisible = finished.isVisible,
                                            opacity = finished.opacity,
                                            elements = layerElements
                                        )
                                    )
                                } else {
                                    // Nested <g> becomes a GroupShape inside parent layer/group
                                    val groupChildren = if (isClipGroup) {
                                        listOf(clipChildren.first()) + finished.collected
                                    } else {
                                        finished.collected.toList()
                                    }
                                    if (groupChildren.isNotEmpty()) {
                                        val bounds = VectorMath.computeCombinedBounds(groupChildren)
                                        groupStack.last().collected.add(
                                            GroupShape(
                                                name = finished.name,
                                                x = bounds?.left ?: 0f,
                                                y = bounds?.top ?: 0f,
                                                width = max(10f, bounds?.width ?: 100f),
                                                height = max(10f, bounds?.height ?: 100f),
                                                rotationDeg = finished.rotationDeg,
                                                opacity = finished.opacity,
                                                isVisible = finished.isVisible,
                                                children = groupChildren,
                                                hasClippingMask = isClipGroup
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        val finalLayers = buildList {
            if (rootElements.isNotEmpty()) {
                add(VectorLayer(name = "Capa Principal", elements = rootElements))
            }
            addAll(topLayers)
            if (isEmpty()) {
                add(VectorLayer(name = "Capa 1"))
            }
        }

        return SvgImportResult(
            widthPx = docW,
            heightPx = docH,
            layers = finalLayers,
            backgroundMode = bgMode,
            backgroundColorArgb = bgColorArgb
        )
    }

    private fun resolveElementName(parser: XmlPullParser, fallback: String): String {
        return parser.getAttributeValue(null, "inkscape:label")
            ?: parser.getAttributeValue(null, "data-name")
            ?: parser.getAttributeValue(null, "id")
            ?: fallback
    }

    private fun readElementText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        var firstTspan = true
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT -> sb.append(parser.text ?: "")
                XmlPullParser.START_TAG -> {
                    if (parser.name?.equals("tspan", ignoreCase = true) == true) {
                        if (!firstTspan && sb.isNotEmpty() && !sb.endsWith("\n")) {
                            sb.append("\n")
                        }
                        firstTspan = false
                    }
                    depth++
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString()
    }

    private data class ParsedElementStyle(
        val fill: PaintStyle,
        val stroke: StrokeStyle,
        val opacity: Float,
        val effects: EffectStyle,
        val isVisible: Boolean
    )

    private fun mergeInheritedStyles(
        parent: Map<String, String>,
        parser: XmlPullParser
    ): Map<String, String> {
        val next = parent.toMutableMap()
        val styleAttr = parser.getAttributeValue(null, "style")
        val keys = listOf(
            "fill", "fill-opacity", "stroke", "stroke-width", "stroke-opacity",
            "stroke-linecap", "stroke-linejoin", "font-size", "font-family"
        )
        keys.forEach { k ->
            val v = parser.getAttributeValue(null, k) ?: extractStyleProp(styleAttr, k)
            if (!v.isNullOrBlank()) next[k] = v
        }
        return next
    }

    private fun parseStyles(
        parser: XmlPullParser,
        gradients: Map<String, ParsedGradient>,
        inherited: Map<String, String>,
        defaultStrokeEnabled: Boolean = false
    ): ParsedElementStyle {
        val styleAttr = parser.getAttributeValue(null, "style")
        fun attrOrStyle(name: String): String? =
            parser.getAttributeValue(null, name)
                ?: extractStyleProp(styleAttr, name)
                ?: inherited[name]

        val fillRaw = attrOrStyle("fill")
        val fillOpacity = attrOrStyle("fill-opacity")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        val strokeRaw = attrOrStyle("stroke")
        val strokeW = parseDim(attrOrStyle("stroke-width")) ?: 3f
        val strokeOpacity = attrOrStyle("stroke-opacity")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        val opacity = (parser.getAttributeValue(null, "opacity") ?: extractStyleProp(styleAttr, "opacity"))
            ?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        val disp = parser.getAttributeValue(null, "display") ?: extractStyleProp(styleAttr, "display")
        val vis = parser.getAttributeValue(null, "visibility") ?: extractStyleProp(styleAttr, "visibility")
        val isVisible = disp != "none" && vis != "hidden"

        val blendCss = extractStyleProp(styleAttr, "mix-blend-mode")
        val blendMode = when (blendCss?.lowercase()) {
            "multiply" -> BlendModeStyle.MULTIPLY
            "screen" -> BlendModeStyle.SCREEN
            "overlay" -> BlendModeStyle.OVERLAY
            "darken" -> BlendModeStyle.DARKEN
            "lighten" -> BlendModeStyle.LIGHTEN
            else -> BlendModeStyle.NORMAL
        }

        val fill = when {
            fillRaw == "none" -> PaintStyle(type = PaintType.NONE)
            fillRaw != null && fillRaw.contains("url(#") -> {
                val id = extractUrlRef(fillRaw)
                val grad = id?.let { gradients[it] }
                if (grad != null && grad.stops.isNotEmpty()) {
                    PaintStyle(
                        type = grad.type,
                        colorArgb = grad.stops.first().colorArgb,
                        secondaryColorArgb = grad.stops.last().colorArgb,
                        gradientAngleDeg = grad.angleDeg,
                        opacity = fillOpacity,
                        stops = grad.stops,
                        gradientCenterX = grad.centerX,
                        gradientCenterY = grad.centerY,
                        radialRadiusFactor = grad.radiusFactor
                    )
                } else {
                    PaintStyle(type = PaintType.SOLID, colorArgb = 0xFF8B5CF6, opacity = fillOpacity)
                }
            }
            fillRaw != null -> PaintStyle(
                type = PaintType.SOLID,
                colorArgb = parseColor(fillRaw) ?: 0xFF8B5CF6,
                opacity = fillOpacity
            )
            else -> PaintStyle(type = PaintType.SOLID, colorArgb = 0xFF000000, opacity = fillOpacity)
        }

        val cap = when (attrOrStyle("stroke-linecap")?.lowercase()) {
            "butt" -> StrokeCapStyle.BUTT
            "square" -> StrokeCapStyle.SQUARE
            else -> StrokeCapStyle.ROUND
        }
        val join = when (attrOrStyle("stroke-linejoin")?.lowercase()) {
            "miter" -> StrokeJoinStyle.MITER
            "bevel" -> StrokeJoinStyle.BEVEL
            else -> StrokeJoinStyle.ROUND
        }
        val dashArray = attrOrStyle("stroke-dasharray")
        val dashed = !dashArray.isNullOrBlank() && dashArray != "none"

        val stroke = if ((strokeRaw != null && strokeRaw != "none") || defaultStrokeEnabled) {
            StrokeStyle(
                enabled = true,
                colorArgb = strokeRaw?.let { parseColor(it) } ?: 0xFFF4F4F8,
                width = strokeW.coerceAtLeast(0.5f),
                opacity = strokeOpacity,
                cap = cap,
                join = join,
                dashed = dashed
            )
        } else {
            StrokeStyle(enabled = false)
        }

        return ParsedElementStyle(
            fill = fill,
            stroke = stroke,
            opacity = opacity,
            effects = EffectStyle(blendMode = blendMode),
            isVisible = isVisible
        )
    }

    private fun extractUrlRef(raw: String): String? {
        val m = Regex("""url\(\s*#([^)\s]+)\s*\)""").find(raw) ?: return null
        return m.groupValues[1].trim()
    }

    private fun extractStyleProp(style: String?, prop: String): String? {
        if (style.isNullOrBlank()) return null
        return style.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$prop:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
    }

    private data class QuadTransform(val ox: Float, val oy: Float, val sx: Float, val sy: Float)
    private data class TransformResult(val tx: Float, val ty: Float, val sx: Float, val sy: Float, val rotDeg: Float)

    private fun parseTransform(transform: String?): TransformResult {
        if (transform.isNullOrBlank()) return TransformResult(0f, 0f, 1f, 1f, 0f)
        var tx = 0f
        var ty = 0f
        var sx = 1f
        var sy = 1f
        var rot = 0f
        Regex("""translate\(\s*([+-]?[\d.]+)(?:[\s,]+([+-]?[\d.]+))?\s*\)""").find(transform)?.let { m ->
            tx = m.groupValues[1].toFloatOrNull() ?: 0f
            ty = m.groupValues.getOrNull(2)?.toFloatOrNull() ?: 0f
        }
        Regex("""scale\(\s*([+-]?[\d.]+)(?:[\s,]+([+-]?[\d.]+))?\s*\)""").find(transform)?.let { m ->
            sx = m.groupValues[1].toFloatOrNull() ?: 1f
            sy = m.groupValues.getOrNull(2)?.toFloatOrNull()?.takeIf { it != 0f } ?: sx
        }
        Regex("""rotate\(\s*([+-]?[\d.]+)""").find(transform)?.let { m ->
            rot = m.groupValues[1].toFloatOrNull() ?: 0f
        }
        return TransformResult(tx, ty, sx, sy, rot)
    }

    private fun parsePercentOrFraction(raw: String?, default: Float): Float {
        if (raw.isNullOrBlank()) return default
        val trimmed = raw.trim()
        return if (trimmed.endsWith("%")) {
            (trimmed.dropLast(1).toFloatOrNull() ?: (default * 100f)) / 100f
        } else {
            trimmed.toFloatOrNull() ?: default
        }
    }

    private fun parseDim(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim().lowercase()
        return when {
            t.endsWith("px") -> t.dropLast(2).toFloatOrNull()
            t.endsWith("dp") -> t.dropLast(2).toFloatOrNull()
            t.endsWith("pt") -> t.dropLast(2).toFloatOrNull()?.times(96f / 72f)
            t.endsWith("in") -> t.dropLast(2).toFloatOrNull()?.times(96f)
            t.endsWith("cm") -> t.dropLast(2).toFloatOrNull()?.times(96f / 2.54f)
            t.endsWith("mm") -> t.dropLast(2).toFloatOrNull()?.times(96f / 25.4f)
            else -> t.toFloatOrNull()
        }
    }

    private fun parseColor(raw: String): Long? {
        val c = raw.trim().lowercase()
        if (c == "none" || c == "transparent") return null
        if (c.startsWith("#")) {
            val hex = c.drop(1)
            return when (hex.length) {
                3 -> {
                    val r = "${hex[0]}${hex[0]}"
                    val g = "${hex[1]}${hex[1]}"
                    val b = "${hex[2]}${hex[2]}"
                    "FF$r$g$b".toLongOrNull(16)
                }
                6 -> "FF$hex".toLongOrNull(16)
                8 -> {
                    // CSS #RRGGBBAA -> ARGB
                    val rgb = hex.substring(0, 6)
                    val aa = hex.substring(6, 8)
                    "$aa$rgb".toLongOrNull(16)
                }
                else -> null
            }
        }
        if (c.startsWith("rgb(")) {
            val nums = c.removePrefix("rgb(").removeSuffix(")").split(",").mapNotNull { it.trim().toIntOrNull() }
            if (nums.size == 3) {
                return (0xFFL shl 24) or ((nums[0].toLong() and 0xFF) shl 16) or
                    ((nums[1].toLong() and 0xFF) shl 8) or (nums[2].toLong() and 0xFF)
            }
        }
        if (c.startsWith("rgba(")) {
            val parts = c.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
            if (parts.size == 4) {
                val r = parts[0].toIntOrNull()?.coerceIn(0, 255) ?: 0
                val g = parts[1].toIntOrNull()?.coerceIn(0, 255) ?: 0
                val b = parts[2].toIntOrNull()?.coerceIn(0, 255) ?: 0
                val a = ((parts[3].toFloatOrNull() ?: 1f).coerceIn(0f, 1f) * 255f).toInt()
                return ((a.toLong() and 0xFF) shl 24) or ((r.toLong() and 0xFF) shl 16) or
                    ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)
            }
        }
        return when (c) {
            "white" -> 0xFFFFFFFF
            "black" -> 0xFF000000
            "red" -> 0xFFFF0000
            "green" -> 0xFF008000
            "lime" -> 0xFF00FF00
            "blue" -> 0xFF0000FF
            "yellow" -> 0xFFFFFF00
            "cyan", "aqua" -> 0xFF00FFFF
            "magenta", "fuchsia" -> 0xFFFF00FF
            "gray", "grey" -> 0xFF888888
            "silver" -> 0xFFC0C0C0
            "orange" -> 0xFFFFA500
            "purple" -> 0xFF800080
            "violet" -> 0xFFEE82EE
            "indigo" -> 0xFF4B0082
            "pink" -> 0xFFFFC0CB
            "gold" -> 0xFFFFD700
            "coral" -> 0xFFFF7F50
            "teal" -> 0xFF008080
            "navy" -> 0xFF000080
            "brown" -> 0xFFA52A2A
            else -> null
        }
    }

    private fun parsePathContours(
        d: String,
        ox: Float,
        oy: Float,
        sx: Float,
        sy: Float
    ): List<PathContour> {
        val tokens = Regex("""[MmLlHhVvCcSsQqTtAaZz]|[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?""")
            .findAll(d)
            .map { it.value }
            .toList()
        if (tokens.isEmpty()) return emptyList()

        val contours = mutableListOf<PathContour>()
        var nodes = mutableListOf<BezierNode>()
        var closed = false
        var idx = 0
        var cmd = 'M'
        var cx = 0f
        var cy = 0f
        var startX = 0f
        var startY = 0f
        var lastCubicCp2X = 0f
        var lastCubicCp2Y = 0f
        var prevWasCubic = false

        fun flushContourIfNotEmpty() {
            if (nodes.isNotEmpty()) {
                contours.add(PathContour(nodes = nodes, isClosed = closed))
                nodes = mutableListOf()
                closed = false
            }
        }

        fun nextFloat(): Float {
            while (idx < tokens.size && tokens[idx].length == 1 && tokens[idx][0].isLetter()) idx++
            return if (idx < tokens.size) tokens[idx++].toFloatOrNull() ?: 0f else 0f
        }

        fun hasMoreNumbers(): Boolean {
            return idx < tokens.size && !(tokens[idx].length == 1 && tokens[idx][0].isLetter())
        }

        fun tx(x: Float): Float = x * sx + ox
        fun ty(y: Float): Float = y * sy + oy

        while (idx < tokens.size) {
            val tok = tokens[idx]
            if (tok.length == 1 && tok[0].isLetter()) {
                cmd = tok[0]
                idx++
            }
            when (cmd) {
                'M', 'm' -> {
                    val isRel = cmd == 'm'
                    var first = true
                    while (hasMoreNumbers()) {
                        val nx = nextFloat() + if (isRel) cx else 0f
                        val ny = nextFloat() + if (isRel) cy else 0f
                        cx = nx
                        cy = ny
                        if (first) {
                            flushContourIfNotEmpty()
                            startX = cx
                            startY = cy
                            first = false
                            cmd = if (isRel) 'l' else 'L'
                        }
                        nodes.add(BezierNode(x = tx(cx), y = ty(cy)))
                    }
                    prevWasCubic = false
                }
                'L', 'l' -> {
                    val isRel = cmd == 'l'
                    while (hasMoreNumbers()) {
                        cx = nextFloat() + if (isRel) cx else 0f
                        cy = nextFloat() + if (isRel) cy else 0f
                        nodes.add(BezierNode(x = tx(cx), y = ty(cy)))
                    }
                    prevWasCubic = false
                }
                'H', 'h' -> {
                    val isRel = cmd == 'h'
                    while (hasMoreNumbers()) {
                        cx = nextFloat() + if (isRel) cx else 0f
                        nodes.add(BezierNode(x = tx(cx), y = ty(cy)))
                    }
                    prevWasCubic = false
                }
                'V', 'v' -> {
                    val isRel = cmd == 'v'
                    while (hasMoreNumbers()) {
                        cy = nextFloat() + if (isRel) cy else 0f
                        nodes.add(BezierNode(x = tx(cx), y = ty(cy)))
                    }
                    prevWasCubic = false
                }
                'C', 'c' -> {
                    val isRel = cmd == 'c'
                    while (hasMoreNumbers()) {
                        val c1x = nextFloat() + if (isRel) cx else 0f
                        val c1y = nextFloat() + if (isRel) cy else 0f
                        val c2x = nextFloat() + if (isRel) cx else 0f
                        val c2y = nextFloat() + if (isRel) cy else 0f
                        val ex = nextFloat() + if (isRel) cx else 0f
                        val ey = nextFloat() + if (isRel) cy else 0f
                        if (nodes.isNotEmpty()) {
                            val last = nodes.removeAt(nodes.lastIndex)
                            nodes.add(last.copy(cp2X = tx(c1x), cp2Y = ty(c1y)))
                        }
                        nodes.add(
                            BezierNode(
                                x = tx(ex), y = ty(ey),
                                cp1X = tx(c2x), cp1Y = ty(c2y),
                                cp2X = tx(ex), cp2Y = ty(ey),
                                smooth = true
                            )
                        )
                        lastCubicCp2X = c2x
                        lastCubicCp2Y = c2y
                        prevWasCubic = true
                        cx = ex
                        cy = ey
                    }
                }
                'S', 's' -> {
                    val isRel = cmd == 's'
                    while (hasMoreNumbers()) {
                        val c1x = if (prevWasCubic) 2f * cx - lastCubicCp2X else cx
                        val c1y = if (prevWasCubic) 2f * cy - lastCubicCp2Y else cy
                        val c2x = nextFloat() + if (isRel) cx else 0f
                        val c2y = nextFloat() + if (isRel) cy else 0f
                        val ex = nextFloat() + if (isRel) cx else 0f
                        val ey = nextFloat() + if (isRel) cy else 0f
                        if (nodes.isNotEmpty()) {
                            val last = nodes.removeAt(nodes.lastIndex)
                            nodes.add(last.copy(cp2X = tx(c1x), cp2Y = ty(c1y)))
                        }
                        nodes.add(
                            BezierNode(
                                x = tx(ex), y = ty(ey),
                                cp1X = tx(c2x), cp1Y = ty(c2y),
                                cp2X = tx(ex), cp2Y = ty(ey),
                                smooth = true
                            )
                        )
                        lastCubicCp2X = c2x
                        lastCubicCp2Y = c2y
                        prevWasCubic = true
                        cx = ex
                        cy = ey
                    }
                }
                'Q', 'q' -> {
                    val isRel = cmd == 'q'
                    while (hasMoreNumbers()) {
                        val qx = nextFloat() + if (isRel) cx else 0f
                        val qy = nextFloat() + if (isRel) cy else 0f
                        val ex = nextFloat() + if (isRel) cx else 0f
                        val ey = nextFloat() + if (isRel) cy else 0f
                        val c1x = cx + (2f / 3f) * (qx - cx)
                        val c1y = cy + (2f / 3f) * (qy - cy)
                        val c2x = ex + (2f / 3f) * (qx - ex)
                        val c2y = ey + (2f / 3f) * (qy - ey)
                        if (nodes.isNotEmpty()) {
                            val last = nodes.removeAt(nodes.lastIndex)
                            nodes.add(last.copy(cp2X = tx(c1x), cp2Y = ty(c1y)))
                        }
                        nodes.add(
                            BezierNode(
                                x = tx(ex), y = ty(ey),
                                cp1X = tx(c2x), cp1Y = ty(c2y),
                                cp2X = tx(ex), cp2Y = ty(ey),
                                smooth = true
                            )
                        )
                        cx = ex
                        cy = ey
                    }
                    prevWasCubic = false
                }
                'T', 't' -> {
                    val isRel = cmd == 't'
                    while (hasMoreNumbers()) {
                        cx = nextFloat() + if (isRel) cx else 0f
                        cy = nextFloat() + if (isRel) cy else 0f
                        nodes.add(BezierNode(x = tx(cx), y = ty(cy)))
                    }
                    prevWasCubic = false
                }
                'A', 'a' -> {
                    val isRel = cmd == 'a'
                    while (hasMoreNumbers()) {
                        nextFloat(); nextFloat(); nextFloat(); nextFloat(); nextFloat()
                        cx = nextFloat() + if (isRel) cx else 0f
                        cy = nextFloat() + if (isRel) cy else 0f
                        nodes.add(BezierNode(x = tx(cx), y = ty(cy)))
                    }
                    prevWasCubic = false
                }
                'Z', 'z' -> {
                    closed = true
                    cx = startX
                    cy = startY
                    prevWasCubic = false
                    flushContourIfNotEmpty()
                }
                else -> idx++
            }
        }
        flushContourIfNotEmpty()
        return contours
    }
}
