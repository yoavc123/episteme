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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo

object SyncNotificationHelper {
    const val CHANNEL_ID = "episteme_sync_progress"
    private const val FOLDER_SYNC_NOTIFICATION_ID = 4701
    private const val METADATA_SYNC_NOTIFICATION_ID = 4702
    private const val CLOUD_SYNC_NOTIFICATION_ID = 4703

    fun folderSyncForegroundInfo(
        context: Context,
        text: String,
        progress: Int = 0,
        maxProgress: Int = 0,
        indeterminate: Boolean = true
    ): ForegroundInfo {
        return foregroundInfo(
            context = context,
            notificationId = FOLDER_SYNC_NOTIFICATION_ID,
            title = context.getString(R.string.notification_folder_sync_title),
            text = text,
            progress = progress,
            maxProgress = maxProgress,
            indeterminate = indeterminate
        )
    }

    fun metadataSyncForegroundInfo(
        context: Context,
        text: String,
        progress: Int,
        maxProgress: Int
    ): ForegroundInfo {
        return foregroundInfo(
            context = context,
            notificationId = METADATA_SYNC_NOTIFICATION_ID,
            title = context.getString(R.string.notification_folder_metadata_title),
            text = text,
            progress = progress,
            maxProgress = maxProgress,
            indeterminate = false
        )
    }

    fun cloudSyncForegroundInfo(context: Context, text: String): ForegroundInfo {
        return foregroundInfo(
            context = context,
            notificationId = CLOUD_SYNC_NOTIFICATION_ID,
            title = context.getString(R.string.notification_cloud_sync_title),
            text = text,
            progress = 0,
            maxProgress = 0,
            indeterminate = true
        )
    }

    private fun foregroundInfo(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        progress: Int,
        maxProgress: Int,
        indeterminate: Boolean
    ): ForegroundInfo {
        ensureChannel(context)
        val safeMax = maxProgress.coerceAtLeast(0)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(safeMax, progress.coerceIn(0, safeMax), indeterminate)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_sync_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_sync_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }
}
