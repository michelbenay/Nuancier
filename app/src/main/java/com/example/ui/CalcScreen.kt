package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agent.calc.LayerAgentDialog
import com.example.data.MixedColor
import com.example.utils.ColorExportUtils
import com.example.utils.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Dimensions standard du canevas de calques
private const val CANVAS_WIDTH = 1080
private const val CANVAS_HEIGHT = 1080

// Modes de fusion de calque
enum class LayerBlendMode(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Produit"),
    SCREEN("Superposition"),
    OVERLAY("Incrustation")
}

// Modèle de calque réactif avec états Compose pour des animations et curseurs d'opacité ultra-fluides
class CalcLayer(
    val id: String = UUID.randomUUID().toString(),
    name: String,
    bitmap: Bitmap,
    isVisible: Boolean = true,
    opacity: Float = 1.0f,
    blendMode: LayerBlendMode = LayerBlendMode.NORMAL
) {
    var name by mutableStateOf(name)
    var bitmap by mutableStateOf(bitmap)
    var isVisible by mutableStateOf(isVisible)
    var opacity by mutableFloatStateOf(opacity)
    var blendMode by mutableStateOf(blendMode)
}

// Modes d'outils
enum class CalcTool(val label: String) {
    BRUSH("Pinceau"),
    ERASER("Gomme"),
    PIPETTE("Pipette"),
    COLOR_REPLACE("Remplacer couleur")
}

// Mode de remplacement de couleur
enum class ReplaceMode(val label: String) {
    GLOBAL("Tout le calque"),
    CONTIGUOUS("Zone continue")
}

// Modèle de métadonnées de projet sauvegardé
data class SavedProjectMeta(
    val folderName: String,
    val name: String,
    val dateStr: String,
    val layerCount: Int,
    val thumbnailBitmap: Bitmap?
)

// Instantané d'un calque pour l'historique d'annulation (Undo)
data class CalcLayerSnapshot(
    val id: String,
    val name: String,
    val bitmap: Bitmap,
    val isVisible: Boolean,
    val opacity: Float,
    val blendMode: LayerBlendMode
)

// Instantané complet de l'état du projet Calc pour l'annulation (Undo)
data class CalcSnapshot(
    val layers: List<CalcLayerSnapshot>,
    val activeLayerId: String,
    val actionDescription: String = "Action"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcScreen(
    mixedColors: List<MixedColor>,
    isSelected: Boolean = false,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Liste ordonnée des calques (Index 0 = Calque d'arrière-plan / inférieur, dernier index = Calque supérieur)
    // Récupération de l'espace de travail conservé pour préserver l'image et les calques même après fermeture de l'appli
    val layers = remember {
        val savedLayers = loadCalcWorkspace(context)
        if (!savedLayers.isNullOrEmpty()) {
            mutableStateListOf<CalcLayer>().apply { addAll(savedLayers) }
        } else {
            mutableStateListOf<CalcLayer>().apply {
                // Calque d'arrière-plan blanc initial uniquement
                val initialBitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
                initialBitmap.eraseColor(android.graphics.Color.WHITE)
                add(
                    CalcLayer(
                        name = "Arrière-plan",
                        bitmap = initialBitmap,
                        isVisible = true,
                        opacity = 1.0f
                    )
                )
            }
        }
    }

    // ID du calque actuellement actif (par défaut l'arrière-plan)
    var activeLayerId by remember {
        mutableStateOf(layers.firstOrNull()?.id ?: "")
    }

    // Compteur de version pour forcer la recomposition lors des modifications directes sur bitmap
    var canvasVersion by remember { mutableLongStateOf(0L) }

    // Sauvegarde automatique et continue de l'espace de travail pour persistance après fermeture de l'appli
    LaunchedEffect(canvasVersion, layers.size) {
        delay(600)
        withContext(Dispatchers.IO) {
            saveCalcWorkspace(context, layers)
        }
    }

    // Outil actif (Pipette sélectionnée par défaut selon demande utilisateur)
    var currentTool by remember { mutableStateOf(CalcTool.PIPETTE) }

    // Paramètres Pinceau
    var brushColor by remember { mutableStateOf(Color(0xFF002FA7)) } // Bleu Outremer par défaut
    var brushSize by remember { mutableFloatStateOf(28f) }
    var brushOpacity by remember { mutableFloatStateOf(1.0f) }

    // Paramètres Gomme
    var eraserSize by remember { mutableFloatStateOf(36f) }

    // Paramètres Remplacement de Couleur (Exigence 2)
    var sourceReplaceColor by remember { mutableStateOf<Color?>(null) }
    var targetReplaceColor by remember { mutableStateOf<Color?>(null) }
    var replaceTolerance by remember { mutableFloatStateOf(0.12f) } // 12% par défaut (limité à max 50%)
    var replaceMode by remember { mutableStateOf(ReplaceMode.GLOBAL) }
    var isProcessingReplace by remember { mutableStateOf(false) }

    // Position du dernier prélèvement pipette (en coordonnées pixels du canevas 1080x1080)
    var pipettePixelPos by remember { mutableStateOf<Offset?>(null) }
    var pipetteColorHex by remember { mutableStateOf<String?>(null) }

    // Piles d'annulation (Undo) avec capture complète des calques et état réactif
    val undoSnapshots = remember { mutableStateListOf<CalcSnapshot>() }

    fun saveUndoState(description: String = "Action") {
        val snapLayers = layers.map { layer ->
            CalcLayerSnapshot(
                id = layer.id,
                name = layer.name,
                bitmap = layer.bitmap.copy(layer.bitmap.config ?: Bitmap.Config.ARGB_8888, true),
                isVisible = layer.isVisible,
                opacity = layer.opacity,
                blendMode = layer.blendMode
            )
        }
        undoSnapshots.add(CalcSnapshot(snapLayers, activeLayerId, description))
        if (undoSnapshots.size > 15) {
            undoSnapshots.removeAt(0)
        }
    }

    fun restoreUndoState() {
        if (undoSnapshots.isNotEmpty()) {
            val lastSnapshot = undoSnapshots.removeAt(undoSnapshots.size - 1)
            layers.clear()
            for (snap in lastSnapshot.layers) {
                layers.add(
                    CalcLayer(
                        id = snap.id,
                        name = snap.name,
                        bitmap = snap.bitmap.copy(snap.bitmap.config ?: Bitmap.Config.ARGB_8888, true),
                        isVisible = snap.isVisible,
                        opacity = snap.opacity,
                        blendMode = snap.blendMode
                    )
                )
            }
            activeLayerId = if (layers.any { it.id == lastSnapshot.activeLayerId }) {
                lastSnapshot.activeLayerId
            } else {
                layers.lastOrNull()?.id ?: ""
            }
            canvasVersion++
            onShowSnackbar("Action annulée (${lastSnapshot.actionDescription})")
        }
    }

    // Boîtes de dialogue et BottomSheets
    var showLayersSheet by remember { mutableStateOf(false) }
    var showLayerAgentDialog by remember { mutableStateOf(false) }
    var showMixedColorsPopup by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSaveProjectDialog by remember { mutableStateOf(false) }
    var showLoadProjectDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    // Nom pour export et sauvegarde
    var exportFileName by remember { mutableStateOf("") }
    var projectNameInput by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<SavedProjectMeta?>(null) }

    // Liste des projets sauvegardés
    var savedProjectsList by remember { mutableStateOf<List<SavedProjectMeta>>(emptyList()) }

    // Zoom & Pan sur le canevas
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Launcher pour importer une image de la galerie dans un nouveau calque (en conservant les proportions)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    // Lecture de l'orientation EXIF si présente pour éviter toute rotation involontaire
                    val orientation = try {
                        context.contentResolver.openInputStream(uri)?.use { s ->
                            val exif = android.media.ExifInterface(s)
                            exif.getAttributeInt(
                                android.media.ExifInterface.TAG_ORIENTATION,
                                android.media.ExifInterface.ORIENTATION_NORMAL
                            )
                        } ?: android.media.ExifInterface.ORIENTATION_NORMAL
                    } catch (_: Exception) {
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    }

                    val stream = context.contentResolver.openInputStream(uri)
                    val rawBmp = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    if (rawBmp != null) {
                        // Corriger l'orientation si nécessaire
                        val matrix = android.graphics.Matrix()
                        when (orientation) {
                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                        }
                        val orientedBmp = if (!matrix.isIdentity) {
                            val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                            rawBmp.recycle()
                            rotated
                        } else {
                            rawBmp
                        }

                        val rawWidth = orientedBmp.width
                        val rawHeight = orientedBmp.height

                        // Mise à l'échelle conservant strictement les proportions de l'image (Fit Center sans déformation)
                        val scaleFactor = minOf(
                            CANVAS_WIDTH.toFloat() / rawWidth,
                            CANVAS_HEIGHT.toFloat() / rawHeight
                        )
                        val fittedWidth = (rawWidth * scaleFactor).roundToInt().coerceAtLeast(1)
                        val fittedHeight = (rawHeight * scaleFactor).roundToInt().coerceAtLeast(1)

                        // Créer un calque transparent aux dimensions standard du canevas
                        val scaledBmp = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
                        scaledBmp.eraseColor(android.graphics.Color.TRANSPARENT)

                        // Dessiner l'image centrée dans le canevas
                        val canvas = android.graphics.Canvas(scaledBmp)
                        val left = (CANVAS_WIDTH - fittedWidth) / 2f
                        val top = (CANVAS_HEIGHT - fittedHeight) / 2f
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                        val srcRect = android.graphics.Rect(0, 0, rawWidth, rawHeight)
                        val dstRect = android.graphics.RectF(left, top, left + fittedWidth, top + fittedHeight)
                        canvas.drawBitmap(orientedBmp, srcRect, dstRect, paint)
                        orientedBmp.recycle()

                        withContext(Dispatchers.Main) {
                            saveUndoState("Import image")
                            val newLayer = CalcLayer(
                                name = "Image importée",
                                bitmap = scaledBmp,
                                isVisible = true,
                                opacity = 1.0f
                            )
                            // Insérer sous la grille si présente pour que la grille de mise au carré se superpose immédiatement
                            val gridIdx = layers.indexOfFirst { it.name.startsWith("Grille") }
                            if (gridIdx != -1) {
                                layers.add(gridIdx, newLayer)
                            } else {
                                layers.add(newLayer)
                            }
                            activeLayerId = newLayer.id
                            canvasVersion++
                            onShowSnackbar("Image importée (proportions d'origine conservées)")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onShowSnackbar("Erreur lors de l'import : ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    // Fonction d'application du remplacement de couleur sur le calque actif
    fun applyColorReplacement(tapCoord: Offset? = null) {
        val active = layers.find { it.id == activeLayerId }
        if (active == null) {
            onShowSnackbar("Aucun calque actif sélectionné")
            return
        }
        val srcCol = sourceReplaceColor
        val tgtCol = targetReplaceColor
        if (srcCol == null || tgtCol == null) {
            onShowSnackbar("Veuillez choisir la couleur source et la nouvelle couleur")
            return
        }

        // Sauvegarder l'état pour Undo
        saveUndoState("Remplacement de couleur")

        isProcessingReplace = true
        scope.launch {
            val updatedBmp = withContext(Dispatchers.Default) {
                replaceColorInBitmap(
                    sourceBitmap = active.bitmap,
                    oldColor = srcCol.toArgb(),
                    newColor = tgtCol.toArgb(),
                    tolerancePercent = replaceTolerance,
                    mode = replaceMode,
                    startX = tapCoord?.x?.toInt() ?: (CANVAS_WIDTH / 2),
                    startY = tapCoord?.y?.toInt() ?: (CANVAS_HEIGHT / 2)
                )
            }
            active.bitmap = updatedBmp
            canvasVersion++
            isProcessingReplace = false
            onShowSnackbar("Remplacement de couleur appliqué avec succès !")
        }
    }

    // Gestion du tracé Pinceau / Gomme
    var lastPoint by remember { mutableStateOf<Offset?>(null) }

    fun drawOnActiveLayer(start: Offset, end: Offset) {
        val active = layers.find { it.id == activeLayerId } ?: return
        if (!active.isVisible) return

        val canvas = android.graphics.Canvas(active.bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (currentTool == CalcTool.BRUSH) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = brushSize
            paint.color = brushColor.toArgb()
            paint.alpha = (brushOpacity * 255).roundToInt().coerceIn(0, 255)
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        } else if (currentTool == CalcTool.ERASER) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = eraserSize
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
        canvasVersion++
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // LIGNE 1 : Titre "Atelier calques"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Atelier calques",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }

                        // Badge d'info du calque actif et nombre total de calques
                        val activeLayer = layers.find { it.id == activeLayerId }
                        val activeName = activeLayer?.name ?: "Aucun"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { showLayersSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Actif : $activeName (${layers.size} calques)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // LIGNE 2 : Tous les icônes d'actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Annuler (Undo) - Flèche retour réactive et claire
                        val canUndo = undoSnapshots.isNotEmpty()
                        Surface(
                            shape = CircleShape,
                            color = if (canUndo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            tonalElevation = if (canUndo) 2.dp else 0.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(
                                    enabled = canUndo,
                                    onClick = { restoreUndoState() }
                                )
                                .testTag("undo_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Undo,
                                    contentDescription = "Annuler la dernière action",
                                    tint = if (canUndo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (canUndo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 4.dp, end = 4.dp)
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                        }

                        // Nouveau canevas / Réinitialiser
                        IconButton(
                            onClick = { showResetConfirmDialog = true },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAdd,
                                contentDescription = "Nouveau canevas vierge",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Projets enregistrés (Dossier)
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val list = loadSavedProjectsList(context)
                                    withContext(Dispatchers.Main) {
                                        savedProjectsList = list
                                        showLoadProjectDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Ouvrir un projet",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Enregistrer le projet
                        IconButton(
                            onClick = {
                                val defaultName = "Projet_Calc_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}"
                                projectNameInput = defaultName
                                showSaveProjectDialog = true
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Enregistrer le projet",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Exporter l'image aplatie
                        IconButton(
                            onClick = {
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                exportFileName = "Calc_Oeuvre_$dateStr"
                                showExportDialog = true
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Exporter l'image",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Importer une image (nouveau calque)
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Importer une photo",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Bouton d'appel / bascule rapide de la Grille (19x23)
                        IconButton(
                            onClick = {
                                val existingGrid = layers.find { it.name.startsWith("Grille") }
                                if (existingGrid != null) {
                                    existingGrid.isVisible = !existingGrid.isVisible
                                    canvasVersion++
                                    onShowSnackbar(if (existingGrid.isVisible) "Grille (19x23) affichée" else "Grille masquée")
                                } else {
                                    saveUndoState("Ajout grille (19x23)")
                                    val gridBmp = createGridBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, columns = 19, rows = 23, lineColor = android.graphics.Color.BLACK)
                                    val gridLayer = CalcLayer(
                                        name = "Grille (19x23)",
                                        bitmap = gridBmp,
                                        isVisible = true,
                                        opacity = 1.0f
                                    )
                                    layers.add(gridLayer)
                                    activeLayerId = gridLayer.id
                                    canvasVersion++
                                    onShowSnackbar("Calque Grille transparent (19x23) ajouté")
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            val hasVisibleGrid = layers.any { it.name.startsWith("Grille") && it.isVisible }
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Appeler la grille (19x23)",
                                tint = if (hasVisibleGrid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Bouton Agent IA (Assistant des Calques - N&B, Outils)
                        IconButton(
                            onClick = { showLayerAgentDialog = true },
                            modifier = Modifier.size(42.dp).testTag("open_layer_agent_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Agent IA des Calques",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Bouton Gestionnaire de Calques
                        FilledTonalButton(
                            onClick = { showLayersSheet = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("open_layers_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Calques (${layers.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Rangée des outils : Pinceau, Gomme, Pipette, Remplacer couleur
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CalcTool.values().forEach { tool ->
                                val isSelectedTool = currentTool == tool
                                FilterChip(
                                    selected = isSelectedTool,
                                    onClick = { currentTool = tool },
                                    label = { Text(tool.label, fontSize = 11.5.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (tool) {
                                                CalcTool.BRUSH -> Icons.Default.Brush
                                                CalcTool.ERASER -> Icons.Default.Clear
                                                CalcTool.PIPETTE -> Icons.Default.Colorize
                                                CalcTool.COLOR_REPLACE -> Icons.Default.SwapHoriz
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }

                            // Indicateur de couleur active pour pinceau / pipette
                            if (currentTool == CalcTool.BRUSH) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(brushColor)
                                        .border(1.5.dp, Color.White, CircleShape)
                                        .clickable { showMixedColorsPopup = true }
                                )
                                Text(
                                    text = "Mélanges",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { showMixedColorsPopup = true }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Volet contextuel selon l'outil actif
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    when (currentTool) {
                        CalcTool.BRUSH -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Taille", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                                    Slider(
                                        value = brushSize,
                                        onValueChange = { brushSize = it },
                                        valueRange = 4f..120f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("${brushSize.toInt()} px", fontSize = 11.sp, modifier = Modifier.width(42.dp))

                                    // Bouton Palette des mélanges
                                    FilledTonalIconButton(
                                        onClick = { showMixedColorsPopup = true },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = "Palette des mélanges", modifier = Modifier.size(18.dp))
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Opacité", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                                    Slider(
                                        value = brushOpacity,
                                        onValueChange = { brushOpacity = it },
                                        valueRange = 0.05f..1.0f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("${(brushOpacity * 100).toInt()}%", fontSize = 11.sp, modifier = Modifier.width(42.dp))
                                }
                            }
                        }

                        CalcTool.ERASER -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Gomme", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Slider(
                                    value = eraserSize,
                                    onValueChange = { eraserSize = it },
                                    valueRange = 6f..140f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${eraserSize.toInt()} px", fontSize = 11.sp, modifier = Modifier.width(42.dp))
                            }
                        }

                        CalcTool.PIPETTE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Colorize, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = pipetteColorHex?.let { "Couleur prélevée : $it" }
                                            ?: "Touchez le canevas pour prélever la couleur",
                                        fontSize = 11.5.sp,
                                        fontWeight = if (pipetteColorHex != null) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (sourceReplaceColor != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(sourceReplaceColor!!)
                                                .border(1.5.dp, Color.White, CircleShape)
                                                .border(1.dp, Color.Gray, CircleShape)
                                        )
                                    }
                                }
                            }
                        }

                        CalcTool.COLOR_REPLACE -> {
                            // Section Remplacement de Couleur (Exigence 2)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Couleur source (à remplacer)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Source :", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(sourceReplaceColor ?: Color.LightGray)
                                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                        )
                                        Text(
                                            text = sourceReplaceColor?.let { c ->
                                                String.format("#%06X", (c.toArgb() and 0xFFFFFF))
                                            } ?: "Aucune (Pipette)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)

                                    // 2. Nouvelle couleur (issue de l'onglet Mélange)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Cible :", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(targetReplaceColor ?: Color.LightGray)
                                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                                .clickable { showMixedColorsPopup = true }
                                        )
                                        // Bouton popup grille des mélanges (EXIGENCE 2)
                                        Button(
                                            onClick = { showMixedColorsPopup = true },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = targetReplaceColor?.let { c ->
                                                    String.format("#%06X", (c.toArgb() and 0xFFFFFF))
                                                } ?: "Grille Mélanges",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Tolérance & Application (limitée à max 50% pour plus de finesse)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Tolérance ${(replaceTolerance * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(82.dp))
                                    Slider(
                                        value = replaceTolerance,
                                        onValueChange = { replaceTolerance = it },
                                        valueRange = 0.01f..0.50f,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Bouton Remplacer
                                    Button(
                                        onClick = { applyColorReplacement(null) },
                                        enabled = !isProcessingReplace && sourceReplaceColor != null && targetReplaceColor != null,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.testTag("apply_replace_button")
                                    ) {
                                        if (isProcessingReplace) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                        } else {
                                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Remplacer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Réglage direct et fluide de l'opacité du calque actif
                    val activeLyr = layers.find { it.id == activeLayerId }
                    if (activeLyr != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Calque ${(activeLyr.opacity * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(78.dp)
                            )
                            Slider(
                                value = activeLyr.opacity,
                                onValueChange = { activeLyr.opacity = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFEFEFEF))
                .clipToBounds()
        ) {
            // Zone de canevas interactive avec support Zoom/Pan et Dessin
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 4.0f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Canevas centré proportionnel
                val canvasDisplaySize = minOf(maxWidth, maxHeight) * 0.94f

                Box(
                    modifier = Modifier
                        .size(canvasDisplaySize)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        .pointerInput(currentTool, activeLayerId, brushColor, brushSize, brushOpacity, eraserSize) {
                            if (currentTool == CalcTool.BRUSH || currentTool == CalcTool.ERASER) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        // Sauvegarder pour Undo au début du tracé
                                        val toolLabel = if (currentTool == CalcTool.BRUSH) "Pinceau" else "Gomme"
                                        saveUndoState(toolLabel)
                                        val scaleX = CANVAS_WIDTH.toFloat() / size.width
                                        val scaleY = CANVAS_HEIGHT.toFloat() / size.height
                                        val pt = Offset(startOffset.x * scaleX, startOffset.y * scaleY)
                                        lastPoint = pt
                                        drawOnActiveLayer(pt, pt)
                                    },
                                    onDragEnd = {
                                        lastPoint = null
                                    },
                                    onDragCancel = {
                                        lastPoint = null
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val scaleX = CANVAS_WIDTH.toFloat() / size.width
                                        val scaleY = CANVAS_HEIGHT.toFloat() / size.height
                                        val current = Offset(change.position.x * scaleX, change.position.y * scaleY)
                                        val prev = lastPoint ?: current
                                        drawOnActiveLayer(prev, current)
                                        lastPoint = current
                                    }
                                )
                            }
                        }
                        .pointerInput(currentTool, activeLayerId, brushColor, brushSize, brushOpacity, eraserSize) {
                            detectTapGestures { tapOffset ->
                                val scaleX = CANVAS_WIDTH.toFloat() / size.width
                                val scaleY = CANVAS_HEIGHT.toFloat() / size.height
                                val canvasPt = Offset(tapOffset.x * scaleX, tapOffset.y * scaleY)

                                if (currentTool == CalcTool.BRUSH || currentTool == CalcTool.ERASER) {
                                    // Sauvegarder pour Undo lors d'un tap simple (point)
                                    val toolLabel = if (currentTool == CalcTool.BRUSH) "Pinceau (point)" else "Gomme (point)"
                                    saveUndoState(toolLabel)
                                    drawOnActiveLayer(canvasPt, canvasPt)
                                } else if (currentTool == CalcTool.PIPETTE) {
                                    // Échantillonner la couleur sur le calque actif ou sur l'image composite
                                    val active = layers.find { it.id == activeLayerId }
                                    if (active != null) {
                                        val px = canvasPt.x.toInt().coerceIn(0, CANVAS_WIDTH - 1)
                                        val py = canvasPt.y.toInt().coerceIn(0, CANVAS_HEIGHT - 1)
                                        val pixel = active.bitmap.getPixel(px, py)
                                        val sampledColor = Color(pixel)
                                        sourceReplaceColor = sampledColor
                                        brushColor = sampledColor
                                        val hexStr = String.format("#%06X", (pixel and 0xFFFFFF))
                                        pipettePixelPos = Offset(px.toFloat(), py.toFloat())
                                        pipetteColorHex = hexStr
                                        onShowSnackbar("Couleur prélevée : $hexStr (assignée à la source)")
                                    }
                                } else if (currentTool == CalcTool.COLOR_REPLACE) {
                                    // Si on clique en mode remplacement, prélever ou appliquer selon le contexte
                                    val active = layers.find { it.id == activeLayerId }
                                    if (active != null) {
                                        val px = canvasPt.x.toInt().coerceIn(0, CANVAS_WIDTH - 1)
                                        val py = canvasPt.y.toInt().coerceIn(0, CANVAS_HEIGHT - 1)
                                        val pixel = active.bitmap.getPixel(px, py)
                                        sourceReplaceColor = Color(pixel)
                                        val hexStr = String.format("#%06X", (pixel and 0xFFFFFF))
                                        pipettePixelPos = Offset(px.toFloat(), py.toFloat())
                                        pipetteColorHex = hexStr
                                        onShowSnackbar("Couleur source sélectionnée : $hexStr")
                                    }
                                }
                            }
                        }
                ) {
                    // Rendu empilé de chaque calque visible dans l'ordre (de l'arrière-plan vers le haut)
                    // Utilise canvasVersion pour forcer le rafraîchissement réactif
                    key(canvasVersion) {
                        layers.forEach { layer ->
                            if (layer.isVisible && layer.opacity > 0f) {
                                Image(
                                    bitmap = layer.bitmap.asImageBitmap(),
                                    contentDescription = layer.name,
                                    alpha = layer.opacity.coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Témoin visuel pour l'endroit touché par la pipette (cercles concentriques comme dans l'onglet analyse)
                    if (pipettePixelPos != null && (currentTool == CalcTool.PIPETTE || currentTool == CalcTool.COLOR_REPLACE)) {
                        val density = LocalDensity.current
                        val normX = pipettePixelPos!!.x / CANVAS_WIDTH.toFloat()
                        val normY = pipettePixelPos!!.y / CANVAS_HEIGHT.toFloat()

                        Box(
                            modifier = Modifier
                                .offset {
                                    val canvasPxW = canvasDisplaySize.toPx()
                                    val canvasPxH = canvasDisplaySize.toPx()
                                    val screenX = normX * canvasPxW
                                    val screenY = normY * canvasPxH
                                    val halfIndicator = with(density) { 16.dp.toPx() }
                                    IntOffset(
                                        x = (screenX - halfIndicator).roundToInt(),
                                        y = (screenY - halfIndicator).roundToInt()
                                    )
                                }
                                .size(32.dp)
                                .border(2.dp, Color.White, CircleShape)
                                .border(1.dp, Color.Black.copy(alpha = 0.7f), CircleShape)
                        ) {
                            // Point central avec la couleur prélevée
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.Center)
                                    .background(sourceReplaceColor ?: Color.Red, CircleShape)
                                    .border(1.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }
            }

            // Bouton réinitialiser zoom si le canevas est agrandi
            if (scale != 1f || offset != Offset.Zero) {
                SmallFloatingActionButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Réinitialiser zoom", modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    // =========================================================================
    // EXIGENCE 2 : POPUP / BOTTOM SHEET GRILLE DES COULEURS DE L'ONGLET MÉLANGE
    // =========================================================================
    if (showMixedColorsPopup) {
        Dialog(
            onDismissRequest = { showMixedColorsPopup = false }
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 560.dp)
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Couleurs de l'onglet Mélange",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showMixedColorsPopup = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    }

                    Text(
                        text = "Sélectionnez une couleur pour l'utiliser avec le pinceau ou comme nouvelle couleur de remplacement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (mixedColors.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                Text(
                                    text = "Aucun mélange enregistré dans l'onglet Mélange.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Créez d'abord des mélanges dans le Nuancier ou l'onglet Mélange.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Grille défilante des couleurs de l'onglet Mélange (EXIGENCE 2)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("mixed_colors_grid"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(mixedColors) { mixed ->
                                val color = mixed.hexCode.toColor()
                                val isLight = mixed.hexCode.isLightColor()

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (currentTool == CalcTool.COLOR_REPLACE) {
                                                targetReplaceColor = color
                                                onShowSnackbar("Couleur cible définie sur \"${mixed.name}\" (${mixed.hexCode})")
                                            } else {
                                                brushColor = color
                                                onShowSnackbar("Couleur du pinceau : \"${mixed.name}\"")
                                            }
                                            showMixedColorsPopup = false
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Pastille couleur généreuse
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(color)
                                                .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = mixed.hexCode.uppercase(),
                                                color = if (isLight) Color.Black else Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(Modifier.height(6.dp))

                                        // Nom du mélange
                                        Text(
                                            text = mixed.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showMixedColorsPopup = false }) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // EXIGENCE 1 : VOLET DE GESTION COMPLÈTE DES CALQUES (ModalBottomSheet)
    // =========================================================================
    if (showLayersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLayersSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Gestion des Calques (${layers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Boutons d'ajout de calques
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Importer image depuis galerie
                        FilledTonalIconButton(
                            onClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Importer image", modifier = Modifier.size(18.dp))
                        }

                        // Nouveau calque vierge
                        Button(
                            onClick = {
                                saveUndoState("Nouveau calque")
                                val blankBmp = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
                                val newIndex = layers.size + 1
                                val newLayer = CalcLayer(
                                    name = "Calque $newIndex",
                                    bitmap = blankBmp,
                                    isVisible = true,
                                    opacity = 1.0f
                                )
                                layers.add(newLayer)
                                activeLayerId = newLayer.id
                                canvasVersion++
                                onShowSnackbar("Calque $newIndex ajouté")
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Nouveau", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Liste des calques (Affichage de haut en bas : le dernier calque de la liste est au sommet)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Inverser pour que le calque supérieur apparaisse en haut dans l'interface
                    val reversedLayers = layers.reversed()

                    items(items = reversedLayers, key = { it.id }) { layer ->
                        val originalIdx = layers.indexOfFirst { it.id == layer.id }
                        val isActive = layer.id == activeLayerId

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.5.dp,
                                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeLayerId = layer.id }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Miniature du calque
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White)
                                                .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                                        ) {
                                            Image(
                                                bitmap = layer.bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        // Nom du calque
                                        Column {
                                            Text(
                                                text = layer.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = if (isActive) "Calque actif" else "Sélectionner",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Commandes d'action sur le calque
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Visibilité (Oeil)
                                        IconButton(
                                            onClick = {
                                                layer.isVisible = !layer.isVisible
                                                canvasVersion++
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Visibilité",
                                                tint = if (layer.isVisible) MaterialTheme.colorScheme.primary else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Monter
                                        IconButton(
                                            onClick = {
                                                if (originalIdx < layers.size - 1) {
                                                    saveUndoState("Monter calque")
                                                    val temp = layers[originalIdx]
                                                    layers[originalIdx] = layers[originalIdx + 1]
                                                    layers[originalIdx + 1] = temp
                                                    canvasVersion++
                                                }
                                            },
                                            enabled = originalIdx < layers.size - 1,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Monter", modifier = Modifier.size(16.dp))
                                        }

                                        // Descendre
                                        IconButton(
                                            onClick = {
                                                if (originalIdx > 0) {
                                                    saveUndoState("Descendre calque")
                                                    val temp = layers[originalIdx]
                                                    layers[originalIdx] = layers[originalIdx - 1]
                                                    layers[originalIdx - 1] = temp
                                                    canvasVersion++
                                                }
                                            },
                                            enabled = originalIdx > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Descendre", modifier = Modifier.size(16.dp))
                                        }

                                        // Dupliquer
                                        IconButton(
                                            onClick = {
                                                saveUndoState("Dupliquer calque")
                                                val copyBmp = layer.bitmap.copy(layer.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
                                                val duplicated = CalcLayer(
                                                    name = "${layer.name} (copie)",
                                                    bitmap = copyBmp,
                                                    isVisible = layer.isVisible,
                                                    opacity = layer.opacity,
                                                    blendMode = layer.blendMode
                                                )
                                                layers.add(originalIdx + 1, duplicated)
                                                activeLayerId = duplicated.id
                                                canvasVersion++
                                                onShowSnackbar("Calque dupliqué")
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Dupliquer", modifier = Modifier.size(16.dp))
                                        }

                                        // Supprimer
                                        IconButton(
                                            onClick = {
                                                if (layers.size > 1) {
                                                    saveUndoState("Supprimer calque")
                                                    layers.removeAt(originalIdx)
                                                    if (activeLayerId == layer.id) {
                                                        activeLayerId = layers.lastOrNull()?.id ?: ""
                                                    }
                                                    canvasVersion++
                                                    onShowSnackbar("Calque supprimé")
                                                } else {
                                                    onShowSnackbar("Impossible de supprimer le dernier calque")
                                                }
                                            },
                                            enabled = layers.size > 1,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                // Slider d'opacité du calque
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Opacité ${(layer.opacity * 100).toInt()}%", fontSize = 11.sp, modifier = Modifier.width(76.dp))
                                    Slider(
                                        value = layer.opacity,
                                        onValueChange = { newOpacity ->
                                            layer.opacity = newOpacity
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Options spécifiques pour le calque Grille : couleur des traits sur fond 100% transparent
                                if (layer.name.contains("Grille", ignoreCase = true)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Traits :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val gridColors = listOf(
                                            "Noir" to android.graphics.Color.BLACK,
                                            "Blanc" to android.graphics.Color.WHITE,
                                            "Rouge" to android.graphics.Color.RED,
                                            "Bleu" to android.graphics.Color.BLUE
                                        )
                                        gridColors.forEach { (cName, cVal) ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        saveUndoState("Couleur grille ($cName)")
                                                        layer.bitmap = createGridBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, columns = 19, rows = 23, lineColor = cVal)
                                                        canvasVersion++
                                                        onShowSnackbar("Traits de la grille : $cName")
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .background(Color(cVal), CircleShape)
                                                            .border(0.5.dp, Color.Gray, CircleShape)
                                                    )
                                                    Text(cName, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Bouton Assistant IA Calques
                FilledTonalButton(
                    onClick = {
                        showLayersSheet = false
                        showLayerAgentDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("layers_sheet_agent_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Assistant IA Calques (Monochrome, Sépia...)", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(10.dp))

                // Actions globales des calques : Grille de mise au carré & Tout fusionner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bouton Ajouter/Réinitialiser Grille de mise au carré (19x23)
                    OutlinedButton(
                        onClick = {
                            saveUndoState("Ajout grille (19x23)")
                            val gridBmp = createGridBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, columns = 19, rows = 23, lineColor = android.graphics.Color.BLACK)
                            val gridLayer = CalcLayer(
                                name = "Grille (19x23)",
                                bitmap = gridBmp,
                                isVisible = true,
                                opacity = 1.0f
                            )
                            layers.add(gridLayer)
                            activeLayerId = gridLayer.id
                            canvasVersion++
                            onShowSnackbar("Calque Grille transparent (19x23) ajouté")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ Grille 19x23", fontSize = 12.sp)
                    }

                    // Bouton Tout fusionner
                    OutlinedButton(
                        onClick = {
                            if (layers.size > 1) {
                                saveUndoState("Fusionner tous les calques")
                                val composite = renderCompositeBitmap(layers, CANVAS_WIDTH, CANVAS_HEIGHT)
                                layers.clear()
                                layers.add(
                                    CalcLayer(
                                        name = "Calque fusionné",
                                        bitmap = composite,
                                        isVisible = true,
                                        opacity = 1.0f
                                    )
                                )
                                activeLayerId = layers.first().id
                                canvasVersion++
                                onShowSnackbar("Tous les calques ont été fusionnés en un seul")
                            }
                        },
                        enabled = layers.size > 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LayersClear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tout fusionner", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // =========================================================================
    // AGENT IA : BOÎTE DE DIALOGUE D'ASSISTANCE SUR LES CALQUES
    // =========================================================================
    if (showLayerAgentDialog) {
        val currentActiveLayer = layers.find { it.id == activeLayerId } ?: layers.firstOrNull()
        if (currentActiveLayer != null) {
            LayerAgentDialog(
                activeLayerName = currentActiveLayer.name,
                activeBitmap = currentActiveLayer.bitmap,
                onApplyTransformedBitmap = { newBitmap, actionDesc ->
                    saveUndoState("Agent : $actionDesc")
                    currentActiveLayer.bitmap = newBitmap
                    canvasVersion++
                    onShowSnackbar(actionDesc)
                },
                onDismiss = { showLayerAgentDialog = false }
            )
        }
    }

    // =========================================================================
    // EXIGENCE 1 : BOÎTE DE DIALOGUE D'EXPORTATION D'IMAGE (PNG / JPG)
    // =========================================================================
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isExporting) showExportDialog = false },
            icon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Exporter l'image aplatie", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Fusionne tous les calques visibles et enregistre l'œuvre en haute résolution (1080x1080) dans Documents/Nuancier/Calques.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = exportFileName,
                        onValueChange = { exportFileName = it },
                        label = { Text("Nom du fichier") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = exportFileName.trim()
                        if (name.isBlank()) {
                            onShowSnackbar("Veuillez saisir un nom de fichier")
                            return@Button
                        }
                        isExporting = true
                        scope.launch {
                            val composite = withContext(Dispatchers.Default) {
                                renderCompositeBitmap(layers, CANVAS_WIDTH, CANVAS_HEIGHT)
                            }
                            val sanitized = if (!name.endsWith(".png", ignoreCase = true)) "$name.png" else name
                            val (success, path) = withContext(Dispatchers.IO) {
                                ColorExportUtils.saveImageToNuancierFolder(
                                    context = context,
                                    fileName = sanitized,
                                    bitmap = composite,
                                    subFolder = "Calques"
                                )
                            }
                            isExporting = false
                            showExportDialog = false
                            if (success) {
                                onShowSnackbar("Image exportée avec succès dans Documents/Nuancier/Calques : $sanitized")
                            } else {
                                onShowSnackbar("Erreur lors de l'exportation")
                            }
                        }
                    },
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Exporter")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }, enabled = !isExporting) {
                    Text("Annuler")
                }
            }
        )
    }

    // =========================================================================
    // EXIGENCE 1 : SAUVEGARDE DU PROJET DE CALQUES (POUR REOUVRIR PLUS TARD)
    // =========================================================================
    if (showSaveProjectDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProjectDialog = false },
            icon = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Enregistrer le projet de calques", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sauvegarde tous les calques actuels, leur ordre, opacité et visibilité dans l'application pour pouvoir reprendre votre travail ultérieurement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = projectNameInput,
                        onValueChange = { projectNameInput = it },
                        label = { Text("Nom du projet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = projectNameInput.trim()
                        if (name.isBlank()) {
                            onShowSnackbar("Veuillez saisir un nom de projet")
                            return@Button
                        }
                        showSaveProjectDialog = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                saveCalcProject(context, name, layers)
                            }
                            if (ok) {
                                onShowSnackbar("Projet \"$name\" sauvegardé avec succès !")
                            } else {
                                onShowSnackbar("Erreur lors de la sauvegarde du projet")
                            }
                        }
                    }
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProjectDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // =========================================================================
    // EXIGENCE 1 : OUVRIR UN PROJET SAUVEGARDÉ
    // =========================================================================
    if (showLoadProjectDialog) {
        AlertDialog(
            onDismissRequest = { showLoadProjectDialog = false },
            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Projets de calques enregistrés", fontWeight = FontWeight.Bold) },
            text = {
                if (savedProjectsList.isEmpty()) {
                    Text(
                        text = "Aucun projet de calques enregistré pour le moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedProjectsList) { project ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Zone clic pour charger le projet
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                showLoadProjectDialog = false
                                                scope.launch {
                                                    val loadedLayers = withContext(Dispatchers.IO) {
                                                        loadCalcProject(context, project.folderName)
                                                    }
                                                    if (loadedLayers.isNotEmpty()) {
                                                        layers.clear()
                                                        layers.addAll(loadedLayers)
                                                        activeLayerId = layers.lastOrNull()?.id ?: ""
                                                        canvasVersion++
                                                        onShowSnackbar("Projet \"${project.name}\" chargé (${loadedLayers.size} calques)")
                                                    } else {
                                                        onShowSnackbar("Erreur lors du chargement du projet")
                                                    }
                                                }
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (project.thumbnailBitmap != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                            ) {
                                                Image(bitmap = project.thumbnailBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(project.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${project.layerCount} calque(s) • ${project.dateStr}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }
                                    }
                                    // Bouton pour supprimer ce projet de calques
                                    IconButton(
                                        onClick = { projectToDelete = project },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Supprimer le projet",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadProjectDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // =========================================================================
    // DIALOGUE DE CONFIRMATION DE SUPPRESSION DE PROJET
    // =========================================================================
    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer le projet ?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Voulez-vous vraiment supprimer définitivement le projet \"${projectToDelete?.name}\" ? Cette action effacera tous les calques enregistrés pour ce projet.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = projectToDelete
                        projectToDelete = null
                        if (target != null) {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    deleteCalcProject(context, target.folderName)
                                }
                                if (ok) {
                                    savedProjectsList = withContext(Dispatchers.IO) {
                                        loadSavedProjectsList(context)
                                    }
                                    onShowSnackbar("Projet \"${target.name}\" supprimé avec succès")
                                } else {
                                    onShowSnackbar("Erreur lors de la suppression du projet")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // =========================================================================
    // DIALOGUE DE CONFIRMATION DE NOUVEAU CANEVAS (REINITIALISATION)
    // =========================================================================
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Icon(Icons.Default.NoteAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Nouveau canevas vierge ?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Voulez-vous commencer un nouveau dessin sur une feuille blanche ? L'espace de travail actuel sera réinitialisé.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        layers.clear()
                        val initialBitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
                        initialBitmap.eraseColor(android.graphics.Color.WHITE)
                        val bg = CalcLayer(name = "Arrière-plan", bitmap = initialBitmap, isVisible = true, opacity = 1.0f)
                        layers.add(bg)
                        activeLayerId = bg.id
                        undoSnapshots.clear()
                        scale = 1f
                        offset = Offset.Zero
                        canvasVersion++
                        scope.launch(Dispatchers.IO) {
                            clearCalcWorkspace(context)
                        }
                        onShowSnackbar("Nouveau canevas blanc prêt !")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Nouveau canevas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

// =============================================================================
// FONCTIONS UTILITAIRES : COMPOSITION, REMPLACEMENT COULEUR & SAUVEGARDE PROJETS
// =============================================================================

/**
 * Fusionne les calques visibles en tenant compte de l'ordre, de l'opacité et des modes de fusion.
 */
private fun renderCompositeBitmap(layers: List<CalcLayer>, width: Int, height: Int): Bitmap {
    val composite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(composite)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    layers.forEach { layer ->
        if (layer.isVisible && layer.opacity > 0f) {
            paint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255).roundToInt()
            paint.xfermode = when (layer.blendMode) {
                LayerBlendMode.NORMAL -> null
                LayerBlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                LayerBlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                LayerBlendMode.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            }
            canvas.drawBitmap(layer.bitmap, 0f, 0f, paint)
        }
    }
    return composite
}

/**
 * Algorithme de remplacement de couleur avec tolérance euclidienne dans l'espace RVB.
 * Supporte le remplacement global sur le calque actif ou par zone contiguë (flood fill).
 */
private fun replaceColorInBitmap(
    sourceBitmap: Bitmap,
    oldColor: Int,
    newColor: Int,
    tolerancePercent: Float,
    mode: ReplaceMode,
    startX: Int,
    startY: Int
): Bitmap {
    val width = sourceBitmap.width
    val height = sourceBitmap.height
    val outBitmap = sourceBitmap.copy(sourceBitmap.config ?: Bitmap.Config.ARGB_8888, true)

    val targetA = (oldColor shr 24) and 0xFF
    val targetR = (oldColor shr 16) and 0xFF
    val targetG = (oldColor shr 8) and 0xFF
    val targetB = oldColor and 0xFF

    val newA = (newColor shr 24) and 0xFF
    val newR = (newColor shr 16) and 0xFF
    val newG = (newColor shr 8) and 0xFF
    val newB = newColor and 0xFF

    // Distance euclidienne maximale en RVB : sqrt(255^2 * 3) ~ 441.67
    val maxDist = tolerancePercent * 441.67f
    val maxDistSq = maxDist * maxDist

    fun isMatch(pixel: Int): Boolean {
        val a = (pixel shr 24) and 0xFF
        if (a == 0 && targetA != 0) return false
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF

        val dr = (r - targetR).toFloat()
        val dg = (g - targetG).toFloat()
        val db = (b - targetB).toFloat()
        val distSq = dr * dr + dg * dg + db * db
        return distSq <= maxDistSq
    }

    if (mode == ReplaceMode.GLOBAL) {
        val pixels = IntArray(width * height)
        outBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            if (isMatch(p)) {
                val origAlpha = (p shr 24) and 0xFF
                // Conserve la transparence du pixel d'origine
                val finalA = if (origAlpha < 255) origAlpha else newA
                pixels[i] = (finalA shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }
        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    } else {
        // Flood fill récursif/file d'attente pour zone contiguë
        val visited = BooleanArray(width * height)
        val queue = ArrayDeque<Int>()
        val startIdx = startY.coerceIn(0, height - 1) * width + startX.coerceIn(0, width - 1)
        queue.add(startIdx)
        visited[startIdx] = true

        val pixels = IntArray(width * height)
        outBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val cx = curr % width
            val cy = curr / width

            val p = pixels[curr]
            if (isMatch(p)) {
                val origAlpha = (p shr 24) and 0xFF
                val finalA = if (origAlpha < 255) origAlpha else newA
                pixels[curr] = (finalA shl 24) or (newR shl 16) or (newG shl 8) or newB

                // Voisins 4-connexes
                val neighbors = arrayOf(
                    Pair(cx + 1, cy),
                    Pair(cx - 1, cy),
                    Pair(cx, cy + 1),
                    Pair(cx, cy - 1)
                )
                for ((nx, ny) in neighbors) {
                    if (nx in 0 until width && ny in 0 until height) {
                        val nIdx = ny * width + nx
                        if (!visited[nIdx]) {
                            visited[nIdx] = true
                            if (isMatch(pixels[nIdx])) {
                                queue.add(nIdx)
                            }
                        }
                    }
                }
            }
        }
        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    return outBitmap
}

/**
 * Sauvegarde complète du projet de calques dans le stockage local de l'application.
 */
private fun saveCalcProject(context: Context, projectName: String, layers: List<CalcLayer>): Boolean {
    return try {
        val projectsDir = File(context.filesDir, "calc_projects")
        if (!projectsDir.exists()) projectsDir.mkdirs()

        val safeName = projectName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val projDir = File(projectsDir, "${safeName}_${System.currentTimeMillis()}")
        if (!projDir.exists()) projDir.mkdirs()

        val json = JSONObject()
        json.put("name", projectName)
        json.put("date", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date()))
        json.put("timestamp", System.currentTimeMillis())

        val layersArray = JSONArray()
        layers.forEachIndexed { index, layer ->
            val layerObj = JSONObject()
            val fileName = "layer_$index.png"
            layerObj.put("id", layer.id)
            layerObj.put("name", layer.name)
            layerObj.put("fileName", fileName)
            layerObj.put("isVisible", layer.isVisible)
            layerObj.put("opacity", layer.opacity.toDouble())
            layerObj.put("blendMode", layer.blendMode.name)
            layersArray.put(layerObj)

            // Sauvegarde de l'image du calque
            val layerFile = File(projDir, fileName)
            FileOutputStream(layerFile).use { out ->
                layer.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        json.put("layers", layersArray)

        // Sauvegarder métadonnées JSON
        val metaFile = File(projDir, "project.json")
        metaFile.writeText(json.toString(), Charsets.UTF_8)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * Supprime un projet de calques et tous ses fichiers associés du stockage local.
 */
private fun deleteCalcProject(context: Context, folderName: String): Boolean {
    return try {
        val projectsDir = File(context.filesDir, "calc_projects")
        val projDir = File(projectsDir, folderName)
        if (projDir.exists()) {
            projDir.deleteRecursively()
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * Récupère la liste des projets sauvegardés.
 */
private fun loadSavedProjectsList(context: Context): List<SavedProjectMeta> {
    val results = mutableListOf<SavedProjectMeta>()
    try {
        val projectsDir = File(context.filesDir, "calc_projects")
        if (!projectsDir.exists()) return emptyList()

        val dirs = projectsDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        dirs.sortedByDescending { it.lastModified() }.forEach { dir ->
            val metaFile = File(dir, "project.json")
            if (metaFile.exists()) {
                val json = JSONObject(metaFile.readText(Charsets.UTF_8))
                val name = json.optString("name", dir.name)
                val dateStr = json.optString("date", "")
                val layersArr = json.optJSONArray("layers")
                val count = layersArr?.length() ?: 0

                // Charger miniature du premier calque existant
                var thumb: Bitmap? = null
                if (count > 0) {
                    val firstObj = layersArr?.getJSONObject(count - 1)
                    val fn = firstObj?.optString("fileName", "layer_0.png") ?: "layer_0.png"
                    val thumbFile = File(dir, fn)
                    if (thumbFile.exists()) {
                        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
                        thumb = BitmapFactory.decodeFile(thumbFile.absolutePath, options)
                    }
                }

                results.add(
                    SavedProjectMeta(
                        folderName = dir.name,
                        name = name,
                        dateStr = dateStr,
                        layerCount = count,
                        thumbnailBitmap = thumb
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return results
}

/**
 * Charge les calques d'un projet sauvegardé.
 */
private fun loadCalcProject(context: Context, folderName: String): List<CalcLayer> {
    val loaded = mutableListOf<CalcLayer>()
    try {
        val projectsDir = File(context.filesDir, "calc_projects")
        val projDir = File(projectsDir, folderName)
        val metaFile = File(projDir, "project.json")
        if (!metaFile.exists()) return emptyList()

        val json = JSONObject(metaFile.readText(Charsets.UTF_8))
        val layersArr = json.optJSONArray("layers") ?: return emptyList()

        for (i in 0 until layersArr.length()) {
            val obj = layersArr.getJSONObject(i)
            val name = obj.optString("name", "Calque $i")
            val fileName = obj.optString("fileName", "layer_$i.png")
            val isVisible = obj.optBoolean("isVisible", true)
            val opacity = obj.optDouble("opacity", 1.0).toFloat()
            val blendModeStr = obj.optString("blendMode", LayerBlendMode.NORMAL.name)
            val blendMode = try {
                LayerBlendMode.valueOf(blendModeStr)
            } catch (e: Exception) {
                LayerBlendMode.NORMAL
            }

            val imgFile = File(projDir, fileName)
            if (imgFile.exists()) {
                val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                if (bmp != null) {
                    val mutableBmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
                    loaded.add(
                        CalcLayer(
                            name = name,
                            bitmap = mutableBmp,
                            isVisible = isVisible,
                            opacity = opacity,
                            blendMode = blendMode
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return loaded
}

/**
 * Crée un calque bitmap entièrement transparent contenant une grille régulière pour la mise au carré.
 * Le fond est 100% transparent pour ne faire apparaître que les traits.
 * @param width Largeur du canevas en pixels (ex. 1080)
 * @param height Hauteur du canevas en pixels (ex. 1080)
 * @param columns Nombre de colonnes (ex. 19)
 * @param rows Nombre de lignes (ex. 23)
 * @param lineColor Couleur des traits (opaque, ex. Noir, Blanc, Rouge)
 */
private fun createGridBitmap(
    width: Int,
    height: Int,
    columns: Int = 19,
    rows: Int = 23,
    lineColor: Int = android.graphics.Color.BLACK
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    // S'assurer que le fond est rigoureusement et totalement transparent
    bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
    val canvas = android.graphics.Canvas(bitmap)

    // Lignes de grille régulières
    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = 2.0f
        style = Paint.Style.STROKE
    }

    // Ligne de bordure extérieure
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = 3.0f
        style = Paint.Style.STROKE
    }

    // Tracé des lignes verticales (19 colonnes)
    val colWidth = width.toFloat() / columns.toFloat()
    for (i in 0..columns) {
        val x = (i * colWidth).coerceIn(0f, width.toFloat() - 1f)
        val paint = if (i == 0 || i == columns) borderPaint else gridPaint
        canvas.drawLine(x, 0f, x, height.toFloat(), paint)
    }

    // Tracé des lignes horizontales (23 lignes)
    val rowHeight = height.toFloat() / rows.toFloat()
    for (j in 0..rows) {
        val y = (j * rowHeight).coerceIn(0f, height.toFloat() - 1f)
        val paint = if (j == 0 || j == rows) borderPaint else gridPaint
        canvas.drawLine(0f, y, width.toFloat(), y, paint)
    }

    return bitmap
}

/**
 * Sauvegarde automatique de l'espace de travail actif (l'ensemble des calques et de l'image de travail)
 * pour conserver l'état même si l'utilisateur quitte l'application.
 */
private fun saveCalcWorkspace(context: Context, layers: List<CalcLayer>) {
    try {
        val baseDir = context.filesDir
        val workspaceDir = File(baseDir, "calc_autosave")
        val tmpDir = File(baseDir, "calc_autosave_tmp")
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        tmpDir.mkdirs()

        val json = JSONObject()
        val array = JSONArray()
        layers.forEachIndexed { index, layer ->
            val fileName = "layer_$index.png"
            val file = File(tmpDir, fileName)
            FileOutputStream(file).use { out ->
                layer.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val obj = JSONObject().apply {
                put("id", layer.id)
                put("name", layer.name)
                put("fileName", fileName)
                put("isVisible", layer.isVisible)
                put("opacity", layer.opacity.toDouble())
                put("blendMode", layer.blendMode.name)
            }
            array.put(obj)
        }
        json.put("layers", array)
        File(tmpDir, "workspace.json").writeText(json.toString(), Charsets.UTF_8)

        if (workspaceDir.exists()) {
            workspaceDir.deleteRecursively()
        }
        tmpDir.renameTo(workspaceDir)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Charge l'espace de travail conservé depuis le stockage local privé.
 */
private fun loadCalcWorkspace(context: Context): List<CalcLayer>? {
    return try {
        val workspaceDir = File(context.filesDir, "calc_autosave")
        val metaFile = File(workspaceDir, "workspace.json")
        if (!metaFile.exists()) return null

        val json = JSONObject(metaFile.readText(Charsets.UTF_8))
        val array = json.optJSONArray("layers") ?: return null
        if (array.length() == 0) return null

        val list = mutableListOf<CalcLayer>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("id", UUID.randomUUID().toString())
            val name = obj.optString("name", "Calque $i")
            val fileName = obj.optString("fileName", "layer_$i.png")
            val isVisible = obj.optBoolean("isVisible", true)
            val opacity = obj.optDouble("opacity", 1.0).toFloat()
            val blendModeStr = obj.optString("blendMode", LayerBlendMode.NORMAL.name)
            val blendMode = try {
                LayerBlendMode.valueOf(blendModeStr)
            } catch (e: Exception) {
                LayerBlendMode.NORMAL
            }
            val imgFile = File(workspaceDir, fileName)
            if (imgFile.exists()) {
                val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                if (bmp != null) {
                    val mutableBmp = bmp.copy(Bitmap.Config.ARGB_8888, true)
                    list.add(
                        CalcLayer(
                            id = id,
                            name = name,
                            bitmap = mutableBmp,
                            isVisible = isVisible,
                            opacity = opacity,
                            blendMode = blendMode
                        )
                    )
                }
            }
        }
        if (list.isNotEmpty()) list else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Nettoie l'espace de travail sauvegardé en cas de réinitialisation complète du canevas.
 */
private fun clearCalcWorkspace(context: Context) {
    try {
        val workspaceDir = File(context.filesDir, "calc_autosave")
        if (workspaceDir.exists()) {
            workspaceDir.deleteRecursively()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


