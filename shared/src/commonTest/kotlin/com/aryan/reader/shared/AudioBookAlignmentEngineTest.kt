package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioBookAlignmentEngineTest {

    @Test
    fun `aligns exact sentence text to transcript word timestamps`() {
        val result = AudioBookAlignmentEngine.align(
            sentences = listOf(
                sentence("s1", "Hello brave world."),
                sentence("s2", "This is aligned."),
            ),
            tracks = listOf(
                track(
                    words = listOf(
                        word("Hello", 0.50, 0.70),
                        word("brave", 0.70, 0.95),
                        word("world", 0.95, 1.20),
                        word("This", 1.50, 1.70),
                        word("is", 1.70, 1.85),
                        word("aligned", 1.85, 2.10),
                    ),
                ),
            ),
        )

        assertEquals(AudioBookAlignmentConfidence.MATCHED, result.entries[0].confidence)
        assertEquals(0.50, result.entries[0].clipBegin)
        assertEquals(1.20, result.entries[0].clipEnd)
        assertEquals(AudioBookAlignmentConfidence.MATCHED, result.entries[1].confidence)
        assertEquals(1.50, result.entries[1].clipBegin)
        assertEquals(2.10, result.entries[1].clipEnd)
        assertTrue(result.entries.none { it.warnings.isNotEmpty() })
    }

    @Test
    fun `normalizes punctuation case whitespace and curly quotes`() {
        val result = AudioBookAlignmentEngine.align(
            sentences = listOf(sentence("s1", "  “Hello,” said ALICE!  ")),
            tracks = listOf(track(words = listOf(word("hello", 3.0, 3.4), word("said", 3.5, 3.7), word("alice", 3.8, 4.2)))),
        )

        assertEquals(AudioBookAlignmentConfidence.MATCHED, result.entries.single().confidence)
        assertEquals(3.0, result.entries.single().clipBegin)
        assertEquals(4.2, result.entries.single().clipEnd)
        assertEquals("hello said alice", AudioBookTextNormalizer.normalize("  “Hello,” said ALICE!  "))
    }

    @Test
    fun `skips intro audio before first chapter sentence`() {
        val result = AudioBookAlignmentEngine.align(
            sentences = listOf(sentence("chapter-1", "Chapter one begins now.")),
            tracks = listOf(track(words = listOf(
                word("Publisher", 0.0, 0.4),
                word("presents", 0.4, 0.8),
                word("Chapter", 12.0, 12.2),
                word("one", 12.2, 12.4),
                word("begins", 12.4, 12.8),
                word("now", 12.8, 13.0),
            ))),
        )

        assertEquals(12.0, result.entries.single().clipBegin)
        assertEquals(13.0, result.entries.single().clipEnd)
    }

    @Test
    fun `interpolates one omitted sentence between matched neighbors`() {
        val result = AudioBookAlignmentEngine.align(
            sentences = listOf(
                sentence("s1", "First sentence appears."),
                sentence("s2", "This sentence is omitted."),
                sentence("s3", "Third sentence appears."),
            ),
            tracks = listOf(track(words = listOf(
                word("First", 10.0, 10.2), word("sentence", 10.2, 10.5), word("appears", 10.5, 10.8),
                word("Third", 12.0, 12.2), word("sentence", 12.2, 12.5), word("appears", 12.5, 12.8),
            ))),
        )

        assertEquals(AudioBookAlignmentConfidence.INTERPOLATED, result.entries[1].confidence)
        assertEquals(10.8, result.entries[1].clipBegin)
        assertEquals(12.0, result.entries[1].clipEnd)
        assertTrue(result.entries[1].warnings.any { it.contains("interpolated") })
    }

    @Test
    fun `advances search window after three consecutive misses`() {
        val result = AudioBookAlignmentEngine.align(
            sentences = listOf(
                sentence("s1", "Missing one."),
                sentence("s2", "Missing two."),
                sentence("s3", "Missing three."),
                sentence("s4", "Recovered sentence."),
            ),
            tracks = listOf(track(words = listOf(word("Recovered", 50.0, 50.4), word("sentence", 50.4, 50.9)))),
        )

        assertEquals(AudioBookAlignmentConfidence.MISSING, result.entries[0].confidence)
        assertEquals(AudioBookAlignmentConfidence.MISSING, result.entries[1].confidence)
        assertEquals(AudioBookAlignmentConfidence.MISSING, result.entries[2].confidence)
        assertEquals(AudioBookAlignmentConfidence.MATCHED, result.entries[3].confidence)
        assertEquals(50.0, result.entries[3].clipBegin)
    }

    @Test
    fun `flags weak matches with warnings instead of crashing`() {
        val result = AudioBookAlignmentEngine.align(
            sentences = listOf(sentence("s1", "The quick brown fox jumps.")),
            tracks = listOf(track(words = listOf(word("quick", 2.0, 2.2), word("brown", 2.2, 2.5), word("fox", 2.5, 2.8)))),
        )

        assertEquals(AudioBookAlignmentConfidence.WEAK_MATCH, result.entries.single().confidence)
        assertEquals(2.0, result.entries.single().clipBegin)
        assertEquals(2.8, result.entries.single().clipEnd)
        assertTrue(result.entries.single().warnings.any { it.contains("weak") })
    }

    @Test
    fun `adds multi track offsets to absolute clip timestamps`() {
        val result = AudioBookAlignmentEngine.align(
            sentences = listOf(sentence("s1", "Second track starts.")),
            tracks = listOf(
                track(sourceId = "disc-1", offset = 0.0, words = listOf(word("unrelated", 0.0, 0.3))),
                track(sourceId = "disc-2", offset = 60.0, words = listOf(word("Second", 1.0, 1.3), word("track", 1.3, 1.6), word("starts", 1.6, 2.0))),
            ),
        )

        assertEquals("disc-2", result.entries.single().audioSourceId)
        assertEquals(61.0, result.entries.single().clipBegin)
        assertEquals(62.0, result.entries.single().clipEnd)
    }

    private fun sentence(id: String, text: String) = AudioBookSentence(id = id, text = text)

    private fun track(
        sourceId: String = "audio-1",
        offset: Double = 0.0,
        words: List<AudioBookTranscriptWord>,
    ) = AudioBookTranscriptTrack(sourceId = sourceId, offsetSeconds = offset, words = words)

    private fun word(text: String, start: Double, end: Double) = AudioBookTranscriptWord(text = text, startSeconds = start, endSeconds = end)
}
