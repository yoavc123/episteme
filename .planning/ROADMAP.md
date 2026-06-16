# Roadmap

### Phase 1: Audio Ebook Sync

**Goal:** A local-first audiobook/ebook synchronization flow that lets users load audio files, align them with EPUB books, save a synced EPUB3 readaloud file, and use synced audio from the reader TTS popup.

**Requirements:** [AES-01, AES-02, AES-03, AES-04, AES-05, AES-06]

- `AES-01` — Users can choose audio files for a normal EPUB book from the library/home UI without treating audio files as readable books.
- `AES-02` — Sync defaults to on-device transcription/alignment and offers direct external API backup providers without requiring app-hosted infrastructure.
- `AES-03` — The app aligns transcript timestamps to EPUB text at sentence granularity with recoverable gaps and deterministic tests.
- `AES-04` — The app writes a valid EPUB3 output containing sentence ids, audio assets, SMIL Media Overlays, and OPF wiring.
- `AES-05` — The reader TTS popup exposes synced audio/readaloud as a selectable option when a synced EPUB exists or as a CTA when it does not.
- `AES-06` — Users can see sync status, progress, failures, cancellation, and the produced synced EPUB in the library.

**Plans:** 10 plans

Plans:
- [x] 01-audio-ebook-sync-01-PLAN.md — Add sync persistence and audio-source import foundation
- [x] 01-audio-ebook-sync-02-PLAN.md — Build EPUB3 sentence markup and Media Overlay writer
- [x] 01-audio-ebook-sync-03-PLAN.md — Add direct external transcription backup providers
- [ ] 01-audio-ebook-sync-04-PLAN.md — Integrate on-device Whisper transcription provider (partial seam; real whisper.cpp still needed)
- [x] 01-audio-ebook-sync-05-PLAN.md — Implement tested transcript-to-EPUB alignment engine
- [x] 01-audio-ebook-sync-06-PLAN.md — Orchestrate background sync pipeline and output import
- [ ] 01-audio-ebook-sync-07-PLAN.md — Add book-entry sync buttons and sync wizard UI
- [ ] 01-audio-ebook-sync-08-PLAN.md — Parse and play EPUB3 Media Overlays in the reader
- [ ] 01-audio-ebook-sync-09-PLAN.md — Add synced audio option to reader TTS popup
- [ ] 01-audio-ebook-sync-10-PLAN.md — Human verify full local/API sync flow

Execution note: Plans 01-03 and 05 are verified; plan 06 is implemented with automated verification skipped by user request. Plan 04 remains partial until real whisper.cpp ASR replaces the native stub.
