package com.aryan.reader.shared.reader

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedEpubMediaOverlayWriterTest {
    @Test
    fun `rewrite creates epub3 media overlay assets and keeps mimetype stored`() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "synced.epub")
        val audio = File(dir, "track.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        writeEpub(source)

        val result = SharedEpubMediaOverlayWriter.rewrite(
            source = source,
            destination = output,
            request = SharedEpubMediaOverlayRequest(
                audioFiles = listOf(SharedEpubMediaOverlayAudioFile("audio-1", "track.mp3", "audio/mpeg", audio)),
                clips = listOf(
                    SharedEpubMediaOverlayClip("chapter.xhtml", "episteme-sync-s1-00001", "audio-1", 0.0, 1.25)
                )
            )
        )

        ZipInputStream(output.inputStream()).use { zip ->
            val first = assertNotNull(zip.nextEntry)
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
        }
        ZipFile(output).use { zip ->
            val opf = zip.readText("OEBPS/content.opf")
            val chapter = zip.readText("OEBPS/chapter.xhtml")
            val smil = zip.readText("OEBPS/episteme-sync/smil/overlay-1.smil")

            assertTrue(opf.contains("version=\"3.0\""))
            assertTrue(opf.contains("media-overlay=\"episteme-sync-smil-chapter\""))
            assertTrue(opf.contains("application/smil+xml"))
            assertTrue(opf.contains("audio/mpeg"))
            assertTrue(chapter.contains("id=\"episteme-sync-s1-00001\""))
            assertTrue(chapter.contains("<em><span id=\"episteme-sync-s1-00002\">important</span></em>"))
            assertTrue(smil.contains("chapter.xhtml#episteme-sync-s1-00001"))
            assertTrue(smil.contains("clipEnd=\"1.250s\""))
            assertNotNull(zip.getEntry("OEBPS/episteme-sync/audio/track.mp3"))
        }
        assertEquals(listOf("OEBPS/episteme-sync/smil/overlay-1.smil"), result.smilPaths)
    }

    private fun writeEpub(target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putStoredText("mimetype", "application/epub+zip")
            zip.putText(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf" /></rootfiles></container>"""
            )
            zip.putText(
                "OEBPS/content.opf",
                """
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata><dc:title>Book</dc:title></metadata>
                  <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" /></manifest>
                  <spine><itemref idref="chapter" /></spine>
                </package>
                """.trimIndent()
            )
            zip.putText(
                "OEBPS/chapter.xhtml",
                """<html><body><p>Hello <em>important</em> world.</p><script>Do not mark.</script></body></html>"""
            )
        }
    }

    private fun ZipFile.readText(name: String): String =
        getInputStream(assertNotNull(getEntry(name))).reader().readText()

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putStoredText(name: String, text: String) {
        val bytes = text.toByteArray()
        val crc = CRC32().apply { update(bytes) }.value
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private inline fun withTempDir(block: (File) -> Unit) {
        val dir = createTempDir(prefix = "epub-media-overlay-test")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
