package com.aryan.reader.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSyncSheetTest {
    @Test
    fun localWhisperRequiresAudioAndLocalModel() {
        assertFalse(
            canStartAudioSync(
                hasAudio = true,
                selectedProvider = AudioSyncProvider.LOCAL_WHISPER,
                localModelReady = false
            )
        )
        assertTrue(
            canStartAudioSync(
                hasAudio = true,
                selectedProvider = AudioSyncProvider.LOCAL_WHISPER,
                localModelReady = true
            )
        )
    }

    @Test
    fun cloudProvidersDoNotRequireLocalModel() {
        assertTrue(
            canStartAudioSync(
                hasAudio = true,
                selectedProvider = AudioSyncProvider.OPENAI,
                localModelReady = false
            )
        )
        assertTrue(
            canStartAudioSync(
                hasAudio = true,
                selectedProvider = AudioSyncProvider.DEEPGRAM,
                localModelReady = false
            )
        )
    }

    @Test
    fun noProviderCanStartWithoutAudio() {
        assertFalse(
            canStartAudioSync(
                hasAudio = false,
                selectedProvider = AudioSyncProvider.OPENAI,
                localModelReady = true
            )
        )
    }

    @Test
    fun sheetStartsAtAudioStepUntilASessionExists() {
        assertEquals(AudioSyncSheetStep.AUDIO, initialAudioSyncSheetStep(null))
        assertEquals(AudioSyncSheetStep.PROGRESS, initialAudioSyncSheetStep(session(AudioSyncStatus.PENDING)))
        assertEquals(AudioSyncSheetStep.PROGRESS, initialAudioSyncSheetStep(session(AudioSyncStatus.COMPLETED)))
    }

    @Test
    fun retryRestartsOnlyWhenCurrentSelectionCanStart() {
        assertTrue(
            shouldRestartAudioSyncOnRetry(
                hasAudio = true,
                selectedProvider = AudioSyncProvider.OPENAI,
                localModelReady = false
            )
        )
        assertFalse(
            shouldRestartAudioSyncOnRetry(
                hasAudio = false,
                selectedProvider = AudioSyncProvider.OPENAI,
                localModelReady = true
            )
        )
        assertFalse(
            shouldRestartAudioSyncOnRetry(
                hasAudio = true,
                selectedProvider = AudioSyncProvider.LOCAL_WHISPER,
                localModelReady = false
            )
        )
    }

    private fun session(status: AudioSyncStatus) = AudioSyncSession(
        sessionId = "session-1",
        bookId = "book-1",
        bookTitle = "Book",
        sourceEpubUri = "file:///book.epub",
        audioSources = emptyList(),
        provider = AudioSyncProvider.LOCAL_WHISPER,
        status = status,
        progressPercent = 0,
        currentStep = null,
        outputEpubPath = null,
        errorMessage = null,
        createdAt = 1L,
        updatedAt = 1L,
        cancelRequested = false,
    )
}
