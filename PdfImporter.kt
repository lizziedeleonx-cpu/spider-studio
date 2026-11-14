package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.model.DocumentBackgroundMode
import com.example.model.ImageShape
import com.example.model.SpiderDocument
import com.example.model.VectorLayer
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object PdfImporter {

    fun importPdfAsDocument(
        context: Context,
        uri: Uri,
        baseName: String,
        maxPages: Int = 5
    ): SpiderDocument {
        val pages = renderPagesInternal(context, uri, maxPages, renderScale = 2f)
        if (pages.isEmpty()) {
            error("El archivo PDF no contiene páginas renderizables.")
        }
        val first = pages.first()
        val docW = first.first.first.toInt().coerceIn(64, 4096)
        val docH = first.first.second.toInt().coerceIn(64, 4096)
        val layers = pages.mapIndexed { idx, (_, img) ->
            VectorLayer(
                name = "Página ${idx + 1}",
                isVisible = idx == 0, // Página 1 visible inicialmente, resto de páginas como capas recuperables
                elements = listOf(
                    img.copy(
                        x = 0f,
                        y = 0f,
                        width = docW.toFloat(),
                        height = docH.toFloat()
                    )
                )
            )
        }
        return SpiderDocument(
            name = baseName.ifBlank { "PDF Importado" },
            widthPx = docW,
            heightPx = docH,
            backgroundMode = DocumentBackgroundMode.SOLID,
            backgroundColorArgb = 0xFFFFFFFF,
            layers = layers
        )
    }

    fun importFirstPagesFromPdf(
        context: Context,
        uri: Uri,
        maxPages: Int = 3
    ): List<ImageShape> {
        return renderPagesInternal(context, uri, maxPages, renderScale = 2f).mapIndexed { i, (_, img) ->
            val maxW = 900f
            val scale = min(1f, maxW / img.width.coerceAtLeast(1f))
            img.copy(
                x = 40f + i * 24f,
                y = 40f + i * 24f,
                width = img.width * scale,
                height = img.height * scale
            )
        }
    }

    private fun renderPagesInternal(
        context: Context,
        uri: Uri,
        maxPages: Int,
        renderScale: Float
    ): List<Pair<Pair<Float, Float>, ImageShape>> {
        val tempPdf = File(context.cacheDir, "tmp_import_${UUID.randomUUID()}.pdf")
        val pfd: ParcelFileDescriptor = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempPdf).use { output -> input.copyTo(output) }
            }
            if (tempPdf.exists() && tempPdf.length() > 0L) {
                ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            }
        } catch (_: Throwable) {
            tempPdf.delete()
            return emptyList()
        }

        val outList = mutableListOf<Pair<Pair<Float, Float>, ImageShape>>()
        val dir = File(context.filesDir, "imported_media").apply { mkdirs() }

        try {
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val count = renderer.pageCount.coerceAtMost(maxPages)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val rawW = page.width * renderScale
                            val rawH = page.height * renderScale
                            val capScale = if (max(rawW, rawH) > 2400f) 2400f / max(rawW, rawH) else 1f
                            val w = (rawW * capScale).toInt().coerceAtLeast(64)
                            val h = (rawH * capScale).toInt().coerceAtLeast(64)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val outFile = File(dir, "pdf_page_${UUID.randomUUID()}.png")
                            FileOutputStream(outFile).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 95, fos)
                            }
                            bmp.recycle()
                            outList.add(
                                (w.toFloat() to h.toFloat()) to ImageShape(
                                    name = "Página PDF ${i + 1}",
                                    x = 0f,
                                    y = 0f,
                                    width = w.toFloat(),
                                    height = h.toFloat(),
                                    imagePath = outFile.absolutePath
                                )
                            )
                        }
                    }
                }
            }
        } finally {
            tempPdf.delete()
        }
        return outList
    }
}
