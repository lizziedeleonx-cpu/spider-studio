package com.example.data

import com.example.model.BezierNode
import com.example.model.BlendModeStyle
import com.example.model.BrushPreset
import com.example.model.CanvasGuide
import com.example.model.ChromeEffectConfig
import com.example.model.ChromeStyleType
import com.example.model.DocumentBackgroundMode
import com.example.model.EffectStyle
import com.example.model.EllipseShape
import com.example.model.GradientStop
import com.example.model.GroupShape
import com.example.model.GuideOrientation
import com.example.model.ImageShape
import com.example.model.NodeHandleMode
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathContour
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.StrokeCapStyle
import com.example.model.StrokeJoinStyle
import com.example.model.StrokeStyle
import com.example.model.TextAlignMode
import com.example.model.TextCaseMode
import com.example.model.TextShape
import com.example.model.VectorElement
import com.example.model.VectorLayer
import org.json.JSONArray
import org.json.JSONObject

object SpiderJsonCodec {

    private const val FORMAT_VERSION = 1

    private fun Float.safeDouble(fallback: Double = 0.0): Double =
        if (isFinite()) toDouble() else fallback

    fun encode(doc: SpiderDocument): String {
        val root = JSONObject()
        root.put("format", "spider-vector")
        root.put("version", FORMAT_VERSION)
        root.put("id", doc.id)
        root.put("name", doc.name)
        root.put("widthPx", doc.widthPx)
        root.put("heightPx", doc.heightPx)
        root.put("backgroundMode", doc.backgroundMode.name)
        root.put("backgroundColorArgb", doc.backgroundColorArgb)
        root.put("createdAt", doc.createdAt)
        root.put("updatedAt", doc.updatedAt)

        val guidesArr = JSONArray()
        doc.guides.forEach { g ->
            guidesArr.put(JSONObject().apply {
                put("id", g.id)
                put("orientation", g.orientation.name)
                put("positionPx", g.positionPx.safeDouble())
            })
        }
        root.put("guides", guidesArr)

        val layersArr = JSONArray()
        doc.layers.forEach { layer ->
            layersArr.put(encodeLayer(layer))
        }
        root.put("layers", layersArr)
        return root.toString()
    }

    fun decode(json: String): SpiderDocument {
        val root = JSONObject(json)
        val guidesArr = root.optJSONArray("guides") ?: JSONArray()
        val guides = buildList {
            for (i in 0 until guidesArr.length()) {
                val g = guidesArr.getJSONObject(i)
                add(
                    CanvasGuide(
                        id = g.optString("id"),
                        orientation = runCatching {
                            GuideOrientation.valueOf(g.optString("orientation"))
                        }.getOrDefault(GuideOrientation.VERTICAL),
                        positionPx = g.optDouble("positionPx", 0.0).toFloat()
                    )
                )
            }
        }
        val layersArr = root.optJSONArray("layers") ?: JSONArray()
        val layers = buildList {
            for (i in 0 until layersArr.length()) {
                add(decodeLayer(layersArr.getJSONObject(i)))
            }
        }.ifEmpty { listOf(VectorLayer(name = "Capa 1")) }

        return SpiderDocument(
            id = root.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            name = root.optString("name", "Proyecto"),
            widthPx = root.optInt("widthPx", 1080).coerceIn(64, 8192),
            heightPx = root.optInt("heightPx", 1080).coerceIn(64, 8192),
            backgroundMode = runCatching {
                DocumentBackgroundMode.valueOf(root.optString("backgroundMode"))
            }.getOrDefault(DocumentBackgroundMode.SOLID),
            backgroundColorArgb = root.optLong("backgroundColorArgb", 0xFF14141C),
            layers = layers,
            guides = guides,
            createdAt = root.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun encodeLayer(layer: VectorLayer): JSONObject = JSONObject().apply {
        put("id", layer.id)
        put("name", layer.name)
        put("isVisible", layer.isVisible)
        put("isLocked", layer.isLocked)
        put("opacity", layer.opacity.safeDouble(1.0))
        val elems = JSONArray()
        layer.elements.forEach { elems.put(encodeElement(it)) }
        put("elements", elems)
    }

    private fun decodeLayer(obj: JSONObject): VectorLayer {
        val elemsArr = obj.optJSONArray("elements") ?: JSONArray()
        val elems = buildList {
            for (i in 0 until elemsArr.length()) {
                decodeElement(elemsArr.getJSONObject(i))?.let { add(it) }
            }
        }
        return VectorLayer(
            id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            name = obj.optString("name", "Capa"),
            isVisible = obj.optBoolean("isVisible", true),
            isLocked = obj.optBoolean("isLocked", false),
            opacity = obj.optDouble("opacity", 1.0).toFloat(),
            elements = elems
        )
    }

    private fun encodeElement(e: VectorElement): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("x", e.x.safeDouble())
        put("y", e.y.safeDouble())
        put("width", e.width.safeDouble(100.0))
        put("height", e.height.safeDouble(100.0))
        put("rotationDeg", e.rotationDeg.safeDouble())
        put("opacity", e.opacity.safeDouble(1.0))
        put("isVisible", e.isVisible)
        put("isLocked", e.isLocked)
        put("fill", encodePaint(e.fill))
        put("stroke", encodeStroke(e.stroke))
        put("effects", encodeEffects(e.effects))

        when (e) {
            is RectShape -> {
                put("type", "rect")
                put("cornerRadius", e.cornerRadius.safeDouble())
            }
            is EllipseShape -> put("type", "ellipse")
            is PolygonShape -> {
                put("type", "polygon")
                put("sides", e.sides)
            }
            is StarShape -> {
                put("type", "star")
                put("points", e.points)
                put("innerRadiusRatio", e.innerRadiusRatio.safeDouble(0.45))
            }
            is PathShape -> {
                put("type", "path")
                put("isClosed", e.isClosed)
                put("fillEvenOdd", e.fillEvenOdd)
                put("isBrushStroke", e.isBrushStroke)
                put("brushPreset", e.brushPreset.name)
                put("brushTaperEnds", e.brushTaperEnds)
                put("nodes", encodeNodesArray(e.nodes))
                val contoursArr = JSONArray()
                e.extraContours.forEach { c ->
                    contoursArr.put(JSONObject().apply {
                        put("id", c.id)
                        put("isClosed", c.isClosed)
                        put("nodes", encodeNodesArray(c.nodes))
                    })
                }
                put("extraContours", contoursArr)
                val pArr = JSONArray()
                e.pressureWidths.forEach { pArr.put(it.safeDouble(1.0)) }
                put("pressureWidths", pArr)
            }
            is TextShape -> {
                put("type", "text")
                put("text", e.text)
                put("fontFamilyKey", e.fontFamilyKey)
                put("fontSize", e.fontSize.safeDouble(48.0))
                put("isBold", e.isBold)
                put("isItalic", e.isItalic)
                put("alignment", e.alignment.name)
                put("letterSpacing", e.letterSpacing.safeDouble())
                put("lineHeightMultiplier", e.lineHeightMultiplier.safeDouble(1.2))
                put("isUnderline", e.isUnderline)
                put("isStrikethrough", e.isStrikethrough)
                put("textTransform", e.textTransform.name)
                put("curveRadius", e.curveRadius.safeDouble())
                put("backgroundBadgeEnabled", e.backgroundBadgeEnabled)
                put("backgroundBadgeColorArgb", e.backgroundBadgeColorArgb)
                put("backgroundPaddingPx", e.backgroundPaddingPx.safeDouble(16.0))
            }
            is ImageShape -> {
                put("type", "image")
                put("imagePath", e.imagePath)
            }
            is GroupShape -> {
                put("type", "group")
                put("hasClippingMask", e.hasClippingMask)
                put("showMaskOutline", e.showMaskOutline)
                val cArr = JSONArray()
                e.children.forEach { cArr.put(encodeElement(it)) }
                put("children", cArr)
            }
        }
    }

    private fun decodeElement(obj: JSONObject): VectorElement? {
        val id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
        val name = obj.optString("name", "Objeto")
        val x = obj.optDouble("x", 0.0).toFloat()
        val y = obj.optDouble("y", 0.0).toFloat()
        val w = obj.optDouble("width", 100.0).toFloat()
        val h = obj.optDouble("height", 100.0).toFloat()
        val rot = obj.optDouble("rotationDeg", 0.0).toFloat()
        val op = obj.optDouble("opacity", 1.0).toFloat()
        val vis = obj.optBoolean("isVisible", true)
        val locked = obj.optBoolean("isLocked", false)
        val fill = decodePaint(obj.optJSONObject("fill"))
        val stroke = decodeStroke(obj.optJSONObject("stroke"))
        val effects = decodeEffects(obj.optJSONObject("effects"))

        return when (obj.optString("type")) {
            "rect" -> RectShape(
                id, name, x, y, w, h, rot, op, vis, locked, fill, stroke, effects,
                cornerRadius = obj.optDouble("cornerRadius", 0.0).toFloat()
            )
            "ellipse" -> EllipseShape(id, name, x, y, w, h, rot, op, vis, locked, fill, stroke, effects)
            "polygon" -> PolygonShape(
                id, name, x, y, w, h, rot, op, vis, locked, fill, stroke, effects,
                sides = obj.optInt("sides", 6).coerceIn(3, 24)
            )
            "star" -> StarShape(
                id, name, x, y, w, h, rot, op, vis, locked, fill, stroke, effects,
                points = obj.optInt("points", 5).coerceIn(3, 24),
                innerRadiusRatio = obj.optDouble("innerRadiusRatio", 0.45).toFloat()
            )
            "path" -> {
                val nodes = decodeNodesArray(obj.optJSONArray("nodes") ?: JSONArray())
                val cArr = obj.optJSONArray("extraContours") ?: JSONArray()
                val extraContours = buildList {
                    for (i in 0 until cArr.length()) {
                        val c = cArr.getJSONObject(i)
                        add(
                            PathContour(
                                id = c.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                                nodes = decodeNodesArray(c.optJSONArray("nodes") ?: JSONArray()),
                                isClosed = c.optBoolean("isClosed", true)
                            )
                        )
                    }
                }
                val pArr = obj.optJSONArray("pressureWidths") ?: JSONArray()
                val pressures = buildList {
                    for (i in 0 until pArr.length()) {
                        add(pArr.optDouble(i, 1.0).toFloat())
                    }
                }
                PathShape(
                    id, name, x, y, w, h, rot, op, vis, locked, fill, stroke, effects,
                    nodes = nodes,
                    isClosed = obj.optBoolean("isClosed", false),
                    extraContours = extraContours,
                    fillEvenOdd = obj.optBoolean("fillEvenOdd", true),
                    isBrushStroke = obj.optBoolean("isBrushStroke", false),
                    pressureWidths = pressures,
                    brushPreset = runCatching {
                        BrushPreset.valueOf(obj.optString("brushPreset"))
                    }.getOrDefault(BrushPreset.ROUND_CALLIGRAPHIC),
                    brushTaperEnds = obj.optBoolean("brushTaperEnds", true)
                )
            }
            "text" -> TextShape(
                id, name, x, y, w, h, rot, op, vis, locked, fill, stroke, effects,
                text = obj.optString("text", "Texto"),
                fontFamilyKey = obj.optString("fontFamilyKey", "space_grotesk"),
                fontSize = obj.optDouble("fontSize", 48.0).toFloat(),
                isBold = obj.optBoolean("isBold", false),
                isItalic = obj.optBoolean("isItalic", false),
                alignment = runCatching {
                    TextAlignMode.valueOf(obj.optString("alignment"))
                }.getOrDefault(TextAlignMode.LEFT),
                letterSpacing = obj.optDouble("letterSpacing", 0.0).toFloat(),
                lineHeightMultiplier = obj.optDouble("lineHeightMultiplier", 1.2).toFloat(),
                isUnderline = obj.optBoolean("isUnderline", false),
                isStrikethrough = obj.optBoolean("isStrikethrough", false),
                textTransform = runCatching {
                    TextCaseMode.valueOf(obj.optString("textTransform"))
                }.getOrDefault(TextCaseMode.NONE),
                curveRadius = obj.optDouble("curveRadius", 0.0).toFloat(),
                backgroundBadgeEnabled = obj.optBoolean("backgroundBadgeEnabled", false),
                backgroundBadgeColorArgb = obj.optLong("backgroundBadgeColorArgb", 0xCC1E1E2A),
                backgroundPaddingPx = obj.optDouble("backgroundPaddingPx", 16.0).toFloat()
            )
            "image" -> ImageShape(
                id, name, x, y, w, h, rot, op, vis, locked, fill, stroke, effects,
                imagePath = obj.optString("imagePath", "")
            )
            "group" -> {
                val cArr = obj.optJSONArray("children") ?: JSONArray()
                val children = buildList {
                    for (i in 0 until cArr.length()) {
                        decodeElement(cArr.getJSONObject(i))?.let { add(it) }
                    }
                }
                GroupShape(
                    id, name, x, y, w, h, rot, op, vis, locked, fill, stroke, effects, children,
                    hasClippingMask = obj.optBoolean("hasClippingMask", false),
                    showMaskOutline = obj.optBoolean("showMaskOutline", false)
                )
            }
            else -> null
        }
    }

    private fun encodePaint(p: PaintStyle): JSONObject = JSONObject().apply {
        put("type", p.type.name)
        put("colorArgb", p.colorArgb)
        put("secondaryColorArgb", p.secondaryColorArgb)
        put("gradientAngleDeg", p.gradientAngleDeg.safeDouble(45.0))
        put("opacity", p.opacity.safeDouble(1.0))
        put("gradientCenterX", p.gradientCenterX.safeDouble(0.5))
        put("gradientCenterY", p.gradientCenterY.safeDouble(0.5))
        put("radialRadiusFactor", p.radialRadiusFactor.safeDouble(0.55))
        val stopsArr = JSONArray()
        p.stops.forEach { s ->
            stopsArr.put(JSONObject().apply {
                put("offset", s.offset.safeDouble())
                put("colorArgb", s.colorArgb)
            })
        }
        put("stops", stopsArr)
    }

    private fun decodePaint(obj: JSONObject?): PaintStyle {
        if (obj == null) return PaintStyle()
        val stopsArr = obj.optJSONArray("stops") ?: JSONArray()
        val stops = buildList {
            for (i in 0 until stopsArr.length()) {
                val s = stopsArr.getJSONObject(i)
                add(
                    GradientStop(
                        offset = s.optDouble("offset", 0.0).toFloat().coerceIn(0f, 1f),
                        colorArgb = s.optLong("colorArgb", 0xFF8B5CF6)
                    )
                )
            }
        }
        return PaintStyle(
            type = runCatching { PaintType.valueOf(obj.optString("type")) }.getOrDefault(PaintType.SOLID),
            colorArgb = obj.optLong("colorArgb", 0xFF8B5CF6),
            secondaryColorArgb = obj.optLong("secondaryColorArgb", 0xFF38BDF8),
            gradientAngleDeg = obj.optDouble("gradientAngleDeg", 45.0).toFloat(),
            opacity = obj.optDouble("opacity", 1.0).toFloat(),
            stops = stops,
            gradientCenterX = obj.optDouble("gradientCenterX", 0.5).toFloat(),
            gradientCenterY = obj.optDouble("gradientCenterY", 0.5).toFloat(),
            radialRadiusFactor = obj.optDouble("radialRadiusFactor", 0.55).toFloat()
        )
    }

    private fun encodeStroke(s: StrokeStyle): JSONObject = JSONObject().apply {
        put("enabled", s.enabled)
        put("colorArgb", s.colorArgb)
        put("width", s.width.safeDouble(4.0))
        put("opacity", s.opacity.safeDouble(1.0))
        put("cap", s.cap.name)
        put("join", s.join.name)
        put("dashed", s.dashed)
        put("dashLength", s.dashLength.safeDouble(14.0))
        put("gapLength", s.gapLength.safeDouble(10.0))
    }

    private fun decodeStroke(obj: JSONObject?): StrokeStyle {
        if (obj == null) return StrokeStyle()
        return StrokeStyle(
            enabled = obj.optBoolean("enabled", false),
            colorArgb = obj.optLong("colorArgb", 0xFFFFFFFF),
            width = obj.optDouble("width", 4.0).toFloat(),
            opacity = obj.optDouble("opacity", 1.0).toFloat(),
            cap = runCatching { StrokeCapStyle.valueOf(obj.optString("cap")) }.getOrDefault(StrokeCapStyle.ROUND),
            join = runCatching { StrokeJoinStyle.valueOf(obj.optString("join")) }.getOrDefault(StrokeJoinStyle.ROUND),
            dashed = obj.optBoolean("dashed", false),
            dashLength = obj.optDouble("dashLength", 14.0).toFloat(),
            gapLength = obj.optDouble("gapLength", 10.0).toFloat()
        )
    }

    private fun encodeEffects(ef: EffectStyle): JSONObject = JSONObject().apply {
        put("shadowEnabled", ef.shadowEnabled)
        put("shadowColorArgb", ef.shadowColorArgb)
        put("shadowOffsetX", ef.shadowOffsetX.safeDouble())
        put("shadowOffsetY", ef.shadowOffsetY.safeDouble(8.0))
        put("shadowBlur", ef.shadowBlur.safeDouble(16.0))
        put("shadowOpacity", ef.shadowOpacity.safeDouble(0.75))
        put("blurRadius", ef.blurRadius.safeDouble())
        put("glowEnabled", ef.glowEnabled)
        put("glowColorArgb", ef.glowColorArgb)
        put("glowRadius", ef.glowRadius.safeDouble(18.0))
        put("blendMode", ef.blendMode.name)
        if (ef.chrome.enabled || ef.chrome != ChromeEffectConfig()) {
            put("chrome", encodeChromeEffectObj(ef.chrome))
        }
    }

    fun encodeChromeConfigJson(c: ChromeEffectConfig): String = encodeChromeEffectObj(c).toString()

    fun decodeChromeConfigJson(json: String): ChromeEffectConfig =
        runCatching { decodeChromeEffectObj(JSONObject(json)) }.getOrDefault(ChromeEffectConfig())

    private fun encodeChromeEffectObj(c: ChromeEffectConfig): JSONObject = JSONObject().apply {
        put("enabled", c.enabled)
        put("styleType", c.styleType.name)
        put("reflectionIntensity", c.reflectionIntensity.safeDouble(0.88))
        put("brightness", c.brightness.safeDouble(0.08))
        put("contrast", c.contrast.safeDouble(1.35))
        put("depth", c.depth.safeDouble(0.35))
        put("bevelRelief", c.bevelRelief.safeDouble(0.70))
        put("lightAngleDeg", c.lightAngleDeg.safeDouble(315.0))
        put("metallicColorArgb", c.metallicColorArgb)
        put("reflectiveGradientBias", c.reflectiveGradientBias.safeDouble(0.60))
        put("shadowIntensity", c.shadowIntensity.safeDouble(0.60))
        put("overallIntensity", c.overallIntensity.safeDouble(1.0))
    }

    private fun decodeChromeEffectObj(obj: JSONObject?): ChromeEffectConfig {
        if (obj == null) return ChromeEffectConfig()
        return ChromeEffectConfig(
            enabled = obj.optBoolean("enabled", false),
            styleType = runCatching {
                ChromeStyleType.valueOf(obj.optString("styleType"))
            }.getOrDefault(ChromeStyleType.CLASSIC_CHROME),
            reflectionIntensity = obj.optDouble("reflectionIntensity", 0.88).toFloat().coerceIn(0f, 1f),
            brightness = obj.optDouble("brightness", 0.08).toFloat().coerceIn(-0.6f, 0.6f),
            contrast = obj.optDouble("contrast", 1.35).toFloat().coerceIn(0.4f, 2.5f),
            depth = obj.optDouble("depth", 0.35).toFloat().coerceIn(0f, 1f),
            bevelRelief = obj.optDouble("bevelRelief", 0.70).toFloat().coerceIn(0f, 1f),
            lightAngleDeg = obj.optDouble("lightAngleDeg", 315.0).toFloat(),
            metallicColorArgb = obj.optLong("metallicColorArgb", 0xFFE2E8F0),
            reflectiveGradientBias = obj.optDouble("reflectiveGradientBias", 0.60).toFloat().coerceIn(0f, 1f),
            shadowIntensity = obj.optDouble("shadowIntensity", 0.60).toFloat().coerceIn(0f, 1f),
            overallIntensity = obj.optDouble("overallIntensity", 1.0).toFloat().coerceIn(0f, 1f)
        )
    }

    private fun encodeNodesArray(nodes: List<BezierNode>): JSONArray = JSONArray().apply {
        nodes.forEach { n ->
            put(JSONObject().apply {
                put("id", n.id)
                put("x", n.x.safeDouble())
                put("y", n.y.safeDouble())
                put("cp1X", n.cp1X.safeDouble())
                put("cp1Y", n.cp1Y.safeDouble())
                put("cp2X", n.cp2X.safeDouble())
                put("cp2Y", n.cp2Y.safeDouble())
                put("smooth", n.smooth)
                put("handleMode", n.handleMode.name)
            })
        }
    }

    private fun decodeNodesArray(nArr: JSONArray): List<BezierNode> = buildList {
        for (i in 0 until nArr.length()) {
            val n = nArr.getJSONObject(i)
            val nx = n.optDouble("x", 0.0).toFloat()
            val ny = n.optDouble("y", 0.0).toFloat()
            val smooth = n.optBoolean("smooth", false)
            val mode = runCatching {
                NodeHandleMode.valueOf(n.optString("handleMode"))
            }.getOrDefault(if (smooth) NodeHandleMode.SMOOTH else NodeHandleMode.CORNER)
            add(
                BezierNode(
                    id = n.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                    x = nx,
                    y = ny,
                    cp1X = n.optDouble("cp1X", nx.toDouble()).toFloat(),
                    cp1Y = n.optDouble("cp1Y", ny.toDouble()).toFloat(),
                    cp2X = n.optDouble("cp2X", nx.toDouble()).toFloat(),
                    cp2Y = n.optDouble("cp2Y", ny.toDouble()).toFloat(),
                    smooth = mode != NodeHandleMode.CORNER,
                    handleMode = mode
                )
            )
        }
    }

    private fun decodeEffects(obj: JSONObject?): EffectStyle {
        if (obj == null) return EffectStyle()
        return EffectStyle(
            shadowEnabled = obj.optBoolean("shadowEnabled", false),
            shadowColorArgb = obj.optLong("shadowColorArgb", 0xAA000000),
            shadowOffsetX = obj.optDouble("shadowOffsetX", 0.0).toFloat(),
            shadowOffsetY = obj.optDouble("shadowOffsetY", 8.0).toFloat(),
            shadowBlur = obj.optDouble("shadowBlur", 16.0).toFloat(),
            shadowOpacity = obj.optDouble("shadowOpacity", 0.75).toFloat(),
            blurRadius = obj.optDouble("blurRadius", 0.0).toFloat(),
            glowEnabled = obj.optBoolean("glowEnabled", false),
            glowColorArgb = obj.optLong("glowColorArgb", 0xFF8B5CF6),
            glowRadius = obj.optDouble("glowRadius", 18.0).toFloat(),
            blendMode = runCatching {
                BlendModeStyle.valueOf(obj.optString("blendMode"))
            }.getOrDefault(BlendModeStyle.NORMAL),
            chrome = decodeChromeEffectObj(obj.optJSONObject("chrome"))
        )
    }
}
