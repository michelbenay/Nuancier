package com.example.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.BaseColor
import com.example.utils.ColorUtils
import com.example.utils.FalseColorMode
import com.example.utils.adjustBrightnessContrast
import com.example.utils.generateFalseColorBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorDecompositionScreen(
    baseColors: List<BaseColor>,
    allPrimaryColors: List<BaseColor>,
    onApplyRecipe: (List<ColorUtils.RecipeComponent>, String) -> Unit,
    modifier: Modifier = Modifier,
    onToggleColorInPalette: ((BaseColor) -> Unit)? = null,
    pendingImageBitmap: Bitmap? = null,
    onConsumePendingImage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageUrlInput by remember { mutableStateOf("") }
    var currentImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentImageUrl by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }

    // Saisie directe d'un code Hexadécimal pour analyse sans photo
    var directHexInput by remember { mutableStateOf("") }

    var rawOriginalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Mode fausses couleurs et opacité
    var falseColorMode by remember { mutableStateOf(FalseColorMode.ORIGINAL) }
    var falseColorOpacity by remember { mutableFloatStateOf(1.0f) }
    var isSavingImage by remember { mutableStateOf(false) }

    var brightnessValue by remember { mutableFloatStateOf(0f) }
    var contrastValue by remember { mutableFloatStateOf(0f) }

    var isLoading by remember { mutableStateOf(false) }
    var isCalculatingDecomposition by remember { mutableStateOf(false) }

    var pickedColorHex by remember { mutableStateOf<String?>(null) }
    var pickedColorName by remember { mutableStateOf<String?>(null) }
    var lastPixelX by remember { mutableStateOf<Int?>(null) }
    var lastPixelY by remember { mutableStateOf<Int?>(null) }
    var tapPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

    // État du zoom et déplacement dans l'image
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var useAllPaletteColors by remember { mutableStateOf(true) }
    var decompositionResult by remember { mutableStateOf<ColorUtils.ColorDecompositionResult?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            currentImageUri = uri
            currentImageUrl = null
            pickedColorHex = null
            zoomScale = 1f
            panOffset = Offset.Zero
        }
    }

    fun saveBitmapLocally(bitmap: Bitmap) {
        try {
            val file = File(context.filesDir, "saved_decomp_photo.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Réception d'une image transmise directement par un agent (ex: tableau d'un maître)
    LaunchedEffect(pendingImageBitmap) {
        val incoming = pendingImageBitmap ?: return@LaunchedEffect
        rawOriginalBitmap = incoming
        originalBitmap = incoming
        activeBitmap = incoming
        displayBitmap = incoming
        currentImageUri = null
        currentImageUrl = null
        brightnessValue = 0f
        contrastValue = 0f
        zoomScale = 1f
        panOffset = Offset.Zero
        saveBitmapLocally(incoming)

        // Sélectionner par défaut le pixel central de la photo pour analyse immédiate
        val cx = incoming.width / 2
        val cy = incoming.height / 2
        lastPixelX = cx
        lastPixelY = cy
        var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
        val radius = 2
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val px = (cx + dx).coerceIn(0, incoming.width - 1)
                val py = (cy + dy).coerceIn(0, incoming.height - 1)
                val p = incoming.getPixel(px, py)
                sumR += (p shr 16) and 0xFF
                sumG += (p shr 8) and 0xFF
                sumB += p and 0xFF
                count++
            }
        }
        val pixel = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
        val hex = String.format("#%06X", 0xFFFFFF and pixel)
        pickedColorHex = hex
        pickedColorName = ColorUtils.getClosestColorName(hex)

        onConsumePendingImage?.invoke()
    }

    LaunchedEffect(currentImageUri, currentImageUrl) {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            var loadedBitmap: Bitmap? = null
            var shouldSave = true
            try {
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        inPreferredColorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                    }
                }
                if (currentImageUri != null) {
                    context.contentResolver.openInputStream(currentImageUri!!)?.use { stream ->
                        loadedBitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
                    }
                } else if (currentImageUrl != null) {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(currentImageUrl)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val drawable = result.drawable
                        if (drawable is android.graphics.drawable.BitmapDrawable) {
                            loadedBitmap = drawable.bitmap
                        }
                    }
                } else {
                    val savedFile = File(context.filesDir, "saved_decomp_photo.jpg")
                    if (savedFile.exists()) {
                        loadedBitmap = BitmapFactory.decodeFile(savedFile.absolutePath, decodeOptions)
                        shouldSave = false
                    }
                }
            } catch (e: Exception) {
                loadedBitmap = null
            }

            if (loadedBitmap != null) {
                withContext(Dispatchers.Main) {
                    rawOriginalBitmap = loadedBitmap
                    originalBitmap = loadedBitmap
                    activeBitmap = loadedBitmap
                    displayBitmap = loadedBitmap
                    brightnessValue = 0f
                    contrastValue = 0f
                    if (shouldSave) saveBitmapLocally(loadedBitmap!!)
                    isLoading = false

                    // Sélectionner par défaut le pixel central de la photo si aucune couleur n'est encore choisie
                    if (pickedColorHex == null && loadedBitmap != null) {
                        val cx = loadedBitmap!!.width / 2
                        val cy = loadedBitmap!!.height / 2
                        lastPixelX = cx
                        lastPixelY = cy
                        var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                        val radius = 2
                        for (dx in -radius..radius) {
                            for (dy in -radius..radius) {
                                val px = (cx + dx).coerceIn(0, loadedBitmap!!.width - 1)
                                val py = (cy + dy).coerceIn(0, loadedBitmap!!.height - 1)
                                val p = loadedBitmap!!.getPixel(px, py)
                                sumR += (p shr 16) and 0xFF
                                sumG += (p shr 8) and 0xFF
                                sumB += p and 0xFF
                                count++
                            }
                        }
                        val pixel = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                        val hex = String.format("#%06X", 0xFFFFFF and pixel)
                        pickedColorHex = hex
                        pickedColorName = ColorUtils.getClosestColorName(hex)
                    }
                }
            } else {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    // Calcul de l'aperçu en temps réel (Luminosité/Contraste + Fausses Couleurs + Opacité)
    LaunchedEffect(brightnessValue, contrastValue, originalBitmap, falseColorMode, falseColorOpacity) {
        val src = originalBitmap ?: return@LaunchedEffect
        kotlinx.coroutines.delay(35) // Permet aux curseurs (opacité, luminosité, contraste) de glisser avec fluidité sans saccade
        withContext(Dispatchers.Default) {
            val adjusted = if (brightnessValue == 0f && contrastValue == 0f) {
                src
            } else {
                adjustBrightnessContrast(src, brightnessValue, contrastValue)
            }

            val finalBmp = if (falseColorMode == FalseColorMode.ORIGINAL || falseColorOpacity <= 0f) {
                adjusted
            } else {
                generateFalseColorBitmap(adjusted, falseColorMode, falseColorOpacity)
            }

            withContext(Dispatchers.Main) {
                displayBitmap = finalBmp
            }
        }
    }

    // Calcul automatique des composantes/recette de la couleur ciblée
    LaunchedEffect(pickedColorHex, useAllPaletteColors, baseColors, allPrimaryColors) {
        val target = pickedColorHex ?: return@LaunchedEffect
        val pool = if (useAllPaletteColors) allPrimaryColors else baseColors
        val candidateColors = pool.map { Pair(it.name, it.hexCode) }

        if (candidateColors.isEmpty()) {
            decompositionResult = null
            return@LaunchedEffect
        }

        isCalculatingDecomposition = true
        withContext(Dispatchers.Default) {
            val res = ColorUtils.findBestDecomposition(target, candidateColors)
            withContext(Dispatchers.Main) {
                decompositionResult = res
                isCalculatingDecomposition = false
            }
        }
    }

    val listState = rememberLazyListState()

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Charger une image par lien Web", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = imageUrlInput,
                    onValueChange = { imageUrlInput = it },
                    placeholder = { Text("https://example.com/image.jpg", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (imageUrlInput.isNotBlank()) {
                            currentImageUrl = imageUrlInput.trim()
                            currentImageUri = null
                            pickedColorHex = null
                            showUrlDialog = false
                        }
                    },
                    enabled = imageUrlInput.isNotBlank()
                ) {
                    Text("Charger")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. BARRE UNIFIÉE ET ALLÉGÉE : PHOTO & SAISIE HEXA SUR LA MÊME LIGNE ---
        item {
            val parsedLiveHex: String? = run {
                val clean = directHexInput.trim().removePrefix("#").uppercase()
                if (clean.length == 6 && clean.all { it in "0123456789ABCDEF" }) "#$clean" else null
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Bouton Choisir Photo
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.testTag("decomp_pick_photo_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Bouton Optionnel Lien URL
                    IconButton(
                        onClick = { showUrlDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Lien Web",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Saisie directe Hexa compacte
                    OutlinedTextField(
                        value = directHexInput,
                        onValueChange = { directHexInput = it },
                        placeholder = { Text("#Hex", fontSize = 11.5.sp) },
                        singleLine = true,
                        leadingIcon = {
                            if (parsedLiveHex != null) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(parsedLiveHex.toColor(), CircleShape)
                                        .border(1.dp, Color.White, CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ColorLens,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        trailingIcon = {
                            if (directHexInput.isNotEmpty()) {
                                IconButton(onClick = { directHexInput = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer", modifier = Modifier.size(13.dp))
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("direct_hex_input")
                    )

                    // Bouton validation Hexa
                    Button(
                        onClick = {
                            if (parsedLiveHex != null) {
                                pickedColorHex = parsedLiveHex
                                pickedColorName = ColorUtils.getClosestColorName(parsedLiveHex)
                                tapPosition = null
                            }
                        },
                        enabled = parsedLiveHex != null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("analyze_direct_hex_button")
                    ) {
                        Icon(Icons.Default.Biotech, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("OK", fontSize = 12.sp)
                    }
                }
            }
        }

        // --- 2. TOGGLE BOUTONS FAUSSES COULEURS + CURSEUR D'OPACITÉ JUSTE AU-DESSUS DE L'IMAGE ---
        if (originalBitmap != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FalseColorMode.values().forEach { mode ->
                            val isSelected = falseColorMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { falseColorMode = mode },
                                label = {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("false_color_mode_${mode.name}")
                            )
                        }
                    }

                    // Curseur d'opacité (si fausses couleurs) + Bouton d'enregistrement de l'aperçu en taille réelle
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (falseColorMode != FalseColorMode.ORIGINAL) {
                                Icon(
                                    imageVector = Icons.Default.Opacity,
                                    contentDescription = "Opacité",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Opacité ${(falseColorOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(74.dp)
                                )
                                Slider(
                                    value = falseColorOpacity,
                                    onValueChange = { falseColorOpacity = it },
                                    valueRange = 0.1f..1.0f,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Text(
                                    text = "Photo Naturelle",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                )
                            }

                            // Bouton d'enregistrement en taille réelle (même dossier que l'onglet Size)
                            Button(
                                onClick = {
                                    val src = originalBitmap ?: rawOriginalBitmap
                                    if (src != null && !isSavingImage) {
                                        isSavingImage = true
                                        scope.launch {
                                            val ok = savePreviewImageToPictures(
                                                context = context,
                                                baseBitmap = src,
                                                brightness = brightnessValue,
                                                contrast = contrastValue,
                                                falseColorMode = falseColorMode,
                                                falseColorOpacity = falseColorOpacity
                                            )
                                            isSavingImage = false
                                            if (ok) {
                                                Toast.makeText(context, "✅ Image enregistrée dans Galerie / Photos (ColorApp_Resized)", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "⚠️ Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isSavingImage && (originalBitmap != null || rawOriginalBitmap != null),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.testTag("save_preview_image_button")
                            ) {
                                if (isSavingImage) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Enregistrer l'image",
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("Enregistrer", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // --- 3. APERÇU PHOTO INTERACTIF (COLOR PICKER AVEC ZOOM & DÉPLACEMENT) ---
        item {
            val imgToDisplay = displayBitmap ?: activeBitmap
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) {
                    val boxWidth = constraints.maxWidth.toFloat()
                    val boxHeight = constraints.maxHeight.toFloat()

                    if (imgToDisplay != null && boxWidth > 0 && boxHeight > 0) {
                        val bmp = imgToDisplay
                        val baseScale = minOf(boxWidth / bmp.width, boxHeight / bmp.height)
                        val dispW = bmp.width * baseScale
                        val dispH = bmp.height * baseScale
                        val baseOffsetX = (boxWidth - dispW) / 2f
                        val baseOffsetY = (boxHeight - dispH) / 2f
                        val centerX = boxWidth / 2f
                        val centerY = boxHeight / 2f

                        // Bornes de déplacement en fonction du niveau de zoom
                        val maxPanX = (dispW * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                        val maxPanY = (dispH * (zoomScale - 1f) / 2f).coerceAtLeast(0f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(imgToDisplay, zoomScale, panOffset) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newZoom = (zoomScale * zoom).coerceIn(1f, 10f)
                                        val newMaxPanX = (dispW * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                        val newMaxPanY = (dispH * (newZoom - 1f) / 2f).coerceAtLeast(0f)

                                        zoomScale = newZoom
                                        panOffset = if (newZoom <= 1f) {
                                            Offset.Zero
                                        } else {
                                            Offset(
                                                (panOffset.x + pan.x).coerceIn(-newMaxPanX, newMaxPanX),
                                                (panOffset.y + pan.y).coerceIn(-newMaxPanY, newMaxPanY)
                                            )
                                        }
                                    }
                                }
                                .pointerInput(imgToDisplay, zoomScale, panOffset) {
                                    detectTapGestures { offset ->
                                        // Transformation inverse : du repère écran vers l'image originale
                                        val untransX = (offset.x - centerX - panOffset.x) / zoomScale + centerX
                                        val untransY = (offset.y - centerY - panOffset.y) / zoomScale + centerY

                                        val relX = untransX - baseOffsetX
                                        val relY = untransY - baseOffsetY

                                        val cx = (relX / baseScale).toInt().coerceIn(0, bmp.width - 1)
                                        val cy = (relY / baseScale).toInt().coerceIn(0, bmp.height - 1)
                                        lastPixelX = cx
                                        lastPixelY = cy

                                        // Toujours prélever la VRAIE couleur calibrée de l'image (pour une recette exacte de peinture)
                                        val sampleSrc = activeBitmap ?: originalBitmap ?: bmp
                                        var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                                        val radius = 2
                                        for (dx in -radius..radius) {
                                            for (dy in -radius..radius) {
                                                val px = (cx + dx).coerceIn(0, sampleSrc.width - 1)
                                                val py = (cy + dy).coerceIn(0, sampleSrc.height - 1)
                                                val p = sampleSrc.getPixel(px, py)
                                                sumR += (p shr 16) and 0xFF
                                                sumG += (p shr 8) and 0xFF
                                                sumB += p and 0xFF
                                                count++
                                            }
                                        }
                                        val pixelInt = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                                        val hex = String.format("#%06X", 0xFFFFFF and pixelInt)
                                        val name = ColorUtils.getClosestColorName(hex)
                                        pickedColorHex = hex
                                        pickedColorName = name
                                    }
                                }
                        ) {
                            // 1. IMAGE AVEC COUCHE GRAPHIQUE ZOOM & TRANSLATION
                            androidx.compose.foundation.Image(
                                bitmap = imgToDisplay.asImageBitmap(),
                                contentDescription = "Image source",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = panOffset.x
                                        translationY = panOffset.y
                                    }
                            )

                            // 2. VISEUR CIBLE HAUTE PRÉCISION (Reste verrouillé sur le pixel exact même en zoomant)
                            if (lastPixelX != null && lastPixelY != null) {
                                val density = LocalDensity.current
                                val untransX = baseOffsetX + (lastPixelX!! + 0.5f) * baseScale
                                val untransY = baseOffsetY + (lastPixelY!! + 0.5f) * baseScale
                                val screenX = (untransX - centerX) * zoomScale + centerX + panOffset.x
                                val screenY = (untransY - centerY) * zoomScale + centerY + panOffset.y

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { screenX.toDp() - 16.dp },
                                            y = with(density) { screenY.toDp() - 16.dp }
                                        )
                                        .size(32.dp)
                                        .border(2.dp, Color.White, CircleShape)
                                        .border(1.dp, Color.Black.copy(alpha = 0.7f), CircleShape)
                                ) {
                                    // Point central avec la couleur prélevée
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.Center)
                                            .background(pickedColorHex?.toColor() ?: Color.Red, CircleShape)
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                }
                            }
                        }

                        // 3. BARRE FLOTTANTE DE COMMANDES DE ZOOM (En haut à droite)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 6.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                // Bouton Zoom -
                                IconButton(
                                    onClick = {
                                        val newZoom = (zoomScale / 1.5f).coerceAtLeast(1f)
                                        zoomScale = newZoom
                                        if (newZoom <= 1f) panOffset = Offset.Zero
                                    },
                                    enabled = zoomScale > 1.05f,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Dézoomer", modifier = Modifier.size(18.dp))
                                }

                                // Indicateur Niveau de Zoom
                                Text(
                                    text = String.format("%.1fx", zoomScale),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (zoomScale > 1.05f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                // Bouton Zoom +
                                IconButton(
                                    onClick = {
                                        val newZoom = (zoomScale * 1.5f).coerceAtMost(10f)
                                        zoomScale = newZoom
                                    },
                                    enabled = zoomScale < 9.9f,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Zoomer", modifier = Modifier.size(18.dp))
                                }

                                // Bouton Recentrer / 1x (si zoomé ou déplacé)
                                if (zoomScale > 1.05f || panOffset != Offset.Zero) {
                                    VerticalDivider(
                                        modifier = Modifier
                                            .height(20.dp)
                                            .padding(horizontal = 2.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.FitScreen, contentDescription = "Recentrer", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // 4. BADGE FLOTTANT DE LA COULEUR PRÉLEVÉE & COORDONNÉES (En haut à gauche)
                        if (pickedColorHex != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                tonalElevation = 4.dp,
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(pickedColorHex!!.toColor(), CircleShape)
                                            .border(1.5.dp, Color.White, CircleShape)
                                            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                                    )
                                    Column {
                                        Text(
                                            text = pickedColorHex!!.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (lastPixelX != null && lastPixelY != null) {
                                            Text(
                                                text = "px (${lastPixelX}, ${lastPixelY})",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. INDICATION D'AIDE DISCRÈTE (En bas au centre)
                        if (zoomScale <= 1.05f) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = "Pincez pour zoomer • Touchez pour prélever",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        // État quand aucune image n'est chargée
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pickedColorHex != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(pickedColorHex!!.toColor())
                                            .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                                            .border(3.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    )
                                    Text(
                                        text = "Couleur créée : ${pickedColorName ?: pickedColorHex}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Code Hex : ${pickedColorHex!!.uppercase()} • Décomposition calculée ci-dessous",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    "Saisissez un code hexa ci-dessus ou chargez une photo pour démarrer l'analyse de composantes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text("Chargement de l'image...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // --- 3b. CORRECTION LUMINOSITÉ & CONTRASTE (PRÉVISUALISATION TEMPS RÉEL) ---
        if (originalBitmap != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Correction de l'image (Luminosité & Contraste)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Slider Luminosité
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Luminosité", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (brightnessValue > 0) "+${brightnessValue.toInt()}" else "${brightnessValue.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = brightnessValue,
                                onValueChange = { brightnessValue = it },
                                valueRange = -100f..100f,
                                modifier = Modifier.testTag("decomp_brightness_slider")
                            )
                        }

                        // Slider Contraste
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Contraste", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (contrastValue > 0) "+${contrastValue.toInt()}" else "${contrastValue.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = contrastValue,
                                onValueChange = { contrastValue = it },
                                valueRange = -100f..100f,
                                modifier = Modifier.testTag("decomp_contrast_slider")
                            )
                        }

                        Text(
                            text = if (brightnessValue != 0f || contrastValue != 0f)
                                "⚡ Aperçu en temps réel. Cliquez sur 'Valider la correction' pour appliquer le réglage."
                            else
                                "Ajustez la luminosité ou le contraste pour voir l'aperçu en temps réel.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val corrected = displayBitmap ?: originalBitmap
                                    if (corrected != null) {
                                        originalBitmap = corrected
                                        activeBitmap = corrected
                                        displayBitmap = corrected
                                        brightnessValue = 0f
                                        contrastValue = 0f

                                        val targetBmp = corrected
                                        val cx = (lastPixelX ?: (targetBmp.width / 2)).coerceIn(0, targetBmp.width - 1)
                                        val cy = (lastPixelY ?: (targetBmp.height / 2)).coerceIn(0, targetBmp.height - 1)

                                        var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                                        val radius = 2
                                        for (dx in -radius..radius) {
                                            for (dy in -radius..radius) {
                                                val px = (cx + dx).coerceIn(0, targetBmp.width - 1)
                                                val py = (cy + dy).coerceIn(0, targetBmp.height - 1)
                                                val p = targetBmp.getPixel(px, py)
                                                sumR += (p shr 16) and 0xFF
                                                sumG += (p shr 8) and 0xFF
                                                sumB += p and 0xFF
                                                count++
                                            }
                                        }
                                        val pixelInt = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                                        val hex = String.format("#%06X", 0xFFFFFF and pixelInt)
                                        val name = ColorUtils.getClosestColorName(hex)
                                        pickedColorHex = hex
                                        pickedColorName = name
                                    }
                                },
                                enabled = displayBitmap != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("decomp_validate_correction_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Valider la correction")
                            }

                            if (brightnessValue != 0f || contrastValue != 0f || (rawOriginalBitmap != null && originalBitmap != rawOriginalBitmap)) {
                                OutlinedButton(
                                    onClick = {
                                        brightnessValue = 0f
                                        contrastValue = 0f
                                        if (rawOriginalBitmap != null) {
                                            originalBitmap = rawOriginalBitmap
                                            activeBitmap = rawOriginalBitmap
                                            displayBitmap = rawOriginalBitmap

                                            val targetBmp = rawOriginalBitmap!!
                                            val cx = (lastPixelX ?: (targetBmp.width / 2)).coerceIn(0, targetBmp.width - 1)
                                            val cy = (lastPixelY ?: (targetBmp.height / 2)).coerceIn(0, targetBmp.height - 1)

                                            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                                            val radius = 2
                                            for (dx in -radius..radius) {
                                                for (dy in -radius..radius) {
                                                    val px = (cx + dx).coerceIn(0, targetBmp.width - 1)
                                                    val py = (cy + dy).coerceIn(0, targetBmp.height - 1)
                                                    val p = targetBmp.getPixel(px, py)
                                                    sumR += (p shr 16) and 0xFF
                                                    sumG += (p shr 8) and 0xFF
                                                    sumB += p and 0xFF
                                                    count++
                                                }
                                            }
                                            val pixelInt = (0xFF shl 24) or ((sumR / count) shl 16) or ((sumG / count) shl 8) or (sumB / count)
                                            val hex = String.format("#%06X", 0xFFFFFF and pixelInt)
                                            val name = ColorUtils.getClosestColorName(hex)
                                            pickedColorHex = hex
                                            pickedColorName = name
                                        }
                                    },
                                    modifier = Modifier.testTag("decomp_reset_correction_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Réinitialiser")
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. COULEUR CIBLE PRÉLEVÉE & CHOIX DE LA PALETTE BASE ---
        if (pickedColorHex != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (tapPosition != null && (displayBitmap ?: activeBitmap) != null) "Couleur Cible Prélevée sur Photo" else "Couleur Cible Analysée",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(pickedColorHex!!.toColor())
                                    .border(1.5.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pickedColorName ?: "Couleur ciblée",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Code Hex : ${pickedColorHex!!.uppercase()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val ryb = ColorUtils.hexToRyb(pickedColorHex!!)
                                val rVal = (ryb[0] * 255f).roundToInt()
                                val yVal = (ryb[1] * 255f).roundToInt()
                                val bVal = (ryb[2] * 255f).roundToInt()
                                Text(
                                    text = "Valeurs RYB (Rouge, Jaune, Bleu) : R:$rVal  J:$yVal  B:$bVal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Affichage du pigment normalisé le plus proche du code Hexa
                        val targetPigment = ColorUtils.getClosestStandardPigment(pickedColorHex!!, pickedColorName ?: "")
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = targetPigment.code,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Pigment le plus proche : ${targetPigment.standardName} (${targetPigment.matchPercentage}%)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${targetPigment.opacity.icon} ${targetPigment.opacity.label} • ${targetPigment.description}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. RÉSULTAT DE LA DÉCOMPOSITION (COMPOSANTES & POIDS) ---
        if (pickedColorHex != null) {
            item {
                val result = decompositionResult
                val pool = if (useAllPaletteColors) allPrimaryColors else baseColors

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Composantes & Recette de Mélange",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Choix des couleurs utilisables pour la décomposition (en tête de carte)
                        Text(
                            text = "Trouver les composantes dans :",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = !useAllPaletteColors,
                                onClick = { useAllPaletteColors = false },
                                label = { Text("Mon Nuancier (${baseColors.size})") },
                                leadingIcon = {
                                    if (!useAllPaletteColors) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = useAllPaletteColors,
                                onClick = { useAllPaletteColors = true },
                                label = { Text("Toute la Palette (${allPrimaryColors.size})") },
                                leadingIcon = {
                                    if (useAllPaletteColors) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isCalculatingDecomposition) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    "Actualisation des proportions en cours...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (pool.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Aucune couleur disponible dans " + (if (useAllPaletteColors) "la Palette" else "votre Nuancier") + ". Ajoutez des couleurs pour calculer les composantes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else if (result != null) {
                            // Avertissement Gamut sRVB (limite d'affichage écran vs peinture physique)
                            val targetGamut = ColorUtils.analyzeGamut(result.targetHex, pickedColorName ?: "")
                            if (targetGamut.isNearOrOutGamut) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("💡", fontSize = 16.sp)
                                        Column {
                                            Text(
                                                text = "Avertissement Espace Couleurs (Hors-Gamut sRVB)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Text(
                                                text = targetGamut.warningText ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Badge de fidélité / score de correspondance
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fidélité visuelle estimée :",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Surface(
                                    color = if (result.matchScorePercent >= 90) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "⚡ ${result.matchScorePercent}% de correspondance",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (result.matchScorePercent >= 90) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Comparaison visuelle Côte à Côte : Couleur Cible vs Mélange Simulé
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Cible", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(result.targetHex.toColor())
                                            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(result.targetHex.uppercase(), style = MaterialTheme.typography.labelSmall)
                                }

                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Mélange Obtenu", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(result.simulatedHex.toColor())
                                            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(result.simulatedHex.uppercase(), style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // Synthèse du comportement d'opacité du mélange
                            val mixtureOpacityProfile = ColorUtils.getMixtureOpacityProfile(result.recipeItems)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "🎨 Propriété physique :",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = mixtureOpacityProfile,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Divider()

                            // Liste détaillée des composantes avec codes Hexa et poids/proportions
                            Text(
                                text = "Composantes nécessaires (${result.recipeItems.size} couleurs) :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                result.recipeItems.forEach { item ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(10.dp),
                                        elevation = CardDefaults.cardElevation(1.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(item.hexCode.toColor())
                                                        .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                )

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = item.colorName,
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        if (item.pigmentCode.isNotEmpty()) {
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = item.pigmentCode,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${item.opacityIcon} ${item.opacityLabel}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = "• ${item.hexCode.uppercase()}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }

                                                    // Statut de coche dans la palette pour affectation au nuancier
                                                    val matchingPaletteColor = allPrimaryColors.find {
                                                        it.hexCode.equals(item.hexCode, ignoreCase = true) || it.name.equals(item.colorName, ignoreCase = true)
                                                    } ?: baseColors.find {
                                                        it.hexCode.equals(item.hexCode, ignoreCase = true) || it.name.equals(item.colorName, ignoreCase = true)
                                                    }
                                                    val isAlreadyInNuancier = matchingPaletteColor?.isInBaseList == true

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        if (isAlreadyInNuancier) {
                                                            Surface(
                                                                color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Check,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(11.dp),
                                                                        tint = Color(0xFF2E7D32)
                                                                    )
                                                                    Spacer(Modifier.width(3.dp))
                                                                    Text(
                                                                        text = "Cochée dans la palette",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = Color(0xFF2E7D32),
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                                                shape = RoundedCornerShape(4.dp),
                                                                modifier = if (matchingPaletteColor != null && onToggleColorInPalette != null) {
                                                                    Modifier.clickable { onToggleColorInPalette(matchingPaletteColor) }
                                                                } else Modifier
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Add,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(11.dp),
                                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                                                    )
                                                                    Spacer(Modifier.width(3.dp))
                                                                    Text(
                                                                        text = "Non cochée (sera affectée)",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "${item.parts} part${if (item.parts > 1) "s" else ""} (${item.percentage}%)",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            // Jauge visuelle de la proportion dans le mélange
                                            LinearProgressIndicator(
                                                progress = { item.percentage / 100f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = item.hexCode.toColor(),
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            val uncheckedCount = result.recipeItems.count { item ->
                                val match = allPrimaryColors.find {
                                    it.hexCode.equals(item.hexCode, ignoreCase = true) || it.name.equals(item.colorName, ignoreCase = true)
                                }
                                match != null && !match.isInBaseList
                            }

                            // Boutons d'actions principales
                            Button(
                                onClick = {
                                    onApplyRecipe(result.recipeItems, pickedColorName ?: pickedColorHex ?: "")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("apply_recipe_to_mixer_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Tester / Appliquer ce mélange dans le Nuancier", fontWeight = FontWeight.Bold)
                                    if (uncheckedCount > 0) {
                                        Text(
                                            text = "($uncheckedCount composante${if (uncheckedCount > 1) "s" else ""} de la palette ${if (uncheckedCount > 1) "seront cochées" else "sera cochée"} automatiquement)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
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
}

/**
 * Enregistre l'image telle qu'elle apparaît dans le preview mais en pleine résolution (taille réelle)
 * dans le même répertoire que l'onglet Size (ColorApp_Resized dans Pictures).
 */
private suspend fun savePreviewImageToPictures(
    context: android.content.Context,
    baseBitmap: Bitmap,
    brightness: Float,
    contrast: Float,
    falseColorMode: FalseColorMode,
    falseColorOpacity: Float
): Boolean = withContext(Dispatchers.IO) {
    try {
        // 1. Application des ajustements luminosité/contraste en pleine résolution
        val adjusted = if (brightness == 0f && contrast == 0f) {
            baseBitmap
        } else {
            adjustBrightnessContrast(baseBitmap, brightness, contrast)
        }

        // 2. Application du rendu fausses couleurs avec l'opacité choisie en pleine résolution
        val fullOutput = if (falseColorMode == FalseColorMode.ORIGINAL || falseColorOpacity <= 0f) {
            adjusted
        } else {
            generateFalseColorBitmap(adjusted, falseColorMode, falseColorOpacity)
        }

        val fileName = "ColorApp_Decomp_${System.currentTimeMillis()}"
        val mimeType = "image/jpeg"
        val extension = "jpg"
        var isSaved = false

        // MediaStore Pictures / ColorApp_Resized (identique à l'onglet Size)
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
                    fullOutput.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
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
            android.util.Log.w("ColorApp_Decomp", "MediaStore insert 1 failed", e)
        }

        // Fallback MediaStore Pictures standard
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
                        fullOutput.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                        outputStream.flush()
                    }
                    isSaved = true
                }
            } catch (e: Throwable) {
                android.util.Log.w("ColorApp_Decomp", "MediaStore insert 2 failed", e)
            }
        }

        isSaved
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    }
}
