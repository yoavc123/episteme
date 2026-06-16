package com.aryan.reader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedAudioTtsOptionModelTest {
    @Test
    fun `option is hidden when reader is not eligible`() {
        val model = syncedAudioTtsOptionModel(eligible = false, available = true, isTtsActive = false)

        assertFalse(model.visible)
        assertFalse(model.canStartPlayback)
        assertFalse(model.canOpenSync)
    }

    @Test
    fun `option starts playback when synced audio exists`() {
        val model = syncedAudioTtsOptionModel(eligible = true, available = true, isTtsActive = false)

        assertTrue(model.visible)
        assertTrue(model.canStartPlayback)
        assertFalse(model.canOpenSync)
    }

    @Test
    fun `option opens sync when synced audio is missing`() {
        val model = syncedAudioTtsOptionModel(eligible = true, available = false, isTtsActive = false)

        assertTrue(model.visible)
        assertFalse(model.canStartPlayback)
        assertTrue(model.canOpenSync)
    }

    @Test
    fun `option disables actions during synthetic tts`() {
        val model = syncedAudioTtsOptionModel(eligible = true, available = true, isTtsActive = true)

        assertTrue(model.visible)
        assertFalse(model.canStartPlayback)
        assertFalse(model.canOpenSync)
    }
}
