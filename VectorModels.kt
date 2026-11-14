package com.example.model

import java.util.UUID

enum class DocumentBackgroundMode {
    TRANSPARENT,
    SOLID
}

enum class PaintType {
    NONE,
    SOLID,
    LINEAR_GRADIENT,
    RADIAL_GRADIENT,
    SWEEP_GRADIENT
}

data class GradientStop(
    val offset: Float,
    val colorArgb: Long
)

data class PaintStyle(
    val type: PaintType = PaintType.SOLID,
    val colorArgb: Long = 0xFF8B5CF6,
    val secondaryColorArgb: Long = 0xFF38BDF8,
    val gradientAngleDeg: Float = 45f,
    val opacity: Float = 1f,
    val stops: List<GradientStop> = emptyList(),
    val gradientCenterX: Float = 0.5f,
    val gradientCenterY: Float = 0.5f,
    val radialRadiusFactor: Float = 0.55f
)

enum class StrokeCapStyle {
    BUTT,
    ROUND,
    SQUARE
}

enum class StrokeJoinStyle {
    MITER,
    ROUND,
    BEVEL
}

data class StrokeStyle(
    val enabled: Boolean = false,
    val colorArgb: Long = 0xFFFFFFFF,
    val width: Float = 4f,
    val opacity: Float = 1f,
    val cap: StrokeCapStyle = StrokeCapStyle.ROUND,
    val join: StrokeJoinStyle = StrokeJoinStyle.ROUND,
    val dashed: Boolean = false,
    val dashLength: Float = 14f,
    val gapLength: Float = 10f
)

enum class BlendModeStyle(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiplicar"),
    SCREEN("Trama"),
    OVERLAY("Superponer"),
    DARKEN("Oscurecer"),
    LIGHTEN("Aclarar")
}

enum class ChromeStyleType(
    val label: String,
    val subtitle: String,
    val defaultTintArgb: Long,
    val secondaryTintArgb: Long
) {
    CLASSIC_CHROME("Classic Chrome", "Plata reflectante", 0xFFE2E8F0, 0xFF64748B),
    LIQUID_CHROME("Liquid Chrome", "Metal líquido", 0xFFF8FAFC, 0xFF475569),
    THREE_D_CHROME("3D Chrome", "Volumen y bisel", 0xFFD1D5DB, 0xFF1E293B),
    HOLOGRAPHIC_CHROME("Holographic Chrome", "Iridiscente", 0xFFE879F9, 0xFF22D3EE),
    GOLD_CHROME("Gold Chrome", "Dorado 24k", 0xFFFDE047, 0xFFB45309),
    ROSE_GOLD_CHROME("Rose Gold Chrome", "Oro rosado", 0xFFFBCFE8, 0xFFBE6A5D);

    fun defaultConfig(): ChromeEffectConfig = when (this) {
        CLASSIC_CHROME -> ChromeEffectConfig(
            enabled = true,
            styleType = this,
            reflectionIntensity = 0.88f,
            brightness = 0.08f,
            contrast = 1.35f,
            depth = 0.25f,
            bevelRelief = 0.65f,
            lightAngleDeg = 315f,
            metallicColorArgb = defaultTintArgb,
            reflectiveGradientBias = 0.55f,
            shadowIntensity = 0.55f,
            overallIntensity = 1.0f
        )
        LIQUID_CHROME -> ChromeEffectConfig(
            enabled = true,
            styleType = this,
            reflectionIntensity = 0.96f,
            brightness = 0.14f,
            contrast = 1.58f,
            depth = 0.30f,
            bevelRelief = 0.82f,
            lightAngleDeg = 300f,
            metallicColorArgb = defaultTintArgb,
            reflectiveGradientBias = 0.85f,
            shadowIntensity = 0.62f,
            overallIntensity = 1.0f
        )
        THREE_D_CHROME -> ChromeEffectConfig(
            enabled = true,
            styleType = this,
            reflectionIntensity = 0.86f,
            brightness = 0.04f,
            contrast = 1.45f,
            depth = 0.82f,
            bevelRelief = 0.92f,
            lightAngleDeg = 315f,
            metallicColorArgb = defaultTintArgb,
            reflectiveGradientBias = 0.50f,
            shadowIntensity = 0.80f,
            overallIntensity = 1.0f
        )
        HOLOGRAPHIC_CHROME -> ChromeEffectConfig(
            enabled = true,
            styleType = this,
            reflectionIntensity = 0.95f,
            brightness = 0.10f,
            contrast = 1.30f,
            depth = 0.35f,
            bevelRelief = 0.72f,
            lightAngleDeg = 42f,
            metallicColorArgb = defaultTintArgb,
            reflectiveGradientBias = 0.78f,
            shadowIntensity = 0.58f,
            overallIntensity = 1.0f
        )
        GOLD_CHROME -> ChromeEffectConfig(
            enabled = true,
            styleType = this,
            reflectionIntensity = 0.90f,
            brightness = 0.06f,
            contrast = 1.40f,
            depth = 0.42f,
            bevelRelief = 0.75f,
            lightAngleDeg = 320f,
            metallicColorArgb = defaultTintArgb,
            reflectiveGradientBias = 0.62f,
            shadowIntensity = 0.64f,
            overallIntensity = 1.0f
        )
        ROSE_GOLD_CHROME -> ChromeEffectConfig(
            enabled = true,
            styleType = this,
            reflectionIntensity = 0.88f,
            brightness = 0.10f,
            contrast = 1.32f,
            depth = 0.38f,
            bevelRelief = 0.74f,
            lightAngleDeg = 325f,
            metallicColorArgb = defaultTintArgb,
            reflectiveGradientBias = 0.58f,
            shadowIntensity = 0.56f,
            overallIntensity = 1.0f
        )
    }
}

data class ChromeEffectConfig(
    val enabled: Boolean = false,
    val styleType: ChromeStyleType = ChromeStyleType.CLASSIC_CHROME,
    val reflectionIntensity: Float = 0.88f,
    val brightness: Float = 0.08f,
    val contrast: Float = 1.35f,
    val depth: Float = 0.35f,
    val bevelRelief: Float = 0.70f,
    val lightAngleDeg: Float = 315f,
    val metallicColorArgb: Long = 0xFFE2E8F0,
    val reflectiveGradientBias: Float = 0.60f,
    val shadowIntensity: Float = 0.60f,
    val overallIntensity: Float = 1.0f
)

data class EffectStyle(
    val shadowEnabled: Boolean = false,
    val shadowColorArgb: Long = 0xAA000000,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 8f,
    val shadowBlur: Float = 16f,
    val shadowOpacity: Float = 0.75f,
    val blurRadius: Float = 0f,
    val glowEnabled: Boolean = false,
    val glowColorArgb: Long = 0xFF8B5CF6,
    val glowRadius: Float = 18f,
    val blendMode: BlendModeStyle = BlendModeStyle.NORMAL,
    val chrome: ChromeEffectConfig = ChromeEffectConfig()
)

enum class TextAlignMode {
    LEFT,
    CENTER,
    RIGHT
}

enum class TextCaseMode(val label: String) {
    NONE("Normal"),
    UPPERCASE("MAYÚS"),
    LOWERCASE("minús"),
    TITLE_CASE("Título")
}

enum class BrushPreset(val label: String) {
    ROUND_CALLIGRAPHIC("Caligráfico"),
    INK_PEN("Tinta"),
    SOFT_AIRBRUSH("Aerógrafo"),
    MARKER_FLAT("Marcador"),
    ARTISTIC_DASH("Trazo Mixto")
}

enum class NodeHandleMode {
    CORNER,
    SMOOTH,
    SYMMETRIC
}

data class BezierNode(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val cp1X: Float = x,
    val cp1Y: Float = y,
    val cp2X: Float = x,
    val cp2Y: Float = y,
    val smooth: Boolean = false,
    val handleMode: NodeHandleMode = if (smooth) NodeHandleMode.SMOOTH else NodeHandleMode.CORNER
)

data class PathContour(
    val id: String = UUID.randomUUID().toString(),
    val nodes: List<BezierNode>,
    val isClosed: Boolean = true
)

sealed interface VectorElement {
    val id: String
    val name: String
    val x: Float
    val y: Float
    val width: Float
    val height: Float
    val rotationDeg: Float
    val opacity: Float
    val isVisible: Boolean
    val isLocked: Boolean
    val fill: PaintStyle
    val stroke: StrokeStyle
    val effects: EffectStyle
}

data class RectShape(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Rectángulo",
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    override val opacity: Float = 1f,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val fill: PaintStyle = PaintStyle(),
    override val stroke: StrokeStyle = StrokeStyle(),
    override val effects: EffectStyle = EffectStyle(),
    val cornerRadius: Float = 0f
) : VectorElement

data class EllipseShape(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Elipse",
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    override val opacity: Float = 1f,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val fill: PaintStyle = PaintStyle(),
    override val stroke: StrokeStyle = StrokeStyle(),
    override val effects: EffectStyle = EffectStyle()
) : VectorElement

data class PolygonShape(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Polígono",
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    override val opacity: Float = 1f,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val fill: PaintStyle = PaintStyle(),
    override val stroke: StrokeStyle = StrokeStyle(),
    override val effects: EffectStyle = EffectStyle(),
    val sides: Int = 6
) : VectorElement

data class StarShape(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Estrella",
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    override val opacity: Float = 1f,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val fill: PaintStyle = PaintStyle(),
    override val stroke: StrokeStyle = StrokeStyle(),
    override val effects: EffectStyle = EffectStyle(),
    val points: Int = 5,
    val innerRadiusRatio: Float = 0.45f
) : VectorElement

data class PathShape(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Trazado",
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    override val opacity: Float = 1f,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val fill: PaintStyle = PaintStyle(type = PaintType.NONE),
    override val stroke: StrokeStyle = StrokeStyle(enabled = true, colorArgb = 0xFFF4F4F8, width = 6f),
    override val effects: EffectStyle = EffectStyle(),
    val nodes: List<BezierNode>,
    val isClosed: Boolean = false,
    val extraContours: List<PathContour> = emptyList(),
    val fillEvenOdd: Boolean = true,
    val isBrushStroke: Boolean = false,
    val pressureWidths: List<Float> = emptyList(),
    val brushPreset: BrushPreset = BrushPreset.ROUND_CALLIGRAPHIC,
    val brushTaperEnds: Boolean = true
) : VectorElement

data class TextShape(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Texto",
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    override val opacity: Float = 1f,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val fill: PaintStyle = PaintStyle(type = PaintType.SOLID, colorArgb = 0xFFF4F4F8),
    override val stroke: StrokeStyle = StrokeStyle(enabled = false, colorArgb = 0xFF8B5CF6, width = 2f),
    override val effects: EffectStyle = EffectStyle(),
    val text: String = "Diseño Vectorial",
    val fontFamilyKey: String = "space_grotesk",
    val fontSize: Float = 64f,
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val alignment: TextAlignMode = TextAlignMode.LEFT,
    val letterSpacing: Float = 0f,
    val lineHeightMultiplier: Float = 1.2f,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val textTransform: TextCaseMode = TextCaseMode.NONE,
    val curveRadius: Float = 0f,
    val backgroundBadgeEnabled: Boolean = false,
    val backgroundBadgeColorArgb: Long = 0xCC1E1E2A,
    val backgroundPaddingPx: Float = 16f
) : VectorElement

data class ImageShape(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Imagen",
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    override val opacity: Float = 1f,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val fill: PaintStyle = PaintStyle(type = PaintType.NONE),
    override val stroke: StrokeStyle = StrokeStyle(enabled = false),
    override val effects: EffectStyle = EffectStyle(),
    val imagePath: String
) : VectorElement

data class GroupShape(
    override val id: String = UUID.randomUUID().toString(),
    override val name: String = "Grupo",
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDeg: Float = 0f,
    override val opacity: Float = 1f,
    override val isVisible: Boolean = true,
    override val isLocked: Boolean = false,
    override val fill: PaintStyle = PaintStyle(type = PaintType.NONE),
    override val stroke: StrokeStyle = StrokeStyle(enabled = false),
    override val effects: EffectStyle = EffectStyle(),
    val children: List<VectorElement>,
    val hasClippingMask: Boolean = false,
    val showMaskOutline: Boolean = false
) : VectorElement

data class VectorLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Capa 1",
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1f,
    val elements: List<VectorElement> = emptyList()
)

enum class GuideOrientation {
    HORIZONTAL,
    VERTICAL
}

data class CanvasGuide(
    val id: String = UUID.randomUUID().toString(),
    val orientation: GuideOrientation,
    val positionPx: Float
)

data class SpiderDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Sin título",
    val widthPx: Int = 1080,
    val heightPx: Int = 1080,
    val backgroundMode: DocumentBackgroundMode = DocumentBackgroundMode.SOLID,
    val backgroundColorArgb: Long = 0xFF14141C,
    val layers: List<VectorLayer> = listOf(VectorLayer(name = "Capa 1")),
    val guides: List<CanvasGuide> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
