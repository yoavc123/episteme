---
phase: 01-audio-ebook-sync
plan: 03
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/aryan/reader/Common.kt
  - app/src/main/java/com/aryan/reader/AiSettingsScreen.kt
  - app/src/main/java/com/aryan/reader/audio/AudioTranscriptionProvider.kt
  - app/src/main/java/com/aryan/reader/audio/OpenAiAudioTranscriptionProvider.kt
  - app/src/main/java/com/aryan/reader/audio/DeepgramAudioTranscriptionProvider.kt
  - app/src/test/java/com/aryan/reader/audio/ExternalTranscriptionProviderTest.kt
  - app/src/main/res/values/strings.xml
autonomous: true
requirements: [AES-02]
user_setup:
  - service: openai
    why: "Optional backup transcription provider when local transcription is unavailable or fails"
    env_vars:
      - name: OPENAI_API_KEY
        source: "User enters key in Episteme AI settings; app stores encrypted in existing BYOK preferences"
  - service: deepgram
    why: "Optional backup transcription provider with word timestamps and large prerecorded audio support"
    env_vars:
      - name: DEEPGRAM_API_KEY
        source: "User enters key in Episteme AI settings; app stores encrypted in existing BYOK preferences"
must_haves:
  truths:
    - "Users can save OpenAI and Deepgram keys locally without an Episteme-hosted server"
    - "External transcription providers return normalized word timestamps"
    - "Offline builds do not expose network backup providers"
  artifacts:
    - path: "app/src/main/java/com/aryan/reader/audio/AudioTranscriptionProvider.kt"
      provides: "Provider interface and transcript word/segment models shared by local and API providers"
    - path: "app/src/main/java/com/aryan/reader/audio/OpenAiAudioTranscriptionProvider.kt"
      provides: "OpenAI Whisper direct API backup using verbose_json word timestamps"
    - path: "app/src/main/java/com/aryan/reader/audio/DeepgramAudioTranscriptionProvider.kt"
      provides: "Deepgram prerecorded direct API backup using word timestamps"
    - path: "app/src/main/java/com/aryan/reader/AiSettingsScreen.kt"
      provides: "BYOK UI for transcription backup keys"
  key_links:
    - from: "AiSettingsScreen.kt"
      to: "Common.kt"
      via: "encrypted BYOK settings load/save"
      pattern: "saveAiByokKey"
    - from: "OpenAiAudioTranscriptionProvider.kt"
      to: "AudioTranscriptionProvider.kt"
      via: "normalized TranscriptWord list"
      pattern: "TranscriptWord"
---

<objective>
Add no-hosting external transcription backup providers behind a common provider contract.

Purpose: Local transcription is primary, but users need direct API backups when local models are missing, too slow, or fail.
Output: BYOK key settings for transcription providers, provider interface, OpenAI and Deepgram clients, and parser tests.
</objective>

<execution_context>
@C:/Users/yoavc/.config/opencode/get-shit-done/workflows/execute-plan.md
@C:/Users/yoavc/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-DISCOVERY.md
@app/src/main/java/com/aryan/reader/Common.kt
@app/src/main/java/com/aryan/reader/AiSettingsScreen.kt
@app/build.gradle.kts
</context>

<tasks>

<task type="auto">
  <name>Task 1: Extend BYOK settings for transcription backups</name>
  <files>app/src/main/java/com/aryan/reader/Common.kt, app/src/main/java/com/aryan/reader/AiSettingsScreen.kt, app/src/main/res/values/strings.xml</files>
  <action>Extend existing encrypted BYOK settings to include `openai` and `deepgram` provider keys. Reuse existing encryption helpers and masked key display. Add UI rows in `AiSettingsScreen` under a new "Transcription backup providers" section. Gate these options with `!BuildConfig.IS_OFFLINE`; offline builds should show explanatory disabled text or hide provider entry points, not attempt network calls. Do not add app-hosted worker URLs or hardcoded secrets.</action>
  <verify>`./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.AndroidSettingsHubModelsTest"` still passes, and manual inspection confirms keys save/delete like existing Gemini/Groq keys.</verify>
  <done>OpenAI/Deepgram keys can be saved, masked, and deleted using the same encrypted BYOK mechanism as existing AI keys.</done>
</task>

<task type="auto">
  <name>Task 2: Implement provider contract and direct API clients</name>
  <files>app/src/main/java/com/aryan/reader/audio/AudioTranscriptionProvider.kt, app/src/main/java/com/aryan/reader/audio/OpenAiAudioTranscriptionProvider.kt, app/src/main/java/com/aryan/reader/audio/DeepgramAudioTranscriptionProvider.kt</files>
  <action>Create `AudioTranscriptionProvider` with a suspending `transcribe(request, progress)` API returning normalized `TranscriptResult` containing words with absolute seconds, segments, source track id, provider id, confidence when available, and recoverable warnings. Implement OpenAI direct multipart upload using `model=whisper-1`, `response_format=verbose_json`, and `timestamp_granularities[]=word`; chunk audio files over 25 MB and offset timestamps by chunk start. Implement Deepgram direct upload to `/v1/listen?model=nova-3&smart_format=true` and parse `results.channels[].alternatives[].words`. Use `HttpURLConnection` or existing lightweight network patterns; do not add a heavy SDK unless tests prove it is necessary.</action>
  <verify>Parser unit tests pass with fixture JSON for OpenAI and Deepgram responses, including timestamp offset behavior.</verify>
  <done>Both API providers convert provider responses into the same `TranscriptResult` shape used by alignment, without requiring self-hosting.</done>
</task>

<task type="auto">
  <name>Task 3: Test external provider parsing and offline gating</name>
  <files>app/src/test/java/com/aryan/reader/audio/ExternalTranscriptionProviderTest.kt</files>
  <action>Add unit tests for OpenAI verbose_json word parsing, Deepgram word parsing, empty/malformed responses, file-size chunk timestamp offsets, missing API key errors, and offline-build provider gating helper. Use local JSON strings and fake HTTP responders; do not call real APIs in tests.</action>
  <verify>`./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.ExternalTranscriptionProviderTest"`</verify>
  <done>API parsing/failure behavior is deterministic and covered without network access.</done>
</task>

</tasks>

<verification>
Run `./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.ExternalTranscriptionProviderTest"` and a broader `./gradlew.bat :app:testOssDebugUnitTest` if BYOK settings tests are affected.
</verification>

<success_criteria>
Users can configure OpenAI/Deepgram as direct backup transcription providers, and both clients produce normalized word timestamps without app-hosted infrastructure.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-03-SUMMARY.md`
</output>
