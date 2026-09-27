package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.data.MixedColor
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ColorExportData(
    val name: String,
    val hexCode: String,
    val components: List<ColorComponentExport>
)

data class ColorComponentExport(
    val name: String,
    val pigmentCode: String,
    val hexCode: String,
    val parts: Int,
    val percentage: Int
)

object ColorExportUtils {

    fun generateDefaultDatePrefix(): String {
        return SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    }

    /**
     * Convertit une liste de MixedColor en structure de données prête pour l'export.
     */
    fun prepareExportData(mixedColors: List<MixedColor>): List<ColorExportData> {
        return mixedColors.map { mixed ->
            val recipeItems = mixed.getRecipeItems()
            val totalParts = recipeItems.sumOf { it.parts }

            val components = if (recipeItems.isEmpty()) {
                // Cas d'un mélange à une seule couleur sans recette complexe
                val pigment = ColorUtils.getPigmentInfo(mixed.name, mixed.hexCode)
                listOf(
                    ColorComponentExport(
                        name = mixed.name,
                        pigmentCode = pigment.pigmentCode,
                        hexCode = mixed.hexCode,
                        parts = 1,
                        percentage = 100
                    )
                )
            } else {
                recipeItems.map { item ->
                    val pigment = ColorUtils.getPigmentInfo(item.name, item.hexCode)
                    val pct = if (totalParts > 0) {
                        (item.parts.toFloat() / totalParts * 100).roundToInt()
                    } else 100
                    ColorComponentExport(
                        name = item.name,
                        pigmentCode = pigment.pigmentCode,
                        hexCode = item.hexCode,
                        parts = item.parts,
                        percentage = pct
                    )
                }
            }

            ColorExportData(
                name = mixed.name,
                hexCode = mixed.hexCode,
                components = components
            )
        }
    }

    /**
     * Génère le texte clair (.txt) élégant et lisible.
     */
    fun generatePlainText(items: List<MixedColor>): String {
        val exportData = prepareExportData(items)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val dateStr = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("        RECETTES ET MÉLANGES DE PEINTURE")
        sb.appendLine("   Date d'export : $dateStr")
        sb.appendLine("   Nombre de mélanges : ${exportData.size}")
        sb.appendLine("==================================================")
        sb.appendLine()

        exportData.forEachIndexed { index, mix ->
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("${index + 1}. [${mix.name}] - Code Hex : ${mix.hexCode.uppercase()}")
            sb.appendLine("   Composantes (${mix.components.size}) :")
            mix.components.forEach { comp ->
                val pigmentInfo = if (comp.pigmentCode.isNotEmpty()) " (Pigment: ${comp.pigmentCode})" else ""
                sb.appendLine("     • ${comp.name}$pigmentInfo - ${comp.hexCode.uppercase()} : ${comp.parts} part(s) (${comp.percentage}%)")
            }
            sb.appendLine()
        }

        sb.appendLine("==================================================")
        sb.appendLine("Généré par l'application Nuancier & Mélanges")
        sb.appendLine("==================================================")
        return sb.toString()
    }

    /**
     * Génère le JSON technique complet pour sauvegarde et réimportation exacte.
     */
    fun generateJson(items: List<MixedColor>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("type", "color_mixtures_backup")
        root.put("date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))

        val itemsArray = JSONArray()
        items.forEach { mixed ->
            val mixObj = JSONObject()
            mixObj.put("id", mixed.id)
            mixObj.put("name", mixed.name)
            mixObj.put("hexCode", mixed.hexCode)
            mixObj.put("recipeString", mixed.recipeString)

            val componentsArray = JSONArray()
            val recipeItems = mixed.getRecipeItems()
            val totalParts = recipeItems.sumOf { it.parts }

            if (recipeItems.isEmpty()) {
                val pigment = ColorUtils.getPigmentInfo(mixed.name, mixed.hexCode)
                val cObj = JSONObject()
                cObj.put("name", mixed.name)
                cObj.put("pigmentCode", pigment.pigmentCode)
                cObj.put("hexCode", mixed.hexCode)
                cObj.put("parts", 1)
                cObj.put("percentage", 100)
                componentsArray.put(cObj)
            } else {
                recipeItems.forEach { rItem ->
                    val pigment = ColorUtils.getPigmentInfo(rItem.name, rItem.hexCode)
                    val pct = if (totalParts > 0) (rItem.parts.toFloat() / totalParts * 100).roundToInt() else 100
                    val cObj = JSONObject()
                    cObj.put("name", rItem.name)
                    cObj.put("pigmentCode", pigment.pigmentCode)
                    cObj.put("hexCode", rItem.hexCode)
                    cObj.put("parts", rItem.parts)
                    cObj.put("percentage", pct)
                    componentsArray.put(cObj)
                }
            }
            mixObj.put("components", componentsArray)
            itemsArray.put(mixObj)
        }

        root.put("mixtures", itemsArray)
        return root.toString(2)
    }

    /**
     * Analyse un fichier JSON importé et retourne la liste des MixedColor à réintégrer.
     */
    fun parseImportJson(jsonStr: String): List<MixedColor> {
        val result = mutableListOf<MixedColor>()
        val root = JSONObject(jsonStr)
        val array = root.optJSONArray("mixtures") ?: JSONArray()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.optString("name", "Mélange importé")
            val hexCode = obj.optString("hexCode", "#FFFFFF")
            val recipeString = obj.optString("recipeString", "")

            result.add(
                MixedColor(
                    name = name,
                    hexCode = hexCode,
                    recipeString = recipeString,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    /**
     * Génère un export texte lisible pour le nuancier de base.
     */
    fun generateNuancierPlainText(items: List<com.example.data.BaseColor>): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val dateStr = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("             NUANCIER DE COULEURS DE BASE")
        sb.appendLine("   Date d'export : $dateStr")
        sb.appendLine("   Nombre de couleurs : ${items.size}")
        sb.appendLine("==================================================")
        sb.appendLine()

        items.forEachIndexed { index, color ->
            val pigmentInfo = ColorUtils.getPigmentInfo(color.name, color.hexCode)
            val codeStr = if (color.pigmentCode.isNotEmpty()) color.pigmentCode else pigmentInfo.pigmentCode
            val pigmentLabel = if (codeStr.isNotEmpty()) " (Pigment: $codeStr)" else ""
            sb.appendLine("--------------------------------------------------")
            sb.appendLine("${index + 1}. [${color.name}] - Code Hex : ${color.hexCode.uppercase()}$pigmentLabel")
            sb.appendLine("   Opacité : ${pigmentInfo.opacity.label}")
            sb.appendLine()
        }

        sb.appendLine("==================================================")
        sb.appendLine("Généré par l'application Nuancier & Mélanges")
        sb.appendLine("==================================================")
        return sb.toString()
    }

    /**
     * Génère le JSON complet pour sauvegarde et rapatriement exact du nuancier de base.
     */
    fun generateNuancierJson(items: List<com.example.data.BaseColor>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("type", "nuancier_backup")
        root.put("date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
        root.put("nombre_couleurs", items.size)

        val colorsArray = JSONArray()
        items.forEach { color ->
            val pigmentInfo = ColorUtils.getPigmentInfo(color.name, color.hexCode)
            val colorObj = JSONObject().apply {
                put("name", color.name)
                put("hexCode", color.hexCode)
                put("pigmentCode", if (color.pigmentCode.isNotEmpty()) color.pigmentCode else pigmentInfo.pigmentCode)
                put("opacity", if (color.opacity.isNotEmpty()) color.opacity else pigmentInfo.opacity.name)
            }
            colorsArray.put(colorObj)
        }
        root.put("colors", colorsArray)
        return root.toString(2)
    }

    /**
     * Analyse un fichier JSON importé pour le nuancier.
     * Supporte à la fois "colors", "couleurs", "palette", "bases" ou "mixtures".
     */
    fun parseNuancierImportJson(jsonStr: String): List<com.example.data.BaseColor> {
        val result = mutableListOf<com.example.data.BaseColor>()
        try {
            val root = JSONObject(jsonStr)
            val array = root.optJSONArray("colors")
                ?: root.optJSONArray("couleurs")
                ?: root.optJSONArray("bases")
                ?: root.optJSONArray("palette")
                ?: root.optJSONArray("mixtures")

            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name", obj.optString("nom", "Couleur ${i + 1}"))
                    var hex = obj.optString("hexCode", obj.optString("hex", obj.optString("color", "")))
                    if (hex.isNotBlank()) {
                        if (!hex.startsWith("#")) hex = "#$hex"
                        val pigmentInfo = ColorUtils.getPigmentInfo(name, hex)
                        val pigmentCode = obj.optString("pigmentCode", pigmentInfo.pigmentCode)
                        val opacity = obj.optString("opacity", pigmentInfo.opacity.name)
                        result.add(
                            com.example.data.BaseColor(
                                name = name,
                                hexCode = hex,
                                isDefault = false,
                                isInBaseList = true,
                                isInPalette = true,
                                pigmentCode = pigmentCode,
                                opacity = opacity
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Tenter avec un tableau JSON direct [ { "name": "...", "hexCode": "..." } ]
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name", obj.optString("nom", "Couleur ${i + 1}"))
                    var hex = obj.optString("hexCode", obj.optString("hex", obj.optString("color", "")))
                    if (hex.isNotBlank()) {
                        if (!hex.startsWith("#")) hex = "#$hex"
                        val pigmentInfo = ColorUtils.getPigmentInfo(name, hex)
                        result.add(
                            com.example.data.BaseColor(
                                name = name,
                                hexCode = hex,
                                isDefault = false,
                                isInBaseList = true,
                                isInPalette = true,
                                pigmentCode = obj.optString("pigmentCode", pigmentInfo.pigmentCode),
                                opacity = obj.optString("opacity", pigmentInfo.opacity.name)
                            )
                        )
                    }
                }
            } catch (_: Exception) { }
        }
        return result
    }

    /**
     * Retourne le répertoire "Nuancier" ou un sous-répertoire (ex: "Polygones") dans Documents (ou fallback sur le dossier privé si besoin).
     */
    fun getNuancierFolder(context: Context, subFolder: String? = null): java.io.File {
        return try {
            val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val baseDir = java.io.File(documentsDir, "Nuancier")
            val targetDir = if (!subFolder.isNullOrBlank()) java.io.File(baseDir, subFolder) else baseDir
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            targetDir
        } catch (_: Exception) {
            val appBaseDir = java.io.File(context.getExternalFilesDir(null), "Nuancier")
            val appDir = if (!subFolder.isNullOrBlank()) java.io.File(appBaseDir, subFolder) else appBaseDir
            if (!appDir.exists()) appDir.mkdirs()
            appDir
        }
    }

    /**
     * Sauvegarde une image Bitmap en format PNG dans le répertoire Documents/Nuancier (ou sous-répertoire comme Aquarelle).
     * Utilise d'abord la création directe dans Documents/Nuancier/Aquarelle avec mkdirs() et MediaScannerConnection
     * pour garantir la création physique immédiate du dossier Documents/Nuancier/Aquarelle.
     */
    fun saveImageToNuancierFolder(
        context: Context,
        fileName: String,
        bitmap: android.graphics.Bitmap,
        subFolder: String? = null
    ): Pair<Boolean, String> {
        val relSubPath = if (!subFolder.isNullOrBlank()) "Nuancier/$subFolder" else "Nuancier"
        val displayLocation = "Documents/$relSubPath"

        // 1. Écriture directe via File dans Environment.DIRECTORY_DOCUMENTS/Nuancier/Aquarelle
        try {
            val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val baseDir = java.io.File(documentsDir, "Nuancier")
            val targetDir = if (!subFolder.isNullOrBlank()) java.io.File(baseDir, subFolder) else baseDir
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val file = java.io.File(targetDir, fileName)
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            // Déclencher le scan média pour que le dossier et le fichier apparaissent immédiatement dans les explorateurs de fichiers
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/png"),
                null
            )
            return Pair(true, "$displayLocation/$fileName")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Sur Android 10+ (API 29+), si File échoue, utiliser MediaStore pour créer dans Documents/Nuancier/<subFolder>
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, displayLocation)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = context.contentResolver.insert(collection, contentValues)

                if (itemUri != null) {
                    context.contentResolver.openOutputStream(itemUri)?.use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        out.flush()
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(itemUri, contentValues, null, null)
                    return Pair(true, "$displayLocation/$fileName")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback dossier externe privé de l'application
        return try {
            val appBaseDir = java.io.File(context.getExternalFilesDir(null), "Nuancier")
            val appDir = if (!subFolder.isNullOrBlank()) java.io.File(appBaseDir, subFolder) else appBaseDir
            if (!appDir.exists()) appDir.mkdirs()
            val file = java.io.File(appDir, fileName)
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            Pair(true, "$displayLocation/$fileName")
        } catch (ex: Exception) {
            ex.printStackTrace()
            Pair(false, ex.localizedMessage ?: "Erreur d'enregistrement d'image")
        }
    }

    /**
     * Sauvegarde directement dans le dossier "Documents/Nuancier" du stockage
     */
    fun saveToNuancierFolder(context: Context, fileName: String, content: String): Pair<Boolean, String> {
        return try {
            val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val nuancierDir = java.io.File(documentsDir, "Nuancier")
            if (!nuancierDir.exists()) {
                nuancierDir.mkdirs()
            }
            val targetFile = java.io.File(nuancierDir, fileName)
            targetFile.writeText(content, Charsets.UTF_8)
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                null,
                null
            )
            Pair(true, "Documents/Nuancier/$fileName")
        } catch (e: Exception) {
            // Fallback sur le dossier privé de l'app si permission publique restreinte
            try {
                val appDir = java.io.File(context.getExternalFilesDir(null), "Nuancier")
                if (!appDir.exists()) appDir.mkdirs()
                val targetFile = java.io.File(appDir, fileName)
                targetFile.writeText(content, Charsets.UTF_8)
                Pair(true, "Documents/Nuancier/$fileName")
            } catch (ex: Exception) {
                ex.printStackTrace()
                Pair(false, ex.localizedMessage ?: "Erreur d'écriture")
            }
        }
    }

    /**
     * Écrit le contenu dans un Uri cible via le ContentResolver.
     */
    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Lit le contenu depuis un Uri source via le ContentResolver.
     */
    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Ouvre directement l'application Google Files (com.google.android.apps.nbu.files)
     * ou le gestionnaire de fichiers Pixel sur le répertoire Documents/Nuancier.
     */
    fun openNuancierFolder(context: Context): Boolean {
        val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val nuancierDir = java.io.File(documentsDir, "Nuancier")
        if (!nuancierDir.exists()) {
            nuancierDir.mkdirs()
        }

        val folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FNuancier")

        // 1. Tenter d'ouvrir directement Google Files (Files by Google) sur le dossier Documents/Nuancier
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "vnd.android.document/directory")
                setPackage("com.google.android.apps.nbu.files")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) { }

        // 2. Lancer l'application Google Files directement (page d'accueil / navigation)
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.files")
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) { }

        // 3. Tenter via Intent Component direct sur Google Files
        try {
            val intent = android.content.Intent().apply {
                component = android.content.ComponentName(
                    "com.google.android.apps.nbu.files",
                    "com.google.android.apps.nbu.files.home.HomeActivity"
                )
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) { }

        // 4. Tenter avec le gestionnaire de fichiers Pixel / Android système (DocumentsUI)
        val systemFilePackages = listOf("com.google.android.documentsui", "com.android.documentsui")
        for (pkg in systemFilePackages) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(folderUri, "vnd.android.document/directory")
                    setPackage(pkg)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) { }

            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            } catch (_: Exception) { }
        }

        // 5. Tentative globale ACTION_VIEW sur le dossier Documents
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "vnd.android.document/directory")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) { }

        return false
    }

    /**
     * Contrat de sélection de document qui pré-positionne automatiquement
     * le sélecteur Android sur le dossier Documents/Nuancier.
     */
    class OpenNuancierDocumentContract : androidx.activity.result.contract.ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): android.content.Intent {
            val intent = super.createIntent(context, input)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FNuancier")
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
            return intent
        }
    }

    /**
     * Contrat de création de document qui pré-positionne automatiquement
     * le sélecteur Android sur le dossier Documents/Nuancier.
     */
    class CreateNuancierDocumentContract(mimeType: String = "application/json") : androidx.activity.result.contract.ActivityResultContracts.CreateDocument(mimeType) {
        override fun createIntent(context: Context, input: String): android.content.Intent {
            val intent = super.createIntent(context, input)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FNuancier")
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
            return intent
        }
    }
}
