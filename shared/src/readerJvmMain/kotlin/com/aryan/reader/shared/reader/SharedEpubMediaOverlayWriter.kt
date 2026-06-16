package com.aryan.reader.shared.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.jsoup.parser.Tag
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object SharedEpubMediaOverlayWriter {
    fun extractSentences(source: File): List<SharedEpubMediaOverlayContentSentences> {
        require(source.isFile) { "EPUB source does not exist: ${source.path}" }
        ZipFile(source).use { zip ->
            val opfPath = zip.findOpfPath() ?: error("EPUB package document was not found.")
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB package document entry is missing.")
            val opfBase = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            val manifest = parseManifest(zip.readUtf8(opfEntry))
            return manifest.values
                .filter { it.isContentDocument() }
                .mapIndexedNotNull { index, item ->
                    val entry = zip.getEntry(resolveZipPath(opfBase, item.href)) ?: return@mapIndexedNotNull null
                    val marked = SharedEpubSentenceMarkup.markSentences(zip.readUtf8(entry), "episteme-sync-s${index + 1}-")
                    SharedEpubMediaOverlayContentSentences(item.href, marked.sentences)
                }
                .filter { it.sentences.isNotEmpty() }
        }
    }

    fun rewrite(
        source: File,
        destination: File,
        request: SharedEpubMediaOverlayRequest
    ): SharedEpubMediaOverlayWriteResult {
        require(source.isFile) { "EPUB source does not exist: ${source.path}" }
        require(request.audioFiles.isNotEmpty()) { "At least one audio file is required." }
        require(request.clips.isNotEmpty()) { "At least one media overlay clip is required." }
        destination.parentFile?.mkdirs()

        ZipFile(source).use { zip ->
            val opfPath = zip.findOpfPath() ?: error("EPUB package document was not found.")
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB package document entry is missing.")
            val originalOpf = zip.readUtf8(opfEntry)
            val opfBase = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            val clipsByContentHref = request.clips.groupBy { it.contentHref }
            val manifest = parseManifest(originalOpf)

            val markedContent = mutableMapOf<String, Pair<String, List<SharedEpubMediaOverlaySentence>>>()
            clipsByContentHref.keys.forEachIndexed { index, href ->
                val entryName = resolveZipPath(opfBase, href)
                val entry = zip.getEntry(entryName) ?: error("Missing EPUB content document: $entryName")
                val marked = SharedEpubSentenceMarkup.markSentences(zip.readUtf8(entry), "episteme-sync-s${index + 1}-")
                markedContent[entryName] = marked.xhtml to marked.sentences
            }

            val audioPaths = request.audioFiles.map { audio -> "${opfBase.withTrailingSlash()}episteme-sync/audio/${audio.fileName.safePathName()}" }
            val smilByContentHref = clipsByContentHref.keys.mapIndexed { index, href ->
                href to "${opfBase.withTrailingSlash()}episteme-sync/smil/overlay-${index + 1}.smil"
            }.toMap()
            val smilBytesByPath = smilByContentHref.map { (href, smilPath) ->
                smilPath to createSmil(
                    contentHref = href,
                    clips = clipsByContentHref.getValue(href),
                    audioFiles = request.audioFiles,
                    smilPath = smilPath,
                    opfBase = opfBase
                ).toByteArray(StandardCharsets.UTF_8)
            }.toMap()
            val updatedOpf = rewriteOpf(
                opf = originalOpf,
                manifest = manifest,
                opfBase = opfBase,
                contentHrefs = clipsByContentHref.keys.toList(),
                smilByContentHref = smilByContentHref,
                audioFiles = request.audioFiles,
                audioPaths = audioPaths.map { it.removePrefix(opfBase.withTrailingSlash()) },
                totalDuration = request.clips.maxOf { it.clipEndSeconds }
            ).toByteArray(StandardCharsets.UTF_8)

            if (destination.exists()) destination.delete()
            ZipOutputStream(destination.outputStream().buffered()).use { output ->
                val entries = zip.entries().asSequence().toList()
                entries.firstOrNull { it.name == "mimetype" }?.let { mimetype ->
                    output.putStoredEntry(mimetype.name, zip.readBytes(mimetype), mimetype.time)
                }
                entries.forEach { entry ->
                    when {
                        entry.name == "mimetype" -> Unit
                        entry.name == opfPath -> output.putDeflatedEntry(entry.name, updatedOpf, entry.time)
                        markedContent.containsKey(entry.name) -> output.putDeflatedEntry(entry.name, markedContent.getValue(entry.name).first.toByteArray(StandardCharsets.UTF_8), entry.time)
                        entry.isDirectory -> output.putDirectoryEntry(entry)
                        entry.name.startsWith("${opfBase.withTrailingSlash()}episteme-sync/") -> Unit
                        else -> output.putCopiedEntry(entry, zip.readBytes(entry))
                    }
                }
                request.audioFiles.zip(audioPaths).forEach { (audio, path) ->
                    output.putDeflatedEntry(path, audio.file.readBytes(), -1L)
                }
                smilBytesByPath.forEach { (path, bytes) ->
                    output.putDeflatedEntry(path, bytes, -1L)
                }
            }

            return SharedEpubMediaOverlayWriteResult(
                opfPath = opfPath,
                smilPaths = smilByContentHref.values.toList(),
                audioPaths = audioPaths,
                markedSentences = markedContent.mapKeys { (path, _) -> path.removePrefix(opfBase.withTrailingSlash()) }
                    .mapValues { (_, value) -> value.second }
            )
        }
    }

    private fun rewriteOpf(
        opf: String,
        manifest: Map<String, ManifestItem>,
        opfBase: String,
        contentHrefs: List<String>,
        smilByContentHref: Map<String, String>,
        audioFiles: List<SharedEpubMediaOverlayAudioFile>,
        audioPaths: List<String>,
        totalDuration: Double
    ): String {
        val document = Jsoup.parse(opf, "", Parser.xmlParser())
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false).charset(StandardCharsets.UTF_8)
        val pkg = document.getAllElements().firstOrNull { it.localNameEquals("package") } ?: error("Missing OPF package element")
        pkg.attr("version", "3.0")
        val metadata = document.getAllElements().firstOrNull { it.localNameEquals("metadata") }
            ?: Element(Tag.valueOf("metadata"), "").also { pkg.prependChild(it) }
        val manifestElement = document.getAllElements().firstOrNull { it.localNameEquals("manifest") }
            ?: Element(Tag.valueOf("manifest"), "").also { pkg.appendChild(it) }

        audioFiles.zip(audioPaths).forEach { (audio, href) ->
            manifestElement.appendElement("item")
                .attr("id", audio.id)
                .attr("href", href)
                .attr("media-type", audio.mediaType)
        }

        smilByContentHref.forEach { (contentHref, smilPath) ->
            val contentItem = manifest.values.firstOrNull { it.href == contentHref }
            val smilId = "episteme-sync-smil-${contentItem?.id ?: contentHref.hashCode().toString().replace('-', 'x')}"
            manifestElement.appendElement("item")
                .attr("id", smilId)
                .attr("href", smilPath.removePrefix(opfBase.withTrailingSlash()))
                .attr("media-type", "application/smil+xml")
            document.getAllElements()
                .firstOrNull { it.localNameEquals("item") && it.attr("href") == contentHref }
                ?.attr("media-overlay", smilId)
        }

        metadata.appendElement("meta")
            .attr("property", "media:duration")
            .text(formatClock(totalDuration))

        return document.outerHtml()
    }

    private fun createSmil(
        contentHref: String,
        clips: List<SharedEpubMediaOverlayClip>,
        audioFiles: List<SharedEpubMediaOverlayAudioFile>,
        smilPath: String,
        opfBase: String
    ): String {
        val audioById = audioFiles.associateBy { it.id }
        val smilDir = smilPath.substringBeforeLast('/', missingDelimiterValue = "")
        val body = clips.joinToString(separator = "") { clip ->
            val audio = audioById.getValue(clip.audioFileId)
            val audioPath = "${opfBase.withTrailingSlash()}episteme-sync/audio/${audio.fileName.safePathName()}"
            val audioRef = relativePath(fromDir = smilDir, to = audioPath)
            """
            <par id="par-${clip.sentenceId}"><text src="${contentHref.xmlEscape()}#${clip.sentenceId.xmlEscape()}"/><audio src="${audioRef.xmlEscape()}" clipBegin="${formatSeconds(clip.clipBeginSeconds)}" clipEnd="${formatSeconds(clip.clipEndSeconds)}"/></par>
            """.trimIndent()
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <smil xmlns="http://www.w3.org/ns/SMIL" version="3.0"><body><seq epub:textref="${contentHref.xmlEscape()}" xmlns:epub="http://www.idpf.org/2007/ops">$body</seq></body></smil>
        """.trimIndent()
    }

    private fun parseManifest(opf: String): Map<String, ManifestItem> {
        val document = Jsoup.parse(opf, "", Parser.xmlParser())
        return document.getAllElements()
            .filter { it.localNameEquals("item") && it.hasAttr("id") && it.hasAttr("href") }
            .associate { item -> item.attr("id") to ManifestItem(item.attr("id"), item.attr("href"), item.attr("media-type").takeIf { value -> value.isNotBlank() }) }
    }

    private data class ManifestItem(val id: String, val href: String, val mediaType: String?) {
        fun isContentDocument(): Boolean = mediaType == "application/xhtml+xml" ||
            href.endsWith(".xhtml", ignoreCase = true) ||
            href.endsWith(".html", ignoreCase = true)
    }
}

private fun ZipFile.findOpfPath(): String? {
    val container = getEntry("META-INF/container.xml")?.let { readUtf8(it) }
    val declared = Regex("""<rootfile\b[^>]*\bfull-path=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        .find(container.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.trimStart('/')
        ?.takeIf { it.isNotBlank() }
    if (declared != null && getEntry(declared) != null) return declared
    return entries().asSequence().map { it.name }.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
}

private fun resolveZipPath(base: String, href: String): String =
    (base.withTrailingSlash() + href).split('/').fold(mutableListOf<String>()) { parts, part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
        parts
    }.joinToString("/")

private fun relativePath(fromDir: String, to: String): String {
    val from = fromDir.split('/').filter { it.isNotBlank() }
    val target = to.split('/').filter { it.isNotBlank() }
    val common = from.zip(target).takeWhile { it.first == it.second }.size
    return (List(from.size - common) { ".." } + target.drop(common)).joinToString("/")
}

private fun String.withTrailingSlash(): String = if (isBlank()) "" else trimEnd('/') + "/"

private fun String.safePathName(): String = substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun String.xmlEscape(): String = replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

private fun formatSeconds(value: Double): String = String.format(Locale.US, "%.3fs", value.coerceAtLeast(0.0))

private fun formatClock(value: Double): String = String.format(Locale.US, "%.3fs", value.coerceAtLeast(0.0))

private fun Element.localNameEquals(expected: String): Boolean = tagName().substringAfter(':').equals(expected, ignoreCase = true)

private fun ZipFile.readUtf8(entry: ZipEntry): String = readBytes(entry).toString(StandardCharsets.UTF_8)

private fun ZipFile.readBytes(entry: ZipEntry): ByteArray = getInputStream(entry).use { it.readBytes() }

private fun ZipOutputStream.putDirectoryEntry(entry: ZipEntry) {
    val copy = ZipEntry(entry.name)
    if (entry.time >= 0L) copy.time = entry.time
    copy.comment = entry.comment
    copy.extra = entry.extra
    putNextEntry(copy)
    closeEntry()
}

private fun ZipOutputStream.putCopiedEntry(entry: ZipEntry, bytes: ByteArray) {
    if (entry.method == ZipEntry.STORED) putStoredEntry(entry.name, bytes, entry.time) else putDeflatedEntry(entry.name, bytes, entry.time)
}

private fun ZipOutputStream.putStoredEntry(name: String, bytes: ByteArray, time: Long) {
    val crc = CRC32().apply { update(bytes) }.value
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        this.crc = crc
        if (time >= 0L) this.time = time
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}

private fun ZipOutputStream.putDeflatedEntry(name: String, bytes: ByteArray, time: Long) {
    val entry = ZipEntry(name).apply {
        method = ZipEntry.DEFLATED
        if (time >= 0L) this.time = time
    }
    putNextEntry(entry)
    write(bytes)
    closeEntry()
}
