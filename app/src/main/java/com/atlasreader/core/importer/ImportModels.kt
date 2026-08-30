package com.atlasreader.core.importer

import android.net.Uri
import com.atlasreader.core.common.AtlasError

/** A single import request, from any source (picker, share, folder, drag-drop). */
data class ImportRequest(
    val uri: Uri,
    val displayName: String? = null,
    val mimeType: String? = null,
    val folderLabel: String? = null,
)

/** Per-file outcome. */
sealed interface ImportOutcome {
    data class Imported(val documentId: Long) : ImportOutcome
    data class Duplicate(val documentId: Long) : ImportOutcome
    data class Unsupported(val displayName: String, val reason: String) : ImportOutcome
    data class Failed(val displayName: String, val error: AtlasError) : ImportOutcome
}

/** Aggregate progress state surfaced to the library UI. */
data class ImportState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val imported: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val currentFile: String? = null,
) {
    val completed: Boolean get() = running && done >= total
}

data class ImportSummary(
    val imported: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val unsupported: Int = 0,
)
