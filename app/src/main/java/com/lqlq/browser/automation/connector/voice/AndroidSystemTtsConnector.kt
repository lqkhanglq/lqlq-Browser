package com.lqlq.browser.automation.connector.voice

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class AndroidSystemTtsConnector(
    private val context: Context,
    private val synthAdapter: AndroidSystemTtsSynthAdapter = RealAndroidSystemTtsSynthAdapter(context)
) : VoiceGenerationConnector {

    override fun listVoices(): List<VoiceDefinition> {
        val catalog = synthAdapter.listVoices()
        return catalog.voices
            .filter { isVietnameseVoice(it.locale) }
            .sortedWith(
                compareByDescending<VoiceDefinition> { it.locale.equals(DEFAULT_LOCALE, ignoreCase = true) }
                    .thenBy { it.networkRequired }
                    .thenBy { it.displayName }
            )
            // Google TTS tra ve TEN giong gan giong het nhau ("Tieng Viet (Viet
            // Nam)...") rat kho phan biet -> danh SO thu tu + ma giong + gioi tinh
            // cho de chon.
            .mapIndexed { index, voice ->
                val code = voice.voiceId.substringAfter("x-", "").substringBefore("-local")
                    .ifBlank { voice.voiceId.takeLast(10) }
                val genderLabel = when (voice.genderHint) {
                    "female" -> " (nữ)"
                    "male" -> " (nam)"
                    else -> ""
                }
                voice.copy(displayName = "Giọng ${index + 1} · $code$genderLabel")
            }
    }

    override suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult {
        validateConfig(config)
        val voices = listVoices()
        val selected = resolveSelectedVoice(config, voices)
        if (selected == null) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.VOICE_NOT_INSTALLED,
                "Thiết bị chưa cài giọng đọc tiếng Việt cho Google TTS trên điện thoại."
            )
        }
        return VoiceProviderConnectionResult(
            providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
            model = config.model,
            voiceId = selected.voiceId,
            locale = selected.locale,
            engineName = selected.engineName,
            voiceCount = voices.size
        )
    }

    override suspend fun synthesizeSample(config: VoiceProviderConfig, text: String): VoiceSampleResult {
        val selected = resolveSelectedVoice(config, listVoices())
            ?: throw VoiceProviderException(
                VoiceProviderErrorCode.VOICE_NOT_INSTALLED,
                "Thiết bị chưa cài giọng đọc tiếng Việt cho Google TTS trên điện thoại."
            )
        val outputFile = createTempAudioFile("voice-sample")
        try {
            synthAdapter.synthesizeToFile(
                text = text,
                config = config.copy(voiceId = selected.voiceId, locale = selected.locale),
                outputFile = outputFile
            )
            val bytes = outputFile.readBytes()
            validateAudio(bytes, outputFile)
            return VoiceSampleResult(
                bytes = bytes,
                mimeType = MIME_TYPE_WAV,
                providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
                voiceId = selected.voiceId,
                locale = selected.locale,
                engineName = selected.engineName
            )
        } finally {
            outputFile.delete()
        }
    }

    override suspend fun generateVoice(
        config: VoiceProviderConfig,
        request: VoiceGenerationRequest
    ): VoiceGenerationResult {
        validateConfig(config)
        val selected = resolveSelectedVoice(config, listVoices())
            ?: throw VoiceProviderException(
                VoiceProviderErrorCode.VOICE_NOT_INSTALLED,
                "Thiết bị chưa cài giọng đọc tiếng Việt cho Google TTS trên điện thoại."
            )

        try {
            val chunks = chunkScript(request.text, MAX_CHARS_PER_CHUNK)
            if (chunks.isEmpty()) {
                throw VoiceProviderException(
                    VoiceProviderErrorCode.INVALID_RESPONSE,
                    "Nội dung voice rỗng sau khi tách chunk."
                )
            }

            val chunkFiles = mutableListOf<File>()
            try {
                chunks.forEachIndexed { index, chunk ->
                    val chunkFile = createTempAudioFile("voice-${request.jobId}-${index + 1}")
                    synthAdapter.synthesizeToFile(
                        text = chunk,
                        config = config.copy(voiceId = selected.voiceId, locale = selected.locale),
                        outputFile = chunkFile
                    )
                    validateAudio(chunkFile.readBytes(), chunkFile)
                    chunkFiles += chunkFile
                }

                val mergedFile = createTempAudioFile("voice-merged-${request.jobId}")
                try {
                    mergeWavFiles(chunkFiles, mergedFile)
                    val finalBytes = mergedFile.readBytes()
                    validateAudio(finalBytes, mergedFile)
                    val durationMs = readDurationMs(mergedFile)
                    return VoiceGenerationResult(
                        bytes = finalBytes,
                        mimeType = MIME_TYPE_WAV,
                        metadata = VoiceArtifactMetadata(
                            providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
                            voiceId = selected.voiceId,
                            locale = selected.locale,
                            chunkCount = chunks.size,
                            durationMs = durationMs,
                            checksum = sha256(finalBytes),
                            engineName = selected.engineName
                        )
                    )
                } finally {
                    mergedFile.delete()
                }
            } finally {
                chunkFiles.forEach { it.delete() }
            }
        } catch (error: CancellationException) {
            throw VoiceProviderException(VoiceProviderErrorCode.CANCELLED, "Đã hủy tạo giọng đọc.")
        }
    }

    override fun openProviderSettings() {
        val intents = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        intents.firstNotNullOfOrNull { intent ->
            runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.getOrNull()
        }
    }

    private fun validateConfig(config: VoiceProviderConfig) {
        require(config.providerId == AutomationVoiceProviders.ANDROID_SYSTEM_TTS) {
            "Only local Google TTS provider is supported."
        }
        require(config.outputFormat.lowercase() == "wav") { "Local Google TTS currently supports wav only." }
        require(isVietnameseVoice(config.locale)) { "Local Google TTS currently requires locale vi-VN." }
        require(config.speechRate in MIN_SPEECH_RATE..MAX_SPEECH_RATE) { "Speech rate out of range." }
        require(config.pitch in MIN_PITCH..MAX_PITCH) { "Pitch out of range." }
    }

    private fun resolveSelectedVoice(
        config: VoiceProviderConfig,
        voices: List<VoiceDefinition>
    ): VoiceDefinition? {
        val exact = config.voiceId?.trim()?.takeIf { it.isNotEmpty() }?.let { requested ->
            voices.firstOrNull { it.voiceId == requested }
        }
        val viOffline = voices.firstOrNull { it.locale.equals(DEFAULT_LOCALE, true) && !it.networkRequired }
        val viAny = voices.firstOrNull { it.locale.startsWith("vi", true) }
        return exact ?: viOffline ?: viAny
    }

    private fun chunkScript(text: String, maxCharsPerChunk: Int): List<String> {
        val paragraphs = text
            .replace("\r", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val segments = if (paragraphs.isEmpty()) {
            splitBySentence(text)
        } else {
            paragraphs.flatMap { splitBySentence(it) }
        }.map { it.trim() }.filter { it.isNotEmpty() }

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (segment in segments) {
            if (segment.length > maxCharsPerChunk) {
                flushChunk(current, chunks)
                segment.chunked(maxCharsPerChunk).forEach { piece ->
                    chunks += piece.trim()
                }
                continue
            }
            if (current.isNotEmpty() && current.length + 1 + segment.length > maxCharsPerChunk) {
                flushChunk(current, chunks)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(segment)
        }
        flushChunk(current, chunks)
        return chunks
    }

    private fun splitBySentence(text: String): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()
        val tokens = normalized.split(Regex("(?<=[.!?;:\\n…])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (tokens.isEmpty()) listOf(normalized) else tokens
    }

    private fun flushChunk(builder: StringBuilder, sink: MutableList<String>) {
        if (builder.isNotEmpty()) {
            sink += builder.toString().trim()
            builder.clear()
        }
    }

    private fun createTempAudioFile(prefix: String): File {
        val dir = File(context.cacheDir, "automation-voice-temp").apply { mkdirs() }
        return File(dir, "$prefix-${UUID.randomUUID().toString().take(8)}.wav")
    }

    private fun validateAudio(bytes: ByteArray, file: File) {
        if (bytes.isEmpty()) {
            throw VoiceProviderException(VoiceProviderErrorCode.INVALID_AUDIO, "File giọng đọc rỗng.")
        }
        if (!looksLikeWav(bytes)) {
            throw VoiceProviderException(VoiceProviderErrorCode.INVALID_AUDIO, "Artifact voice không phải WAV hợp lệ.")
        }
        val durationMs = readDurationMs(file)
        if (durationMs <= 0L) {
            throw VoiceProviderException(VoiceProviderErrorCode.INVALID_AUDIO, "Không đọc được thời lượng audio.")
        }
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val metadataDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            if (metadataDuration > 0L) {
                metadataDuration
            } else {
                readWavDurationFallback(file)
            }
        } catch (_: Throwable) {
            readWavDurationFallback(file)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readWavDurationFallback(file: File): Long {
        return runCatching {
            val bytes = file.readBytes()
            if (!looksLikeWav(bytes) || bytes.size < 44) {
                return 0L
            }
            val sampleRate = littleEndianInt(bytes, 24)
            val byteRate = littleEndianInt(bytes, 28)
            val dataSize = littleEndianInt(bytes, 40)
            if (sampleRate <= 0 || byteRate <= 0 || dataSize <= 0) {
                0L
            } else {
                ((dataSize.toDouble() / byteRate.toDouble()) * 1000.0).toLong().coerceAtLeast(1L)
            }
        }.getOrDefault(0L)
    }

    private fun looksLikeWav(bytes: ByteArray): Boolean {
        return bytes.size >= 12 &&
            String(bytes, 0, 4) == "RIFF" &&
            String(bytes, 8, 4) == "WAVE"
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun mergeWavFiles(inputs: List<File>, output: File) {
        require(inputs.isNotEmpty()) { "At least one WAV input is required." }
        val chunks = inputs.map { readWavPcm(it) }
        val first = chunks.first()
        chunks.drop(1).forEach { chunk ->
            require(chunk.channels == first.channels && chunk.sampleRate == first.sampleRate && chunk.bitsPerSample == first.bitsPerSample) {
                "All WAV chunks must share the same PCM format."
            }
        }
        val mergedData = ByteArrayOutputStream()
        chunks.forEach { mergedData.write(it.data) }
        writeWav(output, first.channels, first.sampleRate, first.bitsPerSample, mergedData.toByteArray())
    }

    private fun readWavPcm(file: File): WavPcmData {
        val bytes = file.readBytes()
        if (!looksLikeWav(bytes) || bytes.size < 44) {
            throw VoiceProviderException(VoiceProviderErrorCode.INVALID_AUDIO, "Chunk WAV không hợp lệ.")
        }
        val channels = littleEndianShort(bytes, 22)
        val sampleRate = littleEndianInt(bytes, 24)
        val bitsPerSample = littleEndianShort(bytes, 34)
        val dataSize = littleEndianInt(bytes, 40)
        val start = 44
        val end = (start + dataSize).coerceAtMost(bytes.size)
        return WavPcmData(
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            data = bytes.copyOfRange(start, end)
        )
    }

    private fun writeWav(
        file: File,
        channels: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        pcmData: ByteArray
    ) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            raf.writeBytes("RIFF")
            writeIntLE(raf, 36 + pcmData.size)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            writeIntLE(raf, 16)
            writeShortLE(raf, 1)
            writeShortLE(raf, channels)
            writeIntLE(raf, sampleRate)
            writeIntLE(raf, byteRate)
            writeShortLE(raf, blockAlign)
            writeShortLE(raf, bitsPerSample)
            raf.writeBytes("data")
            writeIntLE(raf, pcmData.size)
            raf.write(pcmData)
        }
    }

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write((value shr 8) and 0xff)
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write((value shr 8) and 0xff)
        raf.write((value shr 16) and 0xff)
        raf.write((value shr 24) and 0xff)
    }

    companion object {
        const val DEFAULT_LOCALE: String = "vi-VN"
        const val SAMPLE_TEXT: String = "Cảm ơn mọi người đã sử dụng dịch vụ của chúng tôi."
        private const val MIME_TYPE_WAV = "audio/wav"
        private const val MAX_CHARS_PER_CHUNK = 350
        private const val MIN_SPEECH_RATE = 0.5f
        private const val MAX_SPEECH_RATE = 2.0f
        private const val MIN_PITCH = 0.5f
        private const val MAX_PITCH = 2.0f

        fun isVietnameseVoice(locale: String): Boolean {
            val normalized = locale.trim().replace('_', '-').lowercase()
            return normalized == "vi" || normalized == "vi-vn" || normalized.startsWith("vi-")
        }
    }
}

data class AndroidSystemTtsVoiceCatalog(
    val engineName: String,
    val voices: List<VoiceDefinition>
)

interface AndroidSystemTtsSynthAdapter {
    fun listVoices(): AndroidSystemTtsVoiceCatalog

    suspend fun synthesizeToFile(
        text: String,
        config: VoiceProviderConfig,
        outputFile: File
    )
}

private class RealAndroidSystemTtsSynthAdapter(
    private val context: Context
) : AndroidSystemTtsSynthAdapter {

    override fun listVoices(): AndroidSystemTtsVoiceCatalog {
        val engine = createEngine()
        try {
            val availableVoices = runCatching { engine.voices }.getOrNull().orEmpty()
            val defaultVoice = runCatching { engine.defaultVoice }.getOrNull()
            val engineName = engine.defaultEngine ?: "Google TTS tren dien thoai"
            val voices = availableVoices.map { voice ->
                VoiceDefinition(
                    voiceId = voice.name,
                    displayName = buildDisplayName(voice),
                    locale = voice.locale.toLanguageTag(),
                    engineName = engineName,
                    networkRequired = voice.isNetworkConnectionRequired,
                    installed = true,
                    isDefault = voice.name == defaultVoice?.name,
                    genderHint = inferGender(voice.name)
                )
            }
            return AndroidSystemTtsVoiceCatalog(engineName = engineName, voices = voices)
        } finally {
            engine.shutdown()
        }
    }

    override suspend fun synthesizeToFile(
        text: String,
        config: VoiceProviderConfig,
        outputFile: File
    ) {
        val engine = createEngine()
        try {
            val selectedVoice = runCatching { engine.voices }.getOrNull()
                ?.firstOrNull { it.name == config.voiceId }
            if (selectedVoice != null) {
                engine.voice = selectedVoice
            } else {
                engine.language = Locale.forLanguageTag(config.locale)
            }
            engine.setSpeechRate(config.speechRate)
            engine.setPitch(config.pitch)
            synthesize(engine, text, outputFile)
        } finally {
            engine.shutdown()
        }
    }

    private fun createEngine(): TextToSpeech {
        val latch = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        val engine = TextToSpeech(context) { status ->
            initStatus = status
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)
        if (initStatus != TextToSpeech.SUCCESS) {
            engine.shutdown()
            throw VoiceProviderException(
                VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the khoi tao Google TTS tren dien thoai."
            )
        }
        return engine
    }

    private suspend fun synthesize(engine: TextToSpeech, text: String, outputFile: File) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val utteranceId = "automation-tts-${UUID.randomUUID()}"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(
                                VoiceProviderException(
                                    VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                                    "Google TTS tren dien thoai gap loi khi synthesize."
                                )
                            )
                        )
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(
                                VoiceProviderException(
                                    VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                                    "Google TTS tren dien thoai loi ma $errorCode."
                                )
                            )
                        )
                    }
                }
            })

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.synthesizeToFile(text, Bundle(), outputFile, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                engine.synthesizeToFile(text, null, outputFile.absolutePath)
            }
            if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                continuation.resumeWith(
                    Result.failure(
                        VoiceProviderException(
                            VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                            "Google TTS tren dien thoai tu choi synthesize file (ma $result)."
                        )
                    )
                )
            }
        }
    }

    private fun buildDisplayName(voice: android.speech.tts.Voice): String {
        val localeName = voice.locale.getDisplayName(Locale("vi", "VN"))
        val suffix = voice.name.substringAfterLast('-', "")
        return if (suffix.isNotBlank()) "$localeName ($suffix)" else "$localeName (${voice.name})"
    }

    private fun inferGender(value: String): String? {
        val normalized = value.lowercase()
        return when {
            normalized.contains("female") || normalized.contains("nu") -> "female"
            normalized.contains("male") || normalized.contains("nam") -> "male"
            else -> null
        }
    }
}

private data class WavPcmData(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val data: ByteArray
)
