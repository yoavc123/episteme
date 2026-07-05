package com.aryan.reader.audio

import android.content.Context
import com.aryan.reader.AiByokSettings
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSyncProviderSelectorTest {
    @Test
    fun providersForLocalWhisperUsesOnlyLocalProvider() = runTest {
        val selector = selector(
            settings = AiByokSettings(openAiKey = "openai-key", deepgramKey = "deepgram-key")
        )

        val providers = selector.providersFor(mockk<Context>(), AudioSyncProvider.LOCAL_WHISPER)

        assertEquals(listOf("local"), providers.map { it.id })
    }

    @Test
    fun providersForPreferredExternalUsesOnlyThatProviderWhenConfigured() = runTest {
        val selector = selector(
            settings = AiByokSettings(openAiKey = "openai-key", deepgramKey = "deepgram-key")
        )

        val providers = selector.providersFor(mockk<Context>(), AudioSyncProvider.OPENAI)

        assertEquals(listOf("openai-whisper"), providers.map { it.id })
    }

    @Test
    fun providersForUnavailablePreferredExternalReturnsNoProvider() = runTest {
        val selector = selector(
            settings = AiByokSettings(deepgramKey = "deepgram-key")
        )

        val providers = selector.providersFor(mockk<Context>(), AudioSyncProvider.OPENAI)

        assertEquals(emptyList<String>(), providers.map { it.id })
    }

    @Test
    fun providersForOfflineBuildExcludesExternalProviders() = runTest {
        val selector = selector(
            settings = AiByokSettings(openAiKey = "openai-key", deepgramKey = "deepgram-key"),
            isOfflineBuild = true
        )

        val providers = selector.providersFor(mockk<Context>(), AudioSyncProvider.LOCAL_WHISPER)

        assertEquals(listOf("local"), providers.map { it.id })
    }

    private fun selector(
        settings: AiByokSettings,
        isOfflineBuild: Boolean = false
    ) = AudioSyncProviderSelector(
        localProvider = FakeProvider("local"),
        openAiProviderFactory = { FakeProvider("openai-whisper") },
        deepgramProviderFactory = { FakeProvider("deepgram-nova") },
        isOfflineBuild = isOfflineBuild,
        settingsLoader = { settings }
    )

    private class FakeProvider(override val id: String) : AudioTranscriptionProvider {
        override suspend fun transcribe(
            request: AudioTranscriptionRequest,
            progress: suspend (TranscriptionProgress) -> Unit
        ): TranscriptionResult = TranscriptionResult.Success(emptyList())
    }
}
