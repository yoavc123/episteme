package com.aryan.reader.audio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAudioTranscriptionProviderTest {
    @Test
    fun openAiProviderSendsConfiguredModel() = runTest {
        val httpClient = CapturingExternalTranscriptionHttpClient(openAiResponse)
        val provider = OpenAiAudioTranscriptionProvider(
            context = contextWithAudio(),
            apiKey = "openai-key",
            model = "gpt-4o-transcribe",
            httpClient = httpClient
        )

        val result = provider.transcribe(AudioTranscriptionRequest(listOf(AudioSource(Uri.parse("content://audio/track.wav"), "track.wav"))))

        assertTrue(result is TranscriptionResult.Success)
        assertEquals("gpt-4o-transcribe", httpClient.multipartFields.single().getValue("model"))
    }

    @Test
    fun openAiProviderKeepsDefaultModelWhenBlank() = runTest {
        val httpClient = CapturingExternalTranscriptionHttpClient(openAiResponse)
        val provider = OpenAiAudioTranscriptionProvider(
            context = contextWithAudio(),
            apiKey = "openai-key",
            model = "",
            httpClient = httpClient
        )

        val result = provider.transcribe(AudioTranscriptionRequest(listOf(AudioSource(Uri.parse("content://audio/track.wav"), "track.wav"))))

        assertTrue(result is TranscriptionResult.Success)
        assertEquals(OPENAI_AUDIO_SYNC_DEFAULT_MODEL, httpClient.multipartFields.single().getValue("model"))
    }

    @Test
    fun deepgramProviderSendsConfiguredModelInListenUrl() = runTest {
        val httpClient = CapturingExternalTranscriptionHttpClient(deepgramResponse)
        val provider = DeepgramAudioTranscriptionProvider(
            context = contextWithAudio(),
            apiKey = "deepgram-key",
            model = "nova-2",
            httpClient = httpClient
        )

        val result = provider.transcribe(AudioTranscriptionRequest(listOf(AudioSource(Uri.parse("content://audio/track.wav"), "track.wav"))))

        assertTrue(result is TranscriptionResult.Success)
        assertEquals("https://api.deepgram.com/v1/listen?model=nova-2&smart_format=true", httpClient.byteUrls.single())
    }

    private fun contextWithAudio(): Context {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
        every { resolver.getType(any()) } returns "audio/wav"

        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        return context
    }

    private class CapturingExternalTranscriptionHttpClient(
        private val response: String
    ) : ExternalTranscriptionHttpClient {
        val multipartFields = mutableListOf<Map<String, String>>()
        val byteUrls = mutableListOf<String>()

        override suspend fun postMultipart(
            url: String,
            headers: Map<String, String>,
            fields: Map<String, String>,
            fileFieldName: String,
            fileName: String,
            contentType: String,
            bytes: ByteArray
        ): String {
            multipartFields += fields
            return response
        }

        override suspend fun postBytes(
            url: String,
            headers: Map<String, String>,
            contentType: String,
            bytes: ByteArray
        ): String {
            byteUrls += url
            return response
        }
    }
}

private const val openAiResponse = """
    {"text":"hello","words":[{"start":0.0,"end":1.0,"word":"hello"}]}
"""

private const val deepgramResponse = """
    {"results":{"channels":[{"alternatives":[{"transcript":"hello","words":[{"start":0.0,"end":1.0,"word":"hello","punctuated_word":"hello"}]}]}]}}
"""
