package com.aryan.reader.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

class AudioSyncFileImporter(
    private val syncRoot: File
) {
    constructor(context: Context) : this(File(context.filesDir, "audio_sync"))

    data class Candidate(
        val displayName: String,
        val mimeType: String?,
        val input: () -> InputStream
    )

    fun importFromUris(context: Context, sessionId: String, uris: List<Uri>): List<AudioSyncSourceFile> {
        val candidates = uris.map { uri ->
            Candidate(
                displayName = context.displayNameFor(uri),
                mimeType = context.contentResolver.getType(uri),
                input = { requireNotNull(context.contentResolver.openInputStream(uri)) { "Unable to open $uri" } }
            )
        }
        return importCandidates(sessionId, candidates)
    }

    fun importCandidates(sessionId: String, candidates: List<Candidate>): List<AudioSyncSourceFile> {
        require(sessionId.isNotBlank()) { "sessionId is required" }
        val sourceDir = File(syncRoot, "$sessionId/source")
        sourceDir.mkdirs()
        return candidates.mapIndexed { index, candidate -> importCandidate(sourceDir, index, candidate) }
    }

    private fun importCandidate(sourceDir: File, index: Int, candidate: Candidate): AudioSyncSourceFile {
        val safeName = safeFileName(candidate.displayName).ifBlank { "audio-$index" }
        validateSupportedAudioSource(safeName, candidate.mimeType, candidate.input)
        val destination = uniqueDestination(sourceDir, safeName)
        candidate.input().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        return AudioSyncSourceFile(
            displayName = candidate.displayName,
            path = destination.absolutePath,
            mimeType = candidate.mimeType,
            sizeBytes = destination.length()
        )
    }

    private fun validateSupportedAudioSource(
        displayName: String,
        mimeType: String?,
        input: () -> InputStream
    ) {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)
        if (extension in allowedAudioExtensions || mimeType?.startsWith("audio/") == true) return
        if (extension == "zip" || mimeType in zipMimeTypes) {
            validateAudioZip(input)
            return
        }
        error("Unsupported audio source: $displayName")
    }

    private fun validateAudioZip(input: () -> InputStream) {
        var audioCount = 0
        input().use { source ->
            ZipInputStream(source).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    if (entry.isDirectory) continue
                    require(!name.startsWith("/") && ".." !in name.split('/')) { "Unsafe file in audio archive: ${entry.name}" }
                    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)
                    require(extension in allowedAudioExtensions) { "Unsupported file in audio archive: ${entry.name}" }
                    audioCount += 1
                }
            }
        }
        require(audioCount > 0) { "Audio archive does not contain supported audio files." }
    }

    private fun uniqueDestination(sourceDir: File, fileName: String): File {
        var candidate = File(sourceDir, fileName)
        val base = candidate.nameWithoutExtension.ifBlank { "audio" }
        val suffix = candidate.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 1
        while (candidate.exists()) {
            candidate = File(sourceDir, "$base-$index$suffix")
            index += 1
        }
        return candidate
    }

    private fun safeFileName(name: String): String = name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private val allowedAudioExtensions = setOf("mp3", "m4a", "m4b", "mp4", "wav", "webm")
        private val zipMimeTypes = setOf("application/zip", "application/x-zip-compressed")
    }
}

private fun Context.displayNameFor(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return uri.lastPathSegment ?: "audio"
}
