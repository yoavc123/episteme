package com.aryan.reader.audio

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class WhisperLocalTranscriptionProvider(
    private val context: Context,
    private val modelManager: WhisperModelManager,
    private val nativeBridge: WhisperNativeBridgeApi = WhisperNativeBridge,
    private val decoder: AudioDecodeUtils.Decoder = AudioDecodeUtils.androidDecoder
) : AudioTranscriptionProvider {
    override val id: String = "local-whisper"

    override suspend fun transcribe(
        request: AudioTranscriptionRequest,
        progress: suspend (TranscriptionProgress) -> Unit
    ): TranscriptionResult {
        val modelFile = modelManager.selectedOrBundledModelFile()
            ?: return TranscriptionResult.Failure(TranscriptionError.MissingLocalModel())

        var handle = 0L
        return try {
            handle = nativeBridge.loadModel(modelFile.absolutePath)
            if (handle == 0L) {
                return TranscriptionResult.Failure(
                    TranscriptionError.NativeLoadFailed("Whisper could not load the selected local model.")
                )
            }

            val segments = mutableListOf<TranscriptSegment>()
            request.sources.forEachIndexed { sourceIndex, source ->
                progress(
                    TranscriptionProgress(sourceIndex, request.sources.size, 0, "Decoding ${source.displayName ?: "audio"}")
                )
                var chunkCount = 0
                for (chunk in decoder.decode(context, source.uri)) {
                    coroutineContext.ensureActive()
                    val nativeSegments = nativeBridge.transcribe(
                        handle = handle,
                        pcm = chunk.samples,
                        sampleRate = AudioDecodeUtils.WHISPER_SAMPLE_RATE,
                        language = request.language
                    )
                    segments += nativeSegments.map { it.toTranscriptSegment(chunk.startSeconds) }
                    chunkCount += 1
                    progress(
                        TranscriptionProgress(
                            sourceIndex = sourceIndex,
                            sourceCount = request.sources.size,
                            processedChunks = chunkCount,
                            message = "Transcribed ${source.displayName ?: "audio"} chunk $chunkCount"
                        )
                    )
                }
            }
            TranscriptionResult.Success(segments.sortedBy { it.startSeconds })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (decode: IllegalArgumentException) {
            TranscriptionResult.Failure(
                TranscriptionError.DecodeFailed("Could not decode audio for local Whisper transcription.", decode)
            )
        } catch (nativeError: UnsatisfiedLinkError) {
            TranscriptionResult.Failure(
                TranscriptionError.NativeLoadFailed("Local Whisper native library is unavailable.", nativeError)
            )
        } catch (error: Exception) {
            TranscriptionResult.Failure(
                TranscriptionError.Unknown("Local Whisper transcription failed: ${error.message ?: "unknown error"}", error)
            )
        } finally {
            if (handle != 0L) nativeBridge.freeModel(handle)
        }
    }

    private fun NativeWhisperSegment.toTranscriptSegment(offsetSeconds: Double): TranscriptSegment =
        TranscriptSegment(
            startSeconds = offsetSeconds + startSeconds,
            endSeconds = offsetSeconds + endSeconds,
            text = text,
            words = words.map {
                TranscriptWord(
                    startSeconds = offsetSeconds + it.startSeconds,
                    endSeconds = offsetSeconds + it.endSeconds,
                    word = it.word
                )
            }
        )
}

interface WhisperNativeBridgeApi {
    fun loadModel(path: String): Long
    fun freeModel(handle: Long)
    fun transcribe(handle: Long, pcm: FloatArray, sampleRate: Int, language: String?): List<NativeWhisperSegment>
}

object WhisperNativeBridge : WhisperNativeBridgeApi {
    init {
        System.loadLibrary("native-lib")
    }

    override external fun loadModel(path: String): Long
    override external fun freeModel(handle: Long)
    override external fun transcribe(
        handle: Long,
        pcm: FloatArray,
        sampleRate: Int,
        language: String?
    ): List<NativeWhisperSegment>
}

data class NativeWhisperSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val text: String,
    val words: List<NativeWhisperWord> = emptyList()
)

data class NativeWhisperWord(
    val startSeconds: Double,
    val endSeconds: Double,
    val word: String
)
