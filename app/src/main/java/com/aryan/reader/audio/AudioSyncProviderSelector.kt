package com.aryan.reader.audio

import android.content.Context
import com.aryan.reader.AiByokSettings
import com.aryan.reader.BuildConfig
import com.aryan.reader.loadAiByokSettings

interface AudioSyncTranscriptionProviderSelector {
    fun providersFor(context: Context, preferredProvider: AudioSyncProvider): List<AudioTranscriptionProvider>
}

class AudioSyncProviderSelector(
    private val localProvider: AudioTranscriptionProvider,
    private val openAiProviderFactory: (String, String) -> AudioTranscriptionProvider,
    private val deepgramProviderFactory: (String, String) -> AudioTranscriptionProvider,
    private val isOfflineBuild: Boolean = BuildConfig.IS_OFFLINE,
    private val settingsLoader: (Context) -> AiByokSettings = ::loadAiByokSettings
) : AudioSyncTranscriptionProviderSelector {
    constructor(context: Context) : this(
        localProvider = WhisperLocalTranscriptionProvider(context, WhisperModelManager(context)),
        openAiProviderFactory = { key, model -> OpenAiAudioTranscriptionProvider(context, key, model) },
        deepgramProviderFactory = { key, model -> DeepgramAudioTranscriptionProvider(context, key, model) }
    )

    override fun providersFor(context: Context, preferredProvider: AudioSyncProvider): List<AudioTranscriptionProvider> {
        val settings = settingsLoader(context)
        val fallbacks = if (isOfflineBuild) {
            emptyList()
        } else {
            buildList {
                if (settings.openAiKey.isNotBlank()) {
                    add(openAiProviderFactory(settings.openAiKey, settings.openAiAudioSyncModel.modelNameForProvider("openai") ?: OPENAI_AUDIO_SYNC_DEFAULT_MODEL))
                }
                if (settings.deepgramKey.isNotBlank()) {
                    add(deepgramProviderFactory(settings.deepgramKey, settings.deepgramAudioSyncModel.modelNameForProvider("deepgram") ?: DEEPGRAM_AUDIO_SYNC_DEFAULT_MODEL))
                }
            }
        }
        return when (preferredProvider) {
            AudioSyncProvider.LOCAL_WHISPER -> listOf(localProvider) + fallbacks
            AudioSyncProvider.OPENAI -> fallbacks.filter { it.id == "openai-whisper" }.ifEmpty { listOf(localProvider) + fallbacks }
            AudioSyncProvider.DEEPGRAM -> fallbacks.filter { it.id == "deepgram-nova" }.ifEmpty { listOf(localProvider) + fallbacks }
        }
    }
}

private fun String.modelNameForProvider(provider: String): String? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    val separator = trimmed.indexOf(':')
    if (separator < 0) return trimmed
    val selectedProvider = trimmed.substring(0, separator)
    if (selectedProvider !in audioSyncModelProviders) return trimmed
    val prefix = "$provider:"
    return trimmed
        .takeIf { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.takeIf { it.isNotBlank() }
}

private val audioSyncModelProviders = setOf("openai", "deepgram")
