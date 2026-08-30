# Atlas Reader — Architecture

A local-first Android e-reader for EPUB, PDF, Markdown, TXT, RTF and HTML,
with a plugin-capable document engine, an FTS4 search index, a 50k-document
library, and a Material 3 adaptive UI.

This document records the architecture decisions, the reasoning behind them
(THOUGHT), the resulting design (ACTION) and the known weaknesses and
mitigations (OBSERVATION), per subsystem.

---

## 1. Design principles (in priority order)

1. **Reliability** — every failure is a value, never a crash. Engines report
   typed errors; imports never abort the batch; the reader restores position.
2. **Performance** — paged library queries, incremental chunk rendering,
   bounded bitmap caches, background indexing.
3. **Extensibility** — one interface per cross-cutting concern; formats,
   sources and annotations are plugins, not switches.
4. **Android UX quality** — Material 3, dynamic colour, edge-to-edge, adaptive
   navigation (bottom bar / rail / drawer), motion-consistent states.
5. **Accessibility** — large touch targets, content descriptions, screen-reader
   friendly semantics, 12sp+ default text, contrast-aware palettes.
6. **Maintainability** — package boundaries align with future module splits;
   every layer depends only inward.

## 2. Module & package layout

Single `:app` module today, but packages are the future module boundaries:

```
com.atlasreader
├── core/                     # framework-agnostic core → :core:*
│   ├── common/               #   AtlasResult, AtlasLog, DispatcherProvider, TimeProvider
│   ├── database/             #   Room schema, DAOs, LibraryQueryBuilder → :core:database
│   │   ├── dao/              #     Document/Collection/Tag/Cover/Progress/Annotation/Search/UserPreferences DAOs
│   │   └── entity/           #     Room entities + flattened row projections
│   ├── datastore/            #   AppSettings (theme, reader defaults, import prefs)
│   ├── di/                   #   Hilt AppModule (DB + DAOs + provider binds)
│   ├── engine/               #   DocumentEngine plugin contract + engines → :core:engine
│   │   └── engines/          #     Text, Markdown (flexmark), RTF (hand-rolled), HTML (jsoup), EPUB, PDF (PdfRenderer)
│   ├── importer/             #   ImportCoordinator, FileCopier (hash-while-copy), CoverStore
│   ├── indexer/              #   TextTokenizer, SearchIndexer (FTS4 contentless)
│   └── util/                 #   FileHash, TextNormalizer, HtmlUtils, FilenameUtils
├── data/repository/          # Library, Reader, Search, Settings repositories (mappers live here)
├── domain/
│   ├── model/                # DocumentSummary, ReadingPosition, annotations, search models
│   └── usecase/              # thin orchestration façade consumed by ViewModels
├── ui/                       # Compose: theme, navigation, components, 5 screens + reader
├── worker/                   # ReindexWorker, CleanupWorker (Hilt workers)
└── MainActivity.kt / AtlasReaderApp.kt
```

Layer rule: `ui → usecase → repository → dao/engine/importer/indexer → database`.
Nothing below the repository layer imports Compose or Android UI.

### THOUGHT
A single module keeps the first build simple, but Hilt, Room, engine and UI
have very different change frequencies. Package-first layout lets us split
without moving code. `domain` stays pure Kotlin (no Android imports) so use
cases are JVM-testable.

### OBSERVATION
Single-module means full rebuilds on any change. Mitigation: keep `core`
dependency-light, rely on Gradle configuration cache, split when the first
independent consumer (e.g. a Wear companion or a CLI indexer) appears.

## 3. Dependency injection graph

```
AtlasReaderApp (@HiltAndroidApp, Configuration.Provider → HiltWorkerFactory)
└── MainActivity (@AndroidEntryPoint)
    └── AtlasApp → RootViewModel(SettingsRepository)
        └── NavHost
            ├── LibraryScreen  → LibraryViewModel (ObserveLibrary, ContinueReading, Recents,
            │                    ToggleFavorite, DeleteDocuments, ImportFiles/Folder, Collections)
            ├── SearchScreen   → SearchViewModel (SearchLibrary)
            ├── CollectionsScreen → CollectionsViewModel (CollectionsUseCase)
            ├── CollectionDetailScreen
            ├── StatsScreen    → StatsViewModel (DocumentDao)
            ├── SettingsScreen → SettingsViewModel (SettingsRepository, WorkManager)
            └── ReaderScreen   → ReaderViewModel (OpenDocument, ReaderAnnotations,
                                 ReaderPosition, SettingsRepository, TimeProvider)

Singleton graph:
  AtlasDatabase → DAOs
  EngineRegistry → TextEngine, MarkdownEngine, RtfEngine, HtmlEngine, EpubEngine, PdfEngine
  ImportCoordinator → EngineRegistry, FileCopier, CoverStore, DocumentDao, SearchIndexer, AppSettings
  SearchIndexer → AtlasDatabase, SearchDao
  SettingsRepository → AppSettings(DataStore), UserPreferencesDao
  DispatcherProvider (Default), TimeProvider (System) — @Binds
```

All production scopes are `@Singleton`; ViewModels are `@HiltViewModel` with
`SavedStateHandle` (reader document id survives process death).

## 4. Document engine contract (plugin architecture)

```kotlin
interface DocumentEngine {
    val format: DocumentFormat
    suspend fun extractMetadata(context: Context?, source: DocumentSource): ExtractedMetadata
    suspend fun extractCover(context: Context?, source: DocumentSource): ByteArray? = null
    suspend fun parse(context: Context?, source: DocumentSource): ParsedDocument
}
```

`DocumentSource` = SAF uri + display name + post-import local file path.
Engines prefer the local file (fast, JVM-testable with `context = null`).

`ParsedDocument` = metadata + `List<TocEntry>` + `List<ProseChunk>` +
optional `PageProvider` (PDF).

`ProseChunk(resourceToken, index, heading, text, html, baseUrl)`:
- **`text` is the annotation anchor space** — bookmark/highlight/note offsets
  are stable character offsets into `text`.
- **`html` is the render payload** for the reader WebView; the invariant
  `HtmlUtils.textFromHtml(html) ≈ text` makes JS annotation locating possible.
- **`baseUrl`** (EPUB only) is the real chapter file path so relative
  images/CSS/links resolve.

Adding a format = implement `DocumentEngine` + one enum entry + registry line.

### Engines
| Format | Parser | Notes |
|---|---|---|
| TXT | custom | paragraph chunking ~1.5k chars, CHAPTER/PART heading TOC, UTF-8→cp1252 fallback |
| Markdown | flexmark (core, tables, strikethrough) | chunks split on ATX headings |
| RTF | hand-rolled state machine | groups, scoped styles, `\uN`/`\'hh` escapes, colortbl, skipped destinations |
| HTML | jsoup | single chunk, scripts/on* stripped, `#anchor` links native |
| EPUB | java.util.zip + jsoup | container.xml → OPF → spine; EPUB3 nav + EPUB2 NCX TOC; cover meta/property; archive extracted for base URLs |
| PDF | PdfRenderer | fixed-layout `PageProvider`, mutex-guarded lazy renderer, first-page cover |

### THOUGHT
RTF parsing is the riskiest pure logic; a dependency (e.g. `rtfparserkit`) is
an option, but the required surface (bold/italic/underline/color/size/para)
is small and a hand-rolled parser is fully unit-tested on the JVM. flexmark
and jsoup are mature, permissively licensed, and used in read-only mode (no
network fetching).

### OBSERVATION
- flexmark's HTML output is not identical to `textContent` whitespace; the
  JS highlighter therefore uses **snippet-anchored fuzzy locating** (offset
  tolerance ±20 chars, then `indexOf(snippet)` fallback). Offsets written by
  selection use the *same* JS walker as locating, so they are always exact.
- PDF has no text layer via PdfRenderer → full-text search excludes PDF in v1
  (roadmap: PdfBox / PDF.js text layer).
- CJK text has no word segmentation in unicode61 → search is whole-phrase;
  documented limitation.

## 5. Import pipeline

```
SAF picker / share / drag-drop / folder tree
   ↓  ImportRequest(uri, name, mime)
ImportCoordinator (singleton, single-worker channel queue, StateFlow<ImportState>)
   ├─ 1. validate  → engineForUri(name, mime) else Unsupported (continue)
   ├─ 2. copy      → FileCopier streams to filesDir/imports/, hashing head+tail SHA-256 in-flight
   ├─ 3. duplicate → byHash(contentHash) → Duplicate (copy deleted) | continue
   ├─ 4. metadata  → engine.extractMetadata (failures degrade to filename title)
   ├─ 5. cover     → engine.extractCover → covers/<hash>.jpg + covers row
   ├─ 6. insert    → documents row (content-addressed)
   └─ 7. index     → background: engine.parse + SearchIndexer tokens + chunk previews
failures per file are recorded, the batch continues, UI gets a summary snackbar
autoOpen event emitted when preference set
```

### THOUGHT
Hashing **while copying** gives single-pass duplicate detection and
content-addressed storage (stable across renames). Imports are serialised
through one worker so DB writes never contend; parsing/indexing for big books
runs after the row is visible, so the library feels instant.

### OBSERVATION
- Share-intent uri grants are temporary; we read immediately, so copies land
  before grants expire. Folder-tree permissions are persisted with
  `takePersistableUriPermission` at pick time.
- 200 MB per-file cap protects memory; sampled hashing keeps dup detection
  cheap on huge files.

## 6. Search architecture

- **Index**: contentless FTS4 `search_index(documentId, chunkToken, position, term)`
  created via `RoomDatabase.Callback`; rows inserted in batches with
  `openHelper.execSQL`; per-document delete + reindex (idempotent).
- **Tokenizer**: NFKC fold, lowercase, diacritics stripped, letter/digit runs.
- **Query**: `term* AND term*` (prefix, all-terms), fallback OR on syntax
  errors; results grouped by document, ranked by hit count; snippets windowed
  from `chunk_previews` (500 chars/chunk) — **no re-parsing at query time**.
- **Metadata search**: `LIKE` on title/author/fileName with ESCAPE — instant,
  drives suggestions.
- **History**: `search_history` table (30 entries) with prune.

### THOUGHT
Storing tokens row-by-row in a contentless table gives us prefix matching and
`unicode61 remove_diacritics` normalisation without sqlite-fts extensions.
Room cannot model virtual tables as entities, so the index is written through
`SupportSQLiteDatabase` and read through `@RawQuery` — both supported by Room
2.6.

### OBSERVATION
`@RawQuery`-returning-`PagingSource` is used for the library list; if a future
Room version regresses that, the documented fallback is a hand-rolled
`PagingSource` over `@RawQuery` page queries.

## 7. Reading pipeline & annotation model

```
Open → ReaderRepository.open(id)
  ├─ entity → engine → ParsedDocument (PDF re-parsed each open; others LRU-cached ×3)
  ├─ restore position (resourceToken + scrollFraction / pageIndex)
  └─ touch openedAtMs

Continuous rendering (TXT/MD/RTF/HTML/EPUB):
  ReaderWebView loads chunk HTML + theme CSS (loadDataWithBaseURL, base=chapter file)
  ├─ CSS: reader theme (light/sepia/dark/amoled/auto), font size/family, line-height,
  │        page width, margins — injected live without reload
  ├─ JS walker (block-boundary newlines) == chunk.text → offsets
  ├─ highlights: <mark> wrapping via Range API, snippet fuzzy fallback
  ├─ selection: custom ActionMode (Highlight / Add note) → ATLAS_SELECTION_INFO
  ├─ scroll fraction reported throttled → debounced position save
  ├─ in-document search: findAllAsync + findNext
  ├─ tap-to-toggle: JS touch slop/duration filter (links/images excluded)
  └─ cross-chapter links: shouldOverrideUrlLoading → chunk match by baseUrl

Fixed layout (PDF):
  HorizontalPager + PdfPageProvider (mutex-guarded PdfRenderer, bitmap LRU ×8,
  width-fit scaling capped at 4096px)

Session statistics: ON_START/ON_STOP session ms accumulated into
reading_progress.sessionAccumMs; stats screen aggregates total/finished/started.
```

### THOUGHT
One renderer (WebView) for all prose formats = one theme system, one
annotation bridge, one accessibility story. Chunking (~1.5k chars) bounds
layout cost per chapter so scrolling stays smooth and memory stays low.

### OBSERVATION
- `charOffset` is stored but not updated per scroll (resourceToken +
  scrollFraction restore exactly); offsets remain meaningful for
  bookmarks/highlights/notes.
- WebView `allowFileAccess` is constrained to our import tree by routing
  file:// through `shouldOverrideUrlLoading`; content access is off; mixed
  content blocked. External http(s) links open in the system browser.

## 8. Library / paging

- Flattened projection `documents ⋈ covers ⋈ reading_progress` (aliased
  columns → `DocumentRow`) avoids `@Relation`+paging limitations.
- `LibraryQueryBuilder` (pure Kotlin) composes WHERE/ORDER BY for 6 sorts ×
  {query, formats, collection, tag, favourites, status} filters; user text is
  LIKE-escaped.
- `PagingSource<Int, DocumentRow>` via `@RawQuery(observedEntities=…)` — Room
  appends LIMIT/OFFSET and invalidates on document changes.
- View modes: grid (adaptive columns), compact grid, list, detailed list.
- Bulk actions: multi-select → favourite / add-to-collection / delete
  (files + covers + index rows cascade).

### OBSERVATION
Covers are files, not BLOBs (50k × ~60 KB ≈ 3 GB in SQLite otherwise).
Decode is sampled to 320 px and LRU-cached (24 entries). 50k-row scans stay
<50 ms thanks to indexes on addedAtMs/openedAtMs/title/author/format/favorite.

## 9. Database schema

See `docs/database.md` for DDL and per-table justification. Highlights:
- `documents.contentHash` unique → duplicate detection key.
- `covers` stores paths only; bytes on disk → backups stay small.
- `reading_progress` = position pointer + session time (stats).
- `bookmarks/highlights/notes` anchored to `(resourceToken, offsets)`.
- `search_index` contentless FTS4; `chunk_previews` for snippets.
- `user_preferences` key/value for library UI state (view mode, sort) + backups.
- `search_history` suggestions.
- Migrations: schema snapshots exported to `app/schemas/`; migration tests
  follow the Room testing recipe.

## 10. Performance engineering

See `docs/performance.md`. Headline measures:
- Bounded work: chunked prose, sampled hashing, preview-based snippets.
- Bounded memory: bitmap LRUs (covers 24, PDF pages 8), parsed-doc cache ×3,
  Paging 3 prefetch tuning, `enablePlaceholders=false`.
- I/O: content-addressed copies avoid re-copying duplicates; covers decoded
  with `inSampleSize`; DB in WAL mode.
- Background: imports/indexing/reindexing run on `Dispatchers.IO`/workers;
  UI never parses.

## 11. Testing strategy

See `docs/testing.md`. Layers:
- JVM unit: RTF parser, tokenizer, query builder, engines (file-based, null
  context), hashing, normalisation.
- Instrumented: Room round-trips (incl. FTS4 MATCH), library SQL validity,
  UI smoke (app launches, empty state).
- Future: Kaspresso flows, accessibility checks (touch target/contrast
  lint), performance profiling (macrobenchmark), migration tests.

## 12. Known v1 gaps (roadmap)

- Folder auto-monitoring (SAF tree polling) — importer supports folder
  import; continuous watching is scheduled work.
- PDF full-text search + PDF TOC (PdfRenderer lacks text/outline APIs).
- CJK tokenisation (jieba/Lucene analyzers).
- Backup/restore of the whole library (settings JSON exists; document-level
  backup is a future feature).
- DOCX/ODT/CBZ/MOBI/AZW3 engines — the registry makes them additive.
- Theme per-book persistence; reading statistics per day.
