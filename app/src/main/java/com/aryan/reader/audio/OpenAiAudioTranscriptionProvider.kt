package com.aryan.reader.audio

import android.content.Context
import android.net.Uri
import org.json.JSONObject

class OpenAiAudioTranscriptionProvider(
    private val context: Context,
    private val apiKey: String,
    private val model: String = OPENAI_AUDIO_SYNC_DEFAULT_MODEL,
    private val httpClient: ExternalTranscriptionHttpClient = UrlConnectionExternalTranscriptionHttpClient()
) : AudioTranscriptionProvider {
    override val id: String = "openai-whisper"

    override suspend fun transcribe(
        request: AudioTranscriptionRequest,
        progress: (TranscriptionProgress) -> Unit
    ): TranscriptionResult {
        if (apiKey.isBlank()) {
            return TranscriptionResult.Failure(TranscriptionError.MissingApiKey("Add an OpenAI API key to use OpenAI transcription backup."))
        }
        return runCatching {
            val segments = request.sources.flatMapIndexed { index, source ->
                progress(TranscriptionProgress(index, request.sources.size, 0, "Uploading ${source.displayName ?: "audio"} to OpenAI"))
                val bytes = context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
                    ?: error("Unable to open ${source.uri}")
                val response = httpClient.postMultipart(
                    url = "https://api.openai.com/v1/audio/transcriptions",
                    headers = mapOf("Authorization" to "Bearer $apiKey"),
                    fields = buildMap {
                        put("model", model.ifBlank { OPENAI_AUDIO_SYNC_DEFAULT_MODEL })
                        put("response_format", "verbose_json")
                        put("timestamp_granularities[]", "word")
                        request.language?.let { put("language", it) }
                    },
                    fileFieldName = "file",
                    fileName = source.displayName ?: "audio.${source.uri.bestEffortExtension()}",
                    contentType = context.contentResolver.getType(source.uri) ?: "application/octet-stream",
                    bytes = bytes
                )
                OpenAiTranscriptionResponseParser.parse(response)
            }
            TranscriptionResult.Success(segments)
        }.getOrElse { error ->
            TranscriptionResult.Failure(TranscriptionError.ExternalServiceFailed("OpenAI transcription failed: ${error.message ?: "unknown error"}", error))
        }
    }
}

const val OPENAI_AUDIO_SYNC_DEFAULT_MODEL = "whisper-1"

object OpenAiTranscriptionResponseParser {
    fun parse(json: String): List<TranscriptSegment> {
        val root = JSONObject(json)
        val words = root.optJSONArray("words")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                TranscriptWord(
                    startSeconds = item.optDouble("start"),
                    endSeconds = item.optDouble("end"),
                    word = item.optString("word")
                )
            }
        }.orEmpty()
        if (words.isNotEmpty()) {
            return listOf(
                TranscriptSegment(
                    startSeconds = words.first().startSeconds,
                    endSeconds = words.last().endSeconds,
                    text = root.optString("text"),
                    words = words
                )
            )
        }
        val segments = root.optJSONArray("segments") ?: return emptyList()
        return List(segments.length()) { index ->
            val item = segments.getJSONObject(index)
            TranscriptSegment(
                startSeconds = item.optDouble("start"),
                endSeconds = item.optDouble("end"),
                text = item.optString("text"),
                words = emptyList()
            )
        }
    }
}

private fun Uri.bestEffortExtension(): String = lastPathSegment?.substringAfterLast('.', "wav") ?: "wav"
