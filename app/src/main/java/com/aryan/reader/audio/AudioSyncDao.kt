package com.aryan.reader.audio

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioSyncDao {
    @Upsert
    suspend fun upsert(session: AudioSyncEntity)

    @Query("SELECT * FROM audio_sync_sessions WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): AudioSyncEntity?

    @Query("SELECT * FROM audio_sync_sessions WHERE bookId = :bookId ORDER BY updatedAt DESC")
    fun observeByBookId(bookId: String): Flow<List<AudioSyncEntity>>

    @Query("SELECT * FROM audio_sync_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AudioSyncEntity>>

    @Query("UPDATE audio_sync_sessions SET status = :status, progressPercent = :progressPercent, currentStep = :currentStep, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateProgress(sessionId: String, status: String, progressPercent: Int, currentStep: String?, updatedAt: Long)

    @Query("UPDATE audio_sync_sessions SET audioSourcesJson = :audioSourcesJson, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateAudioSources(sessionId: String, audioSourcesJson: String, updatedAt: Long)

    @Query("UPDATE audio_sync_sessions SET status = :status, progressPercent = 100, currentStep = :currentStep, outputEpubPath = :outputEpubPath, errorMessage = NULL, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateOutput(sessionId: String, status: String, currentStep: String?, outputEpubPath: String, updatedAt: Long)

    @Query("UPDATE audio_sync_sessions SET status = :status, errorMessage = :errorMessage, currentStep = :currentStep, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateError(sessionId: String, status: String, errorMessage: String, currentStep: String?, updatedAt: Long)

    @Query("UPDATE audio_sync_sessions SET cancelRequested = 1, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun requestCancel(sessionId: String, updatedAt: Long)

    @Query("DELETE FROM audio_sync_sessions WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
}
