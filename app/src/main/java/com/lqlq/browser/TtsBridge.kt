package com.lqlq.browser

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Cầu nối "LqlqTtsBridge" theo đúng hợp đồng trong ANDROID_TTS_BRIDGE_SPEC.md.
 *
 * Sửa lỗi "không có âm thanh" của bản cũ:
 *  - Đặt AudioAttributes USAGE_MEDIA để tiếng đi qua kênh media.
 *  - Truyền KEY_PARAM_VOLUME từ thanh trượt âm lượng của giao diện.
 *  - Ưu tiên com.google.android.tts, nếu không có thì dùng engine mặc định
 *    thay vì im lặng hoặc văng ứng dụng.
 *  - Khởi tạo lại engine nếu init thất bại.
 */
class TtsBridge(private val context: Context) {

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var enginePackage: String = ""

    private var initLatch = CountDownLatch(1)

    // Ghi nhớ câu đang đọc để "resume" đọc lại câu đó (Android TTS không có pause thật).
    @Volatile
    private var lastText: String = ""

    @Volatile
    private var lastSettingsJson: String = "{}"

    @Volatile
    private var paused = false

    init {
        initEngine("com.google.android.tts")
    }

    private fun initEngine(preferredPackage: String?) {
        ready = false
        initLatch = CountDownLatch(1)
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                enginePackage = preferredPackage ?: (tts?.defaultEngine ?: "")
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                attachProgressListener()
                initLatch.countDown()
                BridgeHub.runJs(
                    "window.LqlqReader && LqlqReader.onNativeVoicesChanged && LqlqReader.onNativeVoicesChanged();"
                )
            } else if (preferredPackage != null) {
                // Engine Google không có / lỗi → thử engine mặc định của máy.
                try {
                    tts?.shutdown()
                } catch (_: Exception) {
                }
                tts = null
                initEngine(null)
            } else {
                initLatch.countDown()
            }
        }
        tts = try {
            if (preferredPackage != null) TextToSpeech(context, listener, preferredPackage)
            else TextToSpeech(context, listener)
        } catch (_: Exception) {
            if (preferredPackage != null) {
                initEngine(null)
                return
            }
            null
        }
        if (tts == null) initLatch.countDown()
    }

    private fun attachProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (paused) return
                BridgeHub.runJs(
                    "window.LqlqReader && LqlqReader.onNativeUtteranceDone && LqlqReader.onNativeUtteranceDone();"
                )
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                reportError("Android TTS gặp lỗi khi đọc.")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                reportError("Android TTS lỗi (mã $errorCode).")
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
        })
    }

    private fun reportError(message: String) {
        val safe = JSONObject.quote(message)
        BridgeHub.runJs(
            "window.LqlqReader && LqlqReader.onNativeUtteranceError && LqlqReader.onNativeUtteranceError($safe);"
        )
    }

    private fun awaitReady(): Boolean {
        if (ready) return true
        try {
            initLatch.await(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
        return ready
    }

    // ------------------------------------------------------------------
    // Hợp đồng JavaScript
    // ------------------------------------------------------------------

    @JavascriptInterface
    fun getTtsEngineInfo(): String {
        if (!awaitReady()) return "{}"
        val engine = tts ?: return "{}"
        val pkg = enginePackage.ifBlank { engine.defaultEngine ?: "" }
        val label = engine.engines.firstOrNull { it.name == pkg }?.label ?: pkg
        return JSONObject().apply {
            put("name", label)
            put("label", label)
            put("packageName", pkg)
        }.toString()
    }

    @JavascriptInterface
    fun getTtsVoices(): String {
        if (!awaitReady()) return "[]"
        val engine = tts ?: return "[]"
        val result = JSONArray()
        val voices = try {
            engine.voices
        } catch (_: Exception) {
            null
        } ?: return "[]"

        val defaultVoice = try {
            engine.defaultVoice
        } catch (_: Exception) {
            null
        }

        val sorted = voices.sortedWith(
            compareByDescending<android.speech.tts.Voice> {
                it.locale.language.equals("vi", ignoreCase = true)
            }.thenBy { it.isNetworkConnectionRequired }
                .thenBy { it.name }
        )

        for (voice in sorted) {
            try {
                result.put(JSONObject().apply {
                    put("id", voice.name)
                    put("name", friendlyVoiceName(voice))
                    put("lang", voice.locale.toLanguageTag())
                    put("default", voice.name == defaultVoice?.name)
                    put("networkRequired", voice.isNetworkConnectionRequired)
                    put("engine", enginePackage.contains("google").let {
                        if (it) "Google" else enginePackage
                    })
                    put("packageName", enginePackage)
                })
            } catch (_: Exception) {
            }
        }
        return result.toString()
    }

    private fun friendlyVoiceName(voice: android.speech.tts.Voice): String {
        val langName = voice.locale.getDisplayName(Locale("vi", "VN"))
        val suffix = voice.name.substringAfterLast('-', "")
        return if (suffix.isNotBlank()) "$langName ($suffix)" else "$langName (${voice.name})"
    }

    @JavascriptInterface
    fun speakText(text: String, settingsJson: String) {
        if (!awaitReady()) {
            reportError("Bộ đọc TTS của Android chưa sẵn sàng.")
            return
        }
        val engine = tts ?: run {
            reportError("Không khởi tạo được TTS trên máy này.")
            return
        }

        paused = false
        lastText = text
        lastSettingsJson = settingsJson

        val settings = try {
            JSONObject(settingsJson)
        } catch (_: Exception) {
            JSONObject()
        }

        val rate = settings.optDouble("rate", 100.0).toFloat() / 100f
        val pitch = settings.optDouble("pitch", 100.0).toFloat() / 100f
        val volume = (settings.optDouble("volume", 100.0).toFloat() / 100f)
            .coerceIn(0f, 1f)
        val voiceId = settings.optString("voice", "")
        val language = settings.optString("language", "vi-VN")
        val utteranceId = settings.optString("utteranceId", "lqlq-reader")

        engine.setSpeechRate(rate.coerceIn(0.1f, 4f))
        engine.setPitch(pitch.coerceIn(0.1f, 4f))

        // Chọn voice: đúng id trước, sau đó voice vi-VN offline, rồi vi bất kỳ.
        var applied = false
        try {
            val voices = engine.voices
            if (voices != null) {
                val exact = voices.firstOrNull { it.name == voiceId }
                val viOffline = voices.firstOrNull {
                    it.locale.language.equals("vi", true) && !it.isNetworkConnectionRequired
                }
                val viAny = voices.firstOrNull { it.locale.language.equals("vi", true) }
                val chosen = exact ?: viOffline ?: viAny
                if (chosen != null) {
                    engine.voice = chosen
                    applied = true
                }
            }
        } catch (_: Exception) {
        }
        if (!applied) {
            try {
                val tag = language.ifBlank { "vi-VN" }.replace('_', '-')
                engine.language = Locale.forLanguageTag(tag)
            } catch (_: Exception) {
                engine.language = Locale("vi", "VN")
            }
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            putInt(
                TextToSpeech.Engine.KEY_PARAM_STREAM,
                AudioManager.STREAM_MUSIC
            )
        }

        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            reportError("TTS từ chối đọc câu này (mã $result).")
        }
    }

    @JavascriptInterface
    fun pauseTts() {
        paused = true
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun resumeTts() {
        if (lastText.isBlank()) return
        paused = false
        // Android TTS không hỗ trợ resume — đọc lại câu hiện tại từ đầu.
        speakText(lastText, lastSettingsJson)
    }

    @JavascriptInterface
    fun stopTts() {
        paused = false
        lastText = ""
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun openTtsSettings() {
        val intents = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }
}
