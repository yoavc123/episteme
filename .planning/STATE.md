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

- Local ASR integration currently has a compilable provider/native seam, but the native bridge is a placeholder and must be replaced with real whisper.cpp transcription.
- Waves 3-6 still require pipeline/UI/reader integration work and cannot be marked complete from the current scaffolding alone.
- Further Gradle verification paused at user request; remaining Android/native validation will be done manually in Android Studio.

## Latest Execution Note

- 2026-06-14: Implemented wave 3 plan 06 orchestration. Added provider selection, sync orchestrator, WorkManager worker, ViewModel controls, output registration into recent files, and focused tests. Automated verification skipped at user request.
- 2026-06-14: Continued wave 2. Completed plan 05 alignment engine and fixed scoring defects found by targeted tests. Improved plan 04 audio decoding to use MediaCodec decoded PCM with 16 kHz mono resampling, but plan 04 remains partial because native Whisper is still a placeholder stub.
- 2026-06-14: Completed and verified plans 01-03. Added audio sync persistence/importer, EPUB3 Media Overlay writer, OpenAI/Deepgram direct BYOK transcription backups, and focused tests. Also fixed two stale shared test references that blocked `:shared:desktopTest` compilation.
- 2026-06-13: Continued requested waves 2-6 briefly. Wave 2 partial scaffolding landed; waves 3-6 documented as dependency-blocked. See `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-WAVES-2-6-NOTES.md`.
