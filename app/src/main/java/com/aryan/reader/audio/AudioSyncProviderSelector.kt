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
    private val openAiProviderFactory: (String) -> AudioTranscriptionProvider,
    private val deepgramProviderFactory: (String) -> AudioTranscriptionProvider,
    private val isOfflineBuild: Boolean = BuildConfig.IS_OFFLINE,
    private val settingsLoader: (Context) -> AiByokSettings = { context -> loadAiByokSettings(context) }
) : AudioSyncTranscriptionProviderSelector {
    constructor(context: Context) : this(
        localProvider = WhisperLocalTranscriptionProvider(context, WhisperModelManager(context)),
        openAiProviderFactory = { key -> OpenAiAudioTranscriptionProvider(context, key) },
        deepgramProviderFactory = { key -> DeepgramAudioTranscriptionProvider(context, key) }
    )

    override fun providersFor(context: Context, preferredProvider: AudioSyncProvider): List<AudioTranscriptionProvider> {
        val settings = settingsLoader(context)
        return when (preferredProvider) {
            AudioSyncProvider.LOCAL_WHISPER -> listOf(localProvider)
            AudioSyncProvider.OPENAI -> if (!isOfflineBuild && settings.openAiKey.isNotBlank()) {
                listOf(openAiProviderFactory(settings.openAiKey))
            } else {
                emptyList()
            }
            AudioSyncProvider.DEEPGRAM -> if (!isOfflineBuild && settings.deepgramKey.isNotBlank()) {
                listOf(deepgramProviderFactory(settings.deepgramKey))
            } else {
                emptyList()
            }
        }
    }
}
