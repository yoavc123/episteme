---
phase: 01-audio-ebook-sync
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/aryan/reader/data/AppDatabase.kt
  - app/src/main/java/com/aryan/reader/audio/AudioSyncEntity.kt
  - app/src/main/java/com/aryan/reader/audio/AudioSyncDao.kt
  - app/src/main/java/com/aryan/reader/audio/AudioSyncModels.kt
  - app/src/main/java/com/aryan/reader/audio/AudioSyncRepository.kt
  - app/src/main/java/com/aryan/reader/audio/AudioSyncFileImporter.kt
  - app/src/test/java/com/aryan/reader/audio/AudioSyncRepositoryTest.kt
autonomous: true
requirements: [AES-01, AES-06]
must_haves:
  truths:
    - "A sync session can be created for an EPUB book without adding audio files as normal readable books"
    - "Selected audio files are copied into app-private per-session storage"
    - "Sync status, progress, selected provider, output EPUB path, and errors persist across app restarts"
  artifacts:
    - path: "app/src/main/java/com/aryan/reader/audio/AudioSyncModels.kt"
      provides: "Domain models for audio sync sessions, status, provider, source files, and progress"
    - path: "app/src/main/java/com/aryan/reader/audio/AudioSyncRepository.kt"
      provides: "Repository API for creating, observing, updating, cancelling, and deleting sync sessions"
    - path: "app/src/main/java/com/aryan/reader/audio/AudioSyncFileImporter.kt"
      provides: "Audio source copy/validation into filesDir/audio_sync"
    - path: "app/src/main/java/com/aryan/reader/data/AppDatabase.kt"
      provides: "Room database version bump and migration for audio_sync_sessions"
  key_links:
    - from: "AudioSyncRepository.kt"
      to: "AudioSyncDao.kt"
      via: "Flow-backed session queries and status updates"
      pattern: "audioSyncDao"
    - from: "AudioSyncFileImporter.kt"
      to: "filesDir/audio_sync/{sessionId}/source"
      via: "safe app-private copy, extension validation, and stored metadata"
      pattern: "audio_sync"
---

<objective>
Create the persistence and file-storage foundation for audiobook sync sessions.

Purpose: Audio files must be loaded for a specific book and tracked through a long-running sync without becoming normal readable library entries.
Output: Room session storage, repository APIs, source audio importer, and unit tests.
</objective>

<execution_context>
@C:/Users/yoavc/.config/opencode/get-shit-done/workflows/execute-plan.md
@C:/Users/yoavc/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-CONTEXT.md
@app/src/main/java/com/aryan/reader/data/AppDatabase.kt
@app/src/main/java/com/aryan/reader/data/RecentFileItem.kt
@app/src/main/java/com/aryan/reader/MainViewModel.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add audio sync Room session storage</name>
  <files>app/src/main/java/com/aryan/reader/data/AppDatabase.kt, app/src/main/java/com/aryan/reader/audio/AudioSyncEntity.kt, app/src/main/java/com/aryan/reader/audio/AudioSyncDao.kt, app/src/main/java/com/aryan/reader/audio/AudioSyncModels.kt</files>
  <action>Add a new `audio_sync_sessions` Room table and domain models. Use `sessionId` as primary key and store `bookId`, `bookTitle`, `sourceEpubUri`, `audioSourcesJson`, `provider`, `status`, `progressPercent`, `currentStep`, `outputEpubPath`, `errorMessage`, `createdAt`, `updatedAt`, and `cancelRequested`. Bump `AppDatabase` from version 23 to 24 and add `MIGRATION_23_24`; do not destructively migrate. Keep enum/string conversions explicit so corrupt rows map to a safe failed/idle state.</action>
  <verify>`./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.AudioSyncRepositoryTest"` passes after tests are added.</verify>
  <done>Database opens with migration 23→24, exposes `audioSyncDao()`, and can persist/query session rows with status/progress fields.</done>
</task>

<task type="auto">
  <name>Task 2: Add audio source importer and repository APIs</name>
  <files>app/src/main/java/com/aryan/reader/audio/AudioSyncRepository.kt, app/src/main/java/com/aryan/reader/audio/AudioSyncFileImporter.kt</files>
  <action>Create `AudioSyncRepository` with APIs to create a session for an EPUB `RecentFileItem`, observe sessions by `bookId`, update progress/status/output/error, request cancellation, and delete a session cache. Create `AudioSyncFileImporter` that accepts `Uri` audio sources, validates extensions/MIME types (`mp3`, `m4a`, `m4b`, `mp4`, `wav`, `webm`, and zip archives containing those files), copies bytes to `context.filesDir/audio_sync/{sessionId}/source/`, records display names/sizes/mime types, and rejects path traversal or unsupported archives. Do not modify `SharedFileCapabilities.androidFilePickerMimeTypes`; audio selection happens inside sync UI, not the normal book picker.</action>
  <verify>Repository tests prove sessions can be created, source metadata saved, progress updated, and deleted without touching `RecentFileItem` rows.</verify>
  <done>Audio sources are linked to sync sessions only, stored under app-private sync folders, and never appear as normal books.</done>
</task>

<task type="auto">
  <name>Task 3: Cover persistence and importer edge cases</name>
  <files>app/src/test/java/com/aryan/reader/audio/AudioSyncRepositoryTest.kt</files>
  <action>Add Robolectric/JUnit tests for migration-backed database creation, repository create/update/delete behavior, importer extension allow-list behavior, unsupported file rejection, and zip traversal rejection. Use temporary files/URIs in tests; do not depend on real audiobook assets.</action>
  <verify>`./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.AudioSyncRepositoryTest"`</verify>
  <done>Tests fail for missing storage/repository behavior before implementation and pass after implementation.</done>
</task>

</tasks>

<verification>
Run `./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.AudioSyncRepositoryTest"` and confirm the app database schema version is 24 with non-destructive migration from 23.
</verification>

<success_criteria>
An EPUB book can have a persistent audio sync session with app-private copied audio sources and observable status/progress, without adding audio files to the normal library.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-01-SUMMARY.md`
</output>
