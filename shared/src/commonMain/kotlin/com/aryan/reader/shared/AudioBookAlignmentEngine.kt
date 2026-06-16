package com.aryan.reader.shared

object AudioBookAlignmentEngine {
    private const val MATCH_THRESHOLD = 0.78
    private const val WEAK_THRESHOLD = 0.50
    private const val WINDOW_PADDING = 2

    fun align(
        sentences: List<AudioBookSentence>,
        tracks: List<AudioBookTranscriptTrack>
    ): AudioBookAlignmentResult {
        val words = tracks.flatMap { track ->
            track.words.map { word ->
                TimedWord(
                    sourceId = track.sourceId,
                    token = AudioBookTextNormalizer.normalize(word.text),
                    startSeconds = track.offsetSeconds + word.startSeconds,
                    endSeconds = track.offsetSeconds + word.endSeconds
                )
            }
        }.filter { it.token.isNotBlank() }
            .sortedBy { it.startSeconds }

        val entries = mutableListOf<AudioBookAlignmentEntry>()
        var searchStart = 0

        sentences.forEach { sentence ->
            val tokens = AudioBookTextNormalizer.tokens(sentence.text)
            val match = bestMatch(tokens, words, searchStart)
            if (match == null || match.score < WEAK_THRESHOLD) {
                entries += AudioBookAlignmentEntry(
                    sentenceId = sentence.id,
                    text = sentence.text,
                    audioSourceId = null,
                    clipBegin = 0.0,
                    clipEnd = 0.0,
                    confidence = AudioBookAlignmentConfidence.MISSING,
                    warnings = listOf("missing alignment")
                )
            } else {
                val first = words[match.startIndex]
                val last = words[match.endIndex]
                val weak = match.score < MATCH_THRESHOLD
                entries += AudioBookAlignmentEntry(
                    sentenceId = sentence.id,
                    text = sentence.text,
                    audioSourceId = first.sourceId,
                    clipBegin = first.startSeconds,
                    clipEnd = last.endSeconds,
                    confidence = if (weak) AudioBookAlignmentConfidence.WEAK_MATCH else AudioBookAlignmentConfidence.MATCHED,
                    warnings = if (weak) listOf("weak alignment match") else emptyList()
                )
                searchStart = (match.endIndex + 1).coerceAtMost(words.size)
            }
        }

        return AudioBookAlignmentResult(interpolate(entries))
    }

    private fun bestMatch(tokens: List<String>, words: List<TimedWord>, searchStart: Int): Match? {
        if (tokens.isEmpty() || words.isEmpty()) return null

        var best: Match? = null
        for (start in searchStart until words.size) {
            val minLength = maxOf(1, tokens.size - WINDOW_PADDING)
            val maxLength = minOf(words.size - start, tokens.size + WINDOW_PADDING)
            for (length in minLength..maxLength) {
                val end = start + length - 1
                val score = orderedTokenScore(tokens, words.subList(start, end + 1).map { it.token })
                if (best == null || score > best.score) {
                    best = Match(start, end, score)
                }
            }
        }
        return best
    }

    private fun orderedTokenScore(sentenceTokens: List<String>, wordTokens: List<String>): Double {
        val matched = longestCommonSubsequenceLength(sentenceTokens, wordTokens)
        return matched.toDouble() / maxOf(sentenceTokens.size, wordTokens.size).toDouble()
    }

    private fun longestCommonSubsequenceLength(left: List<String>, right: List<String>): Int {
        if (left.isEmpty() || right.isEmpty()) return 0
        val previous = IntArray(right.size + 1)
        val current = IntArray(right.size + 1)
        left.forEach { leftToken ->
            for (rightIndex in right.indices) {
                current[rightIndex + 1] = if (leftToken == right[rightIndex]) {
                    previous[rightIndex] + 1
                } else {
                    maxOf(previous[rightIndex + 1], current[rightIndex])
                }
            }
            for (index in current.indices) {
                previous[index] = current[index]
                current[index] = 0
            }
        }
        return previous[right.size]
    }

    private fun interpolate(entries: List<AudioBookAlignmentEntry>): List<AudioBookAlignmentEntry> {
        val result = entries.toMutableList()
        var index = 0
        while (index < result.size) {
            if (result[index].confidence != AudioBookAlignmentConfidence.MISSING) {
                index += 1
                continue
            }

            val startMissing = index
            while (index < result.size && result[index].confidence == AudioBookAlignmentConfidence.MISSING) {
                index += 1
            }
            val endMissingExclusive = index
            val previous = result.getOrNull(startMissing - 1)
            val next = result.getOrNull(endMissingExclusive)
            if (previous == null || next == null || previous.audioSourceId == null) continue

            val missingCount = endMissingExclusive - startMissing
            val gapStart = previous.clipEnd
            val gapEnd = next.clipBegin
            if (gapEnd < gapStart) continue

            val slice = (gapEnd - gapStart) / missingCount.toDouble()
            for (offset in 0 until missingCount) {
                val entryIndex = startMissing + offset
                result[entryIndex] = result[entryIndex].copy(
                    audioSourceId = previous.audioSourceId,
                    clipBegin = gapStart + slice * offset,
                    clipEnd = if (offset == missingCount - 1) gapEnd else gapStart + slice * (offset + 1),
                    confidence = AudioBookAlignmentConfidence.INTERPOLATED,
                    warnings = listOf("interpolated alignment")
                )
            }
        }
        return result
    }

    private data class TimedWord(
        val sourceId: String,
        val token: String,
        val startSeconds: Double,
        val endSeconds: Double
    )

    private data class Match(
        val startIndex: Int,
        val endIndex: Int,
        val score: Double
    )
}
