package com.aryan.reader.audio

import android.content.Context
import com.aryan.reader.data.AppDatabase
import com.aryan.reader.data.RecentFileItem
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AudioSyncRepository(
    private val dao: AudioSyncDao,
    private val syncRoot: File,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    constructor(context: Context) : this(
        dao = AppDatabase.getDatabase(context).audioSyncDao(),
        syncRoot = File(context.filesDir, "audio_sync")
    )

    suspend fun createSession(
        book: RecentFileItem,
        audioSources: List<AudioSyncSourceFile> = emptyList(),
        provider: AudioSyncProvider = AudioSyncProvider.LOCAL_WHISPER
    ): AudioSyncSession {
        val now = clockMillis()
        val session = AudioSyncSession(
            sessionId = UUID.randomUUID().toString(),
            bookId = book.bookId,
            bookTitle = book.title?.takeIf { it.isNotBlank() } ?: book.displayName,
            sourceEpubUri = book.requireAudioSyncEpubUri(),
            audioSources = audioSources,
            provider = provider,
            status = AudioSyncStatus.PENDING,
            progressPercent = 0,
            currentStep = null,
            outputEpubPath = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            cancelRequested = false
        )
        dao.upsert(AudioSyncEntity.fromSession(session))
        return session
    }

    suspend fun getSession(sessionId: String): AudioSyncSession? = dao.getBySessionId(sessionId)?.toSession()

    fun observeBookSessions(bookId: String): Flow<List<AudioSyncSession>> =
        dao.observeByBookId(bookId).map { sessions -> sessions.map { it.toSession() } }

    fun observeAllSessions(): Flow<List<AudioSyncSession>> =
        dao.observeAll().map { sessions -> sessions.map { it.toSession() } }

    fun observeAllSessionSummaries(): Flow<List<AudioSyncSessionSummary>> = dao.observeAllSummaries()

    suspend fun updateProgress(
        sessionId: String,
        status: AudioSyncStatus,
        progressPercent: Int,
        currentStep: String?
    ) {
        dao.updateProgress(sessionId, status.name, progressPercent.coerceIn(0, 100), currentStep, clockMillis())
    }

    suspend fun updateAudioSources(sessionId: String, audioSources: List<AudioSyncSourceFile>) {
        dao.updateAudioSources(sessionId, audioSources.toAudioSourcesJson(), clockMillis())
    }

    suspend fun markCompleted(sessionId: String, outputEpubPath: String) {
        dao.updateOutput(sessionId, AudioSyncStatus.COMPLETED.name, "Completed", outputEpubPath, clockMillis())
    }

    suspend fun markFailed(sessionId: String, errorMessage: String, currentStep: String? = null) {
        dao.updateError(sessionId, AudioSyncStatus.FAILED.name, errorMessage, currentStep, clockMillis())
    }

    suspend fun requestCancellation(sessionId: String) {
        dao.requestCancel(sessionId, clockMillis())
        updateProgress(sessionId, AudioSyncStatus.CANCELLED, 0, AudioSyncStep.CANCELLED.label)
    }

    suspend fun deleteSessionCache(sessionId: String) {
        dao.deleteBySessionId(sessionId)
        File(syncRoot, sessionId).takeIf { it.canonicalPath.startsWith(syncRoot.canonicalPath) }?.deleteRecursively()
    }

    fun sessionDirectory(sessionId: String): File = File(syncRoot, sessionId)

    fun outputFile(session: AudioSyncSession): File {
        val outputDir = File(sessionDirectory(session.sessionId), "output").apply { mkdirs() }
        return File(outputDir, "${session.bookTitle.safeAudioSyncFileStem()}.readaloud.epub")
    }
}

private fun String.safeAudioSyncFileStem(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.', '-')
        .ifBlank { "book" }
