package com.example.agent.size

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.example.agent.tracker.TokenStatsDialog
import com.example.agent.tracker.TokenUsageChip
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.ColorViewModel
import kotlinx.coroutines.launch

@Composable
fun SizeActionAgentDialog(
    imageBitmap: Bitmap,
    colorViewModel: ColorViewModel,
    onApplyBrightnessContrast: (brightness: Float, contrast: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val agentService = remember { SizeAgentService() }

    var instructionText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var actionResult by remember { mutableStateOf<SizeActionResult?>(null) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    var isAddedToNuancier by remember { mutableStateOf(false) }
    var showTokenStats by remember { mutableStateOf(false) }

    if (showTokenStats) {
        TokenStatsDialog(
            currentRequestTokens = actionResult?.tokenUsage,
            onDismiss = { showTokenStats = false }
        )
    }

    val quickActions = listOf(
        "🎨 Extraire 9 couleurs" to "Observe cette photo et extrais exactement 9 teintes représentatives (dominantes claires et sombres, ombres profondes, demi-teintes, lumières, reflets et touches d'accent) avec des noms beaux-arts et codes HEX pour le nuancier.",
        "☀️ Ajuster lumière & contraste" to "Analyse la dynamique visuelle de cette photo et détermine le réglage idéal de luminosité (-100 à 100) et de contraste (-100 à 100).",
        "🍂 Tons chauds (4-5)" to "Extrais 4 à 5 teintes chaudes et terreuses présentes dans cette image avec leurs codes hexadécimaux.",
        "🌊 Tons froids (4-5)" to "Extrais 4 à 5 teintes froides et ombrées présentes dans cette image avec leurs codes hexadécimaux."
    )

    fun sendInstruction(text: String) {
        if (text.isBlank() || isLoading) return
        isLoading = true
        statusFeedback = null
        isAddedToNuancier = false
        coroutineScope.launch {
            val result = agentService.executeAction(imageBitmap, text)
            actionResult = result

            // Si c'est un ajustement lumière/contraste automatique, on l'applique ou on prépare le retour
            if (result is SizeActionResult.AdjustmentProposed) {
                onApplyBrightnessContrast(result.brightness, result.contrast)
                statusFeedback = "Luminosité (${result.brightness.toInt()}) et contraste (${result.contrast.toInt()}) appliqués !"
            }
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Opérateur IA (Action)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Extraction de palettes & réglages automatiques",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        IconButton(onClick = onDismiss, enabled = !isLoading) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Vignette photo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = imageBitmap.asImageBitmap(),
                        contentDescription = "Image active",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "L'agent analyse les pixels pour agir sur l'application",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Boutons d'actions rapides défilables
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickActions.forEach { (label, prompt) ->
                        Button(
                            onClick = {
                                instructionText = prompt
                                sendInstruction(prompt)
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = 6.dp
                            )
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Zone centrale scrollable : résultat de l'action
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(14.dp)
                        .verticalScroll(scrollState)
                ) {
                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "L'IA examine les pixels et prépare l'action...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        actionResult != null -> {
                            val clipboardManager = LocalClipboardManager.current
                            var copiedTextState by remember(actionResult) { mutableStateOf(false) }

                            Column {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    TokenUsageChip(
                                        tokenUsage = actionResult?.tokenUsage,
                                        onClick = { showTokenStats = true }
                                    )
                                }
                                SelectionContainer {
                                    when (val res = actionResult!!) {
                                    is SizeActionResult.PaletteExtracted -> {
                                        val fullPaletteText = "${res.paletteTitle}\n\n${res.explanation}\n\nCouleurs :\n" +
                                            res.colors.joinToString("\n") { "• ${it.nom} : ${it.hexCode}" }
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    "🎨 ${res.paletteTitle}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                OutlinedButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(fullPaletteText))
                                                        copiedTextState = true
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (copiedTextState) Icons.Default.Check else Icons.Default.ContentCopy,
                                                        contentDescription = "Copier la palette",
                                                        modifier = Modifier.size(13.dp),
                                                        tint = if (copiedTextState) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (copiedTextState) "Copié !" else "Copier",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (copiedTextState) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                res.explanation,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))

                                            // Pastilles de couleurs
                                            Text(
                                                "Couleurs extraites (${res.colors.size}) :",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                res.colors.forEach { colorItem ->
                                                    val parsedColor = try {
                                                        Color(android.graphics.Color.parseColor(colorItem.hexCode))
                                                    } catch (e: Exception) {
                                                        Color.Gray
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(MaterialTheme.colorScheme.surface)
                                                            .padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clip(CircleShape)
                                                                .background(parsedColor)
                                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(colorItem.nom, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text(colorItem.hexCode, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            // Bouton d'injection dans le Nuancier
                                            Button(
                                                onClick = {
                                                    val pairs = res.colors.map { it.nom to it.hexCode }
                                                    colorViewModel.addAllExtractedColorsToNuancier(pairs) {
                                                        isAddedToNuancier = true
                                                        statusFeedback = "✅ ${res.colors.size} couleurs ajoutées au Nuancier avec succès !"
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !isAddedToNuancier,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isAddedToNuancier) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                                    disabledContainerColor = Color(0xFF2E7D32),
                                                    disabledContentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(
                                                    if (isAddedToNuancier) Icons.Default.Check else Icons.Default.Palette,
                                                    contentDescription = null
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    if (isAddedToNuancier) "${res.colors.size} couleurs ajoutées au Nuancier !" 
                                                    else "Ajouter ces ${res.colors.size} couleurs au Nuancier",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    is SizeActionResult.AdjustmentProposed -> {
                                        val fullAdjText = "${res.explanation}\nLuminosité : ${res.brightness.toInt()} | Contraste : ${res.contrast.toInt()}"
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.WbSunny,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Réglage appliqué",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(fullAdjText))
                                                        copiedTextState = true
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (copiedTextState) Icons.Default.Check else Icons.Default.ContentCopy,
                                                        contentDescription = "Copier la réponse",
                                                        modifier = Modifier.size(13.dp),
                                                        tint = if (copiedTextState) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (copiedTextState) "Copié !" else "Copier",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (copiedTextState) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(res.explanation, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceAround
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Luminosité", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("${res.brightness.toInt()}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                    }
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Contraste", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("${res.contrast.toInt()}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is SizeActionResult.ArtworkProposed -> {
                                        Column {
                                            Text(
                                                text = res.artwork.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${res.artwork.artist} ${if (res.artwork.year.isNotBlank()) "(${res.artwork.year})" else ""}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (res.artwork.sourceName.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "🏛️ ${res.artwork.sourceName}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            if (res.artwork.explanation.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(res.artwork.explanation, fontSize = 13.sp)
                                            }
                                            if (res.artwork.paletteColors.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Button(
                                                    onClick = {
                                                        val pairs = res.artwork.paletteColors.map { it.name to it.hex }
                                                        colorViewModel.addAllExtractedColorsToNuancier(pairs) {
                                                            isAddedToNuancier = true
                                                            statusFeedback = "✅ ${res.artwork.paletteColors.size} couleurs du chef-d'œuvre ajoutées au Nuancier !"
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = !isAddedToNuancier,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.Palette, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        if (isAddedToNuancier) "Palette ajoutée au Nuancier !"
                                                        else "Ajouter la palette du maître au Nuancier"
                                                    )
                                                }
                                            }
                                            if (res.artwork.bitmap != null || !res.artwork.imageUrl.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val bmp = res.artwork.bitmap ?: agentService.downloadBitmap(context, res.artwork.imageUrl ?: "")
                                                            if (bmp != null) {
                                                                colorViewModel.sendImageToAnalysis(bmp)
                                                                onDismiss()
                                                            } else {
                                                                statusFeedback = "❌ Impossible de charger l'œuvre pour l'analyse"
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Default.Biotech, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Analyser l'œuvre dans l'onglet Analyse")
                                                }
                                            }
                                        }
                                    }
                                    is SizeActionResult.GeneralMessage -> {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(res.message))
                                                        copiedTextState = true
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (copiedTextState) Icons.Default.Check else Icons.Default.ContentCopy,
                                                        contentDescription = "Copier le message",
                                                        modifier = Modifier.size(13.dp),
                                                        tint = if (copiedTextState) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (copiedTextState) "Copié !" else "Copier",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (copiedTextState) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(res.message, fontSize = 13.sp)
                                        }
                                    }
                                    is SizeActionResult.Error -> {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                        ) {
                                            Text(
                                                "⚠️ ${res.errorMessage}",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Choisissez une action automatique ci-dessus ou écrivez votre consigne (ex: 'Extrais 4 teintes pastel').",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    if (statusFeedback != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                statusFeedback!!,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Champ de saisie d'instruction libre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = instructionText,
                        onValueChange = { instructionText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Votre consigne d'action...", fontSize = 13.sp) },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { sendInstruction(instructionText) },
                        enabled = instructionText.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (instructionText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Exécuter",
                            tint = if (instructionText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
