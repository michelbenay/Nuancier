package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "base_colors")
data class BaseColor(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val hexCode: String, // format: #RRGGBB
    val isDefault: Boolean = false,
    val isInBaseList: Boolean = true,
    val isInPalette: Boolean = true,
    val pigmentCode: String = "",
    val opacity: String = "SEMI_OPAQUE"
)

