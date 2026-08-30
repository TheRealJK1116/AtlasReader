# Atlas Reader — Performance engineering

Identified bottlenecks **before** implementation and the mitigations in code.

## Bottlenecks & mitigations

| # | Bottleneck | Mitigation |
|---|---|---|
| 1 | Importing a file copies + hashes + parses + indexes | hash-while-copy (one pass); metadata failure degrades to filename; parse+index runs after the library row exists (background) |
| 2 | Huge prose files in one WebView page | chunking at ~1.5k chars; each chapter is one `loadDataWithBaseURL` (~16 KB layout); `chunk_max` 4k chars |
| 3 | 50k-row library scrolling | Paging 3 with `pageSize=30, prefetchDistance=6, enablePlaceholders=false`; index-backed ORDER BY; flattened JOIN avoids N+1 relations |
| 4 | Cover BLOBs in SQLite | covers are files (`covers/<hash>.jpg`), sampled decode to 320 px, LRU ×24 |
| 5 | PDF memory | `PdfPageProvider` renders one page at a time at display width (cap 4096 px), LRU ×8 bitmaps, mutex-guarded renderer |
| 6 | Full-text search at query time | contentless FTS4 tokens + `chunk_previews` snippets → no re-parse on search |
| 7 | Duplicate detection cost | SHA-256 over head+tail sample (256 KB each), not full file |
| 8 | Startup | no startup DB work; library loads via Paging; DataStore reads are cold but tiny |
| 9 | Reading-position thrash | scroll fraction reported every 200 ms (JS throttle), persisted after 1.5 s debounce |
| 10 | WebView memory across chapters | one WebView reused; chunk HTML replaced via loadDataWithBaseURL; view destroyed on exit |
| 11 | Index building | batched transactions (1k tokens/batch), per-document idempotent delete+insert, `Dispatchers.IO` |
| 12 | RTF/Markdown parsing | JVM-only parsing, no Android reflection; unit-tested, ~ms for typical files |

## Memory budget

- Parsed-document cache: 3 documents (bounded by design — typical book text is
  <10 MB; 3 × 10 MB worst case acceptable, LRU).
- Cover bitmap cache: 24 entries × ~160 KB ≈ 4 MB.
- PDF bitmap cache: 8 pages (two-screen paging) — recycled via GC; rendering
  is width-fit so a tablet page ≈ 3–5 MB × 8 ≈ 40 MB worst case.

## Database optimisation

- WAL journal (concurrent readers).
- `PRAGMA foreign_keys = ON`.
- Indexes cover every ORDER BY/WHERE path (see database.md).
- Contentless FTS4 stores tokens only — no column bloat.
- Bulk deletes cascade via FK, single transaction where possible.

## Future instrumentation

- `androidx.benchmark:benchmark-macro-junit4` — app start, library scroll,
  book open (see docs/testing.md).
- LeakCanary integration documented, not bundled.
