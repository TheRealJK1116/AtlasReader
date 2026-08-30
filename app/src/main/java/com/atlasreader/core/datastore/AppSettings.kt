package com.atlasreader.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/**
 * Hot user preferences persisted in DataStore (immediate UI reads, no DB
 * dependency). Theme, reader defaults, and import behaviour.
 */
@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store: DataStore<Preferences> = context.applicationContext.dataStore

    // ------------------------------------------------------------------ theme

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val dynamicColor: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[KEY_DYNAMIC_COLOR] = enabled }

    suspend fun currentThemeMode(): ThemeMode = themeMode.first()

    // -------------------------------------------------------------- reader UI

    val readerFontSizeSp: Flow<Int> = store.data.map { it[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE_SP }

    val readerFontFamily: Flow<String> = store.data.map { it[KEY_FONT_FAMILY] ?: "system" }

    val readerLineSpacing: Flow<Float> = store.data.map { it[KEY_LINE_SPACING] ?: 1.4f }

    val readerPageWidthPercent: Flow<Int> = store.data.map { it[KEY_PAGE_WIDTH] ?: 92 }

    val readerMarginPercent: Flow<Int> = store.data.map { it[KEY_MARGIN] ?: 6 }

    val readerBrightnessPercent: Flow<Int> = store.data.map { it[KEY_BRIGHTNESS] ?: -1 }

    val readerFullscreen: Flow<Boolean> = store.data.map { it[KEY_FULLSCREEN] ?: false }

    val readerOrientationLock: Flow<Int> = store.data.map { it[KEY_ORIENTATION] ?: 0 }

    val readerTheme: Flow<String> = store.data.map { it[KEY_READER_THEME] ?: "AUTO" }

    suspend fun setReaderFontSizeSp(value: Int) = store.edit { it[KEY_FONT_SIZE] = value.coerceIn(10, 40) }

    suspend fun setReaderFontFamily(value: String) = store.edit { it[KEY_FONT_FAMILY] = value }

    suspend fun setReaderLineSpacing(value: Float) = store.edit { it[KEY_LINE_SPACING] = value.coerceIn(1.0f, 2.5f) }

    suspend fun setReaderPageWidthPercent(value: Int) = store.edit { it[KEY_PAGE_WIDTH] = value.coerceIn(55, 100) }

    suspend fun setReaderMarginPercent(value: Int) = store.edit { it[KEY_MARGIN] = value.coerceIn(0, 15) }

    suspend fun setReaderBrightnessPercent(value: Int) = store.edit { it[KEY_BRIGHTNESS] = value.coerceIn(-1, 100) }

    suspend fun setReaderFullscreen(value: Boolean) = store.edit { it[KEY_FULLSCREEN] = value }

    suspend fun setReaderOrientationLock(value: Int) = store.edit { it[KEY_ORIENTATION] = value }

    suspend fun setReaderTheme(value: String) = store.edit { it[KEY_READER_THEME] = value }

    // ---------------------------------------------------------------- import

    val importAutoOpen: Flow<Boolean> = store.data.map { it[KEY_AUTO_OPEN] ?: true }

    suspend fun setImportAutoOpen(value: Boolean) = store.edit { it[KEY_AUTO_OPEN] = value }

    // ------------------------------------------------------------- misc flags

    val libraryHintDismissed: Flow<Boolean> = store.data.map { it[KEY_HINT_DISMISSED] ?: false }

    suspend fun dismissLibraryHint() = store.edit { it[KEY_HINT_DISMISSED] = true }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_FONT_SIZE = intPreferencesKey("reader_font_size_sp")
        val KEY_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val KEY_LINE_SPACING = floatPreferencesKey("reader_line_spacing")
        val KEY_PAGE_WIDTH = intPreferencesKey("reader_page_width")
        val KEY_MARGIN = intPreferencesKey("reader_margin")
        val KEY_BRIGHTNESS = intPreferencesKey("reader_brightness")
        val KEY_FULLSCREEN = booleanPreferencesKey("reader_fullscreen")
        val KEY_ORIENTATION = intPreferencesKey("reader_orientation")
        val KEY_READER_THEME = stringPreferencesKey("reader_theme")
        val KEY_AUTO_OPEN = booleanPreferencesKey("import_auto_open")
        val KEY_HINT_DISMISSED = booleanPreferencesKey("library_hint_dismissed")
        const val DEFAULT_FONT_SIZE_SP = 18
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "atlas_settings")
