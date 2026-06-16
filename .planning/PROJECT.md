# Episteme Reader Planning Context

Episteme Reader is a Kotlin Multiplatform + Compose document and ebook reader for Android and desktop. The Android app owns current TTS, BYOK AI settings, file import, Room library metadata, WorkManager jobs, and Media3 playback infrastructure.

Current planning focus: add local-first audiobook-to-ebook synchronization that can align user-provided audio files with EPUB text, create an EPUB3 Media Overlays/readaloud output, and expose it from normal book entries plus the reader TTS popup.

Important repo patterns:
- Android app code lives in `app/src/main/java/com/aryan/reader/`.
- Shared reader/EPUB JVM code lives in `shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/`.
- Shared pure models/algorithms live in `shared/src/commonMain/kotlin/com/aryan/reader/shared/`.
- Android unit tests live in `app/src/test/java/com/aryan/reader/`.
- Shared tests live in `shared/src/commonTest/kotlin/com/aryan/reader/shared/` and `shared/src/desktopTest/kotlin/com/aryan/reader/shared/`.
- Existing BYOK AI secret storage is in `Common.kt` / `AiSettingsScreen.kt`.
- Existing TTS popup is `TtsSettingsSheet(...)` in `Common.kt`.
- Existing home cards are in `HomeScreen.kt`; library rows are in `LibraryScreen.kt`.
