package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.SpiderBlack
import com.example.ui.theme.SpiderBorder
import com.example.ui.theme.SpiderCharcoal
import com.example.ui.theme.SpiderSurface
import com.example.ui.theme.SpiderTextSecondary
import com.example.ui.theme.SpiderViolet
import com.example.ui.theme.SpiderVioletSoft

@Composable
fun WelcomeScreen(
    lastProjectId: String?,
    onEnterStudio: () -> Unit,
    onCreateDocument: () -> Unit,
    onResumeLastProject: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpiderBlack)
    ) {
        // Hero background image with dark vignette overlay
        Image(
            painter = painterResource(id = R.drawable.img_welcome_hero),
            contentDescription = "Arte vectorial de fondo Spider Studio Design",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter),
            alpha = 0.55f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x6609090D),
                        0.45f to Color(0xEE09090D),
                        1f to SpiderBlack
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                // Custom Vector Studio Emblem
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SpiderSurface)
                            .border(1.dp, SpiderViolet.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(36.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            drawCircle(SpiderViolet, radius = 5.dp.toPx(), center = Offset(cx, cy))
                            val angles = listOf(-140f, -40f, 0f, 40f, 140f, 180f, -110f, 110f)
                            angles.forEach { deg ->
                                val rad = Math.toRadians(deg.toDouble())
                                val ex = cx + (kotlin.math.cos(rad) * size.width * 0.44f).toFloat()
                                val ey = cy + (kotlin.math.sin(rad) * size.height * 0.44f).toFloat()
                                drawLine(SpiderVioletSoft, Offset(cx, cy), Offset(ex, ey), strokeWidth = 1.8.dp.toPx())
                                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(ex, ey))
                            }
                            drawCircle(
                                color = Color.White.copy(alpha = 0.25f),
                                radius = size.width * 0.36f,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                    Column {
                        Text(
                            "SPIDER STUDIO",
                            style = MaterialTheme.typography.labelLarge,
                            color = SpiderVioletSoft
                        )
                        Text(
                            "Design Suite",
                            style = MaterialTheme.typography.titleMedium,
                            color = SpiderTextSecondary
                        )
                    }
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInVertically { it / 4 }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Precisión vectorial en tu dispositivo.",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Dibuja trazados Bézier editables, gestiona capas y tipografía avanzada, importa SVG/PDF y exporta archivos listos para producción sin depender de conexión.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SpiderTextSecondary
                        )
                    }
                }

                // Capability highlights
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SpiderCharcoal)
                        .border(1.dp, SpiderBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CapabilityRow(
                        icon = Icons.Default.Polyline,
                        title = "Motor Bézier & Nodos",
                        desc = "Pluma con manejadores suaves, lápiz inteligente y pincel."
                    )
                    CapabilityRow(
                        icon = Icons.Default.Layers,
                        title = "Capas, Degradados & Efectos",
                        desc = "Opacidad, bloqueo, sombras, desenfoque y grupos anidados."
                    )
                    CapabilityRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "Exportación Real Sin Pérdida",
                        desc = "SVG vectorial W3C, PNG transparente, JPG y PDF nativo."
                    )
                }

                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onEnterStudio,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("welcome_enter_studio_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SpiderViolet, contentColor = Color.White)
                    ) {
                        Text("Entrar al Estudio", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCreateDocument,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("welcome_new_doc_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nuevo lienzo")
                        }

                        if (lastProjectId != null) {
                            OutlinedButton(
                                onClick = { onResumeLastProject(lastProjectId) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("welcome_resume_button")
                            ) {
                                Text("Continuar último")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(SpiderViolet.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SpiderVioletSoft, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = SpiderTextSecondary)
        }
    }
}
