package com.example.model

data class VectorAssetItem(
    val id: String,
    val title: String,
    val category: String,
    val createElement: (centerX: Float, centerY: Float) -> VectorElement
)

object AssetCatalog {
    val assets: List<VectorAssetItem> = listOf(
        VectorAssetItem(
            id = "asset_spider_crest",
            title = "Emblema Araña",
            category = "Identidad"
        ) { cx, cy ->
            GroupShape(
                name = "Emblema Araña",
                x = cx - 120f,
                y = cy - 120f,
                width = 240f,
                height = 240f,
                children = listOf(
                    PolygonShape(
                        name = "Base Hexágono",
                        x = cx - 120f,
                        y = cy - 120f,
                        width = 240f,
                        height = 240f,
                        sides = 6,
                        fill = PaintStyle(colorArgb = 0xFF1F1F2E),
                        stroke = StrokeStyle(enabled = true, colorArgb = 0xFF8B5CF6, width = 6f)
                    ),
                    StarShape(
                        name = "Patas Geométricas",
                        x = cx - 80f,
                        y = cy - 80f,
                        width = 160f,
                        height = 160f,
                        points = 8,
                        innerRadiusRatio = 0.35f,
                        fill = PaintStyle(colorArgb = 0xFFA78BFA)
                    ),
                    EllipseShape(
                        name = "Centro",
                        x = cx - 26f,
                        y = cy - 26f,
                        width = 52f,
                        height = 52f,
                        fill = PaintStyle(colorArgb = 0xFFF4F4F8)
                    )
                )
            )
        },
        VectorAssetItem(
            id = "asset_pill_badge",
            title = "Insignia Cápsula",
            category = "UI & Etiquetas"
        ) { cx, cy ->
            RectShape(
                name = "Insignia Cápsula",
                x = cx - 140f,
                y = cy - 44f,
                width = 280f,
                height = 88f,
                cornerRadius = 44f,
                fill = PaintStyle(
                    type = PaintType.LINEAR_GRADIENT,
                    colorArgb = 0xFF8B5CF6,
                    secondaryColorArgb = 0xFF6D28D9,
                    gradientAngleDeg = 0f
                ),
                effects = EffectStyle(shadowEnabled = true, shadowBlur = 16f)
            )
        },
        VectorAssetItem(
            id = "asset_spark_4",
            title = "Destello 4 Puntas",
            category = "Iconos"
        ) { cx, cy ->
            StarShape(
                name = "Destello",
                x = cx - 90f,
                y = cy - 90f,
                width = 180f,
                height = 180f,
                points = 4,
                innerRadiusRatio = 0.25f,
                fill = PaintStyle(colorArgb = 0xFF38BDF8)
            )
        },
        VectorAssetItem(
            id = "asset_arrow_vector",
            title = "Flecha Derecha",
            category = "Iconos"
        ) { cx, cy ->
            val nodes = listOf(
                BezierNode(x = cx - 110f, y = cy - 28f),
                BezierNode(x = cx + 20f, y = cy - 28f),
                BezierNode(x = cx + 20f, y = cy - 72f),
                BezierNode(x = cx + 110f, y = cy),
                BezierNode(x = cx + 20f, y = cy + 72f),
                BezierNode(x = cx + 20f, y = cy + 28f),
                BezierNode(x = cx - 110f, y = cy + 28f)
            )
            PathShape(
                name = "Flecha Vectorial",
                x = cx - 110f,
                y = cy - 72f,
                width = 220f,
                height = 144f,
                nodes = nodes,
                isClosed = true,
                fill = PaintStyle(colorArgb = 0xFFF4F4F8),
                stroke = StrokeStyle(enabled = false)
            )
        },
        VectorAssetItem(
            id = "asset_bezier_wave",
            title = "Onda Bézier",
            category = "Trazados"
        ) { cx, cy ->
            val n1 = BezierNode(
                x = cx - 160f, y = cy,
                cp1X = cx - 160f, cp1Y = cy,
                cp2X = cx - 80f, cp2Y = cy - 110f,
                smooth = true
            )
            val n2 = BezierNode(
                x = cx, y = cy,
                cp1X = cx - 80f, cp1Y = cy + 110f,
                cp2X = cx + 80f, cp2Y = cy - 110f,
                smooth = true
            )
            val n3 = BezierNode(
                x = cx + 160f, y = cy,
                cp1X = cx + 80f, cp1Y = cy + 110f,
                cp2X = cx + 160f, cp2Y = cy,
                smooth = true
            )
            PathShape(
                name = "Onda Bézier",
                x = cx - 160f,
                y = cy - 80f,
                width = 320f,
                height = 160f,
                nodes = listOf(n1, n2, n3),
                isClosed = false,
                fill = PaintStyle(type = PaintType.NONE),
                stroke = StrokeStyle(enabled = true, colorArgb = 0xFFA78BFA, width = 10f)
            )
        },
        VectorAssetItem(
            id = "asset_ring_frame",
            title = "Anillo Tecnológico",
            category = "UI & Etiquetas"
        ) { cx, cy ->
            EllipseShape(
                name = "Anillo Punteado",
                x = cx - 110f,
                y = cy - 110f,
                width = 220f,
                height = 220f,
                fill = PaintStyle(type = PaintType.NONE),
                stroke = StrokeStyle(
                    enabled = true,
                    colorArgb = 0xFF8B5CF6,
                    width = 8f,
                    dashed = true,
                    dashLength = 22f,
                    gapLength = 14f
                )
            )
        }
    )
}
