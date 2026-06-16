package com.aryan.reader.shared.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.parser.Tag
import java.nio.charset.StandardCharsets

object SharedEpubSentenceMarkup {
    private val sentencePattern = Regex("[^.!?]+[.!?]?(?:\\s+|$)")
    private val excludedTags = setOf("script", "style", "head", "title", "nav")

    fun markSentences(xhtml: String, idPrefix: String): SharedEpubSentenceMarkupResult {
        val document = Jsoup.parse(xhtml, "", Parser.xmlParser())
        document.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .prettyPrint(false)
            .charset(StandardCharsets.UTF_8)

        val sentences = mutableListOf<SharedEpubMediaOverlaySentence>()
        val root = document.getAllElements().firstOrNull { it.localNameEquals("body") } ?: document
        var index = 0

        fun nextId(): String {
            index += 1
            return "$idPrefix${index.toString().padStart(5, '0')}"
        }

        fun visit(element: Element) {
            if (element.normalizedLocalName() in excludedTags) return
            val children = element.childNodes().toList()
            children.forEach { child ->
                when (child) {
                    is TextNode -> {
                        val replacements = child.toSentenceNodes(::nextId, sentences)
                        if (replacements.isNotEmpty()) child.replaceWith(replacements)
                    }
                    is Element -> visit(child)
                }
            }
        }

        visit(root)
        return SharedEpubSentenceMarkupResult(document.outerHtml(), sentences)
    }

    private fun TextNode.toSentenceNodes(
        idFactory: () -> String,
        sentences: MutableList<SharedEpubMediaOverlaySentence>
    ): List<Node> {
        val source = wholeText
        if (source.isBlank()) return emptyList()

        val matches = sentencePattern.findAll(source).toList()
        if (matches.isEmpty()) return emptyList()

        val nodes = mutableListOf<Node>()
        var cursor = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                nodes += TextNode(source.substring(cursor, match.range.first))
            }
            val text = match.value
            if (text.isBlank()) {
                nodes += TextNode(text)
            } else {
                val id = idFactory()
                sentences += SharedEpubMediaOverlaySentence(id, text.trim())
                nodes += Element(Tag.valueOf("span"), "").attr("id", id).text(text)
            }
            cursor = match.range.last + 1
        }
        if (cursor < source.length) nodes += TextNode(source.substring(cursor))
        return nodes
    }
}

private fun Node.replaceWith(replacements: List<Node>) {
    val parent = parentNode() as? Element ?: return
    val index = siblingIndex()
    remove()
    parent.insertChildren(index, replacements)
}

private fun Element.normalizedLocalName(): String = tagName().substringAfter(':').lowercase()

private fun Element.localNameEquals(expected: String): Boolean = normalizedLocalName().equals(expected, ignoreCase = true)
