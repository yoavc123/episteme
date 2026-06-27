package com.aryan.reader.audio

import android.content.Context
import com.aryan.reader.AiByokSettings
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSyncProviderSelectorTest {
    @Test
    fun providersForUsesConfiguredExternalModelNames() = runTest {
        val openAiModels = mutableListOf<String>()
        val deepgramModels = mutableListOf<String>()
        val selector = AudioSyncProviderSelector(
            localProvider = FakeProvider("local"),
            openAiProviderFactory = { _, model ->
                openAiModels += model
                FakeProvider("openai-whisper")
            },
            deepgramProviderFactory = { _, model ->
                deepgramModels += model
                FakeProvider("deepgram-nova")
            },
            isOfflineBuild = false,
            settingsLoader = {
                AiByokSettings(
                    openAiKey = "openai-key",
                    deepgramKey = "deepgram-key",
                    openAiAudioSyncModel = "openai:gpt-4o-transcribe",
                    deepgramAudioSyncModel = "deepgram:nova-2"
                )
            }
        )

        selector.providersFor(mockk<Context>(), AudioSyncProvider.LOCAL_WHISPER)

        assertEquals(listOf("gpt-4o-transcribe"), openAiModels)
        assertEquals(listOf("nova-2"), deepgramModels)
    }

    @Test
    fun providersForKeepsDefaultsWhenModelsAreBlankOrMismatched() = runTest {
        val openAiModels = mutableListOf<String>()
        val deepgramModels = mutableListOf<String>()
        val selector = AudioSyncProviderSelector(
            localProvider = FakeProvider("local"),
            openAiProviderFactory = { _, model ->
                openAiModels += model
                FakeProvider("openai-whisper")
            },
            deepgramProviderFactory = { _, model ->
                deepgramModels += model
                FakeProvider("deepgram-nova")
            },
            isOfflineBuild = false,
            settingsLoader = {
                AiByokSettings(
                    openAiKey = "openai-key",
                    deepgramKey = "deepgram-key",
                    openAiAudioSyncModel = "deepgram:nova-2",
                    deepgramAudioSyncModel = ""
                )
            }
        )

        selector.providersFor(mockk<Context>(), AudioSyncProvider.LOCAL_WHISPER)

        assertEquals(listOf(OPENAI_AUDIO_SYNC_DEFAULT_MODEL), openAiModels)
        assertEquals(listOf(DEEPGRAM_AUDIO_SYNC_DEFAULT_MODEL), deepgramModels)
    }

    @Test
    fun providersForAcceptsRawCustomModelIds() = runTest {
        val openAiModels = mutableListOf<String>()
        val selector = AudioSyncProviderSelector(
            localProvider = FakeProvider("local"),
            openAiProviderFactory = { _, model ->
                openAiModels += model
                FakeProvider("openai-whisper")
            },
            deepgramProviderFactory = { _, _ -> FakeProvider("deepgram-nova") },
            isOfflineBuild = false,
            settingsLoader = {
                AiByokSettings(
                    openAiKey = "openai-key",
                    openAiAudioSyncModel = "custom-transcribe-model"
                )
            }
        )

        selector.providersFor(mockk<Context>(), AudioSyncProvider.OPENAI)

        assertEquals(listOf("custom-transcribe-model"), openAiModels)
    }

    @Test
    fun providersForAcceptsRawCustomModelIdsWithColons() = runTest {
        val openAiModels = mutableListOf<String>()
        val selector = AudioSyncProviderSelector(
            localProvider = FakeProvider("local"),
            openAiProviderFactory = { _, model ->
                openAiModels += model
                FakeProvider("openai-whisper")
            },
            deepgramProviderFactory = { _, _ -> FakeProvider("deepgram-nova") },
            isOfflineBuild = false,
            settingsLoader = {
                AiByokSettings(
                    openAiKey = "openai-key",
                    openAiAudioSyncModel = "ft:gpt-4o-transcribe:org:custom"
                )
            }
        )

        selector.providersFor(mockk<Context>(), AudioSyncProvider.OPENAI)

        assertEquals(listOf("ft:gpt-4o-transcribe:org:custom"), openAiModels)
    }

    private class FakeProvider(override val id: String) : AudioTranscriptionProvider {
        override suspend fun transcribe(
            request: AudioTranscriptionRequest,
            progress: (TranscriptionProgress) -> Unit
        ): TranscriptionResult = TranscriptionResult.Success(emptyList())
    }
}
