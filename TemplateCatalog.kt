package com.example.model

data class DesignTemplate(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val buildDocument: () -> SpiderDocument
)

object TemplateCatalog {
    val templates: List<DesignTemplate> = listOf(
        DesignTemplate(
            id = "tpl_spider_brand",
            title = "Identidad Spider Tech",
            category = "Marca y Logo",
            description = "Emblema geométrico con degradado violeta, anillos concéntricos y tipografía moderna."
        ) {
            SpiderDocument(
                name = "Identidad Spider Tech",
                widthPx = 1080,
                heightPx = 1080,
                backgroundMode = DocumentBackgroundMode.SOLID,
                backgroundColorArgb = 0xFF0D0D14,
                layers = listOf(
                    VectorLayer(
                        name = "Fondo y Aura",
                        elements = listOf(
                            EllipseShape(
                                name = "Aura Violeta",
                                x = 190f,
                                y = 190f,
                                width = 700f,
                                height = 700f,
                                fill = PaintStyle(
                                    type = PaintType.RADIAL_GRADIENT,
                                    colorArgb = 0x558B5CF6,
                                    secondaryColorArgb = 0x000D0D14
                                )
                            ),
                            PolygonShape(
                                name = "Hexágono Marco",
                                x = 260f,
                                y = 210f,
                                width = 560f,
                                height = 560f,
                                sides = 6,
                                fill = PaintStyle(
                                    type = PaintType.LINEAR_GRADIENT,
                                    colorArgb = 0xFF1A1A28,
                                    secondaryColorArgb = 0xFF2B1D4A,
                                    gradientAngleDeg = 135f
                                ),
                                stroke = StrokeStyle(
                                    enabled = true,
                                    colorArgb = 0xFF8B5CF6,
                                    width = 6f
                                ),
                                effects = EffectStyle(shadowEnabled = true, shadowBlur = 24f)
                            )
                        )
                    ),
                    VectorLayer(
                        name = "Símbolo Vectorial",
                        elements = listOf(
                            StarShape(
                                name = "Núcleo Araña",
                                x = 400f,
                                y = 340f,
                                width = 280f,
                                height = 280f,
                                points = 8,
                                innerRadiusRatio = 0.34f,
                                fill = PaintStyle(
                                    type = PaintType.LINEAR_GRADIENT,
                                    colorArgb = 0xFFA78BFA,
                                    secondaryColorArgb = 0xFF38BDF8,
                                    gradientAngleDeg = 45f
                                )
                            ),
                            EllipseShape(
                                name = "Núcleo Central",
                                x = 490f,
                                y = 430f,
                                width = 100f,
                                height = 100f,
                                fill = PaintStyle(colorArgb = 0xFFF4F4F8)
                            )
                        )
                    ),
                    VectorLayer(
                        name = "Tipografía",
                        elements = listOf(
                            TextShape(
                                name = "Título Principal",
                                x = 140f,
                                y = 815f,
                                width = 800f,
                                height = 96f,
                                text = "SPIDER STUDIO",
                                fontFamilyKey = "space_grotesk",
                                fontSize = 72f,
                                isBold = true,
                                alignment = TextAlignMode.CENTER,
                                letterSpacing = 0.12f,
                                fill = PaintStyle(colorArgb = 0xFFF4F4F8)
                            ),
                            TextShape(
                                name = "Subtítulo",
                                x = 190f,
                                y = 905f,
                                width = 700f,
                                height = 48f,
                                text = "DISEÑO VECTORIAL DE PRECISIÓN",
                                fontFamilyKey = "plus_jakarta_sans",
                                fontSize = 26f,
                                isBold = false,
                                alignment = TextAlignMode.CENTER,
                                letterSpacing = 0.18f,
                                fill = PaintStyle(colorArgb = 0xFFA78BFA)
                            )
                        )
                    )
                )
            )
        },
        DesignTemplate(
            id = "tpl_social_launch",
            title = "Post Lanzamiento Digital",
            category = "Redes Sociales",
            description = "Composición 1:1 lista para redes con tarjeta flotante, acentos y llamada a la acción."
        ) {
            SpiderDocument(
                name = "Post Lanzamiento Digital",
                widthPx = 1080,
                heightPx = 1080,
                backgroundMode = DocumentBackgroundMode.SOLID,
                backgroundColorArgb = 0xFF101018,
                layers = listOf(
                    VectorLayer(
                        name = "Formas Fondo",
                        elements = listOf(
                            RectShape(
                                name = "Tarjeta Base",
                                x = 90f,
                                y = 90f,
                                width = 900f,
                                height = 900f,
                                cornerRadius = 48f,
                                fill = PaintStyle(
                                    type = PaintType.LINEAR_GRADIENT,
                                    colorArgb = 0xFF1B1A2B,
                                    secondaryColorArgb = 0xFF11111A,
                                    gradientAngleDeg = 135f
                                ),
                                stroke = StrokeStyle(enabled = true, colorArgb = 0xFF312E4F, width = 4f)
                            ),
                            EllipseShape(
                                name = "Orbe Destacado",
                                x = 630f,
                                y = 140f,
                                width = 280f,
                                height = 280f,
                                fill = PaintStyle(
                                    type = PaintType.LINEAR_GRADIENT,
                                    colorArgb = 0xFF8B5CF6,
                                    secondaryColorArgb = 0xFFEC4899,
                                    gradientAngleDeg = 45f
                                ),
                                effects = EffectStyle(shadowEnabled = true, shadowBlur = 32f)
                            )
                        )
                    ),
                    VectorLayer(
                        name = "Contenido",
                        elements = listOf(
                            RectShape(
                                name = "Etiqueta Superior",
                                x = 160f,
                                y = 180f,
                                width = 280f,
                                height = 56f,
                                cornerRadius = 28f,
                                fill = PaintStyle(colorArgb = 0xFF2D2550),
                                stroke = StrokeStyle(enabled = true, colorArgb = 0xFF8B5CF6, width = 2f)
                            ),
                            TextShape(
                                name = "Texto Etiqueta",
                                x = 180f,
                                y = 192f,
                                width = 240f,
                                height = 36f,
                                text = "NUEVA EDICIÓN",
                                fontFamilyKey = "jetbrains_mono",
                                fontSize = 22f,
                                isBold = true,
                                alignment = TextAlignMode.CENTER,
                                fill = PaintStyle(colorArgb = 0xFFC4B5FD)
                            ),
                            TextShape(
                                name = "Titular",
                                x = 160f,
                                y = 390f,
                                width = 760f,
                                height = 230f,
                                text = "Arte Vectorial\nSin Límites",
                                fontFamilyKey = "space_grotesk",
                                fontSize = 86f,
                                isBold = true,
                                lineHeightMultiplier = 1.1f,
                                fill = PaintStyle(colorArgb = 0xFFF4F4F8)
                            ),
                            TextShape(
                                name = "Descripción",
                                x = 160f,
                                y = 660f,
                                width = 720f,
                                height = 100f,
                                text = "Curvas Bézier, capas editables y exportación real en SVG, PNG y PDF.",
                                fontFamilyKey = "plus_jakarta_sans",
                                fontSize = 32f,
                                isBold = false,
                                fill = PaintStyle(colorArgb = 0xFFA6A6BC)
                            ),
                            RectShape(
                                name = "Botón CTA",
                                x = 160f,
                                y = 820f,
                                width = 340f,
                                height = 84f,
                                cornerRadius = 42f,
                                fill = PaintStyle(colorArgb = 0xFF8B5CF6)
                            ),
                            TextShape(
                                name = "Texto Botón",
                                x = 180f,
                                y = 842f,
                                width = 300f,
                                height = 44f,
                                text = "Explorar Estudio",
                                fontFamilyKey = "plus_jakarta_sans",
                                fontSize = 30f,
                                isBold = true,
                                alignment = TextAlignMode.CENTER,
                                fill = PaintStyle(colorArgb = 0xFF09090D)
                            )
                        )
                    )
                )
            )
        },
        DesignTemplate(
            id = "tpl_business_card",
            title = "Tarjeta Corporativa Oscura",
            category = "Impresión",
            description = "Formato 1050×600 px elegante con retícula asimétrica y datos editables."
        ) {
            SpiderDocument(
                name = "Tarjeta Corporativa",
                widthPx = 1050,
                heightPx = 600,
                backgroundMode = DocumentBackgroundMode.SOLID,
                backgroundColorArgb = 0xFF0F1016,
                layers = listOf(
                    VectorLayer(
                        name = "Geometría",
                        elements = listOf(
                            RectShape(
                                name = "Franja Lateral",
                                x = 0f,
                                y = 0f,
                                width = 34f,
                                height = 600f,
                                fill = PaintStyle(
                                    type = PaintType.LINEAR_GRADIENT,
                                    colorArgb = 0xFF8B5CF6,
                                    secondaryColorArgb = 0xFF38BDF8,
                                    gradientAngleDeg = 90f
                                )
                            ),
                            PolygonShape(
                                name = "Sello Decorativo",
                                x = 760f,
                                y = 110f,
                                width = 200f,
                                height = 200f,
                                sides = 6,
                                fill = PaintStyle(type = PaintType.NONE),
                                stroke = StrokeStyle(enabled = true, colorArgb = 0xFF8B5CF6, width = 5f)
                            )
                        )
                    ),
                    VectorLayer(
                        name = "Datos",
                        elements = listOf(
                            TextShape(
                                name = "Nombre",
                                x = 100f,
                                y = 130f,
                                width = 620f,
                                height = 76f,
                                text = "Elena Valdés",
                                fontFamilyKey = "space_grotesk",
                                fontSize = 58f,
                                isBold = true,
                                fill = PaintStyle(colorArgb = 0xFFF4F4F8)
                            ),
                            TextShape(
                                name = "Cargo",
                                x = 100f,
                                y = 215f,
                                width = 620f,
                                height = 44f,
                                text = "Directora de Diseño Visual",
                                fontFamilyKey = "plus_jakarta_sans",
                                fontSize = 28f,
                                isBold = false,
                                fill = PaintStyle(colorArgb = 0xFFA78BFA)
                            ),
                            TextShape(
                                name = "Contacto",
                                x = 100f,
                                y = 415f,
                                width = 760f,
                                height = 90f,
                                text = "estudio@spiderdesign.art\n+34 600 123 456  ·  Madrid",
                                fontFamilyKey = "jetbrains_mono",
                                fontSize = 24f,
                                isBold = false,
                                lineHeightMultiplier = 1.4f,
                                fill = PaintStyle(colorArgb = 0xFFA6A6BC)
                            )
                        )
                    )
                )
            )
        },
        DesignTemplate(
            id = "tpl_poster_geo",
            title = "Póster Bauhaus Nocturno",
            category = "Ilustración",
            description = "Composición artística de curvas, estrellas y bloques geométricos en alta resolución."
        ) {
            SpiderDocument(
                name = "Póster Bauhaus Nocturno",
                widthPx = 1240,
                heightPx = 1754,
                backgroundMode = DocumentBackgroundMode.SOLID,
                backgroundColorArgb = 0xFF12121A,
                layers = listOf(
                    VectorLayer(
                        name = "Composición",
                        elements = listOf(
                            EllipseShape(
                                name = "Sol Violeta",
                                x = 220f,
                                y = 240f,
                                width = 800f,
                                height = 800f,
                                fill = PaintStyle(
                                    type = PaintType.LINEAR_GRADIENT,
                                    colorArgb = 0xFF7C3AED,
                                    secondaryColorArgb = 0xFFF43F5E,
                                    gradientAngleDeg = 120f
                                )
                            ),
                            RectShape(
                                name = "Bloque Inferior",
                                x = 140f,
                                y = 780f,
                                width = 960f,
                                height = 620f,
                                cornerRadius = 24f,
                                fill = PaintStyle(colorArgb = 0xE6161622),
                                stroke = StrokeStyle(enabled = true, colorArgb = 0xFFF4F4F8, width = 4f)
                            ),
                            StarShape(
                                name = "Destello",
                                x = 840f,
                                y = 660f,
                                width = 220f,
                                height = 220f,
                                points = 4,
                                innerRadiusRatio = 0.28f,
                                fill = PaintStyle(colorArgb = 0xFF38BDF8)
                            ),
                            TextShape(
                                name = "Título Póster",
                                x = 210f,
                                y = 890f,
                                width = 820f,
                                height = 220f,
                                text = "GEOMETRÍA\nEXPRESIVA",
                                fontFamilyKey = "space_grotesk",
                                fontSize = 96f,
                                isBold = true,
                                lineHeightMultiplier = 1.05f,
                                fill = PaintStyle(colorArgb = 0xFFF4F4F8)
                            ),
                            TextShape(
                                name = "Pie de Póster",
                                x = 210f,
                                y = 1240f,
                                width = 820f,
                                height = 60f,
                                text = "EXHIBICIÓN DIGITAL · SERIE 01",
                                fontFamilyKey = "jetbrains_mono",
                                fontSize = 30f,
                                isBold = false,
                                letterSpacing = 0.16f,
                                fill = PaintStyle(colorArgb = 0xFFA78BFA)
                            )
                        )
                    )
                )
            )
        }
    )
}
