package com.example.agent.size

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.example.agent.tracker.TokenStatsDialog
import com.example.agent.tracker.TokenUsageChip
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.ColorViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Composable
fun SizeAdvisorAgentDialog(
    imageBitmap: Bitmap? = null,
    colorViewModel: ColorViewModel? = null,
    onDismiss: () -> Unit,
    onLoadArtworkIntoAnalysis: ((Bitmap) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val agentService = remember { SizeAgentService() }

    // Chargeur Coil avec en-tête navigateur pour autoriser Wikimedia et The Met sans blocage 403
    val customImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 ColorApp/1.0")
                            .build()
                        chain.proceed(req)
                    }
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    var questionText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var adviceResponse by remember { mutableStateOf<AdvisorReply?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // État pour afficher l'œuvre en plein écran (zoomable)
    var fullscreenArtwork by remember { mutableStateOf<ArtworkDisplayInfo?>(null) }
    var isOperatingArtwork by remember { mutableStateOf(false) }
    var showTokenStats by remember { mutableStateOf(false) }

    if (showTokenStats) {
        TokenStatsDialog(
            currentRequestTokens = adviceResponse?.tokenUsage,
            onDismiss = { showTokenStats = false }
        )
    }

    val quickQuestions = remember(imageBitmap) {
        if (imageBitmap != null) {
            listOf(
                "🍎 Nature morte (9 couleurs)" to "Montre-moi un tableau de nature morte célèbre et analyse sa composition, sa lumière et sa palette complète de 9 couleurs.",
                "🔍 Contrastes & Lumière" to "Analyse les contrastes, la répartition des ombres et la gestion de la lumière sur cette image.",
                "🎨 Harmonies & Palette (9 couleurs)" to "Quelles sont les harmonies dominantes sur cette image ? Détermine une palette complète de 9 teintes clés (ombres profondes, tons moyens, hautes lumières et touches d'accent) avec leurs noms et codes HEX pour peindre ce sujet.",
                "🎭 Autoportrait (9 couleurs)" to "Montre-moi un autoportrait célèbre de maître et analyse son expressivité, ses contrastes et sa palette de 9 couleurs.",
                "🎨 Street art" to "Montre-moi une œuvre emblématique de street art et analyse son style graphique, ses aplats et ses contrastes percutants.",
                "📐 Composition & Cadrage" to "Donne-moi une critique de la composition, du point focal et de l'équilibre des masses.",
                "💎 Vermeer (9 couleurs)" to "Montre-moi un tableau de Johannes Vermeer et analyse la délicatesse de sa lumière, ses harmonies et sa palette de 9 couleurs."
            )
        } else {
            listOf(
                "🍎 Nature morte (9 couleurs)" to "Montre-moi un tableau de nature morte célèbre et analyse sa composition, sa lumière et sa palette complète de 9 couleurs.",
                "🎭 Autoportrait (9 couleurs)" to "Montre-moi un autoportrait célèbre de maître et analyse son expressivité, ses contrastes et sa palette de 9 couleurs.",
                "🎨 Street art" to "Montre-moi une œuvre emblématique de street art et analyse son style graphique, ses aplats et ses contrastes percutants.",
                "💎 Vermeer (9 couleurs)" to "Montre-moi un tableau de Johannes Vermeer et analyse la délicatesse de sa lumière, ses harmonies et sa palette de 9 couleurs.",
                "🌊 Venise de Turner (9 couleurs)" to "Montre-moi une peinture de William Turner, sa lumière incandescente et sa palette de 9 couleurs.",
                "🌸 Impressionnisme (9 couleurs)" to "Affiche un tableau impressionniste célèbre et analyse ses touches de couleur, sa lumière et sa palette de 9 couleurs."
            )
        }
    }

    fun sendQuestion(text: String) {
        if (text.isBlank() || isLoading) return
        isLoading = true
        errorMessage = null
        adviceResponse = null
        coroutineScope.launch {
            val result = agentService.consultAdvisor(imageBitmap, text)
            result.onSuccess { reply ->
                adviceResponse = reply
            }.onFailure { err ->
                errorMessage = err.message ?: "Erreur inconnue"
            }
            isLoading = false
        }
    }

    // Fonction pour charger le tableau dans l'onglet Analyse
    fun handleLoadArtworkIntoAnalysis(artwork: ArtworkDisplayInfo) {
        if (onLoadArtworkIntoAnalysis == null) return

        isOperatingArtwork = true
        coroutineScope.launch {
            val bmp = artwork.bitmap ?: agentService.downloadBitmap(context, artwork.imageUrl ?: "")
            isOperatingArtwork = false
            if (bmp != null) {
                onLoadArtworkIntoAnalysis(bmp)
                Toast.makeText(context, "Tableau « ${artwork.title} » envoyé dans l'onglet Analyse !", Toast.LENGTH_LONG).show()
                fullscreenArtwork = null
                onDismiss()
            } else {
                Toast.makeText(context, "Échec du chargement du tableau haute résolution", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fonction pour enregistrer dans la galerie
    fun handleSaveArtwork(artwork: ArtworkDisplayInfo) {
        isOperatingArtwork = true
        coroutineScope.launch {
            val bmp = artwork.bitmap ?: agentService.downloadBitmap(context, artwork.imageUrl ?: "")
            if (bmp != null) {
                val saved = agentService.saveArtworkToGallery(context, bmp, artwork.title)
                isOperatingArtwork = false
                if (saved) {
                    Toast.makeText(context, "Tableau « ${artwork.title} » enregistré dans votre galerie !", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show()
                }
            } else {
                isOperatingArtwork = false
                Toast.makeText(context, "Impossible de télécharger l'image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 1. MODALE PRINCIPALE DE DIALOGUE AVEC L'EXPERT
    Dialog(
        onDismissRequest = { if (!isLoading && !isOperatingArtwork) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 760.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
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
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Conseiller Artistique & Musée IA",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Critique, harmonies & œuvres de maîtres",
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
                        IconButton(onClick = onDismiss, enabled = !isLoading && !isOperatingArtwork) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vignette miniature si photo présente, ou carte d'invitation si pas d'image
                if (imageBitmap != null) {
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
                            contentDescription = "Image analysée",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Modèle actif observé par l'IA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Posez des questions ou demandez un tableau de peintre",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Exploration de Tableaux de Maîtres",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Demandez un tableau (Cézanne, Monet, Rembrandt...) pour l'afficher et l'étudier.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Suggestions rapides défilables
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickQuestions.forEach { (label, prompt) ->
                        Button(
                            onClick = {
                                questionText = prompt
                                sendQuestion(prompt)
                            },
                            enabled = !isLoading && !isOperatingArtwork,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Zone centrale scrollable : Réponses ou message d'accueil
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(12.dp)
                        .verticalScroll(scrollState)
                ) {
                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "L'Expert analyse, charge l'œuvre haute définition et prépare sa réponse...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        errorMessage != null -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "⚠️ $errorMessage",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        adviceResponse != null -> {
                            val reply = adviceResponse!!
                            val clipboardManager = LocalClipboardManager.current
                            var isCopied by remember(reply) { mutableStateOf(false) }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // 1. CARTE DU TABLEAU D'ART SI PRÉSENT (GRAND AFFICHAGE)
                                reply.artwork?.let { art ->
                                    ArtworkDisplayCard(
                                        artwork = art,
                                        imageLoader = customImageLoader,
                                        isOperating = isOperatingArtwork,
                                        onOpenFullscreen = { fullscreenArtwork = art },
                                        onLoadIntoAnalysis = { handleLoadArtworkIntoAnalysis(art) },
                                        onSaveToGallery = { handleSaveArtwork(art) },
                                        onAddPaletteToNuancier = if (colorViewModel != null && art.paletteColors.isNotEmpty()) {
                                            {
                                                val pairs = art.paletteColors.map { it.name to it.hex }
                                                colorViewModel.addAllExtractedColorsToNuancier(pairs) {
                                                    Toast.makeText(context, "${art.paletteColors.size} couleurs ajoutées au Nuancier !", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else null
                                    )
                                }

                                // 2. AVIS ET TEXTE PÉDAGOGIQUE
                                if (reply.textReply.isNotBlank()) {
                                    ElevatedCard(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Lightbulb,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "Analyse & Conseils :",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TokenUsageChip(
                                                        tokenUsage = reply.tokenUsage,
                                                        onClick = { showTokenStats = true }
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    OutlinedButton(
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(reply.textReply))
                                                            isCopied = true
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                            contentDescription = "Copier",
                                                            modifier = Modifier.size(12.dp),
                                                            tint = if (isCopied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = if (isCopied) "Copié" else "Copier",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isCopied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            SelectionContainer {
                                                Text(
                                                    text = reply.textReply,
                                                    fontSize = 13.5.sp,
                                                    lineHeight = 20.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
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
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Posez une question sur votre image, ou demandez à afficher un tableau de peintre.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Ex: « Montre-moi une nature morte » ou « Un tableau de Vermeer »",
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Champ de saisie et bouton d'envoi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp, max = 100.dp),
                        placeholder = {
                            Text(
                                "Question ou « Montre-moi un tableau de... »",
                                fontSize = 13.sp
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3,
                        enabled = !isLoading && !isOperatingArtwork
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { sendQuestion(questionText) },
                        enabled = questionText.isNotBlank() && !isLoading && !isOperatingArtwork,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Envoyer")
                    }
                }
            }
        }
    }

    // 2. MODALE VISIONNEUSE PLEIN ÉCRAN (IMAGE AFFICHABLE LA PLUS GRANDE AVEC ZOOM TACTILE)
    fullscreenArtwork?.let { art ->
        ArtworkFullscreenViewerDialog(
            artwork = art,
            imageLoader = customImageLoader,
            isOperating = isOperatingArtwork,
            onDismiss = { fullscreenArtwork = null },
            onLoadIntoAnalysis = { handleLoadArtworkIntoAnalysis(art) },
            onSaveToGallery = { handleSaveArtwork(art) }
        )
    }
}

/**
 * Carte affichant le tableau avec dimensions généreuses, cartel, palette de couleurs et boutons d'action.
 */
@Composable
private fun ArtworkDisplayCard(
    artwork: ArtworkDisplayInfo,
    imageLoader: ImageLoader,
    isOperating: Boolean,
    onOpenFullscreen: () -> Unit,
    onLoadIntoAnalysis: () -> Unit,
    onSaveToGallery: () -> Unit,
    onAddPaletteToNuancier: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // En-tête : Titre & Artiste
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artwork.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(artwork.artist)
                            if (artwork.year.isNotBlank()) append(" (${artwork.year})")
                            if (artwork.movement.isNotBlank()) append(" • ${artwork.movement}")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                    if (artwork.sourceName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🏛️ ${artwork.sourceName}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Bouton Plein Écran direct
                IconButton(
                    onClick = onOpenFullscreen,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "Plein écran",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reproduction de l'image (grande taille, cliquable pour plein écran)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { onOpenFullscreen() },
                contentAlignment = Alignment.Center
            ) {
                if (artwork.bitmap != null) {
                    // 1. Affichage instantané du Bitmap pré-téléchargé en haute résolution
                    Image(
                        bitmap = artwork.bitmap!!.asImageBitmap(),
                        contentDescription = artwork.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (!artwork.imageUrl.isNullOrBlank()) {
                    // 2. Affichage via Coil avec custom ImageLoader (en-têtes et CDN vérifiés)
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artwork.imageUrl)
                            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 ColorApp/1.0")
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = artwork.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                            }
                        },
                        error = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color.LightGray)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Chargement de l'image...",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Recherche d'image...",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                // Badge indicateur « Toucher pour agrandir »
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Plein écran",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Palette de couleurs extraite de l'œuvre
            if (artwork.paletteColors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Palette de l'œuvre (${artwork.paletteColors.size} couleurs) :",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onAddPaletteToNuancier != null) {
                        TextButton(
                            onClick = onAddPaletteToNuancier,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ajouter au Nuancier", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    artwork.paletteColors.forEach { swatch ->
                        val parsedColor = try {
                            Color(android.graphics.Color.parseColor(swatch.hex))
                        } catch (e: Exception) {
                            Color.Gray
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(parsedColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = swatch.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Explication de l'œuvre
            if (artwork.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = artwork.explanation,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Boutons d'action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bouton Plein écran
                OutlinedButton(
                    onClick = onOpenFullscreen,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Plein écran", fontSize = 12.sp)
                }

                // Bouton Charger dans l'onglet Analyse
                Button(
                    onClick = onLoadIntoAnalysis,
                    enabled = !isOperating && (artwork.bitmap != null || !artwork.imageUrl.isNullOrBlank()),
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (isOperating) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Biotech, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Charger dans Analyse", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bouton Enregistrer
                FilledTonalButton(
                    onClick = onSaveToGallery,
                    enabled = !isOperating && (artwork.bitmap != null || !artwork.imageUrl.isNullOrBlank()),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Enregistrer", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Visionneuse 100% plein écran avec zoom et pan tactile (option A : l'image la plus grande possible).
 */
@Composable
private fun ArtworkFullscreenViewerDialog(
    artwork: ArtworkDisplayInfo,
    imageLoader: ImageLoader,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onLoadIntoAnalysis: () -> Unit,
    onSaveToGallery: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Reproduction avec zoom tactile et pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val maxOffset = (scale - 1f) * 600f
                                val newX = (offset.x + pan.x).coerceIn(-maxOffset, maxOffset)
                                val newY = (offset.y + pan.y).coerceIn(-maxOffset, maxOffset)
                                offset = Offset(newX, newY)
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (artwork.bitmap != null) {
                    Image(
                        bitmap = artwork.bitmap!!.asImageBitmap(),
                        contentDescription = artwork.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                } else if (!artwork.imageUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artwork.imageUrl)
                            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 ColorApp/1.0")
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = artwork.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        },
                        error = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Impossible d'afficher la reproduction", color = Color.White)
                            }
                        }
                    )
                }
            }

            // Barre supérieure d'informations et fermeture
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artwork.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                append(artwork.artist)
                                if (artwork.year.isNotBlank()) append(" (${artwork.year})")
                                if (artwork.sourceName.isNotBlank()) append(" • 🏛️ ${artwork.sourceName}")
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }

                    // Bouton Réinitialiser Zoom (si zoomé)
                    if (scale > 1.05f) {
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Réinitialiser le zoom")
                        }
                    }

                    // Bouton Fermer
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }
            }

            // Barre inférieure avec boutons d'actions
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Charger dans l'onglet Analyse
                    Button(
                        onClick = onLoadIntoAnalysis,
                        enabled = !isOperating,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isOperating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Biotech, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Charger dans l'onglet Analyse", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Enregistrer dans la galerie
                    FilledTonalButton(
                        onClick = onSaveToGallery,
                        enabled = !isOperating,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}
