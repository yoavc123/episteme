package com.aryan.reader.audio

import com.aryan.reader.FileType
import com.aryan.reader.data.RecentFileItem
import org.json.JSONArray
import org.json.JSONObject

enum class AudioSyncStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AudioSyncProvider {
    LOCAL_WHISPER,
    OPENAI,
    DEEPGRAM
}

data class AudioSyncSourceFile(
    val displayName: String,
    val path: String,
    val mimeType: String?,
    val sizeBytes: Long
)

data class AudioSyncSession(
    val sessionId: String,
    val bookId: String,
    val bookTitle: String,
    val sourceEpubUri: String,
    val audioSources: List<AudioSyncSourceFile>,
    val provider: AudioSyncProvider,
    val status: AudioSyncStatus,
    val progressPercent: Int,
    val currentStep: String?,
    val outputEpubPath: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val cancelRequested: Boolean
)

data class AudioSyncSessionSummary(
    val sessionId: String,
    val bookId: String,
    val status: String,
    val progressPercent: Int,
    val updatedAt: Long,
) {
    val syncStatus: AudioSyncStatus
        get() = status.toAudioSyncStatus()
}

fun RecentFileItem.requireAudioSyncEpubUri(): String {
    require(type == FileType.EPUB) { "Audio sync currently supports EPUB books only." }
    return requireNotNull(uriString) { "Audio sync requires a local EPUB URI." }
}

internal fun List<AudioSyncSourceFile>.toAudioSourcesJson(): String {
    val array = JSONArray()
    forEach { source ->
        array.put(
            JSONObject()
                .put("displayName", source.displayName)
                .put("path", source.path)
                .put("mimeType", source.mimeType)
                .put("sizeBytes", source.sizeBytes)
        )
    }
    return array.toString()
}

internal fun audioSourcesFromJson(json: String?): List<AudioSyncSourceFile> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            AudioSyncSourceFile(
                displayName = item.optString("displayName"),
                path = item.optString("path"),
                mimeType = item.optString("mimeType").takeIf { it.isNotBlank() && it != "null" },
                sizeBytes = item.optLong("sizeBytes", 0L)
            )
        }
    }.getOrDefault(emptyList())
}

internal fun String.toAudioSyncStatus(): AudioSyncStatus {
    return runCatching { AudioSyncStatus.valueOf(this) }.getOrDefault(AudioSyncStatus.FAILED)
}

internal fun String.toAudioSyncProvider(): AudioSyncProvider {
    return runCatching { AudioSyncProvider.valueOf(this) }.getOrDefault(AudioSyncProvider.LOCAL_WHISPER)
}
