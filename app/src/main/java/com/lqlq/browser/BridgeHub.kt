package com.lqlq.browser

/**
 * Cầu nối tĩnh giữa PlaybackService (thông báo) và MainActivity (WebView giao diện).
 * Service gửi lệnh JavaScript; MainActivity thực thi trên WebView giao diện.
 */
object BridgeHub {
    @Volatile
    var jsRunner: ((String) -> Unit)? = null

    fun runJs(script: String) {
        jsRunner?.invoke(script)
    }

    fun readerCommand(cmd: String) {
        runJs("window.LqlqGlue && window.LqlqGlue.readerCmd('$cmd');")
    }

    fun mediaCommand(cmd: String) {
        runJs("window.LqlqGlue && window.LqlqGlue.mediaCmd('$cmd');")
    }
}
