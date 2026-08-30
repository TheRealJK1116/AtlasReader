# Atlas Reader — Testing strategy

## Layers

| Layer | Where | What |
|---|---|---|
| Unit (JVM) | `app/src/test` | RTF parser, tokenizer, query builder, engines (file-based, null Context), file hash, text normaliser, chunking, HTML escaping |
| Integration (JVM) | `app/src/test` | repository logic with fakes (dispatcher/time providers are injectable) |
| Instrumented | `app/src/androidTest` | Room in-memory round-trips incl. FTS4 MATCH via `@RawQuery`; library SQL validity; DAO CASCADE behaviour |
| UI smoke | `app/src/androidTest` | real app launches with production Hilt graph; empty library state renders |
| Migration | `app/src/androidTest` | Room migration test harness (`MigrationTestHelper`) against `app/schemas` snapshots |
| Performance | future `benchmark/` | macrobenchmarks: cold start, library scroll jank, book open latency, index build |
| Accessibility | future | compose-test `SemanticsNodeInteraction` checks for touch-target ≥48dp, content descriptions, contrast lint |

## Existing tests

- `RtfParserTest` — metadata capture, style spans, unicode/hex escapes,
  malformed input resilience.
- `TextTokenizerTest` — positions, diacritics, CJK safety, query terms.
- `LibraryQueryBuilderTest` — SQL shape for every filter/sort combination,
  placeholder/arg parity, LIKE escaping.
- `EngineUnitTest` — markdown chunking/TOC/metadata, text chunking/escaping,
  hash determinism, normalisation.
- `DatabaseRoundTripTest` (instrumented) — insert/query, FTS index →
  MATCH query, library projection SQL against the real schema.
- `LibrarySmokeTest` (instrumented) — app launches, library empty state.

## Conventions

- Tests never touch real SAF files: engines read local files with
  `DocumentSource.localPath` and `context = null`.
- Time and dispatchers come from `TimeProvider`/`DispatcherProvider` so
  session and save-throttle logic is deterministic.
- All worker paths are thin wrappers over repositories — tested through the
  repositories.

## Running

```bash
./gradlew testDebugUnitTest            # JVM tests
./gradlew connectedDebugAndroidTest    # instrumented (emulator/device)
./gradlew lint
```
