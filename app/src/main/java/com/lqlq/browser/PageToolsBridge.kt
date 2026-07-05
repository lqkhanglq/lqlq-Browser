package com.lqlq.browser

import android.webkit.JavascriptInterface

/**
 * Bridge tối thiểu dành cho website bên ngoài.
 *
 * Chỉ cấp quyền lưu TXT cho Chapter Clipper. Không để website truy cập API
 * quản lý thẻ, tệp ngoại tuyến, media hoặc các thao tác đặc quyền của shell.
 */
class PageToolsBridge(private val shellBridge: ShellBridge) {

    @JavascriptInterface
    fun saveTextFile(fileName: String, content: String): Boolean {
        return shellBridge.saveBytes(
            fileName,
            "text/plain",
            content.toByteArray(Charsets.UTF_8)
        )
    }
}
