package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.ProjectEntity
import com.example.model.DocumentPresets
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderCharcoal
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderSurfaceElevated
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletSoft
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    projects: List<ProjectEntity>,
    isWideScreen: Boolean,
    onNewDocument: () -> Unit,
    onOpenTemplates: () -> Unit,
    onImportFile: () -> Unit,
    onQuickPreset: (width: Int, height: Int, title: String) -> Unit,
    onOpenProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    val columns = if (isWideScreen) 3 else 2

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(SpiderBlack)
    ) {
        item(span = { GridItemSpan(columns) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Spider Studio Design",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    "Espacio de trabajo vectorial",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpiderTextSecondary
                )
            }
        }

        // Primary Action Strip
        item(span = { GridItemSpan(columns) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    title = "Nuevo lienzo",
                    subtitle = "Medida libre",
                    icon = Icons.Default.Add,
                    highlighted = true,
                    onClick = onNewDocument,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_new_doc_button")
                )
                QuickActionTile(
                    title = "Plantillas",
                    subtitle = "Editables",
                    icon = Icons.Default.DashboardCustomize,
                    highlighted = false,
                    onClick = onOpenTemplates,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_templates_button")
                )
                QuickActionTile(
                    title = "Importar",
                    subtitle = "SVG, PDF, PNG",
                    icon = Icons.Default.FileOpen,
                    highlighted = false,
                    onClick = onImportFile,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_import_button")
                )
            }
        }

        // Quick Start Presets
        item(span = { GridItemSpan(columns) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Inicio rápido por formato", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(DocumentPresets.all.take(6)) { preset ->
                        Column(
                            modifier = Modifier
                                .width(148.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SpiderSurface)
                                .border(1.dp, SpiderBorder, RoundedCornerShape(14.dp))
                                .clickable { onQuickPreset(preset.widthPx, preset.heightPx, preset.title) }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                preset.category.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SpiderVioletSoft
                            )
                            Text(
                                preset.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SpiderTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Recent Projects Header
        item(span = { GridItemSpan(columns) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Proyectos recientes (${projects.size})", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (projects.isEmpty()) {
            item(span = { GridItemSpan(columns) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SpiderCharcoal)
                        .border(1.dp, SpiderBorder, RoundedCornerShape(18.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Aún no tienes proyectos guardados. Crea tu primer lienzo o abre una plantilla editable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpiderTextSecondary
                    )
                }
            }
        } else {
            items(projects, key = { it.id }) { project ->
                ProjectGridCard(
                    project = project,
                    onOpen = { onOpenProject(project.id) },
                    onDuplicate = { onDuplicateProject(project.id) },
                    onDelete = { onDeleteProject(project.id) }
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgBrush = if (highlighted) {
        Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF5B21B6)))
    } else {
        Brush.linearGradient(listOf(SpiderSurfaceElevated, SpiderSurface))
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .border(
                width = 1.dp,
                color = if (highlighted) SpiderVioletSoft.copy(alpha = 0.6f) else SpiderBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = if (highlighted) Color.White else SpiderVioletSoft,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlighted) Color.White.copy(alpha = 0.8f) else SpiderTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ProjectGridCard(
    project: ProjectEntity,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("dd MMM · HH:mm", Locale("es", "ES")) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SpiderSurface)
            .border(1.dp, SpiderBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .background(Color(0xFF0C0C12)),
            contentAlignment = Alignment.Center
        ) {
            val thumbFile = project.thumbnailPath?.let { File(it) }
            if (thumbFile != null && thumbFile.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(thumbFile),
                    contentDescription = "Miniatura de ${project.name}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
            } else {
                Text(
                    "${project.widthPx}×${project.heightPx}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SpiderTextSecondary
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${project.widthPx}×${project.heightPx} · ${dateFmt.format(Date(project.updatedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpiderTextSecondary,
                    maxLines = 1
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de proyecto")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
