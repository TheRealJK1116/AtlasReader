# Atlas Reader — R8/ProGuard rules.

# jsoup uses reflection for HTML entity and tag lookups; keep the model classes.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Kotlin coroutines internals (debug mode names are irrelevant here).
-dontwarn kotlinx.coroutines.**

# Room ships its own consumer rules; keep entities and DAOs referenced via generated code.
-dontwarn androidx.room.paging.**
