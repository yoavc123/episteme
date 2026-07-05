package com.aryan.reader.audio

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class EpubMediaOverlay(
    val opfPath: String,
    val clips: List<EpubMediaOverlayClip>
) {
    val isAvailable: Boolean = clips.isNotEmpty()
}

data class EpubMediaOverlayClip(
    val index: Int,
    val contentHref: String,
    val contentEntryName: String,
    val elementId: String?,
    val textSrc: String,
    val audioHref: String,
    val audioEntryName: String,
    val clipBeginSeconds: Double,
    val clipEndSeconds: Double
) {
    val durationSeconds: Double = (clipEndSeconds - clipBeginSeconds).coerceAtLeast(0.0)
}

class EpubMediaOverlayParser {
    fun parse(epubFile: File): EpubMediaOverlay {
        require(epubFile.isFile) { "EPUB file does not exist: ${epubFile.path}" }
        ZipFile(epubFile).use { zip ->
            val opfPath = zip.findOpfPath() ?: return EpubMediaOverlay("", emptyList())
            val opfEntry = zip.getEntry(opfPath) ?: return EpubMediaOverlay(opfPath, emptyList())
            return parsePackage(
                opfPath = opfPath,
                opfXml = zip.readUtf8(opfEntry),
                readEntry = { path -> zip.getEntry(path)?.let(zip::readUtf8) },
                hasEntry = { path -> zip.getEntry(path) != null }
            )
        }
    }

    fun parseExtracted(extractionRoot: File): EpubMediaOverlay {
        require(extractionRoot.isDirectory) { "EPUB extraction directory does not exist: ${extractionRoot.path}" }
        val opfPath = findExtractedOpfPath(extractionRoot) ?: return EpubMediaOverlay("", emptyList())
        val opfFile = safeFile(extractionRoot, opfPath)?.takeIf { it.isFile } ?: return EpubMediaOverlay(opfPath, emptyList())
        return parsePackage(
            opfPath = opfPath,
            opfXml = opfFile.readText(StandardCharsets.UTF_8),
            readEntry = { path -> safeFile(extractionRoot, path)?.takeIf { it.isFile }?.readText(StandardCharsets.UTF_8) },
            hasEntry = { path -> safeFile(extractionRoot, path)?.isFile == true }
        )
    }

    private fun parsePackage(
        opfPath: String,
        opfXml: String,
        readEntry: (String) -> String?,
        hasEntry: (String) -> Boolean
    ): EpubMediaOverlay {
        val opfBase = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val manifest = parseManifest(opfXml)
        val contentItems = manifest.values.filter { it.mediaOverlayId.isNotBlank() }
        if (contentItems.isEmpty()) return EpubMediaOverlay(opfPath, emptyList())

        val clips = mutableListOf<EpubMediaOverlayClip>()
        contentItems.forEach { contentItem ->
            val smilItem = manifest[contentItem.mediaOverlayId] ?: return@forEach
            if (!smilItem.isSmil()) return@forEach
            val smilPath = resolveZipPath(opfBase, smilItem.href) ?: return@forEach
            val smilXml = readEntry(smilPath) ?: return@forEach
            val contentEntry = resolveZipPath(opfBase, contentItem.href) ?: return@forEach
            parseSmil(
                smilXml = smilXml,
                smilPath = smilPath,
                contentHref = contentItem.href,
                contentEntry = contentEntry,
                hasEntry = hasEntry
            ).forEach { pending ->
                clips += pending.toClip(clips.size)
            }
        }
        return EpubMediaOverlay(opfPath, clips)
    }

    private fun parseManifest(opfXml: String): Map<String, ManifestItem> {
        val document = Jsoup.parse(opfXml, "", Parser.xmlParser())
        return document.getAllElements()
            .filter { it.localNameEquals("item") && it.hasAttr("id") && it.hasAttr("href") }
            .associate { item ->
                val id = item.attr("id")
                id to ManifestItem(
                    id = id,
                    href = item.attr("href"),
                    mediaType = item.attr("media-type"),
                    mediaOverlayId = item.attr("media-overlay")
                )
            }
    }

    private fun parseSmil(
        smilXml: String,
        smilPath: String,
        contentHref: String,
        contentEntry: String,
        hasEntry: (String) -> Boolean
    ): List<PendingClip> {
        val smilDir = smilPath.substringBeforeLast('/', missingDelimiterValue = "")
        val document = Jsoup.parse(smilXml, "", Parser.xmlParser())
        return document.getAllElements()
            .filter { it.localNameEquals("par") }
            .mapNotNull { par ->
                val text = par.firstDescendant("text") ?: return@mapNotNull null
                val audio = par.firstDescendant("audio") ?: return@mapNotNull null
                val textSrc = text.attr("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val audioSrc = audio.attr("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val audioEntry = resolveZipPath(smilDir, audioSrc) ?: return@mapNotNull null
                if (!hasEntry(audioEntry)) return@mapNotNull null

                val textPath = textSrc.substringBefore('#').takeIf { it.isNotBlank() }
                val resolvedContent = when {
                    textPath == null -> contentEntry
                    textPath == contentHref -> contentEntry
                    else -> resolveZipPath(smilDir, textPath)?.takeIf(hasEntry) ?: contentEntry
                }
                PendingClip(
                    contentHref = contentHref,
                    contentEntryName = resolvedContent,
                    elementId = textSrc.substringAfter('#', missingDelimiterValue = "").takeIf { it.isNotBlank() },
                    textSrc = textSrc,
                    audioHref = audioSrc,
                    audioEntryName = audioEntry,
                    clipBeginSeconds = parseClockValue(audio.attr("clipBegin")) ?: 0.0,
                    clipEndSeconds = parseClockValue(audio.attr("clipEnd")) ?: return@mapNotNull null
                )
            }
            .filter { it.clipEndSeconds >= it.clipBeginSeconds }
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val mediaOverlayId: String
    ) {
        fun isSmil(): Boolean = mediaType.equals("application/smil+xml", ignoreCase = true) || href.endsWith(".smil", ignoreCase = true)
    }

    private data class PendingClip(
        val contentHref: String,
        val contentEntryName: String,
        val elementId: String?,
        val textSrc: String,
        val audioHref: String,
        val audioEntryName: String,
        val clipBeginSeconds: Double,
        val clipEndSeconds: Double
    ) {
        fun toClip(index: Int) = EpubMediaOverlayClip(
            index = index,
            contentHref = contentHref,
            contentEntryName = contentEntryName,
            elementId = elementId,
            textSrc = textSrc,
            audioHref = audioHref,
            audioEntryName = audioEntryName,
            clipBeginSeconds = clipBeginSeconds,
            clipEndSeconds = clipEndSeconds
        )
    }
}

private fun ZipFile.findOpfPath(): String? {
    val container = getEntry("META-INF/container.xml")?.let { readUtf8(it) } ?: return null
    return rootfilePath(container)?.takeIf { getEntry(it) != null }
}

private fun findExtractedOpfPath(extractionRoot: File): String? {
    val container = safeFile(extractionRoot, "META-INF/container.xml")?.takeIf { it.isFile }?.readText(StandardCharsets.UTF_8) ?: return null
    return rootfilePath(container)?.takeIf { safeFile(extractionRoot, it)?.isFile == true }
}

private fun rootfilePath(containerXml: String): String? = Regex("""<rootfile\b[^>]*\bfull-path=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    .find(containerXml)
    ?.groupValues
    ?.getOrNull(1)
    ?.trim()
    ?.trimStart('/')
    ?.takeIf { it.isNotBlank() && resolveZipPath("", it) == it }

internal fun resolveZipPath(base: String, href: String): String? {
    val path = href.substringBefore('#').substringBefore('?').replace('\\', '/')
    if (path.isBlank() || path.startsWith('/') || path.contains(':')) return null
    val raw = (base.withTrailingSlash() + path).split('/')
    val parts = mutableListOf<String>()
    raw.forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return parts.joinToString("/").takeIf { it.isNotBlank() }
}

private fun safeFile(root: File, path: String): File? {
    val normalized = resolveZipPath("", path) ?: return null
    val rootCanonical = root.canonicalFile
    val candidate = File(rootCanonical, normalized).canonicalFile
    return candidate.takeIf { it.path == rootCanonical.path || it.path.startsWith(rootCanonical.path + File.separator) }
}

private fun parseClockValue(value: String): Double? {
    val trimmed = value.trim().removePrefix("npt=").lowercase(Locale.US)
    if (trimmed.isBlank()) return null
    if (trimmed.endsWith("ms")) return trimmed.removeSuffix("ms").toDoubleOrNull()?.div(1000.0)
    if (trimmed.endsWith("s")) return trimmed.removeSuffix("s").toDoubleOrNull()
    if (trimmed.endsWith("min")) return trimmed.removeSuffix("min").toDoubleOrNull()?.times(60.0)
    if (trimmed.endsWith("h")) return trimmed.removeSuffix("h").toDoubleOrNull()?.times(3600.0)
    val fields = trimmed.split(':')
    if (fields.size > 1) {
        var multiplier = 1.0
        var total = 0.0
        fields.asReversed().forEach { field ->
            val number = field.toDoubleOrNull() ?: return null
            total += number * multiplier
            multiplier *= 60.0
        }
        return total
    }
    return trimmed.toDoubleOrNull()
}

private fun Element.firstDescendant(localName: String): Element? = getAllElements().firstOrNull { it.localNameEquals(localName) }

private fun Element.localNameEquals(expected: String): Boolean = tagName().substringAfter(':').equals(expected, ignoreCase = true)

private fun String.withTrailingSlash(): String = if (isBlank()) "" else trimEnd('/') + "/"

private fun ZipFile.readUtf8(entry: ZipEntry): String = getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
