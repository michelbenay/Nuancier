package com.example.data

import kotlinx.coroutines.flow.Flow

class ColorRepository(private val colorDao: ColorDao) {
    val allBaseColors: Flow<List<BaseColor>> = colorDao.getAllBaseColors()
    val allMixedColors: Flow<List<MixedColor>> = colorDao.getAllMixedColors()

    suspend fun insertBaseColor(color: BaseColor): Long {
        return colorDao.insertBaseColor(color)
    }

    suspend fun insertBaseColors(colors: List<BaseColor>) {
        colorDao.insertBaseColors(colors)
    }

    suspend fun deleteBaseColor(color: BaseColor) {
        colorDao.deleteBaseColor(color)
    }

    suspend fun deleteBaseColors(colors: List<BaseColor>) {
        colorDao.deleteBaseColors(colors)
    }

    suspend fun deleteUserBaseColors() {
        colorDao.deleteUserBaseColors()
    }

    suspend fun deleteAllBaseColors() {
        colorDao.deleteAllBaseColors()
    }

    suspend fun insertMixedColor(color: MixedColor): Long {
        return colorDao.insertMixedColor(color)
    }

    suspend fun deleteMixedColor(color: MixedColor) {
        colorDao.deleteMixedColor(color)
    }

    suspend fun deleteAllMixedColors() {
        colorDao.deleteAllMixedColors()
    }
}
