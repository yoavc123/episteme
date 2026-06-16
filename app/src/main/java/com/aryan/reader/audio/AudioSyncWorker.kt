package com.aryan.reader.audio

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class AudioSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Missing audio sync session id.")
        )
        return when (val result = AudioSyncOrchestrator(applicationContext).run(sessionId)) {
            is AudioSyncRunResult.Completed -> Result.success(workDataOf(KEY_OUTPUT_PATH to result.outputFile.absolutePath))
            AudioSyncRunResult.Cancelled -> Result.failure(workDataOf(KEY_ERROR to "Audio sync was cancelled."))
            is AudioSyncRunResult.Failed -> Result.failure(workDataOf(KEY_ERROR to result.message))
        }
    }

    companion object {
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_OUTPUT_PATH = "outputPath"
        const val KEY_ERROR = "error"

        fun uniqueWorkName(sessionId: String): String = "audio-sync-$sessionId"
    }
}
