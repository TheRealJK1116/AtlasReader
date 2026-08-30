package com.atlasreader.core.engine

import android.net.Uri
import com.atlasreader.core.util.FilenameUtils
import java.io.File

/**
 * Where a document lives. After import every library document has a local copy
 * ([localPath]), so engines can parse with plain file I/O (fast, unit-testable).
 * The [uri] is kept for documents opened ad-hoc (e.g. share intents before the
 * importer has finished, or future cloud sources).
 */
data class DocumentSource(
    val uri: Uri? = null,
    val displayName: String,
    val localPath: String? = null,
) {
    val file: File? get() = localPath?.let(::File)
    val extension: String? get() = FilenameUtils.extension(displayName)
}
