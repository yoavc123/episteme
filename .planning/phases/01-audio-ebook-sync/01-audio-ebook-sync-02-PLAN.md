---
phase: 01-audio-ebook-sync
plan: 02
type: tdd
wave: 1
depends_on: []
files_modified:
  - shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayModels.kt
  - shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubSentenceMarkup.kt
  - shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayWriter.kt
  - shared/src/desktopTest/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayWriterTest.kt
autonomous: true
requirements: [AES-04]
must_haves:
  truths:
    - "A synced EPUB output contains sentence-level ids in XHTML content"
    - "A synced EPUB output contains SMIL overlays that point to sentence ids and audio clip ranges"
    - "A synced EPUB output remains a valid EPUB zip with `mimetype` first and stored"
  artifacts:
    - path: "shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayWriter.kt"
      provides: "EPUB3 writer that copies source EPUB, injects audio/smil assets, and updates OPF"
    - path: "shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubSentenceMarkup.kt"
      provides: "XHTML sentence span/id injection without breaking existing markup"
    - path: "shared/src/desktopTest/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayWriterTest.kt"
      provides: "Regression tests for valid Media Overlay output"
  key_links:
    - from: "SharedEpubMediaOverlayWriter.kt"
      to: "SharedEpubSentenceMarkup.kt"
      via: "writer uses generated sentence ids as SMIL text references"
      pattern: "sentenceId"
    - from: "content.opf"
      to: "*.smil"
      via: "manifest item media-overlay attributes"
      pattern: "media-overlay"
---

<objective>
Build the tested shared EPUB3 Media Overlay writer.

Purpose: The app must preserve a synced file as a standards-compatible EPUB3 readaloud, not an app-only sidecar.
Output: Shared JVM EPUB writer, sentence markup helper, data models, and tests.
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
@shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubMetadataEditor.kt
@shared/src/desktopTest/kotlin/com/aryan/reader/shared/reader/SharedEpubMetadataEditorTest.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: RED tests for EPUB3 Media Overlay output</name>
  <files>shared/src/desktopTest/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayWriterTest.kt</files>
  <action>Write failing tests that build a minimal EPUB with `mimetype`, `META-INF/container.xml`, `OPS/content.opf`, and two XHTML spine docs. Assert the writer: keeps `mimetype` first/stored, wraps sentences in stable `span id="episteme-sync-s..."`, preserves nested `em`/`strong`/`a` markup, skips `script`/`style`, adds audio under `OPS/episteme-sync/audio/`, adds SMIL under `OPS/episteme-sync/smil/`, updates OPF manifest with `application/smil+xml`, audio media types, `media-overlay`, and duration metadata.</action>
  <verify>`./gradlew.bat :shared:desktopTest --tests "com.aryan.reader.shared.reader.SharedEpubMediaOverlayWriterTest"` fails before implementation for missing writer APIs.</verify>
  <done>Tests describe exact EPUB3 output structure and fail for the expected missing implementation.</done>
</task>

<task type="auto">
  <name>Task 2: GREEN implementation of sentence markup and EPUB writer</name>
  <files>shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayModels.kt, shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubSentenceMarkup.kt, shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedEpubMediaOverlayWriter.kt</files>
  <action>Implement the models and writer needed by the tests. Reuse the safe ZIP patterns from `SharedEpubMetadataEditor`: preserve `mimetype`, copy all existing entries, update only targeted XHTML and OPF entries, and write new assets deflated except `mimetype`. Sentence markup must be deterministic: generate ids from spine index + sentence index, keep existing element ids intact, and output a map from sentence model to XHTML href/id for SMIL generation. SMIL clips must use `clipBegin`/`clipEnd` seconds with 3 decimal precision and text refs like `chapter.xhtml#episteme-sync-s0001`.</action>
  <verify>`./gradlew.bat :shared:desktopTest --tests "com.aryan.reader.shared.reader.SharedEpubMediaOverlayWriterTest"` passes.</verify>
  <done>Minimal EPUB fixtures are rewritten into valid EPUB3 Media Overlay files matching test assertions.</done>
</task>

</tasks>

<verification>
Run `./gradlew.bat :shared:desktopTest --tests "com.aryan.reader.shared.reader.SharedEpubMediaOverlayWriterTest"`.
</verification>

<success_criteria>
Given a source EPUB, audio files, and sentence clip timings, shared JVM code produces a valid EPUB3 readaloud file with sentence ids, SMIL overlays, audio assets, and OPF wiring.
</success_criteria>

<output>
After completion, create `.planning/phases/01-audio-ebook-sync/01-audio-ebook-sync-02-SUMMARY.md`
</output>
