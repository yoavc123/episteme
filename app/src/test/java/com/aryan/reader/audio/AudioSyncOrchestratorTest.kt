package com.aryan.reader.audio

import android.content.Context
import android.net.Uri
import com.aryan.reader.FileType
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayContentSentences
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayRequest
import com.aryan.reader.shared.reader.SharedEpubMediaOverlaySentence
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioSyncOrchestratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun successWritesOutputAndMarksSessionComplete() = runTest {
        val epub = temp.newFile("book.epub").apply { writeText("epub") }
        val audio = temp.newFile("track.mp3").apply { writeBytes(byteArrayOf(1)) }
        val dao = FakeOrchestratorAudioSyncDao()
        val repository = AudioSyncRepository(dao, temp.newFolder("sync")) { 100L }
        val session = repository.createSession(book(epub), listOf(source(audio)))
        val tools = FakeEpubTools()

        val result = orchestrator(repository, listOf(FakeProvider.success()), tools).run(session.sessionId)

        assertTrue("Expected completion, got $result", result is AudioSyncRunResult.Completed)
        assertEquals(AudioSyncStatus.COMPLETED, repository.getSession(session.sessionId)?.status)
        assertTrue(File(repository.getSession(session.sessionId)?.outputEpubPath.orEmpty()).isFile)
        assertEquals(1, tools.lastRequest?.clips?.size)
    }

    @Test
    fun localFailureFallsBackToBackupProvider() = runTest {
        val epub = temp.newFile("book.epub").apply { writeText("epub") }
        val audio = temp.newFile("track.mp3").apply { writeBytes(byteArrayOf(1)) }
        val repository = AudioSyncRepository(FakeOrchestratorAudioSyncDao(), temp.newFolder("sync")) { 100L }
        val session = repository.createSession(book(epub), listOf(source(audio)))
        val local = FakeProvider.failure("local-whisper")
        val backup = FakeProvider.success("openai-whisper")

        val result = orchestrator(repository, listOf(local, backup), FakeEpubTools()).run(session.sessionId)

        assertTrue("Expected fallback completion, got $result", result is AudioSyncRunResult.Completed)
        assertEquals(1, local.calls)
        assertEquals(1, backup.calls)
    }

    private fun orchestrator(
        repository: AudioSyncRepository,
        providers: List<AudioTranscriptionProvider>,
        tools: AudioSyncEpubTools
    ) = AudioSyncOrchestrator(
        context = mockk<Context>(relaxed = true),
        repository = repository,
        providerSelector = object : AudioSyncTranscriptionProviderSelector {
            override fun providersFor(context: Context, preferredProvider: AudioSyncProvider): List<AudioTranscriptionProvider> = providers
        },
        epubTools = tools,
        outputRegistrar = object : AudioSyncOutputRegistrar {
            override suspend fun register(session: AudioSyncSession, output: File) = Unit
        },
        audioSourceFactory = { _, displayName -> AudioSource(mockk<Uri>(relaxed = true), displayName) }
    )

    private fun book(file: File) = RecentFileItem(
        bookId = "book-1",
        uriString = file.toURI().toString(),
        type = FileType.EPUB,
        displayName = "Book.epub",
        title = "Book",
        timestamp = 1L
    )

    private fun source(file: File) = AudioSyncSourceFile(
        displayName = file.name,
        path = file.absolutePath,
        mimeType = "audio/mpeg",
        sizeBytes = file.length()
    )
}

private class FakeProvider(
    override val id: String,
    private val result: TranscriptionResult
) : AudioTranscriptionProvider {
    var calls = 0

    override suspend fun transcribe(
        request: AudioTranscriptionRequest,
        progress: (TranscriptionProgress) -> Unit
    ): TranscriptionResult {
        calls += 1
        return result
    }

    companion object {
        fun success(id: String = "local-whisper") = FakeProvider(
            id,
            TranscriptionResult.Success(
                listOf(
                    TranscriptSegment(
                        startSeconds = 0.0,
                        endSeconds = 1.0,
                        text = "Hello world",
                        words = listOf(
                            TranscriptWord(0.0, 0.5, "Hello"),
                            TranscriptWord(0.5, 1.0, "world")
                        )
                    )
                )
            )
        )

        fun failure(id: String) = FakeProvider(
            id,
            TranscriptionResult.Failure(TranscriptionError.Unknown("failed"))
        )
    }
}

private class FakeEpubTools : AudioSyncEpubTools {
    var lastRequest: SharedEpubMediaOverlayRequest? = null

    override fun extractSentences(source: File): List<SharedEpubMediaOverlayContentSentences> = listOf(
        SharedEpubMediaOverlayContentSentences(
            contentHref = "chapter.xhtml",
            sentences = listOf(SharedEpubMediaOverlaySentence("s1", "Hello world"))
        )
    )

    override fun write(source: File, output: File, request: SharedEpubMediaOverlayRequest) {
        lastRequest = request
        output.parentFile?.mkdirs()
        output.writeText("synced")
    }
}

private class FakeOrchestratorAudioSyncDao : AudioSyncDao {
    private val state = MutableStateFlow<List<AudioSyncEntity>>(emptyList())

    override suspend fun upsert(session: AudioSyncEntity) {
        state.value = state.value.filterNot { it.sessionId == session.sessionId } + session
    }

    override suspend fun getBySessionId(sessionId: String): AudioSyncEntity? =
        state.value.firstOrNull { it.sessionId == sessionId }

    override fun observeByBookId(bookId: String): Flow<List<AudioSyncEntity>> =
        state.map { sessions -> sessions.filter { it.bookId == bookId }.sortedByDescending { it.updatedAt } }

    override fun observeAll(): Flow<List<AudioSyncEntity>> = state

    override suspend fun updateProgress(sessionId: String, status: String, progressPercent: Int, currentStep: String?, updatedAt: Long) {
        update(sessionId) { it.copy(status = status, progressPercent = progressPercent, currentStep = currentStep, updatedAt = updatedAt) }
    }

    override suspend fun updateAudioSources(sessionId: String, audioSourcesJson: String, updatedAt: Long) {
        update(sessionId) { it.copy(audioSourcesJson = audioSourcesJson, updatedAt = updatedAt) }
    }

    override suspend fun updateOutput(sessionId: String, status: String, currentStep: String?, outputEpubPath: String, updatedAt: Long) {
        update(sessionId) { it.copy(status = status, progressPercent = 100, currentStep = currentStep, outputEpubPath = outputEpubPath, updatedAt = updatedAt) }
    }

    override suspend fun updateError(sessionId: String, status: String, errorMessage: String, currentStep: String?, updatedAt: Long) {
        update(sessionId) { it.copy(status = status, errorMessage = errorMessage, currentStep = currentStep, updatedAt = updatedAt) }
    }

    override suspend fun requestCancel(sessionId: String, updatedAt: Long) {
        update(sessionId) { it.copy(cancelRequested = true, updatedAt = updatedAt) }
    }

    override suspend fun deleteBySessionId(sessionId: String) {
        state.value = state.value.filterNot { it.sessionId == sessionId }
    }

    private fun update(sessionId: String, transform: (AudioSyncEntity) -> AudioSyncEntity) {
        state.value = state.value.map { if (it.sessionId == sessionId) transform(it) else it }
    }
}
