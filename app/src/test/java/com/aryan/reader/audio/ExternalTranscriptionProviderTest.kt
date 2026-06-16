package com.aryan.reader.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalTranscriptionProviderTest {
    @Test
    fun openAiVerboseJsonParsesWordTimestamps() {
        val segments = OpenAiTranscriptionResponseParser.parse(
            """
            {
              "text": "Hello world",
              "words": [
                {"word": "Hello", "start": 0.0, "end": 0.4},
                {"word": "world", "start": 0.4, "end": 0.9}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, segments.size)
        assertEquals("Hello world", segments.single().text)
        assertEquals(0.0, segments.single().startSeconds, 0.0001)
        assertEquals(0.9, segments.single().endSeconds, 0.0001)
        assertEquals(listOf("Hello", "world"), segments.single().words.map { it.word })
    }

    @Test
    fun deepgramResponseParsesAlternativeWords() {
        val segments = DeepgramTranscriptionResponseParser.parse(
            """
            {
              "results": {
                "channels": [{
                  "alternatives": [{
                    "transcript": "Hello world.",
                    "words": [
                      {"word": "hello", "punctuated_word": "Hello", "start": 0.0, "end": 0.5},
                      {"word": "world", "punctuated_word": "world.", "start": 0.5, "end": 1.0}
                    ]
                  }]
                }]
              }
            }
            """.trimIndent()
        )

        assertEquals(1, segments.size)
        assertEquals("Hello world.", segments.single().text)
        assertEquals(0.0, segments.single().startSeconds, 0.0001)
        assertEquals(1.0, segments.single().endSeconds, 0.0001)
        assertEquals(listOf("Hello", "world."), segments.single().words.map { it.word })
    }
}
