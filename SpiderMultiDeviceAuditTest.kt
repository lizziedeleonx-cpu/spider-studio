package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProjectRepository
import com.example.data.SpiderDatabase
import com.example.data.SpiderJsonCodec
import com.example.engine.AndroidVectorRenderer
import com.example.engine.ComposeVectorRenderer.drawStudioCanvas
import com.example.model.AlignAction
import com.example.model.BezierNode
import com.example.model.BlendModeStyle
import com.example.model.BrushPreset
import com.example.model.EditorCanvasConfig
import com.example.model.EditorTool
import com.example.model.EffectStyle
import com.example.model.ImageShape
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathShape
import com.example.model.RectShape
import com.example.model.SpiderDocument
import com.example.model.StrokeStyle
import com.example.model.VectorLayer
import com.example.model.ZOrderAction
import com.example.viewmodel.EditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpiderMultiDeviceAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSingletonsBefore() {
        clearFileProviderCache()
    }

    @After
    fun resetSingletonsAfter() {
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

    private fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 6000L) {
        waitUntil(timeoutMs) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTemplateInEditor() {
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

    // --- 1. Regression Tests: Renderer & Codec ---

    @Test
    fun `json codec handles NaN and Infinity floats without throwing`() {
        val brokenRect = RectShape(
            name = "Rect NaN",
            x = Float.NaN,
            y = Float.POSITIVE_INFINITY,
            width = 120f,
            height = Float.NEGATIVE_INFINITY,
            rotationDeg = Float.NaN,
            opacity = Float.NaN,
            cornerRadius = Float.POSITIVE_INFINITY
        )
        val doc = SpiderDocument(
            name = "Prueba NaN",
            layers = listOf(VectorLayer(elements = listOf(brokenRect)))
        )

        val encoded = SpiderJsonCodec.encode(doc)
        assertTrue(encoded.isNotBlank())
        val decoded = SpiderJsonCodec.decode(encoded)
        val decodedRect = decoded.layers.first().elements.first() as RectShape
        assertTrue(decodedRect.x.isFinite())
        assertTrue(decodedRect.y.isFinite())
        assertTrue(decodedRect.height.isFinite())
        assertTrue(decodedRect.rotationDeg.isFinite())
    }

    @Test
    fun `compose canvas renderer draws brush ribbon with multiply blend mode and glow without stack underflow`() {
        val brushRibbon = PathShape(
            name = "Pincel Blend Glow",
            x = 40f,
            y = 40f,
            width = 240f,
            height = 120f,
            nodes = listOf(
                BezierNode(x = 40f, y = 100f),
                BezierNode(x = 160f, y = 60f),
                BezierNode(x = 280f, y = 120f)
            ),
            isBrushStroke = true,
            pressureWidths = listOf(0.5f, 1.4f, 0.7f),
            brushPreset = BrushPreset.INK_PEN,
            brushTaperEnds = true,
            stroke = StrokeStyle(enabled = true, colorArgb = 0xFF8B5CF6, width = 18f),
            effects = EffectStyle(
                blendMode = BlendModeStyle.MULTIPLY,
                glowEnabled = true,
                glowColorArgb = 0xFF22D3EE,
                glowRadius = 16f
            )
        )
        val doc = SpiderDocument(
            name = "Brush Blend Test",
            widthPx = 400,
            heightPx = 300,
            layers = listOf(VectorLayer(elements = listOf(brushRibbon)))
        )

        // Render via AndroidVectorRenderer and Compose Canvas DrawScope without exception
        val context = ApplicationProvider.getApplicationContext<Context>()
        val renderedBmp = AndroidVectorRenderer.renderDocumentToBitmap(
            context = context,
            doc = doc,
            targetWidth = 200,
            targetHeight = 150,
            transparentBackground = false
        )
        assertEquals(200, renderedBmp.width)
        assertEquals(150, renderedBmp.height)
        renderedBmp.recycle()

        // Also exercise ComposeVectorRenderer directly inside Compose content
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val ctx = LocalContext.current
                Canvas(modifier = Modifier.size(300.dp)) {
                    drawStudioCanvas(
                        context = ctx,
                        doc = doc,
                        config = EditorCanvasConfig(showGrid = true, showRulers = true, showGuides = true),
                        zoom = 0.05f,
                        pan = Offset.Zero,
                        selectedIds = setOf(brushRibbon.id),
                        activeTool = EditorTool.SELECT,
                        activeNodeIndex = null,
                        penInProgressNodes = emptyList(),
                        freehandPreviewPoints = emptyList(),
                        snapLines = emptyList(),
                        customFontPaths = emptyMap()
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `android vector renderer samples oversized raster images safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imgFile = File(context.cacheDir, "audit_test_photo.png")
        val bigBmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        bigBmp.eraseColor(0xFF8B5CF6.toInt())
        FileOutputStream(imgFile).use { fos ->
            bigBmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
        }
        bigBmp.recycle()

        val imageEl = ImageShape(
            name = "Foto Audit",
            x = 20f,
            y = 20f,
            width = 300f,
            height = 225f,
            imagePath = imgFile.absolutePath
        )
        val doc = SpiderDocument(
            widthPx = 600,
            heightPx = 450,
            layers = listOf(VectorLayer(elements = listOf(imageEl)))
        )

        val outBmp = AndroidVectorRenderer.renderDocumentToBitmap(
            context = context,
            doc = doc,
            targetWidth = 300,
            targetHeight = 225,
            transparentBackground = false
        )
        assertEquals(300, outBmp.width)
        assertEquals(225, outBmp.height)
        outBmp.recycle()
        imgFile.delete()
    }

    // --- 2. Regression Tests: ViewModel, Persistence & Locks ---

    @Test
    fun `editor viewmodel preserves unsaved edits across duplicate loadProject and saves synchronously on saveNowAndThen`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ProjectRepository(app, SpiderDatabase.getInstance(app))
        val initialDoc = SpiderDocument(
            name = "Documento Base",
            widthPx = 1000,
            heightPx = 1000,
            layers = listOf(
                VectorLayer(
                    name = "Capa 1",
                    elements = listOf(
                        RectShape(id = "r1", x = 100f, y = 100f, width = 200f, height = 150f)
                    )
                )
            )
        )
        runBlocking { repo.saveDocument(initialDoc) }

        val vm = EditorViewModel(app)
        vm.loadProject(initialDoc.id)
        waitForCondition { vm.uiState.value.document?.id == initialDoc.id && !vm.uiState.value.isLoading }

        val added = RectShape(id = "r2", x = 350f, y = 350f, width = 160f, height = 160f)
        assertTrue(vm.addElementToActiveLayer(added))
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        assertTrue(vm.uiState.value.canUndo)
        assertEquals(2, vm.uiState.value.document!!.layers.first().elements.size)

        // Simulates configuration change / recomposition calling loadProject(sameId) again
        vm.loadProject(initialDoc.id)
        waitForCondition { !vm.uiState.value.isLoading }
        assertEquals(2, vm.uiState.value.document!!.layers.first().elements.size)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        assertTrue(vm.uiState.value.canUndo)

        // saveNowAndThen must persist before invoking callback
        var callbackSavedCount = -1
        vm.saveNowAndThen(silent = true) {
            val persisted = runBlocking { repo.loadDocument(initialDoc.id) }
            callbackSavedCount = persisted?.layers?.firstOrNull()?.elements?.size ?: -1
        }
        waitForCondition { callbackSavedCount >= 0 }
        assertEquals(2, callbackSavedCount)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `locked layers and locked elements block all editing and structural mutations`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ProjectRepository(app, SpiderDatabase.getInstance(app))
        val rect = RectShape(id = "rect_lock", x = 100f, y = 100f, width = 200f, height = 120f, isLocked = true)
        val unlockedRect = RectShape(id = "rect_free", x = 400f, y = 100f, width = 140f, height = 140f, isLocked = false)
        val layer = VectorLayer(id = "layer_1", name = "Capa Principal", isLocked = false, elements = listOf(rect, unlockedRect))
        val doc = SpiderDocument(
            name = "Bloqueos",
            widthPx = 1080,
            heightPx = 1080,
            layers = listOf(layer)
        )
        runBlocking { repo.saveDocument(doc) }

        val vm = EditorViewModel(app)
        vm.loadProject(doc.id)
        waitForCondition { vm.uiState.value.document?.id == doc.id && !vm.uiState.value.isLoading }

        // 1. Element-level lock tests
        vm.selectElementById(layer.id, rect.id)
        assertFalse(vm.isElementEditable(rect.id))
        vm.moveSelectedBy(50f, 50f, withSnap = false)
        vm.resizeSelectedElement(rect.id, 10f, 10f, 400f, 400f)
        vm.rotateSelectedElement(rect.id, 45f)
        vm.alignSelected(AlignAction.CENTER_H)
        vm.reorderSelectedElement(ZOrderAction.BRING_TO_FRONT)
        vm.convertSelectedToPath()
        vm.deleteSelected()

        val afterElementLockAttempts = vm.uiState.value.document!!.layers.first().elements
        assertEquals(2, afterElementLockAttempts.size)
        val stillRect = afterElementLockAttempts.first { it.id == rect.id } as RectShape
        assertEquals(100f, stillRect.x, 0.01f)
        assertEquals(100f, stillRect.y, 0.01f)
        assertEquals(200f, stillRect.width, 0.01f)
        assertEquals(0f, stillRect.rotationDeg, 0.01f)

        // 2. Layer-level lock tests (even unlockedRect inside locked layer must be protected)
        vm.toggleLayerLock(layer.id)
        vm.selectElementById(layer.id, unlockedRect.id)
        assertFalse(vm.isElementEditable(unlockedRect.id))
        assertFalse(vm.addElementToActiveLayer(RectShape(x = 0f, y = 0f, width = 50f, height = 50f)))
        vm.moveSelectedBy(80f, 80f, withSnap = false)
        vm.resizeSelectedElement(unlockedRect.id, 0f, 0f, 500f, 500f)
        vm.rotateSelectedElement(unlockedRect.id, 90f)
        vm.deleteSelected()

        val afterLayerLockAttempts = vm.uiState.value.document!!.layers.first().elements
        assertEquals(2, afterLayerLockAttempts.size)
        val stillFreeRect = afterLayerLockAttempts.first { it.id == unlockedRect.id } as RectShape
        assertEquals(400f, stillFreeRect.x, 0.01f)
        assertEquals(140f, stillFreeRect.width, 0.01f)
        assertEquals(0f, stillFreeRect.rotationDeg, 0.01f)
    }

    // --- 3. Multi-Device Adaptive UI Tests ---

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp")
    fun `compact portrait phone shows bottom nav bar and mobile editor sheet dock`() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("welcome_enter_studio_button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("welcome_enter_studio_button").performClick()
        }
        composeRule.waitForTag("home_import_button")
        composeRule.onNodeWithText("SVG, PDF, PNG").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").assertIsDisplayed()

        // Navigate to Editor via template
        composeRule.onNodeWithTag("nav_templates").performClick()
        composeRule.waitForTag("template_card_tpl_spider_brand")
        composeRule.onNodeWithTag("template_card_tpl_spider_brand").performClick()
        composeRule.waitForTag("editor_canvas")
        composeRule.waitForIdle()

        // Compact phone (<840dp) uses mobile dock & no persistent side inspector
        composeRule.onNodeWithTag("mobile_properties_button").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile_layers_button").assertIsDisplayed()
        composeRule.onNodeWithTag("tool_select").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Capas").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(sdk = [36], qualifiers = "w720dp-h360dp")
    fun `landscape phone switches to navigation rail and renders editor cleanly`() {
        openTemplateInEditor()
        composeRule.onNodeWithTag("editor_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("tool_select").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile_properties_button").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile_layers_button").assertIsDisplayed()

        // Open Properties bottom sheet in landscape phone and verify action strip scrolls without crash
        composeRule.onNodeWithTag("mobile_properties_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            "Selecciona un objeto en el lienzo",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w674dp-h841dp")
    fun `foldable unfolded device renders navigation rail and editor mobile dock`() {
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("welcome_enter_studio_button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("welcome_enter_studio_button").performClick()
        }
        composeRule.waitForTag("nav_home")
        composeRule.onNodeWithTag("nav_home").assertIsDisplayed()
        composeRule.onNodeWithTag("home_new_doc_button").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_templates").performClick()
        composeRule.waitForTag("template_card_tpl_spider_brand")
        composeRule.onNodeWithTag("template_card_tpl_spider_brand").performClick()
        composeRule.waitForTag("editor_canvas")

        composeRule.onNodeWithTag("mobile_properties_button").assertIsDisplayed()
        composeRule.onNodeWithTag("mobile_layers_button").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp")
    fun `expanded tablet displays persistent side inspector alongside editor canvas`() {
        openTemplateInEditor()

        // Expanded tablet (>=840dp) displays persistent side inspector tabs and hides mobile dock
        composeRule.onNodeWithTag("editor_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("tool_select").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Propiedades").fetchSemanticsNodes().isNotEmpty())
        composeRule.onAllNodesWithText("Propiedades")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Capas").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("mobile_properties_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("mobile_layers_button").fetchSemanticsNodes().isEmpty())

        // Switch to Layers tab in persistent side inspector and add a layer
        composeRule.onNodeWithText("Capas").performClick()
        composeRule.waitForTag("add_layer_button")
        composeRule.onNodeWithTag("add_layer_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Propiedades")[0].performClick()
        composeRule.waitForIdle()
    }
}
