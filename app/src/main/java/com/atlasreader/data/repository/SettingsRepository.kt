package com.atlasreader.data.repository

import com.atlasreader.core.database.LibrarySort
import com.atlasreader.core.database.dao.UserPreferencesDao
import com.atlasreader.core.database.entity.UserPreferenceEntity
import com.atlasreader.core.datastore.AppSettings
import com.atlasreader.core.datastore.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings façade: hot UI settings (DataStore) + persisted library UI state
 * (user_preferences table, so it survives process death and joins backups).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val appSettings: AppSettings,
    private val preferencesDao: UserPreferencesDao,
) {

    // ------------------------------------------------------------- hot settings

    val themeMode: Flow<ThemeMode> = appSettings.themeMode
    val dynamicColor: Flow<Boolean> = appSettings.dynamicColor
    val readerFontSizeSp: Flow<Int> = appSettings.readerFontSizeSp
    val readerFontFamily: Flow<String> = appSettings.readerFontFamily
    val readerLineSpacing: Flow<Float> = appSettings.readerLineSpacing
    val readerPageWidthPercent: Flow<Int> = appSettings.readerPageWidthPercent
    val readerMarginPercent: Flow<Int> = appSettings.readerMarginPercent
    val readerBrightnessPercent: Flow<Int> = appSettings.readerBrightnessPercent
    val readerFullscreen: Flow<Boolean> = appSettings.readerFullscreen
    val readerOrientationLock: Flow<Int> = appSettings.readerOrientationLock
    val readerTheme: Flow<String> = appSettings.readerTheme
    val importAutoOpen: Flow<Boolean> = appSettings.importAutoOpen

    suspend fun setThemeMode(mode: ThemeMode) = appSettings.setThemeMode(mode)
    suspend fun setDynamicColor(enabled: Boolean) = appSettings.setDynamicColor(enabled)
    suspend fun setFontSizeSp(value: Int) = appSettings.setReaderFontSizeSp(value)
    suspend fun setFontFamily(value: String) = appSettings.setReaderFontFamily(value)
    suspend fun setLineSpacing(value: Float) = appSettings.setReaderLineSpacing(value)
    suspend fun setPageWidthPercent(value: Int) = appSettings.setReaderPageWidthPercent(value)
    suspend fun setMarginPercent(value: Int) = appSettings.setReaderMarginPercent(value)
    suspend fun setBrightnessPercent(value: Int) = appSettings.setReaderBrightnessPercent(value)
    suspend fun setFullscreen(value: Boolean) = appSettings.setReaderFullscreen(value)
    suspend fun setOrientationLock(value: Int) = appSettings.setReaderOrientationLock(value)
    suspend fun setReaderTheme(value: String) = appSettings.setReaderTheme(value)
    suspend fun setImportAutoOpen(value: Boolean) = appSettings.setImportAutoOpen(value)

    // ------------------------------------------------- library UI persistence

    suspend fun savedLibrarySort(): LibrarySort =
        runCatching { LibrarySort.valueOf(preferencesDao.get(KEY_LIBRARY_SORT) ?: "" ) }
            .getOrDefault(LibrarySort.DATE_ADDED)

    suspend fun saveLibrarySort(sort: LibrarySort) =
        preferencesDao.put(UserPreferenceEntity(KEY_LIBRARY_SORT, sort.name))

    suspend fun savedViewMode(): String =
        preferencesDao.get(KEY_LIBRARY_VIEW_MODE) ?: "GRID"

    suspend fun saveViewMode(mode: String) =
        preferencesDao.put(UserPreferenceEntity(KEY_LIBRARY_VIEW_MODE, mode))

    suspend fun currentThemeMode(): ThemeMode = themeMode.first()

    private companion object {
        const val KEY_LIBRARY_SORT = "library_sort"
        const val KEY_LIBRARY_VIEW_MODE = "library_view_mode"
    }
}
