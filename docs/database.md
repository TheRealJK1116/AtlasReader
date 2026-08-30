# Atlas Reader — Database Design (Room schema v1)

Every table and its justification. Export of the authoritative schema:
`app/schemas/com.atlasreader.core.database.AtlasDatabase/1.json`.

## documents

| column | type | justification |
|---|---|---|
| id | INTEGER PK autoincrement | stable internal key |
| contentHash | TEXT UNIQUE | SHA-256 head+tail sample — the duplicate-detection key; survives renames/moves |
| fileName / displayName | TEXT | disk name vs user-visible name |
| filePath | TEXT | absolute path of the private copy (SAF sources go stale → we import copies) |
| fileSizeBytes | INTEGER | sorting, size display |
| format | TEXT | `DocumentFormat` name; drives engine lookup |
| title / author / description / language / publisher / publishedDate | TEXT? | extracted metadata; title falls back to filename stem |
| sourceUri | TEXT? | original SAF uri (for diagnostics / re-import) |
| addedAtMs / openedAtMs | INTEGER | sorting by date added / recents |
| favorite | BOOLEAN | favourite filter |
| lastPositionJson | TEXT? | reserved position blob |

Indexes: contentHash (unique), addedAtMs, openedAtMs, title, author, favorite,
format — every sort/filter path is index-backed.

## covers

| column | type | justification |
|---|---|---|
| documentId | INTEGER PK, FK→documents CASCADE | one cover per document |
| path | TEXT | file path `covers/<hash>.jpg`; bytes never live in SQLite (50k×60KB ≈ 3GB would bloat backups and paging) |
| width / height | INTEGER | layout hints |
| updatedAtMs | INTEGER | regeneration policy |

## collections / collection_documents

Collections are many-to-many with documents (a book can be in “Sci-fi” and
“Book club”). The cross-ref table carries both FK cascades and a
`documentId` index for “which collections is this in?”.

## tags / document_tags

Tags are a second many-to-many dimension. Tag names are unique
(case-insensitive dedupe via `ensure()`); cross-ref cascades on both sides.

## reading_progress

| column | type | justification |
|---|---|---|
| documentId | INTEGER PK, FK CASCADE | one position per document |
| resourceToken | TEXT? | chunk address (spine id / ordinal) — restores exactly |
| charOffset | INTEGER | reserved for precise offset restore |
| pageIndex | INTEGER | PDF page |
| scrollFraction | REAL | WebView scroll restore |
| percent | REAL | derived overall progress; drives sorting/continue-reading/finished |
| updatedAtMs | INTEGER | recents ordering, continue-reading |
| sessionStartMs / sessionAccumMs | INTEGER | reading statistics (accumulated ms per document) |

## bookmarks

Saved positions with optional user note; anchored by resourceToken + charOffset
+ snippet text.

## highlights

Coloured ranges: `(documentId, resourceToken, startOffset, endOffset, text,
colorHex, note)` — `text` is the snippet used by the JS fuzzy locator.
`updatedAtMs` supports future export/sync.

## notes

`(documentId, resourceToken, anchorOffset, text, linkedHighlightId?)` —
notes may be linked to a highlight (FK SET_NULL so deleting a highlight keeps
the note).

## search_history

`(query, createdAtMs)` — suggestion history, pruned to 30 rows.

## chunk_previews

`PK(documentId, chunkToken), preview TEXT` — first 500 chars of each chunk so
search snippets never require re-parsing.

## user_preferences

`PK(key), value` — library UI state (view mode, sort) and future aggregates;
participates in Android backups (see `res/xml/backup_rules.xml`).

## search_index (virtual)

Contentless FTS4 created in `AtlasDatabase.CALLBACK.onCreate`:

```sql
CREATE VIRTUAL TABLE search_index USING fts4(
  documentId, chunkToken, position, term,
  tokenize=unicode61 "remove_diacritics 2");
```

One row per token; queries use `MATCH 'term* AND term*'` with prefix matching.
Room entities can't model virtual tables, so writes go through
`openHelper.execSQL` (batched transactions) and reads through `@RawQuery`.

## Integrity & migration policy

- `PRAGMA foreign_keys = ON` on every open; CASCADE keeps annotations and
  cross-refs consistent on document deletion.
- WAL journal mode for concurrent reader/writer flows.
- `exportSchema = true`, snapshots in `app/schemas/`; every version bump
  ships a `Migration` object registered in `DatabaseModule` plus a
  migration test.
