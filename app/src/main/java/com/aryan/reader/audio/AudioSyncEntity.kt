package com.aryan.reader.audio

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_sync_sessions")
data class AudioSyncEntity(
    @PrimaryKey val sessionId: String,
    val bookId: String,
    val bookTitle: String,
    val sourceEpubUri: String,
    val audioSourcesJson: String,
    val provider: String,
    val status: String,
    val progressPercent: Int,
    val currentStep: String?,
    val outputEpubPath: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val cancelRequested: Boolean
) {
    fun toSession(): AudioSyncSession = AudioSyncSession(
        sessionId = sessionId,
        bookId = bookId,
        bookTitle = bookTitle,
        sourceEpubUri = sourceEpubUri,
        audioSources = audioSourcesFromJson(audioSourcesJson),
        provider = provider.toAudioSyncProvider(),
        status = status.toAudioSyncStatus(),
        progressPercent = progressPercent.coerceIn(0, 100),
        currentStep = currentStep,
        outputEpubPath = outputEpubPath,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cancelRequested = cancelRequested
    )

    companion object {
        fun fromSession(session: AudioSyncSession): AudioSyncEntity = AudioSyncEntity(
            sessionId = session.sessionId,
            bookId = session.bookId,
            bookTitle = session.bookTitle,
            sourceEpubUri = session.sourceEpubUri,
            audioSourcesJson = session.audioSources.toAudioSourcesJson(),
            provider = session.provider.name,
            status = session.status.name,
            progressPercent = session.progressPercent.coerceIn(0, 100),
            currentStep = session.currentStep,
            outputEpubPath = session.outputEpubPath,
            errorMessage = session.errorMessage,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            cancelRequested = session.cancelRequested
        )
    }
}
