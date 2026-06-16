---
phase: 01-audio-ebook-sync
plan: 05
type: tdd
wave: 2
depends_on: [01-audio-ebook-sync-02, 01-audio-ebook-sync-03]
files_modified:
  - shared/src/commonMain/kotlin/com/aryan/reader/shared/AudioBookAlignmentModels.kt
  - shared/src/commonMain/kotlin/com/aryan/reader/shared/AudioBookTextNormalizer.kt
  - shared/src/commonMain/kotlin/com/aryan/reader/shared/AudioBookAlignmentEngine.kt
  - shared/src/commonTest/kotlin/com/aryan/reader/shared/AudioBookAlignmentEngineTest.kt
autonomous: true
requirements: [AES-03, AES-04]
must_haves:
  truths:
    - "Transcript words can be aligned to EPUB sentences deterministically"
    - "Missing or low-confidence transcript gaps are interpolated or flagged instead of crashing"
    - "Alignment results provide clip timings consumable by the EPUB3 writer"
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/aryan/reader/shared/AudioBookAlignmentEngine.kt"
      provides: "Windowed fuzzy alignment from EPUB sentences to transcript word timestamps"
    - path: "shared/src/commonMain/kotlin/com/aryan/reader/shared/AudioBookTextNormalizer.kt"
      provides: "Shared normalization for punctuation/case/whitespace/quotes"
    - path: "shared/src/commonTest/kotlin/com/aryan/reader/shared/AudioBookAlignmentEngineTest.kt"
      provides: "Deterministic alignment regression coverage"
  key_links:
    - from: "AudioBookAlignmentEngine.kt"
      to: "SharedEpubMediaOverlayModels.kt"
      via: "alignment output fields match writer clip model"
      pattern: "clipBegin"
    - from: "AudioTranscriptionProvider.kt"
      to: "AudioBookAlignmentEngine.kt"
      via: "TranscriptWord-compatible test fixtures"
      pattern: "TranscriptWord"
---

<objective>
Implement a tested shared alignment engine that maps timestamped transcripts to EPUB sentences.

Purpose: Transcription alone does not create readaloud EPUBs; the app needs deterministic forced alignment logic similar to Storyteller's chapter/sentence matching.
Output: Shared alignment models, normalization, windowed fuzzy matching, gap interpolation, and tests.
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
@shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayModels.kt
@app/src/main/java/com/aryan/reader/audio/AudioTranscriptionProvider.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: RED tests for alignment behavior</name>
  <files>shared/src/commonTest/kotlin/com/aryan/reader/shared/AudioBookAlignmentEngineTest.kt</files>
  <action>Write failing tests for: exact sentence-to-word alignment; punctuation/case/curly quote normalization; skipped intro audio before first chapter; transcript with one omitted sentence; interpolation between two matched sentences; three consecutive misses moving the search window; confidence flags for interpolated/weak matches; multi-track absolute timestamp offsets. Use small in-memory sentence/word fixtures and assert exact clip begin/end values.</action>
  <verify>`./gradlew.bat :shared:allTests --tests "com.aryan.reader.shared.AudioBookAlignmentEngineTest"` fails before implementation for missing engine APIs.</verify>
  <done>Tests encode the alignment contract in simple input/output examples.</done>
</task>

<task type="auto">
  <name>Task 2: GREEN alignment engine implementation</name>
  <files>shared/src/commonMain/kotlin/com/aryan/reader/shared/AudioBookAlignmentModels.kt, shared/src/commonMain/kotlin/com/aryan/reader/shared/AudioBookTextNormalizer.kt, shared/src/commonMain/kotlin/com/aryan/reader/shared/AudioBookAlignmentEngine.kt</files>
  <action>Implement normalization and windowed fuzzy matching. Use a bounded Levenshtein or token-similarity score over normalized sentence text and transcript word windows. For each sentence, search near the prior match; if no match crosses threshold, mark it missing and continue; after three consecutive misses, advance the transcript window. Interpolate timings for short missing runs between matched neighbors and mark confidence as `INTERPOLATED`. Return alignment entries with sentence id/text, audio source id, clipBegin, clipEnd, confidence, and warnings consumable by the media overlay writer.</action>
  <verify>`./gradlew.bat :shared:allTests --tests "com.aryan.reader.shared.AudioBookAlignmentEngineTest"` passes.</verify>
  <done>All alignment fixtures pass and output models can be fed directly to the EPUB3 writer.</done>
</task>

</tasks>

<verification>
Run `./gradlew.bat :shared:allTests --tests "com.aryan.reader.shared.AudioBookAlignmentEngineTest"`.
</verification>

<success_criteria>
Given EPUB sentences and timestamped transcript words, shared code returns deterministic sentence clip timings with confidence/warning metadata.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-05-SUMMARY.md`
</output>
