package com.aryan.reader.audio

import android.net.Uri

interface AudioTranscriptionProvider {
    val id: String

    suspend fun transcribe(
        request: AudioTranscriptionRequest,
        progress: (TranscriptionProgress) -> Unit = {}
    ): TranscriptionResult
}

data class AudioTranscriptionRequest(
    val sources: List<AudioSource>,
    val language: String? = null
)

data class AudioSource(
    val uri: Uri,
    val displayName: String? = null
)

data class TranscriptionProgress(
    val sourceIndex: Int,
    val sourceCount: Int,
    val processedChunks: Int,
    val message: String
)

sealed interface TranscriptionResult {
    data class Success(val segments: List<TranscriptSegment>) : TranscriptionResult
    data class Failure(val error: TranscriptionError) : TranscriptionResult
}

data class TranscriptSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val text: String,
    val words: List<TranscriptWord> = emptyList()
)

data class TranscriptWord(
    val startSeconds: Double,
    val endSeconds: Double,
    val word: String
)

sealed class TranscriptionError(open val userMessage: String, open val cause: Throwable? = null) {
    data class MissingLocalModel(
        override val userMessage: String = "Install or import a compatible Whisper ggml model to use local transcription."
    ) : TranscriptionError(userMessage)

    data class NativeLoadFailed(
        override val userMessage: String,
        override val cause: Throwable? = null
    ) : TranscriptionError(userMessage, cause)

    data class DecodeFailed(
        override val userMessage: String,
        override val cause: Throwable? = null
    ) : TranscriptionError(userMessage, cause)

    data class Cancelled(
        override val userMessage: String = "Local transcription was cancelled."
    ) : TranscriptionError(userMessage)

    data class MissingApiKey(
        override val userMessage: String
    ) : TranscriptionError(userMessage)

    data class ExternalServiceFailed(
        override val userMessage: String,
        override val cause: Throwable? = null
    ) : TranscriptionError(userMessage, cause)

    data class Unknown(
        override val userMessage: String,
        override val cause: Throwable? = null
    ) : TranscriptionError(userMessage, cause)
}
