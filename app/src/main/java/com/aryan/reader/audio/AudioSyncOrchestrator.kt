package com.aryan.reader.audio

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.aryan.reader.FileType
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import com.aryan.reader.shared.AudioBookAlignmentEngine
import com.aryan.reader.shared.AudioBookSentence
import com.aryan.reader.shared.AudioBookTranscriptTrack
import com.aryan.reader.shared.AudioBookTranscriptWord
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayAudioFile
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayClip
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayRequest
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayWriter
import java.io.File
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AudioSyncOrchestrator(
    private val context: Context,
    private val repository: AudioSyncRepository = AudioSyncRepository(context),
    private val providerSelector: AudioSyncTranscriptionProviderSelector = AudioSyncProviderSelector(context),
    private val epubTools: AudioSyncEpubTools = SharedAudioSyncEpubTools,
    private val outputRegistrar: AudioSyncOutputRegistrar = RecentFilesAudioSyncOutputRegistrar(context),
    private val audioSourceFactory: (File, String?) -> AudioSource = { file, displayName -> AudioSource(file.toUri(), displayName) }
) {
    suspend fun run(sessionId: String): AudioSyncRunResult = withContext(Dispatchers.IO) {
        val session = repository.getSession(sessionId)
            ?: return@withContext AudioSyncRunResult.Failed("Audio sync session was not found.")
        try {
            failIfCancellationRequested(sessionId)
            repository.updateProgress(sessionId, AudioSyncStatus.RUNNING, 5, AudioSyncStep.PREPARING.label)
            require(session.audioSources.isNotEmpty()) { "Audio sync requires at least one audio source." }
            val sourceEpub = localEpubFile(session)
            val output = repository.outputFile(session)

            failIfCancellationRequested(sessionId)
            val transcript = transcribeWithFallbacks(session)

            failIfCancellationRequested(sessionId)
            repository.updateProgress(sessionId, AudioSyncStatus.RUNNING, 65, AudioSyncStep.ALIGNING.label)
            val contentSentences = epubTools.extractSentences(sourceEpub)
            val allSentences = contentSentences.flatMap { content ->
                content.sentences.map { sentence -> AudioBookSentence(sentence.id, sentence.text) }
            }
            require(allSentences.isNotEmpty()) { "No readable EPUB sentences were found to align." }
            val alignment = AudioBookAlignmentEngine.align(allSentences, transcript)
            val hrefBySentenceId = contentSentences.flatMap { content ->
                content.sentences.map { it.id to content.contentHref }
            }.toMap()
            val audioFiles = session.audioSources.mapIndexed { index, source ->
                SharedEpubMediaOverlayAudioFile(
                    id = "audio-${index + 1}",
                    fileName = File(source.path).name,
                    mediaType = source.mimeType ?: "audio/mpeg",
                    file = File(source.path)
                )
            }
            val clips = alignment.entries.mapNotNull { entry ->
                val href = hrefBySentenceId[entry.sentenceId] ?: return@mapNotNull null
                val audioSourceId = entry.audioSourceId ?: audioFiles.firstOrNull()?.id ?: return@mapNotNull null
                if (entry.clipEnd <= entry.clipBegin) return@mapNotNull null
                SharedEpubMediaOverlayClip(
                    contentHref = href,
                    sentenceId = entry.sentenceId,
                    audioFileId = audioSourceId,
                    clipBeginSeconds = entry.clipBegin,
                    clipEndSeconds = entry.clipEnd
                )
            }
            require(clips.isNotEmpty()) { "No transcript clips could be aligned to the EPUB." }

            failIfCancellationRequested(sessionId)
            repository.updateProgress(sessionId, AudioSyncStatus.RUNNING, 82, AudioSyncStep.WRITING_EPUB.label)
            epubTools.write(sourceEpub, output, SharedEpubMediaOverlayRequest(audioFiles, clips))

            failIfCancellationRequested(sessionId)
            repository.updateProgress(sessionId, AudioSyncStatus.RUNNING, 94, AudioSyncStep.IMPORTING.label)
            outputRegistrar.register(session, output)

            repository.markCompleted(sessionId, output.absolutePath)
            AudioSyncRunResult.Completed(output)
        } catch (cancelled: CancellationException) {
            repository.updateProgress(sessionId, AudioSyncStatus.CANCELLED, 0, AudioSyncStep.CANCELLED.label)
            AudioSyncRunResult.Cancelled
        } catch (error: Exception) {
            repository.markFailed(sessionId, error.message ?: "Audio sync failed.", AudioSyncStep.FAILED.label)
            AudioSyncRunResult.Failed(error.message ?: "Audio sync failed.")
        }
    }

    private suspend fun transcribeWithFallbacks(session: AudioSyncSession): List<AudioBookTranscriptTrack> {
        repository.updateProgress(session.sessionId, AudioSyncStatus.RUNNING, 20, AudioSyncStep.TRANSCRIBING.label)
        val failures = mutableListOf<String>()
        providerSelector.providersFor(context, session.provider).forEach { provider ->
            failIfCancellationRequested(session.sessionId)
            val tracks = mutableListOf<AudioBookTranscriptTrack>()
            var providerFailed = false
            session.audioSources.forEachIndexed { index, source ->
                val sourceCount = session.audioSources.size
                val request = AudioTranscriptionRequest(
                    sources = listOf(audioSourceFactory(File(source.path), source.displayName))
                )
                val progressCallback: suspend (TranscriptionProgress) -> Unit = { progress ->
                    val sourceIndex = (index + progress.sourceIndex).coerceIn(0, sourceCount - 1)
                    repository.updateProgress(
                        session.sessionId,
                        AudioSyncStatus.RUNNING,
                        transcriptionProgressPercent(
                            sourceIndex = sourceIndex,
                            sourceCount = sourceCount,
                            processedChunks = progress.processedChunks,
                        ),
                        transcriptionProgressStatus(
                            sourceIndex = sourceIndex,
                            sourceCount = sourceCount,
                            processedChunks = progress.processedChunks,
                            message = progress.message,
                        )
                    )
                }
                when (val result = provider.transcribe(request, progress = progressCallback)) {
                    is TranscriptionResult.Success -> {
                        val words = result.segments.flatMap { segment ->
                            if (segment.words.isNotEmpty()) {
                                segment.words.map { AudioBookTranscriptWord(it.word, it.startSeconds, it.endSeconds) }
                            } else {
                                listOf(AudioBookTranscriptWord(segment.text, segment.startSeconds, segment.endSeconds))
                            }
                        }
                        if (words.isEmpty()) {
                            failures += "${provider.id}: empty transcript for ${source.displayName}"
                            providerFailed = true
                            return@forEachIndexed
                        }
                        tracks += AudioBookTranscriptTrack("audio-${index + 1}", words = words)
                    }
                    is TranscriptionResult.Failure -> {
                        failures += "${provider.id}: ${result.error.userMessage}"
                        providerFailed = true
                        return@forEachIndexed
                    }
                }
            }
            if (!providerFailed && tracks.isNotEmpty()) return tracks
        }
        error("All transcription providers failed. ${failures.joinToString("; ")}")
    }

    private suspend fun failIfCancellationRequested(sessionId: String) {
        coroutineContext.ensureActive()
        if (repository.getSession(sessionId)?.cancelRequested == true) throw CancellationException("Audio sync was cancelled.")
    }

    private fun localEpubFile(session: AudioSyncSession): File {
        runCatching { File(URI(session.sourceEpubUri)) }.getOrNull()?.takeIf { it.isFile }?.let { return it }
        val copy = File(repository.sessionDirectory(session.sessionId), "source.epub")
        if (!copy.isFile) {
            val uri = session.sourceEpubUri.toUri()
            copy.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                ?: error("Unable to open source EPUB.")
        }
        return copy
    }
}

enum class AudioSyncStep(val label: String) {
    PREPARING("Preparing"),
    TRANSCRIBING("Transcribing"),
    ALIGNING("Aligning"),
    WRITING_EPUB("Writing EPUB"),
    IMPORTING("Importing"),
    CANCELLED("Cancelled"),
    FAILED("Failed")
}

internal fun transcriptionProgressPercent(
    sourceIndex: Int,
    sourceCount: Int,
    processedChunks: Int,
): Int {
    val safeSourceCount = sourceCount.coerceAtLeast(1)
    val safeSourceIndex = sourceIndex.coerceIn(0, safeSourceCount - 1)
    val sourceWindow = 44f / safeSourceCount
    val chunkProgress = processedChunks.coerceAtLeast(0).coerceAtMost(8) / 8f
    return (20f + safeSourceIndex * sourceWindow + chunkProgress * sourceWindow * 0.8f)
        .toInt()
        .coerceIn(20, 64)
}

internal fun transcriptionProgressStatus(
    sourceIndex: Int,
    sourceCount: Int,
    processedChunks: Int,
    message: String,
): String {
    val safeSourceCount = sourceCount.coerceAtLeast(1)
    val sourceNumber = (sourceIndex + 1).coerceIn(1, safeSourceCount)
    val prefix = "Source $sourceNumber of $safeSourceCount, chunk ${processedChunks.coerceAtLeast(0)}"
    val detail = message.trim()
    return if (detail.isBlank()) prefix else "$prefix: $detail"
}

sealed interface AudioSyncRunResult {
    data class Completed(val outputFile: File) : AudioSyncRunResult
    data object Cancelled : AudioSyncRunResult
    data class Failed(val message: String) : AudioSyncRunResult
}

interface AudioSyncEpubTools {
    fun extractSentences(source: File): List<com.aryan.reader.shared.reader.SharedEpubMediaOverlayContentSentences>
    fun write(source: File, output: File, request: SharedEpubMediaOverlayRequest)
}

object SharedAudioSyncEpubTools : AudioSyncEpubTools {
    override fun extractSentences(source: File) = SharedEpubMediaOverlayWriter.extractSentences(source)
    override fun write(source: File, output: File, request: SharedEpubMediaOverlayRequest) {
        SharedEpubMediaOverlayWriter.rewrite(source, output, request)
    }
}

interface AudioSyncOutputRegistrar {
    suspend fun register(session: AudioSyncSession, output: File)
}

class RecentFilesAudioSyncOutputRegistrar(context: Context) : AudioSyncOutputRegistrar {
    private val repository = RecentFilesRepository(context.applicationContext)

    override suspend fun register(session: AudioSyncSession, output: File) {
        repository.addRecentFile(
            RecentFileItem(
                bookId = "readaloud-${session.bookId}-${UUID.nameUUIDFromBytes(output.absolutePath.toByteArray()).toString()}",
                uriString = output.toURI().toString(),
                type = FileType.EPUB,
                displayName = "${session.bookTitle} Readaloud.epub",
                title = "${session.bookTitle} Readaloud",
                timestamp = System.currentTimeMillis(),
                isRecent = true,
                isAvailable = true,
                fileSize = output.length(),
                fileContentModifiedTimestamp = output.lastModified()
            )
        )
    }
}
