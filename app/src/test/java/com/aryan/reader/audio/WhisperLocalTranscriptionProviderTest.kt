package com.aryan.reader.audio

import android.content.Context
import android.net.Uri
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperLocalTranscriptionProviderTest {
    @Test
    fun returnsMissingModelErrorWithoutCrashing() = runTest {
        val manager = WhisperModelManager(Files.createTempDirectory("models").toFile(), MemoryStore())
        val provider = WhisperLocalTranscriptionProvider(
            context = mockk<Context>(relaxed = true),
            modelManager = manager,
            nativeBridge = FakeNativeBridge(),
            decoder = FakeDecoder()
        )

        val result = provider.transcribe(
            AudioTranscriptionRequest(listOf(AudioSource(mockk<Uri>(relaxed = true))))
        )

        assertTrue(result is TranscriptionResult.Failure)
        assertTrue((result as TranscriptionResult.Failure).error is TranscriptionError.MissingLocalModel)
    }

    @Test
    fun normalizesNativeSegmentTimesToChunkOffset() = runTest {
        val manager = WhisperModelManager(Files.createTempDirectory("models").toFile(), MemoryStore())
        manager.importModel("ggml-tiny.bin") { ByteArrayInputStream(modelBytes()) }
        val provider = WhisperLocalTranscriptionProvider(
            context = mockk<Context>(relaxed = true),
            modelManager = manager,
            nativeBridge = FakeNativeBridge(),
            decoder = FakeDecoder()
        )

        val result = provider.transcribe(
            AudioTranscriptionRequest(listOf(AudioSource(mockk<Uri>(relaxed = true))))
        )

        assertTrue(result is TranscriptionResult.Success)
        val segment = (result as TranscriptionResult.Success).segments.single()
        assertEquals(12.5, segment.startSeconds, 0.0001)
        assertEquals(13.5, segment.endSeconds, 0.0001)
        assertEquals("hello", segment.text)
        assertEquals(12.5, segment.words.single().startSeconds, 0.0001)
    }

    @Test
    fun usesBundledModelWhenNoImportedModelExists() = runTest {
        val manager = WhisperModelManager(
            modelsDir = Files.createTempDirectory("models").toFile(),
            preferences = MemoryStore(),
            bundledModelName = "ggml-tiny.bin",
            bundledModelInput = { ByteArrayInputStream(modelBytes()) }
        )
        val provider = WhisperLocalTranscriptionProvider(
            context = mockk<Context>(relaxed = true),
            modelManager = manager,
            nativeBridge = FakeNativeBridge(),
            decoder = FakeDecoder()
        )

        val result = provider.transcribe(
            AudioTranscriptionRequest(listOf(AudioSource(mockk<Uri>(relaxed = true))))
        )

        assertTrue(result is TranscriptionResult.Success)
        assertEquals("hello", (result as TranscriptionResult.Success).segments.single().text)
    }

    @Test
    fun rethrowsCancellationAndFreesNativeModel() = runTest {
        val manager = WhisperModelManager(Files.createTempDirectory("models").toFile(), MemoryStore())
        manager.importModel("ggml-tiny.bin") { ByteArrayInputStream(modelBytes()) }
        val nativeBridge = FakeNativeBridge(cancelDuringTranscription = true)
        val provider = WhisperLocalTranscriptionProvider(
            context = mockk<Context>(relaxed = true),
            modelManager = manager,
            nativeBridge = nativeBridge,
            decoder = FakeDecoder()
        )

        var cancelled = false
        try {
            provider.transcribe(AudioTranscriptionRequest(listOf(AudioSource(mockk<Uri>(relaxed = true)))))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(listOf(1L), nativeBridge.freedHandles)
    }

    private fun modelBytes(): ByteArray = "ggml".toByteArray(Charsets.US_ASCII) + ByteArray(2048) { 1 }
}

private class FakeDecoder : AudioDecodeUtils.Decoder {
    override fun decode(context: Context, source: Uri): Sequence<AudioDecodeUtils.PcmChunk> = sequenceOf(
        AudioDecodeUtils.PcmChunk(FloatArray(16_000), startSeconds = 12.0, durationSeconds = 1.0)
    )
}

private class FakeNativeBridge(
    private val cancelDuringTranscription: Boolean = false
) : WhisperNativeBridgeApi {
    val freedHandles = mutableListOf<Long>()

    override fun loadModel(path: String): Long = 1L

    override fun freeModel(handle: Long) {
        freedHandles += handle
    }

    override fun transcribe(
        handle: Long,
        pcm: FloatArray,
        sampleRate: Int,
        language: String?
    ): List<NativeWhisperSegment> {
        if (cancelDuringTranscription) throw CancellationException("cancelled")
        return listOf(
            NativeWhisperSegment(
                startSeconds = 0.5,
                endSeconds = 1.5,
                text = "hello",
                words = listOf(NativeWhisperWord(0.5, 0.9, "hello"))
            )
        )
    }
}

private class MemoryStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
    override fun remove(key: String) {
        values.remove(key)
    }
}
