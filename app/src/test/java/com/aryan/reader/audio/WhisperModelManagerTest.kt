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
    fun selectedOrBundledModelFileCopiesBundledTinyWhenNoImportIsSelected() {
        val manager = WhisperModelManager(
            modelsDir = temp.newFolder("models"),
            preferences = MemoryPreferenceStore(),
            bundledModelName = "ggml-tiny.bin",
            bundledModelInput = { ByteArrayInputStream(validModelBytes()) }
        )

        val bundled = manager.selectedOrBundledModelFile()

        assertNotNull(bundled)
        assertEquals("ggml-tiny.bin", bundled?.name)
        assertEquals("ggml-tiny.bin", manager.selectedOrBundledModelName())
        assertNull(manager.selectedModelFile())
    }

    @Test
    fun selectedOrBundledModelFilePrefersImportedModelOverBundledTiny() {
        val manager = WhisperModelManager(
            modelsDir = temp.newFolder("models"),
            preferences = MemoryPreferenceStore(),
            bundledModelName = "ggml-tiny.bin",
            bundledModelInput = { ByteArrayInputStream(validModelBytes()) }
        )

        manager.importModel("custom.gguf") { ByteArrayInputStream(validModelBytes("GGUF")) }

        assertEquals("custom.gguf", manager.selectedOrBundledModelFile()?.name)
        assertEquals("custom.gguf", manager.selectedOrBundledModelName())
    }

    @Test
    fun selectedOrBundledModelFileReturnsNullWhenBundledTinyIsNotConfigured() {
        val manager = WhisperModelManager(
            modelsDir = temp.newFolder("models"),
            preferences = MemoryPreferenceStore()
        )

        assertNull(manager.selectedOrBundledModelFile())
        assertNull(manager.selectedOrBundledModelName())
    }

    @Test
    fun selectedOrBundledModelFileDeletesInvalidCopiedBundledTiny() {
        val modelsDir = temp.newFolder("models")
        val manager = WhisperModelManager(
            modelsDir = modelsDir,
            preferences = MemoryPreferenceStore(),
            bundledModelName = "ggml-tiny.bin",
            bundledModelInput = { ByteArrayInputStream(byteArrayOf(0, 0, 0, 0)) }
        )

        assertNull(manager.selectedOrBundledModelFile())
        assertFalse(File(modelsDir, "ggml-tiny.bin").exists())
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
