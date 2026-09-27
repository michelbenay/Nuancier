package com.example.agent.calc

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.agent.tracker.TokenStatsDialog
import com.example.agent.tracker.TokenUsageChip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * ==================================================================================
 * FICHIER 3 / 3 : L'INTERFACE UTILISATEUR DE L'AGENT (DIALOGUE AVEC PARAMÈTRES)
 * ==================================================================================
 * 
 * Cette boîte de dialogue permet à l'utilisateur :
 * - De tester en 1 clic différentes suggestions démontrant la transmission de paramètres :
 *   • Sépia vintage (#704214)
 *   • Cyanotype bleu (#1B3B6F)
 *   • Sanguine rouge (#8B2500)
 *   • Noir et blanc classique (#808080)
 * - De formuler n'importe quelle demande en langage naturel (ex: "ambiance vert émeraude").
 * - De voir Gemini déduire la couleur exacte (#RRGGBB) et de l'observer dans une pastille de prévisualisation !
 */
@Composable
fun LayerAgentDialog(
    activeLayerName: String,
    activeBitmap: Bitmap,
    onApplyTransformedBitmap: (newBitmap: Bitmap, actionDescription: String) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val agentService = remember { LayerAgentService() }

    var userPrompt by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentResult by remember { mutableStateOf<LayerAgentResult?>(null) }
    var showTokenStats by remember { mutableStateOf(false) }

    if (showTokenStats) {
        TokenStatsDialog(
            currentRequestTokens = currentResult?.tokenUsage,
            onDismiss = { showTokenStats = false }
        )
    }

    fun sendInstruction(instructionText: String) {
        if (instructionText.isBlank() || isLoading) return
        userPrompt = instructionText
        isLoading = true
        currentResult = null

        coroutineScope.launch {
            val result = agentService.processInstruction(
                userPrompt = instructionText,
                activeLayerName = activeLayerName,
                activeBitmap = activeBitmap,
                onApplyTransformedBitmap = onApplyTransformedBitmap
            )
            currentResult = result
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Agent IA • Calques & Couleurs",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Calque ciblé : $activeLayerName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showTokenStats = true }) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = "Consommation de tokens",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section d'apprentissage : Suggestions illustrant la variation de paramètres
                Text(
                    text = "Suggestions pour tester l'extraction de paramètres :",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AgentSuggestionChip(
                        label = "Sépia vintage",
                        colorDot = Color(0xFF704214),
                        onClick = { sendInstruction("Passe mon calque en sépia vintage") },
                        enabled = !isLoading
                    )
                    AgentSuggestionChip(
                        label = "Bleu Cyanotype",
                        colorDot = Color(0xFF1B3B6F),
                        onClick = { sendInstruction("Donne un effet cyanotype bleu froid à ce calque") },
                        enabled = !isLoading
                    )
                    AgentSuggestionChip(
                        label = "Sanguine rouge",
                        colorDot = Color(0xFF8B2500),
                        onClick = { sendInstruction("Transforme ce calque en monochrome sanguine ocre rouge") },
                        enabled = !isLoading
                    )
                    AgentSuggestionChip(
                        label = "Noir & Blanc neutre",
                        colorDot = Color(0xFF808080),
                        onClick = { sendInstruction("Passe mon calque en noir et blanc classique") },
                        enabled = !isLoading
                    )
                }

                // Champ de saisie pour formuler toute autre phrase libre
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    label = { Text("Consigne libre à l'agent...") },
                    placeholder = { Text("Ex: Ambiance vert émeraude, ocre jaune...") },
                    trailingIcon = {
                        IconButton(
                            onClick = { sendInstruction(userPrompt) },
                            enabled = userPrompt.isNotBlank() && !isLoading,
                            modifier = Modifier.testTag("send_layer_agent_prompt_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "Envoyer")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Zone scrollable pour le résultat ou le chargement
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLoading) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    Column {
                                        Text(
                                            text = "Gemini analyse votre demande...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Déduction de la nuance et de la couleur hexadécimale...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    currentResult?.let { res ->
                        // 1. Bandeau de l'action exécutée avec prévisualisation du paramètre déduit !
                        if (res.actionsExecuted.isNotEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Paramètre déduit par Gemini & appliqué :",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Pastille visuelle de la couleur choisie par l'IA
                                        if (res.selectedColorHex != null) {
                                            val parsedColor = try {
                                                Color(android.graphics.Color.parseColor(res.selectedColorHex))
                                            } catch (e: Exception) {
                                                MaterialTheme.colorScheme.primary
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(parsedColor)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                )
                                                Text(
                                                    text = "${res.selectedColorName ?: "Teinte"} (${res.selectedColorHex})",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }

                                        res.actionsExecuted.forEach { actionLabel ->
                                            Text(
                                                text = "• $actionLabel",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Explication textuelle de l'Agent
                        item {
                            val clipboardManager = LocalClipboardManager.current
                            var isExplanationCopied by remember(res) { mutableStateOf(false) }

                            Surface(
                                color = if (res.isSuccess) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (res.isSuccess) "Réflexion de Gemini :" else "Erreur :",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TokenUsageChip(
                                                tokenUsage = res.tokenUsage,
                                                onClick = { showTokenStats = true }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    val textToCopy = if (res.errorMessage != null) "${res.explanation}\n\nDétail : ${res.errorMessage}" else res.explanation
                                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                                    isExplanationCopied = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isExplanationCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                    contentDescription = "Copier l'explication",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = if (isExplanationCopied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isExplanationCopied) "Copié !" else "Copier",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isExplanationCopied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SelectionContainer {
                                        Column {
                                            Text(
                                                text = res.explanation,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            res.errorMessage?.let { err ->
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Détail : $err",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

@Composable
private fun AgentSuggestionChip(
    label: String,
    colorDot: Color,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colorDot)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
