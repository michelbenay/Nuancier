package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MixedColor
import com.example.utils.ColorExportUtils
import com.example.utils.ColorUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreationsScreen(
    mixedColors: List<MixedColor>,
    selectedMixedColor: MixedColor?,
    onSelectMixedColor: (MixedColor?) -> Unit,
    onDeleteMixedColor: (MixedColor) -> Unit,
    onDeleteMixedColors: (List<MixedColor>) -> Unit,
    onCopyToPalette: (MixedColor) -> Unit,
    onCopyToBaseColors: (MixedColor) -> Unit,
    onRenameMixedColor: (MixedColor, String) -> Unit,
    onImportMixtures: (List<MixedColor>, (Int) -> Unit) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val context = LocalContext.current
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }
    var colorToRename by remember { mutableStateOf<MixedColor?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var checkedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var customFileName by remember { mutableStateOf("") }
    var lastImportedFileName by remember { mutableStateOf<String?>(null) }

    // Variable temporaire pour le mode d'exportation
    var exportMode by remember { mutableStateOf("json") }

    // Nettoyer les checkedIds qui n'existent plus
    LaunchedEffect(mixedColors) {
        val existingIds = mixedColors.map { it.id }.toSet()
        checkedIds = checkedIds.filter { it in existingIds }.toSet()
    }

    // Réinitialiser le champ nom par défaut à l'ouverture du dialogue d'export
    LaunchedEffect(showExportFormatDialog) {
        if (showExportFormatDialog) {
            val dateStr = ColorExportUtils.generateDefaultDatePrefix()
            customFileName = "Mes_Melanges_$dateStr"
        }
    }

    // Lanceur de secours pour créer un document (SAK / Scoped Storage Picker)
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ColorExportUtils.CreateNuancierDocumentContract()
    ) { uri ->
        if (uri != null) {
            val selectedList = if (checkedIds.isNotEmpty()) {
                mixedColors.filter { it.id in checkedIds }
            } else {
                mixedColors
            }
            val content = if (exportMode == "txt") {
                ColorExportUtils.generatePlainText(selectedList)
            } else {
                ColorExportUtils.generateJson(selectedList)
            }
            val written = ColorExportUtils.writeToUri(context, uri, content)
            if (written) {
                onShowSnackbar("Fichier sauvegardé avec succès.")
            } else {
                onShowSnackbar("Erreur lors de l'enregistrement du fichier.")
            }
        }
    }

    // Fonction d'enregistrement direct dans le répertoire Nuancier
    fun executeDirectSave(mode: String, fileNameInput: String) {
        val selectedList = if (checkedIds.isNotEmpty()) {
            mixedColors.filter { it.id in checkedIds }
        } else {
            mixedColors
        }

        val ext = if (mode == "txt") ".txt" else ".json"
        val baseName = fileNameInput.trim().ifEmpty {
            "Mes_Melanges_${ColorExportUtils.generateDefaultDatePrefix()}"
        }
        val finalFileName = if (baseName.endsWith(ext, ignoreCase = true)) baseName else "$baseName$ext"

        val content = if (mode == "txt") {
            ColorExportUtils.generatePlainText(selectedList)
        } else {
            ColorExportUtils.generateJson(selectedList)
        }

        val (success, _) = ColorExportUtils.saveToNuancierFolder(context, finalFileName, content)
        if (success) {
            onShowSnackbar("Enregistré dans le dossier Nuancier : $finalFileName")
        } else {
            exportMode = mode
            createDocLauncher.launch(finalFileName)
        }
    }

    // Lanceur pour importer un fichier JSON
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ColorExportUtils.OpenNuancierDocumentContract()
    ) { uri ->
        if (uri != null) {
            var fileName = "Fichier JSON"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        val retrievedName = cursor.getString(nameIndex)
                        if (!retrievedName.isNullOrBlank()) {
                            fileName = retrievedName
                        }
                    }
                }
            } catch (_: Exception) { }

            val jsonContent = ColorExportUtils.readFromUri(context, uri)
            if (jsonContent != null) {
                try {
                    val importedList = ColorExportUtils.parseImportJson(jsonContent)
                    if (importedList.isNotEmpty()) {
                        onImportMixtures(importedList) { count ->
                            lastImportedFileName = fileName
                            if (count > 0) {
                                onShowSnackbar("$count mélange(s) rapatrié(s) depuis \"$fileName\" !")
                            } else {
                                onShowSnackbar("Les mélanges de \"$fileName\" sont déjà présents.")
                            }
                        }
                    } else {
                        onShowSnackbar("Aucun mélange valide trouvé dans \"$fileName\".")
                    }
                } catch (e: Exception) {
                    onShowSnackbar("Erreur lors de la lecture de \"$fileName\" : ${e.message}")
                }
            } else {
                onShowSnackbar("Impossible de lire le fichier sélectionné.")
            }
        }
    }

    // Boîte de dialogue de choix du nom et format d'export
    if (showExportFormatDialog) {
        val countToExport = if (checkedIds.isNotEmpty()) checkedIds.size else mixedColors.size
        AlertDialog(
            onDismissRequest = { showExportFormatDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.SaveAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Enregistrer $countToExport mélange(s)", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Le fichier sera automatiquement rangé dans le répertoire \"Nuancier\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = { customFileName = it },
                        label = { Text("Nom du fichier") },
                        placeholder = { Text("ex: Sous_bois_Automne") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text(
                        text = "Choisissez le format de sauvegarde :",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Option 1 : Fichier texte en clair (.txt)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nameToSave = customFileName
                                showExportFormatDialog = false
                                executeDirectSave("txt", nameToSave)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Format Texte en clair (.txt)", fontWeight = FontWeight.SemiBold)
                                Text("Lisible, imprimable avec noms, pigments et pourcentages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 2 : Fichier JSON pour rapatriement (.json)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nameToSave = customFileName
                                showExportFormatDialog = false
                                executeDirectSave("json", nameToSave)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column {
                                Text("Format Sauvegarde (.json)", fontWeight = FontWeight.SemiBold)
                                Text("Format technique permettant de rapatrier vos mélanges à tout moment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportFormatDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Boîte de dialogue de confirmation de suppression en bloc
    if (showBulkDeleteConfirmDialog) {
        val countToDelete = checkedIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Supprimer $countToDelete mélange(s) ?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Êtes-vous sûr de vouloir supprimer définitivement les $countToDelete mélanges sélectionnés ? Cette action est irréversible.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = mixedColors.filter { it.id in checkedIds }
                        onDeleteMixedColors(toDelete)
                        checkedIds = emptySet()
                        showBulkDeleteConfirmDialog = false
                        onShowSnackbar("$countToDelete mélange(s) supprimé(s)")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Boîte de dialogue de renommage d'un mélange
    if (colorToRename != null) {
        val target = colorToRename!!
        AlertDialog(
            onDismissRequest = { colorToRename = null },
            icon = {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Renommer le mélange", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(target.hexCode.toColor())
                                .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                        )
                        Text(
                            text = "Code : ${target.hexCode.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        label = { Text("Nom du mélange") },
                        placeholder = { Text("Ex: Bleu Océan, Ocre...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            onRenameMixedColor(target, renameInputText.trim())
                            onShowSnackbar("Mélange renommé : \"${renameInputText.trim()}\"")
                            colorToRename = null
                        }
                    },
                    enabled = renameInputText.isNotBlank()
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { colorToRename = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // === BARRE D'ACTIONS EXPORT / IMPORT / SUPPRESSION ===
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mes Mélanges",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (checkedIds.isNotEmpty()) "${checkedIds.size} sélectionné(s) sur ${mixedColors.size}"
                               else "${mixedColors.size} mélange(s) au total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastImportedFileName != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$lastImportedFileName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Bouton Tout cocher / Tout décocher
                    if (mixedColors.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                checkedIds = if (checkedIds.size == mixedColors.size) {
                                    emptySet()
                                } else {
                                    mixedColors.map { it.id }.toSet()
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (checkedIds.size == mixedColors.size) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                contentDescription = "Tout cocher/décocher",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 2. Bouton Poubelle (Suppression en bloc des couleurs cochées)
                    if (mixedColors.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (checkedIds.isNotEmpty()) {
                                    showBulkDeleteConfirmDialog = true
                                } else {
                                    onShowSnackbar("Cochez d'abord les mélanges à supprimer")
                                }
                            },
                            enabled = checkedIds.isNotEmpty(),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Supprimer les mélanges cochés",
                                tint = if (checkedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 3. Bouton Enregistrer / Exporter
                    FilledIconButton(
                        onClick = { showExportFormatDialog = true },
                        enabled = mixedColors.isNotEmpty(),
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Enregistrer les mélanges",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 4. Bouton Rapatrier / Importer JSON
                    OutlinedIconButton(
                        onClick = { openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Rapatrier",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (mixedColors.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Aucun mélange enregistré",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Allez dans l'onglet Mélangeur pour fabriquer vos teintes et les retrouver ici, ou cliquez sur \"Rapatrier\" pour importer une sauvegarde JSON.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rapatrier une sauvegarde JSON")
                    }
                }
            }
        } else {
            // Mosaïque des couleurs en grille
            Column(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("creations_grid")
                ) {
                    items(mixedColors, key = { it.id }) { mixed ->
                        val isSelected = selectedMixedColor?.id == mixed.id
                        val isChecked = mixed.id in checkedIds

                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelected) onSelectMixedColor(null)
                                        else onSelectMixedColor(mixed)
                                    },
                                    onLongClick = {
                                        colorToRename = mixed
                                        renameInputText = mixed.name
                                    }
                                )
                                .testTag("mixed_color_item_${mixed.id}"),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                     else if (isChecked) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                                     else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(mixed.hexCode.toColor())
                            ) {
                                // Case à cocher en haut à droite de chaque pastille
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isChecked) MaterialTheme.colorScheme.surface
                                            else Color.Black.copy(alpha = 0.35f)
                                        )
                                        .clickable {
                                            checkedIds = if (isChecked) {
                                                checkedIds - mixed.id
                                            } else {
                                                checkedIds + mixed.id
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            checkedIds = if (checked) {
                                                checkedIds + mixed.id
                                            } else {
                                                checkedIds - mixed.id
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Bandeau de nom au bas de la pastille
                                val isLight = mixed.hexCode.isLightColor()
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(if (isLight) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f))
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = mixed.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLight) Color.Black else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Dialogue d'affichage / gestion du mélange sélectionné
                if (selectedMixedColor != null) {
                    val mixed = selectedMixedColor
                    AlertDialog(
                        onDismissRequest = { onSelectMixedColor(null) },
                        modifier = Modifier.testTag("reverse_lookup_panel"),
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = mixed.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        IconButton(
                                            onClick = {
                                                colorToRename = mixed
                                                renameInputText = mixed.name
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Renommer la couleur",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Code Hex : ${mixed.hexCode.uppercase()}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(mixed.hexCode.toColor())
                                            .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp))
                                    )
                                    IconButton(
                                        onClick = { onSelectMixedColor(null) },
                                        modifier = Modifier.testTag("dismiss_dialog_cross_button")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                                    }
                                }
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Formule de mélange :",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                val items = mixed.getRecipeItems()
                                val total = items.sumOf { it.parts }

                                if (items.isEmpty()) {
                                    val pigment = ColorUtils.getPigmentInfo(mixed.name, mixed.hexCode)
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(mixed.hexCode.toColor())
                                                    .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(4.dp))
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mixed.name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                if (pigment.pigmentCode.isNotEmpty()) {
                                                    Text(
                                                        text = "Pigment : ${pigment.pigmentCode}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Couleur pure (100%)",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items.forEach { recipeItem ->
                                            val pct = if (total > 0) (recipeItem.parts.toFloat() / total * 100).roundToInt() else 0
                                            val pigment = ColorUtils.getPigmentInfo(recipeItem.name, recipeItem.hexCode)
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(recipeItem.hexCode.toColor())
                                                            .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(4.dp))
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = recipeItem.name,
                                                            fontWeight = FontWeight.SemiBold,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            text = if (pigment.pigmentCode.isNotEmpty()) "${pigment.pigmentCode} • ${recipeItem.hexCode.uppercase()}" else recipeItem.hexCode.uppercase(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Text(
                                                        text = "${recipeItem.parts} part(s) ($pct%)",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onCopyToPalette(mixed) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("copy_to_palette_button")
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Palette", fontSize = 12.sp, maxLines = 1)
                                    }
                                    Button(
                                        onClick = { onCopyToBaseColors(mixed) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("copy_to_base_button")
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Nuancier", fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onDeleteMixedColor(mixed) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        modifier = Modifier.testTag("delete_recipe_button")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Supprimer")
                                    }
                                    TextButton(
                                        onClick = { onSelectMixedColor(null) },
                                        modifier = Modifier.testTag("close_details_button")
                                    ) {
                                        Text("Fermer")
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
