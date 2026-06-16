# Discovery: Audio Ebook Sync

## Discovery Level

Level 3 — audio/ML architecture with on-device ASR, external transcription backups, forced alignment, and EPUB3 Media Overlay output.

## Relevant Existing Code

- TTS settings popup: `app/src/main/java/com/aryan/reader/Common.kt` (`TtsSettingsSheet`, `TtsPlaybackManager.TtsMode` usage).
- Home book cards: `app/src/main/java/com/aryan/reader/HomeScreen.kt` (`RecentFileCard`).
- Library book entries: `app/src/main/java/com/aryan/reader/LibraryScreen.kt` (`LibraryListItem`).
- File import and app state: `app/src/main/java/com/aryan/reader/MainViewModel.kt` (`onFilesSelected`, import helpers, `SUPPORTED_MIME_TYPES`).
- BYOK settings: `Common.kt`, `AiSettingsScreen.kt`.
- Room database: `app/src/main/java/com/aryan/reader/data/AppDatabase.kt`, `RecentFileEntity.kt`.
- EPUB rewrite precedent: `shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubMetadataEditor.kt` preserves `mimetype` first/stored and rewrites OPF safely.
- Media playback infrastructure already uses Media3 in `app/build.gradle.kts`.
- Background infrastructure already uses WorkManager in `app/build.gradle.kts`.

## External Reference Findings

- Storyteller aligns EPUB text to audiobook transcription and produces EPUB3-compliant Media Overlays with sentence-level text ids, SMIL references, audio assets, and OPF wiring.
- EPUB3 Media Overlays require stable element ids in XHTML, SMIL files with text/audio pairs, manifest entries with `application/smil+xml`, and `media-overlay` attributes linking content documents to SMIL overlays.
- OpenAI `whisper-1` transcription supports `response_format=verbose_json` and `timestamp_granularities[]=word`, but uploads are limited to 25 MB and must be chunked.
- Deepgram prerecorded API returns word timestamps and sentence/paragraph ranges for uploaded local audio files, with a larger file limit than OpenAI.
- Whisper.cpp has an official Android sample and can run tiny/base models locally; models should not be hard-bundled into release APKs because of size.

## Chosen Architecture

1. Store sync sessions separately from normal books.
2. Copy selected audio source files into app-private sync storage per book/session.
3. Transcription provider interface:
   - Default provider: local Whisper.cpp on-device engine with user-managed/downloaded model.
   - Backup providers: OpenAI Whisper and Deepgram via direct BYOK calls.
4. Alignment engine in shared testable code:
   - Normalize EPUB sentences and transcript words.
   - Use windowed fuzzy matching to map sentences to word ranges.
   - Interpolate short gaps and mark low-confidence ranges.
5. EPUB3 writer in shared JVM code:
   - Wrap sentences in stable spans/ids without breaking existing markup.
   - Add copied audio assets and SMIL overlays.
   - Update OPF manifest/spine metadata and preserve valid EPUB zip layout.
6. Android worker orchestrates transcription → alignment → EPUB3 write → import/readaloud record update.
7. UI exposes sync button on EPUB book entries, wizard/progress state, output open action, and reader TTS popup synced-audio option.

## Risks and Mitigations

- Local ASR native integration is high-risk: isolate behind `AudioTranscriptionProvider`, keep API fallbacks working, and fail gracefully when no model is installed.
- Long audiobooks exceed API upload limits: chunk by duration/size and preserve absolute timestamps.
- EPUB markup can break rich XHTML: test nested emphasis/link/list cases and do not alter script/style/nav content.
- Alignment may be imperfect: surface confidence and allow reprocessing/fallback provider selection rather than hiding errors.
- Offline builds cannot use API fallback or model downloads: disable network provider choices when `BuildConfig.IS_OFFLINE` is true and support user-imported local models.
