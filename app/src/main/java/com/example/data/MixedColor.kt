package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class RecipeItem(
    val colorId: Int,
    val name: String,
    val hexCode: String,
    val parts: Int
) {
    fun toSerializedString(): String {
        val encodedName = name.replace(":", "%3A").replace("|", "%7C")
        val encodedHex = hexCode.replace(":", "%3A").replace("|", "%7C")
        return "$colorId:$encodedName:$encodedHex:$parts"
    }

    companion object {
        fun fromSerializedString(str: String): RecipeItem? {
            val parts = str.split(":")
            if (parts.size < 4) return null
            val id = parts[0].toIntOrNull() ?: return null
            val name = parts[1].replace("%3A", ":").replace("%7C", "|")
            val hex = parts[2].replace("%3A", ":").replace("%7C", "|")
            val p = parts[3].toIntOrNull() ?: return null
            return RecipeItem(id, name, hex, p)
        }
    }
}

@Entity(tableName = "mixed_colors")
data class MixedColor(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val hexCode: String, // Le code hexadécimal de la couleur obtenue
    val recipeString: String, // Liste sérialisée de RecipeItem séparée par "|"
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getRecipeItems(): List<RecipeItem> {
        if (recipeString.isEmpty()) return emptyList()
        return recipeString.split("|").mapNotNull { RecipeItem.fromSerializedString(it) }
    }

    companion object {
        fun createRecipeString(items: List<RecipeItem>): String {
            return items.joinToString("|") { it.toSerializedString() }
        }
    }
}
