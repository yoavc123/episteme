# Plan 09 Summary

## Implemented

- Added a synced book-audio section to `TtsSettingsSheet`.
- Added a reader-context gate so EPUB readers can show synced audio/readaloud affordances while PDF/settings contexts keep the existing Cloud/Device-only UI.
- Added a CTA state for unsynced EPUBs: "Sync audio for this book".
- Added a playback action state for synced EPUBs: "Play synced audio".
- Added `SYNCED` to the TTS mode enum only as an explicit readaloud mode marker; saved/restored synthetic TTS defaults coerce it back to device TTS so synced audio does not become the default Cloud/Device engine.
- Added pure model coverage for synced-audio TTS option visibility/action states.
- Added Vietnamese translations for new translatable strings.

## Verification

One class at a time, per user request:

- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.AudioSyncOrchestratorTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.AudioSyncRepositoryTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.ExternalTranscriptionProviderTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.WhisperModelManagerTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.WhisperLocalTranscriptionProviderTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.tts.SyncedAudioTtsOptionModelTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.tts.TtsModePolicyTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.tts.TtsChunkNavigationTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.tts.TtsCacheManagerSecurityTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.tts.TtsSpeakerPreferencesTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.TtsReplacementChunkTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.AndroidStringFormatResourcesTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.epubreader.EpubReaderBridgeAndControlsTest"`
- `./gradlew.bat --max-workers=1 :app:testOssDebugUnitTest --tests "com.aryan.reader.epubreader.EpubTtsChunkMatchingTest"`
- `./gradlew.bat --max-workers=1 :shared:desktopTest --tests "com.aryan.reader.shared.AudioBookAlignmentEngineTest"`
- `./gradlew.bat --max-workers=1 :shared:desktopTest --tests "com.aryan.reader.shared.reader.SharedEpubMediaOverlayWriterTest"`
- `./gradlew.bat --max-workers=1 :app:assembleOssDebug`

## Skipped

- `MainViewModelTest` was skipped at user request after it passed in isolation once and full-suite runs hit JVM OOM pressure.

## Follow-up Completed

- Plan 08 now provides parser/playback state, and `EpubReaderScreen` passes real media overlay availability into `TtsSettingsSheet`.
- Selecting the synced audio option now stops synthetic TTS, starts `SyncedAudioPlaybackManager`, closes the sheet, and shows the reader audio controls.

## Remaining Manual Check

- The TTS popup and synced playback path compile and have model coverage, but rendered UI and real playback still require plan 10 device/emulator verification.
