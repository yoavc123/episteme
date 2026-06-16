---
phase: 01-audio-ebook-sync
plan: 04
type: execute
wave: 2
depends_on: [01-audio-ebook-sync-03]
files_modified:
  - app/build.gradle.kts
  - app/src/main/cpp/CMakeLists.txt
  - app/src/main/cpp/whisper_jni_bridge.cpp
  - app/src/main/cpp/third_party/whisper.cpp/**
  - app/src/main/java/com/aryan/reader/audio/WhisperLocalTranscriptionProvider.kt
  - app/src/main/java/com/aryan/reader/audio/WhisperModelManager.kt
  - app/src/main/java/com/aryan/reader/audio/AudioDecodeUtils.kt
  - app/src/test/java/com/aryan/reader/audio/WhisperModelManagerTest.kt
autonomous: true
requirements: [AES-02]
must_haves:
  truths:
    - "Local transcription is the default provider when a compatible Whisper model is installed"
    - "The app can run transcription without network access"
    - "Missing or invalid local models fail with actionable errors and do not crash"
  artifacts:
    - path: "app/src/main/java/com/aryan/reader/audio/WhisperLocalTranscriptionProvider.kt"
      provides: "Local on-device transcription provider implementing AudioTranscriptionProvider"
    - path: "app/src/main/java/com/aryan/reader/audio/WhisperModelManager.kt"
      provides: "Local model discovery/import/download state"
    - path: "app/src/main/cpp/whisper_jni_bridge.cpp"
      provides: "JNI bridge from Kotlin to whisper.cpp"
    - path: "app/src/main/cpp/CMakeLists.txt"
      provides: "Native build wiring for whisper.cpp"
  key_links:
    - from: "WhisperLocalTranscriptionProvider.kt"
      to: "AudioTranscriptionProvider.kt"
      via: "implements common provider interface"
      pattern: "AudioTranscriptionProvider"
    - from: "whisper_jni_bridge.cpp"
      to: "app/src/main/cpp/CMakeLists.txt"
      via: "native library target linked into Android app"
      pattern: "whisper"
---

<objective>
Integrate local on-device Whisper transcription behind the provider interface.

Purpose: The locked user decision requires local/on-device transcription as the primary path, with external APIs only as backups.
Output: Whisper.cpp native bridge, model manager, audio decode helper, and local provider implementation.
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
@app/src/main/cpp/CMakeLists.txt
@app/build.gradle.kts
@app/src/main/java/com/aryan/reader/audio/AudioTranscriptionProvider.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Wire whisper.cpp into the Android native build</name>
  <files>app/build.gradle.kts, app/src/main/cpp/CMakeLists.txt, app/src/main/cpp/whisper_jni_bridge.cpp, app/src/main/cpp/third_party/whisper.cpp/**</files>
  <action>Use the official whisper.cpp Android sample as the implementation reference. Vendor the minimal whisper.cpp source needed for Android under `app/src/main/cpp/third_party/whisper.cpp/` and wire it through the existing `CMakeLists.txt`. Add `whisper_jni_bridge.cpp` exposing native methods to load/free a model and transcribe 16 kHz mono PCM buffers with segment/word timestamps when available. Keep the native target isolated from existing MOBI/Woff code. Do not bundle `.bin` model files into the APK.</action>
  <verify>`./gradlew.bat :app:assembleOssDebug` compiles native code on Windows host with existing Android CMake setup.</verify>
  <done>Android build produces a native library exposing local Whisper transcription JNI methods without bundled model assets.</done>
</task>

<task type="auto">
  <name>Task 2: Implement model manager and audio decoding helpers</name>
  <files>app/src/main/java/com/aryan/reader/audio/WhisperModelManager.kt, app/src/main/java/com/aryan/reader/audio/AudioDecodeUtils.kt, app/src/test/java/com/aryan/reader/audio/WhisperModelManagerTest.kt</files>
  <action>Create a model manager that stores user-imported Whisper ggml model files under `filesDir/audio_sync/models/`, validates extension/size/header enough to avoid accidental non-model files, records selected model path in encrypted or private preferences, and exposes state for UI. Add an `AudioDecodeUtils` helper that uses Android MediaExtractor/MediaCodec on device to decode supported audio files to 16 kHz mono PCM chunks for Whisper; in tests, keep decode methods injectable/fakeable. Offline builds must allow importing a local model file and must not require download.</action>
  <verify>`./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.WhisperModelManagerTest"`</verify>
  <done>Local model selection/import state is test-covered and audio decoding can feed PCM chunks to the provider.</done>
</task>

<task type="auto">
  <name>Task 3: Implement local provider and graceful fallback errors</name>
  <files>app/src/main/java/com/aryan/reader/audio/WhisperLocalTranscriptionProvider.kt</files>
  <action>Implement `WhisperLocalTranscriptionProvider : AudioTranscriptionProvider`. It should check model availability, decode each source track/chunk, call the JNI bridge, normalize segment/word timestamps to absolute seconds, report progress, and return structured errors for missing model, native load failure, decode failure, or cancellation. Do not silently call network providers here; fallback choice belongs to the orchestrator plan.</action>
  <verify>`./gradlew.bat :app:assembleOssDebug` and local provider unit tests with faked native bridge pass.</verify>
  <done>Local transcription is callable through the common provider interface and fails safely when prerequisites are missing.</done>
</task>

</tasks>

<verification>
Run `./gradlew.bat :app:testOssDebugUnitTest --tests "com.aryan.reader.audio.WhisperModelManagerTest"` and `./gradlew.bat :app:assembleOssDebug`.
</verification>

<success_criteria>
The app has a local-first transcription provider using on-device Whisper, with user-managed models and no network dependency.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-04-SUMMARY.md`
</output>
