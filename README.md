# Atlas Reader

A modern, local-first Android e-reader and document reader.

- **Formats**: EPUB, PDF, Markdown, TXT, RTF, HTML — plugin architecture, more formats addable without touching existing engines (roadmap: DOCX, ODT, CBZ, CBR, MOBI, AZW3)
- **Library**: 50k+ documents, paged grid/list views, continue-reading, recents, favourites, collections, tags, bulk actions, content-addressed duplicate detection
- **Reader**: continuous WebView rendering with reader themes (light/sepia/dark/AMOLED), fonts, spacing, brightness, fullscreen, bookmarks, multi-colour highlights with notes, in-document search, TOC, session statistics
- **Search**: full-text (FTS4) with snippets + metadata search + history, built in the background
- **UI**: Material 3, dynamic colour, edge-to-edge, adaptive navigation (bottom bar → rail → drawer by screen size), AMOLED mode, foldable/tablet/Chromebook-friendly
- **Stack**: Kotlin · Jetpack Compose · Material 3 · MVVM/Clean Architecture · Room · Coroutines/Flow · Navigation Compose · Hilt · WorkManager · SAF · Paging 3

## Building

Prerequisites: JDK 17, Android SDK (compileSdk 35, minSdk 28).

```bash
# first time only — generates the Gradle wrapper (needs a local Gradle install)
gradle wrapper --gradle-version 8.10.2

./gradlew assembleDebug            # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew connectedDebugAndroidTest# instrumented tests (device/emulator)
./gradlew lint
```

The repo pins a known-good toolchain: AGP 8.7.3 · Gradle 8.10.2 · Kotlin 2.1.0 ·
KSP 2.1.0-1.0.29 · Hilt 2.53.1 · Compose BOM 2024.12.01 · Room 2.6.1.
Opening the project in Android Studio (Ladybug+) and running the `app`
configuration works out of the box.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — full architecture, decisions and rationale
- [`docs/database.md`](docs/database.md) — Room schema, table-by-table justification
- [`docs/performance.md`](docs/performance.md) — bottlenecks and mitigations
- [`docs/testing.md`](docs/testing.md) — testing strategy

## Known v1 limitations

- PDF full-text search and PDF TOC (platform PdfRenderer has no text/outline API)
- Continuous folder auto-monitoring (folder import exists)
- CJK word-segmented search
