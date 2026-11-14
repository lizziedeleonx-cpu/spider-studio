package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChromeStyleEntity
import com.example.data.CustomFontEntity
import com.example.data.PaletteEntity
import com.example.data.PreferencesRepository
import com.example.data.ProjectEntity
import com.example.data.ProjectRepository
import com.example.data.SpiderDatabase
import com.example.data.SpiderJsonCodec
import com.example.data.StudioPreferences
import com.example.engine.PdfImporter
import com.example.engine.SvgImporter
import com.example.model.ChromeEffectConfig
import com.example.model.DocumentBackgroundMode
import com.example.model.ImageShape
import com.example.model.RulerUnit
import com.example.model.SpiderDocument
import com.example.model.TemplateCatalog
import com.example.model.VectorLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SpiderDatabase.getInstance(application)
    val projectRepository = ProjectRepository(application, database)
    val preferencesRepository = PreferencesRepository(application)

    val projects: StateFlow<List<ProjectEntity>> = projectRepository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val palettes: StateFlow<List<PaletteEntity>> = projectRepository.palettesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customFonts: StateFlow<List<CustomFontEntity>> = projectRepository.customFontsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chromeStyles: StateFlow<List<ChromeStyleEntity>> = projectRepository.chromeStylesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<StudioPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioPreferences())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.seedDefaultsIfEmpty()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    fun createNewDocument(
        name: String,
        widthPx: Int,
        heightPx: Int,
        backgroundMode: DocumentBackgroundMode,
        backgroundColorArgb: Long,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val doc = SpiderDocument(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Nuevo Proyecto" },
                widthPx = widthPx.coerceIn(64, 8192),
                heightPx = heightPx.coerceIn(64, 8192),
                backgroundMode = backgroundMode,
                backgroundColorArgb = backgroundColorArgb,
                layers = listOf(VectorLayer(name = "Capa 1"))
            )
            val saved = projectRepository.saveDocument(doc)
            preferencesRepository.setLastOpenedProjectId(saved.id)
            onCreated(saved.id)
        }
    }

    fun createFromTemplate(templateId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val tpl = TemplateCatalog.templates.firstOrNull { it.id == templateId } ?: return@launch
            val doc = tpl.buildDocument().copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val saved = projectRepository.saveDocument(doc)
            preferencesRepository.setLastOpenedProjectId(saved.id)
            onCreated(saved.id)
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            projectRepository.duplicateProject(id)
            _statusMessage.value = "Proyecto duplicado."
        }
    }

    fun renameProject(id: String, newName: String) {
        viewModelScope.launch {
            projectRepository.renameProject(id, newName)
            _statusMessage.value = "Nombre actualizado."
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(id)
            _statusMessage.value = "Proyecto eliminado."
        }
    }

    fun importFileAsProject(uri: Uri, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val cr = app.contentResolver
                    val rawFileName = queryFileName(uri).orEmpty()
                    val cleanName = rawFileName
                        .replace(Regex("\\.(spider|json|svg|png|jpe?g|pdf)$", RegexOption.IGNORE_CASE), "")
                        .trim()
                        .ifBlank { "Archivo Importado" }
                    val mimeType = cr.getType(uri).orEmpty().lowercase()
                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No se pudo leer el archivo seleccionado.")
                    if (bytes.isEmpty()) error("El archivo está vacío.")

                    val isPdf = (bytes.size >= 5 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-") ||
                        mimeType == "application/pdf" ||
                        rawFileName.endsWith(".pdf", ignoreCase = true)

                    val isPng = (bytes.size >= 8 &&
                        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) ||
                        mimeType == "image/png" ||
                        rawFileName.endsWith(".png", ignoreCase = true)

                    val isJpg = (bytes.size >= 3 &&
                        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) ||
                        mimeType == "image/jpeg" || mimeType == "image/jpg" ||
                        rawFileName.endsWith(".jpg", ignoreCase = true) ||
                        rawFileName.endsWith(".jpeg", ignoreCase = true)

                    val doc = when {
                        isPdf -> PdfImporter.importPdfAsDocument(app, uri, cleanName)
                        isPng || isJpg -> importBitmapAsDocument(app, bytes, cleanName, isPng)
                        else -> {
                            val rawText = bytes.toString(Charsets.UTF_8).trim().removePrefix("\uFEFF").trim()
                            when {
                                rawText.startsWith("{") -> {
                                    SpiderJsonCodec.decode(rawText).copy(
                                        id = UUID.randomUUID().toString(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                }
                                rawText.contains("<svg", ignoreCase = true) -> {
                                    SvgImporter.importToDocument(rawText, cleanName, app)
                                }
                                else -> error("Formato no compatible. Usa archivos .svg, .png, .jpg, .pdf o .spider.")
                            }
                        }
                    }
                    projectRepository.saveDocument(doc)
                }
            }
            result.onSuccess { saved ->
                preferencesRepository.setLastOpenedProjectId(saved.id)
                _statusMessage.value = "Proyecto '${saved.name}' importado (${saved.widthPx}×${saved.heightPx} px)."
                onOpened(saved.id)
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Error al importar el archivo."
            }
        }
    }

    private fun importBitmapAsDocument(
        app: Application,
        bytes: ByteArray,
        name: String,
        isPng: Boolean
    ): SpiderDocument {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) {
            error("La imagen seleccionada no tiene dimensiones válidas.")
        }

        val dir = File(app.filesDir, "imported_media").apply { mkdirs() }
        val ext = if (isPng) "png" else "jpg"
        val outFile = File(dir, "img_${UUID.randomUUID()}.$ext")

        val maxSide = max(rawW, rawH)
        var docW = rawW
        var docH = rawH
        if (maxSide > 4096) {
            var sampleSize = 1
            while ((maxSide / sampleSize) > 4096) {
                sampleSize *= 2
            }
            val decoded = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: error("No se pudo decodificar la imagen.")
            docW = decoded.width.coerceIn(64, 4096)
            docH = decoded.height.coerceIn(64, 4096)
            FileOutputStream(outFile).use { fos ->
                decoded.compress(
                    if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    95,
                    fos
                )
            }
            decoded.recycle()
        } else {
            FileOutputStream(outFile).use { fos -> fos.write(bytes) }
            docW = rawW.coerceIn(64, 4096)
            docH = rawH.coerceIn(64, 4096)
        }

        val imgEl = ImageShape(
            name = name,
            x = 0f,
            y = 0f,
            width = docW.toFloat(),
            height = docH.toFloat(),
            imagePath = outFile.absolutePath
        )
        return SpiderDocument(
            name = name,
            widthPx = docW,
            heightPx = docH,
            backgroundMode = if (isPng) DocumentBackgroundMode.TRANSPARENT else DocumentBackgroundMode.SOLID,
            backgroundColorArgb = if (isPng) 0x00000000 else 0xFFFFFFFF,
            layers = listOf(VectorLayer(name = "Imagen", elements = listOf(imgEl)))
        )
    }

    fun importCustomFont(uri: Uri, fallbackName: String = "Fuente Personalizada") {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val cr = getApplication<Application>().contentResolver
                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No se pudo leer la fuente.")
                    val displayName = queryFileName(uri) ?: fallbackName
                    projectRepository.addCustomFont(displayName.removeSuffix(".ttf").removeSuffix(".otf"), bytes)
                }
            }
            res.onSuccess {
                _statusMessage.value = "Fuente '${it.displayName}' instalada."
            }.onFailure {
                _statusMessage.value = "No se pudo importar la fuente seleccionada."
            }
        }
    }

    fun deleteCustomFont(font: CustomFontEntity) {
        viewModelScope.launch {
            projectRepository.deleteCustomFont(font.id, font.filePath)
            _statusMessage.value = "Fuente eliminada."
        }
    }

    fun createPalette(name: String, colors: List<Long>) {
        viewModelScope.launch {
            projectRepository.savePalette(name, colors)
            _statusMessage.value = "Paleta guardada."
        }
    }

    fun deletePalette(id: String) {
        viewModelScope.launch {
            projectRepository.deletePalette(id)
        }
    }

    fun saveChromeStyle(name: String, config: ChromeEffectConfig) {
        viewModelScope.launch {
            projectRepository.saveChromeStyle(name, config)
            _statusMessage.value = "Estilo Chrome guardado."
        }
    }

    fun deleteChromeStyle(id: String) {
        viewModelScope.launch {
            projectRepository.deleteChromeStyle(id)
            _statusMessage.value = "Estilo Chrome eliminado."
        }
    }

    fun updateShowWelcome(show: Boolean) = viewModelScope.launch {
        preferencesRepository.updateShowWelcome(show)
    }

    fun updateAutoSave(enabled: Boolean, intervalSec: Int) = viewModelScope.launch {
        preferencesRepository.updateAutoSave(enabled, intervalSec)
    }

    fun updateCanvasDefaults(
        showGrid: Boolean,
        gridSizePx: Int,
        showRulers: Boolean,
        snapEnabled: Boolean,
        rulerUnit: RulerUnit
    ) = viewModelScope.launch {
        preferencesRepository.updateCanvasDefaults(showGrid, gridSizePx, showRulers, snapEnabled, rulerUnit)
    }

    fun updateStylusPressure(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateStylusPressure(enabled)
    }

    fun updateDefaultExportScale(scale: Int) = viewModelScope.launch {
        preferencesRepository.updateDefaultExportScale(scale)
    }

    private fun queryFileName(uri: Uri): String? {
        val cr = getApplication<Application>().contentResolver
        return cr.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }
}
