package com.atlasreader.data.repository

import com.atlasreader.core.database.entity.DocumentEntity
import com.atlasreader.core.database.entity.DocumentRow
import com.atlasreader.core.database.entity.HighlightEntity
import com.atlasreader.core.database.entity.BookmarkEntity
import com.atlasreader.core.database.entity.NoteEntity
import com.atlasreader.core.database.entity.ReadingProgressEntity
import com.atlasreader.core.engine.DocumentFormat
import com.atlasreader.domain.model.Bookmark
import com.atlasreader.domain.model.DocumentSummary
import com.atlasreader.domain.model.Highlight
import com.atlasreader.domain.model.ReaderNote
import com.atlasreader.domain.model.ReadingPosition

internal fun DocumentRow.toSummary(): DocumentSummary = DocumentSummary(
    id = id,
    contentHash = contentHash,
    fileName = fileName,
    displayName = displayName,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
    format = runCatching { DocumentFormat.valueOf(format) }.getOrDefault(DocumentFormat.TXT),
    title = title ?: displayName,
    author = author,
    description = description,
    addedAtMs = addedAtMs,
    openedAtMs = openedAtMs,
    favorite = favorite,
    coverPath = coverPath,
    progressPercent = progressPercent ?: 0f,
    progressUpdatedAtMs = progressUpdatedAtMs,
)

internal fun DocumentEntity.toSummary(): DocumentSummary = DocumentSummary(
    id = id,
    contentHash = contentHash,
    fileName = fileName,
    displayName = displayName,
    filePath = filePath,
    fileSizeBytes = fileSizeBytes,
    format = runCatching { DocumentFormat.valueOf(format) }.getOrDefault(DocumentFormat.TXT),
    title = title ?: displayName,
    author = author,
    description = description,
    addedAtMs = addedAtMs,
    openedAtMs = openedAtMs,
    favorite = favorite,
    coverPath = null,
    progressPercent = 0f,
    progressUpdatedAtMs = null,
)

internal fun ReadingProgressEntity.toPosition(): ReadingPosition = ReadingPosition(
    documentId = documentId,
    resourceToken = resourceToken,
    charOffset = charOffset,
    pageIndex = pageIndex,
    scrollFraction = scrollFraction,
    percent = percent,
    updatedAtMs = updatedAtMs,
    sessionAccumMs = sessionAccumMs,
)

internal fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    documentId = documentId,
    resourceToken = resourceToken,
    charOffset = charOffset,
    text = text,
    createdAtMs = createdAtMs,
    note = note,
)

internal fun HighlightEntity.toDomain(): Highlight = Highlight(
    id = id,
    documentId = documentId,
    resourceToken = resourceToken,
    startOffset = startOffset,
    endOffset = endOffset,
    text = text,
    colorHex = colorHex,
    note = note,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

internal fun NoteEntity.toDomain(): ReaderNote = ReaderNote(
    id = id,
    documentId = documentId,
    resourceToken = resourceToken,
    anchorOffset = anchorOffset,
    text = text,
    linkedHighlightId = linkedHighlightId,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)
