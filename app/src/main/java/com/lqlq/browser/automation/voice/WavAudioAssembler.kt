package com.lqlq.browser.automation.voice

import java.io.ByteArrayOutputStream

/**
 * Ghép nhiều đoạn WAV (mỗi đoạn = giọng đọc của 1 cảnh, sinh riêng bằng TTS) thành
 * MỘT track WAV liền mạch để mux vào video, đồng thời trả về mốc thời gian THẬT của
 * từng cảnh.
 *
 * Đây là mấu chốt của việc đồng bộ Âm/Hình theo cảnh: trước đây cả video chỉ có 1
 * file giọng đọc + thời lượng cảnh dựa trên ƯỚC LƯỢNG của Gemini nên ảnh trôi khỏi
 * lời đọc. Sinh TTS từng cảnh rồi đo thời lượng thật ở đây => renderer đặt ảnh đúng
 * theo lời đọc thật, không còn đoán.
 *
 * Giữa các cảnh chèn 1 khoảng lặng ngắn (silenceGapMs) để tạo nhịp "dừng một chút"
 * khi chuyển cảnh, thay vì đọc/hiện phụ đề dồn dập liên tục.
 */
object WavAudioAssembler {

    data class SceneBoundaryMs(val startMs: Long, val endMs: Long)

    data class CombinedAudio(
        val wavBytes: ByteArray,
        /** Thời lượng THẬT của từng cảnh (đã gồm khoảng lặng cuối cảnh nếu có), theo đúng thứ tự truyền vào. */
        val sceneDurationsMs: List<Long>,
        val sceneBoundaries: List<SceneBoundaryMs>,
        val totalDurationMs: Long,
        val sampleRateHz: Int,
        val channelCount: Int
    )

    private data class WavPcm(
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val data: ByteArray
    )

    /**
     * @param sceneWavs danh sách WAV theo thứ tự cảnh (ordinal tăng dần). Phần tử null
     *   nghĩa là cảnh đó không có giọng đọc (voiceText rỗng/synthesize lỗi) - sẽ được
     *   thay bằng khoảng lặng placeholderSilenceMs để timeline không lệch.
     * @param silenceGapMs khoảng lặng chèn SAU mỗi cảnh (trừ cảnh cuối) để tạo nhịp chuyển cảnh.
     * @param placeholderSilenceMs độ dài khoảng lặng dùng cho cảnh không có audio.
     */
    fun combine(
        sceneWavs: List<ByteArray?>,
        silenceGapMs: Int,
        placeholderSilenceMs: Int = 900
    ): CombinedAudio {
        require(sceneWavs.isNotEmpty()) { "Khong co doan audio nao de ghep." }

        // Chọn format tham chiếu từ đoạn WAV hợp lệ đầu tiên; tất cả đoạn khác sẽ được
        // yêu cầu cùng format (cùng voice config nên thực tế luôn khớp).
        val parsed = sceneWavs.map { bytes -> bytes?.let { runCatching { parseWav(it) }.getOrNull() } }
        val reference = parsed.firstOrNull { it != null }
            ?: throw IllegalStateException("Khong co doan WAV hop le nao de ghep.")

        val channels = reference.channels
        val sampleRate = reference.sampleRate
        val bitsPerSample = reference.bitsPerSample
        val blockAlign = channels * (bitsPerSample / 8)
        val bytesPerMs = sampleRate.toLong() * blockAlign / 1000L

        val merged = ByteArrayOutputStream()
        val boundaries = ArrayList<SceneBoundaryMs>(sceneWavs.size)
        val durations = ArrayList<Long>(sceneWavs.size)
        var cursorMs = 0L

        parsed.forEachIndexed { index, pcm ->
            val sceneData: ByteArray = if (pcm != null &&
                pcm.channels == channels && pcm.sampleRate == sampleRate && pcm.bitsPerSample == bitsPerSample
            ) {
                pcm.data
            } else {
                // Cảnh không có audio hợp lệ / khác format -> khoảng lặng placeholder cùng format.
                silenceBytes(placeholderSilenceMs, bytesPerMs, blockAlign)
            }
            merged.write(sceneData)

            val isLast = index == parsed.lastIndex
            val gapBytes = if (isLast) ByteArray(0) else silenceBytes(silenceGapMs, bytesPerMs, blockAlign)
            if (gapBytes.isNotEmpty()) merged.write(gapBytes)

            val sceneMs = bytesToMs(sceneData.size.toLong() + gapBytes.size.toLong(), bytesPerMs)
            val startMs = cursorMs
            val endMs = cursorMs + sceneMs
            boundaries += SceneBoundaryMs(startMs, endMs)
            durations += sceneMs
            cursorMs = endMs
        }

        val pcmBytes = merged.toByteArray()
        val wavBytes = wrapWav(channels, sampleRate, bitsPerSample, pcmBytes)
        return CombinedAudio(
            wavBytes = wavBytes,
            sceneDurationsMs = durations,
            sceneBoundaries = boundaries,
            totalDurationMs = bytesToMs(pcmBytes.size.toLong(), bytesPerMs),
            sampleRateHz = sampleRate,
            channelCount = channels
        )
    }

    private fun silenceBytes(durationMs: Int, bytesPerMs: Long, blockAlign: Int): ByteArray {
        if (durationMs <= 0 || bytesPerMs <= 0L) return ByteArray(0)
        var size = durationMs.toLong() * bytesPerMs
        // Căn theo block-align (frame) để không lệch mẫu 16-bit đa kênh.
        if (blockAlign > 1) size -= size % blockAlign
        return ByteArray(size.toInt())
    }

    private fun bytesToMs(bytes: Long, bytesPerMs: Long): Long {
        if (bytesPerMs <= 0L) return 0L
        return bytes / bytesPerMs
    }

    /**
     * Đọc WAV bằng cách duyệt chunk (RIFF/WAVE -> fmt / data) thay vì giả định offset
     * cố định 44 - một số engine TTS chèn thêm chunk (LIST/fact) trước "data".
     */
    private fun parseWav(bytes: ByteArray): WavPcm {
        require(bytes.size > 44) { "WAV qua ngan." }
        require(String(bytes, 0, 4) == "RIFF") { "Khong phai WAV RIFF." }
        require(String(bytes, 8, 4) == "WAVE") { "Khong phai WAV." }

        var offset = 12
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var pcmStart = -1
        var pcmLength = 0
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = leInt(bytes, offset + 4)
            val dataStart = offset + 8
            if (chunkSize < 0 || dataStart + chunkSize > bytes.size) break
            when (chunkId) {
                "fmt " -> {
                    channels = leShort(bytes, dataStart + 2)
                    sampleRate = leInt(bytes, dataStart + 4)
                    bitsPerSample = leShort(bytes, dataStart + 14)
                }
                "data" -> {
                    pcmStart = dataStart
                    pcmLength = chunkSize
                }
            }
            offset = dataStart + chunkSize + (chunkSize % 2)
        }
        require(sampleRate > 0 && channels in 1..2 && bitsPerSample == 16) {
            "WAV format khong ho tro (chi mono/stereo 16-bit)."
        }
        require(pcmStart >= 0 && pcmLength > 0) { "WAV khong co data chunk hop le." }
        return WavPcm(
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            data = bytes.copyOfRange(pcmStart, pcmStart + pcmLength)
        )
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

    private fun leShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

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
