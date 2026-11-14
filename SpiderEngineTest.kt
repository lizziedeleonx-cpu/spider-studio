package com.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.SpiderJsonCodec
import com.example.engine.AndroidVectorRenderer
import com.example.engine.ExportManager
import com.example.engine.ExportValidator
import com.example.engine.SvgExporter
import com.example.engine.SvgImporter
import com.example.engine.VectorBooleanEngine
import com.example.engine.VectorMath
import com.example.model.BezierNode
import com.example.model.BlendModeStyle
import com.example.model.BooleanOp
import com.example.model.BrushPreset
import com.example.model.DocumentBackgroundMode
import com.example.model.EffectStyle
import com.example.model.EllipseShape
import com.example.model.ExportFormat
import com.example.model.ExportScope
import com.example.model.GradientStop
import com.example.model.GroupShape
import com.example.model.NodeHandleMode
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.StrokeStyle
import com.example.model.TemplateCatalog
import com.example.model.TextCaseMode
import com.example.model.TextShape
import com.example.model.VectorLayer
import kotlinx.coroutines.test.runTest
import androidx.core.content.FileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpiderEngineTest {

    @Before
    fun resetFileProviderCache() {
        runCatching {
            val field = FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    @Test
    fun `spider json codec roundtrips layers bezier nodes and spanish characters`() {
        val templateDoc = TemplateCatalog.templates.first().buildDocument()
        val extraLayer = templateDoc.layers.first().copy(
            elements = templateDoc.layers.first().elements + listOf(
                PathShape(
                    name = "Curva Bézier",
                    x = 10f, y = 20f, width = 100f, height = 80f,
                    nodes = listOf(
                        BezierNode(x = 10f, y = 20f, cp2X = 40f, cp2Y = 10f, smooth = true),
                        BezierNode(x = 110f, y = 100f, cp1X = 80f, cp1Y = 90f, smooth = true)
                    ),
                    isClosed = false
                ),
                TextShape(
                    name = "Texto Español",
                    x = 50f, y = 50f, width = 300f, height = 60f,
                    text = "¡Diseño gráfico en España! ñáéíóúü"
                )
            )
        )
        val doc = templateDoc.copy(layers = listOf(extraLayer) + templateDoc.layers.drop(1))

        val json = SpiderJsonCodec.encode(doc)
        val decoded = SpiderJsonCodec.decode(json)

        assertEquals(doc.id, decoded.id)
        assertEquals(doc.widthPx, decoded.widthPx)
        assertEquals(doc.layers.size, decoded.layers.size)
        val textEl = decoded.layers.first().elements.filterIsInstance<TextShape>().first()
        assertEquals("¡Diseño gráfico en España! ñáéíóúü", textEl.text)
    }

    @Test
    fun `svg export and import produces valid editable vectors`() {
        val doc = TemplateCatalog.templates[1].buildDocument()
        val svg = SvgExporter.exportToSvg(doc)
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("</svg>"))

        val parsed = SvgImporter.parseSvg(svg)
        assertTrue(parsed.elements.isNotEmpty())
        assertEquals(doc.widthPx, parsed.widthPx)
        assertEquals(doc.heightPx, parsed.heightPx)
    }

    @Test
    fun `export manager writes valid png jpg svg and pdf files`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val doc = TemplateCatalog.templates.first().buildDocument()

        val png = ExportManager.exportToCacheAndGetShareUri(
            context, doc, ExportFormat.PNG, ExportScope.FULL_PAGE, emptySet(), 0.5f, 90, true, emptyMap()
        )
        val pngBytes = png.cacheFile.readBytes()
        assertTrue(pngBytes.size > 100)
        assertEquals(0x89.toByte(), pngBytes[0])

        val pdf = ExportManager.exportToCacheAndGetShareUri(
            context, doc, ExportFormat.PDF, ExportScope.FULL_PAGE, emptySet(), 0.5f, 90, false, emptyMap()
        )
        val pdfHeader = pdf.cacheFile.readBytes().take(4).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("%PDF", pdfHeader)
    }

    @Test
    fun `vector math hit test and snapping work accurately`() {
        val doc = TemplateCatalog.templates.first().buildDocument()
        val rect = RectShape(x = 100f, y = 100f, width = 200f, height = 100f)
        assertTrue(VectorMath.hitTestElement(rect, 150f, 150f))

        val snap = VectorMath.computeSnap(
            targetX = 537f, targetY = 100f, targetW = 100f, targetH = 100f,
            doc = doc, excludeIds = emptySet(), gridSizePx = 40, snapEnabled = true, threshold = 10f
        )
        // Center of 1080 is 540, rect width 100 -> center at 540 means x=490, or left edge at 540
        assertEquals(540f, snap.x, 0.01f)
    }

    @Test
    fun `elementToPath converts primitives to editable bezier paths`() {
        val rect = RectShape(x = 20f, y = 40f, width = 120f, height = 80f, cornerRadius = 16f)
        val rectPath = VectorBooleanEngine.elementToPath(rect)
        assertNotNull(rectPath)
        assertEquals(8, rectPath!!.nodes.size)
        assertTrue(rectPath.isClosed)

        val ellipse = EllipseShape(x = 0f, y = 0f, width = 200f, height = 100f)
        val ellipsePath = VectorBooleanEngine.elementToPath(ellipse)
        assertNotNull(ellipsePath)
        assertEquals(4, ellipsePath!!.nodes.size)
        assertTrue(ellipsePath.nodes.all { it.handleMode == NodeHandleMode.SYMMETRIC })

        val polygon = PolygonShape(x = 10f, y = 10f, width = 100f, height = 100f, sides = 5)
        val polyPath = VectorBooleanEngine.elementToPath(polygon)
        assertEquals(5, polyPath!!.nodes.size)

        val star = StarShape(x = 10f, y = 10f, width = 100f, height = 100f, points = 6)
        val starPath = VectorBooleanEngine.elementToPath(star)
        assertEquals(12, starPath!!.nodes.size)
    }

    @Test
    fun `de casteljau cubic split and node insertion preserve curve geometry`() {
        val a = BezierNode(x = 0f, y = 0f, cp2X = 0f, cp2Y = 100f, smooth = true, handleMode = NodeHandleMode.SMOOTH)
        val b = BezierNode(x = 200f, y = 0f, cp1X = 200f, cp1Y = 100f, smooth = true, handleMode = NodeHandleMode.SMOOTH)
        val path = PathShape(
            x = 0f, y = 0f, width = 200f, height = 100f,
            nodes = listOf(a, b),
            isClosed = false
        )

        val expectedMid = VectorMath.evalCubic(a, b, 0.5f)
        val (splitPath, insertedIdx) = VectorBooleanEngine.insertNodeAtSegment(path, 0, 0.5f)
        assertEquals(1, insertedIdx)
        assertEquals(3, splitPath.nodes.size)
        assertEquals(expectedMid.x, splitPath.nodes[1].x, 0.05f)
        assertEquals(expectedMid.y, splitPath.nodes[1].y, 0.05f)

        // Check left sub-curve at t=0.5 matches original curve at t=0.25
        val origQuarter = VectorMath.evalCubic(a, b, 0.25f)
        val leftHalf = VectorMath.evalCubic(splitPath.nodes[0], splitPath.nodes[1], 0.5f)
        assertEquals(origQuarter.x, leftHalf.x, 0.05f)
        assertEquals(origQuarter.y, leftHalf.y, 0.05f)
    }

    @Test
    fun `boolean operations union subtract intersect xor produce editable compound paths and valid svg`() {
        val outer = RectShape(x = 100f, y = 100f, width = 200f, height = 200f)
        val inner = RectShape(x = 150f, y = 150f, width = 100f, height = 100f)
        val right = RectShape(x = 200f, y = 100f, width = 200f, height = 200f)

        val union = VectorBooleanEngine.applyBooleanOperation(listOf(outer, right), BooleanOp.UNION)
        assertNotNull(union)
        assertEquals(100f, union!!.x, 1f)
        assertEquals(300f, union.width, 1f)
        assertEquals(200f, union.height, 1f)

        val intersect = VectorBooleanEngine.applyBooleanOperation(listOf(outer, right), BooleanOp.INTERSECT)
        assertNotNull(intersect)
        assertEquals(200f, intersect!!.x, 1f)
        assertEquals(100f, intersect.width, 1f)
        assertEquals(200f, intersect.height, 1f)

        // Subtracting inner square from outer square creates a compound path with a hole
        val subtractedHole = VectorBooleanEngine.applyBooleanOperation(listOf(outer, inner), BooleanOp.SUBTRACT)
        assertNotNull(subtractedHole)
        assertTrue(subtractedHole!!.extraContours.isNotEmpty())
        assertTrue(subtractedHole.fillEvenOdd)

        val xor = VectorBooleanEngine.applyBooleanOperation(listOf(outer, right), BooleanOp.XOR)
        assertNotNull(xor)
        assertTrue(xor!!.extraContours.isNotEmpty())

        // Break apart restores independent PathShapes
        val separated = VectorBooleanEngine.breakApartContours(subtractedHole)
        assertEquals(2, separated.size)

        // JSON codec and SVG exporter preserve compound contour & evenodd rule
        val doc = SpiderDocument(
            name = "Prueba Booleana",
            layers = listOf(VectorLayer(elements = listOf(subtractedHole)))
        )
        val decoded = SpiderJsonCodec.decode(SpiderJsonCodec.encode(doc))
        val decodedPath = decoded.layers.first().elements.first() as PathShape
        assertEquals(1, decodedPath.extraContours.size)
        assertTrue(decodedPath.fillEvenOdd)

        val svg = SvgExporter.exportToSvg(doc)
        assertTrue(svg.contains("fill-rule=\"evenodd\""))
    }

    @Test
    fun `phase 3 json codec roundtrips sweep gradients stops blend modes glow brush presets masks and curved text`() {
        val gradRect = RectShape(
            x = 40f, y = 40f, width = 300f, height = 200f,
            fill = PaintStyle(
                type = PaintType.SWEEP_GRADIENT,
                colorArgb = 0xFF8B5CF6,
                secondaryColorArgb = 0xFF22D3EE,
                stops = listOf(
                    GradientStop(0f, 0xFF8B5CF6),
                    GradientStop(0.5f, 0xFFEC4899),
                    GradientStop(1f, 0xFF22D3EE)
                ),
                gradientCenterX = 0.4f,
                gradientCenterY = 0.6f,
                radialRadiusFactor = 0.75f
            ),
            effects = EffectStyle(
                shadowEnabled = true,
                shadowOffsetX = 6f,
                shadowOffsetY = 10f,
                shadowBlur = 18f,
                shadowOpacity = 0.65f,
                glowEnabled = true,
                glowColorArgb = 0xFF22D3EE,
                glowRadius = 22f,
                blendMode = BlendModeStyle.MULTIPLY
            )
        )
        val brushPath = PathShape(
            x = 100f, y = 100f, width = 200f, height = 80f,
            nodes = listOf(
                BezierNode(x = 100f, y = 120f),
                BezierNode(x = 200f, y = 140f),
                BezierNode(x = 300f, y = 110f)
            ),
            isBrushStroke = true,
            pressureWidths = listOf(0.5f, 1.3f, 0.7f),
            brushPreset = BrushPreset.INK_PEN,
            brushTaperEnds = true,
            stroke = StrokeStyle(enabled = true, width = 18f)
        )
        val curvedText = TextShape(
            x = 60f, y = 300f, width = 400f, height = 120f,
            text = "diseño español ñ",
            isUnderline = true,
            isStrikethrough = true,
            textTransform = TextCaseMode.UPPERCASE,
            curveRadius = 260f,
            backgroundBadgeEnabled = true,
            backgroundBadgeColorArgb = 0xCC1E1E2A,
            backgroundPaddingPx = 20f
        )
        val maskGroup = GroupShape(
            name = "Máscara Test",
            x = 40f, y = 40f, width = 300f, height = 200f,
            children = listOf(
                EllipseShape(x = 60f, y = 60f, width = 180f, height = 180f),
                gradRect
            ),
            hasClippingMask = true,
            showMaskOutline = true
        )
        val doc = SpiderDocument(
            name = "Fase 3 Completo",
            layers = listOf(VectorLayer(elements = listOf(maskGroup, brushPath, curvedText)))
        )

        val decoded = SpiderJsonCodec.decode(SpiderJsonCodec.encode(doc))
        val decGroup = decoded.layers.first().elements[0] as GroupShape
        assertTrue(decGroup.hasClippingMask)
        assertTrue(decGroup.showMaskOutline)
        val decRect = decGroup.children[1] as RectShape
        assertEquals(PaintType.SWEEP_GRADIENT, decRect.fill.type)
        assertEquals(3, decRect.fill.stops.size)
        assertEquals(BlendModeStyle.MULTIPLY, decRect.effects.blendMode)
        assertTrue(decRect.effects.glowEnabled)
        assertEquals(0.65f, decRect.effects.shadowOpacity, 0.01f)

        val decBrush = decoded.layers.first().elements[1] as PathShape
        assertEquals(BrushPreset.INK_PEN, decBrush.brushPreset)
        assertEquals(3, decBrush.pressureWidths.size)
        assertTrue(decBrush.brushTaperEnds)

        val decText = decoded.layers.first().elements[2] as TextShape
        assertTrue(decText.isUnderline)
        assertTrue(decText.isStrikethrough)
        assertEquals(TextCaseMode.UPPERCASE, decText.textTransform)
        assertEquals(260f, decText.curveRadius, 0.01f)
        assertTrue(decText.backgroundBadgeEnabled)
    }

    @Test
    fun `phase 3 brush ribbon outline text transform and smart spacing snap work accurately`() {
        val brush = PathShape(
            x = 0f, y = 0f, width = 200f, height = 40f,
            nodes = listOf(
                BezierNode(x = 0f, y = 20f),
                BezierNode(x = 100f, y = 10f),
                BezierNode(x = 200f, y = 20f)
            ),
            isBrushStroke = true,
            pressureWidths = listOf(0.5f, 1.4f, 0.6f),
            brushPreset = BrushPreset.ROUND_CALLIGRAPHIC,
            brushTaperEnds = true,
            stroke = StrokeStyle(enabled = true, width = 20f)
        )
        val ribbon = VectorMath.buildBrushRibbonOutline(brush)
        assertTrue(ribbon.size >= 8)

        assertEquals("DISEÑO ESPAÑOL Ñ", VectorMath.applyTextTransform("Diseño español ñ", TextCaseMode.UPPERCASE))
        assertEquals("Diseño Español Ñ", VectorMath.applyTextTransform("diseño español ñ", TextCaseMode.TITLE_CASE))

        // Smart equal-spacing detection: A at 100..200, B at 250..350 (gap = 50), moving C near 400..500
        val elA = RectShape(id = "a", x = 100f, y = 100f, width = 100f, height = 80f)
        val elB = RectShape(id = "b", x = 250f, y = 100f, width = 100f, height = 80f)
        val doc = SpiderDocument(
            widthPx = 1080,
            heightPx = 1080,
            layers = listOf(VectorLayer(elements = listOf(elA, elB)))
        )
        val snap = VectorMath.computeSnap(
            targetX = 403f,
            targetY = 102f,
            targetW = 100f,
            targetH = 80f,
            doc = doc,
            excludeIds = setOf("c"),
            gridSizePx = 40,
            snapEnabled = true,
            threshold = 12f
        )
        assertEquals(400f, snap.x, 0.5f)
        assertTrue(snap.lines.any { it.isSpacingGuide && it.distanceLabel == "50 px" })
    }

    @Test
    fun `phase 3 svg exporter and bitmap renderer support clipping masks blend modes gradients and curved text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val maskGroup = GroupShape(
            x = 50f, y = 50f, width = 300f, height = 300f,
            children = listOf(
                EllipseShape(x = 50f, y = 50f, width = 300f, height = 300f),
                RectShape(
                    x = 50f, y = 50f, width = 300f, height = 300f,
                    fill = PaintStyle(
                        type = PaintType.LINEAR_GRADIENT,
                        stops = listOf(
                            GradientStop(0f, 0xFF8B5CF6),
                            GradientStop(0.5f, 0xFFEC4899),
                            GradientStop(1f, 0xFF22D3EE)
                        )
                    ),
                    effects = EffectStyle(blendMode = BlendModeStyle.OVERLAY, glowEnabled = true)
                )
            ),
            hasClippingMask = true
        )
        val curvedText = TextShape(
            x = 100f, y = 420f, width = 400f, height = 100f,
            text = "Spider Studio",
            curveRadius = 240f,
            isUnderline = true,
            backgroundBadgeEnabled = true
        )
        val doc = SpiderDocument(
            widthPx = 600,
            heightPx = 600,
            layers = listOf(VectorLayer(elements = listOf(maskGroup, curvedText)))
        )

        val svg = SvgExporter.exportToSvg(doc)
        assertTrue(svg.contains("<clipPath"))
        assertTrue(svg.contains("mix-blend-mode:overlay"))
        assertTrue(svg.contains("<textPath"))
        assertTrue(svg.contains("offset=\"50.0%\""))

        val bmp = AndroidVectorRenderer.renderDocumentToBitmap(
            context = context,
            doc = doc,
            targetWidth = 300,
            targetHeight = 300,
            transparentBackground = false
        )
        assertEquals(300, bmp.width)
        assertEquals(300, bmp.height)
    }

    @Test
    fun `phase 4 proportional export scaling preserves aspect ratio when clamping oversized canvases`() {
        val (w, h) = ExportManager.computeProportionalTargetSize(1920f, 1080f, 3f)
        assertEquals(4096, w)
        assertEquals(2304, h)

        val (minW, minH) = ExportManager.computeProportionalTargetSize(16f, 8f, 1f)
        assertEquals(64, minW)
        assertEquals(32, minH)
    }

    @Test
    fun `phase 4 professional export validates svg png jpg pdf and spider with layers vectors colors and dimensions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val layer1 = VectorLayer(
            name = "Fondo Creativo",
            opacity = 0.85f,
            isVisible = true,
            elements = listOf(
                RectShape(
                    name = "Tarjeta Degradado",
                    x = 80f, y = 60f, width = 520f, height = 280f, cornerRadius = 24f,
                    fill = PaintStyle(
                        type = PaintType.LINEAR_GRADIENT,
                        colorArgb = 0xFF8B5CF6,
                        secondaryColorArgb = 0xFF22D3EE,
                        stops = listOf(
                            GradientStop(0f, 0xFF8B5CF6),
                            GradientStop(0.5f, 0xFFEC4899),
                            GradientStop(1f, 0xFF22D3EE)
                        ),
                        gradientAngleDeg = 45f
                    )
                ),
                PolygonShape(
                    name = "Hexágono",
                    x = 680f, y = 80f, width = 200f, height = 200f,
                    sides = 6,
                    fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFFF59E0B),
                    stroke = StrokeStyle(enabled = true, colorArgb = 0xFFFFFFFF, width = 6f)
                )
            )
        )
        val layer2 = VectorLayer(
            name = "Curvas y Texto",
            isVisible = true,
            elements = listOf(
                PathShape(
                    name = "Trazo Bézier",
                    x = 100f, y = 400f, width = 400f, height = 180f,
                    nodes = listOf(
                        BezierNode(x = 100f, y = 500f, cp2X = 220f, cp2Y = 380f, smooth = true),
                        BezierNode(x = 500f, y = 500f, cp1X = 380f, cp1Y = 620f, smooth = true)
                    ),
                    isClosed = false,
                    fill = PaintStyle(type = PaintType.NONE),
                    stroke = StrokeStyle(enabled = true, colorArgb = 0xFF38BDF8, width = 10f)
                ),
                TextShape(
                    name = "Título",
                    x = 120f, y = 420f, width = 600f, height = 90f,
                    text = "Diseño Editorial España ñáéíóú",
                    fontSize = 36f,
                    fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFFFFFFFF)
                )
            )
        )
        val hiddenLayer = VectorLayer(
            name = "Guía Oculta",
            isVisible = false,
            elements = listOf(
                EllipseShape(
                    name = "Marca Oculta",
                    x = 900f, y = 400f, width = 160f, height = 160f,
                    fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFFEF4444)
                )
            )
        )

        val doc = SpiderDocument(
            name = "Identidad Pro 16-9",
            widthPx = 1280,
            heightPx = 720,
            backgroundMode = DocumentBackgroundMode.SOLID,
            backgroundColorArgb = 0xFF1A1030,
            layers = listOf(layer1, layer2, hiddenLayer)
        )

        // 1. Export & validate SVG (DOM XML + roundtrip fidelity)
        val svgRes = ExportManager.exportToCacheAndGetShareUri(
            context, doc, ExportFormat.SVG, ExportScope.FULL_PAGE, emptySet(), 1f, 92, false, emptyMap()
        )
        assertTrue(svgRes.validation.isValid)
        assertEquals(1280, svgRes.validation.width)
        assertEquals(720, svgRes.validation.height)
        assertEquals(3, svgRes.validation.layerCount)

        val svgText = svgRes.cacheFile.readText(Charsets.UTF_8)
        val xmlDoc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(InputSource(StringReader(svgText)))
        assertEquals("svg", xmlDoc.documentElement.localName)
        assertTrue(svgText.contains("inkscape:groupmode=\"layer\""))
        assertTrue(svgText.contains("inkscape:label=\"Guía Oculta\""))
        assertTrue(svgText.contains("style=\"display:none\""))

        val reimported = SvgImporter.importToDocument(svgText, "Reimportado", context)
        assertEquals(1280, reimported.widthPx)
        assertEquals(720, reimported.heightPx)
        assertEquals(DocumentBackgroundMode.SOLID, reimported.backgroundMode)
        assertEquals(0xFF1A1030, reimported.backgroundColorArgb)
        assertEquals(3, reimported.layers.size)
        assertEquals("Fondo Creativo", reimported.layers[0].name)
        assertEquals(0.85f, reimported.layers[0].opacity, 0.02f)
        assertTrue(reimported.layers[0].isVisible)
        assertEquals("Guía Oculta", reimported.layers[2].name)
        assertFalse(reimported.layers[2].isVisible)
        val reimportedText = reimported.layers[1].elements.filterIsInstance<TextShape>().firstOrNull()
        assertNotNull(reimportedText)
        assertEquals("Diseño Editorial España ñáéíóú", reimportedText!!.text)

        // 2. Export & validate PNG (transparent vs solid)
        val transparentDoc = doc.copy(backgroundMode = DocumentBackgroundMode.TRANSPARENT)
        val pngAlphaRes = ExportManager.exportToCacheAndGetShareUri(
            context, transparentDoc, ExportFormat.PNG, ExportScope.FULL_PAGE, emptySet(), 0.5f, 92, true, emptyMap()
        )
        assertTrue(pngAlphaRes.validation.isValid)
        assertTrue(pngAlphaRes.validation.hasTransparency)
        assertEquals(640, pngAlphaRes.validation.width)
        assertEquals(360, pngAlphaRes.validation.height)
        val decodedPng = BitmapFactory.decodeFile(pngAlphaRes.cacheFile.absolutePath)
        assertEquals(640, decodedPng.width)
        assertEquals(360, decodedPng.height)
        decodedPng.recycle()

        // 3. Export & validate JPG
        val jpgRes = ExportManager.exportToCacheAndGetShareUri(
            context, doc, ExportFormat.JPG, ExportScope.FULL_PAGE, emptySet(), 0.5f, 88, false, emptyMap()
        )
        assertTrue(jpgRes.validation.isValid)
        assertFalse(jpgRes.validation.hasTransparency)
        assertEquals(640, jpgRes.validation.width)
        assertEquals(360, jpgRes.validation.height)
        val jpgBytes = jpgRes.cacheFile.readBytes()
        assertEquals(0xFF.toByte(), jpgBytes[0])
        assertEquals(0xD8.toByte(), jpgBytes[1])
        assertEquals(0xFF.toByte(), jpgBytes[jpgBytes.size - 2])
        assertEquals(0xD9.toByte(), jpgBytes[jpgBytes.size - 1])

        // 4. Export & validate PDF
        val pdfRes = ExportManager.exportToCacheAndGetShareUri(
            context, doc, ExportFormat.PDF, ExportScope.FULL_PAGE, emptySet(), 0.5f, 92, false, emptyMap()
        )
        assertTrue(pdfRes.validation.isValid)
        assertEquals(640, pdfRes.validation.width)
        assertEquals(360, pdfRes.validation.height)
        val pdfCheck = ExportValidator.validateExportedFile(context, pdfRes.cacheFile, ExportFormat.PDF)
        assertTrue(pdfCheck.isValid)
    }

    @Test
    fun `phase 4 external illustrator and inkscape svg imports layers compound paths clip masks and inherited colors`() {
        val externalSvg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                 xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape"
                 width="400pt" height="200pt" viewBox="0 0 400 200">
              <defs>
                <linearGradient id="brandGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stop-color="#8B5CF6"/>
                  <stop offset="100%" stop-color="#EC4899"/>
                </linearGradient>
                <clipPath id="circleClip">
                  <circle cx="300" cy="100" r="60"/>
                </clipPath>
              </defs>
              <g inkscape:groupmode="layer" inkscape:label="Arte Principal" fill="rgba(255, 85, 0, 0.8)" stroke="#112233" stroke-width="4">
                <path id="anillo" fill-rule="evenodd" d="M 20 20 L 140 20 C 160 20 160 140 140 140 S 20 140 20 20 Z M 50 50 L 110 50 L 110 110 L 50 110 Z"/>
                <g clip-path="url(#circleClip)" transform="translate(10, 5)">
                  <rect x="240" y="40" width="120" height="120" fill="url(#brandGrad)"/>
                </g>
              </g>
            </svg>
        """.trimIndent()

        val imported = SvgImporter.importToDocument(externalSvg, "Logo Vectorial")
        assertEquals(400, imported.widthPx)
        assertEquals(200, imported.heightPx)
        assertEquals(1, imported.layers.size)
        val mainLayer = imported.layers.first()
        assertEquals("Arte Principal", mainLayer.name)
        // Only 2 elements should exist (defs clip circle must NOT leak onto canvas!)
        assertEquals(2, mainLayer.elements.size)

        val compoundPath = mainLayer.elements[0] as PathShape
        assertTrue(compoundPath.fillEvenOdd)
        assertEquals(1, compoundPath.extraContours.size)
        assertEquals(0xCCFF5500.toInt(), compoundPath.fill.colorArgb.toInt())
        assertTrue(compoundPath.stroke.enabled)
        assertEquals(4f, compoundPath.stroke.width, 0.01f)

        val clippedGroup = mainLayer.elements[1] as GroupShape
        assertTrue(clippedGroup.hasClippingMask)
        assertEquals(2, clippedGroup.children.size)
        val maskedRect = clippedGroup.children[1] as RectShape
        assertEquals(PaintType.LINEAR_GRADIENT, maskedRect.fill.type)
        assertEquals(2, maskedRect.fill.stops.size)
    }
}
