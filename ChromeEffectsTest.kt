package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProjectRepository
import com.example.data.SpiderDatabase
import com.example.data.SpiderJsonCodec
import com.example.engine.AndroidVectorRenderer
import com.example.engine.ChromeEffectRenderer
import com.example.engine.ExportManager
import com.example.engine.SvgExporter
import com.example.model.BezierNode
import com.example.model.ChromeEffectConfig
import com.example.model.ChromeStyleType
import com.example.model.DocumentBackgroundMode
import com.example.model.EffectStyle
import com.example.model.ExportFormat
import com.example.model.ExportScope
import com.example.model.GroupShape
import com.example.model.NodeHandleMode
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathShape
import com.example.model.RectShape
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.StrokeStyle
import com.example.model.TextShape
import com.example.model.VectorLayer
import com.example.viewmodel.EditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChromeEffectsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun beforeEach() {
        clearFileProviderCache()
    }

    @After
    fun afterEach() {
        runCatching {
            val field = SpiderDatabase::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            val db = field.get(null) as? SpiderDatabase
            db?.close()
            field.set(null, null)
        }
        clearFileProviderCache()
    }

    private fun clearFileProviderCache() {
        runCatching {
            val field = FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    private fun waitForCondition(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20L)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condition did not become true within ${timeoutMs}ms", condition())
    }

    private fun ComposeTestRule.waitForTag(tag: String, timeoutMs: Long = 6000L) {
        waitUntil(timeoutMs) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openBrandTemplateInEditor() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("welcome_enter_studio_button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("welcome_enter_studio_button").performClick()
        }
        composeRule.waitForTag("nav_templates")
        composeRule.onNodeWithTag("nav_templates").performClick()
        composeRule.waitForTag("template_card_tpl_spider_brand")
        composeRule.onNodeWithTag("template_card_tpl_spider_brand").performClick()
        composeRule.waitForTag("editor_canvas")
        composeRule.waitForIdle()
    }

    // --- 1. Procedural Gradient & Shader Controls ---

    @Test
    fun `all 6 chrome styles synthesize valid metallic ramps responding to all controls`() {
        ChromeStyleType.entries.forEach { style ->
            val baseConfig = style.defaultConfig()
            assertTrue(baseConfig.enabled)

            val baseRamp = ChromeEffectRenderer.computeChromeRamp(baseConfig, alpha = 1f)
            assertTrue("${style.name} should generate multi-stop metallic ramp", baseRamp.colors.size >= 5)
            assertEquals(baseRamp.colors.size, baseRamp.offsets.size)

            // Offsets must be monotonic non-decreasing in [0, 1]
            for (i in 1 until baseRamp.offsets.size) {
                assertTrue(baseRamp.offsets[i] >= baseRamp.offsets[i - 1])
                assertTrue(baseRamp.offsets[i] in 0f..1f)
            }

            // Varying brightness, metallicColorArgb, and contrast must dynamically alter output
            val brighter = ChromeEffectRenderer.computeChromeRamp(
                baseConfig.copy(brightness = 0.45f, contrast = 1.85f, metallicColorArgb = 0xFF8B5CF6)
            )
            assertFalse(baseRamp.colors.contentEquals(brighter.colors))

            val shiftedBias = ChromeEffectRenderer.computeChromeRamp(
                baseConfig.copy(reflectiveGradientBias = 0.05f, lightAngleDeg = 180f)
            )
            val oppositeBias = ChromeEffectRenderer.computeChromeRamp(
                baseConfig.copy(reflectiveGradientBias = 0.95f, lightAngleDeg = 20f)
            )
            assertFalse(
                shiftedBias.offsets.contentEquals(oppositeBias.offsets) &&
                    shiftedBias.colors.contentEquals(oppositeBias.colors)
            )
        }
    }

    // --- 2. Non-destructive Serialization & Editability ---

    @Test
    fun `json codec preserves chrome config and original editable text and bezier nodes`() {
        val chromeText = ChromeStyleType.GOLD_CHROME.defaultConfig().copy(
            reflectionIntensity = 0.92f,
            brightness = 0.12f,
            contrast = 1.65f,
            depth = 0.72f,
            bevelRelief = 0.85f,
            lightAngleDeg = 135f,
            metallicColorArgb = 0xFFFDE047,
            reflectiveGradientBias = 0.74f,
            shadowIntensity = 0.68f,
            overallIntensity = 0.95f
        )

        val textEl = TextShape(
            id = "t_chrome",
            name = "Logo Título",
            x = 100f,
            y = 120f,
            width = 500f,
            height = 140f,
            text = "Diseño Español ÑÁ",
            fontSize = 72f,
            fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFFF4F4F8),
            effects = EffectStyle(chrome = chromeText)
        )

        val bezierEl = PathShape(
            id = "p_chrome",
            name = "Emblema Bézier",
            x = 120f,
            y = 320f,
            width = 240f,
            height = 240f,
            isClosed = true,
            nodes = listOf(
                BezierNode(x = 120f, y = 320f, cp2X = 180f, cp2Y = 300f, handleMode = NodeHandleMode.SMOOTH, smooth = true),
                BezierNode(x = 360f, y = 400f, cp1X = 300f, cp1Y = 320f, cp2X = 360f, cp2Y = 480f),
                BezierNode(x = 200f, y = 560f)
            ),
            fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFF8B5CF6),
            effects = EffectStyle(chrome = ChromeStyleType.HOLOGRAPHIC_CHROME.defaultConfig())
        )

        val logoGroup = GroupShape(
            id = "g_logo",
            name = "Logo Corporativo",
            x = 80f,
            y = 600f,
            width = 300f,
            height = 200f,
            children = listOf(
                StarShape(x = 80f, y = 600f, width = 120f, height = 120f),
                RectShape(x = 220f, y = 620f, width = 140f, height = 140f, cornerRadius = 18f)
            ),
            effects = EffectStyle(chrome = ChromeStyleType.THREE_D_CHROME.defaultConfig())
        )

        val doc = SpiderDocument(
            name = "Chrome Roundtrip",
            layers = listOf(VectorLayer(elements = listOf(textEl, bezierEl, logoGroup)))
        )

        val json = SpiderJsonCodec.encode(doc)
        val decoded = SpiderJsonCodec.decode(json)
        val decodedElems = decoded.layers.first().elements

        val decText = decodedElems[0] as TextShape
        assertTrue(decText.effects.chrome.enabled)
        assertEquals(ChromeStyleType.GOLD_CHROME, decText.effects.chrome.styleType)
        assertEquals("Diseño Español ÑÁ", decText.text)
        assertEquals(0.72f, decText.effects.chrome.depth, 0.001f)
        assertEquals(0.85f, decText.effects.chrome.bevelRelief, 0.001f)
        assertEquals(135f, decText.effects.chrome.lightAngleDeg, 0.01f)
        assertEquals(0xFFFDE047, decText.effects.chrome.metallicColorArgb)
        assertEquals(0.74f, decText.effects.chrome.reflectiveGradientBias, 0.001f)
        assertEquals(0.68f, decText.effects.chrome.shadowIntensity, 0.001f)
        assertEquals(0.95f, decText.effects.chrome.overallIntensity, 0.001f)

        val decPath = decodedElems[1] as PathShape
        assertTrue(decPath.effects.chrome.enabled)
        assertEquals(ChromeStyleType.HOLOGRAPHIC_CHROME, decPath.effects.chrome.styleType)
        assertEquals(3, decPath.nodes.size)
        assertEquals(180f, decPath.nodes[0].cp2X, 0.01f)

        val decGroup = decodedElems[2] as GroupShape
        assertTrue(decGroup.effects.chrome.enabled)
        assertEquals(ChromeStyleType.THREE_D_CHROME, decGroup.effects.chrome.styleType)
        assertEquals(2, decGroup.children.size)

        // Effective chrome propagates non-destructively to children
        val childEffective = ChromeEffectRenderer.effectiveChrome(decGroup.children[0], decGroup.effects.chrome)
        assertEquals(ChromeStyleType.THREE_D_CHROME, childEffective?.styleType)
    }

    // --- 3. EditorViewModel Workflow + Non-Destructive Text/Node Editing + Undo/Redo ---

    @Test
    fun `viewmodel applies chrome presets updates controls non-destructively and supports undo redo`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ProjectRepository(app, SpiderDatabase.getInstance(app))

        val textEl = TextShape(
            id = "tx_1",
            x = 120f,
            y = 140f,
            width = 400f,
            height = 100f,
            text = "Spider Brand",
            fill = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFF8B5CF6)
        )
        val pathEl = PathShape(
            id = "pa_1",
            x = 120f,
            y = 300f,
            width = 200f,
            height = 200f,
            isClosed = true,
            nodes = listOf(
                BezierNode(x = 120f, y = 300f),
                BezierNode(x = 320f, y = 300f),
                BezierNode(x = 220f, y = 500f)
            )
        )
        val doc = SpiderDocument(
            id = "chrome_vm_doc",
            name = "VM Chrome",
            layers = listOf(VectorLayer(id = "l1", elements = listOf(textEl, pathEl)))
        )
        runBlocking { repo.saveDocument(doc) }

        val vm = EditorViewModel(app)
        vm.loadProject(doc.id)
        waitForCondition { vm.uiState.value.document != null }

        vm.selectElementById("l1", "tx_1")
        vm.applyChromePreset(ChromeStyleType.LIQUID_CHROME)

        var currentText = vm.getSelectedElements().first() as TextShape
        assertTrue(currentText.effects.chrome.enabled)
        assertEquals(ChromeStyleType.LIQUID_CHROME, currentText.effects.chrome.styleType)
        assertEquals("Spider Brand", currentText.text)
        assertEquals(0xFF8B5CF6, currentText.fill.colorArgb)

        // Update brightness & edit text while Chrome remains active and intact
        vm.updateChromeEffect(recordUndo = true) { it.copy(depth = 0.88f, bevelRelief = 0.91f) }
        vm.updateSelectedElements(recordUndo = true) { el ->
            if (el is TextShape) el.copy(text = "Chrome Editado") else el
        }
        currentText = vm.getSelectedElements().first() as TextShape
        assertEquals("Chrome Editado", currentText.text)
        assertEquals(0.88f, currentText.effects.chrome.depth, 0.001f)

        // Disable Chrome non-destructively: original text & original fill remain intact
        vm.disableChromeEffect()
        currentText = vm.getSelectedElements().first() as TextShape
        assertFalse(currentText.effects.chrome.enabled)
        assertEquals("Chrome Editado", currentText.text)
        assertEquals(0xFF8B5CF6, currentText.fill.colorArgb)

        // Undo brings Chrome back
        vm.undo()
        vm.selectElementById("l1", "tx_1")
        currentText = vm.getSelectedElements().first() as TextShape
        assertTrue(currentText.effects.chrome.enabled)
        assertEquals(ChromeStyleType.LIQUID_CHROME, currentText.effects.chrome.styleType)

        // Apply Chrome to PathShape and still add a midpoint node safely
        vm.selectElementById("l1", "pa_1")
        vm.applyChromePreset(ChromeStyleType.ROSE_GOLD_CHROME)
        vm.selectNodeForTest(0)
        vm.addMidpointNodeToPath()
        val updatedPath = vm.getSelectedElements().first() as PathShape
        assertTrue(updatedPath.effects.chrome.enabled)
        assertEquals(ChromeStyleType.ROSE_GOLD_CHROME, updatedPath.effects.chrome.styleType)
        assertEquals(4, updatedPath.nodes.size)
    }

    // Helper extension to select node index when testing PathShape editability alongside Chrome
    private fun EditorViewModel.selectNodeForTest(index: Int) {
        val stateField = EditorViewModel::class.java.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = stateField.get(this) as kotlinx.coroutines.flow.MutableStateFlow<com.example.viewmodel.EditorUiState>
        flow.value = flow.value.copy(activeNodeIndex = index, activeContourIndex = 0)
    }

    // --- 4. Custom Styles Persistence in Room ---

    @Test
    fun `repository saves loads and deletes custom chrome styles`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ProjectRepository(app, SpiderDatabase.getInstance(app))

        val customConfig = ChromeStyleType.HOLOGRAPHIC_CHROME.defaultConfig().copy(
            metallicColorArgb = 0xFF8B5CF6,
            depth = 0.77f,
            lightAngleDeg = 210f
        )
        val saved = repo.saveChromeStyle("Holo Violeta", customConfig)
        val listAfterSave = repo.chromeStylesFlow.first()
        assertTrue(listAfterSave.any { it.id == saved.id && it.name == "Holo Violeta" })

        val decoded = SpiderJsonCodec.decodeChromeConfigJson(
            listAfterSave.first { it.id == saved.id }.configJson
        )
        assertEquals(ChromeStyleType.HOLOGRAPHIC_CHROME, decoded.styleType)
        assertEquals(0xFF8B5CF6, decoded.metallicColorArgb)
        assertEquals(0.77f, decoded.depth, 0.001f)
        assertEquals(210f, decoded.lightAngleDeg, 0.01f)

        repo.deleteChromeStyle(saved.id)
        val listAfterDelete = repo.chromeStylesFlow.first()
        assertFalse(listAfterDelete.any { it.id == saved.id })
    }

    // --- 5. Real Rendering & PNG / SVG / PDF Export Verification ---

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `chrome effects render to bitmap and export validly to PNG SVG and PDF`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val chromeDoc = SpiderDocument(
            id = "chrome_export_doc",
            name = "Chrome Export Test",
            widthPx = 600,
            heightPx = 400,
            backgroundMode = DocumentBackgroundMode.TRANSPARENT,
            layers = listOf(
                VectorLayer(
                    elements = listOf(
                        TextShape(
                            x = 60f,
                            y = 40f,
                            width = 480f,
                            height = 110f,
                            text = "SPIDER 3D",
                            fontSize = 68f,
                            stroke = StrokeStyle(enabled = true, colorArgb = 0xFFFFFFFF, width = 2f),
                            effects = EffectStyle(chrome = ChromeStyleType.THREE_D_CHROME.defaultConfig())
                        ),
                        GroupShape(
                            name = "Logo Cromo",
                            x = 60f,
                            y = 180f,
                            width = 200f,
                            height = 160f,
                            children = listOf(
                                StarShape(x = 60f, y = 180f, width = 140f, height = 140f),
                                RectShape(x = 220f, y = 200f, width = 120f, height = 120f, cornerRadius = 24f)
                            ),
                            effects = EffectStyle(chrome = ChromeStyleType.LIQUID_CHROME.defaultConfig())
                        ),
                        PathShape(
                            name = "Curva Oro",
                            x = 360f,
                            y = 190f,
                            width = 180f,
                            height = 140f,
                            isClosed = true,
                            nodes = listOf(
                                BezierNode(x = 360f, y = 190f, cp2X = 430f, cp2Y = 170f),
                                BezierNode(x = 540f, y = 260f, cp1X = 500f, cp1Y = 200f),
                                BezierNode(x = 400f, y = 330f)
                            ),
                            effects = EffectStyle(chrome = ChromeStyleType.GOLD_CHROME.defaultConfig())
                        )
                    )
                )
            )
        )

        // AndroidVectorRenderer bitmap check (must draw non-transparent chrome pixels)
        val renderedBmp = AndroidVectorRenderer.renderDocumentToBitmap(
            context = app,
            doc = chromeDoc,
            targetWidth = 600,
            targetHeight = 400,
            transparentBackground = true,
            customFontPaths = emptyMap()
        )
        assertEquals(600, renderedBmp.width)
        assertEquals(400, renderedBmp.height)
        var nonEmptyPixels = 0
        for (px in 0 until 600 step 10) {
            for (py in 0 until 400 step 10) {
                if ((renderedBmp.getPixel(px, py) ushr 24) > 0) nonEmptyPixels++
            }
        }
        assertTrue("Rendered Chrome bitmap must contain visible metallic pixels", nonEmptyPixels > 20)
        renderedBmp.recycle()

        // PNG Transparent Export
        val pngRes = ExportManager.exportToCacheAndGetShareUri(
            context = app,
            doc = chromeDoc,
            format = ExportFormat.PNG,
            scope = ExportScope.FULL_PAGE,
            selectedIds = emptySet(),
            scale = 1f,
            jpgQuality = 90,
            transparentPng = true,
            customFontPaths = emptyMap()
        )
        assertTrue(pngRes.validation.isValid)
        assertTrue(pngRes.byteSize > 500L)

        // SVG Export (real multi-stop chrome linearGradients + reflect spreadMethod + shadow filter)
        val svgText = SvgExporter.exportToSvg(chromeDoc)
        assertTrue("SVG must contain real Chrome linearGradient defs", svgText.contains("id=\"chg_"))
        assertTrue("Liquid Chrome SVG gradient must use reflect spreadMethod", svgText.contains("spreadMethod=\"reflect\""))
        assertTrue("Chrome shadow SVG filter must emit feDropShadow", svgText.contains("<feDropShadow"))

        val svgRes = ExportManager.exportToCacheAndGetShareUri(
            context = app,
            doc = chromeDoc,
            format = ExportFormat.SVG,
            scope = ExportScope.FULL_PAGE,
            selectedIds = emptySet(),
            scale = 1f,
            jpgQuality = 90,
            transparentPng = false,
            customFontPaths = emptyMap()
        )
        assertTrue("SVG export failed validation: ${svgRes.validation.errorMessage}", svgRes.validation.isValid)

        // PDF Export
        val pdfRes = ExportManager.exportToCacheAndGetShareUri(
            context = app,
            doc = chromeDoc,
            format = ExportFormat.PDF,
            scope = ExportScope.FULL_PAGE,
            selectedIds = emptySet(),
            scale = 1f,
            jpgQuality = 90,
            transparentPng = false,
            customFontPaths = emptyMap()
        )
        assertTrue("PDF export failed validation: ${pdfRes.validation.errorMessage}", pdfRes.validation.isValid)
        assertTrue(pdfRes.byteSize > 400L)
    }

    // --- 6. Phone & Tablet UI Verification of CHROME EFFECTS Panel ---

    @Test
    fun `phone opens chrome studio sheet selects styles and saves custom style`() {
        openBrandTemplateInEditor()

        composeRule.waitForTag("mobile_chrome_button")
        composeRule.onNodeWithTag("mobile_chrome_button").performClick()

        composeRule.waitForTag("chrome_effects_panel")
        composeRule.onNodeWithTag("chrome_live_preview").assertIsDisplayed()

        // All 6 styles visible/selectable
        composeRule.onNodeWithTag("chrome_preset_classic_chrome").performScrollTo().performClick()
        composeRule.onNodeWithTag("chrome_preset_liquid_chrome").performScrollTo().performClick()
        composeRule.onNodeWithTag("chrome_preset_holographic_chrome").performScrollTo().performClick()
        composeRule.onNodeWithTag("chrome_preset_gold_chrome").performScrollTo().performClick()

        // Verify controls exist in the scrollable panel
        composeRule.onNodeWithTag("chrome_slider_reflection").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("chrome_slider_depth").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("chrome_slider_bevel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("chrome_slider_intensity").performScrollTo().assertIsDisplayed()

        // Save custom Chrome style
        composeRule.onNodeWithTag("chrome_save_style_button").performScrollTo().performClick()
        composeRule.waitForTag("chrome_save_style_name_input")
        composeRule.onNodeWithTag("chrome_save_style_name_input").performTextReplacement("Mi Oro Cromo")
        composeRule.onNodeWithTag("chrome_confirm_save_style_button").performClick()

        composeRule.waitForIdle()
        composeRule.waitUntil(5000L) {
            composeRule.onAllNodesWithText("Mi Oro Cromo").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp")
    fun `tablet inspector shows chrome tab and applies rose gold and 3d chrome cleanly`() {
        openBrandTemplateInEditor()

        composeRule.waitForTag("tablet_tab_chrome")
        composeRule.onNodeWithTag("tablet_tab_chrome").performClick()

        composeRule.waitForTag("chrome_effects_panel")
        composeRule.onNodeWithTag("chrome_preset_three_d_chrome").performScrollTo().performClick()
        composeRule.onNodeWithTag("chrome_preset_rose_gold_chrome").performScrollTo().performClick()
        composeRule.onNodeWithTag("chrome_slider_light_angle").performScrollTo().assertIsDisplayed()
    }
}
