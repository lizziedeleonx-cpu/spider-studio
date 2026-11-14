package com.example.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.SpiderJsonCodec
import com.example.model.DocumentBackgroundMode
import com.example.model.ExportFormat
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

data class ExportValidationReport(
    val isValid: Boolean,
    val format: ExportFormat,
    val width: Int,
    val height: Int,
    val layerCount: Int,
    val vectorCount: Int,
    val byteSize: Long,
    val hasTransparency: Boolean,
    val compatibilityNotes: String,
    val errorMessage: String? = null
)

object ExportValidator {

    fun validateExportedFile(
        context: Context,
        file: File,
        format: ExportFormat
    ): ExportValidationReport {
        if (!file.exists() || file.length() <= 0L) {
            return ExportValidationReport(
                isValid = false,
                format = format,
                width = 0,
                height = 0,
                layerCount = 0,
                vectorCount = 0,
                byteSize = 0L,
                hasTransparency = false,
                compatibilityNotes = "Archivo vacío o inexistente.",
                errorMessage = "El archivo exportado no existe o está vacío."
            )
        }

        return when (format) {
            ExportFormat.SVG -> validateSvg(context, file)
            ExportFormat.PNG -> validatePng(file)
            ExportFormat.JPG -> validateJpg(file)
            ExportFormat.PDF -> validatePdf(file)
            ExportFormat.SPIDER -> validateSpider(file)
        }
    }

    private fun validateSvg(context: Context, file: File): ExportValidationReport {
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        if (text.isBlank()) {
            return invalidReport(ExportFormat.SVG, file.length(), "Contenido SVG vacío.")
        }

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val xmlDoc = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
            val root = xmlDoc.documentElement
            if (root == null || !root.localName.equals("svg", ignoreCase = true) && !root.tagName.equals("svg", ignoreCase = true)) {
                return invalidReport(ExportFormat.SVG, file.length(), "El elemento raíz XML no es <svg>.")
            }

            val parsed = SvgImporter.parseSvg(text, context)
            val vbAttr = root.getAttribute("viewBox").trim()
            val vbParts = vbAttr.split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
            val w = if (vbParts.size == 4 && vbParts[2] > 0f) vbParts[2].roundToInt() else parsed.widthPx
            val h = if (vbParts.size == 4 && vbParts[3] > 0f) vbParts[3].roundToInt() else parsed.heightPx

            val vectorTags = listOf("path", "rect", "circle", "ellipse", "polygon", "polyline", "line", "text", "image")
            var domVectorCount = 0
            for (tag in vectorTags) {
                domVectorCount += xmlDoc.getElementsByTagName(tag).length
            }
            val layerCount = parsed.layers.size.coerceAtLeast(1)
            val effectiveVectors = maxOf(parsed.elements.size, domVectorCount)
            val hasAlpha = parsed.backgroundMode == DocumentBackgroundMode.TRANSPARENT

            ExportValidationReport(
                isValid = true,
                format = ExportFormat.SVG,
                width = w,
                height = h,
                layerCount = layerCount,
                vectorCount = effectiveVectors,
                byteSize = file.length(),
                hasTransparency = hasAlpha,
                compatibilityNotes = "XML SVG 1.1 válido ($layerCount capas, $effectiveVectors vectores) • Compatible con Inkscape, Illustrator y Figma"
            )
        } catch (t: Throwable) {
            invalidReport(ExportFormat.SVG, file.length(), "XML SVG inválido: ${t.message}")
        }
    }

    private fun validatePng(file: File): ExportValidationReport {
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return invalidReport(ExportFormat.PNG, file.length(), "No se pudo leer el PNG.")

        val pngSig = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
        )
        if (bytes.size < 26 || !bytes.take(8).toByteArray().contentEquals(pngSig)) {
            return invalidReport(ExportFormat.PNG, file.length(), "Cabecera PNG inválida.")
        }

        // Read IHDR dimensions & color type (byte 25: 4 = GrayAlpha, 6 = RGBA)
        val ihdrW = ((bytes[16].toInt() and 0xFF) shl 24) or
            ((bytes[17].toInt() and 0xFF) shl 16) or
            ((bytes[18].toInt() and 0xFF) shl 8) or
            (bytes[19].toInt() and 0xFF)
        val ihdrH = ((bytes[20].toInt() and 0xFF) shl 24) or
            ((bytes[21].toInt() and 0xFF) shl 16) or
            ((bytes[22].toInt() and 0xFF) shl 8) or
            (bytes[23].toInt() and 0xFF)
        val ihdrColorType = bytes[25].toInt() and 0xFF

        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return invalidReport(ExportFormat.PNG, file.length(), "La imagen PNG no se pudo decodificar.")
        val w = if (bmp.width > 0) bmp.width else ihdrW
        val h = if (bmp.height > 0) bmp.height else ihdrH
        val hasAlpha = ihdrColorType == 4 || ihdrColorType == 6 || bmp.hasAlpha()
        bmp.recycle()

        return ExportValidationReport(
            isValid = w > 0 && h > 0,
            format = ExportFormat.PNG,
            width = w,
            height = h,
            layerCount = 1,
            vectorCount = 0,
            byteSize = file.length(),
            hasTransparency = hasAlpha,
            compatibilityNotes = "PNG sin pérdida válido (${w}×${h} px${if (hasAlpha) ", canal alfa" else ", opaco"}) • Apto para web y diseño"
        )
    }

    private fun validateJpg(file: File): ExportValidationReport {
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return invalidReport(ExportFormat.JPG, file.length(), "No se pudo leer el JPG.")

        if (bytes.size < 16 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            return invalidReport(ExportFormat.JPG, file.length(), "Cabecera JPEG (SOI FF D8) inválida.")
        }
        val hasEoi = bytes.size >= 4 &&
            bytes[bytes.size - 2] == 0xFF.toByte() &&
            bytes[bytes.size - 1] == 0xD9.toByte()
        if (!hasEoi) {
            return invalidReport(ExportFormat.JPG, file.length(), "Marcador final JPEG (EOI FF D9) ausente.")
        }

        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return invalidReport(ExportFormat.JPG, file.length(), "La imagen JPG no se pudo decodificar.")
        val w = bmp.width
        val h = bmp.height
        bmp.recycle()

        return ExportValidationReport(
            isValid = w > 0 && h > 0,
            format = ExportFormat.JPG,
            width = w,
            height = h,
            layerCount = 1,
            vectorCount = 0,
            byteSize = file.length(),
            hasTransparency = false,
            compatibilityNotes = "JPEG RGB estándar (${w}×${h} px) • Compatible con galerías, web e impresoras"
        )
    }

    private fun validatePdf(file: File): ExportValidationReport {
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return invalidReport(ExportFormat.PDF, file.length(), "No se pudo leer el PDF.")

        if (bytes.size < 32) {
            return invalidReport(ExportFormat.PDF, file.length(), "Archivo PDF demasiado pequeño.")
        }
        val header = String(bytes, 0, minOf(16, bytes.size), Charsets.US_ASCII)
        if (!header.startsWith("%PDF-")) {
            return invalidReport(ExportFormat.PDF, file.length(), "Cabecera %PDF- ausente.")
        }
        val tailStart = maxOf(0, bytes.size - 128)
        val tail = String(bytes, tailStart, bytes.size - tailStart, Charsets.US_ASCII)
        if (!tail.contains("%%EOF")) {
            return invalidReport(ExportFormat.PDF, file.length(), "Trailer %%EOF ausente; PDF incompleto.")
        }

        val asciiAll = String(bytes, Charsets.ISO_8859_1)
        var pageW = 0
        var pageH = 0
        var pageCount = 1

        val mediaMatch = Regex("""/MediaBox\s*\[\s*([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)\s*\]""")
            .find(asciiAll)
        if (mediaMatch != null) {
            val x0 = mediaMatch.groupValues[1].toFloatOrNull() ?: 0f
            val y0 = mediaMatch.groupValues[2].toFloatOrNull() ?: 0f
            val x1 = mediaMatch.groupValues[3].toFloatOrNull() ?: 0f
            val y1 = mediaMatch.groupValues[4].toFloatOrNull() ?: 0f
            pageW = (x1 - x0).roundToInt().coerceAtLeast(0)
            pageH = (y1 - y0).roundToInt().coerceAtLeast(0)
        }

        // Verify with Android's native PdfRenderer when supported by runtime
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                    if (pageW <= 0 || pageH <= 0) {
                        renderer.openPage(0).use { page ->
                            pageW = page.width
                            pageH = page.height
                        }
                    }
                }
            }
        }

        return ExportValidationReport(
            isValid = true,
            format = ExportFormat.PDF,
            width = pageW,
            height = pageH,
            layerCount = pageCount,
            vectorCount = 0,
            byteSize = file.length(),
            hasTransparency = false,
            compatibilityNotes = "PDF estándar válido (${if (pageW > 0) "${pageW}×${pageH} pt" else "$pageCount pág."}) • Listo para lectores PDF e impresión"
        )
    }

    private fun validateSpider(file: File): ExportValidationReport {
        return try {
            val json = file.readText(Charsets.UTF_8)
            val doc = SpiderJsonCodec.decode(json)
            val totalElements = doc.layers.sumOf { it.elements.size }
            ExportValidationReport(
                isValid = doc.widthPx > 0 && doc.heightPx > 0,
                format = ExportFormat.SPIDER,
                width = doc.widthPx,
                height = doc.heightPx,
                layerCount = doc.layers.size,
                vectorCount = totalElements,
                byteSize = file.length(),
                hasTransparency = doc.backgroundMode == DocumentBackgroundMode.TRANSPARENT,
                compatibilityNotes = "Proyecto .spider íntegro (${doc.layers.size} capas, $totalElements elementos)"
            )
        } catch (t: Throwable) {
            invalidReport(ExportFormat.SPIDER, file.length(), "JSON .spider inválido: ${t.message}")
        }
    }

    private fun invalidReport(format: ExportFormat, byteSize: Long, message: String): ExportValidationReport =
        ExportValidationReport(
            isValid = false,
            format = format,
            width = 0,
            height = 0,
            layerCount = 0,
            vectorCount = 0,
            byteSize = byteSize,
            hasTransparency = false,
            compatibilityNotes = message,
            errorMessage = message
        )
}
