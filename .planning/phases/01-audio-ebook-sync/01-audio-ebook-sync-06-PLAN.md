---
phase: 01-audio-ebook-sync
plan: 06
type: execute
wave: 3
depends_on: [01-audio-ebook-sync-01, 01-audio-ebook-sync-02, 01-audio-ebook-sync-03, 01-audio-ebook-sync-04, 01-audio-ebook-sync-05]
files_modified:
  - app/src/main/java/com/aryan/reader/audio/AudioSyncWorker.kt
  - app/src/main/java/com/aryan/reader/audio/AudioSyncOrchestrator.kt
  - app/src/main/java/com/aryan/reader/audio/AudioSyncProviderSelector.kt
  - app/src/main/java/com/aryan/reader/MainViewModel.kt
  - app/src/test/java/com/aryan/reader/audio/AudioSyncOrchestratorTest.kt
autonomous: true
requirements: [AES-02, AES-03, AES-04, AES-06]
must_haves:
  truths:
    - "A sync job runs in the background from selected audio to synced EPUB output"
    - "Local transcription is attempted before configured API fallbacks"
    - "Completed synced EPUB output is imported or linked as a normal EPUB3-readable file"
    - "Progress, cancellation, and failures are visible through persisted session status"
  artifacts:
    - path: "app/src/main/java/com/aryan/reader/audio/AudioSyncWorker.kt"
      provides: "WorkManager entry point for long-running sync"
    - path: "app/src/main/java/com/aryan/reader/audio/AudioSyncOrchestrator.kt"
      provides: "Pipeline coordinator for transcribe, align, write, and import"
    - path: "app/src/main/java/com/aryan/reader/MainViewModel.kt"
      provides: "UI-facing start/cancel/observe methods"
  key_links:
    - from: "AudioSyncWorker.kt"
      to: "AudioSyncRepository.kt"
      via: "persisted progress and final output path updates"
      pattern: "updateProgress"
    - from: "AudioSyncOrchestrator.kt"
      to: "SharedEpubMediaOverlayWriter.kt"
      via: "writes final synced EPUB3 after alignment"
      pattern: "MediaOverlayWriter"
---

<objective>
Wire transcription, alignment, EPUB writing, and library output into one background sync pipeline.

Purpose: Users need a reliable long-running job that can survive UI changes, report progress, use local-first fallback logic, and produce a usable EPUB3 file.
Output: WorkManager worker, orchestrator, provider selection, ViewModel entry points, and orchestration tests.
</objective>

<execution_context>
@C:/Users/yoavc/.config/opencode/get-shit-done/workflows/execute-plan.md
@C:/Users/yoavc/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-01-SUMMARY.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-02-SUMMARY.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-03-SUMMARY.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-04-SUMMARY.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-05-SUMMARY.md
@app/src/main/java/com/aryan/reader/MainViewModel.kt
@app/src/main/java/com/aryan/reader/BookImporter.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Implement local-first sync orchestrator</name>
  <files>app/src/main/java/com/aryan/reader/audio/AudioSyncOrchestrator.kt, app/src/main/java/com/aryan/reader/audio/AudioSyncProviderSelector.kt</files>
  <action>Create an orchestrator that loads a session, validates the source EPUB/audio files, chooses providers in order (`local-whisper` first, then user-enabled OpenAI/Deepgram backup providers if not offline), transcribes all audio sources, extracts/marks EPUB sentences using shared writer helpers, aligns transcript to sentences, writes a synced EPUB into `filesDir/audio_sync/{sessionId}/output/{safeTitle}.synced.epub`, and records warnings/confidence counts. Provider fallback should happen only for recoverable local/provider failures and only when the user enabled backup for the session.</action>
  <verify>Unit tests with fake providers prove provider ordering, fallback, failure recording, and output path selection.</verify>
  <done>Pipeline orchestration can be tested without UI or real native/API calls.</done>
</task>

<task type="auto">
  <name>Task 2: Add WorkManager worker and ViewModel controls</name>
  <files>app/src/main/java/com/aryan/reader/audio/AudioSyncWorker.kt, app/src/main/java/com/aryan/reader/MainViewModel.kt</files>
  <action>Add `AudioSyncWorker` that receives `sessionId`, runs the orchestrator on `Dispatchers.IO`, updates foreground/progress metadata when appropriate, respects cancellation, and writes terminal `COMPLETED`, `FAILED`, or `CANCELLED` status. Add `MainViewModel` methods to observe sync sessions, create session from selected audio URIs, start work, cancel work, clear failed/completed session, and open/import final synced EPUB. Import the final EPUB as a normal EPUB item with a display name suffix like `Readaloud` and preserve original metadata where practical.</action>
  <verify>`./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.AudioSyncOrchestratorTest"` passes; `./gradlew.bat :app:assembleOssDebug` compiles.</verify>
  <done>UI code can start/cancel/observe a background sync and find the synced EPUB output when complete.</done>
</task>

<task type="auto">
  <name>Task 3: Test orchestration state transitions</name>
  <files>app/src/test/java/com/aryan/reader/audio/AudioSyncOrchestratorTest.kt</files>
  <action>Add tests using fake repository, fake local provider, fake API provider, fake aligner, and fake writer. Cover success, local failure with API fallback, all providers failed, cancellation before write, output import failure, and progress step ordering (`PREPARING`, `TRANSCRIBING`, `ALIGNING`, `WRITING_EPUB`, `IMPORTING`, `COMPLETED`).</action>
  <verify>`./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.AudioSyncOrchestratorTest"`</verify>
  <done>Sync pipeline state transitions and fallback policy are covered without long-running real transcription.</done>
</task>

</tasks>

<verification>
Run `./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.AudioSyncOrchestratorTest"` and `./gradlew.bat :app:assembleOssDebug`.
</verification>

<success_criteria>
A background job can turn an EPUB + selected audio sources into a synced EPUB3 output using local-first transcription and direct API backups when enabled.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-06-SUMMARY.md`
</output>
