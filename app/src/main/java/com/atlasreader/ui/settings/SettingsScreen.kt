package com.atlasreader.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atlasreader.core.datastore.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSizeSp.collectAsStateWithLifecycle()
    val lineSpacing by viewModel.lineSpacing.collectAsStateWithLifecycle()
    val pageWidth by viewModel.pageWidthPercent.collectAsStateWithLifecycle()
    val brightness by viewModel.brightnessPercent.collectAsStateWithLifecycle()
    val fullscreen by viewModel.fullscreen.collectAsStateWithLifecycle()
    val orientation by viewModel.orientationLock.collectAsStateWithLifecycle()
    val importAutoOpen by viewModel.importAutoOpen.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.exportJson().toByteArray()) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val error = viewModel.importJson(uri, context)
                snackbar.showSnackbar(error ?: "Settings imported")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Appearance")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label()) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            ListItem(
                headlineContent = { Text("Dynamic colour") },
                supportingContent = { Text("Material You — follows your wallpaper (Android 12+)") },
                leadingContent = { Icon(Icons.Outlined.Palette, null) },
                trailingContent = {
                    Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
                },
            )

            HorizontalDivider()
            SectionHeader("Reader defaults")

            ListItem(
                headlineContent = { Text("Font size — ${fontSize}sp") },
                leadingContent = { Icon(Icons.Outlined.FormatSize, null) },
            )
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { viewModel.setFontSize(it.toInt()) },
                valueRange = 12f..32f,
                steps = 19,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(headlineContent = { Text("Line spacing — ${"%.2f".format(lineSpacing)}") })
            Slider(
                value = lineSpacing,
                onValueChange = { viewModel.setLineSpacing(it) },
                valueRange = 1.0f..2.2f,
                steps = 11,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(headlineContent = { Text("Page width — $pageWidth%") })
            Slider(
                value = pageWidth.toFloat(),
                onValueChange = { viewModel.setPageWidth(it.toInt()) },
                valueRange = 60f..100f,
                steps = 7,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(
                headlineContent = {
                    Text(if (brightness < 0) "Brightness — follow system" else "Brightness — $brightness%")
                },
                supportingContent = { Text("Set a fixed reading brightness; system uses your display setting") },
            )
            Slider(
                value = if (brightness < 0) 0f else brightness.toFloat(),
                onValueChange = { viewModel.setBrightness(it.toInt()) },
                valueRange = -1f..100f,
                steps = 100,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(
                headlineContent = { Text("Open fullscreen") },
                supportingContent = { Text("Immersive mode while reading") },
                leadingContent = { Icon(Icons.Outlined.DarkMode, null) },
                trailingContent = { Switch(checked = fullscreen, onCheckedChange = { viewModel.setFullscreen(it) }) },
            )
            ListItem(
                headlineContent = { Text("Orientation lock") },
                leadingContent = { Icon(Icons.Outlined.LightMode, null) },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OrientationChip("Auto", orientation == 0) { viewModel.setOrientation(0) }
                Spacer(Modifier.width(8.dp))
                OrientationChip("Portrait", orientation == 1) { viewModel.setOrientation(1) }
                Spacer(Modifier.width(8.dp))
                OrientationChip("Landscape", orientation == 2) { viewModel.setOrientation(2) }
            }

            HorizontalDivider()
            SectionHeader("Import")
            ListItem(
                headlineContent = { Text("Open imported documents automatically") },
                leadingContent = { Icon(Icons.Outlined.FileDownload, null) },
                trailingContent = {
                    Switch(checked = importAutoOpen, onCheckedChange = { viewModel.setImportAutoOpen(it) })
                },
            )

            HorizontalDivider()
            SectionHeader("Data management")
            ListItem(
                headlineContent = { Text("Rebuild search index") },
                supportingContent = { Text("Re-parse every document and rebuild the full-text index (slow for large libraries)") },
                trailingContent = {
                    Button(onClick = { viewModel.scheduleReindex(context) }) { Text("Reindex") }
                },
            )
            ListItem(
                headlineContent = { Text("Clean up") },
                supportingContent = { Text("Prune search history, orphaned covers and stale temp files") },
                trailingContent = {
                    Button(onClick = { viewModel.scheduleCleanup(context) }) { Text("Clean") }
                },
            )

            HorizontalDivider()
            SectionHeader("Backup & restore")
            ListItem(
                headlineContent = { Text("Export settings") },
                supportingContent = { Text("Save theme and reader preferences to a JSON file") },
                leadingContent = { Icon(Icons.Outlined.FileUpload, null) },
                modifier = Modifier.combinedClickable(
                    onClick = { exportLauncher.launch("atlas-settings.json") },
                    onLongClick = {},
                ),
            )
            ListItem(
                headlineContent = { Text("Import settings") },
                supportingContent = { Text("Restore preferences from an exported JSON file") },
                leadingContent = { Icon(Icons.Outlined.FileDownload, null) },
                modifier = Modifier.combinedClickable(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    onLongClick = {},
                ),
            )

            HorizontalDivider()
            SectionHeader("About")
            ListItem(
                headlineContent = { Text("Atlas Reader") },
                supportingContent = { Text("Version 1.0.0 · Local-first e-reader · EPUB, PDF, Markdown, TXT, RTF, HTML") },
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun OrientationChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED"
}
