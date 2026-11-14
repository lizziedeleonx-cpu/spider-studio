package com.example.model

enum class PresetCategory(val label: String) {
    SOCIAL("Redes Sociales"),
    PRINT("Impresión"),
    SCREEN("Pantallas e Interfaces")
}

data class DocumentPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val widthPx: Int,
    val heightPx: Int,
    val category: PresetCategory
)

object DocumentPresets {
    val all: List<DocumentPreset> = listOf(
        DocumentPreset("ig_post", "Post Instagram", "1080 × 1080 px", 1080, 1080, PresetCategory.SOCIAL),
        DocumentPreset("ig_story", "Historia / Reel", "1080 × 1920 px", 1080, 1920, PresetCategory.SOCIAL),
        DocumentPreset("yt_thumb", "Miniatura YouTube", "1280 × 720 px", 1280, 720, PresetCategory.SOCIAL),
        DocumentPreset("social_banner", "Banner Cabecera", "1500 × 500 px", 1500, 500, PresetCategory.SOCIAL),
        DocumentPreset("print_a4", "A4 Impresión (150 DPI)", "1240 × 1754 px", 1240, 1754, PresetCategory.PRINT),
        DocumentPreset("print_a5", "A5 Postal", "874 × 1240 px", 874, 1240, PresetCategory.PRINT),
        DocumentPreset("print_card", "Tarjeta Corporativa", "1050 × 600 px", 1050, 600, PresetCategory.PRINT),
        DocumentPreset("screen_fhd", "Lienzo 16:9 HD", "1920 × 1080 px", 1920, 1080, PresetCategory.SCREEN),
        DocumentPreset("screen_icon", "Icono / Emblema", "1024 × 1024 px", 1024, 1024, PresetCategory.SCREEN)
    )

    val defaultBackgroundSwatches: List<Long> = listOf(
        0xFF14141C,
        0xFF09090D,
        0xFFFFFFFF,
        0xFFF4F4F8,
        0xFF1E1B2E,
        0xFF0F172A,
        0xFF18181B
    )
}
