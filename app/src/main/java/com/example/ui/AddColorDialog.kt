package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.utils.ColorUtils
import kotlin.math.roundToInt

@Composable
fun AddColorDialog(
    onDismiss: () -> Unit,
    onAddColor: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hexCode by remember { mutableStateOf("#FFFFFF") }

    // Sliders pour le sélecteur RYB soustractif (pigments de peintre)
    var rouge by remember { mutableFloatStateOf(0f) }
    var jaune by remember { mutableFloatStateOf(0f) }
    var bleu by remember { mutableFloatStateOf(0f) }

    fun updateFromSliders(r: Float, y: Float, b: Float) {
        rouge = r
        jaune = y
        bleu = b
        val rgb = ColorUtils.rybSubtractiveToRgb(r / 255f, y / 255f, b / 255f)
        hexCode = ColorUtils.rgbToHex(rgb[0], rgb[1], rgb[2])
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer une couleur de base", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nom et Code Hexa côte à côte
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom") },
                        placeholder = { Text("Bleu Outremer...") },
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("new_color_name_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = hexCode,
                        onValueChange = { input ->
                            val clean = input.trim().uppercase()
                            if (clean.length <= 7) {
                                hexCode = if (clean.startsWith("#") || clean.isEmpty()) clean else "#$clean"
                                if (hexCode.length == 7 && hexCode.startsWith("#")) {
                                    try {
                                        val rgb = ColorUtils.hexToRgb(hexCode)
                                        val rybSub = ColorUtils.rgbToRybSubtractive(rgb[0], rgb[1], rgb[2])
                                        rouge = rybSub[0] * 255f
                                        jaune = rybSub[1] * 255f
                                        bleu = rybSub[2] * 255f
                                        if (name.isBlank()) {
                                            val guessed = ColorUtils.getClosestColorName(hexCode)
                                            if (guessed.isNotBlank()) {
                                                name = guessed
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Ignorer les erreurs d'analyse de format hexadécimal
                                    }
                                }
                            }
                        },
                        label = { Text("Hex") },
                        placeholder = { Text("#FFFFFF") },
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("new_color_hex_input"),
                        singleLine = true
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Aperçu en temps réel compact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(hexCode.toColor())
                            .border(BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                            .testTag("new_color_preview")
                    )
                    Column {
                        Text(
                            text = "Code Hex : $hexCode",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "R : ${rouge.roundToInt()}  J : ${jaune.roundToInt()}  B : ${bleu.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Détection live du pigment C.I. normalisé le plus proche
                val pigmentMatch = ColorUtils.getClosestStandardPigment(hexCode, name)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = pigmentMatch.code,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Pigment le plus proche : ${pigmentMatch.standardName} (${pigmentMatch.matchPercentage}%)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${pigmentMatch.opacity.icon} ${pigmentMatch.opacity.label} • ${pigmentMatch.description}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Sliders de réglage RYB soustractif compacts
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Rouge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "R: ${rouge.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(55.dp)
                        )
                        Slider(
                            value = rouge,
                            onValueChange = { updateFromSliders(it, jaune, bleu) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("slider_red")
                        )
                    }

                    // Jaune
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "J: ${jaune.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(55.dp)
                        )
                        Slider(
                            value = jaune,
                            onValueChange = { updateFromSliders(rouge, it, bleu) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFDFAF37), activeTrackColor = Color(0xFFDFAF37).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("slider_yellow")
                        )
                    }

                    // Bleu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "B: ${bleu.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(55.dp)
                        )
                        Slider(
                            value = bleu,
                            onValueChange = { updateFromSliders(rouge, jaune, it) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("slider_blue")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddColor(name.trim().ifEmpty { "Couleur personnalisée" }, hexCode) },
                enabled = name.isNotBlank() && hexCode.length == 7 && hexCode.startsWith("#"),
                modifier = Modifier.testTag("confirm_add_color_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_add_color_button")
            ) {
                Text("Annuler")
            }
        }
    )
}
