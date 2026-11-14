package com.example.model

enum class EditorTool(val label: String) {
    SELECT("Selección"),
    NODE("Nodos"),
    PEN("Pluma"),
    PENCIL("Lápiz"),
    BRUSH("Pincel"),
    RECTANGLE("Rectángulo"),
    ELLIPSE("Elipse"),
    POLYGON("Polígono"),
    STAR("Estrella"),
    TEXT("Texto"),
    HAND("Mover lienzo")
}

enum class RulerUnit(val suffix: String, val factorFromPx: Float) {
    PX("px", 1f),
    DP("dp", 0.5f),
    MM("mm", 0.084667f) // 300 DPI approx reference for print display
}

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    PNG("png", "image/png", "PNG (Sin pérdida)"),
    JPG("jpg", "image/jpeg", "JPG (Comprimido)"),
    SVG("svg", "image/svg+xml", "SVG (Vector real)"),
    PDF("pdf", "application/pdf", "PDF (Documento)"),
    SPIDER("spider", "application/json", "Proyecto .spider")
}

enum class ExportScope(val label: String) {
    FULL_PAGE("Página completa"),
    SELECTED_AREA("Área seleccionada")
}

enum class ColorInputMode(val label: String) {
    HEX("HEX"),
    RGB("RGB"),
    HSL("HSL")
}

enum class AlignAction {
    LEFT,
    CENTER_H,
    RIGHT,
    TOP,
    CENTER_V,
    BOTTOM,
    DISTRIBUTE_H,
    DISTRIBUTE_V
}

enum class ZOrderAction {
    BRING_FORWARD,
    SEND_BACKWARD,
    BRING_TO_FRONT,
    SEND_TO_BACK
}

enum class BooleanOp(val label: String) {
    UNION("Unir"),
    SUBTRACT("Restar"),
    INTERSECT("Intersección"),
    XOR("Excluir (XOR)")
}

data class SnapLine(
    val isVertical: Boolean,
    val positionDoc: Float,
    val spanStart: Float? = null,
    val spanEnd: Float? = null,
    val distanceLabel: String? = null,
    val isSpacingGuide: Boolean = false
)

data class EditorCanvasConfig(
    val showGrid: Boolean = true,
    val gridSizePx: Int = 40,
    val showRulers: Boolean = true,
    val showGuides: Boolean = true,
    val snapEnabled: Boolean = true,
    val snapToGrid: Boolean = true,
    val snapToObjects: Boolean = true,
    val snapToGuides: Boolean = true,
    val guidesLocked: Boolean = false,
    val rulerUnit: RulerUnit = RulerUnit.PX
)
