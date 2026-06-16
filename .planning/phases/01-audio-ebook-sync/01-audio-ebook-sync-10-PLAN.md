---
phase: 01-audio-ebook-sync
plan: 10
type: execute
wave: 6
depends_on: [01-audio-ebook-sync-09]
files_modified: []
autonomous: false
requirements: [AES-01, AES-02, AES-03, AES-04, AES-05, AES-06]
must_haves:
  truths:
    - "A user can sync an EPUB with local audio and receive an EPUB3 output"
    - "A user can use API backup transcription without hosting anything"
    - "A user can start synced audio from the reader TTS popup"
  artifacts: []
  key_links: []
---

<objective>
Verify the complete audio ebook sync flow with real user-visible behavior.

Purpose: Audio sync is an interactive, long-running, media-heavy feature that needs human verification beyond unit tests.
Output: Human approval or issue list for gap closure.
</objective>

<execution_context>
@C:/Users/yoavc/.config/opencode/get-shit-done/workflows/execute-plan.md
@C:/Users/yoavc/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-09-SUMMARY.md
</context>

<tasks>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 1: Human verify local and API-backed synced EPUB flow</name>
  <files>None</files>
  <action>Pause execution and ask the user to perform the verification steps below against the completed Android build. Do not change code in this checkpoint plan; collect approval or exact observed issues for gap-closure planning.</action>
  <what-built>Complete local-first audio ebook sync: book-entry sync UI, audio file loading, local Whisper transcription, optional OpenAI/Deepgram backup APIs, EPUB3 Media Overlay output, reader playback, and TTS popup synced audio option.</what-built>
  <how-to-verify>
    1. Install/run the OSS debug build on an Android device or emulator with an EPUB test book and matching short audio file.
    2. On Home or Library, confirm an EPUB book entry shows "Sync audio".
    3. Tap "Sync audio", choose one or more audio files, choose Local Whisper, and start sync. If no local model is installed, import/select a tiny/base Whisper model when prompted.
    4. Confirm progress moves through preparing, transcribing, aligning, writing EPUB, and completed.
    5. Confirm a synced EPUB/readaloud output appears/open action is available and the generated file opens as EPUB.
    6. Open the reader TTS popup for the synced EPUB and confirm a synced audio/readaloud option appears.
    7. Start synced audio and confirm audio plays while the reader advances/highlights the matching text.
    8. Optional backup check: configure an OpenAI or Deepgram key in AI settings, run sync with API fallback enabled, and confirm no hosting/deployment is required.
  </how-to-verify>
  <verify>User completes the listed manual verification steps and reports "approved" or a concrete issue list.</verify>
  <done>User confirms the local sync flow, optional direct API backup flow, generated EPUB3 output, and TTS popup synced audio playback all work as expected.</done>
  <resume-signal>Type "approved" if the flow works, or describe exact issues observed.</resume-signal>
</task>

</tasks>

<verification>
Human verification is the blocking check. If issues are reported, create gap-closure plans from the failed observable truths.
</verification>

<success_criteria>
Human confirms the user can create and use a synced EPUB3 readaloud file from local audio, and can optionally use direct API backup providers without hosting.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-10-SUMMARY.md`
</output>
