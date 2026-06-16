package com.aryan.reader.shared

data class AudioBookSentence(
    val id: String,
    val text: String
)

data class AudioBookTranscriptWord(
    val text: String,
    val startSeconds: Double,
    val endSeconds: Double
)

data class AudioBookTranscriptTrack(
    val sourceId: String,
    val offsetSeconds: Double = 0.0,
    val words: List<AudioBookTranscriptWord>
)

enum class AudioBookAlignmentConfidence {
    MATCHED,
    WEAK_MATCH,
    INTERPOLATED,
    MISSING
}

data class AudioBookAlignmentEntry(
    val sentenceId: String,
    val text: String,
    val audioSourceId: String?,
    val clipBegin: Double,
    val clipEnd: Double,
    val confidence: AudioBookAlignmentConfidence,
    val warnings: List<String> = emptyList()
)

data class AudioBookAlignmentResult(
    val entries: List<AudioBookAlignmentEntry>
)
