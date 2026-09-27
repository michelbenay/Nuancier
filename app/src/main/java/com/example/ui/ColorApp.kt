package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.tracker.TokenStatsDialog
import com.example.agent.tracker.TokenUsageTracker
import com.example.utils.ColorExportUtils
import com.example.utils.ColorUtils
import kotlinx.coroutines.launch

// Extension pour parser un hex #RRGGBB en composable Color de manière sécurisée
fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color.Gray
    }
}

// Fonction pour vérifier si une couleur est claire (pour choisir le texte noir ou blanc)
fun String.isLightColor(): Boolean {
    val rgb = ColorUtils.hexToRgb(this)
    val luminance = 0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]
    return luminance > 0.5f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorApp(viewModel: ColorViewModel, modifier: Modifier = Modifier) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val allPrimaryColors by viewModel.allPrimaryColors.collectAsStateWithLifecycle()
    val baseColors by viewModel.baseColors.collectAsStateWithLifecycle()
    val mixedColors by viewModel.mixedColors.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedBaseColorIds.collectAsStateWithLifecycle()
    val proportions by viewModel.colorProportions.collectAsStateWithLifecycle()
    val mixedHex by viewModel.mixedResultHex.collectAsStateWithLifecycle()
    val currentMixName by viewModel.currentMixName.collectAsStateWithLifecycle()
    val selectedMixedColor by viewModel.selectedMixedColor.collectAsStateWithLifecycle()
    val isSynthPlaying by viewModel.soundSynthesizer.isPlaying.collectAsStateWithLifecycle()
    val pendingAnalysisBitmap by viewModel.pendingAnalysisBitmap.collectAsStateWithLifecycle()

    var showAddColorDialog by remember { mutableStateOf(false) }
    var showSynthesizerDialog by remember { mutableStateOf(false) }
    var showAgentDialog by remember { mutableStateOf(false) }
    var showTokenStatsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        TokenUsageTracker.init(context)
    }

    if (showTokenStatsDialog) {
        TokenStatsDialog(
            onDismiss = { showTokenStatsDialog = false }
        )
    }

    if (showSynthesizerDialog) {
        SynthesizerDialog(
            soundSynthesizer = viewModel.soundSynthesizer,
            onDismiss = { showSynthesizerDialog = false }
        )
    }

    if (showAgentDialog) {
        ColorAgentDialog(
            viewModel = viewModel,
            onDismiss = { showAgentDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Nuancier & Mélange",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            val opened = ColorExportUtils.openNuancierFolder(context)
                            if (!opened) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Application Files de Google introuvable (Dossier : Documents/Nuancier)")
                                }
                            }
                        },
                        modifier = Modifier.testTag("open_nuancier_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Ouvrir dans Google Files",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showAgentDialog = true },
                        modifier = Modifier.testTag("open_agent_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Assistant IA Peintre",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showTokenStatsDialog = true },
                        modifier = Modifier.testTag("open_token_stats_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Consommation de tokens",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showSynthesizerDialog = true },
                        modifier = Modifier.testTag("open_synthesizer_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (isSynthPlaying) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text("♪", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Ambiance Sonore Zen",
                                tint = if (isSynthPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Nuancier") },
                    label = { Text("Nuancier", maxLines = 1) },
                    modifier = Modifier.testTag("tab_mixer")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Palette, contentDescription = "Palette") },
                    label = { Text("Palette", maxLines = 1) },
                    modifier = Modifier.testTag("tab_palette")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.GridView, contentDescription = "Mélange") },
                    label = { Text("Mélange", maxLines = 1) },
                    modifier = Modifier.testTag("tab_creations")
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Biotech, contentDescription = "Analyse") },
                    label = { Text("Analyse", maxLines = 1) },
                    modifier = Modifier.testTag("tab_decomposition")
                )
                NavigationBarItem(
                    selected = activeTab == 4,
                    onClick = { viewModel.selectTab(4) },
                    icon = { Icon(Icons.Default.AspectRatio, contentDescription = "Size") },
                    label = { Text("Size", maxLines = 1) },
                    modifier = Modifier.testTag("tab_resize")
                )
                NavigationBarItem(
                    selected = activeTab == 5,
                    onClick = { viewModel.selectTab(5) },
                    icon = { Icon(Icons.Default.Layers, contentDescription = "Calc") },
                    label = { Text("Calc", maxLines = 1) },
                    modifier = Modifier.testTag("tab_calc")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            for (tabIndex in 0..5) {
                val isActive = (activeTab == tabIndex)
                Box(
                    modifier = if (isActive) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .size(0.dp)
                            .graphicsLayer { alpha = 0f }
                    }
                ) {
                    when (tabIndex) {
                        0 -> MixerScreen(
                            baseColors = baseColors,
                            selectedIds = selectedIds,
                            proportions = proportions,
                            mixedHex = mixedHex,
                            mixName = currentMixName,
                            onMixNameChange = { viewModel.setMixName(it) },
                            onToggleSelect = { viewModel.toggleBaseColorSelection(it) },
                            onSetProportion = { id, parts -> viewModel.setBaseColorProportion(id, parts) },
                            onSaveAsSecondaryColor = { name ->
                                viewModel.saveAsSecondaryColor(name) {
                                    scope.launch {
                                        viewModel.selectTab(2)
                                        snackbarHostState.showSnackbar("Mélange enregistré dans les Mélanges !")
                                    }
                                }
                            },
                            onSaveAsBaseColor = { name ->
                                viewModel.saveAsBaseColor(name) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Mélange ajouté au nuancier !")
                                    }
                                }
                            },
                            onRemoveFromBaseList = { color -> viewModel.removeFromBaseList(color) },
                            onClearMixer = { viewModel.clearMixer() },
                            onRemoveAllDirectColors = {
                                viewModel.removeAllDirectNuancierColors { count ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("$count couleur(s) supprimée(s) du nuancier.")
                                    }
                                }
                            },
                            onImportNuancierColors = { list, callback ->
                                viewModel.importNuancierColors(list, callback)
                            },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        )
                        1 -> PaletteScreen(
                            primaryColors = allPrimaryColors,
                            onToggleInBaseList = { viewModel.toggleInBaseList(it) },
                            onDeletePrimaryColor = { viewModel.deleteBaseColor(it) },
                            onCreateBaseColor = { showAddColorDialog = true }
                        )
                        2 -> CreationsScreen(
                            mixedColors = mixedColors,
                            selectedMixedColor = selectedMixedColor,
                            onSelectMixedColor = { viewModel.selectMixedColor(it) },
                            onDeleteMixedColor = { viewModel.deleteMixedColor(it) },
                            onDeleteMixedColors = { viewModel.deleteMixedColors(it) },
                            onRenameMixedColor = { mixed, newName -> viewModel.renameMixedColor(mixed, newName) },
                            onCopyToBaseColors = { mixed ->
                                viewModel.applyMixtureToNuancier(mixed, allPrimaryColors) { newlyCheckedCount ->
                                    scope.launch {
                                        val msg = if (newlyCheckedCount > 0) {
                                            "Mélange « ${mixed.name} » et ses composantes appliqués au Nuancier ! ($newlyCheckedCount couleur(s) activée(s))"
                                        } else {
                                            "Mélange « ${mixed.name} » et ses composantes appliqués au Nuancier !"
                                        }
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                }
                            },
                            onCopyToPalette = { mixed ->
                                viewModel.addCustomBaseColor(mixed.name, mixed.hexCode)
                                viewModel.selectMixedColor(null)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Couleur \"${mixed.name}\" ajoutée à la palette !")
                                }
                            },
                            onImportMixtures = { list, callback ->
                                viewModel.importMixtures(list) { count ->
                                    callback(count)
                                }
                            },
                            onShowSnackbar = { msg ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        )
                        3 -> ColorDecompositionScreen(
                            baseColors = baseColors,
                            allPrimaryColors = allPrimaryColors,
                            pendingImageBitmap = pendingAnalysisBitmap,
                            onConsumePendingImage = { viewModel.clearPendingAnalysisBitmap() },
                            onToggleColorInPalette = { color ->
                                viewModel.toggleInBaseList(color)
                            },
                            onApplyRecipe = { recipe, name ->
                                viewModel.applyRecipeToMixer(recipe, allPrimaryColors, name) { newlyCheckedCount ->
                                    scope.launch {
                                        val msg = if (newlyCheckedCount > 0) {
                                            "Mélange appliqué au Nuancier ! $newlyCheckedCount couleur(s) cochée(s) dans la palette."
                                        } else {
                                            "Mélange de composantes appliqué au Nuancier !"
                                        }
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                }
                            }
                        )
                        4 -> ImageResizeScreen(
                            colorViewModel = viewModel,
                            onShowSnackbar = { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                        5 -> CalcScreen(
                            mixedColors = mixedColors,
                            isSelected = isActive,
                            onShowSnackbar = { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddColorDialog) {
        AddColorDialog(
            onDismiss = { showAddColorDialog = false },
            onAddColor = { name, hex ->
                viewModel.addCustomBaseColor(name, hex)
                showAddColorDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Couleur de base \"$name\" ajoutée !")
                }
            }
        )
    }
}
