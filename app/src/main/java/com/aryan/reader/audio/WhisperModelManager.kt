package com.aryan.reader.audio

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.Locale

class WhisperModelManager(
    private val modelsDir: File,
    private val preferences: PreferenceStore
) {
    constructor(context: Context) : this(
        modelsDir = File(context.filesDir, "audio_sync/models"),
        preferences = SharedPreferencesStore(
            context.getSharedPreferences("whisper_models", Context.MODE_PRIVATE)
        )
    )

    data class ModelInfo(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val selected: Boolean
    )

    sealed interface ImportResult {
        data class Imported(val model: ModelInfo) : ImportResult
        data class Invalid(val reason: String) : ImportResult
    }

    fun selectedModelFile(): File? = preferences.getString(KEY_SELECTED_MODEL_PATH)
        ?.let(::File)
        ?.takeIf { it.isFile && isLikelyWhisperModel(it) }

    fun listModels(): List<ModelInfo> {
        val selectedPath = preferences.getString(KEY_SELECTED_MODEL_PATH)
        return modelsDir.listFiles()
            .orEmpty()
            .filter { it.isFile && isLikelyWhisperModel(it) }
            .sortedBy { it.name.lowercase(Locale.US) }
            .map { file ->
                ModelInfo(
                    file = file,
                    name = file.name,
                    sizeBytes = file.length(),
                    selected = file.absolutePath == selectedPath
                )
            }
    }

    fun selectModel(file: File): ImportResult {
        if (!file.isFile || !isLikelyWhisperModel(file)) {
            return ImportResult.Invalid("Selected file is not a compatible Whisper ggml model.")
        }
        preferences.putString(KEY_SELECTED_MODEL_PATH, file.absolutePath)
        return ImportResult.Imported(file.toInfo(selected = true))
    }

    fun importModel(displayName: String, input: () -> InputStream): ImportResult {
        val safeName = sanitizeModelFileName(displayName)
            ?: return ImportResult.Invalid("Whisper models must use a .bin or .gguf file extension.")
        modelsDir.mkdirs()
        val destination = uniqueDestination(safeName)
        return try {
            input().use { source -> destination.outputStream().use { source.copyTo(it) } }
            if (!isLikelyWhisperModel(destination)) {
                destination.delete()
                ImportResult.Invalid("Imported file does not look like a Whisper ggml/gguf model.")
            } else {
                preferences.putString(KEY_SELECTED_MODEL_PATH, destination.absolutePath)
                ImportResult.Imported(destination.toInfo(selected = true))
            }
        } catch (error: Exception) {
            destination.delete()
            ImportResult.Invalid("Could not import Whisper model: ${error.message ?: "unknown error"}")
        }
    }

    fun importModel(context: Context, uri: Uri, displayName: String): ImportResult =
        importModel(displayName) {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "Unable to open model file" }
        }

    fun clearSelection() {
        preferences.remove(KEY_SELECTED_MODEL_PATH)
    }

    private fun File.toInfo(selected: Boolean) = ModelInfo(
        file = this,
        name = name,
        sizeBytes = length(),
        selected = selected
    )

    private fun uniqueDestination(fileName: String): File {
        var candidate = File(modelsDir, fileName)
        val base = candidate.nameWithoutExtension
        val ext = candidate.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 1
        while (candidate.exists()) {
            candidate = File(modelsDir, "$base-$index$ext")
            index += 1
        }
        return candidate
    }

    companion object {
        private const val KEY_SELECTED_MODEL_PATH = "selected_model_path"
        private const val MIN_MODEL_BYTES = 1024L
        private val allowedExtensions = setOf("bin", "gguf")

        fun sanitizeModelFileName(displayName: String): String? {
            val cleaned = displayName.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { return null }
            val extension = cleaned.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.US)
            return cleaned.takeIf { extension in allowedExtensions }
        }

        fun isLikelyWhisperModel(file: File): Boolean {
            val extension = file.extension.lowercase(Locale.US)
            if (extension !in allowedExtensions || file.length() < MIN_MODEL_BYTES) return false
            val header = ByteArray(4)
            val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
            if (read < 4) return false
            val ascii = header.toString(Charsets.US_ASCII)
            return ascii == "GGUF" || ascii == "ggml" || header.any { it.toInt() != 0 }
        }
    }
}

interface PreferenceStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class SharedPreferencesStore(private val sharedPreferences: SharedPreferences) : PreferenceStore {
    override fun getString(key: String): String? = sharedPreferences.getString(key, null)
    override fun putString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }
    override fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
}
