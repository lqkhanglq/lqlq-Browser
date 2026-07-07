package com.lqlq.browser

import android.webkit.JavascriptInterface

/**
 * Bridge tối thiểu dành cho website bên ngoài.
 *
 * Chỉ cấp quyền lưu TXT cho Chapter Clipper, VÀ chỉ khi [isAllowed] trả về
 * true (tab đó đã được người dùng chủ động bật Chapter Clipper) — trước đây
 * hàm này không kiểm tra gì cả, nghĩa là BẤT KỲ website nào cũng gọi
 * saveTextFile() được ngay cả khi người dùng chưa từng bật công cụ đó.
 * Mỗi tab có 1 instance riêng (xem createPageWebView) nên rate limit dưới
 * đây tự nhiên tính theo từng tab, không lẫn giữa các tab khác nhau.
 */
class PageToolsBridge(
    private val shellBridge: ShellBridge,
    private val isAllowed: () -> Boolean
) {
    private val recentCallTimestamps = ArrayDeque<Long>()

    @JavascriptInterface
    fun saveTextFile(fileName: String, content: String): Boolean {
        if (!isAllowed()) return false
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_TEXT_BYTES) return false
        synchronized(recentCallTimestamps) {
            val now = System.currentTimeMillis()
            while (recentCallTimestamps.isNotEmpty() && now - recentCallTimestamps.first() > RATE_LIMIT_WINDOW_MS) {
                recentCallTimestamps.removeFirst()
            }
            if (recentCallTimestamps.size >= MAX_SAVES_PER_WINDOW) return false
            recentCallTimestamps.addLast(now)
        }
        return shellBridge.saveBytes(fileName, "text/plain", bytes)
    }

    companion object {
        private const val MAX_TEXT_BYTES = 4 * 1024 * 1024 // 4 MiB
        private const val RATE_LIMIT_WINDOW_MS = 60_000L
        private const val MAX_SAVES_PER_WINDOW = 8
    }
}
