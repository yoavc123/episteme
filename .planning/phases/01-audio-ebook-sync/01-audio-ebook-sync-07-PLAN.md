---
phase: 01-audio-ebook-sync
plan: 07
type: execute
wave: 4
depends_on: [01-audio-ebook-sync-06]
files_modified:
  - app/src/main/java/com/aryan/reader/HomeScreen.kt
  - app/src/main/java/com/aryan/reader/LibraryScreen.kt
  - app/src/main/java/com/aryan/reader/audio/AudioSyncSheet.kt
  - app/src/main/java/com/aryan/reader/MainViewModel.kt
  - app/src/main/res/values/strings.xml
  - app/src/androidTest/java/com/aryan/reader/AudioSyncUiTest.kt
autonomous: true
requirements: [AES-01, AES-02, AES-06]
must_haves:
  truths:
    - "Each normal EPUB book entry exposes a sync action"
    - "Users can select one or more audio files for a book from the sync UI"
    - "Users can choose local transcription and optional API fallback providers"
    - "Users can see progress, cancel, retry, and open the synced EPUB after completion"
  artifacts:
    - path: "app/src/main/java/com/aryan/reader/audio/AudioSyncSheet.kt"
      provides: "Bottom sheet/wizard for audio selection, provider choice, model status, progress, and output actions"
    - path: "app/src/main/java/com/aryan/reader/HomeScreen.kt"
      provides: "Home card sync button"
    - path: "app/src/main/java/com/aryan/reader/LibraryScreen.kt"
      provides: "Library row sync button/status"
  key_links:
    - from: "HomeScreen.kt"
      to: "MainViewModel.startAudioSync"
      via: "sync button opens AudioSyncSheet and starts session"
      pattern: "AudioSyncSheet"
    - from: "AudioSyncSheet.kt"
      to: "AudioSyncRepository"
      via: "observes session state from MainViewModel"
      pattern: "audioSync"
---

<objective>
Add the user-facing sync button and sync wizard UI.

Purpose: Users need an obvious flow from a normal book entry to loading audio, choosing local/API backup behavior, monitoring progress, and opening the output.
Output: Home/library sync controls, bottom sheet wizard, ViewModel bindings, strings, and UI tests.
</objective>

<execution_context>
@C:/Users/yoavc/.config/opencode/get-shit-done/workflows/execute-plan.md
@C:/Users/yoavc/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-06-SUMMARY.md
@app/src/main/java/com/aryan/reader/HomeScreen.kt
@app/src/main/java/com/aryan/reader/LibraryScreen.kt
@app/src/main/java/com/aryan/reader/MainViewModel.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add sync actions to home and library book entries</name>
  <files>app/src/main/java/com/aryan/reader/HomeScreen.kt, app/src/main/java/com/aryan/reader/LibraryScreen.kt, app/src/main/res/values/strings.xml</files>
  <action>Add a visible but compact sync action for EPUB entries in `RecentFileCard` and `LibraryListItem`. Use an icon button or pill labeled with localized strings such as "Sync audio". EPUB entries with an active session should show status/progress instead of a second start button. Non-EPUB readable entries should not claim support; either hide the button or show a disabled/explanatory state if the surrounding UI pattern supports it. Avoid interfering with card click/long-click selection behavior.</action>
  <verify>`./gradlew.bat :app:assembleOssDebug` compiles and UI test tags exist for home/library sync buttons.</verify>
  <done>Users can discover sync from normal EPUB book entries on both home and library screens.</done>
</task>

<task type="auto">
  <name>Task 2: Implement audio sync wizard bottom sheet</name>
  <files>app/src/main/java/com/aryan/reader/audio/AudioSyncSheet.kt, app/src/main/java/com/aryan/reader/MainViewModel.kt, app/src/main/res/values/strings.xml</files>
  <action>Create `AudioSyncSheet` with: selected book title/cover, audio file picker using `ActivityResultContracts.OpenMultipleDocuments` with audio/zip MIME types, selected audio list with remove actions, local Whisper model status/import CTA, optional API fallback checkboxes for configured OpenAI/Deepgram keys (hidden/disabled in offline builds), Start/Cancel/Retry controls, progress step text, error details, warnings, and Open synced EPUB action when complete. Wire it to the ViewModel methods from Plan 06. Do not ask users to deploy or host anything.</action>
  <verify>Unit/Compose tests can open the sheet, select fake audio URIs via test hook/fake launcher, and see Start enabled only when book + audio + provider prerequisites are satisfied.</verify>
  <done>Sync UI can create/start/cancel/retry sessions and show completed output state.</done>
</task>

<task type="auto">
  <name>Task 3: Add UI regression coverage</name>
  <files>app/src/androidTest/java/com/aryan/reader/AudioSyncUiTest.kt</files>
  <action>Add Android Compose tests for: home EPUB card shows sync action; non-EPUB card does not show an active sync action; library EPUB row shows sync action; sheet displays local provider first; API fallback options appear only when keys are configured and build is not offline; active session progress renders; completed session renders Open synced EPUB. Use fake ViewModel/session state where existing test patterns allow; avoid real transcription.</action>
  <verify>`./gradlew.bat :app:connectedOssDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aryan.reader.AudioSyncUiTest` when emulator/device is available; otherwise `./gradlew.bat :app:assembleOssDebug` must compile test sources.</verify>
  <done>Core sync UI states are covered without running the expensive sync pipeline.</done>
</task>

</tasks>

<verification>
Run `./gradlew.bat :app:assembleOssDebug`; run the targeted connected UI test when a device/emulator is available.
</verification>

<success_criteria>
Users can start and monitor audio sync directly from EPUB book entries, load audio files, choose local/API backup behavior, and open the completed synced EPUB.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-07-SUMMARY.md`
</output>
