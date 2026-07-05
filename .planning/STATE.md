# Planning State

## Current Phase

- `01-audio-ebook-sync`

## Decisions

- Build on-device/local transcription and alignment as the primary path.
- Add direct external API backup options to existing services, with no self-hosted server required.
- API backup options must use user-provided keys in-app (BYOK), not hardcoded secrets and not an Episteme-hosted relay.
- Keep output as an EPUB3 file with Media Overlays/readaloud assets.
- Add sync UI on normal book entries.
- Add synced audio/readaloud as an option in the reader TTS popup.

## Deferred Ideas

- None.

## Claude's Discretion

- Exact local ASR engine implementation details, provided the implementation is local-first and does not depend on a hosted app backend.
- Exact wording and placement of explanatory UI, provided the sync button and TTS popup option are discoverable.
- Exact API backup provider order, provided local transcription remains the default.

## Blockers

- Plan 10 is a blocking human/device verification checkpoint. It requires an Android device/emulator, a test EPUB, matching short audio, and a user-imported Whisper model; optional API backup verification requires a configured OpenAI or Deepgram key.
- Visual QA for the new Compose surfaces cannot be completed objectively without a rendered Android capture from a device/emulator.

## Latest Execution Note

- 2026-06-24: Continued execution through plan 10 readiness. Completed real whisper.cpp native integration, sync UI strings/output-open compile fixes, media overlay parser/playback wiring, and TTS popup synced playback wiring. Validated one command at a time: `EpubMediaOverlayParserTest`, `SyncedAudioTtsOptionModelTest`, `TtsModePolicyTest`, `AndroidStringFormatResourcesTest`, `externalNativeBuildOssDebug`, `WhisperModelManagerTest`, `WhisperLocalTranscriptionProviderTest`, and `assembleOssDebug`. Current work is at plan 10 human verification.
- 2026-06-16: Implemented wave 5 TTS popup foundation. Added synced-audio CTA/play action state, guarded `SYNCED` mode from becoming saved synthetic TTS default, added tests/translations, and validated affected tests one class at a time plus `assembleOssDebug`. `MainViewModelTest` skipped at user request. Actual media-overlay playback remains dependent on plan 08.
- 2026-06-14: Implemented wave 3 plan 06 orchestration. Added provider selection, sync orchestrator, WorkManager worker, ViewModel controls, output registration into recent files, and focused tests. Automated verification skipped at user request.
- 2026-06-14: Continued wave 2. Completed plan 05 alignment engine and fixed scoring defects found by targeted tests. Improved plan 04 audio decoding to use MediaCodec decoded PCM with 16 kHz mono resampling, but plan 04 remains partial because native Whisper is still a placeholder stub.
- 2026-06-14: Completed and verified plans 01-03. Added audio sync persistence/importer, EPUB3 Media Overlay writer, OpenAI/Deepgram direct BYOK transcription backups, and focused tests. Also fixed two stale shared test references that blocked `:shared:desktopTest` compilation.
- 2026-06-13: Continued requested waves 2-6 briefly. Wave 2 partial scaffolding landed; waves 3-6 documented as dependency-blocked. See `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-WAVES-2-6-NOTES.md`.
