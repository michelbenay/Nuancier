package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorDao {
    // ---- BASE COLORS ----
    @Query("SELECT * FROM base_colors ORDER BY name ASC")
    fun getAllBaseColors(): Flow<List<BaseColor>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseColor(color: BaseColor): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseColors(colors: List<BaseColor>)

    @Delete
    suspend fun deleteBaseColor(color: BaseColor)

    @Delete
    suspend fun deleteBaseColors(colors: List<BaseColor>)

    @Query("DELETE FROM base_colors WHERE isDefault = 0")
    suspend fun deleteUserBaseColors()

    @Query("DELETE FROM base_colors")
    suspend fun deleteAllBaseColors()

    // ---- MIXED COLORS ----
    @Query("SELECT * FROM mixed_colors ORDER BY createdAt DESC")
    fun getAllMixedColors(): Flow<List<MixedColor>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMixedColor(color: MixedColor): Long

    @Delete
    suspend fun deleteMixedColor(color: MixedColor)

    @Query("DELETE FROM mixed_colors")
    suspend fun deleteAllMixedColors()
}
