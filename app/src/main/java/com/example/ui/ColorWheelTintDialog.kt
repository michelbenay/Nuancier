package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.utils.adjustImageColor
import com.example.utils.getArtisticTintName
import kotlin.math.*

/**
 * Dialogue permettant à l'artiste de corriger la dominante de couleur ou la température
 * d'une photo à l'aide d'une rosace chromatique tactile circulaire.
 */
@Composable
fun ColorWheelTintDialog(
    initialHue: Float,
    initialSaturation: Float,
    initialIntensity: Float,
    brightness: Float,
    contrast: Float,
    previewBitmap: Bitmap?,
    onApply: (hue: Float, saturation: Float, intensity: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var hue by remember { mutableFloatStateOf(initialHue) }
    var saturation by remember { mutableFloatStateOf(initialSaturation) }
    var intensity by remember { mutableFloatStateOf(if (initialIntensity <= 0f && initialSaturation > 0f) 0.35f else initialIntensity) }

    // Prévisualisation en direct avec luminosité, contraste et la teinte de la rosace
    val livePreview = remember(previewBitmap, brightness, contrast, hue, saturation, intensity) {
        if (previewBitmap != null) {
            adjustImageColor(
                bitmap = previewBitmap,
                brightness = brightness,
                contrast = contrast,
                tintHue = hue,
                tintSaturation = saturation,
                tintIntensity = intensity
            )
        } else null
    }

    val tintColorInt = remember(hue, saturation) {
        val hsv = floatArrayOf((hue % 360f + 360f) % 360f, saturation.coerceIn(0f, 1f), 1f)
        android.graphics.Color.HSVToColor(hsv)
    }
    val currentTintColor = Color(tintColorInt)
    val tintName = remember(hue, saturation) { getArtisticTintName(hue, saturation) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // En-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(currentTintColor)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Rosace de Couleur",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Correction de teinte & réchauffement",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                HorizontalDivider()

                // Petite fenêtre de prévisualisation en direct
                if (livePreview != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.08f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = livePreview.asImageBitmap(),
                            contentDescription = "Aperçu de la teinte",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Badge indicatif du filtre
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (saturation < 0.05f || intensity < 0.02f) "Neutre (sans teinte)" else "$tintName • ${(intensity * 100).roundToInt()}%",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 2. La Rosace de couleur interactive
                Text(
                    "Touchez ou glissez le doigt sur la rosace :",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ColorWheelCanvas(
                        hue = hue,
                        saturation = saturation,
                        onColorChanged = { newHue, newSat ->
                            hue = newHue
                            saturation = newSat
                            if (intensity <= 0.05f) {
                                intensity = 0.15f // Active un dosage très délicat (15%) au premier toucher
                            }
                        }
                    )
                }

                // Affichage du nom et de la pastille de couleur choisie
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(currentTintColor)
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tintName,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${hue.roundToInt()}° • Sat ${(saturation * 100).roundToInt()}%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 3. Dosage / Intensité du filtre
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Intensité de la correction :",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${(intensity * 100).roundToInt()} %",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Raccourcis rapides d'ambiances artistiques
                Text(
                    "Ambiances artistiques rapides :",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            hue = 38f // Ambre chaud délicat
                            saturation = 0.35f
                            intensity = 0.18f
                        },
                        label = { Text("☀️ Réchauffer", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            hue = 212f // Bleu ombre fraîche subtil
                            saturation = 0.30f
                            intensity = 0.15f
                        },
                        label = { Text("❄️ Refroidir", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            hue = 48f // Ocre doux
                            saturation = 0.32f
                            intensity = 0.16f
                        },
                        label = { Text("🌾 Ocre", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            hue = 0f
                            saturation = 0f
                            intensity = 0f
                        },
                        label = { Text("⚪ Neutre", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            hue = 0f
                            saturation = 0f
                            intensity = 0f
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Zéro", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onApply(hue, saturation, intensity)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Appliquer", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Canvas circulaire représentant la rosace des couleurs (Color Wheel).
 */
@Composable
private fun ColorWheelCanvas(
    hue: Float,
    saturation: Float,
    onColorChanged: (hue: Float, saturation: Float) -> Unit
) {
    // Spectre complet de l'arc-en-ciel
    val rainbowColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sizePx = constraints.maxWidth.toFloat()
        val radius = sizePx / 2f
        val center = Offset(radius, radius)

        // Position actuelle du sélecteur
        val curAngleRad = Math.toRadians(hue.toDouble()).toFloat()
        val curDist = saturation * radius
        val selectorX = center.x + curDist * cos(curAngleRad)
        val selectorY = center.y + curDist * sin(curAngleRad)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val dist = hypot(dx, dy)
                        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                        val sat = (dist / radius).coerceIn(0f, 1f)
                        onColorChanged(angle, sat)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val dx = change.position.x - center.x
                        val dy = change.position.y - center.y
                        val dist = hypot(dx, dy)
                        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                        val sat = (dist / radius).coerceIn(0f, 1f)
                        onColorChanged(angle, sat)
                    }
                }
        ) {
            // 1. Cercle chromatique avec dégradé angulaire
            drawCircle(
                brush = Brush.sweepGradient(rainbowColors, center = center),
                radius = radius,
                center = center
            )

            // 2. Dégradé radial du blanc (centre neutre) vers transparent (bordure saturée)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0.5f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // 3. Bordure extérieure délicate
            drawCircle(
                color = Color.LightGray.copy(alpha = 0.6f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 4. Cercle central repère (zone neutre)
            drawCircle(
                color = Color.Black.copy(alpha = 0.15f),
                radius = radius * 0.12f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 5. Réticule / Curseur sélecteur
            val selOffset = Offset(selectorX, selectorY)
            // Ombre portée
            drawCircle(
                color = Color.Black.copy(alpha = 0.4f),
                radius = 11.dp.toPx(),
                center = selOffset + Offset(1.5f, 1.5f)
            )
            // Contour blanc épais
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = selOffset,
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Anneau noir fin intérieur pour le contraste
            drawCircle(
                color = Color.Black.copy(alpha = 0.7f),
                radius = 7.dp.toPx(),
                center = selOffset,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}
