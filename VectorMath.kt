package com.example.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.model.BezierNode
import com.example.model.CanvasGuide
import com.example.model.EllipseShape
import com.example.model.GroupShape
import com.example.model.GuideOrientation
import com.example.model.ImageShape
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.SnapLine
import com.example.model.SpiderDocument
import com.example.model.StarShape
import com.example.model.TextCaseMode
import com.example.model.TextShape
import com.example.model.VectorElement
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object VectorMath {

    fun buildPolygonPoints(x: Float, y: Float, w: Float, h: Float, sides: Int): List<Offset> {
        val count = sides.coerceAtLeast(3)
        val cx = x + w / 2f
        val cy = y + h / 2f
        val rx = w / 2f
        val ry = h / 2f
        return List(count) { i ->
            val angle = -PI / 2.0 + (2.0 * PI * i / count)
            Offset(cx + (rx * cos(angle)).toFloat(), cy + (ry * sin(angle)).toFloat())
        }
    }

    fun buildStarPoints(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        points: Int,
        innerRatio: Float
    ): List<Offset> {
        val count = points.coerceAtLeast(3) * 2
        val cx = x + w / 2f
        val cy = y + h / 2f
        val rx = w / 2f
        val ry = h / 2f
        val ratio = innerRatio.coerceIn(0.15f, 0.9f)
        return List(count) { i ->
            val rScale = if (i % 2 == 0) 1f else ratio
            val angle = -PI / 2.0 + (PI * i / points.coerceAtLeast(3))
            Offset(
                cx + (rx * rScale * cos(angle)).toFloat(),
                cy + (ry * rScale * sin(angle)).toFloat()
            )
        }
    }

    fun toLocalCoords(element: VectorElement, docX: Float, docY: Float): Offset {
        if (element.rotationDeg == 0f) return Offset(docX, docY)
        val cx = element.x + element.width / 2f
        val cy = element.y + element.height / 2f
        val rad = Math.toRadians(-element.rotationDeg.toDouble())
        val dx = docX - cx
        val dy = docY - cy
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        return Offset(cx + dx * cosA - dy * sinA, cy + dx * sinA + dy * cosA)
    }

    fun hitTestElement(element: VectorElement, docX: Float, docY: Float, tolerance: Float = 16f): Boolean {
        if (!element.isVisible) return false
        val local = toLocalCoords(element, docX, docY)
        val pad = max(tolerance, if (element.stroke.enabled) element.stroke.width / 2f + 8f else 8f)
        return when (element) {
            is PathShape -> {
                if (element.nodes.isEmpty() && element.extraContours.isEmpty()) return false
                val inBounds = local.x in (element.x - pad)..(element.x + element.width + pad) &&
                    local.y in (element.y - pad)..(element.y + element.height + pad)
                if (!inBounds) return false
                if (element.isClosed && element.fill.type != com.example.model.PaintType.NONE) {
                    return true
                }
                val contours = listOf(element.nodes to element.isClosed) +
                    element.extraContours.map { it.nodes to it.isClosed }
                for ((cNodes, cClosed) in contours) {
                    val samples = sampleContourPoints(cNodes, cClosed)
                    for (i in 0 until samples.size - 1) {
                        if (distanceToSegment(local, samples[i], samples[i + 1]) <= pad) return true
                    }
                    if (samples.any { hypot(local.x - it.x, local.y - it.y) <= pad }) return true
                }
                false
            }
            is EllipseShape -> {
                val cx = element.x + element.width / 2f
                val cy = element.y + element.height / 2f
                val rx = (element.width / 2f + pad).coerceAtLeast(1f)
                val ry = (element.height / 2f + pad).coerceAtLeast(1f)
                val nx = (local.x - cx) / rx
                val ny = (local.y - cy) / ry
                nx * nx + ny * ny <= 1f
            }
            is GroupShape -> {
                val inBox = local.x in (element.x - pad)..(element.x + element.width + pad) &&
                    local.y in (element.y - pad)..(element.y + element.height + pad)
                if (!inBox) false
                else if (element.hasClippingMask && element.children.isNotEmpty()) {
                    hitTestElement(element.children.first(), local.x, local.y, tolerance)
                } else {
                    element.children.any { hitTestElement(it, local.x, local.y, tolerance) }
                }
            }
            else -> {
                local.x in (element.x - pad)..(element.x + element.width + pad) &&
                    local.y in (element.y - pad)..(element.y + element.height + pad)
            }
        }
    }

    fun applyTextTransform(text: String, mode: TextCaseMode): String = when (mode) {
        TextCaseMode.NONE -> text
        TextCaseMode.UPPERCASE -> text.uppercase()
        TextCaseMode.LOWERCASE -> text.lowercase()
        TextCaseMode.TITLE_CASE -> text.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }
    }

    /**
     * Builds a variable-width closed outline polygon from a brush PathShape using its pressureWidths
     * and optional smooth end tapering.
     */
    fun buildBrushRibbonOutline(path: PathShape, stepsPerSegment: Int = 8): List<Offset> {
        val nodes = path.nodes
        if (nodes.size < 2) return emptyList()
        val baseWidth = max(2f, path.stroke.width)
        val pressures = path.pressureWidths
        val samples = mutableListOf<Offset>()
        val samplePressures = mutableListOf<Float>()

        val segCount = nodes.size - 1
        for (i in 0 until segCount) {
            val a = nodes[i]
            val b = nodes[i + 1]
            val pA = pressures.getOrElse(i) { 1f }.coerceIn(0.18f, 2.2f)
            val pB = pressures.getOrElse(i + 1) { pA }.coerceIn(0.18f, 2.2f)
            val startStep = if (i == 0) 0 else 1
            for (s in startStep..stepsPerSegment) {
                val t = s.toFloat() / stepsPerSegment
                samples.add(evalCubic(a, b, t))
                samplePressures.add(pA + (pB - pA) * t)
            }
        }
        if (samples.size < 2) return emptyList()

        val leftSide = mutableListOf<Offset>()
        val rightSide = mutableListOf<Offset>()
        val n = samples.size
        for (i in 0 until n) {
            val prev = samples[(i - 1).coerceAtLeast(0)]
            val next = samples[(i + 1).coerceAtMost(n - 1)]
            val tx = next.x - prev.x
            val ty = next.y - prev.y
            val len = hypot(tx, ty).coerceAtLeast(0.001f)
            val nx = -ty / len
            val ny = tx / len

            val u = i.toFloat() / (n - 1).coerceAtLeast(1)
            val taper = if (path.brushTaperEnds) {
                val edge = min(u, 1f - u)
                (edge / 0.14f).coerceIn(0.12f, 1f)
            } else 1f
            val halfW = (baseWidth * 0.5f * samplePressures[i] * taper).coerceAtLeast(0.6f)
            leftSide.add(Offset(samples[i].x + nx * halfW, samples[i].y + ny * halfW))
            rightSide.add(Offset(samples[i].x - nx * halfW, samples[i].y - ny * halfW))
        }
        return leftSide + rightSide.asReversed()
    }

    fun samplePathPoints(path: PathShape, stepsPerSegment: Int = 10): List<Offset> =
        sampleContourPoints(path.nodes, path.isClosed, stepsPerSegment)

    fun sampleContourPoints(
        nodes: List<BezierNode>,
        isClosed: Boolean,
        stepsPerSegment: Int = 10
    ): List<Offset> {
        if (nodes.isEmpty()) return emptyList()
        if (nodes.size == 1) return listOf(Offset(nodes[0].x, nodes[0].y))
        val out = mutableListOf<Offset>()
        val segCount = if (isClosed) nodes.size else nodes.size - 1
        for (i in 0 until segCount) {
            val a = nodes[i]
            val b = nodes[(i + 1) % nodes.size]
            for (s in 0..stepsPerSegment) {
                val t = s.toFloat() / stepsPerSegment
                out.add(evalCubic(a, b, t))
            }
        }
        return out
    }

    fun evalCubic(a: BezierNode, b: BezierNode, t: Float): Offset {
        val u = 1f - t
        val tt = t * t
        val uu = u * u
        val uuu = uu * u
        val ttt = tt * t
        val x = uuu * a.x + 3f * uu * t * a.cp2X + 3f * u * tt * b.cp1X + ttt * b.x
        val y = uuu * a.y + 3f * uu * t * a.cp2Y + 3f * u * tt * b.cp1Y + ttt * b.y
        return Offset(x, y)
    }

    private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-4f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot(p.x - projX, p.y - projY)
    }

    fun moveElementBy(element: VectorElement, dx: Float, dy: Float): VectorElement {
        return when (element) {
            is RectShape -> element.copy(x = element.x + dx, y = element.y + dy)
            is EllipseShape -> element.copy(x = element.x + dx, y = element.y + dy)
            is PolygonShape -> element.copy(x = element.x + dx, y = element.y + dy)
            is StarShape -> element.copy(x = element.x + dx, y = element.y + dy)
            is TextShape -> element.copy(x = element.x + dx, y = element.y + dy)
            is ImageShape -> element.copy(x = element.x + dx, y = element.y + dy)
            is PathShape -> {
                fun shiftNodes(list: List<BezierNode>) = list.map {
                    it.copy(
                        x = it.x + dx,
                        y = it.y + dy,
                        cp1X = it.cp1X + dx,
                        cp1Y = it.cp1Y + dy,
                        cp2X = it.cp2X + dx,
                        cp2Y = it.cp2Y + dy
                    )
                }
                element.copy(
                    x = element.x + dx,
                    y = element.y + dy,
                    nodes = shiftNodes(element.nodes),
                    extraContours = element.extraContours.map { it.copy(nodes = shiftNodes(it.nodes)) }
                )
            }
            is GroupShape -> element.copy(
                x = element.x + dx,
                y = element.y + dy,
                children = element.children.map { moveElementBy(it, dx, dy) }
            )
        }
    }

    fun scaleElementToBounds(
        element: VectorElement,
        newX: Float,
        newY: Float,
        newW: Float,
        newH: Float
    ): VectorElement {
        val safeW = max(12f, newW)
        val safeH = max(12f, newH)
        val oldW = max(1f, element.width)
        val oldH = max(1f, element.height)
        val sx = safeW / oldW
        val sy = safeH / oldH
        return when (element) {
            is RectShape -> element.copy(x = newX, y = newY, width = safeW, height = safeH)
            is EllipseShape -> element.copy(x = newX, y = newY, width = safeW, height = safeH)
            is PolygonShape -> element.copy(x = newX, y = newY, width = safeW, height = safeH)
            is StarShape -> element.copy(x = newX, y = newY, width = safeW, height = safeH)
            is ImageShape -> element.copy(x = newX, y = newY, width = safeW, height = safeH)
            is TextShape -> element.copy(
                x = newX,
                y = newY,
                width = safeW,
                height = safeH,
                fontSize = (element.fontSize * sy).coerceIn(10f, 400f)
            )
            is PathShape -> {
                fun scaleNodes(list: List<BezierNode>) = list.map { n ->
                    n.copy(
                        x = newX + (n.x - element.x) * sx,
                        y = newY + (n.y - element.y) * sy,
                        cp1X = newX + (n.cp1X - element.x) * sx,
                        cp1Y = newY + (n.cp1Y - element.y) * sy,
                        cp2X = newX + (n.cp2X - element.x) * sx,
                        cp2Y = newY + (n.cp2Y - element.y) * sy
                    )
                }
                element.copy(
                    x = newX,
                    y = newY,
                    width = safeW,
                    height = safeH,
                    nodes = scaleNodes(element.nodes),
                    extraContours = element.extraContours.map { it.copy(nodes = scaleNodes(it.nodes)) }
                )
            }
            is GroupShape -> element.copy(
                x = newX,
                y = newY,
                width = safeW,
                height = safeH,
                children = element.children.map { child ->
                    val cx = newX + (child.x - element.x) * sx
                    val cy = newY + (child.y - element.y) * sy
                    scaleElementToBounds(child, cx, cy, child.width * sx, child.height * sy)
                }
            )
        }
    }

    fun updateElementTransform(
        element: VectorElement,
        x: Float = element.x,
        y: Float = element.y,
        width: Float = element.width,
        height: Float = element.height,
        rotationDeg: Float = element.rotationDeg,
        opacity: Float = element.opacity
    ): VectorElement {
        val moved = if (x != element.x || y != element.y || width != element.width || height != element.height) {
            scaleElementToBounds(element, x, y, width, height)
        } else {
            element
        }
        return when (moved) {
            is RectShape -> moved.copy(rotationDeg = rotationDeg, opacity = opacity)
            is EllipseShape -> moved.copy(rotationDeg = rotationDeg, opacity = opacity)
            is PolygonShape -> moved.copy(rotationDeg = rotationDeg, opacity = opacity)
            is StarShape -> moved.copy(rotationDeg = rotationDeg, opacity = opacity)
            is PathShape -> moved.copy(rotationDeg = rotationDeg, opacity = opacity)
            is TextShape -> moved.copy(rotationDeg = rotationDeg, opacity = opacity)
            is ImageShape -> moved.copy(rotationDeg = rotationDeg, opacity = opacity)
            is GroupShape -> moved.copy(rotationDeg = rotationDeg, opacity = opacity)
        }
    }

    fun recalculatePathBounds(path: PathShape): PathShape =
        VectorBooleanEngine.recalculateCompoundBounds(path)

    fun computeCombinedBounds(elements: List<VectorElement>): Rect? {
        if (elements.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        elements.forEach { e ->
            minX = min(minX, e.x)
            minY = min(minY, e.y)
            maxX = max(maxX, e.x + e.width)
            maxY = max(maxY, e.y + e.height)
        }
        return Rect(minX, minY, maxX, maxY)
    }

    fun pointsToSmoothPathNodes(rawPoints: List<Offset>, epsilon: Float = 6f): List<BezierNode> {
        if (rawPoints.isEmpty()) return emptyList()
        if (rawPoints.size == 1) return listOf(BezierNode(x = rawPoints[0].x, y = rawPoints[0].y))
        val simplified = rdpSimplify(rawPoints, epsilon)
        if (simplified.size < 2) return listOf(BezierNode(x = simplified[0].x, y = simplified[0].y))

        val tension = 0.25f
        return simplified.mapIndexed { i, p ->
            val prev = simplified[(i - 1).coerceAtLeast(0)]
            val next = simplified[(i + 1).coerceAtMost(simplified.lastIndex)]
            val dx = (next.x - prev.x) * tension
            val dy = (next.y - prev.y) * tension
            BezierNode(
                x = p.x,
                y = p.y,
                cp1X = if (i == 0) p.x else p.x - dx,
                cp1Y = if (i == 0) p.y else p.y - dy,
                cp2X = if (i == simplified.lastIndex) p.x else p.x + dx,
                cp2Y = if (i == simplified.lastIndex) p.y else p.y + dy,
                smooth = i > 0 && i < simplified.lastIndex
            )
        }
    }

    private fun rdpSimplify(points: List<Offset>, epsilon: Float): List<Offset> {
        if (points.size < 3) return points
        var maxDist = 0f
        var index = 0
        val start = points.first()
        val end = points.last()
        for (i in 1 until points.lastIndex) {
            val d = distanceToSegment(points[i], start, end)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        return if (maxDist > epsilon) {
            val left = rdpSimplify(points.subList(0, index + 1), epsilon)
            val right = rdpSimplify(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    data class SnapResult(val x: Float, val y: Float, val lines: List<SnapLine>)

    fun computeSnap(
        targetX: Float,
        targetY: Float,
        targetW: Float,
        targetH: Float,
        doc: SpiderDocument,
        excludeIds: Set<String>,
        gridSizePx: Int,
        snapEnabled: Boolean,
        threshold: Float = 10f,
        snapToGrid: Boolean = true,
        snapToObjects: Boolean = true,
        snapToGuides: Boolean = true
    ): SnapResult {
        if (!snapEnabled) return SnapResult(targetX, targetY, emptyList())

        data class Candidate(val pos: Float, val spanA: Float, val spanB: Float)

        val candidateXs = mutableListOf<Candidate>()
        val candidateYs = mutableListOf<Candidate>()

        val docW = doc.widthPx.toFloat()
        val docH = doc.heightPx.toFloat()

        // Canvas edges & center
        candidateXs += listOf(
            Candidate(0f, 0f, docH),
            Candidate(docW / 2f, 0f, docH),
            Candidate(docW, 0f, docH)
        )
        candidateYs += listOf(
            Candidate(0f, 0f, docW),
            Candidate(docH / 2f, 0f, docW),
            Candidate(docH, 0f, docW)
        )

        // Guides
        if (snapToGuides) {
            doc.guides.forEach { g ->
                if (g.orientation == GuideOrientation.VERTICAL) {
                    candidateXs += Candidate(g.positionPx, 0f, docH)
                } else {
                    candidateYs += Candidate(g.positionPx, 0f, docW)
                }
            }
        }

        // Other visible elements
        val otherElements = if (snapToObjects) {
            doc.layers.filter { it.isVisible }.flatMap { it.elements }
                .filter { it.id !in excludeIds && it.isVisible }
        } else emptyList()

        otherElements.forEach { el ->
            val minY = min(targetY, el.y)
            val maxY = max(targetY + targetH, el.y + el.height)
            candidateXs += listOf(
                Candidate(el.x, minY, maxY),
                Candidate(el.x + el.width / 2f, minY, maxY),
                Candidate(el.x + el.width, minY, maxY)
            )
            val minX = min(targetX, el.x)
            val maxX = max(targetX + targetW, el.x + el.width)
            candidateYs += listOf(
                Candidate(el.y, minX, maxX),
                Candidate(el.y + el.height / 2f, minX, maxX),
                Candidate(el.y + el.height, minX, maxX)
            )
        }

        var bestDx = Float.MAX_VALUE
        var snapCandX: Candidate? = null
        val targetXPoints = listOf(targetX, targetX + targetW / 2f, targetX + targetW)
        for (tx in targetXPoints) {
            for (cx in candidateXs) {
                val diff = cx.pos - tx
                if (abs(diff) <= threshold && abs(diff) < abs(bestDx)) {
                    bestDx = diff
                    snapCandX = cx
                }
            }
        }

        var bestDy = Float.MAX_VALUE
        var snapCandY: Candidate? = null
        val targetYPoints = listOf(targetY, targetY + targetH / 2f, targetY + targetH)
        for (ty in targetYPoints) {
            for (cy in candidateYs) {
                val diff = cy.pos - ty
                if (abs(diff) <= threshold && abs(diff) < abs(bestDy)) {
                    bestDy = diff
                    snapCandY = cy
                }
            }
        }

        var finalX = if (bestDx != Float.MAX_VALUE) targetX + bestDx else targetX
        var finalY = if (bestDy != Float.MAX_VALUE) targetY + bestDy else targetY
        val lines = mutableListOf<SnapLine>()

        if (snapCandX != null) {
            lines.add(
                SnapLine(
                    isVertical = true,
                    positionDoc = snapCandX.pos,
                    spanStart = snapCandX.spanA,
                    spanEnd = snapCandX.spanB
                )
            )
        } else if (snapToGrid && gridSizePx > 0) {
            val gx = kotlin.math.round(targetX / gridSizePx) * gridSizePx
            if (abs(gx - targetX) <= threshold * 0.75f) finalX = gx
        }

        if (snapCandY != null) {
            lines.add(
                SnapLine(
                    isVertical = false,
                    positionDoc = snapCandY.pos,
                    spanStart = snapCandY.spanA,
                    spanEnd = snapCandY.spanB
                )
            )
        } else if (snapToGrid && gridSizePx > 0) {
            val gy = kotlin.math.round(targetY / gridSizePx) * gridSizePx
            if (abs(gy - targetY) <= threshold * 0.75f) finalY = gy
        }

        // Smart Equal-Spacing Detection between adjacent horizontal/vertical neighbors
        if (snapToObjects && otherElements.size >= 2) {
            val sortedH = otherElements.sortedBy { it.x }
            for (i in 0 until sortedH.lastIndex) {
                val a = sortedH[i]
                val b = sortedH[i + 1]
                val gap = b.x - (a.x + a.width)
                if (gap > 8f) {
                    val candRightOfB = b.x + b.width + gap
                    val candLeftOfA = a.x - gap - targetW
                    if (snapCandX == null && abs(finalX - candRightOfB) <= threshold) {
                        finalX = candRightOfB
                        val midY = min(b.y + b.height / 2f, finalY + targetH / 2f)
                        lines.add(
                            SnapLine(
                                isVertical = false,
                                positionDoc = midY,
                                spanStart = b.x + b.width,
                                spanEnd = finalX,
                                distanceLabel = "${gap.roundToInt()} px",
                                isSpacingGuide = true
                            )
                        )
                        lines.add(
                            SnapLine(
                                isVertical = false,
                                positionDoc = a.y + a.height / 2f,
                                spanStart = a.x + a.width,
                                spanEnd = b.x,
                                distanceLabel = "${gap.roundToInt()} px",
                                isSpacingGuide = true
                            )
                        )
                        break
                    } else if (snapCandX == null && abs(finalX - candLeftOfA) <= threshold) {
                        finalX = candLeftOfA
                        val midY = min(a.y + a.height / 2f, finalY + targetH / 2f)
                        lines.add(
                            SnapLine(
                                isVertical = false,
                                positionDoc = midY,
                                spanStart = finalX + targetW,
                                spanEnd = a.x,
                                distanceLabel = "${gap.roundToInt()} px",
                                isSpacingGuide = true
                            )
                        )
                        break
                    }
                }
            }
        }

        return SnapResult(finalX, finalY, lines)
    }
}
