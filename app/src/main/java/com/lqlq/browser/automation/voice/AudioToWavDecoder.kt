package com.lqlq.browser.automation.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Giai ma 1 file audio nen (mp3/m4a/aac...) ve PCM 16-bit roi boc lai thanh WAV.
 * Dung cho cac voice provider tra ve audio nen (vd Edge Neural TTS tra mp3) de dua
 * ve dung dinh dang WAV ma pipeline dong bo theo canh (WavAudioAssembler) + renderer
 * (yeu cau audio/wav) can. Chay bang MediaExtractor + MediaCodec co san tren Android,
 * khong can thu vien ngoai.
 */
object AudioToWavDecoder {

    private const val TIMEOUT_US = 10_000L

    data class DecodedWav(val wavBytes: ByteArray, val sampleRateHz: Int, val channelCount: Int)

    /** Giai ma bytes audio nen bat ky -> WAV 16-bit PCM. Nem IllegalStateException neu that bai. */
    fun decodeToWav(compressedBytes: ByteArray): DecodedWav {
        require(compressedBytes.isNotEmpty()) { "Audio rong." }
        val tempFile = File.createTempFile("lqlq-tts-decode-", ".bin")
        try {
            tempFile.writeBytes(compressedBytes)
            val (pcm, sampleRate, channels) = decodeFileToPcm(tempFile)
            require(pcm.isNotEmpty()) { "Giai ma audio ra PCM rong." }
            val wav = wrapWav(channels, sampleRate, 16, pcm)
            return DecodedWav(wav, sampleRate, channels)
        } finally {
            tempFile.delete()
        }
    }

    private fun decodeFileToPcm(file: File): Triple<ByteArray, Int, Int> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    trackIndex = index
                    format = candidate
                    break
                }
            }
            require(trackIndex >= 0 && format != null) { "File audio khong co audio track hop le." }
            extractor.selectTrack(trackIndex)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            decoder.configure(format, null, null, 0)
            decoder.start()
            val bufferInfo = MediaCodec.BufferInfo()
            val pcmOutput = ByteArrayOutputStream()
            var sawInputEos = false
            var sawOutputEos = false
            var outSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            try {
                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = requireNotNull(decoder.getInputBuffer(inputIndex))
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (bufferInfo.size > 0 && outputBuffer != null) {
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.get(chunk)
                            pcmOutput.write(chunk)
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = decoder.outputFormat
                        outSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outChannelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } finally {
                runCatching { decoder.stop() }
                runCatching { decoder.release() }
            }
            require(outChannelCount in 1..2) { "Chi ho tro audio mono/stereo." }
            return Triple(pcmOutput.toByteArray(), outSampleRate, outChannelCount)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun wrapWav(channels: Int, sampleRate: Int, bitsPerSample: Int, pcmData: ByteArray): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val out = ByteArrayOutputStream(44 + pcmData.size)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 36 + pcmData.size)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, 16)
        writeShortLE(out, 1)
        writeShortLE(out, channels)
        writeIntLE(out, sampleRate)
        writeIntLE(out, byteRate)
        writeShortLE(out, blockAlign)
        writeShortLE(out, bitsPerSample)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeIntLE(out, pcmData.size)
        out.write(pcmData)
        return out.toByteArray()
    }

    private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }
}
