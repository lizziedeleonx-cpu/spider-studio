package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.SpiderJsonCodec
import com.example.model.DocumentBackgroundMode
import com.example.model.ExportFormat
import com.example.model.ExportScope
import com.example.model.SpiderDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ExportManager {

    data class ExportResult(
        val fileName: String,
        val cacheFile: File,
        val shareUri: Uri,
        val byteSize: Long,
        val validation: ExportValidationReport
    )

    fun computeProportionalTargetSize(
        baseW: Float,
        baseH: Float,
        scale: Float
    ): Pair<Int, Int> {
        val safeW = baseW.coerceAtLeast(1f)
        val safeH = baseH.coerceAtLeast(1f)
        var w = safeW * scale
        var h = safeH * scale
        val maxSide = max(w, h)
        if (maxSide > 4096f) {
            val factor = 4096f / maxSide
            w *= factor
            h *= factor
        }
        val minSide = min(w, h)
        if (minSide < 32f) {
            val factor = 32f / minSide
            w *= factor
            h *= factor
            val cappedMax = max(w, h)
            if (cappedMax > 4096f) {
                val f2 = 4096f / cappedMax
                w *= f2
                h *= f2
            }
        }
        return w.roundToInt().coerceIn(1, 4096) to h.roundToInt().coerceIn(1, 4096)
    }

    suspend fun exportToCacheAndGetShareUri(
        context: Context,
        doc: SpiderDocument,
        format: ExportFormat,
        scope: ExportScope,
        selectedIds: Set<String>,
        scale: Float,
        jpgQuality: Int,
        transparentPng: Boolean,
        customFontPaths: Map<String, String>
    ): ExportResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeBase = doc.name.replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_-]"), "_").ifBlank { "spider_design" }
        val fileName = "$safeBase.${format.extension}"
        val outFile = File(dir, fileName)
        FileOutputStream(outFile).use { os ->
            writeExportStream(
                context = context,
                outputStream = os,
                doc = doc,
                format = format,
                scope = scope,
                selectedIds = selectedIds,
                scale = scale,
                jpgQuality = jpgQuality,
                transparentPng = transparentPng,
                customFontPaths = customFontPaths
            )
        }
        val validation = ExportValidator.validateExportedFile(context, outFile, format)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        ExportResult(fileName, outFile, uri, outFile.length(), validation)
    }

    suspend fun exportToUri(
        context: Context,
        targetUri: Uri,
        doc: SpiderDocument,
        format: ExportFormat,
        scope: ExportScope,
        selectedIds: Set<String>,
        scale: Float,
        jpgQuality: Int,
        transparentPng: Boolean,
        customFontPaths: Map<String, String>
    ): ExportValidationReport = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempFile = File(dir, "tmp_${UUID.randomUUID()}.${format.extension}")
        try {
            FileOutputStream(tempFile).use { os ->
                writeExportStream(
                    context = context,
                    outputStream = os,
                    doc = doc,
                    format = format,
                    scope = scope,
                    selectedIds = selectedIds,
                    scale = scale,
                    jpgQuality = jpgQuality,
                    transparentPng = transparentPng,
                    customFontPaths = customFontPaths
                )
            }
            val validation = ExportValidator.validateExportedFile(context, tempFile, format)
            if (!validation.isValid) {
                error(validation.errorMessage ?: "El archivo generado no superó la comprobación de integridad.")
            }
            context.contentResolver.openOutputStream(targetUri)?.use { os ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(os)
                }
                os.flush()
            } ?: error("No se pudo abrir el destino seleccionado.")
            validation
        } finally {
            tempFile.delete()
        }
    }

    private fun writeExportStream(
        context: Context,
        outputStream: OutputStream,
        doc: SpiderDocument,
        format: ExportFormat,
        scope: ExportScope,
        selectedIds: Set<String>,
        scale: Float,
        jpgQuality: Int,
        transparentPng: Boolean,
        customFontPaths: Map<String, String>
    ) {
        val selectedOnly = scope == ExportScope.SELECTED_AREA && selectedIds.isNotEmpty()
        val cropRectF = if (selectedOnly) {
            val allVisible = doc.layers.filter { it.isVisible }.flatMap { it.elements }.filter { it.id in selectedIds }
            VectorMath.computeCombinedBounds(allVisible)?.let {
                RectF(it.left, it.top, it.right, it.bottom)
            }
        } else null

        val baseW = cropRectF?.width() ?: doc.widthPx.toFloat()
        val baseH = cropRectF?.height() ?: doc.heightPx.toFloat()
        val (targetW, targetH) = computeProportionalTargetSize(baseW, baseH, scale)

        when (format) {
            ExportFormat.PNG -> {
                val bmp = AndroidVectorRenderer.renderDocumentToBitmap(
                    context = context,
                    doc = doc,
                    targetWidth = targetW,
                    targetHeight = targetH,
                    transparentBackground = transparentPng,
                    customFontPaths = customFontPaths,
                    cropBounds = cropRectF
                )
                bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                bmp.recycle()
            }
            ExportFormat.JPG -> {
                val opaqueBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val c = Canvas(opaqueBmp)
                c.drawColor(Color.WHITE)
                AndroidVectorRenderer.renderToCanvas(
                    context = context,
                    canvas = c,
                    doc = doc,
                    targetWidth = targetW.toFloat(),
                    targetHeight = targetH.toFloat(),
                    transparentBackground = false,
                    customFontPaths = customFontPaths,
                    cropBounds = cropRectF
                )
                opaqueBmp.compress(Bitmap.CompressFormat.JPEG, jpgQuality.coerceIn(50, 100), outputStream)
                opaqueBmp.recycle()
            }
            ExportFormat.SVG -> {
                val svg = SvgExporter.exportToSvg(
                    doc = doc,
                    selectedIds = selectedIds,
                    selectedOnly = selectedOnly
                )
                outputStream.write(svg.toByteArray(Charsets.UTF_8))
            }
            ExportFormat.PDF -> {
                val nativeBytes = runCatching {
                    val pdfDoc = PdfDocument()
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, 1).create()
                        val page = pdfDoc.startPage(pageInfo)
                        if (doc.backgroundMode == DocumentBackgroundMode.TRANSPARENT) {
                            page.canvas.drawColor(Color.WHITE)
                        }
                        AndroidVectorRenderer.renderToCanvas(
                            context = context,
                            canvas = page.canvas,
                            doc = doc,
                            targetWidth = targetW.toFloat(),
                            targetHeight = targetH.toFloat(),
                            transparentBackground = false,
                            customFontPaths = customFontPaths,
                            cropBounds = cropRectF
                        )
                        pdfDoc.finishPage(page)
                        val baos = ByteArrayOutputStream()
                        pdfDoc.writeTo(baos)
                        baos.toByteArray()
                    } finally {
                        pdfDoc.close()
                    }
                }.getOrNull()

                val isNativeValid = nativeBytes != null &&
                    nativeBytes.size > 32 &&
                    String(nativeBytes, 0, minOf(5, nativeBytes.size), Charsets.US_ASCII) == "%PDF-" &&
                    String(
                        nativeBytes,
                        maxOf(0, nativeBytes.size - 128),
                        minOf(128, nativeBytes.size),
                        Charsets.US_ASCII
                    ).contains("%%EOF")

                if (isNativeValid) {
                    outputStream.write(nativeBytes!!)
                } else {
                    val opaqueBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val c = Canvas(opaqueBmp)
                    c.drawColor(Color.WHITE)
                    AndroidVectorRenderer.renderToCanvas(
                        context = context,
                        canvas = c,
                        doc = doc,
                        targetWidth = targetW.toFloat(),
                        targetHeight = targetH.toFloat(),
                        transparentBackground = false,
                        customFontPaths = customFontPaths,
                        cropBounds = cropRectF
                    )
                    val jpgOut = ByteArrayOutputStream()
                    opaqueBmp.compress(Bitmap.CompressFormat.JPEG, 92, jpgOut)
                    opaqueBmp.recycle()
                    writeSingleImagePdf14(outputStream, targetW, targetH, jpgOut.toByteArray())
                }
            }
            ExportFormat.SPIDER -> {
                val json = SpiderJsonCodec.encode(doc)
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            }
        }
        outputStream.flush()
    }

    private fun writeSingleImagePdf14(
        out: OutputStream,
        width: Int,
        height: Int,
        jpegBytes: ByteArray
    ) {
        val offsets = mutableListOf<Int>()
        val buf = ByteArrayOutputStream()
        fun writeAscii(s: String) {
            buf.write(s.toByteArray(Charsets.US_ASCII))
        }
        writeAscii("%PDF-1.4\n")

        offsets += buf.size()
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets += buf.size()
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        offsets += buf.size()
        writeAscii("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n")

        offsets += buf.size()
        writeAscii("4 0 obj\n<< /Type /XObject /Subtype /Image /Width $width /Height $height /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpegBytes.size} >>\nstream\n")
        buf.write(jpegBytes)
        writeAscii("\nendstream\nendobj\n")

        val contentStream = "q\n$width 0 0 $height 0 0 cm\n/Im0 Do\nQ\n"
        offsets += buf.size()
        writeAscii("5 0 obj\n<< /Length ${contentStream.length} >>\nstream\n$contentStream\nendstream\nendobj\n")

        val xrefPos = buf.size()
        writeAscii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { off ->
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        out.write(buf.toByteArray())
    }
}
