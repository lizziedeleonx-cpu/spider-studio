package com.example.engine

import androidx.compose.ui.geometry.Offset
import com.example.model.BezierNode
import com.example.model.BooleanOp
import com.example.model.EllipseShape
import com.example.model.NodeHandleMode
import com.example.model.PathContour
import com.example.model.PathShape
import com.example.model.PolygonShape
import com.example.model.RectShape
import com.example.model.StarShape
import com.example.model.VectorElement
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object VectorBooleanEngine {

    private const val CIRCLE_KAPPA = 0.55228475f

    /**
     * Converts any geometric VectorElement (Rect, Ellipse, Polygon, Star, or rotated Path)
     * into an unrotated, fully editable PathShape in document coordinates.
     */
    fun elementToPath(element: VectorElement): PathShape? {
        val rawPath = when (element) {
            is PathShape -> element
            is RectShape -> rectToPath(element)
            is EllipseShape -> ellipseToPath(element)
            is PolygonShape -> polygonToPath(element)
            is StarShape -> starToPath(element)
            else -> return null
        }
        return if (rawPath.rotationDeg == 0f) {
            recalculateCompoundBounds(rawPath)
        } else {
            bakeRotation(rawPath)
        }
    }

    private fun rectToPath(r: RectShape): PathShape {
        val x = r.x
        val y = r.y
        val w = r.width
        val h = r.height
        val cr = min(r.cornerRadius, min(w, h) / 2f).coerceAtLeast(0f)
        val nodes = if (cr < 0.5f) {
            listOf(
                BezierNode(x = x, y = y),
                BezierNode(x = x + w, y = y),
                BezierNode(x = x + w, y = y + h),
                BezierNode(x = x, y = y + h)
            )
        } else {
            val k = cr * CIRCLE_KAPPA
            listOf(
                BezierNode(x = x + cr, y = y, cp1X = x + cr - k, cp1Y = y, cp2X = x + cr, cp2Y = y),
                BezierNode(x = x + w - cr, y = y, cp1X = x + w - cr, cp1Y = y, cp2X = x + w - cr + k, cp2Y = y),
                BezierNode(x = x + w, y = y + cr, cp1X = x + w, cp1Y = y + cr - k, cp2X = x + w, cp2Y = y + cr),
                BezierNode(x = x + w, y = y + h - cr, cp1X = x + w, cp1Y = y + h - cr, cp2X = x + w, cp2Y = y + h - cr + k),
                BezierNode(x = x + w - cr, y = y + h, cp1X = x + w - cr + k, cp1Y = y + h, cp2X = x + w - cr, cp2Y = y + h),
                BezierNode(x = x + cr, y = y + h, cp1X = x + cr, cp1Y = y + h, cp2X = x + cr - k, cp2Y = y + h),
                BezierNode(x = x, y = y + h - cr, cp1X = x, cp1Y = y + h - cr + k, cp2X = x, cp2Y = y + h - cr),
                BezierNode(x = x, y = y + cr, cp1X = x, cp1Y = y + cr, cp2X = x, cp2Y = y + cr - k)
            )
        }
        return PathShape(
            id = r.id,
            name = "${r.name} (Curvas)",
            x = x, y = y, width = w, height = h,
            rotationDeg = r.rotationDeg, opacity = r.opacity, isVisible = r.isVisible,
            isLocked = r.isLocked, fill = r.fill, stroke = r.stroke, effects = r.effects,
            nodes = nodes,
            isClosed = true
        )
    }

    private fun ellipseToPath(e: EllipseShape): PathShape {
        val cx = e.x + e.width / 2f
        val cy = e.y + e.height / 2f
        val rx = e.width / 2f
        val ry = e.height / 2f
        val kx = rx * CIRCLE_KAPPA
        val ky = ry * CIRCLE_KAPPA
        val nodes = listOf(
            BezierNode(x = cx, y = e.y, cp1X = cx - kx, cp1Y = e.y, cp2X = cx + kx, cp2Y = e.y, smooth = true, handleMode = NodeHandleMode.SYMMETRIC),
            BezierNode(x = e.x + e.width, y = cy, cp1X = e.x + e.width, cp1Y = cy - ky, cp2X = e.x + e.width, cp2Y = cy + ky, smooth = true, handleMode = NodeHandleMode.SYMMETRIC),
            BezierNode(x = cx, y = e.y + e.height, cp1X = cx + kx, cp1Y = e.y + e.height, cp2X = cx - kx, cp2Y = e.y + e.height, smooth = true, handleMode = NodeHandleMode.SYMMETRIC),
            BezierNode(x = e.x, y = cy, cp1X = e.x, cp1Y = cy + ky, cp2X = e.x, cp2Y = cy - ky, smooth = true, handleMode = NodeHandleMode.SYMMETRIC)
        )
        return PathShape(
            id = e.id,
            name = "${e.name} (Curvas)",
            x = e.x, y = e.y, width = e.width, height = e.height,
            rotationDeg = e.rotationDeg, opacity = e.opacity, isVisible = e.isVisible,
            isLocked = e.isLocked, fill = e.fill, stroke = e.stroke, effects = e.effects,
            nodes = nodes,
            isClosed = true
        )
    }

    private fun polygonToPath(p: PolygonShape): PathShape {
        val pts = VectorMath.buildPolygonPoints(p.x, p.y, p.width, p.height, p.sides)
        return PathShape(
            id = p.id,
            name = "${p.name} (Curvas)",
            x = p.x, y = p.y, width = p.width, height = p.height,
            rotationDeg = p.rotationDeg, opacity = p.opacity, isVisible = p.isVisible,
            isLocked = p.isLocked, fill = p.fill, stroke = p.stroke, effects = p.effects,
            nodes = pts.map { BezierNode(x = it.x, y = it.y) },
            isClosed = true
        )
    }

    private fun starToPath(s: StarShape): PathShape {
        val pts = VectorMath.buildStarPoints(s.x, s.y, s.width, s.height, s.points, s.innerRadiusRatio)
        return PathShape(
            id = s.id,
            name = "${s.name} (Curvas)",
            x = s.x, y = s.y, width = s.width, height = s.height,
            rotationDeg = s.rotationDeg, opacity = s.opacity, isVisible = s.isVisible,
            isLocked = s.isLocked, fill = s.fill, stroke = s.stroke, effects = s.effects,
            nodes = pts.map { BezierNode(x = it.x, y = it.y) },
            isClosed = true
        )
    }

    private fun bakeRotation(path: PathShape): PathShape {
        val cx = path.x + path.width / 2f
        val cy = path.y + path.height / 2f
        val rad = Math.toRadians(path.rotationDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        fun rot(px: Float, py: Float): Offset {
            val dx = px - cx
            val dy = py - cy
            return Offset(cx + dx * cosA - dy * sinA, cy + dx * sinA + dy * cosA)
        }
        fun rotNodes(list: List<BezierNode>) = list.map { n ->
            val p = rot(n.x, n.y)
            val c1 = rot(n.cp1X, n.cp1Y)
            val c2 = rot(n.cp2X, n.cp2Y)
            n.copy(x = p.x, y = p.y, cp1X = c1.x, cp1Y = c1.y, cp2X = c2.x, cp2Y = c2.y)
        }
        return recalculateCompoundBounds(
            path.copy(
                rotationDeg = 0f,
                nodes = rotNodes(path.nodes),
                extraContours = path.extraContours.map { it.copy(nodes = rotNodes(it.nodes)) }
            )
        )
    }

    fun recalculateCompoundBounds(path: PathShape): PathShape {
        val allNodes = path.nodes + path.extraContours.flatMap { it.nodes }
        if (allNodes.isEmpty()) return path
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        allNodes.forEach { n ->
            minX = min(minX, n.x)
            minY = min(minY, n.y)
            maxX = max(maxX, n.x)
            maxY = max(maxY, n.y)
        }
        return path.copy(
            x = minX,
            y = minY,
            width = max(12f, maxX - minX),
            height = max(12f, maxY - minY)
        )
    }

    // --- Exact De Casteljau Cubic Split ---

    data class SplitCubicResult(
        val leftStart: BezierNode,
        val midNode: BezierNode,
        val rightEnd: BezierNode
    )

    fun splitCubicAt(a: BezierNode, b: BezierNode, t: Float): SplitCubicResult {
        val p0 = Offset(a.x, a.y)
        val p1 = Offset(a.cp2X, a.cp2Y)
        val p2 = Offset(b.cp1X, b.cp1Y)
        val p3 = Offset(b.x, b.y)

        val p01 = lerp(p0, p1, t)
        val p12 = lerp(p1, p2, t)
        val p23 = lerp(p2, p3, t)
        val p012 = lerp(p01, p12, t)
        val p123 = lerp(p12, p23, t)
        val p0123 = lerp(p012, p123, t)

        val isCurved = (p012 - p0123).getDistance() > 0.5f || (p123 - p0123).getDistance() > 0.5f
        val leftStart = a.copy(cp2X = p01.x, cp2Y = p01.y)
        val midNode = BezierNode(
            x = p0123.x,
            y = p0123.y,
            cp1X = p012.x,
            cp1Y = p012.y,
            cp2X = p123.x,
            cp2Y = p123.y,
            smooth = isCurved,
            handleMode = if (isCurved) NodeHandleMode.SMOOTH else NodeHandleMode.CORNER
        )
        val rightEnd = b.copy(cp1X = p23.x, cp1Y = p23.y)
        return SplitCubicResult(leftStart, midNode, rightEnd)
    }

    data class SegmentHit(val segmentIndex: Int, val t: Float, val distance: Float)

    fun findClosestSegmentOnPrimary(path: PathShape, localPoint: Offset, steps: Int = 20): SegmentHit? {
        val nodes = path.nodes
        if (nodes.size < 2) return null
        val segCount = if (path.isClosed) nodes.size else nodes.size - 1
        var bestDist = Float.MAX_VALUE
        var bestSeg = -1
        var bestT = 0.5f
        for (i in 0 until segCount) {
            val a = nodes[i]
            val b = nodes[(i + 1) % nodes.size]
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val pt = VectorMath.evalCubic(a, b, t)
                val d = (pt - localPoint).getDistance()
                if (d < bestDist) {
                    bestDist = d
                    bestSeg = i
                    bestT = t.coerceIn(0.05f, 0.95f)
                }
            }
        }
        return if (bestSeg >= 0) SegmentHit(bestSeg, bestT, bestDist) else null
    }

    fun insertNodeAtSegment(path: PathShape, segmentIndex: Int, t: Float): Pair<PathShape, Int> {
        val nodes = path.nodes.toMutableList()
        if (nodes.size < 2 || segmentIndex !in nodes.indices) return path to 0
        val nextIdx = (segmentIndex + 1) % nodes.size
        val split = splitCubicAt(nodes[segmentIndex], nodes[nextIdx], t.coerceIn(0.05f, 0.95f))
        nodes[segmentIndex] = split.leftStart
        nodes[nextIdx] = split.rightEnd
        val insertIdx = segmentIndex + 1
        nodes.add(insertIdx, split.midNode)
        return recalculateCompoundBounds(path.copy(nodes = nodes)) to insertIdx
    }

    fun reversePath(path: PathShape): PathShape {
        val revNodes = path.nodes.reversed().map { n ->
            n.copy(cp1X = n.cp2X, cp1Y = n.cp2Y, cp2X = n.cp1X, cp2Y = n.cp1Y)
        }
        return path.copy(nodes = revNodes)
    }

    fun simplifyPath(path: PathShape, epsilon: Float = 5f): PathShape {
        if (path.nodes.size <= 3) return path
        val pts = path.nodes.map { Offset(it.x, it.y) }
        val simplified = VectorMath.pointsToSmoothPathNodes(pts, epsilon = epsilon)
        return recalculateCompoundBounds(path.copy(nodes = simplified))
    }

    fun breakApartContours(path: PathShape): List<PathShape> {
        if (path.extraContours.isEmpty()) return listOf(path)
        val primary = recalculateCompoundBounds(
            path.copy(id = UUID.randomUUID().toString(), name = "${path.name} 1", extraContours = emptyList())
        )
        val extras = path.extraContours.mapIndexed { idx, c ->
            recalculateCompoundBounds(
                path.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${path.name} ${idx + 2}",
                    nodes = c.nodes,
                    isClosed = c.isClosed,
                    extraContours = emptyList()
                )
            )
        }
        return listOf(primary) + extras
    }

    // --- Real Vector Boolean Engine (Planar Segment Arrangement + Winding Tracing) ---

    private data class Pt(val x: Float, val y: Float) {
        fun dist(o: Pt): Float = hypot(x - o.x, y - o.y)
    }

    private data class Seg(val a: Pt, val b: Pt, val fromA: Boolean)

    fun applyBooleanOperation(elements: List<VectorElement>, op: BooleanOp): PathShape? {
        if (elements.size < 2) return null
        val paths = elements.mapNotNull { elementToPath(it) }
        if (paths.size < 2) return null

        val primary = paths.first()
        var accContours = pathToPolygons(primary)
        for (i in 1 until paths.size) {
            val nextContours = pathToPolygons(paths[i])
            accContours = booleanPolygons(accContours, nextContours, op)
            if (accContours.isEmpty()) break
        }
        if (accContours.isEmpty()) return null

        val pathContours = accContours.map { poly ->
            val simplified = simplifyCollinear(poly)
            PathContour(
                nodes = simplified.map { PtNode -> BezierNode(x = PtNode.x, y = PtNode.y) },
                isClosed = true
            )
        }.filter { it.nodes.size >= 3 }

        if (pathContours.isEmpty()) return null
        val main = pathContours.first()
        val extras = pathContours.drop(1)
        return recalculateCompoundBounds(
            PathShape(
                id = UUID.randomUUID().toString(),
                name = "${op.label} Vectorial",
                x = 0f, y = 0f, width = 10f, height = 10f,
                rotationDeg = 0f,
                opacity = primary.opacity,
                isVisible = true,
                isLocked = false,
                fill = primary.fill,
                stroke = primary.stroke,
                effects = primary.effects,
                nodes = main.nodes,
                isClosed = true,
                extraContours = extras,
                fillEvenOdd = true
            )
        )
    }

    private fun pathToPolygons(path: PathShape): List<List<Pt>> {
        val raw = mutableListOf<List<Pt>>()
        raw += contourToPolygon(path.nodes, isClosed = true)
        path.extraContours.forEach { c ->
            raw += contourToPolygon(c.nodes, isClosed = true)
        }
        val valid = raw.filter { it.size >= 3 && abs(signedArea(it)) > 1f }
        // Orient contours by Even-Odd nesting depth: even depth -> CCW (+area), odd depth (holes) -> CW (-area)
        return valid.mapIndexed { idx, poly ->
            val testPt = interiorSamplePoint(poly)
            var depth = 0
            for (j in valid.indices) {
                if (j != idx && pointInPolygon(testPt, valid[j])) depth++
            }
            val wantCcw = depth % 2 == 0
            val area = signedArea(poly)
            if ((wantCcw && area < 0f) || (!wantCcw && area > 0f)) poly.reversed() else poly
        }
    }

    private fun contourToPolygon(nodes: List<BezierNode>, isClosed: Boolean): List<Pt> {
        if (nodes.size < 2) return emptyList()
        val out = mutableListOf<Pt>()
        val segs = if (isClosed) nodes.size else nodes.size - 1
        for (i in 0 until segs) {
            val a = nodes[i]
            val b = nodes[(i + 1) % nodes.size]
            val isStraight = hypot(a.cp2X - a.x, a.cp2Y - a.y) < 0.5f &&
                hypot(b.cp1X - b.x, b.cp1Y - b.y) < 0.5f
            if (isStraight) {
                out.add(Pt(a.x, a.y))
            } else {
                val steps = 20
                for (s in 0 until steps) {
                    val o = VectorMath.evalCubic(a, b, s.toFloat() / steps)
                    out.add(Pt(o.x, o.y))
                }
            }
        }
        return deduplicateClosePts(out)
    }

    private fun deduplicateClosePts(pts: List<Pt>, minDist: Float = 0.25f): List<Pt> {
        if (pts.isEmpty()) return pts
        val res = mutableListOf(pts.first())
        for (i in 1 until pts.size) {
            if (pts[i].dist(res.last()) >= minDist) res.add(pts[i])
        }
        if (res.size > 1 && res.first().dist(res.last()) < minDist) res.removeAt(res.lastIndex)
        return res
    }

    private fun booleanPolygons(
        polysA: List<List<Pt>>,
        polysB: List<List<Pt>>,
        op: BooleanOp
    ): List<List<Pt>> {
        val segsA = polysA.flatMap { toSegments(it, fromA = true) }
        val segsB = polysB.flatMap { toSegments(it, fromA = false) }

        val splitA = splitSegmentsAgainst(segsA, segsB)
        val splitB = splitSegmentsAgainst(segsB, segsA)

        val kept = mutableListOf<Pair<Pt, Pt>>()

        splitA.forEach { s ->
            val mid = Pt((s.a.x + s.b.x) * 0.5f, (s.a.y + s.b.y) * 0.5f)
            val shared = findSharedEdgeStatus(s.a, s.b, splitB)
            when (shared) {
                1 -> { // Shared same direction
                    if (op == BooleanOp.UNION || op == BooleanOp.INTERSECT) kept += s.a to s.b
                }
                -1 -> { // Shared opposite direction
                    if (op == BooleanOp.SUBTRACT) kept += s.a to s.b
                }
                else -> {
                    val inB = pointInPolygons(mid, polysB)
                    when (op) {
                        BooleanOp.UNION -> if (!inB) kept += s.a to s.b
                        BooleanOp.INTERSECT -> if (inB) kept += s.a to s.b
                        BooleanOp.SUBTRACT -> if (!inB) kept += s.a to s.b
                        BooleanOp.XOR -> kept += if (inB) (s.b to s.a) else (s.a to s.b)
                    }
                }
            }
        }

        splitB.forEach { s ->
            val mid = Pt((s.a.x + s.b.x) * 0.5f, (s.a.y + s.b.y) * 0.5f)
            val shared = findSharedEdgeStatus(s.a, s.b, splitA)
            if (shared == 0) {
                val inA = pointInPolygons(mid, polysA)
                when (op) {
                    BooleanOp.UNION -> if (!inA) kept += s.a to s.b
                    BooleanOp.INTERSECT -> if (inA) kept += s.a to s.b
                    BooleanOp.SUBTRACT -> if (inA) kept += s.b to s.a
                    BooleanOp.XOR -> kept += if (inA) (s.b to s.a) else (s.a to s.b)
                }
            }
        }

        return traceClosedLoops(kept)
    }

    private fun toSegments(poly: List<Pt>, fromA: Boolean): List<Seg> =
        poly.indices.map { i -> Seg(poly[i], poly[(i + 1) % poly.size], fromA) }

    private fun splitSegmentsAgainst(source: List<Seg>, against: List<Seg>): List<Seg> {
        val out = mutableListOf<Seg>()
        for (s in source) {
            val ts = mutableListOf(0f, 1f)
            for (o in against) {
                collectIntersectionTs(s.a, s.b, o.a, o.b, ts)
            }
            ts.sort()
            val cleanTs = mutableListOf(ts.first())
            for (i in 1 until ts.size) {
                if (ts[i] - cleanTs.last() > 1e-4f) cleanTs.add(ts[i])
            }
            for (i in 0 until cleanTs.lastIndex) {
                val t0 = cleanTs[i]
                val t1 = cleanTs[i + 1]
                val p0 = Pt(s.a.x + (s.b.x - s.a.x) * t0, s.a.y + (s.b.y - s.a.y) * t0)
                val p1 = Pt(s.a.x + (s.b.x - s.a.x) * t1, s.a.y + (s.b.y - s.a.y) * t1)
                if (p0.dist(p1) > 0.05f) out.add(Seg(p0, p1, s.fromA))
            }
        }
        return out
    }

    private fun collectIntersectionTs(a1: Pt, a2: Pt, b1: Pt, b2: Pt, ts: MutableList<Float>) {
        val dax = a2.x - a1.x
        val day = a2.y - a1.y
        val dbx = b2.x - b1.x
        val dby = b2.y - b1.y
        val denom = dax * dby - day * dbx
        val dx = b1.x - a1.x
        val dy = b1.y - a1.y

        if (abs(denom) < 1e-4f) {
            // Check collinearity
            val lenA = hypot(dax, day).coerceAtLeast(1e-4f)
            val perpDist = abs(dx * day - dy * dax) / lenA
            if (perpDist < 0.25f) {
                val lenSq = dax * dax + day * day
                if (lenSq > 1e-4f) {
                    val tb1 = ((b1.x - a1.x) * dax + (b1.y - a1.y) * day) / lenSq
                    val tb2 = ((b2.x - a1.x) * dax + (b2.y - a1.y) * day) / lenSq
                    if (tb1 in 0.001f..0.999f) ts += tb1
                    if (tb2 in 0.001f..0.999f) ts += tb2
                }
            }
            return
        }

        val t = (dx * dby - dy * dbx) / denom
        val u = (dx * day - dy * dax) / denom
        if (t in -0.001f..1.001f && u in -0.001f..1.001f) {
            val ct = t.coerceIn(0f, 1f)
            if (ct > 0.001f && ct < 0.999f) ts += ct
        }
    }

    /** Returns 1 if shared same direction, -1 if opposite, 0 if not shared. */
    private fun findSharedEdgeStatus(a: Pt, b: Pt, list: List<Seg>): Int {
        val tol = 0.35f
        for (s in list) {
            if (a.dist(s.a) < tol && b.dist(s.b) < tol) return 1
            if (a.dist(s.b) < tol && b.dist(s.a) < tol) return -1
        }
        return 0
    }

    private fun pointInPolygons(p: Pt, polys: List<List<Pt>>): Boolean {
        var inside = false
        for (poly in polys) {
            if (pointInPolygon(p, poly)) inside = !inside
        }
        return inside
    }

    private fun pointInPolygon(p: Pt, poly: List<Pt>): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val pi = poly[i]
            val pj = poly[j]
            val intersects = ((pi.y > p.y) != (pj.y > p.y)) &&
                (p.x < (pj.x - pi.x) * (p.y - pi.y) / ((pj.y - pi.y).takeIf { abs(it) > 1e-6f } ?: 1e-6f) + pi.x)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    private fun interiorSamplePoint(poly: List<Pt>): Pt {
        // Compute midpoint of first edge nudged slightly toward polygon centroid/inside
        val cx = poly.sumOf { it.x.toDouble() }.toFloat() / poly.size
        val cy = poly.sumOf { it.y.toDouble() }.toFloat() / poly.size
        val c = Pt(cx, cy)
        if (pointInPolygon(c, poly)) return c
        val a = poly[0]
        val b = poly[1]
        val mid = Pt((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val nx = -dy / len
        val ny = dx / len
        val probe1 = Pt(mid.x + nx * 0.5f, mid.y + ny * 0.5f)
        return if (pointInPolygon(probe1, poly)) probe1 else Pt(mid.x - nx * 0.5f, mid.y - ny * 0.5f)
    }

    private fun traceClosedLoops(edges: List<Pair<Pt, Pt>>): List<List<Pt>> {
        if (edges.isEmpty()) return emptyList()
        val snapTol = 0.6f
        val used = BooleanArray(edges.size)
        val loops = mutableListOf<List<Pt>>()

        for (startIdx in edges.indices) {
            if (used[startIdx]) continue
            used[startIdx] = true
            val first = edges[startIdx]
            val loop = mutableListOf(first.first, first.second)
            var currentEnd = first.second
            var prevDir = atan2((first.second.y - first.first.y).toDouble(), (first.second.x - first.first.x).toDouble())

            var guard = 0
            while (currentEnd.dist(first.first) > snapTol && guard < edges.size + 4) {
                guard++
                var bestIdx = -1
                var bestScore = Double.MAX_VALUE
                for (i in edges.indices) {
                    if (used[i]) continue
                    val e = edges[i]
                    val d = currentEnd.dist(e.first)
                    if (d <= snapTol) {
                        val dir = atan2((e.second.y - e.first.y).toDouble(), (e.second.x - e.first.x).toDouble())
                        var turn = dir - prevDir
                        while (turn <= -PI) turn += 2 * PI
                        while (turn > PI) turn -= 2 * PI
                        val score = d * 10.0 + abs(turn)
                        if (score < bestScore) {
                            bestScore = score
                            bestIdx = i
                        }
                    }
                }
                if (bestIdx < 0) break
                used[bestIdx] = true
                val nextEdge = edges[bestIdx]
                loop.add(nextEdge.second)
                prevDir = atan2(
                    (nextEdge.second.y - nextEdge.first.y).toDouble(),
                    (nextEdge.second.x - nextEdge.first.x).toDouble()
                )
                currentEnd = nextEdge.second
            }

            if (loop.size >= 3 && currentEnd.dist(first.first) <= snapTol * 2f) {
                loop.removeAt(loop.lastIndex)
                if (loop.size >= 3 && abs(signedArea(loop)) > 2f) {
                    loops.add(loop)
                }
            }
        }
        return loops
    }

    private fun simplifyCollinear(poly: List<Pt>): List<Pt> {
        if (poly.size < 4) return poly
        val out = mutableListOf<Pt>()
        val n = poly.size
        for (i in 0 until n) {
            val prev = poly[(i - 1 + n) % n]
            val curr = poly[i]
            val next = poly[(i + 1) % n]
            val v1x = curr.x - prev.x
            val v1y = curr.y - prev.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y
            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)
            if (len1 < 0.1f || len2 < 0.1f) continue
            val cross = abs(v1x * v2y - v1y * v2x) / (len1 * len2)
            val dot = (v1x * v2x + v1y * v2y) / (len1 * len2)
            if (cross > 0.015f || dot < 0f) {
                out.add(curr)
            }
        }
        return if (out.size >= 3) out else poly
    }

    private fun signedArea(poly: List<Pt>): Float {
        var area = 0f
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            area += (a.x * b.y - b.x * a.y)
        }
        return area * 0.5f
    }

    private fun lerp(a: Offset, b: Offset, t: Float): Offset =
        Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}
