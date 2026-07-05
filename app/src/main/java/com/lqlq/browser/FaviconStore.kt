package com.lqlq.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Bộ nhớ đệm favicon cục bộ.
 *
 * Favicon chỉ được lấy từ WebChromeClient.onReceivedIcon() của trang đã tải,
 * không tự tạo request /favicon.ico. Vì vậy trang thẻ mới hiển thị ngay bằng
 * icon đã có hoặc chữ thay thế, không phát sinh một loạt request mạng nền.
 */
class FaviconStore(context: Context) {

    companion object {
        private const val MAX_EDGE_PX = 64
        private const val MAX_FILES = 96
        private const val MAX_DATA_URL_BYTES = 40 * 1024
    }

    private val directory = File(context.filesDir, "favicon_cache_v1").apply { mkdirs() }
    private val memory = object : LruCache<String, String>(48) {}
    private val executor = Executors.newSingleThreadExecutor()

    fun put(url: String?, bitmap: Bitmap?) {
        val key = keyFor(url) ?: return
        val source = bitmap ?: return
        val copy = try {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            null
        } ?: return

        executor.execute {
            try {
                val scaled = scaleDown(copy)
                if (scaled !== copy) copy.recycle()

                val bytes = ByteArrayOutputStream().use { output ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
                    output.toByteArray()
                }
                scaled.recycle()

                if (bytes.isEmpty() || bytes.size > MAX_DATA_URL_BYTES) return@execute
                val file = fileFor(key)
                val temp = File(directory, "${file.name}.tmp")
                temp.writeBytes(bytes)
                if (!temp.renameTo(file)) {
                    file.writeBytes(bytes)
                    temp.delete()
                }
                file.setLastModified(System.currentTimeMillis())
                memory.put(key, toDataUrl(bytes))
                trimNow()
            } catch (_: Exception) {
                try { copy.recycle() } catch (_: Exception) {}
            }
        }
    }

    /** Chỉ đọc RAM; an toàn để gọi từ callback UI như onPageFinished. */
    fun peekDataUrl(url: String?): String {
        val key = keyFor(url) ?: return ""
        return memory.get(key).orEmpty()
    }

    /** Có thể đọc tệp cache; nên gọi từ JavaScript bridge/luồng nền. */
    fun getDataUrl(url: String?): String {
        val key = keyFor(url) ?: return ""
        memory.get(key)?.let { return it }
        return try {
            val file = fileFor(key)
            if (!file.isFile) return ""
            val bytes = file.readBytes()
            if (bytes.isEmpty() || bytes.size > MAX_DATA_URL_BYTES) return ""
            file.setLastModified(System.currentTimeMillis())
            toDataUrl(bytes).also { memory.put(key, it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun trimAsync() {
        executor.execute { trimNow() }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val largest = max(width, height)
        if (largest <= MAX_EDGE_PX) return bitmap
        val ratio = MAX_EDGE_PX.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun trimNow() {
        try {
            val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            files.drop(MAX_FILES).forEach { it.delete() }
            directory.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { temp ->
                if (System.currentTimeMillis() - temp.lastModified() > 60_000L) temp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun fileFor(key: String) = File(directory, "$key.png")

    private fun keyFor(url: String?): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank()) return null
        return try {
            val uri = Uri.parse(raw)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            val scheme = uri.scheme?.lowercase().orEmpty()
            val port = uri.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
            sha256("$scheme://$host$port")
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun toDataUrl(bytes: ByteArray): String =
        "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}
