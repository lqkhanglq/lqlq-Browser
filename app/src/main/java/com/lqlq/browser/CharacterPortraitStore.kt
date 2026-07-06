package com.lqlq.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Ảnh ngoại hình lớn (tối đa 1024x2048) hiển thị trong bảng Hồ sơ Phiêu lưu —
 * tách riêng khỏi avatar nhỏ ở menu (avatarId/customAvatarData trong
 * AdventureProfileStore, giữ nguyên không đổi). Lưu file JPEG trong bộ nhớ
 * riêng của app thay vì base64 trong SharedPreferences vì ảnh này lớn hơn
 * nhiều so với avatar nhỏ (giống cách DynamicLootImageCache đã làm).
 */
class CharacterPortraitStore(context: Context) {

    companion object {
        private const val FILE_NAME = "character_portrait.jpg"
        private const val MAX_WIDTH = 1024
        private const val MAX_HEIGHT = 2048
    }

    private val directory = File(context.applicationContext.filesDir, "adventure_portrait").apply { mkdirs() }
    private val file = File(directory, FILE_NAME)

    @Synchronized
    fun exists(): Boolean = file.isFile

    @Synchronized
    fun save(dataUri: String): Boolean {
        val comma = dataUri.indexOf(',')
        if (comma < 0) return false
        val bytes = try {
            Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT)
        } catch (error: IllegalArgumentException) {
            return false
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val scale = minOf(1f, MAX_WIDTH.toFloat() / bitmap.width, MAX_HEIGHT.toFloat() / bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        val temp = File(directory, "$FILE_NAME.tmp")
        return try {
            temp.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)
            }
            temp.renameTo(file)
        } catch (error: Exception) {
            temp.delete()
            false
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    @Synchronized
    fun clear(): Boolean = !file.exists() || file.delete()

    fun open(): InputStream? = if (file.isFile) FileInputStream(file) else null
}

class CharacterPortraitAssetHandler(
    private val store: CharacterPortraitStore
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val stream = store.open() ?: return null
        return WebResourceResponse("image/jpeg", null, stream)
    }
}
