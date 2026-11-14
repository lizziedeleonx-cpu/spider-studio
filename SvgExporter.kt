package com.example.engine

import android.util.Base64
import com.example.model.BlendModeStyle
import com.example.model.BrushPreset
import com.example.model.ChromeEffectConfig
import com.example.model.ChromeStyleType
import com.example.model.DocumentBackgroundMode
import com.example.model.EllipseShape
import com.example.model.GradientStop
import com.example.model.GroupShape
import com.example.model.ImageShape
import com.example.model.PaintStyle
import com.example.model.PaintType
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.StrokeCapStyle
import com.example.model.StrokeJoinStyle
import com.example.model.TextAlignMode
import com.example.model.TextShape
import com.example.model.VectorElement
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object SvgExporter {

    fun exportToSvg(
        doc: SpiderDocument,
        selectedIds: Set<String> = emptySet(),
        selectedOnly: Boolean = false
    ): String {
        val visibleElements = doc.layers
            .filter { it.isVisible }
            .flatMap { layer -> layer.elements.filter { it.isVisible } }

        val targetElements = if (selectedOnly && selectedIds.isNotEmpty()) {
            visibleElements.filter { it.id in selectedIds }
        } else null

        val bounds = if (targetElements != null && targetElements.isNotEmpty()) {
            VectorMath.computeCombinedBounds(targetElements)
        } else null

        val vbX = bounds?.left ?: 0f
        val vbY = bounds?.top ?: 0f
        val vbW = bounds?.width?.coerceAtLeast(1f) ?: doc.widthPx.toFloat()
        val vbH = bounds?.height?.coerceAtLeast(1f) ?: doc.heightPx.toFloat()

        val defs = StringBuilder()
        val body = StringBuilder()
        var defCounter = 0
        var nodeCounter = 0
        val usedIds = HashSet<String>()

        fun nextDefId(prefix: String): String = "${prefix}_${++defCounter}"

        fun uniqueXmlId(rawPrefix: String): String {
            val base = rawPrefix
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .trim('_')
                .ifEmpty { "obj" }
                .let { if (it.first().isDigit()) "n_$it" else it }
            var cand = base
            while (!usedIds.add(cand)) {
                cand = "${base}_${++nodeCounter}"
            }
            return cand
        }

        fun resolveFillAttr(paint: PaintStyle): String {
            val stops = if (paint.stops.size >= 2) {
                paint.stops.sortedBy { it.offset }
            } else {
                listOf(
                    GradientStop(0f, paint.colorArgb),
                    GradientStop(1f, paint.secondaryColorArgb)
                )
            }
            return when (paint.type) {
                PaintType.NONE -> "none"
                PaintType.SOLID -> argbToHexRgb(paint.colorArgb)
                PaintType.LINEAR_GRADIENT -> {
                    val id = nextDefId("lg")
                    val rad = Math.toRadians(paint.gradientAngleDeg.toDouble())
                    val x1 = 50 - (cos(rad) * 50)
                    val y1 = 50 - (sin(rad) * 50)
                    val x2 = 50 + (cos(rad) * 50)
                    val y2 = 50 + (sin(rad) * 50)
                    defs.append(
                        String.format(
                            Locale.US,
                            """<linearGradient id="%s" x1="%.1f%%" y1="%.1f%%" x2="%.1f%%" y2="%.1f%%">""",
                            id, x1, y1, x2, y2
                        )
                    )
                    stops.forEach { st ->
                        defs.append(
                            String.format(
                                Locale.US,
                                """<stop offset="%.1f%%" stop-color="%s" stop-opacity="%.3f"/>""",
                                st.offset * 100f, argbToHexRgb(st.colorArgb), argbAlpha(st.colorArgb)
                            )
                        )
                    }
                    defs.append("</linearGradient>")
                    "url(#$id)"
                }
                PaintType.RADIAL_GRADIENT, PaintType.SWEEP_GRADIENT -> {
                    val id = nextDefId("rg")
                    defs.append(
                        String.format(
                            Locale.US,
                            """<radialGradient id="%s" cx="%.1f%%" cy="%.1f%%" fx="%.1f%%" fy="%.1f%%" r="%.1f%%">""",
                            id,
                            paint.gradientCenterX * 100f,
                            paint.gradientCenterY * 100f,
                            paint.gradientCenterX * 100f,
                            paint.gradientCenterY * 100f,
                            paint.radialRadiusFactor * 100f
                        )
                    )
                    stops.forEach { st ->
                        defs.append(
                            String.format(
                                Locale.US,
                                """<stop offset="%.1f%%" stop-color="%s" stop-opacity="%.3f"/>""",
                                st.offset * 100f, argbToHexRgb(st.colorArgb), argbAlpha(st.colorArgb)
                            )
                        )
                    }
                    defs.append("</radialGradient>")
                    "url(#$id)"
                }
            }
        }

        fun resolveChromeFillAttr(chrome: ChromeEffectConfig): String {
            val id = nextDefId("chg")
            val rad = Math.toRadians(chrome.lightAngleDeg.toDouble())
            val x1 = 50 - (cos(rad) * 50)
            val y1 = 50 - (sin(rad) * 50)
            val x2 = 50 + (cos(rad) * 50)
            val y2 = 50 + (sin(rad) * 50)
            val spread = if (chrome.styleType == ChromeStyleType.LIQUID_CHROME) """ spreadMethod="reflect"""" else ""
            val ramp = ChromeEffectRenderer.computeChromeRamp(chrome, 1f)
            defs.append(
                String.format(
                    Locale.US,
                    """<linearGradient id="%s" x1="%.1f%%" y1="%.1f%%" x2="%.1f%%" y2="%.1f%%"%s>""",
                    id, x1, y1, x2, y2, spread
                )
            )
            ramp.offsets.forEachIndexed { idx, off ->
                val col = ramp.colors[idx].toLong() and 0xFFFFFFFFL
                defs.append(
                    String.format(
                        Locale.US,
                        """<stop offset="%.1f%%" stop-color="%s" stop-opacity="%.3f"/>""",
                        off * 100f, argbToHexRgb(col), argbAlpha(col)
                    )
                )
            }
            defs.append("</linearGradient>")
            return "url(#$id)"
        }

        fun resolveFilterAttr(el: VectorElement, activeChrome: ChromeEffectConfig? = null): String {
            val hasChromeShadow = activeChrome != null && activeChrome.shadowIntensity * activeChrome.overallIntensity > 0.03f
            if (!el.effects.shadowEnabled && !el.effects.glowEnabled && el.effects.blurRadius <= 0.5f && !hasChromeShadow) return ""
            val id = nextDefId("flt")
            defs.append("""<filter id="$id" x="-30%" y="-30%" width="160%" height="160%">""")
            when {
                el.effects.blurRadius > 0.5f -> defs.append(
                    String.format(
                        Locale.US,
                        """<feGaussianBlur stdDeviation="%.2f"/>""",
                        el.effects.blurRadius / 2f
                    )
                )
                el.effects.glowEnabled -> defs.append(
                    String.format(
                        Locale.US,
                        """<feDropShadow dx="0" dy="0" stdDeviation="%.2f" flood-color="%s" flood-opacity="0.85"/>""",
                        el.effects.glowRadius / 2f,
                        argbToHexRgb(el.effects.glowColorArgb)
                    )
                )
                hasChromeShadow && activeChrome != null -> {
                    val rad = Math.toRadians(activeChrome.lightAngleDeg.toDouble())
                    val dist = 6f + activeChrome.depth * 18f
                    val dx = -cos(rad).toFloat() * dist
                    val dy = -sin(rad).toFloat() * dist + 4f
                    val blur = (8f + activeChrome.depth * 14f) / 2f
                    val op = (activeChrome.shadowIntensity * activeChrome.overallIntensity * 0.72f).coerceIn(0f, 1f)
                    defs.append(
                        String.format(
                            Locale.US,
                            """<feDropShadow dx="%.2f" dy="%.2f" stdDeviation="%.2f" flood-color="#000000" flood-opacity="%.3f"/>""",
                            dx, dy, blur, op
                        )
                    )
                }
                el.effects.shadowEnabled -> defs.append(
                    String.format(
                        Locale.US,
                        """<feDropShadow dx="%.2f" dy="%.2f" stdDeviation="%.2f" flood-color="%s" flood-opacity="%.3f"/>""",
                        el.effects.shadowOffsetX,
                        el.effects.shadowOffsetY,
                        el.effects.shadowBlur / 2f,
                        argbToHexRgb(el.effects.shadowColorArgb),
                        argbAlpha(el.effects.shadowColorArgb) * el.effects.shadowOpacity
                    )
                )
            }
            defs.append("</filter>")
            return """ filter="url(#$id)""""
        }

        fun resolveTransformAttr(el: VectorElement): String {
            return if (el.rotationDeg != 0f) {
                String.format(
                    Locale.US,
                    """ transform="rotate(%.2f %.2f %.2f)"""",
                    el.rotationDeg,
                    el.x + el.width / 2f,
                    el.y + el.height / 2f
                )
            } else ""
        }

        fun resolveStyleAttr(el: VectorElement): String {
            val parts = mutableListOf<String>()
            val cssBlend = when (el.effects.blendMode) {
                BlendModeStyle.NORMAL -> null
                BlendModeStyle.MULTIPLY -> "multiply"
                BlendModeStyle.SCREEN -> "screen"
                BlendModeStyle.OVERLAY -> "overlay"
                BlendModeStyle.DARKEN -> "darken"
                BlendModeStyle.LIGHTEN -> "lighten"
            }
            if (cssBlend != null) parts.add("mix-blend-mode:$cssBlend")
            if (!el.isVisible) parts.add("display:none")
            return if (parts.isEmpty()) "" else """ style="${parts.joinToString(";")}""""
        }

        fun commonAttrs(
            el: VectorElement,
            layerOpacity: Float,
            activeChrome: ChromeEffectConfig? = null
        ): String {
            val fillStr = if (activeChrome != null) {
                resolveChromeFillAttr(activeChrome)
            } else {
                resolveFillAttr(el.fill)
            }
            val fillOp = when {
                activeChrome != null -> (0.25f + 0.75f * activeChrome.overallIntensity).coerceIn(0f, 1f)
                el.fill.type == PaintType.NONE -> 0f
                el.fill.type == PaintType.SOLID -> (el.fill.opacity * argbAlpha(el.fill.colorArgb)).coerceIn(0f, 1f)
                else -> el.fill.opacity.coerceIn(0f, 1f)
            }
            val strokeAttrs = if (el.stroke.enabled && el.stroke.width > 0f) {
                val cap = when (el.stroke.cap) {
                    StrokeCapStyle.BUTT -> "butt"
                    StrokeCapStyle.ROUND -> "round"
                    StrokeCapStyle.SQUARE -> "square"
                }
                val join = when (el.stroke.join) {
                    StrokeJoinStyle.MITER -> "miter"
                    StrokeJoinStyle.ROUND -> "round"
                    StrokeJoinStyle.BEVEL -> "bevel"
                }
                val dash = if (el.stroke.dashed) {
                    String.format(Locale.US, """ stroke-dasharray="%.1f %.1f"""", el.stroke.dashLength, el.stroke.gapLength)
                } else ""
                String.format(
                    Locale.US,
                    """ stroke="%s" stroke-width="%.2f" stroke-opacity="%.3f" stroke-linecap="%s" stroke-linejoin="%s"%s""",
                    if (activeChrome != null) "#FFFFFF" else argbToHexRgb(el.stroke.colorArgb),
                    el.stroke.width,
                    el.stroke.opacity * argbAlpha(el.stroke.colorArgb),
                    cap,
                    join,
                    dash
                )
            } else if (activeChrome != null && activeChrome.bevelRelief * activeChrome.overallIntensity > 0.05f) {
                val bevelW = (1.2f + activeChrome.bevelRelief * 2.5f).coerceIn(1f, 5f)
                String.format(
                    Locale.US,
                    """ stroke="#FFFFFF" stroke-width="%.2f" stroke-opacity="%.3f" stroke-linecap="round" stroke-linejoin="round"""",
                    bevelW,
                    (0.35f + 0.45f * activeChrome.bevelRelief * activeChrome.overallIntensity).coerceIn(0f, 1f)
                )
            } else {
                """ stroke="none""""
            }
            val transform = resolveTransformAttr(el)
            val opacity = (el.opacity * layerOpacity).coerceIn(0f, 1f)
            val filterAttr = resolveFilterAttr(el, activeChrome)
            val styleAttr = resolveStyleAttr(el)
            return String.format(
                Locale.US,
                """ fill="%s" fill-opacity="%.3f"%s opacity="%.3f"%s%s%s""",
                fillStr, fillOp, strokeAttrs, opacity, transform, filterAttr, styleAttr
            )
        }

        fun writeRawGeometryTag(el: VectorElement, out: StringBuilder, includeTransform: Boolean = false) {
            val t = if (includeTransform) resolveTransformAttr(el) else ""
            when (el) {
                is RectShape -> out.append(
                    String.format(
                        Locale.US,
                        """<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" rx="%.2f" ry="%.2f"%s/>""",
                        el.x, el.y, el.width, el.height, el.cornerRadius, el.cornerRadius, t
                    )
                )
                is EllipseShape -> out.append(
                    String.format(
                        Locale.US,
                        """<ellipse cx="%.2f" cy="%.2f" rx="%.2f" ry="%.2f"%s/>""",
                        el.x + el.width / 2f, el.y + el.height / 2f, el.width / 2f, el.height / 2f, t
                    )
                )
                is PolygonShape -> {
                    val pts = VectorMath.buildPolygonPoints(el.x, el.y, el.width, el.height, el.sides)
                    val ptsStr = pts.joinToString(" ") { String.format(Locale.US, "%.2f,%.2f", it.x, it.y) }
                    out.append("""<polygon points="$ptsStr"$t/>""")
                }
                is StarShape -> {
                    val pts = VectorMath.buildStarPoints(el.x, el.y, el.width, el.height, el.points, el.innerRadiusRatio)
                    val ptsStr = pts.joinToString(" ") { String.format(Locale.US, "%.2f,%.2f", it.x, it.y) }
                    out.append("""<polygon points="$ptsStr"$t/>""")
                }
                is PathShape -> {
                    val d = buildPathDataString(el)
                    out.append("""<path d="$d"$t/>""")
                }
                else -> out.append(
                    String.format(Locale.US, """<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f"%s/>""", el.x, el.y, el.width, el.height, t)
                )
            }
        }

        fun writeElement(
            el: VectorElement,
            layerOpacity: Float,
            out: StringBuilder,
            indent: String = "    ",
            inheritedChrome: ChromeEffectConfig? = null
        ) {
            if (!el.isVisible && selectedOnly) return
            val activeChrome = ChromeEffectRenderer.effectiveChrome(el, inheritedChrome)
            val safeId = uniqueXmlId(el.name)
            val safeLabel = escapeXml(el.name)
            val labelAttrs = """id="$safeId" inkscape:label="$safeLabel" data-name="$safeLabel""""

            // Optional 3D Chrome extrusion backing geometry for non-group elements
            if (activeChrome != null && el !is GroupShape && el !is ImageShape) {
                val effDepth = activeChrome.depth * activeChrome.overallIntensity
                if (effDepth > 0.08f) {
                    val rad = Math.toRadians(activeChrome.lightAngleDeg.toDouble())
                    val dist = (min(Math.max(el.width, el.height) * 0.08f, 22f) * effDepth).coerceAtLeast(2f)
                    val dx = -cos(rad).toFloat() * dist
                    val dy = -sin(rad).toFloat() * dist + dist * 0.2f
                    val wallTag = StringBuilder()
                    writeRawGeometryTag(el, wallTag, includeTransform = true)
                    out.append(
                        String.format(
                            Locale.US,
                            """%s<g transform="translate(%.2f %.2f)" fill="#1F2937" stroke="#0F172A" stroke-width="1.2" opacity="%.3f">%s</g>%n""",
                            indent, dx, dy, (0.85f * el.opacity * layerOpacity).coerceIn(0f, 1f), wallTag.toString()
                        )
                    )
                }
            }

            when (el) {
                is RectShape -> {
                    out.append(
                        String.format(
                            Locale.US,
                            """%s<rect %s x="%.2f" y="%.2f" width="%.2f" height="%.2f" rx="%.2f" ry="%.2f"%s/>%n""",
                            indent, labelAttrs, el.x, el.y, el.width, el.height,
                            el.cornerRadius, el.cornerRadius, commonAttrs(el, layerOpacity, activeChrome)
                        )
                    )
                }
                is EllipseShape -> {
                    out.append(
                        String.format(
                            Locale.US,
                            """%s<ellipse %s cx="%.2f" cy="%.2f" rx="%.2f" ry="%.2f"%s/>%n""",
                            indent, labelAttrs, el.x + el.width / 2f, el.y + el.height / 2f,
                            el.width / 2f, el.height / 2f, commonAttrs(el, layerOpacity, activeChrome)
                        )
                    )
                }
                is PolygonShape -> {
                    val pts = VectorMath.buildPolygonPoints(el.x, el.y, el.width, el.height, el.sides)
                    val ptsStr = pts.joinToString(" ") { String.format(Locale.US, "%.2f,%.2f", it.x, it.y) }
                    out.append("""$indent<polygon $labelAttrs points="$ptsStr"${commonAttrs(el, layerOpacity, activeChrome)}/>""" + "\n")
                }
                is StarShape -> {
                    val pts = VectorMath.buildStarPoints(el.x, el.y, el.width, el.height, el.points, el.innerRadiusRatio)
                    val ptsStr = pts.joinToString(" ") { String.format(Locale.US, "%.2f,%.2f", it.x, it.y) }
                    out.append("""$indent<polygon $labelAttrs points="$ptsStr"${commonAttrs(el, layerOpacity, activeChrome)}/>""" + "\n")
                }
                is PathShape -> {
                    if (el.isBrushStroke && el.pressureWidths.size >= 2 &&
                        el.brushPreset != BrushPreset.MARKER_FLAT && el.brushPreset != BrushPreset.ARTISTIC_DASH
                    ) {
                        val ribbon = VectorMath.buildBrushRibbonOutline(el)
                        if (ribbon.size >= 3) {
                            val d = buildString {
                                append(String.format(Locale.US, "M %.2f %.2f", ribbon[0].x, ribbon[0].y))
                                for (i in 1 until ribbon.size) {
                                    append(String.format(Locale.US, " L %.2f %.2f", ribbon[i].x, ribbon[i].y))
                                }
                                append(" Z")
                            }
                            val fillVal = if (activeChrome != null) resolveChromeFillAttr(activeChrome) else argbToHexRgb(el.stroke.colorArgb)
                            out.append(
                                String.format(
                                    Locale.US,
                                    """%s<path %s d="%s" fill="%s" fill-opacity="%.3f" stroke="none" opacity="%.3f"%s%s%s/>%n""",
                                    indent, labelAttrs, d, fillVal,
                                    el.stroke.opacity * argbAlpha(el.stroke.colorArgb),
                                    (el.opacity * layerOpacity).coerceIn(0f, 1f),
                                    resolveTransformAttr(el),
                                    resolveFilterAttr(el, activeChrome),
                                    resolveStyleAttr(el)
                                )
                            )
                            return
                        }
                    }
                    if (el.nodes.isNotEmpty() || el.extraContours.isNotEmpty()) {
                        val d = buildPathDataString(el)
                        val fillRuleAttr = if (el.fillEvenOdd && el.extraContours.isNotEmpty()) {
                            """ fill-rule="evenodd""""
                        } else ""
                        out.append("""$indent<path $labelAttrs d="$d"$fillRuleAttr${commonAttrs(el, layerOpacity, activeChrome)}/>""" + "\n")
                    }
                }
                is TextShape -> {
                    val displayText = VectorMath.applyTextTransform(el.text, el.textTransform)
                    val textTransformAttr = resolveTransformAttr(el)
                    if (el.backgroundBadgeEnabled) {
                        val pad = el.backgroundPaddingPx.coerceAtLeast(0f)
                        out.append(
                            String.format(
                                Locale.US,
                                """%s<rect x="%.2f" y="%.2f" width="%.2f" height="%.2f" rx="%.2f" fill="%s" fill-opacity="%.3f"%s/>%n""",
                                indent, el.x - pad, el.y - pad * 0.6f, el.width + pad * 2f, el.height + pad * 1.2f,
                                min(24f, pad + 8f), argbToHexRgb(el.backgroundBadgeColorArgb),
                                argbAlpha(el.backgroundBadgeColorArgb) * el.opacity * layerOpacity,
                                textTransformAttr
                            )
                        )
                    }
                    val anchor = when (el.alignment) {
                        TextAlignMode.LEFT -> "start"
                        TextAlignMode.CENTER -> "middle"
                        TextAlignMode.RIGHT -> "end"
                    }
                    val tx = when (el.alignment) {
                        TextAlignMode.LEFT -> el.x
                        TextAlignMode.CENTER -> el.x + el.width / 2f
                        TextAlignMode.RIGHT -> el.x + el.width
                    }
                    val fontName = when (el.fontFamilyKey) {
                        "space_grotesk" -> "Space Grotesk, sans-serif"
                        "plus_jakarta_sans" -> "Plus Jakarta Sans, sans-serif"
                        "jetbrains_mono" -> "JetBrains Mono, monospace"
                        "serif" -> "serif"
                        "monospace" -> "monospace"
                        else -> "sans-serif"
                    }
                    val weight = if (el.isBold) "bold" else "normal"
                    val fStyle = if (el.isItalic) "italic" else "normal"
                    val decList = buildList {
                        if (el.isUnderline) add("underline")
                        if (el.isStrikethrough) add("line-through")
                    }
                    val decAttr = if (decList.isNotEmpty()) """ text-decoration="${decList.joinToString(" ")}"""" else ""

                    if (abs(el.curveRadius) >= 40f) {
                        val tpId = nextDefId("tp")
                        val r = abs(el.curveRadius).coerceAtLeast(40f)
                        val cx = el.x + el.width / 2f
                        val by = el.y + el.fontSize
                        val sweepFlag = if (el.curveRadius > 0f) 1 else 0
                        val sx = cx - r * 0.85f
                        val ex = cx + r * 0.85f
                        defs.append(
                            String.format(
                                Locale.US,
                                """<path id="%s" d="M %.2f %.2f A %.2f %.2f 0 0 %d %.2f %.2f"/>""",
                                tpId, sx, by, r, r, sweepFlag, ex, by
                            )
                        )
                        out.append(
                            String.format(
                                Locale.US,
                                """%s<text %s font-family="%s" font-size="%.1f" font-weight="%s" font-style="%s"%s letter-spacing="%.2fem"%s><textPath href="#%s" xlink:href="#%s" startOffset="50%%" text-anchor="middle">%s</textPath></text>%n""",
                                indent, labelAttrs, fontName, el.fontSize, weight, fStyle, decAttr,
                                el.letterSpacing, commonAttrs(el, layerOpacity, activeChrome), tpId, tpId,
                                escapeXml(displayText.replace("\n", " "))
                            )
                        )
                    } else {
                        val lines = displayText.split("\n")
                        out.append(
                            String.format(
                                Locale.US,
                                """%s<text %s x="%.2f" y="%.2f" font-family="%s" font-size="%.1f" font-weight="%s" font-style="%s"%s text-anchor="%s" letter-spacing="%.2fem"%s>""",
                                indent, labelAttrs, tx, el.y + el.fontSize,
                                fontName, el.fontSize, weight, fStyle, decAttr, anchor, el.letterSpacing,
                                commonAttrs(el, layerOpacity, activeChrome)
                            )
                        )
                        lines.forEachIndexed { idx, line ->
                            if (idx == 0) {
                                out.append(escapeXml(line))
                            } else {
                                out.append(
                                    String.format(
                                        Locale.US,
                                        """<tspan x="%.2f" dy="%.2f">%s</tspan>""",
                                        tx, el.fontSize * el.lineHeightMultiplier, escapeXml(line)
                                    )
                                )
                            }
                        }
                        out.append("</text>\n")
                    }
                }
                is ImageShape -> {
                    val file = File(el.imagePath)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val mime = if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                            "image/jpeg"
                        } else {
                            "image/png"
                        }
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        out.append(
                            String.format(
                                Locale.US,
                                """%s<image %s x="%.2f" y="%.2f" width="%.2f" height="%.2f" preserveAspectRatio="none" href="data:%s;base64,%s" xlink:href="data:%s;base64,%s" opacity="%.3f"%s%s%s/>%n""",
                                indent, labelAttrs, el.x, el.y, el.width, el.height,
                                mime, b64, mime, b64,
                                (el.opacity * layerOpacity).coerceIn(0f, 1f),
                                resolveTransformAttr(el),
                                resolveFilterAttr(el),
                                resolveStyleAttr(el)
                            )
                        )
                    }
                }
                is GroupShape -> {
                    val gAttrs = buildString {
                        append(labelAttrs)
                        append(String.format(Locale.US, """ opacity="%.3f"""", (el.opacity * layerOpacity).coerceIn(0f, 1f)))
                        append(resolveTransformAttr(el))
                        append(resolveFilterAttr(el))
                        append(resolveStyleAttr(el))
                    }
                    if (el.hasClippingMask && el.children.size >= 2) {
                        val clipId = nextDefId("clip")
                        val maskEl = el.children.first()
                        defs.append("""<clipPath id="$clipId">""")
                        writeRawGeometryTag(maskEl, defs, includeTransform = true)
                        defs.append("</clipPath>")
                        out.append("""$indent<g $gAttrs clip-path="url(#$clipId)">""" + "\n")
                        el.children.drop(1).forEach { writeElement(it, 1f, out, "$indent  ", activeChrome) }
                        if (el.showMaskOutline) {
                            writeElement(
                                when (maskEl) {
                                    is RectShape -> maskEl.copy(fill = PaintStyle(type = PaintType.NONE))
                                    is EllipseShape -> maskEl.copy(fill = PaintStyle(type = PaintType.NONE))
                                    is PolygonShape -> maskEl.copy(fill = PaintStyle(type = PaintType.NONE))
                                    is StarShape -> maskEl.copy(fill = PaintStyle(type = PaintType.NONE))
                                    is PathShape -> maskEl.copy(fill = PaintStyle(type = PaintType.NONE))
                                    else -> maskEl
                                },
                                1f,
                                out,
                                "$indent  ",
                                null
                            )
                        }
                        out.append("$indent</g>\n")
                    } else {
                        out.append("""$indent<g $gAttrs>""" + "\n")
                        el.children.forEach { writeElement(it, 1f, out, "$indent  ", activeChrome) }
                        out.append("$indent</g>\n")
                    }
                }
            }
        }

        if (targetElements != null) {
            targetElements.forEach { writeElement(it, 1f, body, "  ") }
        } else {
            if (doc.backgroundMode == DocumentBackgroundMode.SOLID) {
                val bgId = uniqueXmlId("doc_background")
                body.append(
                    String.format(
                        Locale.US,
                        """  <rect id="%s" x="0" y="0" width="%d" height="%d" fill="%s" fill-opacity="%.3f"/>%n""",
                        bgId, doc.widthPx, doc.heightPx,
                        argbToHexRgb(doc.backgroundColorArgb),
                        argbAlpha(doc.backgroundColorArgb)
                    )
                )
            }
            doc.layers.forEachIndexed { idx, layer ->
                val layerId = uniqueXmlId("layer_${idx + 1}_${layer.name}")
                val safeLabel = escapeXml(layer.name)
                val displayAttr = if (!layer.isVisible) """ style="display:none"""" else ""
                body.append(
                    String.format(
                        Locale.US,
                        """  <g id="%s" inkscape:groupmode="layer" inkscape:label="%s" data-name="%s" opacity="%.3f"%s>%n""",
                        layerId, safeLabel, safeLabel, layer.opacity.coerceIn(0f, 1f), displayAttr
                    )
                )
                layer.elements.forEach { el -> writeElement(el, 1f, body, "    ") }
                body.append("  </g>\n")
            }
        }

        val wStr = formatDimension(vbW)
        val hStr = formatDimension(vbH)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
            append(
                String.format(
                    Locale.US,
                    """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:inkscape="http://www.inkscape.org/namespaces/inkscape" width="%s" height="%s" viewBox="%.2f %.2f %.2f %.2f">%n""",
                    wStr, hStr, vbX, vbY, vbW, vbH
                )
            )
            if (defs.isNotEmpty()) {
                append("  <defs>\n")
                append("    ").append(defs).append("\n")
                append("  </defs>\n")
            }
            append(body)
            append("</svg>\n")
        }
    }

    private fun formatDimension(v: Float): String {
        val rounded = kotlin.math.round(v)
        return if (abs(v - rounded) < 0.01f) rounded.toInt().toString()
        else String.format(Locale.US, "%.2f", v)
    }

    private fun buildPathDataString(el: PathShape): String {
        val d = StringBuilder()
        fun appendSvgContour(nodes: List<com.example.model.BezierNode>, closed: Boolean) {
            if (nodes.isEmpty()) return
            if (d.isNotEmpty()) d.append(" ")
            d.append(String.format(Locale.US, "M %.2f %.2f", nodes[0].x, nodes[0].y))
            val segs = if (closed) nodes.size else nodes.size - 1
            for (i in 0 until segs) {
                val a = nodes[i]
                val b = nodes[(i + 1) % nodes.size]
                d.append(
                    String.format(
                        Locale.US,
                        " C %.2f %.2f %.2f %.2f %.2f %.2f",
                        a.cp2X, a.cp2Y, b.cp1X, b.cp1Y, b.x, b.y
                    )
                )
            }
            if (closed) d.append(" Z")
        }
        appendSvgContour(el.nodes, el.isClosed)
        el.extraContours.forEach { c -> appendSvgContour(c.nodes, c.isClosed) }
        return d.toString()
    }

    private fun argbToHexRgb(argb: Long): String {
        val rgb = argb.toInt() and 0x00FFFFFF
        return String.format("#%06X", rgb)
    }

    private fun argbAlpha(argb: Long): Float {
        return (((argb.toInt() ushr 24) and 0xFF) / 255f).coerceIn(0f, 1f)
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
