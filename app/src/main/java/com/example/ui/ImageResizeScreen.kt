package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.utils.adjustBrightnessContrast
import com.example.utils.adjustImageColor
import com.example.utils.getArtisticTintName
import com.example.agent.size.SizeAdvisorAgentDialog
import com.example.agent.size.SizeActionAgentDialog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.media.MediaScannerConnection
import kotlin.math.roundToInt

data class SelectedImageDetails(
    val uri: Uri,
    val originalWidth: Int,
    val originalHeight: Int,
    val fileSizeBytes: Long,
    val previewBitmap: Bitmap?
)

enum class ResizePreset(val label: String, val maxWidth: Int, val maxHeight: Int, val scalePercent: Float? = null) {
    FULL_HD("1920 px (Full HD)", 1920, 1920),
    HD("1280 px (HD)", 1280, 1280),
    WEB("800 px (Web / Écran)", 800, 800),
    HALF("50% de la taille", 0, 0, scalePercent = 0.5f),
    QUARTER("25% de la taille", 0, 0, scalePercent = 0.25f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageResizeScreen(
    colorViewModel: ColorViewModel? = null,
    onShowSnackbar: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var imageDetailsList by remember { mutableStateOf<List<SelectedImageDetails>>(emptyList()) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    // Dialogues des Assistants IA
    var showAdvisorDialog by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }

    var selectedPreset by remember { mutableStateOf(ResizePreset.HD) }
    var qualityPercent by remember { mutableFloatStateOf(80f) }
    var isPngFormat by remember { mutableStateOf(false) }

    var applyCorrection by remember { mutableStateOf(false) }
    var brightnessValue by remember { mutableFloatStateOf(0f) }
    var contrastValue by remember { mutableFloatStateOf(0f) }

    // Teinte & Température (Rosace de couleur)
    var showTintWheelDialog by remember { mutableStateOf(false) }
    var tintHue by remember { mutableFloatStateOf(0f) }
    var tintSaturation by remember { mutableFloatStateOf(0f) }
    var tintIntensity by remember { mutableFloatStateOf(0f) }

    // Signature / Filigrane
    var enableSignature by remember { mutableStateOf(false) }
    var signatureText by remember { mutableStateOf("© Michel Benay") }
    var signatureHasBackground by remember { mutableStateOf(true) } // true = fond transparent, false = sans fond
    var signatureAlign by remember { mutableIntStateOf(2) } // 0 = Gauche, 1 = Centre, 2 = Droite
    var signatureBottomMarginPx by remember { mutableFloatStateOf(10f) }

    var isProcessing by remember { mutableStateOf(false) }
    var processedCount by remember { mutableIntStateOf(0) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isLoadingDetails = true
            coroutineScope.launch(Dispatchers.IO) {
                val localUris = saveResizeImagesLocally(context, uris)
                withContext(Dispatchers.Main) {
                    selectedUris = localUris
                    selectedImageIndex = 0
                }
            }
        }
    }

    // Charger les photos conservées au démarrage de l'écran si aucune photo n'est encore chargée
    LaunchedEffect(Unit) {
        if (selectedUris.isEmpty()) {
            val saved = withContext(Dispatchers.IO) {
                loadSavedResizeImages(context)
            }
            if (saved.isNotEmpty()) {
                selectedUris = saved
                selectedImageIndex = 0
            }
        }
    }

    // Decode original dimensions and preview when selectedUris change
    LaunchedEffect(selectedUris) {
        if (selectedUris.isEmpty()) {
            imageDetailsList = emptyList()
            return@LaunchedEffect
        }
        isLoadingDetails = true
        imageDetailsList = withContext(Dispatchers.IO) {
            selectedUris.mapNotNull { uri ->
                try {
                    var width = 0
                    var height = 0
                    var fileSize = 0L

                    // 1. Lire la taille du fichier
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            fileSize = pfd.statSize
                        }
                    } catch (ignored: Exception) {}

                    // 2. Décoder les dimensions
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(inputStream, null, options)
                            width = options.outWidth
                            height = options.outHeight
                        }
                    } catch (ignored: Exception) {}

                    // 3. Décoder l'aperçu Bitmap
                    var previewBitmap: Bitmap? = null
                    try {
                        val sampleSize = if (width > 0 && height > 0) calculateInSampleSize(width, height, 400, 400) else 1
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize.coerceAtLeast(1)
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    inPreferredColorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                                }
                            }
                            previewBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        }
                    } catch (ignored: Exception) {}

                    if (previewBitmap != null) {
                        if (width <= 0) width = previewBitmap!!.width
                        if (height <= 0) height = previewBitmap!!.height
                    }

                    if (width > 0 && height > 0 && previewBitmap != null) {
                        SelectedImageDetails(
                            uri = uri,
                            originalWidth = width,
                            originalHeight = height,
                            fileSizeBytes = fileSize,
                            previewBitmap = previewBitmap
                        )
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
        isLoadingDetails = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête / Explication
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AspectRatio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Redimensionner vos photos",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Réduisez la taille et le poids de vos images avant de les partager ou conserver.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Rangée : Bouton de sélection (taille réduite) + 2 Assistants IA dédiés
        val activeBitmapForAgent = remember(imageDetailsList, selectedImageIndex) {
            imageDetailsList.getOrNull(selectedImageIndex.coerceIn(0, (imageDetailsList.size - 1).coerceAtLeast(0)))?.previewBitmap
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Bouton de sélection de photos principal (largeur flexible)
            Button(
                onClick = { photoPickerLauncher.launch("image/*") },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("select_photos_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (selectedUris.isEmpty()) "Photos" else "${selectedUris.size} photo(s)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Bouton pour vider/retirer les photos si des photos sont actuellement chargées
            if (selectedUris.isNotEmpty()) {
                IconButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            clearSavedResizeImages(context)
                            withContext(Dispatchers.Main) {
                                selectedUris = emptyList()
                                imageDetailsList = emptyList()
                                selectedImageIndex = 0
                                onShowSnackbar("Photos retirées")
                            }
                        }
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Retirer les photos",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 2. Bouton Conseiller Artistique IA (Dialogue de critique & conseils)
            FilledTonalButton(
                onClick = {
                    showAdvisorDialog = true
                },
                modifier = Modifier
                    .height(50.dp)
                    .testTag("ai_advisor_button"),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Conseil", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // 3. Bouton Opérateur IA (Dialogue d'actions : palettes, lumière/contraste auto)
            FilledTonalButton(
                onClick = {
                    if (activeBitmapForAgent != null) {
                        showActionDialog = true
                    } else {
                        onShowSnackbar("Sélectionnez d'abord une photo pour faire agir l'IA.")
                    }
                },
                modifier = Modifier
                    .height(50.dp)
                    .testTag("ai_action_button"),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Action", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Dialogues des Assistants IA
        if (showAdvisorDialog) {
            SizeAdvisorAgentDialog(
                imageBitmap = activeBitmapForAgent,
                colorViewModel = colorViewModel,
                onDismiss = { showAdvisorDialog = false },
                onLoadArtworkIntoAnalysis = { artworkBitmap ->
                    if (colorViewModel != null) {
                        colorViewModel.sendImageToAnalysis(artworkBitmap)
                        onShowSnackbar("Tableau chargé avec succès dans l'onglet Analyse !")
                    }
                }
            )
        }

        if (showActionDialog && activeBitmapForAgent != null && colorViewModel != null) {
            SizeActionAgentDialog(
                imageBitmap = activeBitmapForAgent,
                colorViewModel = colorViewModel,
                onApplyBrightnessContrast = { b, c ->
                    applyCorrection = true
                    brightnessValue = b
                    contrastValue = c
                    onShowSnackbar("Correction appliquée : Lumière ${b.toInt()}, Contraste ${c.toInt()}")
                },
                onDismiss = { showActionDialog = false }
            )
        }

        if (showTintWheelDialog) {
            val currentSelectedBmp = imageDetailsList.getOrNull(selectedImageIndex.coerceIn(0, (imageDetailsList.size - 1).coerceAtLeast(0)))?.previewBitmap
            ColorWheelTintDialog(
                initialHue = tintHue,
                initialSaturation = tintSaturation,
                initialIntensity = tintIntensity,
                brightness = brightnessValue,
                contrast = contrastValue,
                previewBitmap = currentSelectedBmp,
                onApply = { h, s, i ->
                    applyCorrection = true
                    tintHue = h
                    tintSaturation = s
                    tintIntensity = i
                    val tName = getArtisticTintName(h, s)
                    onShowSnackbar("Teinte appliquée : $tName (${(i * 100).roundToInt()}%)")
                },
                onDismiss = { showTintWheelDialog = false }
            )
        }

        if (isLoadingDetails) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Aperçu des photos sélectionnées & Ajustement en temps réel
        if (imageDetailsList.isNotEmpty() && !isLoadingDetails) {
            val currentDetails = imageDetailsList.getOrNull(selectedImageIndex.coerceIn(0, imageDetailsList.size - 1))
            val rawPreview = currentDetails?.previewBitmap
            val adjustedPreview = remember(
                rawPreview,
                brightnessValue,
                contrastValue,
                tintHue,
                tintSaturation,
                tintIntensity,
                applyCorrection
            ) {
                var bmp = rawPreview
                if (bmp != null && applyCorrection) {
                    val hasBC = brightnessValue != 0f || contrastValue != 0f
                    val hasTint = tintSaturation > 0.02f && tintIntensity > 0.02f
                    if (hasBC || hasTint) {
                        bmp = adjustImageColor(
                            bitmap = bmp,
                            brightness = brightnessValue,
                            contrast = contrastValue,
                            tintHue = tintHue,
                            tintSaturation = tintSaturation,
                            tintIntensity = tintIntensity
                        )
                    }
                }
                bmp
            }

            val finalPreview = remember(
                adjustedPreview,
                enableSignature,
                signatureText,
                signatureHasBackground,
                signatureAlign,
                signatureBottomMarginPx
            ) {
                if (adjustedPreview == null) null
                else if (enableSignature && signatureText.isNotBlank()) {
                    applySignatureOverlay(
                        sourceBitmap = adjustedPreview.copy(Bitmap.Config.ARGB_8888, true),
                        text = signatureText,
                        hasBackground = signatureHasBackground,
                        align = signatureAlign,
                        bottomMarginPx = signatureBottomMarginPx.roundToInt()
                    )
                } else {
                    adjustedPreview
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Aperçu photo (${selectedImageIndex + 1}/${imageDetailsList.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (currentDetails != null) {
                            Text(
                                "${currentDetails.originalWidth}x${currentDetails.originalHeight} px • ${formatFileSize(currentDetails.fileSizeBytes)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Zone d'affichage de la grande image avec aperçu en temps réel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val displayBmp = finalPreview ?: adjustedPreview
                        if (displayBmp != null) {
                            Image(
                                bitmap = displayBmp.asImageBitmap(),
                                contentDescription = "Aperçu photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.Gray
                            )
                        }

                        val hasActiveAdjustment = applyCorrection && (brightnessValue != 0f || contrastValue != 0f || (tintSaturation > 0.02f && tintIntensity > 0.02f))
                        if (hasActiveAdjustment || enableSignature) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    "Aperçu temps réel",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Sélection de vignettes si plusieurs images
                    if (imageDetailsList.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(imageDetailsList) { index, details ->
                                val isSelected = index == selectedImageIndex
                                val thumbnailRaw = details.previewBitmap
                                val thumbnailAdjusted = remember(thumbnailRaw, brightnessValue, contrastValue, tintHue, tintSaturation, tintIntensity, applyCorrection) {
                                    if (thumbnailRaw != null && applyCorrection) {
                                        val hasBC = brightnessValue != 0f || contrastValue != 0f
                                        val hasTint = tintSaturation > 0.02f && tintIntensity > 0.02f
                                        if (hasBC || hasTint) {
                                            adjustImageColor(
                                                bitmap = thumbnailRaw,
                                                brightness = brightnessValue,
                                                contrast = contrastValue,
                                                tintHue = tintHue,
                                                tintSaturation = tintSaturation,
                                                tintIntensity = tintIntensity
                                            )
                                        } else {
                                            thumbnailRaw
                                        }
                                    } else {
                                        thumbnailRaw
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.LightGray.copy(alpha = 0.3f))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedImageIndex = index }
                                ) {
                                    if (thumbnailAdjusted != null) {
                                        Image(
                                            bitmap = thumbnailAdjusted.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Contrôles d'ajustement de l'image (Luminosité, Contraste, Teinte rosace)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ajuster l'image (Lumière, Teinte) :", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = applyCorrection,
                            onCheckedChange = { applyCorrection = it },
                            modifier = Modifier.testTag("toggle_correction_switch")
                        )
                    }

                    if (applyCorrection) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(12.dp)
                        ) {
                            // 1. Luminosité
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Luminosité :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${if (brightnessValue > 0) "+" else ""}${brightnessValue.roundToInt()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = brightnessValue,
                                    onValueChange = { brightnessValue = it },
                                    valueRange = -100f..100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 2. Contraste
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Contraste :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${if (contrastValue > 0) "+" else ""}${contrastValue.roundToInt()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = contrastValue,
                                    onValueChange = { contrastValue = it },
                                    valueRange = -100f..100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            // 3. Teinte & Température (Rosace Chromatique)
                            val hasTintActive = tintSaturation > 0.03f && tintIntensity > 0.03f
                            val currentTintColorInt = if (hasTintActive) {
                                val hsv = floatArrayOf((tintHue % 360f + 360f) % 360f, tintSaturation, 1f)
                                android.graphics.Color.HSVToColor(hsv)
                            } else 0
                            val currentTintName = remember(tintHue, tintSaturation) { getArtisticTintName(tintHue, tintSaturation) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (hasTintActive) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color(currentTintColorInt))
                                                .border(1.dp, Color.White, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        Icon(
                                            Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column {
                                        Text(
                                            "Teinte (Rosace) :",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            if (hasTintActive) "$currentTintName (${(tintIntensity * 100).roundToInt()}%)" else "Neutre (sans filtre)",
                                            fontSize = 11.sp,
                                            color = if (hasTintActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (hasTintActive) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (hasTintActive) {
                                        IconButton(
                                            onClick = {
                                                tintHue = 0f
                                                tintSaturation = 0f
                                                tintIntensity = 0f
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Effacer la teinte",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    FilledTonalButton(
                                        onClick = { showTintWheelDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (hasTintActive) "Modifier" else "Rosace...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // Bouton de réinitialisation complète si un réglage est actif
                            if (brightnessValue != 0f || contrastValue != 0f || hasTintActive) {
                                TextButton(
                                    onClick = {
                                        brightnessValue = 0f
                                        contrastValue = 0f
                                        tintHue = 0f
                                        tintSaturation = 0f
                                        tintIntensity = 0f
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tout réinitialiser", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Options de redimensionnement
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Paramètres de redimensionnement",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Choix de la taille (Preset)
                    Text("Dimensions cibles :", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ResizePreset.values().forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset },
                                label = { Text(preset.label, fontSize = 13.sp) },
                                leadingIcon = if (selectedPreset == preset) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider()

                    // Qualité / Compression (pour JPEG)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Qualité de compression :", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${qualityPercent.roundToInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = qualityPercent,
                            onValueChange = { qualityPercent = it },
                            valueRange = 30f..100f,
                            steps = 13,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()

                    // Format
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Format d'enregistrement :", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = !isPngFormat,
                                onClick = { isPngFormat = false },
                                label = { Text("JPG", fontSize = 12.sp) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = isPngFormat,
                                onClick = { isPngFormat = true },
                                label = { Text("PNG", fontSize = 12.sp) }
                            )
                        }
                    }

                    // Calcul de la taille estimée pour la première image
                    val first = imageDetailsList.firstOrNull()
                    if (first != null && first.originalWidth > 0) {
                        val targetDims = calculateTargetDimensions(
                            first.originalWidth,
                            first.originalHeight,
                            selectedPreset
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Aperçu estimation :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    "${first.originalWidth}x${first.originalHeight} ➔ ${targetDims.first}x${targetDims.second} px",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Carte Bande de signature / Filigrane
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Draw,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bande de signature / filigrane :", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = enableSignature,
                            onCheckedChange = { enableSignature = it },
                            modifier = Modifier.testTag("toggle_signature_switch")
                        )
                    }

                    if (enableSignature) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(12.dp)
                        ) {
                            // Champ de texte
                            OutlinedTextField(
                                value = signatureText,
                                onValueChange = { signatureText = it },
                                label = { Text("Texte de la signature", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Choix du fond (avec fond transparent vs sans fond)
                            Text("Style du fond :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = signatureHasBackground,
                                    onClick = { signatureHasBackground = true },
                                    label = { Text("Fond transparent", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !signatureHasBackground,
                                    onClick = { signatureHasBackground = false },
                                    label = { Text("Sans fond", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Alignement (Gauche, Centre, Droite)
                            Text("Alignement du texte :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(0 to "Gauche", 1 to "Centre", 2 to "Droite").forEach { (idx, label) ->
                                    FilterChip(
                                        selected = signatureAlign == idx,
                                        onClick = { signatureAlign = idx },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Distance du bas (curseur ~50px ajustable)
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Distance du bas :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${signatureBottomMarginPx.roundToInt()} px",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = signatureBottomMarginPx,
                                    onValueChange = { signatureBottomMarginPx = it },
                                    valueRange = 0f..25f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Bouton de lancement
            if (isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Traitement en cours ($processedCount / ${imageDetailsList.size})...",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        isProcessing = true
                        processedCount = 0
                        coroutineScope.launch {
                            val savedCount = processAndSaveImages(
                                context = context,
                                detailsList = imageDetailsList,
                                preset = selectedPreset,
                                quality = qualityPercent.roundToInt(),
                                isPng = isPngFormat,
                                brightness = brightnessValue,
                                contrast = contrastValue,
                                tintHue = tintHue,
                                tintSaturation = tintSaturation,
                                tintIntensity = tintIntensity,
                                applyCorrection = applyCorrection,
                                enableSignature = enableSignature,
                                signatureText = signatureText,
                                signatureHasBackground = signatureHasBackground,
                                signatureAlign = signatureAlign,
                                signatureBottomMarginPx = signatureBottomMarginPx,
                                onProgress = { count -> processedCount = count }
                            )
                            isProcessing = false
                            if (savedCount > 0) {
                                onShowSnackbar("✅ $savedCount photo(s) traitée(s) et enregistrée(s) dans Galerie / Photos !")
                            } else {
                                onShowSnackbar("⚠️ Erreur lors de l'enregistrement des images.")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("execute_resize_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Traiter et enregistrer (${imageDetailsList.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Helpers pour le calcul des dimensions et le traitement
private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun calculateTargetDimensions(
    origW: Int,
    origH: Int,
    preset: ResizePreset
): Pair<Int, Int> {
    if (origW <= 0 || origH <= 0) return Pair(100, 100)
    
    if (preset.scalePercent != null) {
        val newW = (origW * preset.scalePercent).roundToInt().coerceAtLeast(1)
        val newH = (origH * preset.scalePercent).roundToInt().coerceAtLeast(1)
        return Pair(newW, newH)
    }

    val maxW = preset.maxWidth
    val maxH = preset.maxHeight

    if (origW <= maxW && origH <= maxH) {
        return Pair(origW, origH)
    }

    val ratio = origW.toFloat() / origH.toFloat()
    return if (origW > origH) {
        val targetW = maxW
        val targetH = (maxW / ratio).roundToInt().coerceAtLeast(1)
        Pair(targetW, targetH)
    } else {
        val targetH = maxH
        val targetW = (maxH * ratio).roundToInt().coerceAtLeast(1)
        Pair(targetW, targetH)
    }
}

private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "Taille inconnue"
    val kb = sizeInBytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format("%.1f Mo", mb)
    } else {
        String.format("%.0f Ko", kb)
    }
}

private fun applySignatureOverlay(
    sourceBitmap: Bitmap,
    text: String,
    hasBackground: Boolean,
    align: Int, // 0 = Gauche, 1 = Centre, 2 = Droite
    bottomMarginPx: Int
): Bitmap {
    if (text.isBlank()) return sourceBitmap

    val resultBitmap = if (sourceBitmap.isMutable) sourceBitmap else sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(resultBitmap)

    val width = resultBitmap.width
    val height = resultBitmap.height

    // Taille de police proportionnelle à la taille finale de l'image (2x plus petite pour rester discrète)
    val refDim = minOf(width, height)
    val fontSize = (refDim * 0.0175f).coerceIn(8f, 65f)

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSize
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    val textBounds = android.graphics.Rect()
    textPaint.getTextBounds(text, 0, text.length, textBounds)
    val textWidth = textPaint.measureText(text)
    val textHeight = textBounds.height().toFloat()

    // Hauteur de bande fine et proportionnelle
    val bandHeight = (textHeight * 1.8f).coerceAtLeast(fontSize * 1.3f)

    // Calcul de la marge du bas
    val scaleFactor = height / 1080f
    val effectiveMarginBottom = (bottomMarginPx * scaleFactor).coerceAtLeast(bottomMarginPx * 0.5f)

    val bandBottom = (height - effectiveMarginBottom).coerceIn(bandHeight, height.toFloat())
    val bandTop = bandBottom - bandHeight

    if (hasBackground) {
        // Bande claire semi-transparente discrète (blanc opacité ~60%)
        val bandPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(150, 255, 255, 255)
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, bandTop, width.toFloat(), bandBottom, bandPaint)

        // Texte sombre contrasté
        textPaint.color = android.graphics.Color.rgb(25, 25, 25)
        textPaint.clearShadowLayer()
    } else {
        // Sans fond : texte blanc pur avec ombre portée douce
        textPaint.color = android.graphics.Color.WHITE
        textPaint.setShadowLayer(fontSize * 0.2f, 1.5f, 1.5f, android.graphics.Color.argb(200, 0, 0, 0))
    }

    // Alignement horizontal discret
    val horizontalPadding = (width * 0.02f).coerceIn(8f, 30f)
    val x = when (align) {
        0 -> horizontalPadding // Gauche
        1 -> (width - textWidth) / 2f // Centre
        else -> width - textWidth - horizontalPadding // Droite
    }

    // Position Y centrée dans la bande
    val y = bandTop + (bandHeight + textHeight) / 2f - textBounds.bottom

    canvas.drawText(text, x, y, textPaint)

    return resultBitmap
}

private suspend fun processAndSaveImages(
    context: Context,
    detailsList: List<SelectedImageDetails>,
    preset: ResizePreset,
    quality: Int,
    isPng: Boolean,
    brightness: Float,
    contrast: Float,
    tintHue: Float = 0f,
    tintSaturation: Float = 0f,
    tintIntensity: Float = 0f,
    applyCorrection: Boolean,
    enableSignature: Boolean,
    signatureText: String,
    signatureHasBackground: Boolean,
    signatureAlign: Int,
    signatureBottomMarginPx: Float,
    onProgress: (Int) -> Unit
): Int = withContext(Dispatchers.IO) {
    var successCount = 0

    val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val mimeType = if (isPng) "image/png" else "image/jpeg"
    val extension = if (isPng) "png" else "jpg"

    detailsList.forEachIndexed { index, details ->
        try {
            val targetDimsInitial = calculateTargetDimensions(details.originalWidth, details.originalHeight, preset)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    details.originalWidth,
                    details.originalHeight,
                    targetDimsInitial.first,
                    targetDimsInitial.second
                ).coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    inPreferredColorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                }
            }

            var fullBitmap = context.contentResolver.openInputStream(details.uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            if (fullBitmap == null && details.previewBitmap != null && !details.previewBitmap.isRecycled) {
                fullBitmap = details.previewBitmap.copy(Bitmap.Config.ARGB_8888, true)
            }

            if (fullBitmap != null) {
                // 1. Correction luminosité / contraste / teinte rosace
                val hasAdjustment = applyCorrection && (brightness != 0f || contrast != 0f || (tintSaturation > 0.02f && tintIntensity > 0.02f))
                val bitmapToProcess = if (hasAdjustment) {
                    adjustImageColor(
                        bitmap = fullBitmap,
                        brightness = brightness,
                        contrast = contrast,
                        tintHue = tintHue,
                        tintSaturation = tintSaturation,
                        tintIntensity = tintIntensity
                    )
                } else {
                    fullBitmap
                }

                // 2. Redimensionnement (Resize)
                val targetDims = calculateTargetDimensions(bitmapToProcess.width, bitmapToProcess.height, preset)
                var finalBitmap = if (bitmapToProcess.width == targetDims.first && bitmapToProcess.height == targetDims.second) {
                    bitmapToProcess
                } else {
                    Bitmap.createScaledBitmap(bitmapToProcess, targetDims.first, targetDims.second, true)
                }

                // 3. Signature appliquée APRÈS le resize pour une netteté de texte maximale
                if (enableSignature && signatureText.isNotBlank()) {
                    finalBitmap = applySignatureOverlay(
                        sourceBitmap = finalBitmap,
                        text = signatureText,
                        hasBackground = signatureHasBackground,
                        align = signatureAlign,
                        bottomMarginPx = signatureBottomMarginPx.roundToInt()
                    )
                }

                val fileName = "Resized_${System.currentTimeMillis()}_${index + 1}"
                var isSaved = false

                // Enregistrement MediaStore standard (Pictures/ColorApp_Resized)
                try {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.$extension")
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ColorApp_Resized")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }

                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            finalBitmap.compress(format, quality, outputStream)
                            outputStream.flush()
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            context.contentResolver.update(uri, contentValues, null, null)
                        }
                        isSaved = true
                    }
                } catch (e: Throwable) {
                    android.util.Log.w("ColorApp_Resize", "MediaStore insert 1 failed", e)
                }

                // Fallback MediaStore Pictures racine
                if (!isSaved) {
                    try {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.$extension")
                            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                            }
                        }

                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                finalBitmap.compress(format, quality, outputStream)
                                outputStream.flush()
                            }
                            isSaved = true
                        }
                    } catch (e: Throwable) {
                        android.util.Log.w("ColorApp_Resize", "MediaStore insert 2 failed", e)
                    }
                }

                // Fallback direct File public Pictures
                if (!isSaved) {
                    try {
                        val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ColorApp_Resized")
                        if (!picturesDir.exists()) picturesDir.mkdirs()
                        val outFile = File(picturesDir, "$fileName.$extension")
                        FileOutputStream(outFile).use { out ->
                            finalBitmap.compress(format, quality, out)
                            out.flush()
                        }
                        MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), arrayOf(mimeType), null)
                        isSaved = true
                    } catch (e: Throwable) {
                        android.util.Log.w("ColorApp_Resize", "Direct file save failed", e)
                    }
                }

                if (isSaved) {
                    successCount++
                }

                if (finalBitmap != bitmapToProcess && finalBitmap != fullBitmap) {
                    finalBitmap.recycle()
                }
                if (bitmapToProcess != fullBitmap) {
                    bitmapToProcess.recycle()
                }
                fullBitmap.recycle()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            android.util.Log.e("ColorApp_Resize", "Error processing image", t)
        }
        withContext(Dispatchers.Main) {
            onProgress(index + 1)
        }
    }

    successCount
}

/**
 * Sauvegarde localement les images sélectionnées dans le stockage privé persistant de l'application
 * pour qu'elles restent disponibles même si l'utilisateur quitte l'application.
 */
private fun saveResizeImagesLocally(context: Context, uris: List<Uri>): List<Uri> {
    val dir = File(context.filesDir, "saved_resize_images")
    if (dir.exists()) {
        dir.deleteRecursively()
    }
    dir.mkdirs()

    val localUris = mutableListOf<Uri>()
    uris.forEachIndexed { index, uri ->
        try {
            val file = File(dir, "photo_$index.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            if (file.exists() && file.length() > 0) {
                localUris.add(Uri.fromFile(file))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return localUris
}

/**
 * Récupère les photos persistées dans le stockage privé de l'application.
 */
private fun loadSavedResizeImages(context: Context): List<Uri> {
    val dir = File(context.filesDir, "saved_resize_images")
    if (!dir.exists()) return emptyList()
    val files = dir.listFiles { f -> f.isFile && f.length() > 0 } ?: return emptyList()
    return files.sortedBy { it.name }.map { Uri.fromFile(it) }
}

/**
 * Supprime les photos persistées pour l'onglet Size.
 */
private fun clearSavedResizeImages(context: Context) {
    val dir = File(context.filesDir, "saved_resize_images")
    if (dir.exists()) {
        dir.deleteRecursively()
    }
}
