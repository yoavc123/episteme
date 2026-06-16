---
phase: 01-audio-ebook-sync
plan: 09
type: execute
wave: 5
depends_on: [01-audio-ebook-sync-07, 01-audio-ebook-sync-08]
files_modified:
  - app/src/main/java/com/aryan/reader/tts/TtsPlaybackManager.kt
  - app/src/main/java/com/aryan/reader/Common.kt
  - app/src/main/java/com/aryan/reader/epubreader/EpubReaderScreen.kt
  - app/src/main/res/values/strings.xml
  - app/src/androidTest/java/com/aryan/reader/epubreader/EpubReaderScreenTest.kt
autonomous: true
requirements: [AES-05, AES-06]
must_haves:
  truths:
    - "The reader TTS popup shows a synced audio option for EPUB books"
    - "When a synced EPUB/readaloud exists, selecting synced audio starts media-overlay playback instead of synthetic TTS"
    - "When no synced EPUB exists, the TTS popup offers a clear CTA to sync audio"
  artifacts:
    - path: "app/src/main/java/com/aryan/reader/Common.kt"
      provides: "TTS settings sheet synced audio mode/tab/CTA"
    - path: "app/src/main/java/com/aryan/reader/tts/TtsPlaybackManager.kt"
      provides: "Playback mode/state compatibility for synced audio option"
    - path: "app/src/main/java/com/aryan/reader/epubreader/EpubReaderScreen.kt"
      provides: "TTS popup wiring to synced media overlay availability and playback actions"
  key_links:
    - from: "TtsSettingsSheet"
      to: "SyncedAudioPlaybackManager"
      via: "selected synced audio mode starts media overlay playback"
      pattern: "SYNCED|Synced"
    - from: "TtsSettingsSheet"
      to: "AudioSyncSheet"
      via: "CTA opens sync flow when no synced output exists"
      pattern: "on.*Sync"
---

<objective>
Expose synced audiobook/readaloud playback as a reader TTS popup option.

Purpose: The user explicitly requested the sync/readaloud feature to show up in the TTS popup, not only in library UI.
Output: TTS mode UI/state wiring, reader integration, and UI regression tests.
</objective>

<execution_context>
@C:/Users/yoavc/.config/opencode/get-shit-done/workflows/execute-plan.md
@C:/Users/yoavc/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-07-SUMMARY.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-08-SUMMARY.md
@app/src/main/java/com/aryan/reader/Common.kt
@app/src/main/java/com/aryan/reader/tts/TtsPlaybackManager.kt
@app/src/main/java/com/aryan/reader/epubreader/EpubReaderScreen.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add synced audio mode to TTS settings UI</name>
  <files>app/src/main/java/com/aryan/reader/tts/TtsPlaybackManager.kt, app/src/main/java/com/aryan/reader/Common.kt, app/src/main/res/values/strings.xml</files>
  <action>Add a synced audio/readaloud option to `TtsSettingsSheet`. Keep existing Cloud and Device options unchanged. The synced option should appear only in reader contexts where the current file is EPUB or an EPUB3 media overlay is detected. If a synced output exists, show it as selectable and indicate it uses the book's real audio. If no output exists, show a CTA such as "Sync audio for this book" that opens the sync wizard. Do not make synced audio the default unless the user selects it.</action>
  <verify>`./gradlew.bat :app:assembleOssDebug` compiles and existing TTS unit tests still pass.</verify>
  <done>TTS popup contains a discoverable synced audio option without regressing Cloud/Device TTS modes.</done>
</task>

<task type="auto">
  <name>Task 2: Wire TTS popup selection to EPUB reader playback/sync flow</name>
  <files>app/src/main/java/com/aryan/reader/epubreader/EpubReaderScreen.kt</files>
  <action>Pass synced media overlay availability, current sync session state, `onStartSyncedPlayback`, and `onOpenAudioSync` callbacks into `TtsSettingsSheet`. Selecting synced audio should stop any active synthetic TTS session, start `SyncedAudioPlaybackManager`, and close or update the sheet consistently with existing TTS behavior. The CTA should open the same `AudioSyncSheet` used by book entries for the current book.</action>
  <verify>`./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.tts.*"` and `./gradlew.bat :app:assembleOssDebug`.</verify>
  <done>Reader TTS popup can start synced playback or launch sync setup for the current EPUB.</done>
</task>

<task type="auto">
  <name>Task 3: Add TTS popup UI tests</name>
  <files>app/src/androidTest/java/com/aryan/reader/epubreader/EpubReaderScreenTest.kt</files>
  <action>Extend existing EPUB reader UI tests to cover: TTS settings sheet shows synced audio option for a synced EPUB; selecting it invokes synced playback state; unsynced EPUB shows sync CTA; PDF/non-EPUB reader contexts do not show an invalid synced-audio mode. Use fakes/test hooks rather than real audio playback.</action>
  <verify>`./gradlew.bat :app:connectedOssDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.aryan.reader.epubreader.EpubReaderScreenTest` when emulator/device is available; otherwise compile with `./gradlew.bat :app:assembleOssDebug`.</verify>
  <done>Regression coverage proves the TTS popup integration is visible and correctly gated.</done>
</task>

</tasks>

<verification>
Run `./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.tts.*"`, `./gradlew.bat :app:assembleOssDebug`, and targeted connected EPUB reader tests when available.
</verification>

<success_criteria>
The reader TTS popup supports synced audiobook playback as a first-class option and can launch sync setup when needed.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-09-SUMMARY.md`
</output>
