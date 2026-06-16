package com.aryan.reader.shared.reader

import java.io.File

data class SharedEpubMediaOverlayAudioFile(
    val id: String,
    val fileName: String,
    val mediaType: String,
    val file: File
)

data class SharedEpubMediaOverlayClip(
    val contentHref: String,
    val sentenceId: String,
    val audioFileId: String,
    val clipBeginSeconds: Double,
    val clipEndSeconds: Double
)

data class SharedEpubMediaOverlayRequest(
    val audioFiles: List<SharedEpubMediaOverlayAudioFile>,
    val clips: List<SharedEpubMediaOverlayClip>
)

data class SharedEpubMediaOverlayContentSentences(
    val contentHref: String,
    val sentences: List<SharedEpubMediaOverlaySentence>
)

data class SharedEpubMediaOverlaySentence(
    val id: String,
    val text: String
)

data class SharedEpubSentenceMarkupResult(
    val xhtml: String,
    val sentences: List<SharedEpubMediaOverlaySentence>
)

data class SharedEpubMediaOverlayWriteResult(
    val opfPath: String,
    val smilPaths: List<String>,
    val audioPaths: List<String>,
    val markedSentences: Map<String, List<SharedEpubMediaOverlaySentence>>
)
