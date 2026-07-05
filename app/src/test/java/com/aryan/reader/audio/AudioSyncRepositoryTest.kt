package com.aryan.reader.audio

import com.aryan.reader.FileType
import com.aryan.reader.data.RecentFileItem
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioSyncRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun createSessionStoresEpubBookWithoutImportingAudioAsBook() = runTest {
        val dao = FakeAudioSyncDao()
        val repository = AudioSyncRepository(dao, temp.newFolder("sync")) { 100L }

        val session = repository.createSession(epubBook())

        assertEquals("book-1", session.bookId)
        assertEquals(AudioSyncProvider.LOCAL_WHISPER, session.provider)
        assertEquals(AudioSyncStatus.PENDING, session.status)
        assertEquals(session, repository.getSession(session.sessionId))
        assertEquals(session, repository.observeBookSessions("book-1").first().single())
    }

    @Test
    fun updateProgressOutputErrorAndCancelPersist() = runTest {
        var now = 100L
        val repository = AudioSyncRepository(FakeAudioSyncDao(), temp.newFolder("sync")) { now }
        val session = repository.createSession(epubBook())

        now = 200L
        repository.updateProgress(session.sessionId, AudioSyncStatus.RUNNING, 55, "Transcribing")
        var updated = repository.getSession(session.sessionId)!!
        assertEquals(AudioSyncStatus.RUNNING, updated.status)
        assertEquals(55, updated.progressPercent)
        assertEquals("Transcribing", updated.currentStep)

        now = 300L
        repository.markCompleted(session.sessionId, "/tmp/readaloud.epub")
        updated = repository.getSession(session.sessionId)!!
        assertEquals(AudioSyncStatus.COMPLETED, updated.status)
        assertEquals(100, updated.progressPercent)
        assertEquals("/tmp/readaloud.epub", updated.outputEpubPath)

        now = 400L
        repository.markFailed(session.sessionId, "boom", "Aligning")
        updated = repository.getSession(session.sessionId)!!
        assertEquals(AudioSyncStatus.FAILED, updated.status)
        assertEquals("boom", updated.errorMessage)

        repository.requestCancellation(session.sessionId)
        updated = repository.getSession(session.sessionId)!!
        assertTrue(updated.cancelRequested)
        assertEquals(AudioSyncStatus.CANCELLED, updated.status)
        assertEquals("Cancelled", updated.currentStep)
    }

    @Test
    fun observeAllSessionSummariesDoesNotParseAudioSources() = runTest {
        val dao = FakeAudioSyncDao()
        val repository = AudioSyncRepository(dao, temp.newFolder("sync")) { 100L }
        val session = repository.createSession(epubBook())

        dao.updateAudioSources(session.sessionId, "not-json", 200L)
        dao.updateProgress(session.sessionId, AudioSyncStatus.RUNNING.name, 42, "Transcribing", 300L)

        val summary = repository.observeAllSessionSummaries().first().single()

        assertEquals(session.sessionId, summary.sessionId)
        assertEquals(session.bookId, summary.bookId)
        assertEquals(AudioSyncStatus.RUNNING, summary.syncStatus)
        assertEquals(42, summary.progressPercent)
    }

    @Test
    fun deleteSessionCacheRemovesRowAndDirectory() = runTest {
        val root = temp.newFolder("sync")
        val repository = AudioSyncRepository(FakeAudioSyncDao(), root) { 100L }
        val session = repository.createSession(epubBook())
        File(root, "${session.sessionId}/source").apply { mkdirs() }.resolve("a.mp3").writeBytes(byteArrayOf(1))

        repository.deleteSessionCache(session.sessionId)

        assertNull(repository.getSession(session.sessionId))
        assertTrue(!File(root, session.sessionId).exists())
    }

    @Test
    fun importerCopiesSupportedAudioAndRejectsUnsupportedFiles() {
        val importer = AudioSyncFileImporter(temp.newFolder("sync"))

        val imported = importer.importCandidates(
            "session-1",
            listOf(AudioSyncFileImporter.Candidate("track.mp3", "audio/mpeg") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) })
        )

        assertEquals("track.mp3", imported.single().displayName)
        assertTrue(File(imported.single().path).isFile)

        val failure = runCatching {
            importer.importCandidates(
                "session-2",
                listOf(AudioSyncFileImporter.Candidate("notes.txt", "text/plain") { ByteArrayInputStream(byteArrayOf(1)) })
            )
        }
        assertTrue(failure.isFailure)
    }

    @Test
    fun importerRejectsUnsafeZipEntries() {
        val importer = AudioSyncFileImporter(temp.newFolder("sync"))
        val zipBytes = zipBytes("../evil.mp3")

        val failure = runCatching {
            importer.importCandidates(
                "session-zip",
                listOf(AudioSyncFileImporter.Candidate("audio.zip", "application/zip") { ByteArrayInputStream(zipBytes) })
            )
        }

        assertTrue(failure.isFailure)
    }

    private fun epubBook() = RecentFileItem(
        bookId = "book-1",
        uriString = "file:///book.epub",
        type = FileType.EPUB,
        displayName = "Book.epub",
        title = "Book",
        timestamp = 1L
    )

    private fun zipBytes(entryName: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}

private class FakeAudioSyncDao : AudioSyncDao {
    private val state = MutableStateFlow<List<AudioSyncEntity>>(emptyList())

    override suspend fun upsert(session: AudioSyncEntity) {
        state.value = state.value.filterNot { it.sessionId == session.sessionId } + session
    }

    override suspend fun getBySessionId(sessionId: String): AudioSyncEntity? =
        state.value.firstOrNull { it.sessionId == sessionId }

    override fun observeByBookId(bookId: String): Flow<List<AudioSyncEntity>> =
        state.map { sessions -> sessions.filter { it.bookId == bookId }.sortedByDescending { it.updatedAt } }

    override fun observeAll(): Flow<List<AudioSyncEntity>> = state

    override fun observeAllSummaries(): Flow<List<AudioSyncSessionSummary>> = state.map { sessions ->
        sessions
            .sortedByDescending { it.updatedAt }
            .map { session ->
                AudioSyncSessionSummary(
                    sessionId = session.sessionId,
                    bookId = session.bookId,
                    status = session.status,
                    progressPercent = session.progressPercent,
                    updatedAt = session.updatedAt,
                )
            }
    }

    override suspend fun updateProgress(sessionId: String, status: String, progressPercent: Int, currentStep: String?, updatedAt: Long) {
        update(sessionId) { it.copy(status = status, progressPercent = progressPercent, currentStep = currentStep, updatedAt = updatedAt) }
    }

    override suspend fun updateAudioSources(sessionId: String, audioSourcesJson: String, updatedAt: Long) {
        update(sessionId) { it.copy(audioSourcesJson = audioSourcesJson, updatedAt = updatedAt) }
    }

    override suspend fun updateOutput(sessionId: String, status: String, currentStep: String?, outputEpubPath: String, updatedAt: Long) {
        update(sessionId) { it.copy(status = status, progressPercent = 100, currentStep = currentStep, outputEpubPath = outputEpubPath, errorMessage = null, updatedAt = updatedAt) }
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
