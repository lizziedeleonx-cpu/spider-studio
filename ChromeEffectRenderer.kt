package com.example.engine

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.ChromeEffectConfig
import com.example.model.ChromeStyleType
import com.example.model.PaintType
import com.example.model.PathShape
import com.example.model.TextAlignMode
import com.example.model.TextShape
import com.example.model.VectorElement
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class ChromeGradientRamp(
    val offsets: FloatArray,
    val colors: IntArray
)

object ChromeEffectRenderer {

    fun effectiveChrome(element: VectorElement, inheritedChrome: ChromeEffectConfig?): ChromeEffectConfig? {
        return when {
            element.effects.chrome.enabled -> element.effects.chrome
            inheritedChrome != null && inheritedChrome.enabled -> inheritedChrome
            else -> null
        }
    }

    /**
     * Synthesizes real multi-stop metallic gradient colors from lighting, contrast,
     * brightness, gradient bias, tint color, and reflection intensity.
     */
    fun computeChromeRamp(config: ChromeEffectConfig, alpha: Float = 1f): ChromeGradientRamp {
        val intensity = config.overallIntensity.coerceIn(0f, 1f)
        val refl = (config.reflectionIntensity * (0.35f + 0.65f * intensity)).coerceIn(0.05f, 1f)
        val tint = config.metallicColorArgb.toInt()
        val bias = config.reflectiveGradientBias.coerceIn(0f, 1f)
        val shift = (bias - 0.5f) * 0.22f

        val rawStops: List<Pair<Float, Int>> = when (config.styleType) {
            ChromeStyleType.CLASSIC_CHROME -> listOf(
                0.00f to mixArgb(0xFFF8FAFC.toInt(), tint, 0.22f),
                (0.18f + shift * 0.5f).coerceIn(0.05f, 0.40f) to mixArgb(0xFFB8C4D4.toInt(), tint, 0.52f),
                (0.41f + shift).coerceIn(0.20f, 0.55f) to 0xFFFFFFFF.toInt(),
                (0.49f + shift).coerceIn(0.28f, 0.65f) to mixArgb(0xFF141824.toInt(), tint, 0.25f),
                (0.56f + shift).coerceIn(0.35f, 0.75f) to mixArgb(0xFF3E485B.toInt(), tint, 0.42f),
                (0.76f + shift * 0.4f).coerceIn(0.58f, 0.92f) to mixArgb(0xFFDCE4EF.toInt(), tint, 0.45f),
                1.00f to mixArgb(0xFF7B879B.toInt(), tint, 0.50f)
            )
            ChromeStyleType.LIQUID_CHROME -> {
                val freqScale = 0.7f + bias * 0.55f
                listOf(
                    0.00f to 0xFFFFFFFF.toInt(),
                    (0.12f * freqScale).coerceIn(0.05f, 0.22f) to mixArgb(0xFF1B202C.toInt(), tint, 0.28f),
                    (0.26f * freqScale).coerceIn(0.16f, 0.36f) to mixArgb(0xFFEDF2F8.toInt(), tint, 0.35f),
                    0.42f to mixArgb(0xFF0E1118.toInt(), tint, 0.22f),
                    0.56f to 0xFFFFFFFF.toInt(),
                    0.70f to mixArgb(0xFF334155.toInt(), tint, 0.45f),
                    0.85f to mixArgb(0xFFE2E8F0.toInt(), tint, 0.35f),
                    1.00f to mixArgb(0xFF1E293B.toInt(), tint, 0.35f)
                )
            }
            ChromeStyleType.THREE_D_CHROME -> listOf(
                0.00f to 0xFFFFFFFF.toInt(),
                (0.22f + shift * 0.4f).coerceIn(0.10f, 0.38f) to mixArgb(0xFFCBD5E1.toInt(), tint, 0.50f),
                (0.46f + shift).coerceIn(0.26f, 0.62f) to mixArgb(0xFF111827.toInt(), tint, 0.25f),
                (0.54f + shift).coerceIn(0.35f, 0.72f) to mixArgb(0xFF64748B.toInt(), tint, 0.45f),
                0.80f to mixArgb(0xFFF1F5F9.toInt(), tint, 0.30f),
                1.00f to mixArgb(0xFF1E293B.toInt(), tint, 0.35f)
            )
            ChromeStyleType.HOLOGRAPHIC_CHROME -> {
                val phase = (config.lightAngleDeg / 360f + bias * 0.35f) % 1f
                listOf(
                    0.00f to mixArgb(hslToArgb((phase * 360f) % 360f, 0.92f, 0.72f), tint, 0.25f),
                    0.20f to mixArgb(hslToArgb(((phase + 0.18f) * 360f) % 360f, 0.95f, 0.65f), 0xFFFFFFFF.toInt(), 0.22f),
                    0.38f to mixArgb(hslToArgb(((phase + 0.38f) * 360f) % 360f, 0.85f, 0.38f), tint, 0.25f),
                    0.50f to 0xFFFFFFFF.toInt(),
                    0.66f to mixArgb(hslToArgb(((phase + 0.56f) * 360f) % 360f, 0.94f, 0.66f), tint, 0.20f),
                    0.84f to mixArgb(hslToArgb(((phase + 0.78f) * 360f) % 360f, 0.90f, 0.68f), 0xFFF8FAFC.toInt(), 0.20f),
                    1.00f to mixArgb(hslToArgb(((phase + 0.95f) * 360f) % 360f, 0.85f, 0.52f), tint, 0.28f)
                )
            }
            ChromeStyleType.GOLD_CHROME -> listOf(
                0.00f to 0xFFFFFBEB.toInt(),
                (0.18f + shift * 0.4f).coerceIn(0.08f, 0.36f) to mixArgb(0xFFFDE047.toInt(), tint, 0.40f),
                (0.42f + shift).coerceIn(0.24f, 0.58f) to 0xFFFFFFFF.toInt(),
                (0.50f + shift).coerceIn(0.32f, 0.68f) to mixArgb(0xFF5C3404.toInt(), tint, 0.28f),
                (0.66f + shift * 0.3f).coerceIn(0.45f, 0.85f) to mixArgb(0xFFD97706.toInt(), tint, 0.42f),
                0.86f to mixArgb(0xFFFEF08A.toInt(), tint, 0.35f),
                1.00f to mixArgb(0xFF92400E.toInt(), tint, 0.40f)
            )
            ChromeStyleType.ROSE_GOLD_CHROME -> listOf(
                0.00f to 0xFFFFF1F5.toInt(),
                (0.20f + shift * 0.4f).coerceIn(0.08f, 0.36f) to mixArgb(0xFFFBCFE8.toInt(), tint, 0.45f),
                (0.42f + shift).coerceIn(0.24f, 0.58f) to 0xFFFFFFFF.toInt(),
                (0.50f + shift).coerceIn(0.32f, 0.68f) to mixArgb(0xFF58222B.toInt(), tint, 0.25f),
                0.68f to mixArgb(0xFFD9847B.toInt(), tint, 0.45f),
                0.86f to mixArgb(0xFFFDE2E4.toInt(), tint, 0.32f),
                1.00f to mixArgb(0xFF9D4E55.toInt(), tint, 0.38f)
            )
        }

        val sorted = rawStops.sortedBy { it.first }
        val offsets = FloatArray(sorted.size)
        val colors = IntArray(sorted.size)
        val outAlpha = (alpha * (0.25f + 0.75f * intensity)).coerceIn(0f, 1f)

        for (i in sorted.indices) {
            offsets[i] = sorted[i].first.coerceIn(0f, 1f)
            val baseC = sorted[i].second
            val graded = applyToneAndSpecular(
                argb = baseC,
                brightness = config.brightness,
                contrast = config.contrast,
                reflectionIntensity = refl
            )
            colors[i] = ((outAlpha * 255f).toInt().coerceIn(0, 255) shl 24) or (graded and 0x00FFFFFF)
        }
        return ChromeGradientRamp(offsets, colors)
    }

    fun buildLinearChromeShader(
        config: ChromeEffectConfig,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        alpha: Float = 1f
    ): LinearGradient {
        val ramp = computeChromeRamp(config, alpha)
        val cx = x + w / 2f
        val cy = y + h / 2f
        val rad = Math.toRadians(config.lightAngleDeg.toDouble())
        val dx = (cos(rad) * max(8f, w) * 0.55f).toFloat()
        val dy = (sin(rad) * max(8f, h) * 0.55f).toFloat()
        val tile = if (config.styleType == ChromeStyleType.LIQUID_CHROME) Shader.TileMode.MIRROR else Shader.TileMode.CLAMP
        return LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, ramp.colors, ramp.offsets, tile)
    }

    fun drawChromeForPath(
        canvas: Canvas,
        path: Path,
        element: VectorElement,
        chrome: ChromeEffectConfig,
        alpha: Float
    ) {
        val intensity = chrome.overallIntensity.coerceIn(0f, 1f)
        if (intensity <= 0.01f) return

        val rad = Math.toRadians(chrome.lightAngleDeg.toDouble())
        val lightX = cos(rad).toFloat()
        val lightY = sin(rad).toFloat()

        // 1. Directional Shadow from Chrome Lighting
        val effShadow = (chrome.shadowIntensity * intensity).coerceIn(0f, 1f)
        if (effShadow > 0.02f) {
            val shadowDist = 6f + chrome.depth * 18f
            val shadowBlur = 8f + chrome.depth * 14f
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = if (element.fill.type != PaintType.NONE || !element.stroke.enabled) Paint.Style.FILL else Paint.Style.STROKE
                strokeWidth = max(2f, element.stroke.width)
                color = applyAlphaInt(0xFF000000.toInt(), effShadow * 0.72f * alpha)
                maskFilter = BlurMaskFilter(shadowBlur, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.save()
            canvas.translate(-lightX * shadowDist, -lightY * shadowDist + 4f)
            canvas.drawPath(path, sp)
            canvas.restore()
        }

        // 2. Stepped 3D Metallic Extrusion Volume
        val effDepth = chrome.depth * intensity
        if (effDepth > 0.03f) {
            val maxExtrudePx = (min(max(element.width, element.height) * 0.09f, 26f) * effDepth).coerceAtLeast(1.5f)
            val steps = (effDepth * 12f).roundToInt().coerceIn(2, 12)
            val darkWallColor = applyToneAndSpecular(
                mixArgb(0xFF0F131E.toInt(), chrome.metallicColorArgb.toInt(), 0.42f),
                chrome.brightness * 0.4f,
                chrome.contrast,
                chrome.reflectionIntensity * 0.5f
            )
            val midWallColor = applyToneAndSpecular(
                mixArgb(0xFF475569.toInt(), chrome.metallicColorArgb.toInt(), 0.65f),
                chrome.brightness * 0.5f,
                chrome.contrast,
                chrome.reflectionIntensity * 0.7f
            )
            val isFillMode = element.fill.type != PaintType.NONE || element !is PathShape || element.isClosed
            for (step in steps downTo 1) {
                val frac = step.toFloat() / steps.toFloat()
                val ox = -lightX * maxExtrudePx * frac
                val oy = -lightY * maxExtrudePx * frac + (maxExtrudePx * 0.25f * frac)
                val wallColor = mixArgb(midWallColor, darkWallColor, frac)
                val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = if (isFillMode) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                    strokeWidth = if (isFillMode) 1.5f else max(2f, element.stroke.width)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = applyAlphaInt(wallColor, alpha * (0.95f - frac * 0.15f))
                }
                canvas.save()
                canvas.translate(ox, oy)
                canvas.drawPath(path, wp)
                canvas.restore()
            }
        }

        // 3. Base Metallic Reflection Fill
        val hasFill = element.fill.type != PaintType.NONE || element !is PathShape || element.isClosed
        if (hasFill) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = buildLinearChromeShader(chrome, element.x, element.y, element.width, element.height, alpha)
                if (element.effects.blurRadius > 0.5f) {
                    maskFilter = BlurMaskFilter(element.effects.blurRadius, BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.drawPath(path, fillPaint)

            // Liquid Chrome / Specular Hotspot Radial Gleam
            if (chrome.reflectionIntensity > 0.15f) {
                val specX = element.x + element.width * (0.5f + lightX * 0.26f)
                val specY = element.y + element.height * (0.5f + lightY * 0.26f)
                val specR = max(20f, max(element.width, element.height) * 0.62f)
                val specAlpha = (chrome.reflectionIntensity * intensity * 0.42f * alpha).coerceIn(0f, 0.55f)
                val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    shader = RadialGradient(
                        specX, specY, specR,
                        intArrayOf(
                            applyAlphaInt(0xFFFFFFFF.toInt(), specAlpha),
                            applyAlphaInt(chrome.metallicColorArgb.toInt(), specAlpha * 0.28f),
                            0x00000000
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawPath(path, specPaint)
            }
        }

        // 4. Bevel & Relief Specular Rim + Crevice
        val effBevel = chrome.bevelRelief * intensity
        if (effBevel > 0.03f) {
            val rimWidth = (1.6f + effBevel * 5.2f).coerceIn(1.5f, 9f)
            // Dark bottom/opposite bevel edge
            val darkRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = rimWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = applyAlphaInt(0xFF0A0D14.toInt(), effBevel * 0.68f * alpha)
            }
            canvas.save()
            canvas.translate(-lightX * (rimWidth * 0.28f), -lightY * (rimWidth * 0.28f))
            canvas.drawPath(path, darkRim)
            canvas.restore()

            // Crisp bright specular highlight edge
            val brightRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = (rimWidth * 0.72f).coerceAtLeast(1.2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = applyAlphaInt(
                    mixArgb(0xFFFFFFFF.toInt(), chrome.metallicColorArgb.toInt(), 0.2f),
                    (0.45f + 0.55f * effBevel) * alpha
                )
            }
            canvas.save()
            canvas.translate(lightX * (rimWidth * 0.22f), lightY * (rimWidth * 0.22f))
            canvas.drawPath(path, brightRim)
            canvas.restore()
        }

        // 5. Chrome Stroke / Path Ribbon when stroke-only or stroke enabled
        if (element.stroke.enabled && element.stroke.width > 0f) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = element.stroke.width
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                shader = buildLinearChromeShader(chrome, element.x, element.y, element.width, element.height, alpha * element.stroke.opacity)
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    fun drawChromeText(
        canvas: Canvas,
        element: TextShape,
        tf: Typeface,
        displayText: String,
        chrome: ChromeEffectConfig,
        alpha: Float,
        arcPath: Path? = null
    ) {
        val intensity = chrome.overallIntensity.coerceIn(0f, 1f)
        val rad = Math.toRadians(chrome.lightAngleDeg.toDouble())
        val lightX = cos(rad).toFloat()
        val lightY = sin(rad).toFloat()

        fun buildBaseTextPaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = element.fontSize
            letterSpacing = element.letterSpacing
            isUnderlineText = element.isUnderline
            isStrikeThruText = element.isStrikethrough
            if (arcPath != null) {
                textAlign = when (element.alignment) {
                    TextAlignMode.LEFT -> Paint.Align.LEFT
                    TextAlignMode.CENTER -> Paint.Align.CENTER
                    TextAlignMode.RIGHT -> Paint.Align.RIGHT
                }
            }
        }

        val layoutAlign = when (element.alignment) {
            TextAlignMode.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignMode.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignMode.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val textWidth = element.width.toInt().coerceAtLeast(24)

        fun drawTextPass(tp: TextPaint, offsetX: Float = 0f, offsetY: Float = 0f) {
            canvas.save()
            if (arcPath != null) {
                canvas.translate(offsetX, offsetY)
                canvas.drawTextOnPath(displayText.replace("\n", " "), arcPath, 0f, 0f, tp)
            } else {
                canvas.translate(element.x + offsetX, element.y + offsetY)
                val layout = StaticLayout.Builder
                    .obtain(displayText, 0, displayText.length, tp, textWidth)
                    .setAlignment(layoutAlign)
                    .setLineSpacing(0f, element.lineHeightMultiplier)
                    .build()
                layout.draw(canvas)
            }
            canvas.restore()
        }

        // 1. Chrome Drop Shadow
        val effShadow = (chrome.shadowIntensity * intensity).coerceIn(0f, 1f)
        if (effShadow > 0.02f) {
            val shadowDist = 4f + chrome.depth * 14f
            val shadowPaint = buildBaseTextPaint().apply {
                color = applyAlphaInt(0xFF000000.toInt(), effShadow * 0.78f * alpha)
                setShadowLayer(
                    (6f + chrome.depth * 12f).coerceAtLeast(2f),
                    0f, 0f,
                    applyAlphaInt(0xFF000000.toInt(), effShadow * 0.72f * alpha)
                )
            }
            drawTextPass(shadowPaint, -lightX * shadowDist, -lightY * shadowDist + 3f)
        }

        // 2. Stepped 3D Typography Extrusion
        val effDepth = chrome.depth * intensity
        if (effDepth > 0.03f) {
            val maxExtrudePx = (min(element.fontSize * 0.18f, 20f) * effDepth).coerceAtLeast(1.5f)
            val steps = (effDepth * 10f).roundToInt().coerceIn(2, 10)
            val darkWall = applyToneAndSpecular(
                mixArgb(0xFF0F131E.toInt(), chrome.metallicColorArgb.toInt(), 0.40f),
                chrome.brightness * 0.4f, chrome.contrast, chrome.reflectionIntensity * 0.5f
            )
            val midWall = applyToneAndSpecular(
                mixArgb(0xFF475569.toInt(), chrome.metallicColorArgb.toInt(), 0.62f),
                chrome.brightness * 0.5f, chrome.contrast, chrome.reflectionIntensity * 0.7f
            )
            for (step in steps downTo 1) {
                val frac = step.toFloat() / steps.toFloat()
                val wallPaint = buildBaseTextPaint().apply {
                    style = Paint.Style.FILL_AND_STROKE
                    strokeWidth = max(1.2f, element.fontSize * 0.02f)
                    strokeJoin = Paint.Join.ROUND
                    color = applyAlphaInt(mixArgb(midWall, darkWall, frac), alpha * 0.94f)
                }
                drawTextPass(wallPaint, -lightX * maxExtrudePx * frac, -lightY * maxExtrudePx * frac + maxExtrudePx * 0.25f * frac)
            }
        }

        // 3. Bevel Rim Crevice & Specular Rim
        val effBevel = chrome.bevelRelief * intensity
        if (effBevel > 0.03f) {
            val rimW = max(1.2f, element.fontSize * 0.035f * (0.5f + effBevel))
            val darkRim = buildBaseTextPaint().apply {
                style = Paint.Style.STROKE
                strokeWidth = rimW
                strokeJoin = Paint.Join.ROUND
                color = applyAlphaInt(0xFF0A0D14.toInt(), effBevel * 0.65f * alpha)
            }
            drawTextPass(darkRim, -lightX * 1.4f, -lightY * 1.4f)

            val lightRim = buildBaseTextPaint().apply {
                style = Paint.Style.STROKE
                strokeWidth = rimW * 0.75f
                strokeJoin = Paint.Join.ROUND
                color = applyAlphaInt(0xFFFFFFFF.toInt(), (0.4f + 0.55f * effBevel) * alpha)
            }
            drawTextPass(lightRim, lightX * 1.2f, lightY * 1.2f)
        }

        // 4. Primary Chrome Gradient Text Face
        val faceX = if (arcPath != null) element.x else 0f
        val faceY = if (arcPath != null) element.y else 0f
        val facePaint = buildBaseTextPaint().apply {
            shader = buildLinearChromeShader(chrome, faceX, faceY, element.width, max(element.fontSize * 1.3f, element.height), alpha)
            if (element.effects.blurRadius > 0.5f) {
                maskFilter = BlurMaskFilter(element.effects.blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        }
        drawTextPass(facePaint)

        // Optional explicit stroke overlay on text
        if (element.stroke.enabled && element.stroke.width > 0f) {
            val strokePaint = buildBaseTextPaint().apply {
                style = Paint.Style.STROKE
                strokeWidth = element.stroke.width
                strokeJoin = Paint.Join.ROUND
                color = applyAlphaInt(element.stroke.colorArgb.toInt(), element.stroke.opacity * alpha)
            }
            drawTextPass(strokePaint)
        }
    }

    private fun applyToneAndSpecular(
        argb: Int,
        brightness: Float,
        contrast: Float,
        reflectionIntensity: Float
    ): Int {
        var r = ((argb ushr 16) and 0xFF) / 255f
        var g = ((argb ushr 8) and 0xFF) / 255f
        var b = (argb and 0xFF) / 255f

        // Brightness
        r = (r + brightness).coerceIn(0f, 1f)
        g = (g + brightness).coerceIn(0f, 1f)
        b = (b + brightness).coerceIn(0f, 1f)

        // Contrast around 0.5
        val c = contrast.coerceIn(0.4f, 2.5f)
        r = ((r - 0.5f) * c + 0.5f).coerceIn(0f, 1f)
        g = ((g - 0.5f) * c + 0.5f).coerceIn(0f, 1f)
        b = ((b - 0.5f) * c + 0.5f).coerceIn(0f, 1f)

        // Specular boost on bright highlights
        val lum = 0.299f * r + 0.587f * g + 0.114f * b
        if (lum > 0.65f) {
            val boost = (lum - 0.65f) * reflectionIntensity * 0.75f
            r = (r + boost).coerceIn(0f, 1f)
            g = (g + boost).coerceIn(0f, 1f)
            b = (b + boost).coerceIn(0f, 1f)
        }

        val ri = (r * 255f).roundToInt().coerceIn(0, 255)
        val gi = (g * 255f).roundToInt().coerceIn(0, 255)
        val bi = (b * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun mixArgb(c1: Int, c2: Int, t: Float): Int {
        val ratio = t.coerceIn(0f, 1f)
        val a = (((c1 ushr 24) and 0xFF) * (1f - ratio) + ((c2 ushr 24) and 0xFF) * ratio).roundToInt()
        val r = (((c1 ushr 16) and 0xFF) * (1f - ratio) + ((c2 ushr 16) and 0xFF) * ratio).roundToInt()
        val g = (((c1 ushr 8) and 0xFF) * (1f - ratio) + ((c2 ushr 8) and 0xFF) * ratio).roundToInt()
        val b = ((c1 and 0xFF) * (1f - ratio) + (c2 and 0xFF) * ratio).roundToInt()
        return ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    private fun hslToArgb(hDeg: Float, s: Float, l: Float): Int {
        val h = ((hDeg % 360f) + 360f) % 360f
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (rf, gf, bf) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((rf + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((gf + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((bf + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun applyAlphaInt(argb: Int, alpha: Float): Int {
        val a = (((argb ushr 24) and 0xFF) * alpha).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (argb and 0x00FFFFFF)
    }
}
