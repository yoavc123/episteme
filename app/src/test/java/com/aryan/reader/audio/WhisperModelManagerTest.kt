package com.aryan.reader.audio

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun selectedModelFileCopiesAndSelectsBundledModelOnFreshInstall() {
        val modelsDir = temp.newFolder("models")
        val prefs = MemoryPreferenceStore()
        val manager = WhisperModelManager(modelsDir, prefs) {
            ByteArrayInputStream(validModelBytes())
        }

        val selected = manager.selectedModelFile()

        val bundledFile = File(modelsDir, "ggml-tiny.bin")
        assertEquals(bundledFile.absolutePath, selected?.absolutePath)
        assertTrue(bundledFile.isFile)
        assertEquals(bundledFile.absolutePath, prefs.getString("selected_model_path"))
        assertEquals(listOf(bundledFile.absolutePath), manager.listModels().map { it.file.absolutePath })
        assertTrue(manager.listModels().single().selected)
    }

    @Test
    fun selectedModelFileKeepsExistingSelectedModelOverBundledModel() {
        val modelsDir = temp.newFolder("models")
        val selectedFile = File(modelsDir, "imported.gguf").apply { writeBytes(validModelBytes("GGUF")) }
        val manager = WhisperModelManager(modelsDir, MemoryPreferenceStore()) {
            error("Bundled model should not be read when a selected model exists.")
        }
        manager.selectModel(selectedFile)

        val selected = manager.selectedModelFile()

        assertEquals(selectedFile.absolutePath, selected?.absolutePath)
        assertFalse(File(modelsDir, "ggml-tiny.bin").exists())
    }

    @Test
    fun selectedModelFileDeletesInvalidBundledModelAndLeavesNoSelection() {
        val modelsDir = temp.newFolder("models")
        val prefs = MemoryPreferenceStore()
        val manager = WhisperModelManager(modelsDir, prefs) {
            ByteArrayInputStream(byteArrayOf(0, 0, 0, 0))
        }

        val selected = manager.selectedModelFile()

        assertNull(selected)
        assertFalse(File(modelsDir, "ggml-tiny.bin").exists())
        assertNull(prefs.getString("selected_model_path"))
        assertTrue(manager.listModels().isEmpty())
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
