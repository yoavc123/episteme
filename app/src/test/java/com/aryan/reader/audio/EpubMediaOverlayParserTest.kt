package com.aryan.reader.audio

import com.aryan.reader.shared.reader.SharedEpubMediaOverlayAudioFile
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayClip
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayRequest
import com.aryan.reader.shared.reader.SharedEpubMediaOverlayWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class EpubMediaOverlayParserTest {
    @Test
    fun parsesGeneratedWriterMediaOverlay() = withTempDir { dir ->
        val source = File(dir, "source.epub")
        val output = File(dir, "synced.epub")
        val audio = File(dir, "track.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        writeBaseEpub(source)

        SharedEpubMediaOverlayWriter.rewrite(
            source = source,
            destination = output,
            request = SharedEpubMediaOverlayRequest(
                audioFiles = listOf(SharedEpubMediaOverlayAudioFile("audio-1", "track.mp3", "audio/mpeg", audio)),
                clips = listOf(SharedEpubMediaOverlayClip("chapter.xhtml", "episteme-sync-s1-00001", "audio-1", 0.0, 1.25))
            )
        )

        val overlay = EpubMediaOverlayParser().parse(output)

        assertTrue(overlay.isAvailable)
        assertEquals("OEBPS/content.opf", overlay.opfPath)
        assertEquals(1, overlay.clips.size)
        val clip = overlay.clips.single()
        assertEquals("chapter.xhtml", clip.contentHref)
        assertEquals("OEBPS/chapter.xhtml", clip.contentEntryName)
        assertEquals("episteme-sync-s1-00001", clip.elementId)
        assertEquals("OEBPS/episteme-sync/audio/track.mp3", clip.audioEntryName)
        assertEquals(0.0, clip.clipBeginSeconds, 0.0001)
        assertEquals(1.25, clip.clipEndSeconds, 0.0001)
    }

    @Test
    fun parsesRelativeSmilPathsAndClockValues() = withTempDir { dir ->
        val epub = File(dir, "relative.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.putStoredText("mimetype", "application/epub+zip")
            zip.putText("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OPS/pkg/content.opf" /></rootfiles></container>""")
            zip.putText(
                "OPS/pkg/content.opf",
                """
                <package version="3.0">
                  <manifest>
                    <item id="chap" href="../text/chapter.xhtml" media-type="application/xhtml+xml" media-overlay="mo1" />
                    <item id="mo1" href="overlays/chapter.smil" media-type="application/smil+xml" />
                    <item id="aud" href="audio/track.mp3" media-type="audio/mpeg" />
                  </manifest>
                </package>
                """.trimIndent()
            )
            zip.putText("OPS/text/chapter.xhtml", "<html><body><p id='s1'>Hello</p></body></html>")
            zip.putText(
                "OPS/pkg/overlays/chapter.smil",
                """
                <smil><body><seq><par><text src="../text/chapter.xhtml#s1"/><audio src="../audio/track.mp3" clipBegin="00:00:01.500" clipEnd="00:00:03.000"/></par></seq></body></smil>
                """.trimIndent()
            )
            zip.putText("OPS/pkg/audio/track.mp3", "audio")
        }

        val overlay = EpubMediaOverlayParser().parse(epub)

        assertTrue(overlay.isAvailable)
        val clip = overlay.clips.single()
        assertEquals("OPS/text/chapter.xhtml", clip.contentEntryName)
        assertEquals("OPS/pkg/audio/track.mp3", clip.audioEntryName)
        assertEquals(1.5, clip.clipBeginSeconds, 0.0001)
        assertEquals(3.0, clip.clipEndSeconds, 0.0001)
    }

    @Test
    fun returnsEmptyForBookWithoutMediaOverlays() = withTempDir { dir ->
        val epub = File(dir, "source.epub")
        writeBaseEpub(epub)

        val overlay = EpubMediaOverlayParser().parse(epub)

        assertFalse(overlay.isAvailable)
        assertTrue(overlay.clips.isEmpty())
    }

    @Test
    fun rejectsUnsafeContainerRootfilePath() = withTempDir { dir ->
        val epub = File(dir, "unsafe.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.putStoredText("mimetype", "application/epub+zip")
            zip.putText("META-INF/container.xml", """<container><rootfiles><rootfile full-path="../content.opf" /></rootfiles></container>""")
            zip.putText("content.opf", "<package />")
        }

        val overlay = EpubMediaOverlayParser().parse(epub)

        assertFalse(overlay.isAvailable)
        assertEquals("", overlay.opfPath)
    }

    @Test
    fun skipsMalformedSmilClips() = withTempDir { dir ->
        val epub = File(dir, "malformed.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.putStoredText("mimetype", "application/epub+zip")
            zip.putText("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/content.opf" /></rootfiles></container>""")
            zip.putText(
                "OEBPS/content.opf",
                """
                <package><manifest>
                  <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" media-overlay="smil" />
                  <item id="smil" href="overlay.smil" media-type="application/smil+xml" />
                </manifest></package>
                """.trimIndent()
            )
            zip.putText("OEBPS/chapter.xhtml", "<html><body><p id='s1'>Hello</p></body></html>")
            zip.putText("OEBPS/overlay.smil", "<smil><body><par><text src='chapter.xhtml#s1'/><audio src='../bad.mp3' clipBegin='0s'/></par></body></smil>")
        }

        val overlay = EpubMediaOverlayParser().parse(epub)

        assertFalse(overlay.isAvailable)
    }

    private fun writeBaseEpub(target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putStoredText("mimetype", "application/epub+zip")
            zip.putText("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/content.opf" /></rootfiles></container>""")
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
            zip.putText("OEBPS/chapter.xhtml", "<html><body><p>Hello world.</p></body></html>")
        }
    }

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
        val dir = createTempDir(prefix = "epub-media-overlay-parser-test")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
