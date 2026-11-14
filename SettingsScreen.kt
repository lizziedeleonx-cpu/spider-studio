package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.StudioPreferences
import com.example.model.RulerUnit
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderVioletSoft

@Composable
fun SettingsScreen(
    preferences: StudioPreferences,
    onUpdateShowWelcome: (Boolean) -> Unit,
    onUpdateAutoSave: (Boolean, Int) -> Unit,
    onUpdateCanvasDefaults: (Boolean, Int, Boolean, Boolean, RulerUnit) -> Unit,
    onUpdateStylusPressure: (Boolean) -> Unit,
    onUpdateExportScale: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpiderBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Configuración del estudio", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Preferencias persistidas localmente en el dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = SpiderTextSecondary
            )

            SettingsCard("Inicio y recuperación") {
                SettingSwitchRow(
                    title = "Pantalla de bienvenida al abrir",
                    subtitle = "Muestra la identidad de Spider Studio al iniciar.",
                    checked = preferences.showWelcomeOnLaunch,
                    onCheckedChange = onUpdateShowWelcome
                )
                HorizontalDivider(color = SpiderBorder)
                SettingSwitchRow(
                    title = "Guardado automático",
                    subtitle = "Protege tus capas y trazados frente a cierres inesperados.",
                    checked = preferences.autoSaveEnabled,
                    onCheckedChange = { onUpdateAutoSave(it, preferences.autoSaveIntervalSec) }
                )
                if (preferences.autoSaveEnabled) {
                    Text("Intervalo de autoguardado", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 30, 60).forEach { sec ->
                            FilterChip(
                                selected = preferences.autoSaveIntervalSec == sec,
                                onClick = { onUpdateAutoSave(true, sec) },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                }
            }

            SettingsCard("Lienzo, reglas y ajuste magnético") {
                SettingSwitchRow(
                    title = "Mostrar cuadrícula por defecto",
                    subtitle = "Guía visual de alineación al abrir documentos.",
                    checked = preferences.defaultShowGrid,
                    onCheckedChange = {
                        onUpdateCanvasDefaults(
                            it,
                            preferences.defaultGridSizePx,
                            preferences.defaultShowRulers,
                            preferences.defaultSnapEnabled,
                            preferences.defaultRulerUnit
                        )
                    }
                )
                Text("Espaciado de cuadrícula", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20, 40, 80).forEach { sz ->
                        FilterChip(
                            selected = preferences.defaultGridSizePx == sz,
                            onClick = {
                                onUpdateCanvasDefaults(
                                    preferences.defaultShowGrid,
                                    sz,
                                    preferences.defaultShowRulers,
                                    preferences.defaultSnapEnabled,
                                    preferences.defaultRulerUnit
                                )
                            },
                            label = { Text("${sz} px") }
                        )
                    }
                }
                HorizontalDivider(color = SpiderBorder)
                SettingSwitchRow(
                    title = "Mostrar reglas por defecto",
                    subtitle = "Regla horizontal y vertical sincronizadas con el zoom.",
                    checked = preferences.defaultShowRulers,
                    onCheckedChange = {
                        onUpdateCanvasDefaults(
                            preferences.defaultShowGrid,
                            preferences.defaultGridSizePx,
                            it,
                            preferences.defaultSnapEnabled,
                            preferences.defaultRulerUnit
                        )
                    }
                )
                Text("Unidad de regla", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RulerUnit.entries.forEach { u ->
                        FilterChip(
                            selected = preferences.defaultRulerUnit == u,
                            onClick = {
                                onUpdateCanvasDefaults(
                                    preferences.defaultShowGrid,
                                    preferences.defaultGridSizePx,
                                    preferences.defaultShowRulers,
                                    preferences.defaultSnapEnabled,
                                    u
                                )
                            },
                            label = { Text(u.suffix) }
                        )
                    }
                }
                HorizontalDivider(color = SpiderBorder)
                SettingSwitchRow(
                    title = "Ajuste magnético (Snap)",
                    subtitle = "Alinea objetos a guías, bordes y centros.",
                    checked = preferences.defaultSnapEnabled,
                    onCheckedChange = {
                        onUpdateCanvasDefaults(
                            preferences.defaultShowGrid,
                            preferences.defaultGridSizePx,
                            preferences.defaultShowRulers,
                            it,
                            preferences.defaultRulerUnit
                        )
                    }
                )
            }

            SettingsCard("Lápiz digital y exportación") {
                SettingSwitchRow(
                    title = "Sensibilidad de presión (Stylus)",
                    subtitle = "Adapta el grosor del pincel cuando el dispositivo admite lápiz.",
                    checked = preferences.stylusPressureEnabled,
                    onCheckedChange = onUpdateStylusPressure
                )
                HorizontalDivider(color = SpiderBorder)
                Text("Resolución predeterminada al exportar", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 4).forEach { scale ->
                        FilterChip(
                            selected = preferences.defaultExportScale == scale,
                            onClick = { onUpdateExportScale(scale) },
                            label = { Text("${scale}x") }
                        )
                    }
                }
            }

            Text(
                "Spider Studio Design · Motor Vectorial Nativo Offline",
                style = MaterialTheme.typography.labelSmall,
                color = SpiderVioletSoft
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SpiderSurface)
            .border(1.dp, SpiderBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = SpiderVioletSoft)
        content()
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpiderTextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
