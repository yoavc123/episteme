/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader

import android.app.Application
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class CloudSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "CloudSyncWorker"
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        setForeground(
            SyncNotificationHelper.cloudSyncForegroundInfo(
                appContext,
                appContext.getString(R.string.notification_cloud_sync_running)
            )
        )

        val application = appContext.applicationContext as? Application
            ?: return@withContext Result.failure()
        val syncViewModel = MainViewModel(application)
        try {
            syncViewModel.syncWithCloud(showBanner = false, runInWorker = true).join()
            Result.success()
        } catch (error: Exception) {
            Timber.tag("CloudSyncWorker").e(error, "Cloud sync worker failed")
            Result.failure()
        }
    }
}
