package com.atlasreader.core.util

/** File-name helpers shared by the importer and the engine registry. */
object FilenameUtils {
    private val EXTENSION = Regex("(?i)\\.([a-z0-9]{1,10})$")

    fun extension(name: String): String? =
        EXTENSION.find(name)?.groupValues?.get(1)?.lowercase()

    fun stripExtension(name: String): String =
        EXTENSION.replace(name, "")

    fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
}
