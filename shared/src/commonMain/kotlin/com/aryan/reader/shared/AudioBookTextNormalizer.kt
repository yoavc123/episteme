package com.aryan.reader.shared

object AudioBookTextNormalizer {
    private val punctuation = Regex("[^\\p{L}\\p{N}']+")
    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String {
        return text
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', ' ')
            .replace('”', ' ')
            .lowercase()
            .replace(punctuation, " ")
            .trim()
            .replace(whitespace, " ")
    }

    fun tokens(text: String): List<String> {
        return normalize(text).split(' ').filter { it.isNotBlank() }
    }
}
