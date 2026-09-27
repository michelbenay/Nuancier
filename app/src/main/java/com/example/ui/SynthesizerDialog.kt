package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.SoundSynthesizer

@Composable
fun SynthesizerDialog(
    soundSynthesizer: SoundSynthesizer,
    onDismiss: () -> Unit
) {
    val isPlaying by soundSynthesizer.isPlaying.collectAsStateWithLifecycle()
    val dropVolume by soundSynthesizer.waterDropVolume.collectAsStateWithLifecycle()
    val gongVolume by soundSynthesizer.gongVolume.collectAsStateWithLifecycle()

    val maxDropDelay by soundSynthesizer.maxDropDelayMs.collectAsStateWithLifecycle()
    val maxGongDelay by soundSynthesizer.maxGongDelayMs.collectAsStateWithLifecycle()

    val dropBaseFreq by soundSynthesizer.waterDropBaseFreq.collectAsStateWithLifecycle()
    val gongFreq by soundSynthesizer.gongFrequency.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("synthesizer_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Ambiance Zen & Méditation",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Génération audio en temps réel : la goutte d'eau et le gong résonnent à des moments totalement aléatoires et libres, compris entre 2 secondes et la durée maximale configurée par chaque potentiomètre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Bouton Marche / Arrêt principal
                Button(
                    onClick = {
                        if (isPlaying) {
                            soundSynthesizer.stop()
                        } else {
                            soundSynthesizer.start()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("toggle_synth_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "Arrêter l'ambiance sonore" else "Lancer l'ambiance Zen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                HorizontalDivider()

                // Contrôle 1 : Goutte d'eau
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💧 Volume Goutte d'eau", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("${(dropVolume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = dropVolume,
                        onValueChange = { soundSynthesizer.setWaterDropVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("drop_volume_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ton / Clarté Goutte (Fréquence)", style = MaterialTheme.typography.bodySmall)
                        Text("${dropBaseFreq.toInt()} Hz", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = dropBaseFreq,
                        onValueChange = { soundSynthesizer.setWaterDropBaseFreq(it) },
                        valueRange = 200f..1200f,
                        modifier = Modifier.testTag("drop_freq_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Délai max Goutte (Aléatoire 2s à Max)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "2s à ${"%.1f".format(maxDropDelay / 1000f)}s",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = maxDropDelay.toFloat(),
                        onValueChange = { soundSynthesizer.setDropMaxDelay(it.toLong()) },
                        valueRange = 2000f..15000f,
                        modifier = Modifier.testTag("drop_interval_slider")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Contrôle 2 : Gong Grave
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔔 Volume Gong Grave", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("${(gongVolume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = gongVolume,
                        onValueChange = { soundSynthesizer.setGongVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("gong_volume_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fréquence Gong (Hauteur de note)", style = MaterialTheme.typography.bodySmall)
                        Text("${gongFreq.toInt()} Hz", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = gongFreq,
                        onValueChange = { soundSynthesizer.setGongFrequency(it) },
                        valueRange = 50f..250f,
                        modifier = Modifier.testTag("gong_freq_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Délai max Gong (Aléatoire 2s à Max)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "2s à ${"%.1f".format(maxGongDelay / 1000f)}s",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = maxGongDelay.toFloat(),
                        onValueChange = { soundSynthesizer.setGongMaxDelay(it.toLong()) },
                        valueRange = 2000f..15000f,
                        modifier = Modifier.testTag("gong_interval_slider")
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Le son continue de jouer en arrière-plan pendant que vous travaillez vos mélanges de couleurs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_synth_dialog")
            ) {
                Text("Fermer")
            }
        }
    )
}
