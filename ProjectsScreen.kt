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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.data.ProjectEntity
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderTextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProjectSort(val label: String) {
    RECENT("Recientes"),
    NAME("Nombre"),
    SIZE("Resolución")
}

@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    onNewDocument: () -> Unit,
    onImportFile: () -> Unit,
    onOpenProject: (String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onExportProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProjectSort.RECENT) }
    var renameTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val filtered = remember(projects, search, sort) {
        val base = projects.filter {
            search.isBlank() || it.name.contains(search.trim(), ignoreCase = true)
        }
        when (sort) {
            ProjectSort.RECENT -> base.sortedByDescending { it.updatedAt }
            ProjectSort.NAME -> base.sortedBy { it.name.lowercase() }
            ProjectSort.SIZE -> base.sortedByDescending { it.widthPx * it.heightPx }
        }
    }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpiderBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Proyectos", style = MaterialTheme.typography.headlineLarge)
                Text("${projects.size} documento(s) en almacenamiento local", style = MaterialTheme.typography.bodySmall, color = SpiderTextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportFile) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Importar")
                }
                Button(onClick = onNewDocument) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo")
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Buscar por nombre...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("projects_search_input")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectSort.entries.forEach { s ->
                FilterChip(
                    selected = sort == s,
                    onClick = { sort = s },
                    label = { Text(s.label) }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filtered, key = { it.id }) { proj ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SpiderSurface)
                        .border(1.dp, SpiderBorder, RoundedCornerShape(16.dp))
                        .clickable { onOpenProject(proj.id) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0C0C12)),
                        contentAlignment = Alignment.Center
                    ) {
                        val file = proj.thumbnailPath?.let { File(it) }
                        if (file != null && file.exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(file),
                                contentDescription = proj.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(proj.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${proj.widthPx} × ${proj.heightPx} px", style = MaterialTheme.typography.labelSmall, color = SpiderTextSecondary)
                        Text(fmt.format(Date(proj.updatedAt)), style = MaterialTheme.typography.bodySmall, color = SpiderTextSecondary)
                    }
                    IconButton(onClick = {
                        renameTarget = proj
                        renameInput = proj.name
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Renombrar")
                    }
                    IconButton(onClick = { onDuplicateProject(proj.id) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicar")
                    }
                    IconButton(onClick = { onExportProject(proj.id) }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Exportar")
                    }
                    IconButton(onClick = { onDeleteProject(proj.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar")
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar proyecto") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    renameTarget?.let { onRenameProject(it.id, renameInput) }
                    renameTarget = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            }
        )
    }
}
