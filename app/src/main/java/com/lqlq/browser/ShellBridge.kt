package com.lqlq.browser

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONObject
import java.io.File

/**
 * Cầu nối "LqlqAndroid" cho WebView giao diện (shell).
 * KHÔNG gắn vào WebView của website bên ngoài.
 */
class ShellBridge(private val activity: MainActivity) {

    // ------------------------------------------------------------------
    // Điều khiển thẻ / trang web thật
    // ------------------------------------------------------------------

    @JavascriptInterface
    fun openPage(tabId: String, url: String) {
        activity.runOnUiThread { activity.openPage(tabId, url) }
    }

    @JavascriptInterface
    fun switchPage(tabId: String) {
        activity.runOnUiThread { activity.switchPage(tabId) }
    }

    @JavascriptInterface
    fun closePage(tabId: String) {
        activity.runOnUiThread { activity.closePage(tabId) }
    }

    @JavascriptInterface
    fun showHome() {
        activity.runOnUiThread { activity.showHome() }
    }

    @JavascriptInterface
    fun setOverlayOpen(open: Boolean) {
        activity.runOnUiThread { activity.setOverlayOpen(open) }
    }

    @JavascriptInterface
    fun openHtmlFile() {
        activity.runOnUiThread { activity.launchOpenHtmlFile() }
    }

    @JavascriptInterface
    fun savePageOffline() {
        activity.runOnUiThread { activity.saveActivePageOffline() }
    }

    @JavascriptInterface
    fun reloadPage() {
        activity.runOnUiThread { activity.reloadActivePage() }
    }

    @JavascriptInterface
    fun setToolbarHeight(cssPx: Float) {
        activity.runOnUiThread { activity.setToolbarHeightCss(cssPx) }
    }

    @JavascriptInterface
    fun injectChapterClipper() {
        activity.runOnUiThread { activity.injectChapterClipper() }
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            toast("Không mở được liên kết ngoài.")
        }
    }

    // ------------------------------------------------------------------
    // Thông báo nền: đọc truyện TXT + nhạc/video
    // ------------------------------------------------------------------

    @JavascriptInterface
    fun onReaderState(json: String) {
        handleState("reader", json)
    }

    @JavascriptInterface
    fun onMediaState(json: String) {
        handleState("media", json)
    }

    private fun handleState(kind: String, json: String) {
        val data = try {
            JSONObject(json)
        } catch (_: Exception) {
            JSONObject()
        }
        val active = data.optBoolean("active", false)
        Log.d("lqlqPlayback", "handleState kind=$kind active=$active playing=${data.optBoolean("playing", false)} json=$json")
        if (!active) {
            PlaybackService.stopKind(activity, kind)
            return
        }
        try {
            PlaybackService.update(
                activity,
                kind,
                data.optString("title", ""),
                data.optString("text", ""),
                data.optBoolean("playing", false)
            )
        } catch (error: Exception) {
            // Trên một số máy, startForegroundService() có thể bị hệ thống
            // chặn nếu app đang ở nền quá lâu trước khi gọi — ghi log để dò.
            Log.w("lqlqPlayback", "Không khởi động được PlaybackService cho kind=$kind", error)
        }
    }

    // ------------------------------------------------------------------
    // Lưu tệp (TXT, ảnh đã chỉnh, trang ngoại tuyến...) vào Download
    // ------------------------------------------------------------------

    @JavascriptInterface
    fun saveTextFile(fileName: String, content: String): Boolean {
        return saveBytes(fileName, "text/plain", content.toByteArray(Charsets.UTF_8))
    }

    @JavascriptInterface
    fun saveBase64File(fileName: String, mimeType: String, base64Data: String): Boolean {
        val bytes = try {
            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (_: Exception) {
            toast("Dữ liệu tệp không hợp lệ.")
            return false
        }
        return saveBytes(fileName, mimeType.ifBlank { "application/octet-stream" }, bytes)
    }

    internal fun saveBytes(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        val safeMime = mimeType.substringBefore(";").trim()
            .ifBlank { "application/octet-stream" }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "lqlq-file" }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, safeMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = activity.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: throw IllegalStateException("insert null")
                activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            } else {
                val dir = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, safeName).writeBytes(bytes)
            }
            toast("Đã lưu vào Download: $safeName")
            true
        } catch (_: Exception) {
            toast("Không lưu được tệp $safeName.")
            false
        }
    }

    @JavascriptInterface
    fun showToast(message: String) = toast(message)

    private fun toast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
