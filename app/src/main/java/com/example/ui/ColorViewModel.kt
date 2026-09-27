package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BaseColor
import com.example.data.ColorRepository
import com.example.data.MixedColor
import com.example.data.RecipeItem
import com.example.utils.ColorUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.audio.SoundSynthesizer

class ColorViewModel(private val repository: ColorRepository) : ViewModel() {

    // Image transmise pour analyse (depuis un agent ou une autre vue)
    private val _pendingAnalysisBitmap = MutableStateFlow<Bitmap?>(null)
    val pendingAnalysisBitmap: StateFlow<Bitmap?> = _pendingAnalysisBitmap.asStateFlow()

    fun sendImageToAnalysis(bitmap: Bitmap) {
        _pendingAnalysisBitmap.value = bitmap
        selectTab(3) // Basculer automatiquement vers l'onglet Analyse
    }

    fun clearPendingAnalysisBitmap() {
        _pendingAnalysisBitmap.value = null
    }

    // Synthétiseur d'ambiance Zen (Goutte d'eau & Gong grave)
    val soundSynthesizer = SoundSynthesizer()

    override fun onCleared() {
        super.onCleared()
        soundSynthesizer.stop()
    }

    // Liste primaire : toutes les couleurs créées/disponibles dans la palette (Onglet Palette)
    val allPrimaryColors: StateFlow<List<BaseColor>> = repository.allBaseColors
        .map { list ->
            list.filter { it.isInPalette }
                .sortedByDescending { ColorUtils.getColorSortScore(it.hexCode, it.name) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Liste de bases : uniquement les couleurs affectées au nuancier (Onglet Mélangeur / Nuancier)
    val baseColors: StateFlow<List<BaseColor>> = repository.allBaseColors
        .map { list ->
            list.filter { it.isInBaseList }
                .sortedByDescending { ColorUtils.getColorSortScore(it.hexCode, it.name) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Liste des mélanges fabriqués (la mosaïque de couleurs) triée de la même façon
    val mixedColors: StateFlow<List<MixedColor>> = repository.allMixedColors
        .map { list ->
            list.sortedByDescending { ColorUtils.getColorSortScore(it.hexCode, it.name) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Couleurs de base cochées pour le mélange
    private val _selectedBaseColorIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedBaseColorIds = _selectedBaseColorIds.asStateFlow()

    // Proportions (nombre de parts) pour chaque couleur de base cochée
    private val _colorProportions = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val colorProportions = _colorProportions.asStateFlow()

    // Nom du mélange actif en cours dans le Nuancier / Mélangeur
    private val _currentMixName = MutableStateFlow("")
    val currentMixName = _currentMixName.asStateFlow()

    fun setMixName(name: String) {
        _currentMixName.value = name
    }

    // Onglet actif (0 = Mélangeur, 1 = Mes Créations, 2 = Nuancier de Base)
    private val _activeTab = MutableStateFlow(0)
    val activeTab = _activeTab.asStateFlow()

    // Mélange sélectionné pour l'affichage de la recette correspondante (recherche inverse)
    private val _selectedMixedColor = MutableStateFlow<MixedColor?>(null)
    val selectedMixedColor = _selectedMixedColor.asStateFlow()

    // Couleur finale calculée du mélange en cours
    val mixedResultHex: StateFlow<String> = combine(
        baseColors,
        _selectedBaseColorIds,
        _colorProportions
    ) { colors, selectedIds, proportions ->
        if (selectedIds.isEmpty()) return@combine "#FFFFFF"
        
        val colorsToBlend = colors.filter { it.id in selectedIds }.map { color ->
            val parts = proportions[color.id] ?: 1
            Pair(color.hexCode, parts.toFloat())
        }
        
        if (colorsToBlend.isEmpty()) "#FFFFFF"
        else ColorUtils.blendColors(colorsToBlend)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "#FFFFFF"
    )

    init {
        // Initialiser la base de données avec des couleurs d'artiste par défaut si elle est vide,
        // ou ajouter automatiquement les nouvelles couleurs de base d'artiste sans effacer les couleurs existantes
        viewModelScope.launch {
            val list = repository.allBaseColors.first()
            if (list.isEmpty()) {
                val defaults = ColorUtils.DEFAULT_ARTIST_COLORS.map { (name, hex) ->
                    val pigment = ColorUtils.getPigmentInfo(name, hex)
                    BaseColor(
                        name = name,
                        hexCode = hex,
                        isDefault = true,
                        pigmentCode = pigment.pigmentCode,
                        opacity = pigment.opacity.name
                    )
                }
                repository.insertBaseColors(defaults)
            } else {
                // Compléter avec les couleurs d'artiste par défaut qui ne sont pas encore présentes
                val missingDefaults = ColorUtils.DEFAULT_ARTIST_COLORS.filter { (name, hex) ->
                    list.none { it.name.equals(name, ignoreCase = true) || it.hexCode.equals(hex, ignoreCase = true) }
                }.map { (name, hex) ->
                    val pigment = ColorUtils.getPigmentInfo(name, hex)
                    BaseColor(
                        name = name,
                        hexCode = hex,
                        isDefault = true,
                        pigmentCode = pigment.pigmentCode,
                        opacity = pigment.opacity.name
                    )
                }
                if (missingDefaults.isNotEmpty()) {
                    repository.insertBaseColors(missingDefaults)
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _activeTab.value = index
    }

    fun selectMixedColor(mixedColor: MixedColor?) {
        _selectedMixedColor.value = mixedColor
    }

    // Coche / décoche une couleur de base
    fun toggleBaseColorSelection(colorId: Int) {
        val currentSelected = _selectedBaseColorIds.value
        if (colorId in currentSelected) {
            _selectedBaseColorIds.value = currentSelected - colorId
            val currentProportions = _colorProportions.value.toMutableMap()
            currentProportions.remove(colorId)
            _colorProportions.value = currentProportions
        } else {
            _selectedBaseColorIds.value = currentSelected + colorId
            val currentProportions = _colorProportions.value.toMutableMap()
            currentProportions[colorId] = 1 // 1 part par défaut
            _colorProportions.value = currentProportions
        }
    }

    // Change la proportion en "parts" (de 1 à 10)
    fun setBaseColorProportion(colorId: Int, parts: Int) {
        if (parts < 1) return
        val currentProportions = _colorProportions.value.toMutableMap()
        currentProportions[colorId] = parts
        _colorProportions.value = currentProportions
    }

    // Réinitialise le mélangeur
    fun clearMixer() {
        _selectedBaseColorIds.value = emptySet()
        _colorProportions.value = emptyMap()
        _currentMixName.value = ""
    }

    // Ajoute une nouvelle couleur de base personnalisée dans la liste primaire (et affectée au nuancier par défaut)
    fun addCustomBaseColor(name: String, hexCode: String) {
        viewModelScope.launch {
            val pigment = ColorUtils.getPigmentInfo(name, hexCode)
            repository.insertBaseColor(
                BaseColor(
                    name = name,
                    hexCode = hexCode,
                    isDefault = false,
                    isInBaseList = true,
                    isInPalette = true,
                    pigmentCode = pigment.pigmentCode,
                    opacity = pigment.opacity.name
                )
            )
        }
    }

    // Ajoute un mélange créé uniquement au nuancier (pas dans la liste primaire de la palette)
    fun addMixtureToNuancier(name: String, hexCode: String) {
        viewModelScope.launch {
            val pigment = ColorUtils.getPigmentInfo(name, hexCode)
            repository.insertBaseColor(
                BaseColor(
                    name = name,
                    hexCode = hexCode,
                    isDefault = false,
                    isInBaseList = true,
                    isInPalette = false,
                    pigmentCode = pigment.pigmentCode,
                    opacity = pigment.opacity.name
                )
            )
        }
    }

    // Ajoute une liste de couleurs extraites d'une image directement au nuancier
    fun addAllExtractedColorsToNuancier(colors: List<Pair<String, String>>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val baseColorsToInsert = colors.map { (name, hex) ->
                val pigment = ColorUtils.getPigmentInfo(name, hex)
                BaseColor(
                    name = name,
                    hexCode = hex,
                    isDefault = false,
                    isInBaseList = true,
                    isInPalette = false,
                    pigmentCode = pigment.pigmentCode,
                    opacity = pigment.opacity.name
                )
            }
            repository.insertBaseColors(baseColorsToInsert)
            onComplete?.invoke()
        }
    }

    // Active spécifiquement un ensemble de couleurs dans le Nuancier
    fun setNuancierActiveColors(activeIds: Set<Int>) {
        viewModelScope.launch {
            val all = repository.allBaseColors.first()
            val updated = all.map { color ->
                if (color.id in activeIds) {
                    color.copy(isInBaseList = true)
                } else if (color.isInPalette) {
                    color.copy(isInBaseList = false)
                } else {
                    color
                }
            }
            repository.insertBaseColors(updated)
        }
    }

    // Configure une couleur et sa proportion dans le mélangeur
    fun setMixerSlotColorAndProportion(colorId: Int, parts: Int) {
        val currentSelected = _selectedBaseColorIds.value
        _selectedBaseColorIds.value = currentSelected + colorId
        val currentProportions = _colorProportions.value.toMutableMap()
        currentProportions[colorId] = parts.coerceIn(1, 10)
        _colorProportions.value = currentProportions
    }

    // Bascule l'affectation d'une couleur primaire vers la liste de bases du nuancier
    fun toggleInBaseList(color: BaseColor) {
        viewModelScope.launch {
            val updated = color.copy(isInBaseList = !color.isInBaseList)
            if (!updated.isInBaseList && color.id in _selectedBaseColorIds.value) {
                toggleBaseColorSelection(color.id)
            }
            repository.insertBaseColor(updated)
        }
    }

    // Retire une couleur de la liste de bases du nuancier
    fun removeFromBaseList(color: BaseColor) {
        viewModelScope.launch {
            if (color.id in _selectedBaseColorIds.value) {
                toggleBaseColorSelection(color.id)
            }
            if (!color.isInPalette) {
                repository.deleteBaseColor(color)
            } else {
                repository.insertBaseColor(color.copy(isInBaseList = false))
            }
        }
    }

    // Supprime en bloc toutes les couleurs ajoutées directement au nuancier (hors palette)
    fun removeAllDirectNuancierColors(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val nonPaletteColors = baseColors.value.filter { !it.isInPalette }
            if (nonPaletteColors.isNotEmpty()) {
                val idsToRemove = nonPaletteColors.map { it.id }.toSet()
                _selectedBaseColorIds.value = _selectedBaseColorIds.value - idsToRemove
                val curProps = _colorProportions.value.toMutableMap()
                idsToRemove.forEach { curProps.remove(it) }
                _colorProportions.value = curProps
                repository.deleteBaseColors(nonPaletteColors)
                onComplete?.invoke(nonPaletteColors.size)
            } else {
                onComplete?.invoke(0)
            }
        }
    }

    // Supprime définitivement une couleur de la liste primaire (et donc de la DB)
    fun deleteBaseColor(color: BaseColor) {
        viewModelScope.launch {
            if (color.id in _selectedBaseColorIds.value) {
                toggleBaseColorSelection(color.id)
            }
            repository.deleteBaseColor(color)
        }
    }

    // Réinitialise toutes les couleurs de base à celles d'artiste par défaut
    fun resetBaseColorsToDefaults() {
        viewModelScope.launch {
            repository.deleteAllBaseColors()
            val defaults = ColorUtils.DEFAULT_ARTIST_COLORS.map { (name, hex) ->
                val pigment = ColorUtils.getPigmentInfo(name, hex)
                BaseColor(
                    name = name,
                    hexCode = hex,
                    isDefault = true,
                    pigmentCode = pigment.pigmentCode,
                    opacity = pigment.opacity.name
                )
            }
            repository.insertBaseColors(defaults)
            clearMixer()
        }
    }

    // Applique une recette trouvée par décomposition directement dans le mélangeur (Onglet 0)
    // S'assure que toutes les composantes de la palette sont cochées (isInBaseList = true) pour être affectées au Nuancier
    fun applyRecipeToMixer(
        recipeItems: List<ColorUtils.RecipeComponent>,
        allPrimaryColors: List<BaseColor>,
        mixName: String = "",
        onComplete: ((Int) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val selectedIds = mutableSetOf<Int>()
            val proportions = mutableMapOf<Int, Int>()
            var newlyCheckedCount = 0

            val currentBaseColors = baseColors.value
            val pool = (allPrimaryColors + currentBaseColors).distinctBy { it.id }

            for (item in recipeItems) {
                val matchingColor = pool.find {
                    it.hexCode.equals(item.hexCode, ignoreCase = true) || it.name.equals(item.colorName, ignoreCase = true)
                }
                if (matchingColor != null) {
                    if (!matchingColor.isInBaseList) {
                        // S'assurer qu'elle est cochée dans la palette pour être présente dans le Nuancier
                        val updated = matchingColor.copy(isInBaseList = true)
                        repository.insertBaseColor(updated)
                        newlyCheckedCount++
                    }
                    selectedIds.add(matchingColor.id)
                    proportions[matchingColor.id] = item.parts
                } else {
                    val pigment = ColorUtils.getPigmentInfo(item.colorName, item.hexCode)
                    val newColor = BaseColor(
                        name = item.colorName,
                        hexCode = item.hexCode,
                        isDefault = false,
                        isInBaseList = true,
                        isInPalette = true,
                        pigmentCode = pigment.pigmentCode,
                        opacity = pigment.opacity.name
                    )
                    val newId = repository.insertBaseColor(newColor).toInt()
                    selectedIds.add(newId)
                    proportions[newId] = item.parts
                    newlyCheckedCount++
                }
            }

            _selectedBaseColorIds.value = selectedIds
            _colorProportions.value = proportions
            if (mixName.isNotBlank()) {
                _currentMixName.value = mixName
            }
            _activeTab.value = 0 // Aller à l'onglet Nuancier
            onComplete?.invoke(newlyCheckedCount)
        }
    }

    // Affecte un mélange enregistré et l'ensemble de ses composantes au Nuancier / Mélangeur
    // (exactement comme lors d'un transfert depuis l'onglet Analyse)
    fun applyMixtureToNuancier(
        mixed: MixedColor,
        allPrimaryColors: List<BaseColor>,
        onComplete: ((Int) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val recipeItems = mixed.getRecipeItems()
            val selectedIds = mutableSetOf<Int>()
            val proportions = mutableMapOf<Int, Int>()
            var newlyCheckedCount = 0

            val currentBaseColors = baseColors.value
            val pool = (allPrimaryColors + currentBaseColors).distinctBy { it.id }

            if (recipeItems.isNotEmpty()) {
                for (item in recipeItems) {
                    val matchingColor = pool.find {
                        it.id == item.colorId ||
                        it.hexCode.equals(item.hexCode, ignoreCase = true) ||
                        it.name.equals(item.name, ignoreCase = true)
                    }
                    if (matchingColor != null) {
                        if (!matchingColor.isInBaseList) {
                            val updated = matchingColor.copy(isInBaseList = true)
                            repository.insertBaseColor(updated)
                            newlyCheckedCount++
                        }
                        selectedIds.add(matchingColor.id)
                        proportions[matchingColor.id] = item.parts
                    } else {
                        val pigment = ColorUtils.getPigmentInfo(item.name, item.hexCode)
                        val newColor = BaseColor(
                            name = item.name,
                            hexCode = item.hexCode,
                            isDefault = false,
                            isInBaseList = true,
                            isInPalette = true,
                            pigmentCode = pigment.pigmentCode,
                            opacity = pigment.opacity.name
                        )
                        val newId = repository.insertBaseColor(newColor).toInt()
                        selectedIds.add(newId)
                        proportions[newId] = item.parts
                        newlyCheckedCount++
                    }
                }
            } else {
                val matchingColor = pool.find {
                    it.hexCode.equals(mixed.hexCode, ignoreCase = true) ||
                    it.name.equals(mixed.name, ignoreCase = true)
                }
                if (matchingColor != null) {
                    if (!matchingColor.isInBaseList) {
                        repository.insertBaseColor(matchingColor.copy(isInBaseList = true))
                        newlyCheckedCount++
                    }
                    selectedIds.add(matchingColor.id)
                    proportions[matchingColor.id] = 1
                } else {
                    val pigment = ColorUtils.getPigmentInfo(mixed.name, mixed.hexCode)
                    val newColor = BaseColor(
                        name = mixed.name,
                        hexCode = mixed.hexCode,
                        isDefault = false,
                        isInBaseList = true,
                        isInPalette = true,
                        pigmentCode = pigment.pigmentCode,
                        opacity = pigment.opacity.name
                    )
                    val newId = repository.insertBaseColor(newColor).toInt()
                    selectedIds.add(newId)
                    proportions[newId] = 1
                    newlyCheckedCount++
                }
            }

            _selectedBaseColorIds.value = selectedIds
            _colorProportions.value = proportions
            _currentMixName.value = mixed.name
            _activeTab.value = 0 // Aller à l'onglet Nuancier / Mélangeur
            _selectedMixedColor.value = null // Fermer le panneau de détail
            onComplete?.invoke(newlyCheckedCount)
        }
    }

    // Enregistre le mélange en cours sous un nom personnalisé
    fun saveCurrentMixture(name: String) {
        saveAsSecondaryColor(name) {}
    }

    // Enregistre le mélange en cours dans les couleurs secondaires
    fun saveAsSecondaryColor(name: String, onCompleted: () -> Unit) {
        viewModelScope.launch {
            val selectedIds = _selectedBaseColorIds.value
            val proportions = _colorProportions.value
            val colors = baseColors.value
            
            val recipeItems = colors.filter { it.id in selectedIds }.map { color ->
                RecipeItem(
                    colorId = color.id,
                    name = color.name,
                    hexCode = color.hexCode,
                    parts = proportions[color.id] ?: 1
                )
            }
            
            if (recipeItems.isEmpty()) return@launch
            
            val finalHex = mixedResultHex.value
            val recipeString = MixedColor.createRecipeString(recipeItems)
            
            val mixedColor = MixedColor(
                name = name.trim().ifEmpty { "Mélange N°" + (mixedColors.value.size + 1) },
                hexCode = finalHex,
                recipeString = recipeString
            )
            repository.insertMixedColor(mixedColor)
            _currentMixName.value = ""
            onCompleted()
        }
    }

    // Enregistre le mélange en cours dans les couleurs de base
    fun saveAsBaseColor(name: String, onCompleted: () -> Unit) {
        viewModelScope.launch {
            val finalHex = mixedResultHex.value
            val finalName = name.trim().ifEmpty { "Teinte Base N°" + (baseColors.value.size + 1) }
            repository.insertBaseColor(BaseColor(name = finalName, hexCode = finalHex, isDefault = false, isInBaseList = true, isInPalette = false))
            onCompleted()
        }
    }

    // Enregistre directement un mélange avec sa recette explicite (utilisé par l'Agent IA ou des presets)
    fun createAndSaveMixtureDirectly(
        name: String,
        items: List<RecipeItem>,
        calculatedHex: String? = null
    ) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch

            val finalHex = calculatedHex ?: run {
                val hexWithWeights = items.map { it.hexCode to it.parts.toFloat() }
                ColorUtils.blendColors(hexWithWeights)
            }
            val recipeString = MixedColor.createRecipeString(items)

            val mixedColor = MixedColor(
                name = name.trim().ifEmpty { "Mélange N°" + (mixedColors.value.size + 1) },
                hexCode = finalHex,
                recipeString = recipeString,
                createdAt = System.currentTimeMillis()
            )
            repository.insertMixedColor(mixedColor)
        }
    }

    // Renomme un mélange existant
    fun renameMixedColor(mixedColor: MixedColor, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val updated = mixedColor.copy(name = trimmed)
            repository.insertMixedColor(updated)
            if (_selectedMixedColor.value?.id == mixedColor.id) {
                _selectedMixedColor.value = updated
            }
        }
    }

    // Supprime un mélange de la mosaïque
    fun deleteMixedColor(mixedColor: MixedColor) {
        viewModelScope.launch {
            if (_selectedMixedColor.value?.id == mixedColor.id) {
                _selectedMixedColor.value = null
            }
            repository.deleteMixedColor(mixedColor)
        }
    }

    // Supprime une liste de mélanges de la mosaïque (suppression en bloc)
    fun deleteMixedColors(mixedColorList: List<MixedColor>) {
        viewModelScope.launch {
            val currentSelectedId = _selectedMixedColor.value?.id
            mixedColorList.forEach { mixed ->
                if (currentSelectedId == mixed.id) {
                    _selectedMixedColor.value = null
                }
                repository.deleteMixedColor(mixed)
            }
        }
    }

    // Réintègre/Rapatrie une liste de mélanges (depuis une importation JSON)
    fun importMixtures(importedList: List<MixedColor>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            var count = 0
            val existing = mixedColors.value
            importedList.forEach { toImport ->
                // Évite les doublons stricts (même nom et même hex) ou les rajoute
                val alreadyExists = existing.any { it.name.equals(toImport.name, ignoreCase = true) && it.hexCode.equals(toImport.hexCode, ignoreCase = true) }
                if (!alreadyExists) {
                    repository.insertMixedColor(
                        MixedColor(
                            name = toImport.name,
                            hexCode = toImport.hexCode,
                            recipeString = toImport.recipeString,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    count++
                }
            }
            onComplete(count)
        }
    }

    // Réintègre/Rapatrie une liste de couleurs dans le Nuancier de base (depuis une importation JSON)
    fun importNuancierColors(importedList: List<BaseColor>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            var count = 0
            val all = repository.allBaseColors.first()
            val toInsertOrUpdate = mutableListOf<BaseColor>()

            importedList.forEach { toImport ->
                val existing = all.find {
                    it.name.equals(toImport.name, ignoreCase = true) ||
                    it.hexCode.equals(toImport.hexCode, ignoreCase = true)
                }
                if (existing != null) {
                    if (!existing.isInBaseList) {
                        toInsertOrUpdate.add(existing.copy(isInBaseList = true))
                        count++
                    }
                } else {
                    val pigment = ColorUtils.getPigmentInfo(toImport.name, toImport.hexCode)
                    toInsertOrUpdate.add(
                        BaseColor(
                            name = toImport.name,
                            hexCode = toImport.hexCode,
                            isDefault = false,
                            isInBaseList = true,
                            isInPalette = true,
                            pigmentCode = if (toImport.pigmentCode.isNotEmpty()) toImport.pigmentCode else pigment.pigmentCode,
                            opacity = if (toImport.opacity.isNotEmpty()) toImport.opacity else pigment.opacity.name
                        )
                    )
                    count++
                }
            }
            if (toInsertOrUpdate.isNotEmpty()) {
                repository.insertBaseColors(toInsertOrUpdate)
            }
            onComplete(count)
        }
    }

    // Charge un mélange enregistré dans le mélangeur actif pour le modifier/retravailler
    fun loadMixtureIntoMixer(mixedColor: MixedColor) {
        val recipeItems = mixedColor.getRecipeItems()
        val newSelectedIds = recipeItems.map { it.colorId }.toSet()
        val newProportions = recipeItems.associate { it.colorId to it.parts }
        
        _selectedBaseColorIds.value = newSelectedIds
        _colorProportions.value = newProportions
        _activeTab.value = 0 // Retourner à l'onglet Mélangeur
    }
}

class ColorViewModelFactory(private val repository: ColorRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ColorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ColorViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
