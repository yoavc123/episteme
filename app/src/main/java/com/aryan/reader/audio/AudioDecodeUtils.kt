package com.aryan.reader.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import kotlin.math.min

object AudioDecodeUtils {
    const val WHISPER_SAMPLE_RATE = 16_000

    data class PcmChunk(
        val samples: FloatArray,
        val startSeconds: Double,
        val durationSeconds: Double
    )

    interface Decoder {
        fun decode(context: Context, source: Uri): Sequence<PcmChunk>
    }

    val androidDecoder: Decoder = AndroidMediaDecoder()

    fun pcm16ToFloatArray(buffer: ByteArray, bytesRead: Int = buffer.size): FloatArray {
        val sampleCount = bytesRead / 2
        return FloatArray(sampleCount) { index ->
            val low = buffer[index * 2].toInt() and 0xff
            val high = buffer[index * 2 + 1].toInt()
            val sample = (high shl 8) or low
            (sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) / 32768f)
        }
    }

    private class AndroidMediaDecoder : Decoder {
        override fun decode(context: Context, source: Uri): Sequence<PcmChunk> = sequence {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                context.contentResolver.openAssetFileDescriptor(source, "r")?.use { descriptor ->
                    extractor.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length
                    )
                } ?: throw IllegalArgumentException("Unable to open audio source")

                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index)
                        .getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true
                } ?: throw IllegalArgumentException("No audio track found")

                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: throw IllegalArgumentException("Audio track is missing a MIME type")
                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }

                val bufferInfo = BufferInfo()
                var inputDone = false
                var outputDone = false
                var outputSampleRate = format.optionalInt(MediaFormat.KEY_SAMPLE_RATE, WHISPER_SAMPLE_RATE)
                var outputChannels = format.optionalInt(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)

                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            inputBuffer?.clear()
                            val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                            if (sampleSize <= 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime.coerceAtLeast(0L), 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outputFormat = codec.outputFormat
                            outputSampleRate = outputFormat.optionalInt(MediaFormat.KEY_SAMPLE_RATE, outputSampleRate)
                            outputChannels = outputFormat.optionalInt(MediaFormat.KEY_CHANNEL_COUNT, outputChannels).coerceAtLeast(1)
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val bytes = ByteArray(bufferInfo.size)
                                outputBuffer.get(bytes)
                                val samples = pcm16ToMono16kFloatArray(bytes, outputSampleRate, outputChannels)
                                if (samples.isNotEmpty()) {
                                    yield(
                                        PcmChunk(
                                            samples = samples,
                                            startSeconds = bufferInfo.presentationTimeUs / 1_000_000.0,
                                            durationSeconds = samples.size.toDouble() / WHISPER_SAMPLE_RATE.toDouble()
                                        )
                                    )
                                }
                            }
                            outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            } finally {
                codec?.runCatching { stop() }
                codec?.release()
                extractor.release()
            }
        }
    }

    private fun pcm16ToMono16kFloatArray(bytes: ByteArray, sourceSampleRate: Int, channelCount: Int): FloatArray {
        val sourceChannels = channelCount.coerceAtLeast(1)
        val sourceFrames = bytes.size / 2 / sourceChannels
        if (sourceFrames <= 0) return FloatArray(0)
        val safeSampleRate = sourceSampleRate.coerceAtLeast(1)
        val outputFrames = maxOf(1, (sourceFrames.toLong() * WHISPER_SAMPLE_RATE / safeSampleRate).toInt())
        return FloatArray(outputFrames) { outputIndex ->
            val sourceFrame = min(sourceFrames - 1, (outputIndex.toLong() * safeSampleRate / WHISPER_SAMPLE_RATE).toInt())
            var mixed = 0f
            for (channel in 0 until sourceChannels) {
                val byteIndex = (sourceFrame * sourceChannels + channel) * 2
                val low = bytes[byteIndex].toInt() and 0xff
                val high = bytes[byteIndex + 1].toInt()
                val sample = (high shl 8) or low
                mixed += sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) / 32768f
            }
            mixed / sourceChannels.toFloat()
        }
    }

    private fun MediaFormat.optionalInt(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback
}
