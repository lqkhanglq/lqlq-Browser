package com.lqlq.browser

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

class DynamicLootImageCache(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "dynamic_loot").apply { mkdirs() }

    @Synchronized
    fun save(itemId: String, bitmap: Bitmap): String {
        val fileName = "${safeHash(itemId)}.webp"
        val target = File(directory, fileName)
        target.outputStream().use { output ->
            val format = if (android.os.Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 82, output)
        }
        return fileName
    }

    fun open(fileName: String): InputStream? {
        val safe = File(fileName).name
        val file = File(directory, safe)
        return if (file.isFile) FileInputStream(file) else null
    }

    private fun safeHash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(Locale.US, it) }
    }
}

class DynamicLootAssetHandler(
    private val cache: DynamicLootImageCache
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val stream = cache.open(path) ?: return null
        val mime = when {
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            else -> "image/webp"
        }
        return WebResourceResponse(mime, null, stream)
    }
}
