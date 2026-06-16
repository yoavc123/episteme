package com.aryan.reader.audio

import android.content.Context
import com.aryan.reader.BuildConfig
import com.aryan.reader.loadAiByokSettings

interface AudioSyncTranscriptionProviderSelector {
    fun providersFor(context: Context, preferredProvider: AudioSyncProvider): List<AudioTranscriptionProvider>
}

class AudioSyncProviderSelector(
    private val localProvider: AudioTranscriptionProvider,
    private val openAiProviderFactory: (String) -> AudioTranscriptionProvider,
    private val deepgramProviderFactory: (String) -> AudioTranscriptionProvider,
    private val isOfflineBuild: Boolean = BuildConfig.IS_OFFLINE
) : AudioSyncTranscriptionProviderSelector {
    constructor(context: Context) : this(
        localProvider = WhisperLocalTranscriptionProvider(context, WhisperModelManager(context)),
        openAiProviderFactory = { key -> OpenAiAudioTranscriptionProvider(context, key) },
        deepgramProviderFactory = { key -> DeepgramAudioTranscriptionProvider(context, key) }
    )

    override fun providersFor(context: Context, preferredProvider: AudioSyncProvider): List<AudioTranscriptionProvider> {
        val settings = loadAiByokSettings(context)
        val fallbacks = if (isOfflineBuild) {
            emptyList()
        } else {
            buildList {
                if (settings.openAiKey.isNotBlank()) add(openAiProviderFactory(settings.openAiKey))
                if (settings.deepgramKey.isNotBlank()) add(deepgramProviderFactory(settings.deepgramKey))
            }
        }
        return when (preferredProvider) {
            AudioSyncProvider.LOCAL_WHISPER -> listOf(localProvider) + fallbacks
            AudioSyncProvider.OPENAI -> fallbacks.filter { it.id == "openai-whisper" }.ifEmpty { listOf(localProvider) + fallbacks }
            AudioSyncProvider.DEEPGRAM -> fallbacks.filter { it.id == "deepgram-nova" }.ifEmpty { listOf(localProvider) + fallbacks }
        }
    }
}
