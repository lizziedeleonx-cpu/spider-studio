package com.example.data

import android.content.Context
import android.graphics.Bitmap
import com.example.engine.AndroidVectorRenderer
import com.example.model.ChromeEffectConfig
import com.example.model.SpiderDocument
import com.example.model.TemplateCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val database: SpiderDatabase
) {
    private val projectDao = database.projectDao()
    private val paletteDao = database.paletteDao()
    private val fontDao = database.customFontDao()
    private val chromeStyleDao = database.chromeStyleDao()

    val projectsFlow: Flow<List<ProjectEntity>> = projectDao.observeAll()
    val palettesFlow: Flow<List<PaletteEntity>> = paletteDao.observeAll()
    val customFontsFlow: Flow<List<CustomFontEntity>> = fontDao.observeAll()
    val chromeStylesFlow: Flow<List<ChromeStyleEntity>> = chromeStyleDao.observeAll()

    suspend fun seedDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        if (paletteDao.count() == 0) {
            paletteDao.upsert(
                PaletteEntity(
                    id = "pal_spider_core",
                    name = "Spider Violeta",
                    colorsCsv = listOf(0xFF09090D, 0xFF171720, 0xFF8B5CF6, 0xFFA78BFA, 0xFF38BDF8, 0xFFF4F4F8)
                        .joinToString(",") { it.toString(16) },
                    updatedAt = System.currentTimeMillis()
                )
            )
            paletteDao.upsert(
                PaletteEntity(
                    id = "pal_editorial_neon",
                    name = "Editorial Neón",
                    colorsCsv = listOf(0xFF101018, 0xFFF43F5E, 0xFFFB923C, 0xFFFACC15, 0xFF22C55E, 0xFF06B6D4)
                        .joinToString(",") { it.toString(16) },
                    updatedAt = System.currentTimeMillis() - 1000L
                )
            )
        }
        if (projectDao.count() == 0) {
            val starterDoc = TemplateCatalog.templates.first().buildDocument()
            saveDocument(starterDoc)
        }
    }

    suspend fun saveDocument(doc: SpiderDocument): SpiderDocument = withContext(Dispatchers.IO) {
        val updated = doc.copy(updatedAt = System.currentTimeMillis())
        val json = SpiderJsonCodec.encode(updated)
        val existingThumb = projectDao.getById(updated.id)?.thumbnailPath
        val baseEntity = ProjectEntity(
            id = updated.id,
            name = updated.name,
            widthPx = updated.widthPx,
            heightPx = updated.heightPx,
            documentJson = json,
            thumbnailPath = existingThumb,
            createdAt = updated.createdAt,
            updatedAt = updated.updatedAt
        )
        projectDao.upsert(baseEntity)
        val thumbPath = renderAndSaveThumbnail(updated)
        if (thumbPath != null && thumbPath != existingThumb) {
            projectDao.upsert(baseEntity.copy(thumbnailPath = thumbPath))
        }
        updated
    }

    suspend fun loadDocument(id: String): SpiderDocument? = withContext(Dispatchers.IO) {
        val entity = projectDao.getById(id) ?: return@withContext null
        runCatching { SpiderJsonCodec.decode(entity.documentJson) }.getOrNull()
    }

    suspend fun duplicateProject(id: String): SpiderDocument? = withContext(Dispatchers.IO) {
        val existing = loadDocument(id) ?: return@withContext null
        val copy = existing.copy(
            id = UUID.randomUUID().toString(),
            name = "${existing.name} (Copia)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveDocument(copy)
    }

    suspend fun renameProject(id: String, newName: String) = withContext(Dispatchers.IO) {
        val existing = loadDocument(id) ?: return@withContext
        saveDocument(existing.copy(name = newName.trim().ifBlank { existing.name }))
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        val thumbFile = File(thumbnailsDir(), "$id.png")
        if (thumbFile.exists()) thumbFile.delete()
        projectDao.deleteById(id)
    }

    suspend fun savePalette(name: String, colors: List<Long>) = withContext(Dispatchers.IO) {
        paletteDao.upsert(
            PaletteEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Paleta Personalizada" },
                colorsCsv = colors.joinToString(",") { it.toString(16) },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePalette(id: String) = withContext(Dispatchers.IO) {
        paletteDao.deleteById(id)
    }

    suspend fun addCustomFont(displayName: String, sourceBytes: ByteArray): CustomFontEntity =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
            val id = "custom_${UUID.randomUUID()}"
            val outFile = File(dir, "$id.ttf")
            outFile.writeBytes(sourceBytes)
            val entity = CustomFontEntity(
                id = id,
                displayName = displayName,
                filePath = outFile.absolutePath,
                addedAt = System.currentTimeMillis()
            )
            fontDao.upsert(entity)
            entity
        }

    suspend fun deleteCustomFont(id: String, filePath: String) = withContext(Dispatchers.IO) {
        File(filePath).delete()
        fontDao.deleteById(id)
    }

    suspend fun getCustomFontMap(): Map<String, String> = withContext(Dispatchers.IO) {
        fontDao.getAllSync().associate { it.id to it.filePath }
    }

    suspend fun saveChromeStyle(name: String, config: ChromeEffectConfig): ChromeStyleEntity =
        withContext(Dispatchers.IO) {
            val cleanName = name.trim().ifBlank { config.styleType.label }
            val normalized = config.copy(enabled = true)
            val entity = ChromeStyleEntity(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                configJson = SpiderJsonCodec.encodeChromeConfigJson(normalized),
                updatedAt = System.currentTimeMillis()
            )
            chromeStyleDao.upsert(entity)
            entity
        }

    suspend fun deleteChromeStyle(id: String) = withContext(Dispatchers.IO) {
        chromeStyleDao.deleteById(id)
    }

    private suspend fun renderAndSaveThumbnail(doc: SpiderDocument): String? {
        return runCatching {
            val fontMap = getCustomFontMap()
            val maxDim = 420
            val aspect = doc.widthPx.toFloat() / doc.heightPx.toFloat().coerceAtLeast(1f)
            val thumbW = if (aspect >= 1f) maxDim else (maxDim * aspect).toInt().coerceAtLeast(80)
            val thumbH = if (aspect >= 1f) (maxDim / aspect).toInt().coerceAtLeast(80) else maxDim
            val bmp = AndroidVectorRenderer.renderDocumentToBitmap(
                context = context,
                doc = doc,
                targetWidth = thumbW,
                targetHeight = thumbH,
                transparentBackground = false,
                customFontPaths = fontMap
            )
            val outFile = File(thumbnailsDir(), "${doc.id}.png")
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
            }
            bmp.recycle()
            outFile.absolutePath
        }.getOrNull()
    }

    private fun thumbnailsDir(): File = File(context.filesDir, "thumbnails").apply { mkdirs() }
}
