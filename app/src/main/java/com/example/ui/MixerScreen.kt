package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.data.BaseColor
import com.example.utils.ColorExportUtils
import com.example.utils.ColorUtils
import kotlin.math.roundToInt

@Composable
fun MixerScreen(
    baseColors: List<BaseColor>,
    selectedIds: Set<Int>,
    proportions: Map<Int, Int>,
    mixedHex: String,
    mixName: String = "",
    onMixNameChange: (String) -> Unit = {},
    onToggleSelect: (Int) -> Unit,
    onSetProportion: (Int, Int) -> Unit,
    onSaveAsSecondaryColor: (String) -> Unit,
    onSaveAsBaseColor: (String) -> Unit,
    onRemoveFromBaseList: (BaseColor) -> Unit,
    onClearMixer: () -> Unit,
    onRemoveAllDirectColors: (() -> Unit)? = null,
    onImportNuancierColors: (List<BaseColor>, (Int) -> Unit) -> Unit = { _, _ -> },
    onShowSnackbar: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var localMixName by remember(mixName) { mutableStateOf(mixName) }
    val effectiveMixName = if (mixName.isNotEmpty()) mixName else localMixName
    val totalParts = proportions.values.sum()

    var showExportFormatDialog by remember { mutableStateOf(false) }
    var customFileName by remember { mutableStateOf("") }
    var lastImportedFileName by remember { mutableStateOf<String?>(null) }
    var exportMode by remember { mutableStateOf("json") }

    // État d'enregistrement du nuancier pour conditionner la poubelle générale
    var isNuancierSaved by rememberSaveable { mutableStateOf(false) }
    var lastSavedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var savedDirectColorIds by rememberSaveable { mutableStateOf<Set<Int>>(emptySet()) }
    var showDeleteAllDirectDialog by remember { mutableStateOf(false) }

    // Couleurs directes au nuancier (hors palette de pigments primaires)
    val directColors = remember(baseColors) { baseColors.filter { !it.isInPalette } }
    val directColorsCount = directColors.size
    val canShowBulkDelete = isNuancierSaved && directColorsCount > 0 && directColors.all { it.id in savedDirectColorIds }

    // Initialiser le nom de fichier par défaut à l'ouverture du dialogue d'export
    LaunchedEffect(showExportFormatDialog) {
        if (showExportFormatDialog) {
            val dateStr = ColorExportUtils.generateDefaultDatePrefix()
            customFileName = "Mon_Nuancier_$dateStr"
        }
    }

    // Lanceur de secours pour créer un document (SAK / Scoped Storage Picker)
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ColorExportUtils.CreateNuancierDocumentContract()
    ) { uri ->
        if (uri != null) {
            val selectedList = if (selectedIds.isNotEmpty()) {
                baseColors.filter { it.id in selectedIds }
            } else {
                baseColors
            }
            val content = if (exportMode == "txt") {
                ColorExportUtils.generateNuancierPlainText(selectedList)
            } else {
                ColorExportUtils.generateNuancierJson(selectedList)
            }
            val written = ColorExportUtils.writeToUri(context, uri, content)
            if (written) {
                isNuancierSaved = true
                savedDirectColorIds = directColors.map { it.id }.toSet()
                lastSavedFileName = "Nuancier"
                onShowSnackbar("Fichier sauvegardé avec succès.")
            } else {
                onShowSnackbar("Erreur lors de l'enregistrement du fichier.")
            }
        }
    }

    // Fonction d'enregistrement direct dans le répertoire Nuancier
    fun executeDirectSave(mode: String, fileNameInput: String) {
        val selectedList = if (selectedIds.isNotEmpty()) {
            baseColors.filter { it.id in selectedIds }
        } else {
            baseColors
        }

        val ext = if (mode == "txt") ".txt" else ".json"
        val baseName = fileNameInput.trim().ifEmpty {
            "Mon_Nuancier_${ColorExportUtils.generateDefaultDatePrefix()}"
        }
        val finalFileName = if (baseName.endsWith(ext, ignoreCase = true)) baseName else "$baseName$ext"

        val content = if (mode == "txt") {
            ColorExportUtils.generateNuancierPlainText(selectedList)
        } else {
            ColorExportUtils.generateNuancierJson(selectedList)
        }

        val (success, _) = ColorExportUtils.saveToNuancierFolder(context, finalFileName, content)
        if (success) {
            isNuancierSaved = true
            savedDirectColorIds = directColors.map { it.id }.toSet()
            lastSavedFileName = finalFileName
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
                    val importedList = ColorExportUtils.parseNuancierImportJson(jsonContent)
                    if (importedList.isNotEmpty()) {
                        onImportNuancierColors(importedList) { count ->
                            lastImportedFileName = fileName
                            isNuancierSaved = true
                            lastSavedFileName = fileName
                            savedDirectColorIds = baseColors.filter { !it.isInPalette }.map { it.id }.toSet() + importedList.filter { !it.isInPalette }.map { it.id }.toSet()
                            if (count > 0) {
                                onShowSnackbar("$count couleur(s) rapatriée(s) depuis \"$fileName\" !")
                            } else {
                                onShowSnackbar("Les couleurs de \"$fileName\" sont déjà présentes dans le nuancier.")
                            }
                        }
                    } else {
                        onShowSnackbar("Aucune couleur valide trouvée dans \"$fileName\".")
                    }
                } catch (e: Exception) {
                    onShowSnackbar("Erreur lors de la lecture de \"$fileName\" : ${e.message}")
                }
            } else {
                onShowSnackbar("Impossible de lire le fichier sélectionné.")
            }
        }
    }

    // Boîte de dialogue de confirmation pour supprimer en bloc les couleurs directes du nuancier
    if (showDeleteAllDirectDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDirectDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Supprimer les couleurs ajoutées ?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Voulez-vous supprimer en bloc les $directColorsCount couleur(s) ajoutée(s) directement au nuancier (hors palette) ?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (lastSavedFileName != null) {
                        Text(
                            text = "Votre nuancier a bien été enregistré sous « $lastSavedFileName ». Vos couleurs restent sauvegardées dans vos fichiers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDirectDialog = false
                        isNuancierSaved = false
                        savedDirectColorIds = emptySet()
                        onRemoveAllDirectColors?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_delete_all_direct_colors_button")
                ) {
                    Text("Supprimer ($directColorsCount)")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllDirectDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    // Boîte de dialogue de choix du nom et format d'export
    if (showExportFormatDialog) {
        val countToExport = if (selectedIds.isNotEmpty()) selectedIds.size else baseColors.size
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
                    Text("Enregistrer $countToExport couleur(s)", fontWeight = FontWeight.Bold)
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
                        placeholder = { Text("ex: Mon_Nuancier_Aquarelle") },
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
                                Text("Lisible, imprimable avec noms, pigments et codes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Format JSON pour rapatriement (.json)", fontWeight = FontWeight.SemiBold)
                                Text("Sauvegarde complète du nuancier pour réimportation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportFormatDialog = false }) {
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
        // === FIXED MIXER RESULT ZONE ===
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("mixed_result_card"),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Première ligne : Bloc de couleur élargi et boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bloc de couleur élargi au maximum (weight = 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedIds.isEmpty()) Color.LightGray.copy(alpha = 0.4f) else mixedHex.toColor())
                            .border(BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                            .testTag("result_color_block"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedIds.isEmpty()) {
                            Text(
                                text = "Mélange vide",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bouton Enregistrer (Disquette)
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onSaveAsSecondaryColor(effectiveMixName)
                                localMixName = ""
                                onMixNameChange("")
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                BorderStroke(
                                    1.dp, 
                                    if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                ), 
                                RoundedCornerShape(8.dp)
                            )
                            .testTag("save_as_secondary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Enregistrer le mélange",
                            tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    // Bouton Vider (Poubelle)
                    IconButton(
                        onClick = {
                            if (selectedIds.isNotEmpty()) {
                                onClearMixer()
                                localMixName = ""
                                onMixNameChange("")
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                BorderStroke(
                                    1.dp, 
                                    if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                ), 
                                RoundedCornerShape(8.dp)
                            )
                            .testTag("clear_mixer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Vider le mélange",
                            tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                // Deuxième ligne : Saisie du nom du mélange ou consigne
                if (selectedIds.isNotEmpty()) {
                    OutlinedTextField(
                        value = effectiveMixName,
                        onValueChange = {
                            localMixName = it
                            onMixNameChange(it)
                        },
                        label = { Text("Nom du mélange ($mixedHex)", fontSize = 11.sp) },
                        placeholder = { Text("Ex: Vert d'eau...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mix_name_input"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Cocher des couleurs de base pour composer un mélange",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // === SCROLLABLE SCENE ===
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section proportions (si des couleurs sont sélectionnées)
            if (selectedIds.isNotEmpty()) {
                item {
                    Text(
                        text = "Proportions du mélange (en parts)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(baseColors.filter { it.id in selectedIds }) { color ->
                    val parts = proportions[color.id] ?: 1
                    val percentage = if (totalParts > 0) (parts.toFloat() / totalParts * 100).roundToInt() else 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proportion_card_${color.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Échantillon et nom
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(color.hexCode.toColor())
                                        .border(BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = color.name,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "$parts part(s) • $percentage%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Contrôles de parts
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { if (parts > 1) onSetProportion(color.id, parts - 1) },
                                    enabled = parts > 1,
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors()
                                ) {
                                    Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text(
                                    text = "$parts",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(16.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { onSetProportion(color.id, parts + 1) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors()
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Section Liste de bases / Nuancier avec Barre d'actions (Enregistrer & Rapatrier)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
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
                                text = "Nuancier de base",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (selectedIds.isNotEmpty()) "${selectedIds.size} sélectionnée(s) sur ${baseColors.size}"
                                       else "${baseColors.size} couleur(s) au total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (lastSavedFileName != null && canShowBulkDelete) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = "Enregistré : $lastSavedFileName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else if (lastImportedFileName != null) {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Poubelle générale pour suppression en bloc des couleurs directes
                            // (Visible UNIQUEMENT si le nuancier a été enregistré)
                            if (canShowBulkDelete) {
                                FilledTonalIconButton(
                                    onClick = { showDeleteAllDirectDialog = true },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("delete_all_direct_colors_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer en bloc les $directColorsCount couleurs directes du nuancier",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Bouton Enregistrer / Exporter
                            FilledIconButton(
                                onClick = { showExportFormatDialog = true },
                                enabled = baseColors.isNotEmpty(),
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Enregistrer le nuancier",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Bouton Rapatrier / Importer JSON
                            OutlinedIconButton(
                                onClick = { openDocLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Rapatrier",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (baseColors.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Aucune couleur dans la liste de bases.\nAllez dans l'onglet Palette pour cocher des couleurs primaires à affecter au nuancier.",
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(baseColors, key = { it.id }) { color ->
                    val isChecked = color.id in selectedIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSelect(color.id) }
                            .testTag("base_color_checkbox_card_${color.id}"),
                        border = BorderStroke(
                            width = if (isChecked) 2.dp else 1.dp,
                            color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleSelect(color.id) },
                                    modifier = Modifier.testTag("base_color_checkbox_${color.id}")
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color.hexCode.toColor())
                                        .border(BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                val pigmentInfo = ColorUtils.getPigmentInfo(color.name, color.hexCode)
                                val codeStr = if (color.pigmentCode.isNotEmpty()) color.pigmentCode else pigmentInfo.pigmentCode
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = color.name,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (codeStr.isNotEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = codeStr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${pigmentInfo.opacity.icon} ${pigmentInfo.opacity.label} • ${color.hexCode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!color.isInPalette) {
                                IconButton(
                                    onClick = { onRemoveFromBaseList(color) },
                                    modifier = Modifier.testTag("delete_base_color_button_${color.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer du nuancier",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
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
