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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Cầu nối "LqlqAndroid" cho WebView giao diện (shell).
 * KHÔNG gắn vào WebView của website bên ngoài.
 */
class ShellBridge(private val activity: MainActivity) {

    // ------------------------------------------------------------------
    // Điều khiển thẻ / trang web thật
    // ------------------------------------------------------------------


    @JavascriptInterface
    fun getTabState(): String = activity.getTabStateJson()

    @JavascriptInterface
    fun getActivePageSnapshot(): String = activity.getActivePageSnapshotJson()

    @JavascriptInterface
    fun getCachedFaviconData(url: String): String = activity.getCachedFaviconData(url)

    @JavascriptInterface
    fun showTabSwitcher() {
        activity.runOnUiThread { activity.showNativeTabSwitcher() }
    }

    @JavascriptInterface
    fun createTab(url: String, mode: String): String {
        var result = ""
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            try {
                result = activity.createNativeTab(url, mode)
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }

    @JavascriptInterface
    fun selectTab(tabId: String, mode: String) {
        activity.runOnUiThread { activity.selectNativeTab(tabId, mode) }
    }

    @JavascriptInterface
    fun closeTab(tabId: String, mode: String) {
        activity.runOnUiThread { activity.closeNativeTab(tabId, mode) }
    }

    @JavascriptInterface
    fun closeOtherTabs(mode: String) {
        activity.runOnUiThread {
            activity.setNativeTabMode(mode)
            activity.closeOtherNativeTabs()
        }
    }

    @JavascriptInterface
    fun closeAllTabs(mode: String) {
        activity.runOnUiThread {
            activity.setNativeTabMode(mode)
            activity.closeAllNativeTabs()
        }
    }

    @JavascriptInterface
    fun setTabMode(mode: String) {
        activity.runOnUiThread { activity.setNativeTabMode(mode) }
    }

    @JavascriptInterface
    fun openPage(tabId: String, url: String) {
        activity.runOnUiThread { activity.openPage(tabId, url) }
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
    fun setAppearance(theme: String, accent: String) {
        activity.runOnUiThread { activity.applyNativeAppearance(theme, accent) }
    }

    @JavascriptInterface
    fun openHtmlFile() {
        activity.runOnUiThread { activity.launchOpenHtmlFile() }
    }

    @JavascriptInterface
    fun savePageOffline() {
        activity.runOnUiThread { activity.saveActivePageOffline() }
    }

    /**
     * Việc (v0.23.24 — Vấn đề 2): mở lại ĐÚNG tệp .mht/.html đã lưu ngoại
     * tuyến, từ Uri (content:// do chính app tạo qua MediaStore, hoặc
     * file:// trên Android < Q) — dùng khi người dùng bấm vào 1 mục trong
     * "Trang đã lưu" có gắn cờ offline, thay vì điều hướng online lại URL.
     */
    @JavascriptInterface
    fun openOfflineUri(uriString: String, fallbackUrl: String) {
        activity.runOnUiThread { activity.openOfflineUriFromShell(uriString, fallbackUrl) }
    }

    @JavascriptInterface
    fun reloadPage() {
        activity.runOnUiThread { activity.reloadActivePage() }
    }

    @JavascriptInterface
    fun printPage() {
        activity.runOnUiThread { activity.printActivePage() }
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
    fun extractChapterForReader() {
        activity.runOnUiThread { activity.extractChapterForReader() }
    }


    /**
     * Việc (v0.23.28): bật/tắt lớp ẩn quảng cáo DOM — mặc định TẮT (đặt ở
     * MainActivity.adblockDomEnabled, khởi tạo false). Lớp chặn domain/
     * redirect (native, luôn bật) không bị ảnh hưởng bởi cờ này.
     */
    @JavascriptInterface
    fun setAdblockDomEnabled(enabled: Boolean) {
        activity.adblockDomEnabled = enabled
    }

    /**
     * Bật/tắt lớp chặn domain quảng cáo đã biết + chặn nhảy trang sang domain
     * lạ (MainActivity.domainGuardEnabled, mặc định BẬT). Khác với
     * setAdblockDomEnabled() ở trên — đây là lớp chặn ở tầng mạng
     * (shouldOverrideUrlLoading/shouldInterceptRequest), không liên quan tới
     * việc ẩn quảng cáo trong DOM.
     */
    @JavascriptInterface
    fun setDomainGuardEnabled(enabled: Boolean) {
        activity.domainGuardEnabled = enabled
    }

    @JavascriptInterface
    fun isDomainGuardEnabled(): Boolean = activity.domainGuardEnabled

    /**
     * Đồng bộ (không cần runOnUiThread callback riêng) vì WebView JS→Java
     * hỗ trợ giá trị trả về ngay khi gọi cùng luồng UI; dùng để cập nhật
     * trạng thái nút menu (bật/tắt) mỗi khi mở menu hoặc chuyển tab.
     */
    @JavascriptInterface
    fun isChapterClipperEnabled(): Boolean = activity.isChapterClipperEnabled()

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

    /** Mở trình chọn tài liệu Android để lấy URI bền vững cho MP3/MP4. */
    @JavascriptInterface
    fun openNativeMediaFile() {
        activity.runOnUiThread { activity.launchNativeMediaFilePicker() }
    }

    /** Chọn một thư mục để tạo playlist ổn định từ toàn bộ MP3/MP4 bên trong. */
    @JavascriptInterface
    fun openNativeMediaFolder() {
        activity.runOnUiThread { activity.launchNativeMediaFolderPicker() }
    }

    /** Phát URL media trực tiếp bằng Media3 thay vì thẻ video trong WebView. */
    @JavascriptInterface
    fun playNativeMediaUrl(url: String, title: String, mimeType: String) {
        activity.runOnUiThread { activity.playNativeMediaUrl(url, title, mimeType) }
    }

    @JavascriptInterface
    fun nativeMediaCommand(command: String) {
        activity.runOnUiThread { activity.nativeMediaCommand(command) }
    }

    @JavascriptInterface
    fun setNativeMediaVolume(value: Float) {
        activity.runOnUiThread { activity.setNativeMediaVolume(value) }
    }

    @JavascriptInterface
    fun setNativeMediaRepeatOne(enabled: Boolean) {
        activity.runOnUiThread { activity.setNativeMediaRepeatOne(enabled) }
    }

    /** Đồng bộ trạng thái YouTube iframe để Activity tự vào PiP khi bấm Home. */
    @JavascriptInterface
    fun setYoutubePlaybackState(active: Boolean, playing: Boolean, url: String) {
        activity.runOnUiThread { activity.setYoutubePlaybackState(active, playing, url) }
    }

    @JavascriptInterface
    fun enterYoutubePictureInPicture() {
        activity.runOnUiThread { activity.enterYoutubePictureInPicture() }
    }

    @JavascriptInterface
    fun openYoutubeExternally(url: String) {
        activity.runOnUiThread { activity.openYoutubeExternally(url) }
    }

    @JavascriptInterface
    fun getNativeMediaState(): String = activity.getNativeMediaStateJson()

    /**
     * Việc (v0.23.21): "Nhạc và video nền" giờ là công cụ tiện ích kiểu
     * Chapter Clipper — bật/tắt bằng nút menu ☰, KHÁC với tín hiệu
     * active/playing của onMediaState() (vốn chỉ phản ánh có đang phát hay
     * không). Nút bọt nổi hiện theo đúng trạng thái BẬT/TẮT công cụ này,
     * không còn theo trạng thái phát nữa — khớp yêu cầu: đóng bảng thêm
     * nhạc (X) không tắt bọt nổi, chỉ tắt hẳn qua menu mới ẩn bọt.
     */
    @JavascriptInterface
    fun setMediaToolEnabled(enabled: Boolean) {
        activity.runOnUiThread { activity.setMediaBubbleVisible(enabled) }
    }

    private fun handleState(kind: String, json: String) {
        val data = try {
            JSONObject(json)
        } catch (_: Exception) {
            JSONObject()
        }
        val active = data.optBoolean("active", false)
        Log.d("lqlqPlayback", "handleState kind=$kind active=$active playing=${data.optBoolean("playing", false)} json=$json")

        // Media3/MediaSessionService tự sở hữu notification và nút khóa màn hình.
        // Không để PlaybackService cũ tạo thêm một thông báo media thứ hai.
        if (kind == "media" && data.optString("backend") == "native") {
            PlaybackService.stopKind(activity, kind)
            return
        }

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
        return saveBytesReturningUri(fileName, mimeType, bytes) != null
    }

    /**
     * Việc (v0.23.24 — Vấn đề 2): giống saveBytes() nhưng trả về Uri (dạng
     * String) của tệp vừa lưu — cần để "Lưu trang ngoại tuyến" biết CHÍNH
     * XÁC tệp .mht nào vừa ghi, để "Trang đã lưu" có thể mở lại ĐÚNG tệp đó
     * (qua openOfflineUri()) thay vì chỉ điều hướng online lại URL gốc.
     */
    internal fun saveBytesReturningUri(fileName: String, mimeType: String, bytes: ByteArray): String? {
        val safeMime = mimeType.substringBefore(";").trim()
            .ifBlank { "application/octet-stream" }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "lqlq-file" }
        return try {
            val resultUri: String
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, safeMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = activity.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: throw IllegalStateException("insert null")
                try {
                    activity.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                        ?: throw IllegalStateException("output null")
                    val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    activity.contentResolver.update(uri, ready, null, null)
                    resultUri = uri.toString()
                } catch (error: Exception) {
                    activity.contentResolver.delete(uri, null, null)
                    throw error
                }
            } else {
                val dir = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = File(dir, safeName)
                file.writeBytes(bytes)
                resultUri = Uri.fromFile(file).toString()
            }
            toast("Đã lưu vào Download: $safeName")
            resultUri
        } catch (_: Exception) {
            toast("Không lưu được tệp $safeName.")
            null
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
