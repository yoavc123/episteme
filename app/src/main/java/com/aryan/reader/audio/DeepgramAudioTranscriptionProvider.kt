package com.aryan.reader.audio

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

class DeepgramAudioTranscriptionProvider(
    private val context: Context,
    private val apiKey: String,
    private val model: String = DEEPGRAM_AUDIO_SYNC_DEFAULT_MODEL,
    private val httpClient: ExternalTranscriptionHttpClient = UrlConnectionExternalTranscriptionHttpClient()
) : AudioTranscriptionProvider {
    override val id: String = "deepgram-nova"

    override suspend fun transcribe(
        request: AudioTranscriptionRequest,
        progress: (TranscriptionProgress) -> Unit
    ): TranscriptionResult {
        if (apiKey.isBlank()) {
            return TranscriptionResult.Failure(TranscriptionError.MissingApiKey("Add a Deepgram API key to use Deepgram transcription backup."))
        }
        return runCatching {
            val segments = request.sources.flatMapIndexed { index, source ->
                progress(TranscriptionProgress(index, request.sources.size, 0, "Uploading ${source.displayName ?: "audio"} to Deepgram"))
                val bytes = context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
                    ?: error("Unable to open ${source.uri}")
                val response = httpClient.postBytes(
                    url = "https://api.deepgram.com/v1/listen?model=${deepgramModelQueryValue()}&smart_format=true",
                    headers = mapOf("Authorization" to "Token $apiKey"),
                    contentType = context.contentResolver.getType(source.uri) ?: "application/octet-stream",
                    bytes = bytes
                )
                DeepgramTranscriptionResponseParser.parse(response)
            }
            TranscriptionResult.Success(segments)
        }.getOrElse { error ->
            TranscriptionResult.Failure(TranscriptionError.ExternalServiceFailed("Deepgram transcription failed: ${error.message ?: "unknown error"}", error))
        }
    }

    private fun deepgramModelQueryValue(): String =
        URLEncoder.encode(model.ifBlank { DEEPGRAM_AUDIO_SYNC_DEFAULT_MODEL }, Charsets.UTF_8.name())
}

const val DEEPGRAM_AUDIO_SYNC_DEFAULT_MODEL = "nova-3"

object DeepgramTranscriptionResponseParser {
    fun parse(json: String): List<TranscriptSegment> {
        val alternative = JSONObject(json)
            .optJSONObject("results")
            ?.optJSONArray("channels")
            ?.optJSONObject(0)
            ?.optJSONArray("alternatives")
            ?.optJSONObject(0)
            ?: return emptyList()
        val words = alternative.optJSONArray("words")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                TranscriptWord(
                    startSeconds = item.optDouble("start"),
                    endSeconds = item.optDouble("end"),
                    word = item.optString("punctuated_word", item.optString("word"))
                )
            }
        }.orEmpty()
        return if (words.isEmpty()) {
            emptyList()
        } else {
            listOf(
                TranscriptSegment(
                    startSeconds = words.first().startSeconds,
                    endSeconds = words.last().endSeconds,
                    text = alternative.optString("transcript"),
                    words = words
                )
            )
        }
    }
}
