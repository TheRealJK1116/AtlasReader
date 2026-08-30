package com.atlasreader.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.atlasreader.core.datastore.ThemeMode
import com.atlasreader.data.repository.SettingsRepository
import com.atlasreader.worker.CleanupWorker
import com.atlasreader.worker.ReindexWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val dynamicColor: StateFlow<Boolean> =
        settings.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val fontSizeSp: StateFlow<Int> =
        settings.readerFontSizeSp.stateIn(viewModelScope, SharingStarted.Eagerly, 18)
    val fontFamily: StateFlow<String> =
        settings.readerFontFamily.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val lineSpacing: StateFlow<Float> =
        settings.readerLineSpacing.stateIn(viewModelScope, SharingStarted.Eagerly, 1.4f)
    val pageWidthPercent: StateFlow<Int> =
        settings.readerPageWidthPercent.stateIn(viewModelScope, SharingStarted.Eagerly, 92)
    val marginPercent: StateFlow<Int> =
        settings.readerMarginPercent.stateIn(viewModelScope, SharingStarted.Eagerly, 6)
    val brightnessPercent: StateFlow<Int> =
        settings.readerBrightnessPercent.stateIn(viewModelScope, SharingStarted.Eagerly, -1)
    val fullscreen: StateFlow<Boolean> =
        settings.readerFullscreen.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val orientationLock: StateFlow<Int> =
        settings.readerOrientationLock.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val importAutoOpen: StateFlow<Boolean> =
        settings.importAutoOpen.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
    fun setFontSize(value: Int) = viewModelScope.launch { settings.setFontSizeSp(value) }
    fun setFontFamily(value: String) = viewModelScope.launch { settings.setFontFamily(value) }
    fun setLineSpacing(value: Float) = viewModelScope.launch { settings.setLineSpacing(value) }
    fun setPageWidth(value: Int) = viewModelScope.launch { settings.setPageWidthPercent(value) }
    fun setMargin(value: Int) = viewModelScope.launch { settings.setMarginPercent(value) }
    fun setBrightness(value: Int) = viewModelScope.launch { settings.setBrightnessPercent(value) }
    fun setFullscreen(value: Boolean) = viewModelScope.launch { settings.setFullscreen(value) }
    fun setOrientation(value: Int) = viewModelScope.launch { settings.setOrientationLock(value) }
    fun setImportAutoOpen(value: Boolean) = viewModelScope.launch { settings.setImportAutoOpen(value) }

    fun scheduleReindex(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReindexWorker>().build()
        )
    }

    fun scheduleCleanup(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<CleanupWorker>().build()
        )
    }

    /** Serialise settings + library preferences to JSON (for SAF export). */
    fun exportJson(): String {
        val json = JSONObject()
        json.put("format", "atlas-reader-settings")
        json.put("version", 1)
        json.put("themeMode", themeMode.value.name)
        json.put("dynamicColor", dynamicColor.value)
        json.put("fontSizeSp", fontSizeSp.value)
        json.put("fontFamily", fontFamily.value)
        json.put("lineSpacing", lineSpacing.value.toDouble())
        json.put("pageWidthPercent", pageWidthPercent.value)
        json.put("marginPercent", marginPercent.value)
        json.put("brightnessPercent", brightnessPercent.value)
        json.put("fullscreen", fullscreen.value)
        json.put("orientationLock", orientationLock.value)
        json.put("importAutoOpen", importAutoOpen.value)
        return json.toString()
    }

    /** Apply settings from an exported JSON file. Returns error message or null. */
    suspend fun importJson(uri: Uri, context: Context): String? = try {
        val json = JSONObject(
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return "Could not read file"
        )
        if (json.optString("format") != "atlas-reader-settings") return "Not an Atlas Reader settings file"
        runCatching { ThemeMode.valueOf(json.getString("themeMode")) }.getOrNull()?.let { settings.setThemeMode(it) }
        settings.setDynamicColor(json.optBoolean("dynamicColor", true))
        settings.setFontSizeSp(json.optInt("fontSizeSp", 18))
        settings.setFontFamily(json.optString("fontFamily", "system"))
        settings.setLineSpacing(json.optDouble("lineSpacing", 1.4).toFloat())
        settings.setPageWidthPercent(json.optInt("pageWidthPercent", 92))
        settings.setMarginPercent(json.optInt("marginPercent", 6))
        settings.setBrightnessPercent(json.optInt("brightnessPercent", -1))
        settings.setFullscreen(json.optBoolean("fullscreen", false))
        settings.setOrientationLock(json.optInt("orientationLock", 0))
        settings.setImportAutoOpen(json.optBoolean("importAutoOpen", true))
        null
    } catch (e: Exception) {
        e.message ?: "Import failed"
    }
}
