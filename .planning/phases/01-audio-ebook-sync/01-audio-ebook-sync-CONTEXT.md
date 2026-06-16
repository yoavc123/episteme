# Phase Context: Audio Ebook Sync

## User Request

Add syncing of audio files to ebooks. The app should allow loading audio files, show a sync button on each normal book entry, sync the book with audio, and keep the synced file as an EPUB3 file like Storyteller. The sync/readaloud option must show up in the reader TTS popup.

## Locked Decisions

- Build on-device alignment/transcription as the primary path.
- Use APIs to existing external services as backup options to running locally.
- Do not require self-hosting or an app-hosted service.
- Store the synced output as an EPUB3 file.
- Expose sync from book entries and the TTS popup.

## Scope Boundaries

- The first implementation targets EPUB source books. Non-EPUB book types can show disabled/explanatory sync UI until converted to EPUB in a later phase.
- Audio files should be selected for sync workflows and stored as sources/cache; they should not become normal readable library entries.
- External API providers must be BYOK/direct-to-provider and disabled in offline builds.
