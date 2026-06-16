package com.aryan.reader.audio

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WhisperModelManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun importModelStoresValidModelAndSelectsIt() {
        val manager = WhisperModelManager(temp.newFolder("models"), MemoryPreferenceStore())

        val result = manager.importModel("ggml-tiny.bin") {
            ByteArrayInputStream(validModelBytes())
        }

        assertTrue(result is WhisperModelManager.ImportResult.Imported)
        val imported = result as WhisperModelManager.ImportResult.Imported
        assertEquals("ggml-tiny.bin", imported.model.name)
        assertEquals(imported.model.file.absolutePath, manager.selectedModelFile()?.absolutePath)
        assertEquals(1, manager.listModels().size)
        assertEquals(true, manager.listModels().single().selected)
    }

    @Test
    fun importModelRejectsNonModelExtensions() {
        val manager = WhisperModelManager(temp.newFolder("models"), MemoryPreferenceStore())

        val result = manager.importModel("notes.txt") {
            ByteArrayInputStream(validModelBytes())
        }

        assertTrue(result is WhisperModelManager.ImportResult.Invalid)
        assertNull(manager.selectedModelFile())
    }

    @Test
    fun importModelRejectsTinyOrEmptyModelFiles() {
        val manager = WhisperModelManager(temp.newFolder("models"), MemoryPreferenceStore())

        val result = manager.importModel("ggml-tiny.bin") {
            ByteArrayInputStream(byteArrayOf(0, 0, 0, 0))
        }

        assertTrue(result is WhisperModelManager.ImportResult.Invalid)
        assertNull(manager.selectedModelFile())
    }

    @Test
    fun selectModelPersistsExistingModel() {
        val modelsDir = temp.newFolder("models")
        val prefs = MemoryPreferenceStore()
        val file = File(modelsDir, "model.gguf").apply { writeBytes(validModelBytes("GGUF")) }
        val manager = WhisperModelManager(modelsDir, prefs)

        val result = manager.selectModel(file)

        assertTrue(result is WhisperModelManager.ImportResult.Imported)
        assertNotNull(manager.selectedModelFile())
        assertEquals(file.absolutePath, prefs.getString("selected_model_path"))
    }

    private fun validModelBytes(header: String = "ggml"): ByteArray =
        header.toByteArray(Charsets.US_ASCII) + ByteArray(2048) { 1 }
}

private class MemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
    override fun remove(key: String) {
        values.remove(key)
    }
}
