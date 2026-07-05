package com.lqlq.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JsResult
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SHELL_HOST = "appassets.androidapp.com"
        private const val SHELL_URL = "https://$SHELL_HOST/assets/www/index.html"
    }

    private lateinit var root: FrameLayout
    private lateinit var shellWebView: WebView
    private lateinit var pageContainer: FrameLayout

    /** WebView của từng thẻ đang mở website thật. */
    private val pageWebViews = LinkedHashMap<String, WebView>()
    private var activeTabId: String? = null
    private var pageVisible = false

    /** Lớp phủ của giao diện (menu, panel...) đang mở → phải nổi trên trang web. */
    @Volatile
    private var overlayOpen = false

    private var toolbarHeightPx = 0

    private lateinit var assetLoader: WebViewAssetLoader
    private var ttsBridge: TtsBridge? = null
    private lateinit var shellBridge: ShellBridge

    private val htmlFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            loadOfflineHtml(uri)
        }
    }

    // ------------------------------------------------------------------
    // Chọn tệp (input type=file) — dùng chung cho shell và trang web
    // ------------------------------------------------------------------
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult
        val data = result.data
        val uris = when {
            result.resultCode != RESULT_OK || data == null -> null
            data.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Video toàn màn hình
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nút âm lượng vật lý điều khiển kênh media (nhạc + TTS).
        volumeControlStream = AudioManager.STREAM_MUSIC

        root = FrameLayout(this)
        shellWebView = WebView(this)
        pageContainer = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.WHITE)
        }

        root.addView(
            shellWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            pageContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(SHELL_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        setupShellWebView()
        registerBridgeHub()
        requestStartupPermissions()
        setupBackHandling()

        shellWebView.loadUrl(SHELL_URL)
    }

    private fun registerBridgeHub() {
        BridgeHub.jsRunner = { script ->
            runOnUiThread {
                if (!isDestroyed) {
                    shellWebView.evaluateJavascript(script, null)
                }
            }
        }
    }

    private fun requestStartupPermissions() {
        val wanted = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
            wanted += Manifest.permission.READ_MEDIA_AUDIO
            wanted += Manifest.permission.READ_MEDIA_VIDEO
            wanted += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            wanted += Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= 28) {
                wanted += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }

        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // ------------------------------------------------------------------
    // Shell WebView (giao diện lqlq)
    // ------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupShellWebView() {
        applyCommonSettings(shellWebView.settings)
        shellWebView.settings.allowFileAccess = true

        // Việc 3 (v0.23.4): đặt nền trong suốt NGAY từ đầu, giữ nguyên mãi —
        // không toggle WHITE/TRANSPARENT lúc mở/đóng menu nữa. Lý do: nếu
        // WebView từng vẽ 1 khung hình với nền trắng đục (ngay cả trong quá
        // khứ), khung hình đó có thể bị hiển thị lại y hệt trong khoảnh khắc
        // bringChildToFront() trước khi CSS kịp vẽ lại nội dung thật (chớp
        // trắng). Nền trong suốt cố định + html,body{background:...} đục
        // trong CSS (bình thường) đảm bảo không còn khung hình trắng "cũ"
        // nào có thể lộ ra. z-order vẫn do bringChildToFront() quyết định.
        shellWebView.setBackgroundColor(Color.TRANSPARENT)

        ttsBridge = TtsBridge(applicationContext)
        shellBridge = ShellBridge(this)
        shellWebView.addJavascriptInterface(shellBridge, "LqlqAndroid")
        shellWebView.addJavascriptInterface(ttsBridge!!, "LqlqTtsBridge")
        shellWebView.addJavascriptInterface(
            PageBridge { currentPageWebView() },
            "LqlqPageBridge"
        )

        shellWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                // Giữ shell trong asset; link http bấm trong shell mở như một trang.
                if (request.url.host == SHELL_HOST) return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val tabId = activeTabId ?: "shell-tab"
                    openPage(tabId, url)
                    return true
                }
                openExternalUri(request.url)
                return true
            }
        }

        shellWebView.webChromeClient = createChromeClient()
    }

    // ------------------------------------------------------------------
    // Page WebViews (website thật)
    // ------------------------------------------------------------------

    private fun applyCommonSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // SỬA LỖI KHÔNG CÓ ÂM THANH: cho phép media tự phát không cần chạm.
        settings.mediaPlaybackRequiresUserGesture = false

        // Cần để mở tệp .mht đã lưu bằng file://... (trang ngoại tuyến).
        settings.allowFileAccess = true
    }

    private fun currentPageWebView(): WebView? =
        activeTabId?.let { pageWebViews[it] }

    fun openPage(tabId: String, url: String) {
        activeTabId = tabId
        val webView = pageWebViews.getOrPut(tabId) { createPageWebView(tabId) }
        bringPageToFront(webView)
        webView.loadUrl(url)
        setPageVisible(true)
    }

    fun switchPage(tabId: String) {
        activeTabId = tabId
        val webView = pageWebViews[tabId]
        if (webView != null) {
            bringPageToFront(webView)
            setPageVisible(true)
            notifyShellPage(tabId, webView.url ?: "", webView.title ?: "", loading = false)
        } else {
            setPageVisible(false)
        }
    }

    fun closePage(tabId: String) {
        val webView = pageWebViews.remove(tabId) ?: return
        pageContainer.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        if (activeTabId == tabId) {
            setPageVisible(false)
        }
    }

    fun showHome() {
        setPageVisible(false)
    }

    fun reloadActivePage() {
        currentPageWebView()?.reload()
    }

    // ------------------------------------------------------------------
    // Việc 4 (v0.23.4): Chapter Clipper — content-script thật, tiêm trực
    // tiếp vào WebView của trang web đang mở (không round-trip qua
    // PageBridge/menu như kiến trúc cũ, tránh đơ/lag).
    // ------------------------------------------------------------------

    private fun readAssetText(path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun injectChapterClipper() {
        val webView = currentPageWebView()
        if (webView == null || !pageVisible) {
            Toast.makeText(
                this,
                "Hãy mở một trang web thật rồi bật Chapter Clipper.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        try {
            val css = readAssetText("www/tools/chapter-clipper-styles.css")
            val js = readAssetText("www/tools/chapter-clipper-content.js")
            val injected = """
(function () {
  try {
    if (!document.getElementById('lqlq-chapter-clipper-style')) {
      var style = document.createElement('style');
      style.id = 'lqlq-chapter-clipper-style';
      style.textContent = ${JSONObject.quote(css)};
      (document.head || document.documentElement).appendChild(style);
    }
  } catch (e) {}
})();
$js
"""
            webView.evaluateJavascript(injected, null)
            Toast.makeText(this, "Đã bật Chapter Clipper trên trang này.", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Không nạp được Chapter Clipper.", Toast.LENGTH_SHORT).show()
        }
    }

    fun setToolbarHeightCss(cssPx: Float) {
        val density = resources.displayMetrics.density
        toolbarHeightPx = (cssPx * density).toInt().coerceAtLeast(0)
        val params = pageContainer.layoutParams as FrameLayout.LayoutParams
        params.topMargin = toolbarHeightPx
        pageContainer.layoutParams = params
    }

    private fun bringPageToFront(webView: WebView) {
        for (view in pageWebViews.values) {
            view.visibility = if (view === webView) View.VISIBLE else View.GONE
        }
        if (webView.parent == null) {
            pageContainer.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    fun setOverlayOpen(open: Boolean) {
        overlayOpen = open
        updatePageVisibility()
    }

    private fun updatePageVisibility() {
        // pageContainer LUÔN hiển thị khi có trang web đang mở — menu/panel của
        // giao diện nổi lên bằng z-order (bringChildToFront), không che bằng
        // cách ẩn pageContainer (việc đó từng làm lộ trang chủ phía dưới).
        pageContainer.visibility = if (pageVisible) View.VISIBLE else View.GONE

        if (overlayOpen) {
            // Đưa shell lên trên để menu/panel nổi trên trang web thật; nền
            // shell luôn trong suốt (đặt 1 lần trong setupShellWebView()) nên
            // không cần chờ hay set lại ở đây — tránh chớp trắng do vẽ lại.
            root.bringChildToFront(shellWebView)
        } else {
            // Đóng overlay: nếu đang có trang web thật thì đưa nó lên trên;
            // nếu không (đang ở trang chủ) thì giữ shell trên cùng như cũ.
            if (pageVisible) {
                root.bringChildToFront(pageContainer)
            } else {
                root.bringChildToFront(shellWebView)
            }
        }
        root.invalidate()
    }

    private fun setPageVisible(visible: Boolean) {
        pageVisible = visible
        updatePageVisibility()
        shellWebView.evaluateJavascript(
            "window.LqlqGlue && LqlqGlue.onNativePageVisibility(${visible});",
            null
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createPageWebView(tabId: String): WebView {
        val webView = WebView(this)
        applyCommonSettings(webView.settings)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val scheme = request.url.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                openExternalUri(request.url)
                return true
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: android.graphics.Bitmap?
            ) {
                if (tabId == activeTabId) {
                    notifyShellPage(tabId, url, view.title ?: "", loading = true)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                notifyShellPage(tabId, url, view.title ?: "", loading = false)
            }

            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String,
                isReload: Boolean
            ) {
                // QUAN TRỌNG: doUpdateVisitedHistory() có thể được gọi nhiều lần
                // trong một chuỗi chuyển hướng (redirect), TRƯỚC KHI trang đích
                // cuối cùng thực sự tải xong (ví dụ gõ từ khóa → tạm qua
                // www.google.com/search?q=... rồi mới điều hướng tiếp).
                // Nếu báo loading=false ở đây, Nhật ký sẽ ghi nhầm URL trung
                // gian thay vì trang đích thật. Chỉ cập nhật thanh địa chỉ/tiêu
                // đề tạm thời (loading=true) — mục Nhật ký chỉ được ghi thật sự
                // tại onPageFinished(), khi URL cuối cùng đã tải xong.
                notifyShellPage(tabId, url, view.title ?: "", loading = true)
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail
            ): Boolean {
                // Không để cả app văng khi renderer của một trang chết.
                closePage(tabId)
                Toast.makeText(
                    this@MainActivity,
                    "Trang web gặp sự cố và đã được đóng.",
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }
        }

        webView.webChromeClient = createChromeClient()

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }

        return webView
    }

    private fun notifyShellPage(tabId: String, url: String, title: String, loading: Boolean) {
        val payload = JSONObject().apply {
            put("tabId", tabId)
            put("url", url)
            put("title", title)
            put("loading", loading)
        }
        shellWebView.evaluateJavascript(
            "window.LqlqGlue && LqlqGlue.onNativePage($payload);",
            null
        )
    }

    private fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(this, "Tệp này cần lưu bằng nút lưu trong ứng dụng.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                setTitle(fileName)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Đang tải xuống: $fileName", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Không tải xuống được tệp này.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExternalUri(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "Không có ứng dụng nào mở được liên kết này.", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------
    // Trang ngoại tuyến: mở tệp HTML đã lưu + lưu trang web thật
    // ------------------------------------------------------------------

    fun launchOpenHtmlFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/html",
                    "application/xhtml+xml",
                    "text/plain",
                    "application/octet-stream",
                    // Tệp .mht/.mhtml lưu bằng saveWebArchive() (giữ ảnh/CSS).
                    "application/x-mimearchive",
                    "message/rfc822",
                    "multipart/related"
                )
            )
        }
        try {
            htmlFileLauncher.launch(Intent.createChooser(intent, "Chọn tệp HTML/MHT đã lưu"))
        } catch (_: Exception) {
            Toast.makeText(this, "Không mở được hộp chọn tệp.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadOfflineHtml(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri) ?: ""
            val name = queryDisplayName(uri) ?: ""
            val isMht = mimeType.contains("mimearchive", true) ||
                mimeType.contains("rfc822", true) ||
                mimeType.contains("multipart/related", true) ||
                name.endsWith(".mht", true) ||
                name.endsWith(".mhtml", true)

            val tabId = "offline-html"
            activeTabId = tabId
            val webView = pageWebViews.getOrPut(tabId) { createPageWebView(tabId) }
            bringPageToFront(webView)

            if (isMht) {
                // .mht giữ nguyên ảnh/CSS đã đóng gói — cần copy về file cục bộ
                // rồi mở bằng file:// (webView cần allowFileAccess = true).
                val tempFile = File(cacheDir, "offline_open_${System.currentTimeMillis()}.mht")
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("empty")
                webView.settings.allowFileAccess = true
                webView.loadUrl("file://${tempFile.absolutePath}")
            } else {
                val html = contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: throw IllegalStateException("empty")
                if (html.isBlank()) throw IllegalStateException("blank")
                webView.loadDataWithBaseURL(
                    "https://offline.lqlq.local/",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
            setPageVisible(true)
            Toast.makeText(this, "Đã mở trang ngoại tuyến.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Không đọc được tệp này.", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveActivePageOffline() {
        val page = currentPageWebView()
        if (page == null || !pageVisible) {
            Toast.makeText(this, "Chưa có trang web nào đang mở.", Toast.LENGTH_SHORT).show()
            return
        }
        val title = (page.title ?: "trang-web").take(60).ifBlank { "trang-web" }

        // saveWebArchive() lưu file .mht đầy đủ (HTML + ảnh + CSS đóng gói
        // cùng file), khác với outerHTML cũ (chỉ có HTML, mất ảnh/CSS).
        val cacheMhtDir = File(cacheDir, "offline_mht").apply { mkdirs() }
        val tempPath = File(cacheMhtDir, "page_${System.currentTimeMillis()}.mht").absolutePath
        page.saveWebArchive(tempPath, false) { savedPath ->
            if (savedPath == null) {
                Toast.makeText(this, "Không lưu được nội dung trang.", Toast.LENGTH_SHORT).show()
                return@saveWebArchive
            }
            Thread {
                try {
                    val bytes = File(savedPath).readBytes()
                    shellBridge.saveBytes(
                        "${title}_offline.mht",
                        "application/x-mimearchive",
                        bytes
                    )
                } catch (_: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Không đọc được tệp đã lưu.", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    try {
                        File(savedPath).delete()
                    } catch (_: Exception) {
                    }
                }
            }.start()
        }
    }

    // ------------------------------------------------------------------
    // WebChromeClient chung: chọn tệp + video toàn màn hình
    // ------------------------------------------------------------------

    private fun createChromeClient() = object : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback

            // Tự dựng intent thay vì fileChooserParams.createIntent() vì hàm đó
            // xử lý sai accept dạng ".txt" hoặc "audio/*,video/*" trên nhiều máy
            // → hộp chọn tệp không mở hoặc không chọn được gì.
            val accepts = (fileChooserParams.acceptTypes ?: emptyArray())
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val mimes = accepts.mapNotNull { accept ->
                when {
                    accept.contains("/") -> accept
                    accept.startsWith(".") -> MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(accept.removePrefix(".").lowercase())
                        ?: if (accept.equals(".txt", true)) "text/plain" else null
                    else -> null
                }
            }.distinct()

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimes.size == 1) mimes[0] else "*/*"
                if (mimes.size > 1) {
                    putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                }
                if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }

            return try {
                fileChooserLauncher.launch(Intent.createChooser(intent, "Chọn tệp"))
                true
            } catch (_: Exception) {
                filePathCallback = null
                false
            }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback
            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            view.setBackgroundColor(Color.BLACK)
        }

        override fun onHideCustomView() {
            exitCustomView()
        }

        // Việc 4 (v0.23.4): Chapter Clipper (content-script tiêm vào trang
        // thật) dùng alert()/confirm() thường — WebView mặc định không hiện
        // hộp thoại nếu không override 2 hàm này (onJsConfirm mặc định trả
        // false ngay), nên phải tự vẽ AlertDialog native ở đây.
        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult
        ): Boolean {
            try {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .setCancelable(true)
                    .show()
            } catch (_: Exception) {
                result.confirm()
            }
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String,
            message: String,
            result: JsResult
        ): Boolean {
            try {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Hủy") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .setCancelable(true)
                    .show()
            } catch (_: Exception) {
                result.cancel()
            }
            return true
        }
    }

    private fun exitCustomView() {
        customView?.let { root.removeView(it) }
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    // ------------------------------------------------------------------
    // Nút back: thoát video → lùi trang → về trang chủ → nền
    // ------------------------------------------------------------------

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val page = currentPageWebView()
                when {
                    customView != null -> exitCustomView()
                    // Menu / panel của giao diện đang mở → đóng nó trước
                    overlayOpen -> shellWebView.evaluateJavascript(
                        "window.LqlqGlue && LqlqGlue.closeTopOverlay();",
                        null
                    )
                    pageVisible && page?.canGoBack() == true -> page.goBack()
                    pageVisible -> showHome()
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    // ------------------------------------------------------------------
    // Vòng đời: KHÔNG tạm dừng WebView khi ra nền để nhạc/TTS tiếp tục
    // ------------------------------------------------------------------

    override fun onPause() {
        super.onPause()
        // Cố ý không gọi webView.onPause()/pauseTimers() để media chạy nền.
        shellWebView.resumeTimers()
    }

    override fun onResume() {
        super.onResume()
        shellWebView.resumeTimers()
    }

    override fun onDestroy() {
        BridgeHub.jsRunner = null
        ttsBridge?.shutdown()
        // Ứng dụng bị đóng hẳn → WebView không còn, dừng service và gỡ thông báo
        try {
            stopService(Intent(this, PlaybackService::class.java))
        } catch (_: Exception) {
        }
        for (webView in pageWebViews.values) {
            webView.destroy()
        }
        pageWebViews.clear()
        shellWebView.destroy()
        super.onDestroy()
    }
}
